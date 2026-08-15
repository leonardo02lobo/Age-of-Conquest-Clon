# Checklist — Parcial II

Estado de cada requisito del enunciado (`spec.yaml`) frente a lo que **ya existe**
en el clon (fases M1–M6, 98 pruebas verdes) y frente a la **plantilla** del
documento (`PLANTILLA.md`).

**Leyenda de estado del modelo:**
`✅ implementado` — la mecánica existe y está probada en el código ·
`🟡 parcial` — existe pero le falta algo · `❌ ausente` — no hay nada en el código.

**Leyenda de trabajo de redacción:** lo que hay que escribir, independientemente
de si el código ya lo hace. El entregable es el **documento**, no el programa.

---

## Resumen ejecutivo

| Entregable | Requisitos | Código | Redacción pendiente |
|---|---|---|---|
| E1 Diccionario | 3 | ✅ todo el estado y los parámetros existen | tabular + unidades + procedencia |
| E2 Ecuaciones | 6 | ✅ 5 de 6 (falta formalizar el RNG) | derivar, justificar, balancear |
| E3 Algoritmos | 3 | ✅ turno · ✅ IA · ❌ **LEF** | pseudocódigo + 2 diagramas + resolver la LEF |
| E4 Fronteras | 2 | 🟡 falta la regla de insolvencia | tabla de dominios + 6 funciones por partes |

**Ruta crítica:** `E3.R3` (LEF/reloj) → `E4.R1` (insolvencia) → `E2.R5` (formalizar
la generación de aleatorios). Todo lo demás es transcribir y justificar lo que
el código ya hace.

---

## E1 — Diccionario Formal de Variables y Parámetros

- [ ] **E1.R1 — Clasificar variables (estado / flujo / auxiliares)** — 🟡 código completo, tabla por construir
  - ✅ Estado de provincia en `src/model/Province.java:19-24` — población, tropas, felicidad, fortificación, dueño.
  - ✅ Estado de nación en `src/model/Nation.java:17-22` — oro, AP, tasa, sede del rey, eliminada, relaciones.
  - ✅ Reloj en `src/model/GameState.java:22`.
  - Falta: separar explícitamente **flujos** (recaudación, gasto, crecimiento, ΔH, bajas) — hoy viven implícitos dentro de `resolveEconomy`, sin nombre propio.
  - Falta: nombrar las **auxiliares** que el código ya calcula sin bautizar (fuerza efectiva en `CombatResolver`, descontento $d$ en `resolveRevolts:531`, defensa efectiva en `GreedyAgent:170-178`).
  - → rellenar `PLANTILLA.md` §1.1, §1.2, §1.3.

- [ ] **E1.R2 — Unidades de medida** — ❌ nada escrito, es trabajo nuevo
  - El código no documenta unidades salvo en comentarios sueltos de `Rules.java`.
  - Cuidado con: `maxTaxGoldPerProvince` es oro/(provincia·turno) **a población y tasa máximas**, no oro absoluto; `troopsPerUpkeepGold` es soldados por oro (inverso de un coste).
  - → rellenar la columna "Unidad" de todas las tablas de `PLANTILLA.md` §1.

- [ ] **E1.R3 — Parámetros fijos con procedencia** — ✅ código completo, falta clasificar
  - ✅ Los ~45 parámetros ya están centralizados y comentados en `src/model/Rules.java`.
  - ✅ Varios ya vienen marcados en los comentarios como "documentado del juego real" (bono del rey 0.30, fortificación 0.50, población máxima 1M, 250 oro, 20 soldados/oro, tasas {0,50,100,150,200}, pérdida del 90% al morir el rey, costes de decretos).
  - ✅ Los calibrables ya tienen respaldo experimental de M5 (100 partidas/variante): `fortDefenseBonus` es no lineal (0 → partida de 30 turnos; 1.0 → 134 turnos y cambia de ganador), `combatAttrition` decide el vencedor (0.5 → Grecia 100%; ≥0.7 → Galia), `revoltRiskK = 0` vuelve la partida totalmente determinista.
  - Falta: la columna de procedencia `[D]/[M]/[C]` y la tabla de costes de órdenes.
  - → rellenar `PLANTILLA.md` §1.4 (falta la tabla de costes en AP/oro, fuente `Rules.java:48-137`).

