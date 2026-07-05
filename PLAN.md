# Plan: Clon de *Age of Conquest IV*

Proyecto para la materia **Simulación de Sistemas (UNET)**.
Fecha: 2026-07-05. Basado en investigación de fuentes oficiales, foros de Steam, wiki de fans y búsqueda exhaustiva de plantillas en GitHub.

---

## 0. Resumen ejecutivo

- **No existe ningún clon ni plantilla directa de Age of Conquest en GitHub.** Sí existen proyectos que cubren partes del problema (clones de Risk, motores de estrategia por turnos, técnicas de mapas de provincias) que sirven como referencia.
- El juego real usa **turnos simultáneos (WEGO)**, **combate determinista**, economía de **impuestos sobre población**, **felicidad/revueltas**, diplomacia de 3 estados e **IA basada en algoritmos genéticos** — un sistema dinámico, discreto y parcialmente estocástico: encaja perfecto como **estudio de simulación** según la metodología del curso (Shannon: modelo → verificación → validación → experimentos).
- **Recomendación:** construir un motor propio en **Java** (ya existe el esqueleto del proyecto), separando el *modelo de simulación* (sin interfaz, ejecutable por lotes) de la *interfaz gráfica*. Usar los repos encontrados solo como referencia de reglas, IA y renderizado de mapas.

---

## 1. Análisis del juego (cómo funciona realmente)

### 1.1 Bucle central — turnos simultáneos (WEGO)
- Todos los jugadores planifican órdenes en paralelo; al cerrar el turno, **todas las órdenes se ejecutan a la vez**. No hay turnos secuenciales tipo Civilization.
- Cada acción consume **Puntos de Acción (AP)**; los AP por turno crecen con el número de provincias. Costes documentados: saquear = 1 AP, fortificar = 0.5 AP, festival de fertilidad = 0.5 AP/provincia.
- Partida típica: 30–40 turnos. En multijugador el turno dura tiempo real configurable (por defecto 24 h).
- Resolución de conflictos de órdenes: prioridad por orden de emisión dentro del turno; empates a favor de la nación más grande; los reyes mueven con prioridad.

### 1.2 Mapa
- **Provincias** contiguas tipo Risk (no hexágonos, no casillas), con grafo de adyacencia. Casillas de agua: las tropas embarcan automáticamente al hacer clic en el mar.
- Unidad especial **rey/gobernante**: si muere, el jugador pierde ~90 % del territorio o es eliminado (regla configurable).
- Sin niebla de guerra (todo el mapa visible — coherente con el diseño WEGO tipo Risk).

### 1.3 Militar
- Los ejércitos son **cifras de soldados** (sin tipos de unidad; todas iguales a igual tamaño). Sin límite de apilamiento. Unidades especiales: barcos (transporte) y rey.
- Movimiento: **1 provincia por turno**.
- **Combate 100 % determinista** (confirmado por el desarrollador): gana proporcionalmente quien tenga más fuerza. Modificadores conocidos: **+30 %** si el rey lucha con las tropas, **+50 %** para defensor fortificado. La fórmula exacta de bajas no es pública → la definiremos nosotros (ver §5.3).
- Reclutar consume **población** de la provincia + oro; el gasto militar por turno hace crecer las tropas existentes.

### 1.4 Economía
- Ingresos = **impuestos sobre la población** de cada provincia (tope ~250 oro/turno por provincia con 1 M de habitantes).
- Tasa impositiva elegible solo en la "tax season": 0/50/100/150/200 % — subirla desploma la felicidad.
- Gastos: mantenimiento militar (~1 oro por cada 20 soldados/turno), administración, fortificaciones, decretos (repartir dinero, fiestas de inauguración, gobernadores), festivales, regalos diplomáticos.
- **Saqueo**: convierte población enemiga en oro (1 AP).

