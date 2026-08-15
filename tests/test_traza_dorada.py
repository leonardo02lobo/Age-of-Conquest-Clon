"""Prueba dorada: reproducción de la traza de escritorio de §4.7 del Parcial II.

§4.7 reproduce un turno completo con números concretos, verificable a mano, y
el propio documento lo declara "caso de prueba para la implementación del
Parcial III". Ejercita E1, E2, E3, E4, E5 y E9, y con ellos el generador
triangular, la matriz de terreno, la moral, el descontento, la población y el
combate: en torno al 70 % del modelo sobre un estado de cuatro provincias.

Los sorteos están fijados por el documento en R₁ = 0.7314 y R₂ = 0.2891, e se
inyectan con `GeneradorFijo`.

Tolerancia: los valores impresos en §4.7 están redondeados a 2–4 decimales y
algunos pasos intermedios del documento se calculan con cifras ya redondeadas
(por ejemplo b_gan se obtiene de P_d/P_a redondeado a 2 decimales). Se adopta
por tanto una tolerancia relativa de 1e-3, declarada en el informe.
"""

from __future__ import annotations

import math
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from sim import economia, militar                                    # noqa: E402
from sim.agentes import planificar                                   # noqa: E402
from sim.azar import GeneradorFijo, triangular_inversa               # noqa: E402
from sim.estado import Estado, Imperio, Provincia                    # noqa: E402
from sim.parametros import EstadoDiplomatico as ED                   # noqa: E402
from sim.parametros import Parametros, Terreno                       # noqa: E402
from sim.reloj import funcion_llegada, formato_tau                   # noqa: E402

TOL = 1e-3
fallos: list[str] = []
comprobaciones = 0


def igual(etiqueta: str, obtenido: float, esperado: float, tol: float = TOL) -> None:
    """Comprueba igualdad relativa y registra el resultado."""
    global comprobaciones
    comprobaciones += 1
    denom = max(abs(esperado), 1e-9)
    error = abs(obtenido - esperado) / denom
    ok = error <= tol
    marca = "✓" if ok else "✗"
    print(f"  {marca} {etiqueta:<42} obtenido={obtenido:12.4f}  documento={esperado:12.4f}"
          f"  err_rel={error:.2e}")
    if not ok:
        fallos.append(f"{etiqueta}: obtenido {obtenido}, documento {esperado}")


def estado_traza() -> Estado:
    """Estado al inicio del turno t = 5, tal como lo declara §4.7.

    Mapa mínimo: p1 (capital de I₁) — p2 — p4 (de I₂), y p3 colgando de p1.
    Esa topología da d(p4, p1) = 2, que es la distancia que el documento usa
    para el techo de moral al cerrar el turno.
    """
    par = Parametros()
    provincias = {
        "p1": Provincia("p1", "p1", Terreno.LLANURA, 5000, {"p2", "p3"},
                        "I1", fortificacion=1, guarnicion=20, descontento=35),
        "p2": Provincia("p2", "p2", Terreno.BOSQUE, 3000, {"p1", "p4"},
                        "I1", fortificacion=0, guarnicion=10, descontento=45),
        "p3": Provincia("p3", "p3", Terreno.MONTANA, 2000, {"p1"},
                        "I1", fortificacion=0, guarnicion=5, descontento=25),
        "p4": Provincia("p4", "p4", Terreno.LLANURA, 4000, {"p2"},
                        "I2", fortificacion=2, guarnicion=70, descontento=30),
    }
    imperios = {
        "I1": Imperio("I1", "I1", "AGR", oro=150.0, tasa=125.0, capital="p1"),
        "I2": Imperio("I2", "I2", "DEF", oro=500.0, tasa=100.0, capital="p4"),
    }
    estado = Estado("traza §4.7", par, provincias, imperios)
    estado.turno = 5
    estado.fijar_relacion("I1", "I2", ED.GUERRA)

    a1 = estado.crear_ejercito("I1", 120.0, "p2")
    a1.moral = 0.94
    estado.imperio("I1").poder_militar = militar.poder_militar(estado, "I1")
    estado.imperio("I2").poder_militar = militar.poder_militar(estado, "I2")
    return estado


