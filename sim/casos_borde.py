"""Análisis de casos borde — §5 del Parcial II y requisito del enunciado.

El enunciado del Parcial III pide explícitamente explicar "qué ocurre en el
simulador ante situaciones extremas (ej. impuestos al máximo, moral en cero,
bancarrota)". Cada caso construye un estado que fuerza la frontera y ejecuta
la simulación mostrando la trayectoria resultante.
"""

from __future__ import annotations

from . import economia, militar
from .escenario import cargar_referencia
from .estado import Estado
from .eventos import Motor
from .parametros import EstadoDiplomatico as ED
from .parametros import Parametros
from .recolector import Recolector


def _cabecera(titulo: str, referencia: str) -> None:
    print("\n" + "=" * 78)
    print(f"  CASO BORDE: {titulo}")
    print(f"  Referencia del modelo: {referencia}")
    print("=" * 78)


def impuestos_al_maximo(turnos: int = 20) -> None:
    """θ = θ_max = 150 % sostenido — frontera superior de θ_i, §5.1 y §5.2(a).

    El descontento crece a ΔD = η_θ(150−50) + … − η_r = +4.5 puntos/turno en
    paz. Al cruzar D* = 60 la renta cae **de golpe** a cero (ec. 5.1), no de
    forma gradual, y se abre el lazo de realimentación positiva destructivo:
    D_p ≥ D* ⟹ I_p = 0 ⟹ G_i ↓ ⟹ menos tropas ⟹ más derrotas ⟹ ΔD_p ↑
    """
    _cabecera("IMPUESTOS AL MÁXIMO (θ = 150 %)", "§5.1 dominio de θ_i · §5.2(a) umbral fiscal")
    estado = cargar_referencia()
    rec = Recolector(verboso=False)
    motor = Motor(estado, rec)
    objetivo = "aquilonia"

    print(f"\n  ΔD teórico en paz = η_θ(150−50) − η_r = "
          f"{estado.p.eta_fiscal * 100 - estado.p.eta_recuperacion:+.1f} puntos/turno")
    print(f"  Umbral fiscal D* = {estado.p.umbral_fiscal:.0f}, "
          f"descontento inicial D⁰ = {estado.p.descontento_inicial:.0f}")
    print(f"\n  {'turno':>5} {'θ':>7} {'D medio':>9} {'prov>D*':>8} {'renta':>9} {'oro':>10} {'M':>9}")
    print("  " + "-" * 62)

    cruce = None
    for t in range(turnos):
        estado.imperio(objetivo).tasa = estado.p.tasa_maxima
        motor.avanzar_turno()
        if estado.fin_juego:
            break
        # La política del agente vuelve a fijar θ cada turno; se sobrescribe.
        estado.imperio(objetivo).tasa = estado.p.tasa_maxima
        provincias = estado.provincias_de(objetivo)
        if not provincias:
            print(f"  {estado.turno:>5}  (imperio sin provincias)")
            break
        d_medio = sum(p.descontento for p in provincias) / len(provincias)
        secas = sum(1 for p in provincias if p.descontento >= estado.p.umbral_fiscal)
        renta = economia.recaudacion(estado, objetivo)
        imp = estado.imperio(objetivo)
        if secas and cruce is None:
            cruce = estado.turno
        print(f"  {estado.turno:>5} {imp.tasa:>7.0f} {d_medio:>9.1f} "
              f"{secas:>4}/{len(provincias):<3} {renta:>9.2f} {imp.oro:>10.2f} "
              f"{imp.poder_militar:>9.1f}")

    print(f"\n  → Primera provincia que deja de tributar: turno {cruce}")
    print("  → El salto de renta a cero es una discontinuidad deliberada, no una")
    print("     degradación: la provincia pasa de tributar íntegramente a nada.")
    t_rec = int(-(-40 // (estado.p.eta_fiscal * estado.p.tasa_neutra + estado.p.eta_recuperacion)))
    print(f"  → Tiempo de recuperación desde D=100 en el mejor caso (paz, θ=0): "
          f"{t_rec} turnos sin recaudar")


def bancarrota(turnos: int = 15) -> None:
    """Déficit sostenido — frontera inferior de G_i, §5.1 y §5.2(b).

    G_i < 0 ⟹ G_i ← 0 y desertan ΔM_i = ⌈|G_i|/c_up⌉ unidades. La Proposición 3
    garantiza que la regla no oscila: el gasto del turno siguiente se reduce en
    al menos el déficit incurrido.
    """
    _cabecera("BANCARROTA (déficit sostenido)", "§5.1 dominio de G_i · §5.2(b) insolvencia")
    par = Parametros()
    estado = cargar_referencia(par)
    objetivo = "aquilonia"
    imp = estado.imperio(objetivo)

    # Se fuerza el déficit: tasa 0 (renta nula) y un ejército desproporcionado.
    imp.tasa = 0.0
    estado.crear_ejercito(objetivo, 2000.0, imp.capital)
    imp.poder_militar = militar.poder_militar(estado, objetivo)

    print(f"\n  Estado forzado: θ = 0 (renta nula), M = {imp.poder_militar:.0f} unidades")
    print(f"  Coste de mantenimiento = c_adm·n + c_up·M = "
          f"{economia.coste_mantenimiento(estado, objetivo):.2f} oro/turno")
    print(f"\n  {'turno':>5} {'renta':>9} {'coste':>9} {'neto':>10} {'oro':>8} "
          f"{'desertan':>9} {'M':>10}")
    print("  " + "-" * 66)

    for _ in range(turnos):
        imp.tasa = 0.0
        imp.poder_militar = militar.poder_militar(estado, objetivo)
        r = economia.recaudacion(estado, objetivo)
        c = economia.coste_mantenimiento(estado, objetivo)
        imp.oro += r - c
        neto = r - c
        oro_previo = imp.oro
        desertado = economia.insolvencia(estado, objetivo)
        m = militar.poder_militar(estado, objetivo)
        print(f"  {estado.turno:>5} {r:>9.2f} {c:>9.2f} {neto:>+10.2f} "
              f"{imp.oro:>8.2f} {desertado:>9.0f} {m:>10.1f}")
        estado.turno += 1
        if m <= 0:
            break

    print("\n  → Las tropas impagadas desertan en orden determinista: primero los")
    print("     ejércitos más alejados de la capital, luego por menor moral, luego")
    print("     por menor id; las guarniciones desertan en último lugar.")
    print("  → Proposición 3: la regla no oscila, el imperio retorna a solvencia.")
    print("  → Caso terminal: si R_i < c_adm·n_i, ninguna deserción resuelve el")
    print("     déficit; M_i → 0 y el imperio subsiste sin ejército hasta ser")
    print("     conquistado. Es la vía de derrota puramente económica del modelo.")


def moral_minima() -> None:
    """μ_a saturada en μ_min = 0.40 — §5.1 y §5.2(d).

    El techo de moral μ̄_a = max(μ_min, 1 − λ_d·d) satura a
    d_max = (1−μ_min)/λ_d = 10 provincias. Define un radio de operación
    efectivo: más allá, un ejército opera permanentemente al 40 %.
    """
    _cabecera("MORAL EN EL MÍNIMO (μ = 0.40)", "§5.1 dominio de μ_a · §5.2(d) proyección de fuerza")
    estado = cargar_referencia()
    par = estado.p
    d_max = (1 - par.moral_minima) / par.lambda_distancia

    print(f"\n  μ̄_a = max({par.moral_minima}, 1 − {par.lambda_distancia}·d)"
          f"  →  satura en d_max = {d_max:.0f} provincias")
    # Defensor de referencia: llanura con D_p = 70, φ = 2 y descontento 60.
    p = estado.provincia("p12")
    p.fortificacion = 2
    p.descontento = 60.0
    defensa_ref = 70.0
    pd_ref = militar.potencia_defensora(estado, defensa_ref, p)
    print(f"\n  Defensor de referencia: LLANURA, D_p={defensa_ref:.0f}, φ=2, D={p.descontento:.0f}"
          f"  →  P_d = {pd_ref:.2f}")
    print(f"\n  {'d':>3} {'μ̄_a':>8} {'P_a con F=100':>15} {'k = P_d/P_a':>13}  régimen")
    print("  " + "-" * 76)

    for d in range(0, 13):
        techo = max(par.moral_minima, 1 - par.lambda_distancia * d)
        pa = militar.potencia_atacante(estado, 100.0, techo, p)
        k = pd_ref / pa if pa else float("inf")
        marca = "  ← saturado" if techo <= par.moral_minima else ""
        print(f"  {d:>3} {techo:>8.3f} {pa:>15.2f} {k:>13.3f}  "
              f"{militar.regimen(k)}{marca}")

    print("\n  → Combinado con el criterio de ataque P_a^det ≥ γ_atq·P_d^det, el techo")
    print("     define un ALCANCE MÁXIMO DE CONQUISTA por campaña: para seguir")
    print("     avanzando, un imperio debe mover su capital —lo que solo ocurre si la")
    print("     pierde— o consolidar y aceptar el techo. Es el mecanismo B1c.")
    print("  → La moral nunca llega a cero: el dominio es [μ_min, 1] = [0.40, 1.00].")
    print("     Un μ = 0 haría P_a = 0 y todo ataque imposible; el suelo lo impide.")


def provincia_indefensa() -> None:
    """D_p = 0 — caso degenerado (a) de §5.3: ocupación sin bajas."""
    _cabecera("PROVINCIA SIN DEFENSA (D_p = 0)", "§5.3(a) caso degenerado")
    estado = cargar_referencia()
    p = estado.provincia("p12")
    p.propietario = "borealis"
    p.guarnicion = 0.0
    estado.fijar_relacion("aquilonia", "borealis", ED.GUERRA)

    defensa = militar.defensa_provincia(estado, p)
    print(f"\n  Provincia {p.id}: propietario={p.propietario}, g_p={p.guarnicion}, "
          f"D_p = {defensa}")
    print(f"  P_d = {militar.potencia_defensora(estado, defensa, p):.2f}")
    print(f"  Aleatorios consumidos antes: {estado.rng.consumidos}")
    print("\n  → P_a > P_d con probabilidad 1: se resuelve por E5 directamente,")
    print("     SIN crear entidad Combate y SIN consumir aleatorios.")
    print("  → Consistente con la ley lineal: b_gan = K_B·(m_d/m_a)·0 = 0.")
    print("     Ocupación sin bajas.")


def perdida_capital() -> None:
    """Pérdida de la capital — caso degenerado (c) de §5.3."""
    _cabecera("PÉRDIDA DE LA CAPITAL", "§5.3(c) caso degenerado")
    estado = cargar_referencia()
    iid = "aquilonia"
    imp = estado.imperio(iid)
    ejercito = estado.ejercitos_de(iid)[0]
    # Se aleja el ejército para que el cambio de capital sea visible.
    ejercito.ubicacion = "p10"

    print(f"\n  Capital original: {imp.capital}")
    print(f"  Ejército a{ejercito.id} en {ejercito.ubicacion}: "
          f"d = {estado.distancia(ejercito.ubicacion, imp.capital)}, "
          f"μ̄ = {militar.techo_moral(estado, ejercito):.3f}")

    perdida = imp.capital
    estado.provincia(perdida).propietario = "borealis"
    restantes = estado.provincias_de(iid)
    imp.capital = max(restantes, key=lambda x: (x.poblacion, x.id)).id

    print(f"\n  {perdida} es conquistada → capital reasignada a "
          f"{imp.capital} (arg max L_p, desempate por menor id)")
    print(f"  Ejército a{ejercito.id}: d = "
          f"{estado.distancia(ejercito.ubicacion, imp.capital)}, "
          f"μ̄ = {militar.techo_moral(estado, ejercito):.3f}")
    print("\n  → Al cambiar la capital cambian TODAS las distancias d(u_a, c_i) y")
    print("     por tanto todos los techos de moral. Perder la capital reconfigura")
    print("     instantáneamente el alcance operativo del imperio.")
    print("  → Si P_i = ∅, entonces c_i ← ∅ y se dispara E6 Eliminación.")


CASOS = {
    "impuestos": impuestos_al_maximo,
    "bancarrota": bancarrota,
    "moral": moral_minima,
    "indefensa": provincia_indefensa,
    "capital": perdida_capital,
}


def ejecutar_todos() -> None:
    for fn in CASOS.values():
        fn()
