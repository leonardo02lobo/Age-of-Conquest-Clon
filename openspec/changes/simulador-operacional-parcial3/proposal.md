## Why

El Parcial III exige **materializar las ecuaciones y diagramas del Parcial II en un modelo
operacional computarizado**. El repositorio contiene hoy un clon jugable de *Age of Conquest IV*
(fases M1–M6, 4.470 LOC, 98 pruebas verdes) que **no implementa el modelo del Parcial II**: son dos
modelos distintos del mismo sistema, construidos por caminos distintos (`PLAN.md` = ingeniería
inversa del juego; `docs/parcial2/PARCIAL2.md` = modelo formal de eventos discretos). Defender el
código actual como implementación del documento entregado fallaría en la *Sustentación Técnica*, que
es la mitad de la nota del parcial.

### Inventario de la divergencia

| Concepto | `docs/parcial2/PARCIAL2.md` (documento entregado) | `src/` (código actual) |
|---|---|---|
| Avance del tiempo | LEF (montículo), reloj $\tau=t+\varphi$, clave $(\tau,\pi,\varsigma)$ | incremento fijo: `GameState.advanceTurn()` → `turn++` |
| Eventos | 10 eventos E1–E10 con fase y predicado de revalidación | secuencia fija de llamadas dentro de `TurnEngine.endTurn()` |
| Moral civil | **Descontento** $D_p\in[0,100]$ *creciente*, umbral fiscal $D^\ast=60$ | **Felicidad** $H\in[0,100]$ *decreciente*, umbral 50 |
| Terreno | 4 tipos, matriz $\mathcal{T}$ (ATQ/DEF) y coste de cruce $w(T)$ | inexistente |
| Ejércitos | entidades móviles persistentes, $\mu_a$, $A_{\max}=4$, $F_{\min}$ | tropas como entero por provincia |
| Moral militar | $\mu_a$ con techo por distancia a la capital (3.27)–(3.29) | inexistente |
| Azar del combate | $U_a,U_d\sim\text{Triangular}(0.8,1,1.2)$, LCG explícito, transformada inversa | combate **100 % determinista** |
| Azar del modelo | ninguna otra fuente | **revueltas Monte Carlo** (única fuente de azar) |
| Fortificación | niveles $\{0..4\}$, $\Phi=1+0.15\phi$, asedio $-1$ al caer | booleana, $+50\%$ |
| Liderazgo | **capital** $c_i$ reasignable; define el techo de moral | **rey**: $+30\%$ en combate, muerte $\Rightarrow -90\%$ territorio |
| Puntos de acción | no existen | sistema AP (`Rules.actionPointsFor`) |
| Tasa impositiva | continua $[0,150]$, decidida cada turno | discreta $\{0,50,100,150,200\}$, solo en temporada fiscal (cada 5 turnos) |
| IA | 4 estrategias (AGR/DEF/ECO/EQU) con vector de parámetros | 1 `GreedyAgent` |
| Diplomacia | guarda diplomática, coalición anti-líder B3, alianzas con histéresis | solo declaración de guerra |
| Victoria | $q_\ell\ge\Theta_V=0.60$ ∨ $m=1$ ∨ $t\ge 200$ | dominación total o límite de turnos |
| Insolvencia | deserción forzosa (5.2) + Proposición 3 | oro negativo **sin consecuencia** (`Nation.setGold`) |
| Escenario | $N=24$, 4 imperios × 3 provincias, terrenos, $s_0=20260805$ | 23 provincias (18 tierra + 5 mar), naciones históricas, $s_0=20260705$ |
| Mecánicas solo en el código | — | agua/viaje naval, saqueo, decretos, festival, revueltas, rey |

El documento del Parcial II es además **autocontenido y demostrado** (Teoremas 1–3, 7 funciones por
partes, 9 casos degenerados, traza de escritorio numérica en §4.7). Es una especificación ejecutable
casi línea a línea: implementarla es el camino de menor riesgo y máxima nota, no el más caro.

## What Changes

- **Se construye un motor de simulación por eventos discretos nuevo**, fiel al documento del Parcial II,
  en un árbol de paquetes propio (`src/des/…`), con **trazabilidad explícita ecuación ↔ código**
  (cada método anota la ecuación o el evento del documento que implementa).