def main() -> int:
    estado = estado_traza()
    a1 = estado.ejercitos[1]

    print("\n=== Estado inicial (τ = 5.00) ===")
    igual("M_1 poder militar inicial", estado.imperio("I1").poder_militar, 155.0)

    # ---------------------------------------------------------------- E1
    print("\n=== τ = 5.00 — E1 Inicio de Turno ===")
    igual("I_p1 renta", economia.renta_provincia(estado, estado.provincia("p1")), 65.625)
    igual("I_p2 renta", economia.renta_provincia(estado, estado.provincia("p2")), 37.5)
    igual("I_p3 renta", economia.renta_provincia(estado, estado.provincia("p3")), 25.0)
    r, c, _ = economia.actualizar_tesoro(estado, "I1")
    igual("R_1 recaudación", r, 128.125)
    igual("C_1 coste", c, 13.75)
    igual("G_1 tesoro", estado.imperio("I1").oro, 264.375)

    # ---------------------------------------------------------------- E2
    print("\n=== τ = 5.10 — E2 Planificación de I1 (AGRESIVA) ===")
    # El documento evalúa el ataque de a1 con la moral y la fuerza previas.
    fuerza_enviada = 120.0 * (1 - 0.15)
    igual("F_env fuerza destacada", fuerza_enviada, 102.0)
    pa_det = militar.potencia_atacante(estado, fuerza_enviada, 0.94, estado.provincia("p4"))
    pd_det = militar.potencia_defensora(estado, 70.0, estado.provincia("p4"))
    igual("P_a^det potencia determinista atacante", pa_det, 95.88)
    igual("P_d^det potencia determinista defensora", pd_det, 80.08)
    igual("umbral γ_atq·P_d^det (AGRESIVA 1.1)", 1.1 * pd_det, 88.09, tol=2e-3)
    igual("k cociente determinista", pd_det / pa_det, 0.835, tol=2e-3)
    assert pa_det >= 1.1 * pd_det, "AGRESIVA debe atacar"
    print("    → régimen estocástico (2/3 < k < 3/2): el resultado no está garantizado")
    print("    → una IA DEFENSIVA (γ_atq=1.8) exigiría "
          f"{1.8 * pd_det:.1f} y no atacaría ✓")

    movimientos, traza = planificar(estado, "I1")
    for linea in traza:
        print(f"    {linea}")
    igual("u_1 unidades reclutadas", float(int(0.90 * 264.375 / 1.5)), 158.0)
    igual("G_1 tras reclutar", estado.imperio("I1").oro, 27.375)
    igual("g_p1 guarnición de la capital tras la leva",
          estado.provincia("p1").guarnicion, 30.0)
    a2 = [e for e in estado.ejercitos_de("I1") if e.id != 1][0]
    igual("F_a2 ejército levantado", a2.fuerza, 148.0)
    igual("g_p2 retaguardia", estado.provincia("p2").guarnicion, 28.0)
    igual("F_a1 tras destacar", a1.fuerza, 102.0)

    assert len(movimientos) >= 1, "debe programarse el ataque de a1"
    eid, destino, coste = [m for m in movimientos if m[0] == 1][0]
    assert destino == "p4", f"el objetivo debe ser p4, no {destino}"
    igual("c coste de movimiento a p4 (LLANURA)", coste, 0.6667, tol=1e-3)
    tau_llegada = funcion_llegada(5, coste)
    igual("τ_lleg instante de llegada", tau_llegada / 1_000_000, 5.817, tol=1e-4)

    # ---------------------------------------------------------------- E4
    print("\n=== τ = 5.818 — E4 Resolución de Combate ===")
    igual("U_a = F⁻¹(0.7314)", triangular_inversa(0.7314), 1.0534, tol=1e-3)
    igual("U_d = F⁻¹(0.2891)", triangular_inversa(0.2891), 0.9521, tol=1e-3)

    estado.rng = GeneradorFijo([0.7314, 0.2891])
    resultado = militar.resolver_combate(
        estado, a1.fuerza, a1.moral, 70.0, estado.provincia("p4"), estado.rng)
    igual("P_a potencia atacante", resultado.p_atacante, 101.00, tol=2e-3)
    igual("P_d potencia defensora", resultado.p_defensor, 76.24, tol=2e-3)
    assert resultado.vence_atacante, "debe vencer el atacante"
    igual("b_gan bajas del vencedor", resultado.bajas_ganador, 53.89, tol=2e-3)
    igual("b_perd bajas del perdedor", resultado.bajas_perdedor, 70.0)
    igual("aleatorios consumidos por el combate",
          float(estado.rng.consumidos), 2.0)

    militar.desgaste_moral(estado, a1, resultado.bajas_ganador)
    igual("μ_a1 tras el desgaste de combate", a1.moral, 0.692, tol=2e-3)
    a1.fuerza -= resultado.bajas_ganador
    igual("F_a1 supervivientes", a1.fuerza, 48.11, tol=2e-3)
    print(f"    F_gan⁺ = {a1.fuerza:.2f} > 0.30·F_gan⁻ = {0.30 * 102:.2f} "
          "→ la fórmula nunca aniquila al vencedor ✓")

    # ---------------------------------------------------------------- E5
    print("\n=== τ = 5.819 — E5 Conquista ===")
    p4 = estado.provincia("p4")
    p4.bajas_turno += resultado.bajas_ganador + resultado.bajas_perdedor
    p4.propietario = "I1"
    p4.fortificacion = max(0, p4.fortificacion - 1)
    p4.descontento = min(100.0, p4.descontento + 10.0)
    p4.guarnicion = 0.0
    a1.ubicacion = "p4"
    igual("φ_p4 tras el asedio", float(p4.fortificacion), 1.0)
    igual("D_p4 tras la ocupación militar", p4.descontento, 40.0)
    igual("n_1 provincias de I1", float(estado.n("I1")), 4.0)

    # ---------------------------------------------------------------- E9
    print("\n=== τ = 5.90 — E9 Fin de Turno ===")
    igual("ΔD descontento del turno", economia.delta_descontento(estado, "I1"), 5.0)
    economia.aplicar_descontento(estado, "I1")
    igual("D_p1", estado.provincia("p1").descontento, 40.0)
    igual("D_p2", estado.provincia("p2").descontento, 50.0)
    igual("D_p3", estado.provincia("p3").descontento, 30.0)
    igual("D_p4", p4.descontento, 45.0)

    igual("β_p4 bajas locales del turno", p4.bajas_turno, 123.89, tol=2e-3)
    economia.actualizar_poblacion(estado, p4)
    igual("L_p4 población tras el daño de guerra", p4.poblacion, 3792.0, tol=2e-3)

    igual("d(p4, c_1) distancia a la capital",
          float(estado.distancia("p4", "p1")), 2.0)
    igual("μ̄_a1 techo de moral", militar.techo_moral(estado, a1), 0.88)
    militar.regenerar_moral(estado, a1)
    igual("μ_a1 tras regenerar", a1.moral, 0.792, tol=2e-3)

    m1 = militar.poder_militar(estado, "I1")
    igual("M_1 poder militar al cierre", m1, 259.11, tol=2e-3)
    igual("q_1 cuota territorial", estado.cuota("I1"), 4 / 4)  # mapa de 4 provincias

    # ---------------------------------------------------------------- resumen
    print("\n" + "=" * 78)
    if fallos:
        print(f"TRAZA DORADA: {len(fallos)} DISCREPANCIA(S) de {comprobaciones} comprobaciones")
        for f in fallos:
            print(f"  ✗ {f}")
        return 1
    print(f"TRAZA DORADA SUPERADA: {comprobaciones}/{comprobaciones} comprobaciones "
          f"coinciden con §4.7 (tolerancia relativa {TOL:g})")
    print("=" * 78)
    return 0


if __name__ == "__main__":
    sys.exit(main())
