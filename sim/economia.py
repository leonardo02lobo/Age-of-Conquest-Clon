"""Subsistema económico y demográfico — §3.2 y §5.2 del documento del Parcial II.

Contiene la renta provincial (F₁ `calcularIngreso`), la ecuación de estado del
tesoro, la dinámica del descontento (refinamiento O2), la tasa de equilibrio,
el tamaño máximo sostenible, la dinámica poblacional y la regla de insolvencia.
"""

from __future__ import annotations

import math

from .estado import Estado, Provincia, clamp


# --------------------------------------------------------------------- renta

def renta_provincia(estado: Estado, p: Provincia) -> float:
    """I_p(t) — ec. (3.1), definición de F₁.

        I_p = ι·L_p·(θ_i/100)·(1 + β_φ·φ_p)·1[D_p < D*]

    El indicador es el **umbral fiscal**: una provincia cuyo descontento
    alcanza D* deja de tributar por completo. Es una discontinuidad
    deliberada (§5.2a), no una degradación gradual.
    """
    if p.propietario is None:
        return 0.0
    par = estado.p
    if p.descontento >= par.umbral_fiscal:
        return 0.0
    tasa = estado.imperio(p.propietario).tasa
    return par.iota * p.poblacion * (tasa / 100.0) * (1.0 + par.beta_fort * p.fortificacion)


def recaudacion(estado: Estado, iid: str) -> float:
    """R_i(t) = Σ_{p∈P_i} I_p — ec. (3.2)."""
    return sum(renta_provincia(estado, p) for p in estado.provincias_de(iid))


def coste_mantenimiento(estado: Estado, iid: str) -> float:
    """C_i(t) = c_adm·n_i + c_up·M_i — ec. (3.3).

    El primer sumando es el brazo económico del bucle de sobreextensión B1:
    cada provincia añadida cuesta c_adm oro por turno con independencia de lo
    que produzca.
    """
    par = estado.p
    return par.coste_admin * estado.n(iid) + par.coste_mantenimiento * estado.imperio(iid).poder_militar


# -------------------------------------------------------------------- tesoro

def actualizar_tesoro(estado: Estado, iid: str) -> tuple[float, float, float]:
    """G_i(t+1) = G_i(t) + R_i − C_i — ec. (3.4). Se ejecuta en E1.

    El gasto discrecional X_i = c_u·u_i + c_φ·z_i (3.5) se descuenta en E2, no
    aquí. Devuelve (R_i, C_i, neto) para la traza y el informe.
    """
    imp = estado.imperio(iid)
    r = recaudacion(estado, iid)
    c = coste_mantenimiento(estado, iid)
    imp.oro += r - c
    return r, c, r - c


def insolvencia(estado: Estado, iid: str) -> float:
    """Regla de insolvencia — ec. (5.2). Repara el hueco del modelo conceptual.

        G_i < 0  ⟹  G_i ← 0  y  desertan  ΔM_i = ⌈|G_i| / c_up⌉  unidades

    Orden de deserción determinista: primero los ejércitos más alejados de la
    capital (peor abastecidos), a igual distancia los de menor moral, a igual
    moral los de menor identificador; las guarniciones desertan en último lugar.

    Proposición 3: la regla no oscila. El gasto del turno siguiente se reduce
    en c_up·ΔM_i ≥ |G_i| mientras la recaudación no disminuye —el imperio
    conserva sus provincias—, de modo que retorna a solvencia.

    Devuelve la fuerza total desertada.
    """
    imp = estado.imperio(iid)
    if imp.oro >= 0:
        return 0.0

    par = estado.p
    por_desertar = math.ceil(abs(imp.oro) / par.coste_mantenimiento)
    imp.oro = 0.0
    desertado = 0.0

    capital = imp.capital
    ejercitos = sorted(
        estado.ejercitos_de(iid),
        key=lambda e: (
            -(estado.distancia(e.ubicacion, capital) if capital else 0),
            e.moral,
            e.id,
        ),
    )
    for e in ejercitos:
        if por_desertar <= 0:
            break
        quita = min(e.fuerza, por_desertar)
        e.fuerza -= quita
        por_desertar -= quita
        desertado += quita
        if e.fuerza < par.fuerza_minima:
            estado.destruir_ejercito(e.id)  # §5.3(b)

    # Las guarniciones desertan en último lugar, en orden determinista.
    for p in estado.provincias_de(iid):
        if por_desertar <= 0:
            break
        quita = min(p.guarnicion, por_desertar)
        p.guarnicion -= quita
        por_desertar -= quita
        desertado += quita

    return desertado


