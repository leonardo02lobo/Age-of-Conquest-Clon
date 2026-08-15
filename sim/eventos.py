"""Motor de eventos discretos — §4.1, §4.3 y §4.5 del Parcial II.

Un módulo por capítulo del documento; aquí viven los diez eventos E1–E10 con
sus pseudocódigos, los predicados de validez de la tabla 4.3 y el despachador
del bucle principal de §4.1.
"""

from __future__ import annotations

from . import agentes, economia, militar
from .diplomacia import es_legal, evaluar_diplomacia
from .estado import Estado
from .parametros import EstadoDiplomatico as ED
from .reloj import (
    EPSILON,
    FASE_DIPLOMACIA,
    FASE_FIN_JUEGO,
    FASE_FIN_TURNO,
    FASE_INICIO,
    FASE_PLANIFICACION,
    Evento,
    ListaEventosFuturos,
    TipoEvento,
    formato_tau,
    funcion_llegada,
    instante,
)


# ============================================================ predicados 4.3

def es_valido(estado: Estado, ev: Evento) -> bool:
    """Predicado de validez del evento — tabla 4.3, §4.3.3.

    Un evento se programa con el estado del instante en que se emite pero se
    ejecuta con el del instante de llegada, que puede haber cambiado. Si el
    predicado es falso el evento se descarta sin efecto.
    """
    t, d = ev.tipo, ev.datos

    if t is TipoEvento.PLANIFICACION:
        imp = estado.imperios.get(d["imperio"])
        return imp is not None and imp.activo and estado.n(imp.id) > 0

    if t is TipoEvento.MOVIMIENTO:
        e = estado.ejercitos.get(d["ejercito"])
        if e is None or not estado.imperio(e.propietario).activo:
            return False
        # El caso más relevante: si entre la emisión y la llegada el imperio ha
        # firmado la paz con el dueño del destino, la invasión se cancela.
        return es_legal(estado, e.propietario, d["destino"])

    if t is TipoEvento.RESOLUCION_COMBATE:
        c = estado.combates.get(d["combate"])
        if c is None or c.atacante not in estado.ejercitos:
            return False
        return estado.provincia(c.provincia).propietario == c.defensor

    if t is TipoEvento.CONQUISTA:
        imp = estado.imperios.get(d["conquistador"])
        return imp is not None and imp.activo

    if t is TipoEvento.ELIMINACION:
        imp = estado.imperios.get(d["imperio"])
        return imp is not None and imp.activo and estado.n(imp.id) == 0

    if t is TipoEvento.FIN_JUEGO:
        return not estado.fin_juego

    return True


# =================================================================== eventos

def e1_inicio_turno(estado: Estado, lef: ListaEventosFuturos, ev: Evento,
                    rec) -> None:
    """E1 — Inicio de Turno. Recaudación, coste, tesoro e insolvencia.

    La recaudación **precede a la planificación** porque el imperio debe
    conocer su tesoro antes de decidir cuánto reclutar, y usa la población de
    inicio de turno: es un censo fiscal levantado al abrir el turno.
    """
    t = ev.turno
    estado.turno = t
    rec.linea(f"── τ={formato_tau(ev.tau)}  E1 INICIO_TURNO  (turno {t}) ──")

    for imp in estado.imperios_activos():
        # M_i debe estar actualizado antes de cobrar el mantenimiento (3.3).
        imp.poder_militar = militar.poder_militar(estado, imp.id)
        r, c, neto = economia.actualizar_tesoro(estado, imp.id)
        desertado = economia.insolvencia(estado, imp.id)
        rec.linea(
            f"   {imp.id:<12} R={r:8.2f}  C={c:8.2f}  neto={neto:+8.2f}  "
            f"G={imp.oro:9.2f}  n={estado.n(imp.id)}"
            + (f"  ⚠ INSOLVENCIA: desertan {desertado:.0f} ud" if desertado else "")
        )
        rec.registrar_economia(t, imp.id, r, c, imp.oro)

    lef.programar(Evento(instante(t, FASE_DIPLOMACIA), TipoEvento.DIPLOMACIA))
    for imp in estado.imperios_activos():
        lef.programar(Evento(instante(t, FASE_PLANIFICACION),
                             TipoEvento.PLANIFICACION, {"imperio": imp.id}))
    lef.programar(Evento(instante(t, FASE_FIN_TURNO), TipoEvento.FIN_TURNO))


def e7e8_diplomacia(estado: Estado, lef: ListaEventosFuturos, ev: Evento,
                    rec) -> None:
    """E7/E8 — Diplomacia. Ausentes en el Parcial I; reparan D2."""
    cambios = evaluar_diplomacia(estado)
    if cambios:
        rec.linea(f"   τ={formato_tau(ev.tau)}  E7/E8 DIPLOMACIA")
        for c in cambios:
            rec.linea(f"      {c}")
            rec.registrar_diplomacia(ev.turno, c)