- **BREAKING (para el proyecto, no para el código existente):** el modelo operacional que se defiende
  pasa a ser el del Parcial II. El clon M1–M6 (`src/model`, `src/engine`, `src/ai`, `src/ui`) **se
  conserva intacto** como artefacto de contexto y demo secundaria; no se borra ni se refactoriza.
- Se implementan: LEF con clave $(\tau,\pi,\varsigma)$ y predicados de revalidación; los 10 eventos
  E1–E10 con sus fases; renta/descontento/población/tesoro con la regla de insolvencia; terreno,
  movimiento con función de llegada (4.4), moral, y combate con generación de variables aleatorias
  por LCG + transformada inversa; diplomacia con guarda, coalición anti-líder e histéresis; las 4
  estrategias de IA; y el escenario de referencia de 24 provincias del Anexo C.
- Se añade una **interfaz operacional** (consola interactiva + modo lote) que cumple el requisito del
  enunciado de *"ejecutar al menos cinco (5) fases consecutivas"* con entrada de variables (tropas,
  tasa impositiva) e inspección del estado tras cada evento.
- Se añade una **suite de verificación y validación**: la traza de escritorio de §4.7 como prueba
  dorada exacta, una prueba por ecuación, aserciones de los Teoremas 1–3, forzado de los 12 dominios
  y 9 casos degenerados, y un experimento de calibración de los parámetros `[C]` contra observaciones
  del juego real con tablas y gráficas.
- Se produce el **Informe Técnico Final en PDF** reutilizando la cadena de construcción ya existente
  en `docs/parcial2/.build/`, más el guion de la defensa en vivo.

### Preocupaciones que se declaran por anticipado

1. **"Coincide con el juego real"** — 2 de cada 3 parámetros del Parcial II están marcados `[M]`
   (decisión de modelado), no `[D]` (documentado). La validación literal contra *Age of Conquest IV*
   solo es posible en los `[D]`. Se adopta una validación en tres niveles (verificación contra el
   documento → validación estructural contra hechos documentados del juego → calibración numérica de
   los `[C]` contra observaciones capturadas del juego real) y se declara el alcance en el informe.
2. **Historial de contribuciones** — el enunciado dice que se evaluará *"el historial de
   contribuciones del equipo"*. El repositorio tiene 7 commits de un solo autor y un `first commit`
   que aplana el historial. Se recomienda repartir el trabajo por subsistema entre los dos
   integrantes y commitear cada uno el suyo desde el inicio de esta fase.
3. **Fichero de datos del Anexo C** — el documento afirma que la especificación del mapa de 24
   provincias *"acompaña a este documento como fichero de datos"*; ese fichero no existe en el
   repositorio. Hay que construirlo respetando las restricciones declaradas (24 provincias, grafo
   conexo, grado medio ≈ 3,5, 10 LLANURA / 6 BOSQUE / 4 MONTAÑA / 4 COSTA, 4 imperios × 3 provincias,
   12 neutrales).

### Alternativas consideradas

| Ruta | Descripción | Veredicto |
|---|---|---|
| **A. Motor DES nuevo, clon conservado** | Implementar el Parcial II en `src/des/`, reutilizando la infraestructura probada (patrón de carga JSON, `BatchRunner`, exportación CSV, `MapPanel`) | **Elegida.** Fidelidad total al documento, sin romper 98 pruebas ni la GUI existente; el clon sigue disponible como demo de contexto |
| **B. Refactorizar el motor actual** | Convertir `TurnEngine` en un motor de LEF y sustituir felicidad→descontento, añadir terreno, ejércitos, moral… | Descartada. Toca las 98 pruebas, el formato de escenario, la GUI y el `BatchRunner`; el resultado es indistinguible de A pero destruye el activo existente y arriesga quedarse a medias |
| **C. Documentar el código actual y declarar desviaciones** | Escribir el informe sobre lo que ya hay, justificando cada divergencia | Descartada. Contradice el objetivo literal de la evaluación (*"materializar las ecuaciones del parcial anterior"*) y expone el proyecto en la sustentación técnica |
| **D. Reescribir en Python por la parte estadística** | Motor en Python + matplotlib para validación | Descartada como motor. Se adopta parcialmente: el motor queda en Java (código y pruebas existentes, Java 21+) y la gráfica se genera desde los CSV que el motor ya sabe exportar |

