"""Recolector de estadísticas — componente 4 del motor, §4.1.

Acumula las magnitudes que alimentan las cinco métricas que el Parcial I fijó
como objetivos del modelo:

    O1  Duración media de la partida ....... turno final al disparar E10
    O2  Balance entre estrategias de IA .... tasa de victoria por estrategia
    O3  Efecto bola de nieve ............... correlación provincias↔oro↔poder
    O4  Intensidad bélica .................. combates por turno, bajas
    O5  Puntos de inflexión ................ primer turno con q_ℓ ≥ 0.5
"""

from __future__ import annotations

from dataclasses import dataclass, field


@dataclass
class Recolector:
    """Traza legible y series temporales de la partida."""

    verboso: bool = True
    traza: list[str] = field(default_factory=list)

    # Series por turno: (turno, imperio, n, oro, poder, cuota, descontento medio)
    series: list[tuple] = field(default_factory=list)
    economia: list[tuple] = field(default_factory=list)
    combates: list[tuple] = field(default_factory=list)
    conquistas: list[tuple] = field(default_factory=list)
    diplomacia: list[tuple] = field(default_factory=list)

    turno_inflexion: int | None = None   # O5

    def linea(self, texto: str) -> None:
        self.traza.append(texto)
        if self.verboso:
            print(texto)

    # ------------------------------------------------------------ registros

    def registrar_economia(self, turno: int, iid: str, r: float, c: float,
                           oro: float) -> None:
        self.economia.append((turno, iid, r, c, oro))

    def registrar_combate(self, turno: int, fuerza_previa: float, resultado) -> None:
        self.combates.append((
            turno, fuerza_previa, resultado.vence_atacante,
            resultado.p_atacante, resultado.p_defensor,
            resultado.bajas_ganador, resultado.bajas_perdedor,
        ))

    def registrar_conquista(self, turno: int, conquistador: str,
                            antiguo: str | None, provincia: str) -> None:
        self.conquistas.append((turno, conquistador, antiguo, provincia))

    def registrar_diplomacia(self, turno: int, descripcion: str) -> None:
        self.diplomacia.append((turno, descripcion))

    def registrar_turno(self, turno: int, iid: str, estado, delta_descontento: float) -> None:
        provincias = estado.provincias_de(iid)
        descontento_medio = (
            sum(p.descontento for p in provincias) / len(provincias) if provincias else 0.0
        )
        imp = estado.imperio(iid)
        cuota = estado.cuota(iid)
        self.series.append((
            turno, iid, imp.estrategia, len(provincias), imp.oro,
            imp.poder_militar, cuota, descontento_medio, imp.tasa,
            delta_descontento, estado.en_guerra(iid),
        ))
        # O5: primer turno en que el líder supera el 50 % del mapa.
        if self.turno_inflexion is None and cuota >= 0.5:
            self.turno_inflexion = turno


@dataclass
class ResultadoPartida:
    """Resumen de una réplica, la unidad de análisis del experimento.

    La simulación es **terminante** (acaba por Θ_V, m = 1 o t_max), no
    estacionaria: no hay periodo de calentamiento que eliminar y la unidad de
    análisis es la réplica independiente.
    """

    semilla: int
    ganador: str | None
    estrategia_ganadora: str | None
    turno_final: int
    cuota_final: float
    num_combates: int
    bajas_totales: float
    turno_inflexion: int | None
    eventos_procesados: int
    eventos_cancelados: int
    aleatorios_consumidos: int
    ganador_en_guerra_continua: bool
    parametro: str = ""
    valor: float = 0.0

    @staticmethod
    def cabecera_csv() -> str:
        return ("parametro,valor,semilla,ganador,estrategia,turno_final,cuota_final,"
                "combates,bajas,turno_inflexion,eventos,cancelados,aleatorios,"
                "ganador_guerra_continua")

    def fila_csv(self) -> str:
        return (
            f"{self.parametro},{self.valor},{self.semilla},{self.ganador},"
            f"{self.estrategia_ganadora},{self.turno_final},{self.cuota_final:.4f},"
            f"{self.num_combates},{self.bajas_totales:.2f},"
            f"{self.turno_inflexion if self.turno_inflexion is not None else ''},"
            f"{self.eventos_procesados},{self.eventos_cancelados},"
            f"{self.aleatorios_consumidos},{int(self.ganador_en_guerra_continua)}"
        )