---

## E2 — Formulación del Modelo Matemático *(35% de la nota)*

- [ ] **E2.R1 — Ecuación del tesoro** — ✅ implementado en `src/engine/TurnEngine.java:481-509`
  - La ecuación existe tal cual: renta condicionada a $H \ge 50$, mantenimiento $\lceil M/20 \rceil$, administración $1$ oro/provincia.
  - Falta redactar: por qué el mantenimiento usa **techo** y no proporción continua; el gasto discrecional $C_{n,t}$ desglosado; el punto de equilibrio fiscal.
  - Falta: balance dimensional (E2.R6).

- [ ] **E2.R2 — Dinámica poblacional** — ✅ implementado (crecimiento `:504-505`, recluta `:161-168`, saqueo `:464-466`, festival `:339-341`)
  - ⚠ **Sutileza que hay que declarar:** el reclutamiento descuenta población en la **fase de emisión** de la orden, mientras que el crecimiento se aplica en la **fase económica**. El orden cambia el resultado numérico; si no se documenta, la ecuación no es reproducible por un tercero.

- [ ] **E2.R3 — Dinámica de la moral** — ✅ implementado en `TurnEngine.java:499-507`
  - Falta el análisis que da puntos: punto fijo $\tau^{eq} = 100 + (h_0 - w\,\mathbb{1}[guerra])/\beta$ → **125 en paz, 75 en guerra**. Consecuencia: en guerra, cualquier tasa ≥ 100 hunde la moral hasta cruzar el umbral fiscal.

- [ ] **E2.R4 — Modelo de combate** — ✅ implementado en `src/engine/CombatResolver.java:27-49`
  - Lanchester discreto de una pasada: fuerza = tropas × (1 + bonos); gana quien tenga más fuerza; supervivientes $\max(1, \mathrm{round}(T(1-\varphi F_{perd}/F_{gan})))$; el perdedor se aniquila; empate exacto para el defensor; $T_D=0$ → ocupación sin bajas.
  - Falta redactar: **la comparación explícita con la ley cuadrática de Lanchester** (el enunciado la nombra). Argumentar por qué una sola pasada, y hacer notar que el desgaste depende solo del **cociente** de fuerzas, no del tamaño absoluto.
  - Falta: la tabla de ejemplos numéricos de `PLANTILLA.md` §2.4 (es también el banco de pruebas del Parcial III).

- [ ] **E2.R5 — Generación de variables aleatorias** — 🟡 implementado pero **sin formalizar**
  - ✅ Existe: `TurnEngine.java:528-555` con $q = \min(0.9,\ \kappa d^2)$, supresión ×0.5 por guarnición, sorteo `state.random().nextDouble()`.
  - ✅ Semilla fija `Rules.randomSeed = 20260705` → partidas reproducibles; `BatchRunner` ya usa **semillas apareadas** entre variantes.
  - ❌ **Falta en el documento** (el enunciado lo pide literalmente: *"Leer generación de variables aleatorias"*):
    - [ ] Método congruencial lineal: $X_{k+1} = (aX_k + c) \bmod m$ con los valores reales de `java.util.Random` ($a=25214903917$, $c=11$, $m=2^{48}$) y su periodo.
    - [ ] Transformada inversa para generar la Bernoulli a partir de $U(0,1)$.
    - [ ] Justificación de la semilla fija y del uso de semillas apareadas como reducción de varianza.

- [ ] **E2.R6 — Balance dimensional** — ❌ trabajo nuevo, barato y puntúa directo
  - La rúbrica dice literalmente "dimensionalmente balanceadas". Una tabla de cinco filas cubre el criterio.

---

## E3 — Diseño Algorítmico y Lógica de Decisión *(35% de la nota)*

