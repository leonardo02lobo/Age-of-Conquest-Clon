"""Subsistema militar y de movimiento — §3.3, §3.4 y §3.6 del Parcial II.

Poder militar, fuerza defensiva, coste de movimiento, reclutamiento,
resolución de combate y dinámica de la moral.
"""

from __future__ import annotations

from dataclasses import dataclass

from .azar import triangular_inversa
from .estado import Estado, Provincia, clamp


# ------------------------------------------------------- fuerza y defensa

def poder_militar(estado: Estado, iid: str) -> float:
    """M_i = Σ_a F_a + Σ_p g_p — ec. (3.12).

    Incluye las guarniciones, que también son fuerza sostenida por el tesoro y
    por tanto computan en el mantenimiento (3.3). Cada fuerza se cuenta
    exactamente una vez (reparación del defecto D8).
    """
    return (
        sum(e.fuerza for e in estado.ejercitos_de(iid))
        + sum(p.guarnicion for p in estado.provincias_de(iid))
    )


def defensa_provincia(estado: Estado, p: Provincia) -> float:
    """D_p = g_p + Σ_{a: u_a=p, π_a=π_p} F_a — ec. (3.12b).

    Guarnición estacionada más los ejércitos propios que se encuentren en la
    provincia. Ambos la defienden; el ejército no se disuelve al llegar.
    """
    total = p.guarnicion
    for e in estado.ejercitos.values():
        if e.ubicacion == p.id and e.propietario == p.propietario:
            total += e.fuerza
    return total


def repartir_bajas(estado: Estado, p: Provincia, bajas: float) -> None:
    """Reparto proporcional de las bajas del defensor vencedor — ec. (3.12c).

        Δg_p = b_gan·g_p/D_p,   ΔF_a = b_gan·F_a/D_p
    """
    total = defensa_provincia(estado, p)
    if total <= 0:
        return
    fraccion = bajas / total
    p.guarnicion = max(0.0, p.guarnicion - p.guarnicion * fraccion)
    for e in list(estado.ejercitos.values()):
        if e.ubicacion == p.id and e.propietario == p.propietario:
            e.fuerza = max(0.0, e.fuerza - e.fuerza * fraccion)
            if e.fuerza < estado.p.fuerza_minima:
                estado.destruir_ejercito(e.id)  # §5.3(b)


def destruir_defensa(estado: Estado, p: Provincia) -> None:
    """La defensa vencida se destruye por completo — ec. (3.20)."""
    p.guarnicion = 0.0
    for e in list(estado.ejercitos.values()):
        if e.ubicacion == p.id and e.propietario == p.propietario:
            estado.destruir_ejercito(e.id)


# ------------------------------------------------------------- movimiento

def coste_movimiento(estado: Estado, destino: Provincia) -> float:
    """c(a, p→q) = w(T_q) / v_a — ec. (3.13), definición de F₄. En turnos.

    Con v_a = 1.5 solo el avance por LLANURA (c = 0.667) se resuelve dentro
    del turno en que se ordena; COSTA (0.800), BOSQUE (0.933) y MONTAÑA
    (1.333) superan Δ = 0.746 y deben programarse como evento futuro. Es la
    justificación material de la existencia de la LEF.
    """
    return destino.terreno.coste / estado.p.puntos_movimiento


# ----------------------------------------------------------- reclutamiento

def reclutar(estado: Estado, iid: str, fraccion: float) -> int:
    """u_i = ⌊f_rec·G_i / c_u⌋ — ec. (3.11), definición de F₂.

    Las tropas nacen en la guarnición de la capital y el coste se descuenta
    del tesoro. Devuelve las unidades reclutadas.
    """
    imp = estado.imperio(iid)
    par = estado.p
    unidades = int(fraccion * imp.oro / par.coste_unidad)
    if unidades < 1 or imp.capital is None:
        return 0
    imp.oro -= par.coste_unidad * unidades
    estado.provincia(imp.capital).guarnicion += unidades
    return unidades


# ------------------------------------------------------------------ combate

def factor_fortificacion(estado: Estado, nivel: int) -> float:
    """Φ(φ) = 1 + β_F·φ — ec. (3.16), definición de F₈.

    Forma lineal: cada nivel añade un anillo defensivo de eficacia constante.
    Con Φ_max = 4 el rango es [1.00, 1.60], lo que impide posiciones
    inexpugnables.
    """
    return 1.0 + estado.p.beta_defensa * nivel


def factor_respaldo_civil(estado: Estado, descontento: float) -> float:
    """Ψ(D) = 1 − ψ·D/100 — ec. (3.17), refinamiento O2.

    Una provincia descontenta defiende peor. Ψ desempeña para la guarnición el
    papel que μ_a desempeña para el ejército invasor, y evita que D_p sea una
    variable de efecto puramente contable. Rango [0.60, 1.00].
    """
    return 1.0 - estado.p.psi * descontento / 100.0


def potencia_atacante(estado: Estado, fuerza: float, moral: float,
                      p: Provincia, u: float = 1.0) -> float:
    """P_a = F_a·μ_a·T(T_p, ATQ)·U_a — ec. (3.14)."""
    return fuerza * moral * p.terreno.ataque * u


def potencia_defensora(estado: Estado, defensa: float, p: Provincia,
                       u: float = 1.0) -> float:
    """P_d = D_p·Φ(φ_p)·T(T_p, DEF)·Ψ(D_p)·U_d — ec. (3.15)."""
    return (
        defensa
        * factor_fortificacion(estado, p.fortificacion)
        * p.terreno.defensa
        * factor_respaldo_civil(estado, p.descontento)
        * u
    )