### 1.5 Población y felicidad
- Población por provincia (tope 1 M), crece con el tiempo; el festival de fertilidad da +20 % de una vez. Reclutar la consume.
- **Felicidad** por provincia (0–100 %): bajo 50 % la provincia deja de pagar impuestos y aparece **riesgo de revuelta** (los foros citan riesgo ≈ k·(50 − felicidad)² — no verificado oficialmente). Al rebelarse la provincia se vuelve neutral. Guarnecer reduce el riesgo. **Este es el componente estocástico natural del modelo → Monte Carlo.**

### 1.6 Diplomacia
- Tres estados: **guerra / neutral / alianza**. Acciones: declarar guerra, paz, alianza, enviar oro, **exigir sumisión** (protectorado que paga tributo).
- La IA reacciona a tu comportamiento: guerras contra amigos de una IA acumulan descontento con el tiempo; declarar "guerras falsas" penaliza felicidad.

### 1.7 IA
- La IA original usa **algoritmos genéticos** (diseñada con el Dr. Holger Mauch); detalles no publicados. Dificultades hasta "Inhuman". Jugadores la describen como "correcta pero predecible".

### 1.8 Victoria
- Modo "Supremacy": dominar el mapa (solo o por alianza). Opcional: límite de turnos → gana el mejor clasificado. Muerte del rey puede eliminar a un jugador.

### 1.9 Escenarios
- 260+ mapas (Europa, Colonización, Guerras Americanas, Imperios Asiáticos, Conquista Mundial) con cientos de facciones históricas (Roma, Cartago, Incas, Japón, Francia…). Los mapas se definen como imagen de provincias + archivos de configuración de texto (bundles `.config`).

### 1.10 Lagunas de documentación pública (decisiones propias)
1. Fórmula exacta de combate → diseñaremos una (Lanchester discreto, §5.3).
2. Fórmula de AP por turno → propondremos `AP = base + k·provincias`.
3. Frecuencia de la tax season y tasa de crecimiento poblacional → parámetros calibrables del modelo.
4. Algoritmo exacto de la IA → implementaremos heurística + opción de calibrado genético (§5.6).

---

## 2. Plantillas y referencias encontradas en GitHub

