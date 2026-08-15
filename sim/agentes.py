"""Árbol de decisión de la IA — §2.4.9 y §4.6 del Parcial II. Definición de F₁₁ y F₃.

La política de cada imperio es **determinista**: dado el estado, las decisiones
son únicas, y ningún agente consume números del generador aleatorio. Toda la
aleatoriedad del modelo reside en U_a y U_d (§3.5). Esta separación es
deliberada: permite atribuir la variabilidad entre réplicas exclusivamente al
azar del combate y no a la política.
"""

from __future__ import annotations

from collections import deque
from dataclasses import dataclass

from .diplomacia import es_legal
from .economia import tasa_equilibrio
from .estado import Estado, clamp
from .militar import (
    coste_movimiento,
    defensa_provincia,
    potencia_atacante,
    potencia_defensora,
    reclutar,
)


@dataclass(frozen=True)
class ParametrosEstrategia:
    """Vector de seis parámetros que define una estrategia — tabla §2.4.9."""

    tasa: float | None      # θ^σ; None = adaptativa (se calcula cada turno)
    f_reclutar: float       # f_rec  fracción del tesoro a reclutar
    gamma_ataque: float     # γ_atq  ventaja mínima para atacar
    gamma_guerra: float     # γ_σ    superioridad para declarar guerra
    f_guarnicion: float     # f_gua  fracción de fuerza retenida al partir
    f_fortificar: float     # f_fort prioridad de fortificación


# Las cuatro estrategias dejan de ser etiquetas y adquieren significado formal:
# γ_atq decide si el imperio opera en el régimen determinista de la
# Proposición 1 (γ ≥ 1.5) o acepta riesgo en el régimen estocástico.
PARAMETROS_ESTRATEGIA: dict[str, ParametrosEstrategia] = {
    # AGRESIVA: maximiza R1 a corto plazo e ignora B1. Régimen estocástico.
    "AGR": ParametrosEstrategia(125.0, 0.90, 1.1, 1.2, 0.15, 0.05),
    # DEFENSIVA: nunca lanza una ofensiva que pueda perder. Régimen determinista.
    "DEF": ParametrosEstrategia(100.0, 0.60, 1.8, 2.5, 0.50, 0.40),
    # ECONÓMICA: única con política fiscal adaptativa; evita B1d por completo.
    "ECO": ParametrosEstrategia(None, 0.30, 2.0, 3.0, 0.40, 0.25),
    # EQUILIBRADA: referencia de control frente a la que medir a las otras tres.
    "EQU": ParametrosEstrategia(None, 0.70, 1.4, 1.8, 0.30, 0.20),
}

NOMBRE_ESTRATEGIA = {
    "AGR": "AGRESIVA", "DEF": "DEFENSIVA",
    "ECO": "ECONÓMICA", "EQU": "EQUILIBRADA",
}


def politica_fiscal(estado: Estado, iid: str) -> float:
    """Fija θ_i según la estrategia — §4.6.1–§4.6.4, con recorte a [0, θ_max].

    ECONÓMICA usa θ^eq (3.8) directamente: baja la tasa al entrar en guerra y
    al sobrepasar n*, manteniendo el descontento estacionario. EQUILIBRADA usa
    el punto medio entre 100 y θ^eq. AGRESIVA y DEFENSIVA son fijas.
    """
    imp = estado.imperio(iid)
    par = PARAMETROS_ESTRATEGIA[imp.estrategia]
    if imp.estrategia == "ECO":
        objetivo = tasa_equilibrio(estado, iid)
    elif imp.estrategia == "EQU":
        objetivo = 0.5 * (100.0 + tasa_equilibrio(estado, iid))
    else:
        objetivo = par.tasa
    return clamp(objetivo, 0.0, estado.p.tasa_maxima)


def decidir_fortificacion(estado: Estado, iid: str) -> str | None:
    """Fortifica la provincia fronteriza con menor fuerza defensiva D_p.

    Solo actúa si hay holgura suficiente: G_i ≥ c_φ / f_fort. Con
    f_fort = 0.05 (AGRESIVA) el umbral es 800 oro, de modo que casi nunca
    fortifica; con 0.40 (DEFENSIVA) basta con 100.
    """
    imp = estado.imperio(iid)
    par = PARAMETROS_ESTRATEGIA[imp.estrategia]
    if imp.oro < estado.p.coste_fortificacion / par.f_fortificar:
        return None
    candidatas = [p for p in estado.frontera(iid)
                  if p.fortificacion < estado.p.fortificacion_maxima]
    if not candidatas:
        return None
    objetivo = min(candidatas, key=lambda p: (defensa_provincia(estado, p), p.id))
    imp.oro -= estado.p.coste_fortificacion
    objetivo.fortificacion += 1
    return objetivo.id