def e2_planificacion(estado: Estado, lef: ListaEventosFuturos, ev: Evento,
                     rec) -> None:
    """E2 — Planificación / Reclutamiento. Programa los movimientos."""
    iid = ev.datos["imperio"]
    movimientos, traza = agentes.planificar(estado, iid)
    if traza:
        estrategia = agentes.NOMBRE_ESTRATEGIA[estado.imperio(iid).estrategia]
        rec.linea(f"   τ={formato_tau(ev.tau)}  E2 PLANIFICACIÓN {iid} [{estrategia}] "
                  f"θ={estado.imperio(iid).tasa:.1f}%")
        for linea in traza:
            rec.linea(f"      {linea}")

    for eid, destino, coste in movimientos:
        lef.programar(Evento(
            funcion_llegada(ev.turno, coste),
            TipoEvento.MOVIMIENTO,
            {"ejercito": eid, "destino": destino},
        ))
        estado.en_transito.add(eid)


def e3_movimiento(estado: Estado, lef: ListaEventosFuturos, ev: Evento,
                  rec) -> None:
    """E3 — Movimiento de Ejército, con guarda diplomática (repara D1 y D8)."""
    e = estado.ejercitos[ev.datos["ejercito"]]
    estado.en_transito.discard(e.id)   # la llegada cierra el tránsito
    destino = ev.datos["destino"]
    q = estado.provincia(destino)
    iid = e.propietario

    if q.propietario == iid or (
            q.propietario is not None
            and estado.relacion(iid, q.propietario) is ED.ALIANZA):
        # Avance propio o tránsito aliado: el ejército NO se disuelve en la
        # guarnición (reparación D8) y no se genera combate.
        e.ubicacion = destino
        return

    if q.propietario is None:
        e.ubicacion = destino
        lef.programar(Evento(ev.tau + EPSILON, TipoEvento.CONQUISTA,
                             {"provincia": destino, "conquistador": iid}))
        return

    # δ = GUERRA, garantizado por Legal. Caso degenerado (a) de §5.3: una
    # provincia sin defensa se ocupa sin bajas y sin crear entidad Combate.
    defensa = militar.defensa_provincia(estado, q)
    if defensa <= 0:
        e.ubicacion = destino
        lef.programar(Evento(ev.tau + EPSILON, TipoEvento.CONQUISTA,
                             {"provincia": destino, "conquistador": iid}))
        return

    c = estado.crear_combate(destino, e.id, defensa)
    q.en_conflicto = True
    e.en_combate = True
    rec.linea(f"   τ={formato_tau(ev.tau)}  E3 MOVIMIENTO a{e.id}({iid}) → {destino} "
              f"[{q.terreno.codigo}] invade a {q.propietario}")
    lef.programar(Evento(ev.tau + EPSILON, TipoEvento.RESOLUCION_COMBATE,
                         {"combate": c.id}))


def e4_resolucion_combate(estado: Estado, lef: ListaEventosFuturos, ev: Evento,
                          rec) -> None:
    """E4 — Resolución de Combate. Consume exactamente dos aleatorios."""
    c = estado.combates[ev.datos["combate"]]
    e = estado.ejercitos[c.atacante]
    p = estado.provincia(c.provincia)
    fuerza_previa = e.fuerza

    r = militar.resolver_combate(estado, e.fuerza, e.moral, c.defensa, p, estado.rng)

    if r.vence_atacante:
        militar.desgaste_moral(estado, e, r.bajas_ganador)   # (3.29) antes de restar F
        e.fuerza -= r.bajas_ganador                          # (3.21)
        militar.destruir_defensa(estado, p)                  # (3.20)
        e.ubicacion = p.id
        if e.fuerza < estado.p.fuerza_minima:
            estado.destruir_ejercito(e.id)                   # §5.3(b)
        lef.programar(Evento(ev.tau + EPSILON, TipoEvento.CONQUISTA,
                             {"provincia": p.id, "conquistador": e.propietario}))
    else:
        militar.repartir_bajas(estado, p, r.bajas_ganador)   # (3.12c)
        estado.destruir_ejercito(e.id)

    estado.num_combates += 1
    estado.bajas_totales += r.bajas_ganador + r.bajas_perdedor
    p.bajas_turno += r.bajas_ganador + r.bajas_perdedor
    p.en_conflicto = False
    estado.destruir_combate(c.id)

    vencedor = "ATACANTE" if r.vence_atacante else "DEFENSOR"
    rec.linea(
        f"   τ={formato_tau(ev.tau)}  E4 COMBATE {p.id}: "
        f"P_a={r.p_atacante:.2f} (U_a={r.u_atacante:.4f}) vs "
        f"P_d={r.p_defensor:.2f} (U_d={r.u_defensor:.4f}) → vence {vencedor}, "
        f"bajas {r.bajas_ganador:.2f}/{r.bajas_perdedor:.2f}"
    )
    rec.registrar_combate(ev.turno, fuerza_previa, r)