| Proyecto | Stack / Licencia | Qué aporta | Veredicto |
|---|---|---|---|
| [triplea-game/triplea](https://github.com/triplea-game/triplea) | Java / GPL-3 | Lo más cercano funcionalmente: territorios, combate por turnos, economía, IA fuerte, multijugador, escenarios XML | Referencia principal de reglas e IA en Java; forkarlo hereda un motor enorme y UI Swing antigua |
| [medovina/Warlight](https://github.com/medovina/Warlight) | Java / Apache-2.0 | Núcleo de reglas Risk compacto y limpio (42 regiones, bonos, combate) + framework de agentes IA | **Mejor referencia de código** para nuestro motor Java; licencia permisiva |
| [boardgame.io](https://github.com/boardgameio/boardgame.io) | TypeScript / MIT | Framework de juegos por turnos: estado, fases, multijugador, lobby, bots MCTS | Mejor base si el proyecto fuera web |
| [argosopentech/Conquest](https://github.com/argosopentech/Conquest) | Godot 3 / MIT-CC0 | Clon de Risk jugable con multijugador | Base si se eligiera Godot (requiere migrar a Godot 4) |
| [yairm210/Unciv](https://github.com/yairm210/Unciv) | Kotlin / MPL-2 | La mejor referencia open-source de IA, diplomacia y datos moddables (JSON) | Solo referencia — es un 4X, recortarlo cuesta más que empezar de cero |
| [openfrontio/OpenFrontIO](https://github.com/openfrontio/OpenFrontIO) | TypeScript / AGPL-3 | Conquista territorial web de calidad producción | Descartado: tiempo real y AGPL |
| [SVG-World-Map](https://github.com/raphaellepuschitz/SVG-World-Map) | JS / MIT | Mapamundi SVG con 3.000+ provincias clicables | Referencia de render de mapa (web) |
| [Thomas-Holtvedt/opengs](https://github.com/Thomas-Holtvedt/opengs), [dementive/gsg](https://github.com/dementive/gsg) | Godot | Técnica estándar de mapa de provincias: bitmap de colores únicos → provincia por píxel + grafo de adyacencia | Referencia de la técnica de mapa |

**Conclusión de la búsqueda:** ningún repo combina provincias + WEGO + economía + diplomacia + IA con licencia cómoda y código moderno. Las mecánicas diferenciales de AoC (felicidad, población, AP, diplomacia con IA) hay que escribirlas igual. → Construir motor propio usando estos repos como referencia.

---

## 3. Análisis de posibilidades (rutas)

### Ruta A — Motor propio en Java (recomendada)
Partir del esqueleto existente (`src/App.java`). Núcleo del juego como librería pura (sin UI) + interfaz Swing/JavaFX encima.
- ✅ Coincide con el proyecto ya creado y con Java como lenguaje del curso.
- ✅ Separación modelo/vista permite **ejecutar la simulación por lotes** (IA vs IA, miles de partidas) — exactamente lo que pide un estudio de simulación.
- ✅ Control total de fórmulas; Warlight (Apache-2.0) como guía de código.
- ❌ Hay que escribir todo: 6–10 semanas de trabajo por fases.

### Ruta B — Extender TripleA (Java, GPL-3)
Crear un escenario tipo AoC sobre el motor TripleA (mapas XML sin tocar el motor).
- ✅ Lo más rápido hacia algo jugable con IA y multijugador.
- ❌ No es "replicar el juego": no se aprende ni se controla el modelo; motor gigante y antiguo; GPL; las mecánicas de felicidad/población/AP **no existen** en TripleA y añadirlas exige tocar su núcleo.

### Ruta C — Web con boardgame.io + mapa SVG (TypeScript, MIT)
- ✅ Turnos, multijugador, lobby y bots resueltos por el framework; UI moderna en navegador.
- ❌ Cambio de stack completo respecto al proyecto Java ya creado; el valor académico (modelar el sistema) queda parcialmente delegado al framework.

### Ruta D — Godot 4 partiendo de argosopentech/Conquest
- ✅ Mejor camino a un juego "de verdad" multiplataforma con UI pulida.
- ❌ Aprender Godot/GDScript + migración Godot 3→4; economía, diplomacia e IA faltan igualmente.

### Decisión
**Ruta A.** Es la única que a la vez: respeta el proyecto Java existente, maximiza el aprendizaje de modelado (objetivo de la materia), permite experimentos de simulación por lotes y evita problemas de licencia. Si más adelante se quiere una UI vistosa, el núcleo Java puede reutilizarse (p. ej. exportando estado a JSON hacia una vista web).

---

## 4. Encuadre como estudio de simulación (metodología del curso)

Siguiendo las etapas del material del curso (formular → recoger datos → construir y verificar → validar → experimentar):

1. **Formulación del problema:** modelar la dinámica de expansión imperial de AoC IV como sistema dinámico, discreto (reloj por turnos), estocástico (revueltas, crecimiento) y de reglas.
2. **Variables del modelo:** por provincia: población, felicidad, fortificación, tropas, dueño; por nación: oro, AP, relaciones diplomáticas; global: turno, temporada fiscal.
3. **Relaciones:** ecuaciones en diferencias por turno (ingresos, crecimiento, moral) + reglas de combate/movimiento + proceso aleatorio de revueltas (Monte Carlo).
4. **Verificación:** pruebas unitarias de cada regla (JUnit); partidas deterministas con semilla fija.
5. **Validación:** comparar trayectorias (curva de expansión, quiebras, revueltas) contra el comportamiento documentado del juego real (fuentes de §1).
6. **Experimentos:** con el modo por lotes (IA vs IA): sensibilidad de la tasa impositiva, valor de la fortificación, efecto del +30 % del rey, distribución de duración de partidas, etc. → gráficas y análisis estadístico para el informe.

---

## 5. Diseño técnico propuesto (Ruta A)

### 5.1 Arquitectura de paquetes

```
src/
  model/        // Estado puro: Province, Nation, Army, DiplomaticState, GameState
  map/          // Grafo de adyacencia, carga de escenarios (JSON)
  engine/       // Motor WEGO: Order, TurnResolver, CombatResolver, EconomyPhase, RevoltPhase
  ai/           // Interfaz Agent + heurísticas (y opcional: calibrado genético)
  sim/          // Runner por lotes: N partidas, semillas, exportación CSV de métricas
  ui/           // Swing/JavaFX: mapa clicable, panel de nación, cola de órdenes
  App.java      // Punto de entrada (menú: jugar / hotseat / simulación por lotes)
```

Principio clave: **`model` + `engine` no importan nada de `ui`** — el mismo motor sirve para jugar y para simular por lotes.

### 5.2 Modelo de datos (esencial)

- `Province {id, nombre, población, felicidad, fortificada, dueño, tropas, esAgua, adyacentes[]}`
- `Nation {id, nombre, oro, ap, rey(Province), relaciones: Map<Nation, {GUERRA|NEUTRAL|ALIANZA}>, esIA}`
- `Order` (sellada): `Move, Recruit, Fortify, Pillage, Decree, Diplomacy…` — cada una con coste en AP/oro y prioridad.
- Escenario en **JSON**: lista de provincias con adyacencias + naciones con posiciones iniciales y relaciones (siguiendo el patrón bitmap/config del juego real, pero en texto para simplicidad).

### 5.3 Fórmulas propuestas (para las lagunas documentales)

- **AP por turno:** `AP = 3 + 0.5 · provincias` (calibrable).
- **Combate determinista** (estilo Lanchester discreto, respetando los modificadores conocidos):
  - Fuerza atacante `FA = tropas_A · (1 + 0.3·reyPresente)`
  - Fuerza defensora `FD = tropas_D · (1 + 0.3·reyPresente + 0.5·fortificada)`
  - Gana el de mayor fuerza; supervivientes del ganador `= tropas_ganador · (1 − F_perdedor/F_ganador · φ)` con `φ ≈ 0.7` calibrable. Empate exacto → defensor retiene.
- **Ingresos:** `oro += Σ provincias: 250 · (población/1M) · (tasa/100) · [felicidad ≥ 50]`
- **Mantenimiento:** `oro −= ceil(tropas_totales/20) + admin(provincias)`
- **Felicidad:** `Δ = −impacto(tasa) − enGuerra·2 + decretos + 1(recuperación base)`, acotada [0,100].
- **Revuelta (Monte Carlo):** si `felicidad < 50`: `P = min(0.9, k·(50−felicidad)²)`, con reducción por guarnición; sorteo por provincia y turno con `Random(semilla)`.
- **Población:** `pob *= (1+g)` con `g ≈ 1–2 %/turno`; reclutar resta `soldados·c` habitantes; festival: `pob *= 1.2`.

Todos los parámetros en una clase `Rules` (constantes con valores por defecto) → los **experimentos de simulación** consisten en variarlos.

### 5.4 Motor de turno (orden de resolución WEGO)

1. Recoger órdenes de todos los jugadores (humanos por UI, IA por `Agent.decide(state)`).
2. Validar y cobrar AP/oro.
3. Resolver por fases: diplomacia → decretos/fortificación → reclutamiento → movimientos (prioridad: reyes primero, luego por orden de emisión; empates → nación más grande) → combates → saqueos.
4. Fase económica: ingresos, mantenimiento, crecimiento, felicidad.
5. Fase estocástica: revueltas.
6. Comprobar eliminaciones (muerte de rey) y condiciones de victoria.

### 5.5 UI (fase tardía, mínima primero)

- **Iteración 1:** consola/hotseat por texto (suficiente para verificar el motor).
- **Iteración 2:** Swing — mapa como polígonos coloreados por dueño (JSON con coordenadas), clic para seleccionar/ordenar, panel lateral de nación, botón "Fin de turno".
- Técnica de mapa si se quiere estética AoC: imagen PNG donde cada provincia tiene un color único → lookup de provincia por píxel (misma técnica que opengs/gsg y que el propio AoC).

### 5.6 IA

- **Nivel 1 (obligatorio):** heurística codiciosa — reforzar fronteras, atacar solo con ventaja ≥ 1.5×, expandirse a neutrales débiles, mantener felicidad > 50, pedir paz si pierde.
- **Nivel 2 (opcional, gran valor para la materia):** los pesos de la heurística como cromosoma → **algoritmo genético** que los evoluciona jugando por lotes (réplica conceptual de la IA real de AoC, y un experimento de simulación excelente).

---

## 6. Fases de implementación

| Fase | Entregable | Estimación |
|---|---|---|
| **M1 — Modelo y mapa** | Clases de dominio + carga de escenario JSON + mapa de prueba (Europa simplificada, ~20 provincias, 4 naciones) + JUnit | 1 semana |
| **M2 — Motor WEGO** | Órdenes, validación de AP, movimiento, combate determinista, victoria; partida completa por consola (hotseat) | 1–2 semanas |
| **M3 — Economía y población** | Impuestos, mantenimiento, crecimiento, felicidad, revueltas Monte Carlo, saqueo, decretos | 1 semana |
| **M4 — IA heurística** | `Agent` + IA jugable; partidas IA vs IA completas | 1 semana |
| **M5 — Simulación por lotes** | Runner con semillas, exportación CSV, gráficas (duración de partidas, sensibilidad de parámetros) → material del informe | 1 semana |
| **M6 — UI gráfica** | Mapa Swing clicable + panel de órdenes; hotseat gráfico | 1–2 semanas |
| **M7 — Diplomacia + rey** | Estados diplomáticos, tributo/protectorado, mecánica del rey, reacción diplomática de la IA | 1 semana |
| **M8 — Extras (opcional)** | IA genética, más escenarios, editor simple de mapas, guardado de partidas | según tiempo |

El proyecto es demostrable desde M2 (consola) y presentable desde M5–M6.

---

## 7. Riesgos y mitigaciones

- **Alcance excesivo** (AoC tiene 10 años de desarrollo): mitigado por fases — M1–M4 ya constituyen un clon funcional mínimo; diplomacia y UI avanzada son incrementos.
- **Fórmulas desconocidas:** asumidas como parámetros calibrables y documentadas como decisiones de modelado (§5.3) — académicamente esto es una *virtud* (análisis de sensibilidad).
- **UI en Java:** empezar por consola garantiza que el motor (lo evaluable) nunca dependa del avance de la interfaz.
- **Multijugador en red:** fuera de alcance; hotseat + IA cubren el caso de uso.

---

## 8. Referencias

**Juego:** [ageofconquest.com](https://www.ageofconquest.com/) · [Steam](https://store.steampowered.com/app/314970/Age_of_Conquest_IV/) · [ficha de prensa Noble Master](https://www.noblemaster.com/press/sheet.php?p=age_of_conquest_IV) · [modding](https://www.ageofconquest.com/modding.html) · [historial de versiones](https://www.ageofconquest.com/history.html) · guías/hilos de Steam: [Tips for game](https://steamcommunity.com/app/314970/discussions/0/365163686062495477/), [How does it Work?](https://steamcommunity.com/app/314970/discussions/0/365163686058402807/), [Combat](https://steamcommunity.com/app/314970/discussions/0/357284767241691998/), [guía de _Fyr](https://steamcommunity.com/sharedfiles/filedetails/?id=2037890632) · reseñas: [Turn Based Lovers](https://turnbasedlovers.com/review/age-of-conquest-iv-review/), [Daikon Media](https://daikonmedia.com/age-of-conquest-iv-review/) · wiki fans: ageofconquest.fandom.com

**Código de referencia:** [Warlight](https://github.com/medovina/Warlight) (Apache-2.0, Java) · [TripleA](https://github.com/triplea-game/triplea) (GPL-3, Java) · [boardgame.io](https://github.com/boardgameio/boardgame.io) (MIT, TS) · [Conquest](https://github.com/argosopentech/Conquest) (MIT, Godot) · [Unciv](https://github.com/yairm210/Unciv) (MPL-2, Kotlin) · [opengs](https://github.com/Thomas-Holtvedt/opengs) / [gsg](https://github.com/dementive/gsg) (técnica de mapas de provincias) · [SVG-World-Map](https://github.com/raphaellepuschitz/SVG-World-Map)