def siguiente_paso_hacia_frontera(estado: Estado, ejercito) -> str | None:
    """BFS multifuente desde ∂P_i sobre el subgrafo propio — §4.6.5.

    Un ejército sin objetivo rentable avanza hacia la frontera para no dejar
    fuerza ociosa en el interior. Coste O(|P_i| + |E_i|) por imperio y turno.
    """
    iid = ejercito.propietario
    dist: dict[str, int] = {}
    cola: deque[str] = deque()
    for p in estado.frontera(iid):
        dist[p.id] = 0
        cola.append(p.id)
    while cola:
        x = cola.popleft()
        for y in sorted(estado.provincia(x).adyacentes):
            if estado.provincia(y).propietario == iid and y not in dist:
                dist[y] = dist[x] + 1
                cola.append(y)

    aqui = ejercito.ubicacion
    if aqui not in dist or dist[aqui] == 0:
        return None  # ya está en la frontera: mantener posición refuerza D_p
    for y in sorted(estado.provincia(aqui).adyacentes):
        if estado.provincia(y).propietario == iid and dist.get(y, 10**6) < dist[aqui]:
            return y
    return None


def seleccionar_objetivo(estado: Estado, ejercito) -> str | None:
    """F₃ `SeleccionarObjetivos` — §4.6.

    Evalúa los destinos legales adyacentes con las **potencias deterministas**
    (U = 1) y ataca solo si P_a^det ≥ γ_atq·P_d^det. Entre los candidatos
    válidos elige el de mayor población L_q (desempate por menor id).
    """
    iid = ejercito.propietario
    par = PARAMETROS_ESTRATEGIA[estado.imperio(iid).estrategia]
    fuerza_enviada = ejercito.fuerza * (1.0 - par.f_guarnicion)

    mejor = None
    mejor_clave = None
    for qid in sorted(estado.provincia(ejercito.ubicacion).adyacentes):
        q = estado.provincia(qid)
        if q.propietario == iid or not es_legal(estado, iid, qid):
            continue
        pa = potencia_atacante(estado, fuerza_enviada, ejercito.moral, q)
        pd = potencia_defensora(estado, defensa_provincia(estado, q), q)
        if pa >= par.gamma_ataque * pd:
            clave = (-q.poblacion, qid)
            if mejor_clave is None or clave < mejor_clave:
                mejor, mejor_clave = qid, clave
    return mejor


def planificar(estado: Estado, iid: str) -> tuple[list[tuple[int, str, float]], list[str]]:
    """Evento E2 (parte de decisión) — pseudocódigo de §4.5.

    Fija la tasa, recluta, fortifica, levanta ejércitos y selecciona objetivos.
    Devuelve (movimientos, traza), donde cada movimiento es
    (id_ejercito, destino, coste_en_turnos) para que E2 los programe en la LEF.
    """
    imp = estado.imperio(iid)
    par = PARAMETROS_ESTRATEGIA[imp.estrategia]
    traza: list[str] = []

    # --- política fiscal (3.8) ---
    imp.tasa = politica_fiscal(estado, iid)

    # --- reclutamiento F₂, ec. (3.11) ---
    unidades = reclutar(estado, iid, par.f_reclutar)
    if unidades:
        traza.append(f"recluta {unidades} ud en {imp.capital} (G={imp.oro:.2f})")

    # --- fortificación ---
    fortificada = decidir_fortificacion(estado, iid)
    if fortificada:
        traza.append(f"fortifica {fortificada}")

    # --- levantamiento de ejércitos ---
    # Cierra el ciclo recluta → guarnición de la capital → fuerza ofensiva.
    if imp.capital is not None:
        cap = estado.provincia(imp.capital)
        if (cap.guarnicion > estado.p.guarnicion_reserva
                and len(estado.ejercitos_de(iid)) < estado.p.ejercitos_maximos):
            fuerza = cap.guarnicion - estado.p.guarnicion_reserva
            nuevo = estado.crear_ejercito(iid, fuerza, imp.capital)
            cap.guarnicion = estado.p.guarnicion_reserva
            traza.append(f"leva ejército a{nuevo.id} F={fuerza:.2f} en {imp.capital}")

    # --- selección de objetivos F₃ ---
    movimientos: list[tuple[int, str, float]] = []
    for e in estado.ejercitos_de(iid):
        if e.id in estado.en_transito:
            continue  # guarda de tránsito — ver nota en Estado.en_transito
        objetivo = seleccionar_objetivo(estado, e)
        if objetivo is not None:
            # Deja retaguardia antes de partir.
            retaguardia = e.fuerza * par.f_guarnicion
            estado.provincia(e.ubicacion).guarnicion += retaguardia
            e.fuerza -= retaguardia
            coste = coste_movimiento(estado, estado.provincia(objetivo))
            movimientos.append((e.id, objetivo, coste))
            traza.append(f"a{e.id} ataca {objetivo} (F={e.fuerza:.2f}, c={coste:.3f})")
        else:
            paso = siguiente_paso_hacia_frontera(estado, e)
            if paso is not None:
                coste = coste_movimiento(estado, estado.provincia(paso))
                movimientos.append((e.id, paso, coste))
                traza.append(f"a{e.id} avanza a {paso}")

    return movimientos, traza
