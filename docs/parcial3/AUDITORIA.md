# Auditoría de conformidad — ¿están implementadas las ecuaciones del Parcial II?

**Fecha:** 19 de agosto de 2026 · **Alcance:** commit `6e6fa6e` (reescritura del motor
Java por A. Carvajalino) contrastado contra el *Modelo conceptual* (Parcial I) y el
*Modelo formal* (Parcial II), más las correcciones aplicadas a raíz de la auditoría.

Para las brechas del entregable (informe, experimentos, figuras, defensa) ver
[`PENDIENTES.md`](PENDIENTES.md); este documento no las repite.

---

## 1. Veredicto

| | `sim/` (Python, 2.912 líneas) | `src/` (Java, 3.519 líneas) |
|:--|:--|:--|
| Ecuaciones del Parcial II | **completas** | **completas salvo moral y LEF** |
| Verificación | traza dorada 44/44 · fronteras 34/34 | 86/86 pruebas JUnit |
| Estado tras la auditoría | sin cambios | **6 defectos corregidos** |

Sí, las ecuaciones se crearon. El motor Java pasó de ser un clon heurístico a una
segunda implementación del modelo formal, y sus fórmulas son fieles al documento. Pero
la reescritura dejó **seis defectos** —dos de ellos funcionales y graves— que esta
auditoría corrige y verifica.

---

## 2. Cobertura de ecuaciones

Verificada leyendo cada fórmula del PDF contra el código, no por los comentarios.

| Ec. | Contenido | `sim/` | `src/` (tras corrección) |
|:--|:--|:--:|:--|
| 3.1–3.3 | Renta $I_p$, recaudación $R_i$, coste $C_i$ | ✅ | ✅ `TurnEngine.resolveEconomy` |
| 3.4–3.5 | Tesoro $G_i$, gasto discrecional $X_i$ | ✅ | ✅ |
| 3.6–3.7 | Descontento $\Delta D_p$ y clamp | ✅ | ✅ `resolveEndOfTurn` |
| 3.8 | Tasa de equilibrio $\theta^{\text{eq}}$ | ✅ | ✅ `GreedyAgent.thetaEq` |
| 3.9 | Tamaño sostenible $n^{\max}$ | ✅ | — resultado analítico, no operativo |
| 3.10 | Población con saturación y daño de guerra | ✅ | ✅ |
| 3.11 | Reclutamiento $u_i=\lfloor f_{\text{rec}}G_i/c_u\rfloor$ | ✅ | ✅ |
| 3.12 / 3.12b | Poder militar $M_i$ / defensa $\mathcal{D}_p$ | ✅ | ✅ |
| 3.13 | Coste de movimiento | ✅ | ✖ no aplica: WEGO mueve a adyacentes |
| **3.14–3.15** | Potencias $P_a$, $P_d$ | ✅ | ✅ **corregido (D1)** |
| 3.16–3.17 | $\Phi(\phi)=1+\beta_F\phi$ · $\Psi(D)=1-\psi D/100$ | ✅ | ✅ |
| 3.18–3.21 | Victoria, bajas, supervivientes | ✅ | ✅ |
| 3.23–3.26 | Triangular por transformada inversa · LCG 48 bits | ✅ | ✅ idéntico ($a$=25214903917, $c$=11, $m=2^{48}$) |
| **3.27–3.29** | Moral: techo, regeneración, desgaste | ✅ | ✖ **no implementada** (D3) |
| 3.30–3.32 | Guerra, coalición anti-líder, alianzas con histéresis | ✅ | ✅ `resolveAutoDiplomacy` |
| 4.1–4.4 | Reloj $\tau=t+\varphi$, LEF, función de llegada | ✅ | ✖ no aplica: paradigma WEGO |
| **5.1** | Dominios de las 12 variables | ✅ | ✅ **corregido (D5)** |
| 5.2 | Insolvencia con deserción forzosa | ✅ | ⚠️ sin el orden de deserción de §5.2 |
| 5.6 | Asedio: $\phi \leftarrow \max(0,\phi-1)$ | ✅ | ✅ |
| §2.4.6 | Matriz de terreno $\mathcal{T}$ | ✅ | ✅ **corregido (D1)** |
| §2.4.9 | Tabla de estrategias (6 parámetros × 4) | ✅ | ✅ valor por valor |
| Anexo A | Los 35 parámetros | ✅ | ✅ los declarados coinciden |

Las dos ausencias del motor Java (**moral** y **LEF**) no son descuidos sino
consecuencias del paradigma: sin entidad Ejército con identidad propia no hay $\mu_a$
que actualizar, y un motor de incremento fijo no tiene lista de eventos futuros. Ahora
están **declaradas** en el código y en el README, que es lo que faltaba.

---

## 3. Defectos encontrados y corregidos

