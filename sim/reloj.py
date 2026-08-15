"""Reloj de simulación y Lista de Eventos Futuros — §4.1–§4.3.

El reloj se expresa como τ = t + φ, con t el turno y φ ∈ [0,1) la fase.
No avanza con paso fijo: SALTA al instante del próximo evento de la LEF.

Decisión de implementación: internamente τ se representa en **micro-fases
enteras** (1 turno = 1 000 000 micro-fases) en lugar de en coma flotante.
Motivo: la clave de la LEF (4.2) debe ser un orden total estricto y la
simulación exactamente reproducible. Con `float`, sumar repetidamente
ε = 10⁻³ y restos `c mod Δ` acumula error binario y dos eventos que deberían
caer en el mismo instante pueden separarse —o colisionar— según el orden de
las operaciones. Con enteros la comparación es exacta.
"""

from __future__ import annotations

import heapq
from dataclasses import dataclass, field
from enum import Enum
from typing import Any

# Un turno = 10⁶ micro-fases. τ_int = turno · MICRO + fase_micro
MICRO = 1_000_000

# Fases del turno — tabla 4.1, expresadas en micro-fases.
FASE_INICIO = 0            # φ_INI = 0.00
FASE_DIPLOMACIA = 50_000   # φ_DIP = 0.05
FASE_PLANIFICACION = 100_000  # φ_PLA = 0.10
FASE_MOVIMIENTO = 150_000  # φ_MOV = 0.15
FASE_FIN_TURNO = 900_000   # φ_FIN = 0.90
FASE_FIN_JUEGO = 950_000   # φ_JUE = 0.95
EPSILON = 1_000            # ε = 10⁻³

# Anchura útil de la ventana de movimiento — ec. (4.3).
#   Δ = φ_FIN − φ_MOV − 4ε = 0.90 − 0.15 − 0.004 = 0.746
DELTA = FASE_FIN_TURNO - FASE_MOVIMIENTO - 4 * EPSILON
assert DELTA == 746_000, "Δ debe valer 0.746 turnos (ec. 4.3)"


class TipoEvento(Enum):
    """Catálogo de eventos con su fase φ y su prioridad π — tabla 4.1."""

    INICIO_TURNO = ("E1", FASE_INICIO, 0)
    DIPLOMACIA = ("E7/E8", FASE_DIPLOMACIA, 1)
    PLANIFICACION = ("E2", FASE_PLANIFICACION, 2)
    MOVIMIENTO = ("E3", FASE_MOVIMIENTO, 3)
    RESOLUCION_COMBATE = ("E4", None, 4)
    CONQUISTA = ("E5", None, 5)
    ELIMINACION = ("E6", None, 6)
    FIN_TURNO = ("E9", FASE_FIN_TURNO, 7)
    FIN_JUEGO = ("E10", FASE_FIN_JUEGO, 8)

    def __init__(self, etiqueta: str, fase: int | None, prioridad: int):
        self.etiqueta = etiqueta
        self.fase = fase          # None = condicional, hereda del evento padre
        self.prioridad = prioridad


@dataclass
class Evento:
    """Registro de la LEF — §4.3.1: (τ, π, ς, tipo, entidades, parámetros)."""

    tau: int                      # instante en micro-fases
    tipo: TipoEvento
    datos: dict[str, Any] = field(default_factory=dict)
    secuencia: int = 0            # ς, asignado al insertar

    @property
    def turno(self) -> int:
        return self.tau // MICRO

    @property
    def fase(self) -> float:
        return (self.tau % MICRO) / MICRO

    def __str__(self) -> str:
        detalle = ", ".join(f"{k}={v}" for k, v in self.datos.items() if k != "combate")
        return f"τ={formato_tau(self.tau)} {self.tipo.etiqueta} {self.tipo.name}" + (
            f" ({detalle})" if detalle else ""
        )


def formato_tau(tau: int) -> str:
    """Representación legible del reloj, p. ej. 5.817."""
    return f"{tau / MICRO:.3f}"


def instante(turno: int, fase: int) -> int:
    """Compone un instante a partir de un turno y una fase en micro-fases."""
    return turno * MICRO + fase


def funcion_llegada(turno: int, coste_turnos: float) -> int:
    """Instante de llegada de un movimiento — ec. (4.4), repara el defecto D3.

        τ_lleg(t, c) = (t + ⌊c/Δ⌋) + φ_MOV + (c mod Δ)

    Reparte el coste en turnos completos más una fase válida. El Teorema 1
    garantiza que la fase resultante cae siempre en [0.15, 0.896), de modo que
    la cadena MOVIMIENTO → COMBATE → CONQUISTA → ELIMINACIÓN (3ε) concluye
    estrictamente antes del FIN_TURNO de fase 0.90.

    El redondeo de `coste_turnos` a micro-fases es la única discretización que
    introduce el reloj, y se aplica una sola vez, aquí.
    """
    if coste_turnos < 0:
        raise ValueError("El coste de movimiento no puede ser negativo")
    coste = round(coste_turnos * MICRO)
    turnos_completos, resto = divmod(coste, DELTA)
    return instante(turno + turnos_completos, FASE_MOVIMIENTO + resto)


class ListaEventosFuturos:
    """LEF como montículo binario sobre la clave lexicográfica (τ, π, ς) — ec. (4.2).

    ς es un contador global monótono asignado al insertar; como es único por
    construcción, la clave es un **orden total estricto**: no hay empates y la
    simulación es completamente determinista dada la semilla. Esto resuelve la
    indeterminación ante llegadas simultáneas (defecto D4): dos ejércitos que
    alcanzan la misma provincia en el mismo instante se resuelven en orden de
    emisión, y el segundo encuentra la provincia ya modificada por el primero.

    Coste: O(log|L|) por inserción y por extracción.
    """

    def __init__(self) -> None:
        self._monticulo: list[tuple[int, int, int, Evento]] = []
        self._secuencia = 0

    def __len__(self) -> int:
        return len(self._monticulo)

    def __bool__(self) -> bool:
        return bool(self._monticulo)

    def programar(self, evento: Evento) -> Evento:
        """Inserta un evento asignándole el siguiente ς."""
        self._secuencia += 1
        evento.secuencia = self._secuencia
        heapq.heappush(
            self._monticulo,
            (evento.tau, evento.tipo.prioridad, evento.secuencia, evento),
        )
        return evento

    def extraer_minimo(self) -> Evento:
        """Extrae el evento de clave mínima."""
        return heapq.heappop(self._monticulo)[3]

    def pendientes(self) -> list[Evento]:
        """Eventos pendientes en orden de clave (para inspección)."""
        return [e for _, _, _, e in sorted(self._monticulo, key=lambda x: x[:3])]
