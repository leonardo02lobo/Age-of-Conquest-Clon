"""Subsistema diplomático — §3.7 del Parcial II. Definición de F₁₂.

Repara los defectos D1 (δ_ij se declaraba pero nunca se consultaba) y D2
(E7 y E8 figuraban en el catálogo pero no tenían pseudocódigo).

La evaluación se ejecuta en la fase φ_DIP = 0.05, **antes** de la
planificación (0.10), de modo que cada imperio decide conociendo ya el estado
diplomático vigente del turno.
"""

from __future__ import annotations

from .estado import Estado
from .militar import poder_militar
from .parametros import EstadoDiplomatico as ED


def es_legal(estado: Estado, iid: str, destino: str) -> bool:
    """Legal(i,q) — guarda diplomática de los movimientos, §3.7.1 (repara D1).

        Legal(i,q) ⟺ π_q = i ∨ π_q = ∅ ∨ δ_{i,π_q} = GUERRA
                              ∨ (δ_{i,π_q} = ALIANZA ∧ tránsito)

    Un movimiento hacia territorio de un imperio con el que se está en PAZ es
    ilegal y se rechaza. Hacia territorio ALIADO se permite el tránsito pero
    no genera combate. La alianza adquiere así efecto mecánico.
    """
    p = estado.provincia(destino)
    if p.propietario is None or p.propietario == iid:
        return True
    return estado.relacion(iid, p.propietario) in (ED.GUERRA, ED.ALIANZA)


def evaluar_diplomacia(estado: Estado) -> list[str]:
    """Eventos E7 y E8 — pseudocódigo de §4.5. Devuelve la traza de cambios."""
    par = estado.p
    traza: list[str] = []
    lider = estado.lider()
    if lider is None:
        return traza
    cuota_lider = estado.cuota(lider)

    # ---------- E7: declaraciones de guerra — ec. (3.30) ----------
    for imp in estado.imperios_activos():
        i = imp.id
        if cuota_lider >= par.cuota_amenaza and i != lider and \
                estado.relacion(i, lider) is ED.PAZ:
            # Coalición anti-líder: cierra el bucle balanceador B3.
            estado.fijar_relacion(i, lider, ED.GUERRA)
            traza.append(f"E7 {i} → {lider} (coalición, q_ℓ={cuota_lider:.3f})")
            continue

        # Agresión oportunista, calibrada por estrategia: una nueva por turno.
        from .agentes import PARAMETROS_ESTRATEGIA
        gamma = PARAMETROS_ESTRATEGIA[imp.estrategia].gamma_guerra
        m_i = poder_militar(estado, i)
        for otro in estado.imperios_activos():
            j = otro.id
            if j == i or estado.relacion(i, j) is not ED.PAZ:
                continue
            if not estado.son_adyacentes(i, j):
                continue
            m_j = poder_militar(estado, j)
            if m_i / max(m_j, 1.0) >= gamma:
                estado.fijar_relacion(i, j, ED.GUERRA)
                traza.append(f"E7 {i} → {j} (oportunismo, M_i/M_j={m_i/max(m_j,1.0):.2f})")
                break  # una guerra nueva por turno

    # ---------- E8: alianzas — ecs. (3.31) y (3.32) ----------
    activos = [imp.id for imp in estado.imperios_activos()]
    for a in range(len(activos)):
        for b in range(a + 1, len(activos)):
            i, j = activos[a], activos[b]
            rel = estado.relacion(i, j)
            if (rel is ED.PAZ and i != lider and j != lider
                    and cuota_lider >= par.cuota_amenaza
                    and estado.relacion(i, lider) is ED.GUERRA
                    and estado.relacion(j, lider) is ED.GUERRA):
                # Dos imperios que comparten enemigo se alían.
                estado.fijar_relacion(i, j, ED.ALIANZA)
                traza.append(f"E8+ {i} ↔ {j} (alianza contra {lider})")
            elif rel is ED.ALIANZA and \
                    cuota_lider < par.cuota_amenaza - par.histeresis_alianza:
                # Ruptura con histéresis: sin el margen ς_h, una cuota que
                # oscilase alrededor de θ_am provocaría formación y disolución
                # en turnos alternos, un artefacto sin correlato real.
                estado.fijar_relacion(i, j, ED.PAZ)
                traza.append(f"E8− {i} ↔ {j} (disolución, q_ℓ={cuota_lider:.3f})")

    return traza