def e5_conquista(estado: Estado, lef: ListaEventosFuturos, ev: Evento,
                 rec) -> None:
    """E5 — Conquista de Provincia."""
    p = estado.provincia(ev.datos["provincia"])
    conquistador = ev.datos["conquistador"]
    antiguo = p.propietario

    if antiguo is not None and antiguo != conquistador:
        estado.fijar_relacion(conquistador, antiguo, ED.GUERRA)

    p.propietario = conquistador
    p.fortificacion = max(0, p.fortificacion - 1)      # asedio, ec. (5.6)
    p.descontento = min(100.0, p.descontento + 10.0)   # ocupación militar
    p.guarnicion = 0.0

    rec.linea(f"   τ={formato_tau(ev.tau)}  E5 CONQUISTA {p.id} "
              f"({antiguo or 'NEUTRAL'} → {conquistador}) φ={p.fortificacion} D={p.descontento:.0f}")
    rec.registrar_conquista(ev.turno, conquistador, antiguo, p.id)

    if antiguo is not None:
        viejo = estado.imperio(antiguo)
        # §5.3(c): al cambiar la capital cambian todas las distancias d(u_a, c_i)
        # y por tanto todos los techos de moral. Perder la capital reconfigura
        # instantáneamente el alcance operativo del imperio.
        if viejo.capital == p.id:
            restantes = estado.provincias_de(antiguo)
            viejo.capital = (
                max(restantes, key=lambda x: (x.poblacion, [-ord(ch) for ch in x.id])).id
                if restantes else None
            )
            if viejo.capital:
                rec.linea(f"      capital de {antiguo} reasignada a {viejo.capital}")
        if estado.n(antiguo) == 0:
            lef.programar(Evento(ev.tau + EPSILON, TipoEvento.ELIMINACION,
                                 {"imperio": antiguo}))


def e6_eliminacion(estado: Estado, lef: ListaEventosFuturos, ev: Evento,
                   rec) -> None:
    """E6 — Eliminación de Imperio."""
    iid = ev.datos["imperio"]
    imp = estado.imperio(iid)
    imp.activo = False
    imp.capital = None
    for e in estado.ejercitos_de(iid):
        estado.destruir_ejercito(e.id)
    for otro in estado.imperios:
        if otro != iid:
            estado.fijar_relacion(iid, otro, ED.PAZ)
    estado.turno_eliminacion[iid] = ev.turno
    rec.linea(f"   τ={formato_tau(ev.tau)}  E6 ELIMINACIÓN {iid} (turno {ev.turno})")


def e9_fin_turno(estado: Estado, lef: ListaEventosFuturos, ev: Evento,
                 rec) -> None:
    """E9 — Fin de Turno. Descontento, población, moral y poder militar.

    El crecimiento poblacional y el descontento se resuelven **después** de los
    combates: así el daño de guerra ϱβ_p de (3.10) se aplica sobre la población
    del turno en que ocurrió la batalla, y el descontento refleja el estado de
    guerra realmente vigente.
    """
    t = ev.turno
    par = estado.p

    for imp in estado.imperios_activos():
        delta = economia.aplicar_descontento(estado, imp.id)     # (3.6)(3.7)
        for p in estado.provincias_de(imp.id):
            economia.actualizar_poblacion(estado, p)             # (3.10)
        for e in estado.ejercitos_de(imp.id):
            militar.regenerar_moral(estado, e)                   # (3.28)
        imp.poder_militar = militar.poder_militar(estado, imp.id)  # (3.12)
        rec.registrar_turno(t, imp.id, estado, delta)

    # Las provincias neutrales también crecen.
    for p in estado.provincias.values():
        if p.propietario is None:
            economia.actualizar_poblacion(estado, p)

    lider = estado.lider()
    cuota = estado.cuota(lider) if lider else 0.0
    rec.linea(f"   τ={formato_tau(ev.tau)}  E9 FIN_TURNO  líder={lider} "
              f"q_ℓ={cuota:.3f}  m={estado.m}  ν={estado.num_combates}  β={estado.bajas_totales:.0f}")

    if cuota >= par.cuota_victoria or estado.m <= 1 or t >= par.turnos_maximos:
        lef.programar(Evento(instante(t, FASE_FIN_JUEGO), TipoEvento.FIN_JUEGO,
                             {"ganador": lider}))
    else:
        lef.programar(Evento(instante(t + 1, FASE_INICIO), TipoEvento.INICIO_TURNO))


