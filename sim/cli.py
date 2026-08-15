"""Interfaz operacional del simulador.

Cumple el requisito del enunciado: "interacción básica (por consola o una
interfaz gráfica mínima) para ejecutar al menos cinco (5) fases consecutivas
del juego", con entrada de variables (tropas, nivel de impuestos) y cálculo
del estado del turno siguiente.
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

from . import casos_borde, lote, militar
from .agentes import NOMBRE_ESTRATEGIA, PARAMETROS_ESTRATEGIA
from .economia import renta_provincia, tamano_maximo_sostenible, tasa_equilibrio
from .escenario import cargar, cargar_referencia
from .estado import Estado, clamp
from .eventos import Motor
from .parametros import PROCEDENCIA, UNIDADES, Parametros
from .recolector import Recolector
from .reloj import MICRO, formato_tau

RAIZ = Path(__file__).resolve().parent.parent


# ------------------------------------------------------------------- vistas

def ver_estado(estado: Estado) -> None:
    lider = estado.lider()
    print(f"\n  Escenario: {estado.nombre}   τ={formato_tau(estado.tau)}   "
          f"turno={estado.turno}   m={estado.m}   ν={estado.num_combates}   "
          f"β={estado.bajas_totales:.0f}")
    print(f"  {'imperio':<12} {'estr':<5} {'n':>3} {'q':>6} {'oro':>9} {'M':>9} "
          f"{'θ':>6} {'θ^eq':>7} {'capital':>8} {'guerra':>7}")
    print("  " + "-" * 84)
    for imp in estado.imperios.values():
        marca = "★" if imp.id == lider else (" " if imp.activo else "✝")
        print(f"{marca} {imp.id:<12} {imp.estrategia:<5} {estado.n(imp.id):>3} "
              f"{estado.cuota(imp.id):>6.3f} {imp.oro:>9.2f} {imp.poder_militar:>9.1f} "
              f"{imp.tasa:>6.1f} {tasa_equilibrio(estado, imp.id):>7.1f} "
              f"{str(imp.capital):>8} {'sí' if estado.en_guerra(imp.id) else 'no':>7}")


def ver_imperio(estado: Estado, iid: str) -> None:
    imp = estado.imperio(iid)
    par = PARAMETROS_ESTRATEGIA[imp.estrategia]
    print(f"\n  {imp.nombre} ({imp.id}) — {NOMBRE_ESTRATEGIA[imp.estrategia]}")
    print(f"    G_i = {imp.oro:.2f}   θ_i = {imp.tasa:.1f}%   M_i = {imp.poder_militar:.1f}   "
          f"c_i = {imp.capital}   α_i = {int(imp.activo)}")
    print(f"    n_i = {estado.n(iid)}   q_i = {estado.cuota(iid):.3f}   "
          f"B_i = {len(estado.frontera(iid))}   ḡ_i = {estado.dispersion_guarniciones(iid):.2f}   "
          f"Vul_i = {estado.vulnerabilidad(iid):.3f}")
    print(f"    θ^eq = {tasa_equilibrio(estado, iid):.2f}%   "
          f"n^max = {tamano_maximo_sostenible(estado, estado.en_guerra(iid)):.1f} provincias")
    print(f"    estrategia: f_rec={par.f_reclutar} γ_atq={par.gamma_ataque} "
          f"γ_σ={par.gamma_guerra} f_gua={par.f_guarnicion} f_fort={par.f_fortificar}")
    print(f"    δ: " + ", ".join(f"{k}={v.value}" for k, v in sorted(imp.relaciones.items())))
    print(f"    provincias: " + ", ".join(p.id for p in estado.provincias_de(iid)))
    for e in estado.ejercitos_de(iid):
        print(f"    a{e.id}: F={e.fuerza:.2f} u={e.ubicacion} μ={e.moral:.3f} "
              f"μ̄={militar.techo_moral(estado, e):.3f}")


def ver_provincia(estado: Estado, pid: str) -> None:
    p = estado.provincia(pid)
    print(f"\n  {p.nombre} ({p.id}) — terreno {p.terreno.name} "
          f"(ATQ×{p.terreno.ataque} DEF×{p.terreno.defensa} w={p.terreno.coste})")
    print(f"    π_p = {p.propietario or 'NEUTRAL'}   L_p = {p.poblacion:.0f}   "
          f"φ_p = {p.fortificacion}   g_p = {p.guarnicion:.2f}   D_p = {p.descontento:.1f}")
    print(f"    D_p (fuerza defensiva) = {militar.defensa_provincia(estado, p):.2f}   "
          f"I_p (renta) = {renta_provincia(estado, p):.2f}"
          + ("   ⚠ POR ENCIMA DEL UMBRAL FISCAL D*, no tributa"
             if p.descontento >= estado.p.umbral_fiscal else ""))
    print(f"    V_p = {', '.join(sorted(p.adyacentes))}")


def ver_lef(motor: Motor) -> None:
    print(f"\n  LEF: {len(motor.lef)} eventos pendientes "
          f"(procesados {motor.eventos_procesados}, cancelados {motor.eventos_cancelados})")
    print(f"    {'τ':>10} {'π':>3} {'ς':>5}  evento")
    print("    " + "-" * 62)
    for ev in motor.lef.pendientes()[:20]:
        print(f"    {formato_tau(ev.tau):>10} {ev.tipo.prioridad:>3} {ev.secuencia:>5}  "
              f"{ev.tipo.etiqueta} {ev.tipo.name}"
              + (f" {ev.datos}" if ev.datos else ""))


def evaluar_ataque(estado: Estado, origen: str, destino: str) -> None:
    """Muestra el régimen de combate sin consumir aleatorios (Proposición 1)."""
    o, d = estado.provincia(origen), estado.provincia(destino)
    atacantes = [e for e in estado.ejercitos.values() if e.ubicacion == origen]
    if not atacantes:
        print(f"  No hay ningún ejército en {origen}")
        return
    e = atacantes[0]
    defensa = militar.defensa_provincia(estado, d)
    pa = militar.potencia_atacante(estado, e.fuerza, e.moral, d)
    pd = militar.potencia_defensora(estado, defensa, d)
    k = militar.cociente_determinista(estado, e.fuerza, e.moral, defensa, d)
    print(f"\n  a{e.id} ({e.propietario}) F={e.fuerza:.2f} μ={e.moral:.3f} "
          f"desde {origen} → {destino} [{d.terreno.name}]")
    print(f"    P_a^det = {pa:.2f}   P_d^det = {pd:.2f}   (D_p = {defensa:.2f})")
    print(f"    k = P_d/P_a = {k:.4f}   →  régimen {militar.regimen(k)}")
    print("    umbrales de la Proposición 1: k ≤ 2/3 = 0.667 · k ≥ 3/2 = 1.500")
    for cod, par in PARAMETROS_ESTRATEGIA.items():
        decide = "ATACA" if pa >= par.gamma_ataque * pd else "no ataca"
        print(f"      {NOMBRE_ESTRATEGIA[cod]:<12} γ_atq={par.gamma_ataque}: "
              f"exige {par.gamma_ataque * pd:8.2f} → {decide}")
    print("    (no se ha consumido ningún número aleatorio)")


# -------------------------------------------------------------- interactivo

AYUDA = """
  Comandos:
    avanzar [n]              procesa n turnos completos (por defecto 1)
    paso                     procesa un único evento de la LEF
    estado                   resumen de todos los imperios
    ver imperio <id>         detalle de un imperio
    ver provincia <id>       detalle de una provincia
    ver lef                  cola de eventos pendientes
    evaluar <origen> <dest>  régimen de combate, sin consumir aleatorios
    fijar tasa <imp> <v>     θ_i ∈ [0, 150]
    fijar guarnicion <p> <v> g_p ≥ 0
    fijar fuerza <a> <v>     F_a ≥ 0  (a = id de ejército)
    fijar fortificacion <p> <v>   φ_p ∈ {0..4}
    fijar descontento <p> <v>     D_p ∈ [0, 100]
    fijar poblacion <p> <v>       L_p ∈ [0, 20000]
    ayuda / salir
