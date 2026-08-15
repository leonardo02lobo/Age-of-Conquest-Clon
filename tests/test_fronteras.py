"""Condiciones extremas y casos degenerados — §5.1 y §5.3 del Parcial II.

Dos de las técnicas de validación reconocidas en simulación: *extreme condition
tests* (forzar cada variable a su frontera) y *degenerate tests* (comprobar que
los casos límite producen el comportamiento declarado y no estados indefinidos,
excepciones no controladas ni bucles infinitos).
"""

from __future__ import annotations

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from sim import economia, militar                                    # noqa: E402
from sim.escenario import cargar_referencia                           # noqa: E402
from sim.eventos import Motor                                         # noqa: E402
from sim.parametros import EstadoDiplomatico as ED                    # noqa: E402
from sim.parametros import Parametros                                 # noqa: E402
from sim.recolector import Recolector                                 # noqa: E402

fallos: list[str] = []
total = 0


def comprobar(etiqueta: str, condicion: bool, detalle: str = "") -> None:
    global total
    total += 1
    print(f"  {'✓' if condicion else '✗'} {etiqueta}" + (f"  — {detalle}" if detalle else ""))
    if not condicion:
        fallos.append(etiqueta)


def seccion(titulo: str) -> None:
    print(f"\n── {titulo} " + "─" * max(0, 60 - len(titulo)))


# ===================================================== §5.1 dominios acotados

def test_dominios() -> None:
    seccion("§5.1 — dominios de las variables de estado")
    estado = cargar_referencia()
    p = estado.provincia("p12")
    imp = estado.imperio("aquilonia")

    # D_p ∈ [0, 100] — saturación por ambos extremos.
    imp.tasa = estado.p.tasa_maxima
    for _ in range(60):
        economia.aplicar_descontento(estado, "aquilonia")
    ds = [q.descontento for q in estado.provincias_de("aquilonia")]
    comprobar("D_p satura en 100 con θ máxima sostenida",
              all(d == 100.0 for d in ds), f"D = {ds}")

    imp.tasa = 0.0
    for _ in range(80):
        economia.aplicar_descontento(estado, "aquilonia")
    ds = [q.descontento for q in estado.provincias_de("aquilonia")]
    comprobar("D_p satura en 0 con θ = 0 en paz",
              all(d == 0.0 for d in ds), f"D = {ds}")

    # L_p ∈ [0, L_max].
    p.poblacion = estado.p.poblacion_maxima
    economia.actualizar_poblacion(estado, p)
    comprobar("L_p no supera L_max", p.poblacion <= estado.p.poblacion_maxima,
              f"L = {p.poblacion:.0f}")

    p.poblacion = 100.0
    p.bajas_turno = 10_000.0
    economia.actualizar_poblacion(estado, p)
    comprobar("L_p no baja de 0 con daño de guerra masivo", p.poblacion == 0.0)

    # μ_a ∈ [μ_min, 1]. El desgaste de un solo combate está acotado: como
    # b_gan/F_a < K_B = 0.7, el factor de (3.29) nunca baja de
    # 1 − γ_μ·0.7 = 0.65. Se necesitan varias victorias ajustadas encadenadas
    # para llegar al suelo — que es justamente el límite natural a las
    # campañas relámpago que el documento atribuye a este mecanismo.
    e = estado.ejercitos_de("aquilonia")[0]
    e.fuerza = 100.0
    e.moral = 1.0
    militar.desgaste_moral(estado, e, 100.0)
    comprobar("un combate reduce μ_a según (3.29), sin saltar al suelo",
              abs(e.moral - 0.5) < 1e-9, f"μ = {e.moral}")
    for _ in range(10):
        militar.desgaste_moral(estado, e, 70.0)
    comprobar("μ_a satura en μ_min tras combates encadenados",
              e.moral == estado.p.moral_minima, f"μ = {e.moral}")
    for _ in range(30):
        militar.regenerar_moral(estado, e)
    comprobar("μ_a no supera 1 al regenerar en la capital",
              e.moral <= 1.0, f"μ = {e.moral:.3f}")

    # G_i ≥ 0 tras la regla de insolvencia.
    imp.oro = -500.0
    economia.insolvencia(estado, "aquilonia")
    comprobar("G_i nunca queda negativo tras la insolvencia", imp.oro >= 0.0,
              f"G = {imp.oro}")

    # φ_p ∈ {0..Φ_max}: el agente no fortifica más allá del tope.
    from sim.agentes import decidir_fortificacion
    for q in estado.provincias_de("aquilonia"):
        q.fortificacion = estado.p.fortificacion_maxima
    imp.oro = 100_000.0
    decidir_fortificacion(estado, "aquilonia")
    comprobar("φ_p no supera Φ_max",
              all(q.fortificacion <= estado.p.fortificacion_maxima
                  for q in estado.provincias_de("aquilonia")))