## Capabilities

### New Capabilities
- `motor-eventos-discretos`: reloj $\tau=t+\varphi$, LEF como montículo con clave $(\tau,\pi,\varsigma)$, despachador con revalidación, catálogo y planificación de los eventos E1–E10, función de llegada (4.4) y garantías de causalidad y terminación (Teoremas 1–3).
- `modelo-estado-formal`: vector de estado $S(\tau)$, tabla de parámetros del capítulo 2, dominios y saturaciones de las 12 variables acotadas, las 7 funciones por partes, los 9 casos degenerados, y el formato + carga + validación del escenario de referencia del Anexo C.
- `subsistema-economico-demografico`: renta provincial (3.1), recaudación y coste (3.2)–(3.3), ecuación del tesoro (3.4)–(3.5), descontento (3.6)–(3.7), tasa de equilibrio (3.8), tamaño sostenible (3.9), población (3.10) y regla de insolvencia (5.2).
- `subsistema-militar`: matriz de terreno, coste de movimiento (3.13), reclutamiento (3.11), poder militar y fuerza defensiva (3.12)–(3.12c), potencias y criterio de combate (3.14)–(3.18), bajas (3.19)–(3.21), moral (3.27)–(3.29) y generación de variables aleatorias por LCG + transformada inversa (3.23)–(3.26).
- `subsistema-diplomatico`: matriz simétrica $\delta_{ij}$, guarda diplomática de los movimientos, declaración de guerra (3.30) con coalición anti-líder, formación (3.31) y ruptura con histéresis (3.32) de alianzas.
- `agentes-estrategia`: las cuatro políticas AGR/DEF/ECO/EQU como vector de parámetros, el árbol de decisión de §4.6, la selección de objetivos por $\gamma_{\text{atq}}$ y el BFS multifuente de refuerzo de frontera.
- `interfaz-operacional`: ejecución interactiva por consola con entrada de variables e inspección paso a paso de al menos cinco fases consecutivas, modo espectador, modo lote con semillas apareadas y exportación CSV, y modo de demostración de casos borde.
- `verificacion-y-validacion`: prueba dorada de la traza de escritorio de §4.7, pruebas unitarias por ecuación, aserciones de los Teoremas 1–3, forzado de dominios y casos degenerados, protocolo de captura de observaciones del juego real y calibración de los parámetros `[C]` con tablas y gráficas.
- `informe-tecnico-y-defensa`: Informe Técnico Final en PDF (alcance, arquitectura, validación y calibración, análisis de casos borde) generado con la cadena de `docs/parcial2/.build/`, más el guion y el escenario reproducible de la demostración en vivo.

### Modified Capabilities
<!-- Ninguna: openspec/specs/ está vacío; el clon M1–M6 no tiene specs registradas y se conserva sin cambios de requisito. -->

## Impact

- **Código nuevo:** `src/des/` (modelo de estado, LEF y reloj, eventos, subsistemas, agentes, aleatorios),
  `src/des/ui/` (consola operacional), `src/des/sim/` (lotes y experimentos), `test/des/` (pruebas).
- **Código existente:** sin cambios. `src/model`, `src/engine`, `src/ai`, `src/ui`, `src/sim` y las 98
  pruebas actuales siguen compilando y pasando; el `App` actual mantiene su comportamiento y se le
  añade un punto de entrada separado para el simulador operacional.
- **Datos nuevos:** `scenarios/referencia24.json` (mapa del Anexo C con terrenos), `resultados/p3/*.csv`.
- **Documentación:** `docs/parcial3/` (informe, plantilla, checklist frente al enunciado, guion de
  defensa), reutilizando `docs/parcial2/.build/`.
- **Dependencias:** ninguna nueva. Java 21+, `lib/gson-2.11.0.jar`, `lib/junit-platform-console-standalone-1.10.2.jar`.
- **Riesgos:** volumen de trabajo (motor completo desde cero), disponibilidad del juego real para
  capturar observaciones de validación, y el historial de contribuciones del equipo.
