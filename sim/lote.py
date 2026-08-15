"""Ejecución por lotes y análisis de salida — instrumento de experimentación.

La simulación es **terminante**: acaba por Θ_V, por m = 1 o por t_max. No hay
periodo de calentamiento que eliminar y la unidad de análisis es la **réplica
independiente**, de modo que los intervalos de confianza se construyen sobre
la media entre réplicas.

Se emplea la técnica de **números aleatorios comunes** (semillas apareadas):
al comparar dos configuraciones se ejecuta la réplica r de ambas con la misma
semilla s_r, de modo que las diferencias observadas no se deban a la suerte
del sorteo sino a la configuración. Es una técnica de reducción de varianza
(§3.5.3 del documento).
"""

from __future__ import annotations

import math
import statistics
from pathlib import Path

from .agentes import NOMBRE_ESTRATEGIA
from .escenario import cargar_referencia
from .eventos import Motor
from .parametros import Parametros
from .recolector import Recolector, ResultadoPartida


def jugar_una(semilla: int, parametros: Parametros | None = None,
              parametro: str = "base", valor: float = 0.0) -> ResultadoPartida:
    """Juega una réplica completa y devuelve su resumen."""
    par = (parametros or Parametros()).copia(semilla=semilla)
    estado = cargar_referencia(par)
    rec = Recolector(verboso=False)
    motor = Motor(estado, rec)
    motor.ejecutar()

    ganador = estado.ganador
    estrategia = NOMBRE_ESTRATEGIA[estado.imperio(ganador).estrategia] if ganador else None

    # Validación predictiva (§3.2.4): ¿estuvo el ganador en guerra continua?
    # El modelo predice que n^max_guerra = 13 < 15 = ⌈Θ_V·N⌉, es decir, que un
    # imperio en guerra permanente no debería poder alcanzar la victoria.
    turnos_ganador = [s for s in rec.series if s[1] == ganador]
    guerra_continua = bool(turnos_ganador) and all(s[10] for s in turnos_ganador)

    return ResultadoPartida(
        semilla=semilla,
        ganador=ganador,
        estrategia_ganadora=estrategia,
        turno_final=estado.turno,
        cuota_final=estado.cuota(ganador) if ganador else 0.0,
        num_combates=estado.num_combates,
        bajas_totales=estado.bajas_totales,
        turno_inflexion=rec.turno_inflexion,
        eventos_procesados=motor.eventos_procesados,
        eventos_cancelados=motor.eventos_cancelados,
        aleatorios_consumidos=estado.rng.consumidos,
        ganador_en_guerra_continua=guerra_continua,
        parametro=parametro,
        valor=valor,
    )


def replicas(n: int, semilla_base: int = 20_260_805,
             parametros: Parametros | None = None,
             parametro: str = "base", valor: float = 0.0) -> list[ResultadoPartida]:
    """n réplicas con semillas s_base, s_base+1, … (números aleatorios comunes)."""
    return [jugar_una(semilla_base + i, parametros, parametro, valor) for i in range(n)]


def intervalo_confianza(datos: list[float], nivel: float = 0.95) -> tuple[float, float, float]:
    """(media, semiamplitud, desviación) del IC de la media, aproximación normal."""
    if not datos:
        return 0.0, 0.0, 0.0
    media = statistics.fmean(datos)
    if len(datos) < 2:
        return media, 0.0, 0.0
    s = statistics.stdev(datos)
    z = 1.959964 if nivel == 0.95 else 2.575829
    return media, z * s / math.sqrt(len(datos)), s


def ic_proporcion(exitos: int, n: int, nivel: float = 0.95) -> tuple[float, float]:
    """IC binomial de una proporción (aproximación normal)."""
    if n == 0:
        return 0.0, 0.0
    p = exitos / n
    z = 1.959964 if nivel == 0.95 else 2.575829
    return p, z * math.sqrt(max(p * (1 - p), 0.0) / n)


def resumir(resultados: list[ResultadoPartida], etiqueta: str = "") -> str:
    """Resumen estadístico de una tanda de réplicas."""
    n = len(resultados)
    dur, semi, sd = intervalo_confianza([float(r.turno_final) for r in resultados])
    comb, comb_semi, _ = intervalo_confianza([float(r.num_combates) for r in resultados])
    bajas, bajas_semi, _ = intervalo_confianza([r.bajas_totales for r in resultados])

    lineas = [f"── {etiqueta or 'réplicas'} (n = {n}) " + "─" * 30]
    lineas.append(f"   O1 duración .......... {dur:7.2f} ± {semi:5.2f} turnos  (σ={sd:.2f})")
    lineas.append(f"   O4 combates .......... {comb:7.2f} ± {comb_semi:5.2f}")
    lineas.append(f"   O4 bajas ............. {bajas:7.1f} ± {bajas_semi:5.1f}")

    inflexiones = [float(r.turno_inflexion) for r in resultados if r.turno_inflexion]
    if inflexiones:
        infl, infl_semi, _ = intervalo_confianza(inflexiones)
        lineas.append(f"   O5 punto de inflexión  {infl:7.2f} ± {infl_semi:5.2f} turnos "
                      f"({len(inflexiones)}/{n} partidas)")

    lineas.append("   O2 tasa de victoria por estrategia:")
    for est in ("AGRESIVA", "DEFENSIVA", "ECONÓMICA", "EQUILIBRADA"):
        victorias = sum(1 for r in resultados if r.estrategia_ganadora == est)
        p, semi_p = ic_proporcion(victorias, n)
        barra = "█" * int(round(p * 40))
        lineas.append(f"      {est:<12} {p:6.1%} ± {semi_p:5.1%}  {barra}")

    guerra = sum(1 for r in resultados if r.ganador_en_guerra_continua)
    lineas.append(f"   Validación predictiva §3.2.4: ganadores en guerra continua "
                  f"{guerra}/{n} ({guerra / n:.1%})")
    return "\n".join(lineas)


def exportar_csv(resultados: list[ResultadoPartida], ruta: Path) -> None:
    ruta.parent.mkdir(parents=True, exist_ok=True)
    with ruta.open("w", encoding="utf-8") as f:
        f.write(ResultadoPartida.cabecera_csv() + "\n")
        for r in resultados:
            f.write(r.fila_csv() + "\n")


def barrido_k_bajas(n: int = 30, valores: list[float] | None = None,
                    semilla_base: int = 20_260_805) -> list[ResultadoPartida]:
    """Análisis de sensibilidad de K_B — el parámetro que gobierna el bucle B2.

    §6.3 del documento predice que K_B afecta a la duración de la partida (O1)
    y a la intensidad bélica (O4). Se usan las **mismas semillas** en todas las
    variantes para que la comparación sea apareada.
    """
    valores = valores or [0.3, 0.5, 0.7, 0.9]
    todos: list[ResultadoPartida] = []
    for v in valores:
        par = Parametros().copia(k_bajas=v)
        todos.extend(replicas(n, semilla_base, par, "k_bajas", v))
    return todos