# ---------------------------------------------------------------- descontento

def delta_descontento(estado: Estado, iid: str) -> float:
    """ΔD_p(t) — ec. (3.6). Igual para todas las provincias del imperio.

        ΔD = η_θ(θ_i − θ₀) + η_w·1[guerra] + η_n·max(0, n_i − n*) − η_r
             presión fiscal   esfuerzo de     sobreextensión B1d    recuperación
                              guerra
    """
    par = estado.p
    imp = estado.imperio(iid)
    return (
        par.eta_fiscal * (imp.tasa - par.tasa_neutra)
        + par.eta_guerra * (1.0 if estado.en_guerra(iid) else 0.0)
        + par.eta_extension * max(0, estado.n(iid) - par.umbral_admin)
        - par.eta_recuperacion
    )


def tasa_equilibrio(estado: Estado, iid: str) -> float:
    """θ^eq_i — ec. (3.8): la tasa que mantiene ΔD_p = 0.

    Con los valores por defecto, un imperio pequeño en paz tiene θ^eq = 75 %;
    el mismo imperio en guerra baja a ≈ 41.7 %. Es la política fiscal de la
    estrategia ECONÓMICA.
    """
    par = estado.p
    numerador = (
        par.eta_recuperacion
        - par.eta_guerra * (1.0 if estado.en_guerra(iid) else 0.0)
        - par.eta_extension * max(0, estado.n(iid) - par.umbral_admin)
    )
    return par.tasa_neutra + numerador / par.eta_fiscal


def tamano_maximo_sostenible(estado: Estado, en_guerra: bool) -> float:
    """n^max — ec. (3.9): tamaño administrable sin descontento creciente.

        n^max = n* + (η_r − η_w·1[guerra] + η_θ·θ₀) / η_n

    Con los valores por defecto: 17 provincias en paz, 13 en guerra. Como la
    cuota de victoria exige ⌈Θ_V·N⌉ = 15, **un imperio en guerra permanente no
    puede alcanzar la victoria sin que su descontento crezca sin freno**. Esta
    predicción del modelo se contrasta empíricamente en el Parcial III.
    """
    par = estado.p
    return par.umbral_admin + (
        par.eta_recuperacion
        - par.eta_guerra * (1.0 if en_guerra else 0.0)
        + par.eta_fiscal * par.tasa_neutra
    ) / par.eta_extension


def aplicar_descontento(estado: Estado, iid: str) -> float:
    """D_p(t+1) = clamp(D_p + ΔD_p, 0, 100) — ec. (3.7). Se ejecuta en E9."""
    delta = delta_descontento(estado, iid)
    for p in estado.provincias_de(iid):
        p.descontento = clamp(p.descontento + delta, 0.0, 100.0)
    return delta


# ---------------------------------------------------------------- población

def actualizar_poblacion(estado: Estado, p: Provincia) -> None:
    """L_p(t+1) = min(L_max, L_p(1+g_L)) − ϱ·β_p(t) — ec. (3.10) y (5.5).

    El término de daño de guerra acopla el subsistema militar con el económico:
    las provincias disputadas repetidamente se empobrecen, y conquistar el
    mismo territorio muchas veces destruye su valor económico. El acumulador
    β_p se consume y se reinicia aquí.
    """
    par = estado.p
    crecida = min(par.poblacion_maxima, p.poblacion * (1.0 + par.crecimiento_poblacion))
    p.poblacion = max(0.0, crecida - par.habitantes_por_baja * p.bajas_turno)
    p.bajas_turno = 0.0