| # | Defecto | Evidencia | Corrección |
|:--|:--|:--|:--|
| **D1** | Ec. 3.14 recibía `TerrainType.LLANURA` como terreno del atacante en vez del de la provincia disputada → la columna ATQ de la matriz $\mathcal{T}$ **no tenía ningún efecto** (siempre 1.00, nunca 0.80 en montaña) | `TurnEngine:507` | pasa `target.terrain()` a ambas potencias |
| **D2** | **La victoria por cuota $\Theta_V$ no existía.** `thetaV = 0.60` estaba declarado y nunca se leía; E9 solo terminaba por eliminación total o $t_{\max}$ | las 20 partidas de prueba agotaban los 200 turnos | `checkVictory` comprueba $q_\ell \ge \Theta_V$; y $q_\ell$ se calcula sobre provincias **de tierra** (con las marítimas en el denominador la cuota jamás se alcanzaba) |
| **D3** | Comentarios falsos: la cabecera de `TurnEngine` y el javadoc de `resolveEndOfTurn` prometían la ec. 3.28 (moral), que no se implementa | `TurnEngine:29`, `:632` | limitación declarada explícitamente en el código y en el README |
| **D4** | `ScenarioLoader` recortaba en silencio la población a $L_{\max}$: las 18 provincias de `europa_antigua.json` (200 000–800 000 hab) **colapsaban todas a 20 000**, borrando la heterogeneidad económica del mapa | `ScenarioLoader:91` | el escenario se reescala al rango del modelo (÷80 → 2 500–10 000) y el cargador **rechaza** con error descriptivo lo que exceda $L_{\max}$ |
| **D5** | Consecuencia de D4: seis pruebas de `GreedyAgentTest` usaban poblaciones de 500 000 y solo pasaban gracias al recorte silencioso | 6/86 fallos al corregir D4 | fixtures reescalados ÷50, proporciones intactas |
| **D6** | Métrica fantasma: `GameResult.revolts` y la salida «revueltas/partida» seguían nombrando las revueltas Monte Carlo, eliminadas en la reescritura; el contador mide en realidad **insolvencias** | `BatchRunner:65` | renombrado a `insolvencies` en las tres clases, el CSV y la prueba |

**Verificación tras las correcciones:** `javac` limpio · **86/86 pruebas JUnit** ·
traza dorada Python **44/44** · fronteras **34/34** · `--turnos 5` sin regresiones.

### Efecto medible de D1 + D2 + D4

Antes: las 20 partidas terminaban en el turno 200 y las poblaciones eran idénticas.
Después, con 100 partidas por variante, el motor produce dinámica real y los
experimentos discriminan:

| Experimento | Hallazgo |
|:--|:--|
| `base` | duración media **100.8** turnos (rango 19–200) |
| `sensibilidad_fiscal` | **η_θ domina la partida**: con η_θ ≥ 0.10 *todas* las réplicas agotan los 200 turnos y las insolvencias saltan a 394–580 por partida. Es la vía de derrota puramente económica de §5.2(b), reproducida |
| `fortificacion` | β_F es **no monótono**: 0.10 alarga la partida a 167.6 turnos, ≥ 0.25 la acorta a ~40 |
| `desgaste` | K_B mueve al ganador (Galia 98 % → 61 %) más que a la duración |

---

## 4. Lo que la reescritura cambia en el informe

El §1.3 de `PARCIAL3.md` describía el clon Java como *«un modelo distinto: combate
determinista, felicidad, revueltas, puntos de acción»*, con 4.470 líneas y 98 pruebas.
Nada de eso sigue siendo cierto. Se reescribió, y el cambio **es favorable**: ahora hay
dos implementaciones independientes del mismo modelo formal bajo paradigmas de tiempo
distintos, que es exactamente la *model-to-model validation* que la tabla de §4.1
marcaba como «parcial». Convertirla en una comparación real es el trabajo barato de
mayor rendimiento que queda pendiente.

También se actualizó el README, que afirmaba «combate determinista», «se conserva sin
cambios», «98 pruebas», cuatro experimentos con parámetros inexistentes
(`fortDefenseBonus`, `combatAttrition`, `revoltRiskK`) y hallazgos de un motor que ya
no existe.

---

## 5. Pendiente

**Del motor Java** (ninguno bloqueante):

- El orden de deserción por insolvencia no sigue §5.2 (primero los ejércitos más
  alejados de la capital, guarniciones al final): la implementación recorre las
  provincias en el orden del mapa. Declararlo o implementarlo.
- `resultados/revueltas.csv` es la salida de un experimento eliminado — borrarlo.
- `docs/subsistema_economico.py` es una **tercera implementación** de las ec. 3.1–3.9,
  huérfana (nadie la importa) y duplicada de `sim/economia.py`. Fiel al documento, pero
  confunde sobre cuál es el simulador: integrarla como material didáctico del informe o
  retirarla.
- Los cuatro parámetros de moral de `Rules` quedan reservados sin uso; el comentario ya
  lo dice.

**Del entregable:** las ocho brechas de [`PENDIENTES.md`](PENDIENTES.md), con dos
cambios de estado desde ayer:

- **B8 publicación — resuelto.** `main` está sincronizado con `origin/main`. Queda subir
  el PDF a Moodle.
- **B7 equipo — parcialmente resuelto.** A. Carvajalino ya tiene un commit propio
  (`6e6fa6e`, +2.471 líneas). Sigue faltando `CONTRIBUCIONES.md` y el anexo de reparto.
- B1 (O3 sin medir), B2 (series no exportadas), B3 (predicado E5), B4 (3 de 4 barridos
  de §6.3), B5 (comparación con el juego real), B6 (figuras): **sin cambios**.

> **Nota sobre B3.** El predicado de E5 en `sim/eventos.py` sigue sin la segunda
> condición de la tabla 4.3, y la doble conquista continúa apareciendo en la traza. No
> se ha tocado en esta pasada porque corregirlo altera todas las cifras publicadas en
> §4.2, §4.3 y §4.4 del informe, y exige el ciclo completo de regeneración descrito en
> `PENDIENTES.md` (paso 1 → paso 8). Es la primera tarea de esa secuencia.

Tras estos cambios hay que **regenerar `PARCIAL3.pdf`**: el `.md` se editó y el PDF
publicado ya no lo refleja.