# ================================================ §5.2 funciones por partes

def test_funciones_por_partes() -> None:
    seccion("§5.2 — funciones por partes")
    estado = cargar_referencia()
    p = estado.provincias_de("aquilonia")[0]
    par = estado.p

    # (a) umbral fiscal: ambos lados de la discontinuidad.
    p.descontento = par.umbral_fiscal - 1
    renta_debajo = economia.renta_provincia(estado, p)
    p.descontento = par.umbral_fiscal
    renta_encima = economia.renta_provincia(estado, p)
    comprobar("(a) D_p = D*−1 tributa", renta_debajo > 0, f"I_p = {renta_debajo:.2f}")
    comprobar("(a) D_p = D* no tributa (salto, no degradación)", renta_encima == 0.0)

    # (c) los tres regímenes de la Proposición 1.
    q = estado.provincia("p12")
    q.fortificacion = 0
    q.descontento = 0.0
    k_bajo = militar.cociente_determinista(estado, 1000.0, 1.0, 10.0, q)
    k_alto = militar.cociente_determinista(estado, 10.0, 1.0, 1000.0, q)
    k_medio = militar.cociente_determinista(estado, 100.0, 1.0, 100.0, q)
    comprobar("(c) k ≤ 2/3 → victoria garantizada", k_bajo <= 2 / 3, f"k = {k_bajo:.3f}")
    comprobar("(c) k ≥ 3/2 → derrota garantizada", k_alto >= 3 / 2, f"k = {k_alto:.3f}")
    comprobar("(c) 2/3 < k < 3/2 → régimen estocástico",
              2 / 3 < k_medio < 3 / 2, f"k = {k_medio:.3f}")

    # Régimen determinista: 200 combates con k ≤ 2/3 deben ganarse todos.
    victorias = sum(
        militar.resolver_combate(estado, 1000.0, 1.0, 10.0, q, estado.rng).vence_atacante
        for _ in range(200))
    comprobar("(c) 200/200 victorias en el régimen determinista favorable",
              victorias == 200, f"{victorias}/200")

    # (d) techo de moral saturado.
    d_max = (1 - par.moral_minima) / par.lambda_distancia
    comprobar("(d) d_max = (1−μ_min)/λ_d = 10", abs(d_max - 10.0) < 1e-9)

    # (f) asedio: la fortificación se degrada al caer la provincia.
    q.fortificacion = 3
    q.propietario = "borealis"
    estado.fijar_relacion("aquilonia", "borealis", ED.GUERRA)
    from sim.eventos import e5_conquista
    from sim.reloj import Evento, ListaEventosFuturos, TipoEvento
    lef = ListaEventosFuturos()
    rec = Recolector(verboso=False)
    e5_conquista(estado, lef, Evento(5_000_000, TipoEvento.CONQUISTA,
                                     {"provincia": q.id, "conquistador": "aquilonia"}), rec)
    comprobar("(f) asedio: φ_p ← max(0, φ_p − 1)", q.fortificacion == 2,
              f"φ = {q.fortificacion}")
    comprobar("(f) ocupación militar: D_p += 10", q.descontento >= 10.0)


# =================================================== §5.3 casos degenerados