- [ ] **E3.R1 — Ciclo de fin de turno** — ✅ implementado en `src/engine/TurnEngine.java:286-319`
  - Orden real: planificación WEGO (costes cobrados al emitir) → decretos → fortificar → reclutar → mover/combatir → saquear → economía → revueltas → eliminaciones → victoria → $t{+}1$ y refresco de AP.
  - Dentro de la economía: recaudación → mantenimiento → crecimiento poblacional → moral.
  - ⚠ **Discrepancia con el enunciado que hay que justificar por escrito:** el enunciado sugiere *"primero crecimiento de población, luego recaudación"*; el motor recauda **antes** de crecer. Es defendible (el censo fiscal se levanta al inicio del turno) pero sin la justificación se lee como un error frente al criterio de Lógica Algorítmica.
  - Falta: pseudocódigo (ya esbozado en la plantilla) + diagrama de flujo + los cuatro párrafos de justificación del orden.

- [ ] **E3.R2 — Árbol de decisión de la IA** — ✅ implementado en `src/ai/GreedyAgent.java:61-77`
  - Política de 7 pasos con umbrales públicos (`GreedyAgent.java:38-58`): ajuste fiscal por felicidad media (50/80), hasta 3 fiestas si $H<45$, ataque con ventaja ≥1.5× sobre la defensa efectiva, guerra oportunista con superioridad ≥2×, refuerzo de frontera por BFS, fortificar la sede del rey, reclutar sobre una reserva de 30 oro.
  - ✅ Determinista y verificada: partida IA vs IA completa y reproducible sobre Europa Antigua.
  - Falta: el diagrama de árbol (esbozado en la plantilla) y el pseudocódigo del BFS de refuerzo.

- [ ] **E3.R3 — LEF y reloj de la simulación** — ❌ **AUSENTE en el código. Riesgo principal del entregable.**
  - El motor avanza por **incremento fijo de tiempo**: `GameState.advanceTurn()` (`src/model/GameState.java:55`) hace $t \leftarrow t+1$ y las fases del turno son una secuencia fija de llamadas. **No existe cola de eventos ordenada por tiempo.**
  - Lo que sí existe y sirve de materia prima:
    - Eventos **periódicos**: fin de turno (cada 1), temporada fiscal (cada 5 turnos, `Rules.isTaxSeason:155-157`), refresco de AP.
    - Eventos **condicionales**: revuelta, muerte del rey, huida del rey, eliminación de nación, fin de partida.
    - Prioridades implícitas: los reyes mueven antes que el resto (`TurnEngine.java:373-374`), y las órdenes se revalidan al resolver (una provincia conquistada antes de su saqueo cancela el saqueo, `:460`) — que es exactamente la **cancelación de eventos** de una LEF.
  - **Decisión pendiente** (elegir una antes de redactar):
    - [ ] **Opción A — reinterpretación formal (recomendada).** Documentar el modelo como simulación por incremento fijo de tiempo y demostrar que es un caso particular de LEF: las 10 fases son eventos con prioridad fija en el mismo instante $t$; los eventos genuinamente diferidos (temporada fiscal) se programan en la cola. Coste: solo redacción, el código no cambia.
    - [ ] **Opción B — implementar una LEF real.** Refactorizar `endTurn()` sobre una `PriorityQueue<Evento>` con clave $(t, \text{prioridad}, \text{secuencia})$. Da trazabilidad 1:1 documento↔código pero toca el motor y las 98 pruebas.
  - → `PLANTILLA.md` §3.3 ya trae la estructura del evento, el catálogo, la relación de orden y el bucle principal; falta elegir la opción y escribir la argumentación.

---

## E4 — Condiciones de Frontera y Puntos Críticos *(15% de la nota)*