def e10_fin_juego(estado: Estado, lef: ListaEventosFuturos, ev: Evento,
                  rec) -> None:
    """E10 — Fin de Juego."""
    estado.fin_juego = True
    estado.ganador = ev.datos.get("ganador")
    g = estado.ganador
    rec.linea(f"── τ={formato_tau(ev.tau)}  E10 FIN_JUEGO  ganador={g} "
              f"({agentes.NOMBRE_ESTRATEGIA[estado.imperio(g).estrategia] if g else '—'}) "
              f"turno={ev.turno} q={estado.cuota(g) if g else 0:.3f} "
              f"ν={estado.num_combates} β={estado.bajas_totales:.0f} ──")


MANEJADORES = {
    TipoEvento.INICIO_TURNO: e1_inicio_turno,
    TipoEvento.DIPLOMACIA: e7e8_diplomacia,
    TipoEvento.PLANIFICACION: e2_planificacion,
    TipoEvento.MOVIMIENTO: e3_movimiento,
    TipoEvento.RESOLUCION_COMBATE: e4_resolucion_combate,
    TipoEvento.CONQUISTA: e5_conquista,
    TipoEvento.ELIMINACION: e6_eliminacion,
    TipoEvento.FIN_TURNO: e9_fin_turno,
    TipoEvento.FIN_JUEGO: e10_fin_juego,
}


# =============================================================== despachador

class Motor:
    """Despachador del bucle principal — §4.1.

        MIENTRAS 𝓛 ≠ ∅ ∧ Z = 0:
            e ← ExtraerMinimo(𝓛)      // clave (τ, π, ς)
            SI ¬Valido(e): continuar  // tabla 4.3
            τ ← e.tiempo              // el reloj SALTA al instante del evento
            Procesar(e)

    Teorema 3 (terminación): el número total de eventos está acotado por
    t_max·(3 + |I| + 4·|I|·A_max), finito.
    """

    def __init__(self, estado: Estado, recolector):
        self.estado = estado
        self.rec = recolector
        self.lef = ListaEventosFuturos()
        self.eventos_procesados = 0
        self.eventos_cancelados = 0
        # Invariantes verificados en ejecución (Teoremas 1 y 2).
        self.violaciones_causalidad = 0
        self.violaciones_ventana = 0
        self._tau_anterior = -1
        self.lef.programar(Evento(instante(estado.turno, FASE_INICIO),
                                  TipoEvento.INICIO_TURNO))

    def paso(self) -> Evento | None:
        """Procesa un único evento. Devuelve el evento procesado, o None."""
        if not self.lef or self.estado.fin_juego:
            return None
        ev = self.lef.extraer_minimo()

        # Teorema 2 (causalidad): el reloj es monótono no decreciente.
        if ev.tau < self._tau_anterior:
            self.violaciones_causalidad += 1
        self._tau_anterior = ev.tau

        # Teorema 1: toda llegada cae en la ventana [0.15, 0.896).
        if ev.tipo is TipoEvento.MOVIMIENTO and not (
                0.15 <= ev.fase < (FASE_FIN_TURNO - 4 * EPSILON) / 1_000_000):
            self.violaciones_ventana += 1

        if not es_valido(self.estado, ev):
            # Un movimiento cancelado libera igualmente la guarda de tránsito:
            # de lo contrario el ejército quedaría inmovilizado para siempre.
            if ev.tipo is TipoEvento.MOVIMIENTO:
                self.estado.en_transito.discard(ev.datos["ejercito"])
            self.eventos_cancelados += 1
            return ev

        self.estado.tau = ev.tau
        MANEJADORES[ev.tipo](self.estado, self.lef, ev, self.rec)
        self.eventos_procesados += 1
        return ev

    def avanzar_turno(self) -> None:
        """Procesa eventos hasta cerrar el turno en curso."""
        objetivo = self.estado.turno
        while not self.estado.fin_juego and self.lef:
            ev = self.paso()
            if ev is None:
                break
            if ev.tipo in (TipoEvento.FIN_TURNO, TipoEvento.FIN_JUEGO) and ev.turno >= objetivo:
                break

    def ejecutar(self, turnos: int | None = None) -> None:
        """Ejecuta la simulación completa, o `turnos` turnos."""
        if turnos is None:
            while not self.estado.fin_juego and self.lef:
                if self.paso() is None:
                    break
        else:
            for _ in range(turnos):
                if self.estado.fin_juego:
                    break
                self.avanzar_turno()