"""


def _fijar(estado: Estado, campo: str, objetivo: str, valor: float) -> None:
    """Entrada de variables con validación de dominio (§5.1)."""
    par = estado.p
    if campo == "tasa":
        if not 0 <= valor <= par.tasa_maxima:
            print(f"  ✗ θ debe estar en [0, {par.tasa_maxima:.0f}]"); return
        estado.imperio(objetivo).tasa = valor
    elif campo == "guarnicion":
        if valor < 0:
            print("  ✗ g_p debe ser ≥ 0"); return
        estado.provincia(objetivo).guarnicion = valor
    elif campo == "fuerza":
        if valor < 0:
            print("  ✗ F_a debe ser ≥ 0"); return
        estado.ejercitos[int(objetivo)].fuerza = valor
    elif campo == "fortificacion":
        if not 0 <= valor <= par.fortificacion_maxima:
            print(f"  ✗ φ_p debe estar en [0, {par.fortificacion_maxima}]"); return
        estado.provincia(objetivo).fortificacion = int(valor)
    elif campo == "descontento":
        if not 0 <= valor <= 100:
            print("  ✗ D_p debe estar en [0, 100]"); return
        estado.provincia(objetivo).descontento = valor
    elif campo == "poblacion":
        if not 0 <= valor <= par.poblacion_maxima:
            print(f"  ✗ L_p debe estar en [0, {par.poblacion_maxima:.0f}]"); return
        estado.provincia(objetivo).poblacion = valor
    else:
        print(f"  ✗ campo desconocido: {campo}"); return
    print(f"  ✓ {campo}({objetivo}) ← {valor}")


def interactivo(estado: Estado, motor: Motor) -> None:
    print("\n" + "=" * 78)
    print(f"  SIMULADOR — {estado.nombre}")
    print(f"  Modelo de eventos discretos del Parcial II · semilla s₀ = {estado.p.semilla}")
    print("=" * 78)
    print(AYUDA)
    ver_estado(estado)

    while True:
        try:
            linea = input(f"\n[τ={formato_tau(estado.tau)}] > ").strip()
        except (EOFError, KeyboardInterrupt):
            print()
            break
        if not linea:
            continue
        partes = linea.split()
        cmd = partes[0].lower()

        try:
            if cmd in ("salir", "exit", "q"):
                break
            elif cmd == "ayuda":
                print(AYUDA)
            elif cmd == "avanzar":
                n = int(partes[1]) if len(partes) > 1 else 1
                motor.rec.verboso = True
                for _ in range(n):
                    if estado.fin_juego:
                        print("  La partida ha terminado.")
                        break
                    motor.avanzar_turno()
                motor.rec.verboso = False
            elif cmd == "paso":
                motor.rec.verboso = True
                ev = motor.paso()
                motor.rec.verboso = False
                print(f"  procesado: {ev}" if ev else "  no quedan eventos")
            elif cmd == "estado":
                ver_estado(estado)
            elif cmd == "ver":
                if partes[1] == "imperio":
                    ver_imperio(estado, partes[2])
                elif partes[1] == "provincia":
                    ver_provincia(estado, partes[2])
                elif partes[1] == "lef":
                    ver_lef(motor)
                else:
                    print("  ✗ ver imperio|provincia|lef")
            elif cmd == "evaluar":
                evaluar_ataque(estado, partes[1], partes[2])
            elif cmd == "fijar":
                _fijar(estado, partes[1], partes[2], float(partes[3]))
            else:
                print(f"  ✗ comando desconocido: {cmd}   (escribe 'ayuda')")
        except (IndexError, ValueError, KeyError) as exc:
            print(f"  ✗ {exc}")


# -------------------------------------------------------------------- tabla

def tabla_parametros() -> None:
    """Emite la tabla de parámetros en Markdown, para el Anexo A del informe."""
    par = Parametros()
    print("| Campo | Valor | Unidad | Procedencia |")
    print("|:--|--:|:--|:--:|")
    for campo, valor in vars(par).items():
        print(f"| `{campo}` | {valor} | {UNIDADES.get(campo, '—')} | "
              f"{PROCEDENCIA.get(campo, '[M]')} |")


# --------------------------------------------------------------------- main

def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(
        prog="python3 -m sim",
        description="Simulador por eventos discretos de Age of Conquest "
                    "(modelo operacional del Parcial II).")
    ap.add_argument("--escenario", type=Path, help="ruta a un escenario JSON")
    ap.add_argument("--semilla", type=int, help="semilla s₀ del generador")
    ap.add_argument("--turnos", type=int, help="ejecuta N turnos mostrando la traza")
    ap.add_argument("--partida", action="store_true", help="partida completa")
    ap.add_argument("--interactivo", action="store_true", help="modo interactivo")
    ap.add_argument("--caso", choices=list(casos_borde.CASOS) + ["todos"],
                    help="demostración de un caso borde")
    ap.add_argument("--lote", type=int, metavar="N", help="N réplicas de la config. base")
    ap.add_argument("--barrido", action="store_true",
                    help="análisis de sensibilidad de K_B (bucle B2)")
    ap.add_argument("--traza-dorada", action="store_true",
                    help="verifica la traza de escritorio de §4.7")
    ap.add_argument("--tabla-parametros", action="store_true",
                    help="emite el Anexo A en Markdown")
    args = ap.parse_args(argv)

    if args.tabla_parametros:
        tabla_parametros()
        return 0

    if args.traza_dorada:
        sys.path.insert(0, str(RAIZ))
        from tests import test_traza_dorada
        return test_traza_dorada.main()

    if args.caso:
        if args.caso == "todos":
            casos_borde.ejecutar_todos()
        else:
            casos_borde.CASOS[args.caso]()
        return 0

    if args.lote:
        resultados = lote.replicas(args.lote)
        print(lote.resumir(resultados, f"Configuración de referencia"))
        ruta = RAIZ / "resultados" / "p3" / "base.csv"
        lote.exportar_csv(resultados, ruta)
        print(f"\n  → {ruta.relative_to(RAIZ)}")
        return 0

    if args.barrido:
        n = 30
        resultados = lote.barrido_k_bajas(n=n)
        print("\n  Análisis de sensibilidad de K_B (bucle B2 de atrición)")
        print("  Semillas apareadas entre variantes: números aleatorios comunes.\n")
        for v in sorted({r.valor for r in resultados}):
            print(lote.resumir([r for r in resultados if r.valor == v], f"K_B = {v}"))
            print()
        ruta = RAIZ / "resultados" / "p3" / "barrido_kb.csv"
        lote.exportar_csv(resultados, ruta)
        print(f"  → {ruta.relative_to(RAIZ)}")
        return 0

    # Modos que necesitan un estado.
    par = Parametros()
    if args.semilla is not None:
        par = par.copia(semilla=args.semilla)
    estado = cargar(args.escenario, par) if args.escenario else cargar_referencia(par)
    rec = Recolector(verboso=True)
    motor = Motor(estado, rec)

    if args.interactivo:
        rec.verboso = False
        interactivo(estado, motor)
        return 0

    turnos = args.turnos if args.turnos else (None if args.partida else 5)
    print("=" * 78)
    print(f"  {estado.nombre} · semilla s₀ = {par.semilla} · "
          f"{'partida completa' if turnos is None else f'{turnos} turnos'}")
    print("=" * 78)
    motor.ejecutar(turnos)
    ver_estado(estado)
    print(f"\n  eventos procesados={motor.eventos_procesados} "
          f"cancelados={motor.eventos_cancelados} "
          f"aleatorios={estado.rng.consumidos} (= 2·ν = {2 * estado.num_combates}) "
          f"| Teorema 1: {motor.violaciones_ventana} violaciones · "
          f"Teorema 2: {motor.violaciones_causalidad} violaciones")
    return 0


if __name__ == "__main__":
    sys.exit(main())