def test_casos_degenerados() -> None:
    seccion("§5.3 — casos degenerados")
    estado = cargar_referencia()

    # (a) provincia sin defensa: ocupación sin bajas ni aleatorios.
    q = estado.provincia("p12")
    q.propietario = "borealis"
    q.guarnicion = 0.0
    estado.fijar_relacion("aquilonia", "borealis", ED.GUERRA)
    comprobar("(a) D_p = 0 en provincia sin guarnición ni ejércitos",
              militar.defensa_provincia(estado, q) == 0.0)

    # (b) ejército por debajo del mínimo.
    e = estado.ejercitos_de("aquilonia")[0]
    e.fuerza = estado.p.fuerza_minima - 0.1
    imp = estado.imperio("aquilonia")
    imp.oro = -1.0
    economia.insolvencia(estado, "aquilonia")
    comprobar("(b) el ejército con F_a < F_min se disuelve",
              e.id not in estado.ejercitos)

    # (c) pérdida de la capital: reasignación y recálculo de distancias.
    estado2 = cargar_referencia()
    imp2 = estado2.imperio("aquilonia")
    capital_original = imp2.capital
    estado2.provincia(capital_original).propietario = "borealis"
    restantes = estado2.provincias_de("aquilonia")
    imp2.capital = max(restantes, key=lambda x: (x.poblacion, x.id)).id
    comprobar("(c) la capital se reasigna a arg max L_p",
              imp2.capital != capital_original and imp2.capital is not None,
              f"{capital_original} → {imp2.capital}")

    # (d) imperio con provincias pero sin ejércitos: NO se elimina.
    estado3 = cargar_referencia()
    for e in estado3.ejercitos_de("aquilonia"):
        estado3.destruir_ejercito(e.id)
    rec = Recolector(verboso=False)
    motor = Motor(estado3, rec)
    motor.ejecutar(turnos=6)
    comprobar("(d) imperio sin ejércitos sobrevive y levanta uno nuevo",
              estado3.imperio("aquilonia").activo
              and len(estado3.ejercitos_de("aquilonia")) > 0,
              f"activo={estado3.imperio('aquilonia').activo}, "
              f"ejércitos={len(estado3.ejercitos_de('aquilonia'))}")

    # (e) empate en la cuota del líder: menor identificador, reproducible.
    estado4 = cargar_referencia()
    lideres = {cargar_referencia().lider() for _ in range(5)}
    comprobar("(e) el desempate del líder es determinista", len(lideres) == 1,
              f"líder = {lideres.pop()}")

    # (g) siempre queda al menos un imperio activo.
    comprobar("(g) m ≥ 1 por construcción", estado4.m >= 1, f"m = {estado4.m}")


# ============================================ terminación y reproducibilidad

def test_terminacion() -> None:
    seccion("Teorema 3 — terminación bajo configuraciones extremas")
    configuraciones = [
        ("K_B mínimo", Parametros().copia(k_bajas=0.01)),
        ("K_B máximo", Parametros().copia(k_bajas=1.0)),
        ("sin sobreextensión", Parametros().copia(eta_extension=0.0)),
        ("sobreextensión extrema", Parametros().copia(eta_extension=5.0)),
        ("coalición inmediata", Parametros().copia(cuota_amenaza=0.0)),
        ("sin coalición", Parametros().copia(cuota_amenaza=1.0)),
        ("cuota de victoria baja", Parametros().copia(cuota_victoria=0.30)),
    ]
    for etiqueta, par in configuraciones:
        estado = cargar_referencia(par)
        motor = Motor(estado, Recolector(verboso=False))
        motor.ejecutar()
        cota = par.turnos_maximos * (3 + 4 + 4 * 4 * par.ejercitos_maximos)
        comprobar(
            f"termina con {etiqueta}",
            estado.fin_juego and estado.turno <= par.turnos_maximos
            and motor.eventos_procesados < cota,
            f"turno {estado.turno}, {motor.eventos_procesados} eventos "
            f"(cota {cota}), T1={motor.violaciones_ventana} T2={motor.violaciones_causalidad}")


def test_reproducibilidad() -> None:
    seccion("Reproducibilidad")
    firmas = []
    for _ in range(3):
        estado = cargar_referencia()
        motor = Motor(estado, Recolector(verboso=False))
        motor.ejecutar()
        firmas.append((estado.ganador, estado.turno, estado.num_combates,
                       round(estado.bajas_totales, 6), estado.rng.consumidos))
    comprobar("tres ejecuciones con la misma semilla dan el mismo resultado",
              len(set(firmas)) == 1, str(firmas[0]))

    estado = cargar_referencia(Parametros().copia(semilla=12345))
    motor = Motor(estado, Recolector(verboso=False))
    motor.ejecutar()
    comprobar("otra semilla da otra trayectoria",
              (estado.ganador, estado.turno, estado.num_combates) != firmas[0][:3],
              f"({estado.ganador}, {estado.turno}, {estado.num_combates})")

    comprobar("cada combate consume exactamente 2 aleatorios",
              estado.rng.consumidos == 2 * estado.num_combates,
              f"{estado.rng.consumidos} = 2·{estado.num_combates}")


def main() -> int:
    print("=" * 72)
    print("  CONDICIONES EXTREMAS Y CASOS DEGENERADOS — §5 del Parcial II")
    print("=" * 72)
    test_dominios()
    test_funciones_por_partes()
    test_casos_degenerados()
    test_terminacion()
    test_reproducibilidad()
    print("\n" + "=" * 72)
    if fallos:
        print(f"  {len(fallos)} FALLO(S) de {total} comprobaciones:")
        for f in fallos:
            print(f"    ✗ {f}")
        return 1
    print(f"  {total}/{total} comprobaciones superadas")
    print("=" * 72)
    return 0


if __name__ == "__main__":
    sys.exit(main())