- [ ] **E4.R1 — Límites de las variables** — 🟡 casi todo implementado, **un hueco real**
  - ✅ Felicidad acotada a $[0,100]$ (`src/model/Province.java:76-78`).
  - ✅ Población $\ge 0$ y con techo $10^6$ (`Province.java:64-69`, `TurnEngine.java:504`).
  - ✅ Tropas $\ge 0$; el ganador de un combate conserva siempre ≥1 soldado (`CombatResolver.java:47-49`); $T_D=0$ → ocupación sin bajas (`:33`).
  - ✅ AP $\ge 0$; orden rechazada si no alcanza (`TurnEngine.java:274-281`).
  - ✅ Territorio 0 → eliminación en el barrido (`TurnEngine.java:602-608`).
  - ❌ **HUECO — insolvencia.** `Nation.setGold` (`src/model/Nation.java:49-51`) permite oro negativo **sin ninguna consecuencia mecánica**: el comentario lo llama "deuda" pero nada la penaliza. El enunciado pregunta literalmente qué ocurre "cuando el déficit económico supera las reservas".
    - [ ] Definir la regla en el documento (propuesta en `PLANTILLA.md` §4.1: deserción de tropas proporcional al impago y $E \leftarrow 0$).
    - [ ] Verificar que la regla no oscila (deserción → menos mantenimiento → superávit → recluta → deserción…).
    - [ ] Decidir si se implementa ya o queda para el Parcial III (el Parcial II solo exige el diseño).

- [ ] **E4.R2 — Funciones por partes** — 🟡 las discontinuidades existen, falta escribirlas
  - ✅ Umbral fiscal $H^{*}=50$: salto de renta a cero (`TurnEngine.java:487`) **y** activación del riesgo de revuelta (`:531`) — doble efecto en el mismo punto.
  - ✅ Muerte del rey: pérdida del 90% del territorio, conservando las provincias con mayor guarnición; las liberadas pasan a neutral **con su guarnición** (`TurnEngine.java:575-594`).
  - ✅ Combate: escalón sobre el signo de $F^A - F^D$, con el empate exacto a favor del defensor.
  - ✅ Saturación del riesgo de revuelta en $q_{max}=0.9$ (`:535-536`) — punto de saturación en $d=\sqrt{0.9/\kappa}\approx 47.4$, es decir $H \approx 2.6$.
  - ✅ Temporada fiscal: control disponible solo si $(t-1) \bmod 5 = 0$ (`Rules.java:155-157`).
  - ✅ Techo poblacional: crecimiento geométrico truncado.
  - Falta redactar las seis como funciones a trozos **y su efecto sobre la trayectoria del sistema** — sobre todo el lazo de realimentación positiva que abre el umbral fiscal: $H<50$ → renta 0 → sin oro para decretos → $H$ sigue cayendo → revuelta, con un retardo de hasta 5 turnos para poder bajar la tasa.

---

## Trabajo transversal

- [ ] **Coherencia de símbolos (15% de la nota).** Al terminar, extraer el conjunto de símbolos usados en §2, §3 y §4 y comprobar que está contenido en las tablas de §1. Cualquier símbolo huérfano cuesta puntos directos.
- [ ] **Autosuficiencia.** Releer buscando frases del tipo "como en el juego" o "el jugador sabe que": cada una es un agujero para el programador independiente que menciona la nota final del enunciado.
- [ ] **Anexos** (opcional, alta rentabilidad):
  - [ ] Escenario de referencia Europa Antigua (23 provincias, 4 naciones) — `scenarios/europa_antigua.json`.
  - [ ] Traza numérica de un turno completo, variable a variable.
  - [ ] Resultados de sensibilidad de M5 (`resultados/*.csv`, 2100 partidas) como evidencia empírica de la calibración.
- [ ] **Tabla de trazabilidad** modelo ↔ archivos del clon (`PLANTILLA.md` §5) — evidencia directa de que el documento es programable.

---

## Orden de ataque sugerido

1. **§1 completa** — es la base de coherencia de todo lo demás y se transcribe casi mecánicamente desde `Rules.java`, `Province.java` y `Nation.java`.
2. **§2.1–§2.4** — las ecuaciones ya existen en el código; el trabajo es derivarlas y justificarlas.
3. **§3.1 y §3.2** — pseudocódigo y diagramas a partir de `endTurn` y `GreedyAgent.plan`.
4. **Decidir E3.R3** (opción A o B) y escribir §3.3 — el punto de mayor riesgo.
5. **§4** — definir la regla de insolvencia y escribir las funciones por partes.
6. **§2.5 y §2.6** — formalizar el generador aleatorio y el balance dimensional.
7. **Pasada de coherencia** de símbolos y de autosuficiencia.