def cociente_determinista(estado: Estado, fuerza: float, moral: float,
                          defensa: float, p: Provincia) -> float:
    """k = P_d^det / P_a^det, el cociente evaluado con U = 1 — §3.4.2.

    Proposición 1 (regímenes del combate), consecuencia directa del soporte
    acotado de la triangular, U_a/U_d ∈ [2/3, 3/2]:

        k ≤ 2/3          → el atacante vence con probabilidad 1
        2/3 < k < 3/2    → régimen estocástico (k = 1 ⟹ Pr = 1/2)
        k ≥ 3/2          → el atacante pierde con probabilidad 1
    """
    pa = potencia_atacante(estado, fuerza, moral, p)
    if pa <= 0:
        return float("inf")
    return potencia_defensora(estado, defensa, p) / pa


def regimen(k: float) -> str:
    """Etiqueta del régimen de combate según la Proposición 1."""
    if k <= 2 / 3:
        return "determinista (victoria garantizada)"
    if k >= 3 / 2:
        return "determinista (derrota garantizada)"
    return "estocástico"


@dataclass
class ResultadoCombate:
    """Desenlace de un evento E4, para la traza y el recolector."""

    vence_atacante: bool
    u_atacante: float
    u_defensor: float
    p_atacante: float
    p_defensor: float
    bajas_ganador: float
    bajas_perdedor: float


def resolver_combate(estado: Estado, fuerza_atacante: float, moral_atacante: float,
                     defensa: float, p: Provincia, rng) -> ResultadoCombate:
    """Resolución de un combate — ecs. (3.14)–(3.21). Consume dos aleatorios.

    Vence el atacante ⟺ P_a > P_d; el **empate exacto lo retiene el defensor**
    (3.18). Las bajas del vencedor son

        b_gan = F_gan·(P_perd/P_gan)·K_B                            (3.19)

    y el bando derrotado se destruye por completo (3.20). Como en el vencedor
    P_perd/P_gan < 1 y K_B = 0.70 < 1, se cumple F_gan⁺ > 0.30·F_gan⁻: la
    fórmula nunca aniquila al vencedor.

    Relación con Lanchester (§3.4.4): b_gan = K_B·(m_d/m_a)·D_p es
    proporcional a la fuerza inicial del perdedor e independiente de la propia
    — la firma de la **ley lineal**, apropiada para el combate premoderno donde
    solo las unidades en contacto con la línea de frente combaten.
    """
    par = estado.p
    u_a = triangular_inversa(rng.uniforme(), par.tri_a, par.tri_c, par.tri_b)
    u_d = triangular_inversa(rng.uniforme(), par.tri_a, par.tri_c, par.tri_b)

    pa = potencia_atacante(estado, fuerza_atacante, moral_atacante, p, u_a)
    pd = potencia_defensora(estado, defensa, p, u_d)

    if pa > pd:
        bajas_ganador = fuerza_atacante * (pd / pa) * par.k_bajas
        return ResultadoCombate(True, u_a, u_d, pa, pd, bajas_ganador, defensa)

    bajas_ganador = defensa * (pa / pd) * par.k_bajas if pd > 0 else 0.0
    return ResultadoCombate(False, u_a, u_d, pa, pd, bajas_ganador, fuerza_atacante)


# -------------------------------------------------------------------- moral

def techo_moral(estado: Estado, ejercito) -> float:
    """μ̄_a = max(μ_min, 1 − λ_d·d(u_a, c_i)) — ec. (3.27), definición de F₅.

    Brazo militar del bucle de sobreextensión B1 (mecanismo B1c). Con
    λ_d = 0.06 y μ_min = 0.40 el techo satura a d = 10 provincias: más allá,
    un ejército opera permanentemente al 40 % de eficacia. Define un **radio
    de operación efectivo** por campaña.
    """
    par = estado.p
    capital = estado.imperio(ejercito.propietario).capital
    if capital is None:
        return par.moral_minima
    d = estado.distancia(ejercito.ubicacion, capital)
    return max(par.moral_minima, 1.0 - par.lambda_distancia * d)


def regenerar_moral(estado: Estado, ejercito) -> None:
    """μ_a(t+1) = min(μ̄_a, μ_a + ρ_μ) — ec. (3.28), definición de F₉.

    Si el ejército se ha alejado de su capital, μ̄_a puede haber caído por
    debajo de μ_a: en tal caso la fórmula **reduce** la moral hasta el nuevo
    techo. Regeneración y penalización por distancia son el mismo mecanismo.
    """
    ejercito.moral = min(techo_moral(estado, ejercito),
                         ejercito.moral + estado.p.rho_moral)


def desgaste_moral(estado: Estado, ejercito, bajas: float) -> None:
    """μ_a ← max(μ_min, μ_a(1 − γ_μ·b_gan/F_a)) — ec. (3.29), repara D6.

    Una victoria ajustada cuesta moral, una holgada casi no. El ejército que
    encadena combates difíciles se degrada, lo que introduce un límite natural
    a las campañas relámpago y refuerza el bucle de atrición B2.
    """
    if ejercito.fuerza <= 0:
        return
    par = estado.p
    factor = 1.0 - par.gamma_moral * bajas / ejercito.fuerza
    ejercito.moral = clamp(ejercito.moral * factor, par.moral_minima, 1.0)
