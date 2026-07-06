# Age of Conquest — Clon

Clon del juego de estrategia por turnos *Age of Conquest IV* (Noble Master Games),
desarrollado como proyecto de **Simulación de Sistemas (UNET)**.
El diseño completo, el análisis del juego original y la hoja de ruta están en [PLAN.md](PLAN.md).

## Estado actual — fases M1 a M6 completadas

- **M1 — Modelo de dominio** (`src/model/`): `Province` (provincias de tierra y zonas
  marítimas, población, felicidad, guarnición), `Nation` (oro, puntos de acción, rey,
  relaciones), `DiplomaticState` (GUERRA/NEUTRAL/ALIANZA, siempre simétrico),
  `GameState` y `Rules` (parámetros calibrables de la simulación).
- **M1 — Cargador de escenarios** (`src/map/ScenarioLoader.java`): lee escenarios JSON
  y valida ids únicos, adyacencias (las simetriza), conectividad del mapa, zonas
  marítimas sin dueño/población, reyes en provincia propia y relaciones sin conflictos.
- **M2 — Motor de turnos WEGO** (`src/engine/`): órdenes (`mover`, `reclutar`,
  `fortificar`, `guerra`) con cobro de AP/oro/población al emitirlas; resolución
  simultánea al cerrar el turno (fortificación → reclutamiento → movimientos con los
  reyes primero y luego por orden de emisión → combates); combate determinista tipo
  Lanchester (+30% rey, +50% fortificación, empate para el defensor); viaje naval
  cruzando una zona marítima; muerte del rey (pérdida del 90% del territorio);
  eliminaciones y victoria por dominación o por límite de turnos.
- **M2 — Partida jugable por consola** (`src/ui/ConsoleGame.java`): hotseat con todas
  las naciones (la IA llega en M4).
- **M3 — Economía y población**: fase económica al cierre de cada turno — impuestos
  sobre la población (las provincias con felicidad &lt; 50% no pagan), mantenimiento
  militar y administrativo (el oro puede ser negativo: deuda), crecimiento
  poblacional, evolución de la felicidad (recuperación base, presión fiscal, guerra);
  **revueltas Monte Carlo** con generador sembrado (`Rules.randomSeed`, partidas
  reproducibles); temporada fiscal cada 5 turnos con tasas 0/50/100/150/200%;
  saqueo (población propia → oro) y decretos (repartir dinero, fiesta, festival
  de fertilidad).
- **M4 — IA heurística** (`src/ai/GreedyAgent.java`): las naciones `ia: true` juegan
  solas — ajustan impuestos según la felicidad media, decretan fiestas donde hay
  descontento, atacan solo con ventaja ≥ 1.5× sobre la defensa efectiva, declaran
  guerras oportunistas, mueven las tropas del interior hacia la frontera (BFS),
  fortifican al rey y reclutan con el excedente. Umbrales públicos y calibrables;
  IA determinista (el único azar de la partida son las revueltas).
- **Escenario de prueba** (`scenarios/europa_antigua.json`): Europa antigua con
  23 provincias (18 de tierra + 5 marítimas), 4 naciones (Imperio Romano, Cartago,
  Galia, Grecia) y 4 provincias neutrales; Roma y Cartago empiezan en guerra.
  Por defecto el humano lleva al Imperio Romano contra tres IA.
- **M5 — Simulación por lotes** (`src/sim/`): `BatchRunner` juega N partidas
  completas IA contra IA, cada una con su semilla y su configuración de `Rules`
  (mismas semillas entre variantes → comparaciones apareadas); `sim.Simulacion`
  trae 4 experimentos predefinidos, imprime el resumen estadístico y exporta
  cada partida a `resultados/<experimento>.csv`.
- **M6 — Interfaz gráfica Swing** (`src/ui/MapPanel.java`, `src/ui/SwingGame.java`):
  mapa clicable con una provincia por polígono coloreada por dueño (nombre, tropas,
  ♔ rey y ⛨ fortificación); al seleccionar una provincia propia se resaltan sus
  destinos alcanzables y el clic en uno abre el diálogo de movimiento (con opción
  de llevar al rey); panel lateral de nación y órdenes (reclutar, fortificar,
  saquear, decretos, guerra, impuestos), crónica de la partida y botón de fin de
  turno; modo espectador automático si solo juegan IA. Los escenarios llevan
  coordenadas de polígono opcionales (`"poligono"`); sin ellas se genera una
  cuadrícula.
- **98 pruebas JUnit** (`test/`) del modelo, el cargador, el combate, el motor,
  la economía, la IA y el runner por lotes.

Próximas fases (ver PLAN.md §6): M7 diplomacia avanzada (paz, alianzas, tributo).

## Experimentos de simulación

```bash
java -cp "lib/gson-2.11.0.jar:bin" sim.Simulacion --experimento todos --n 100
```

| Experimento | Parámetro variado | Pregunta |
|---|---|---|
| `base` | (ninguno) | Distribución de duración y ganador con la config. de referencia |
| `fortificacion` | `fortDefenseBonus` 0–1 | ¿Cuánto estabiliza o estanca la partida el bono defensivo? |
| `desgaste` | `combatAttrition` φ 0.3–1 | Letalidad del combate: ¿guerras baratas o victorias pírricas? |
| `revueltas` | `revoltRiskK` 0–1.6e-3 | Peso del componente estocástico (Monte Carlo) |

Hallazgos con 100 partidas por variante (semillas 1000–1099, escenario Europa
Antigua, ~3 s en total): la fortificación tiene un efecto fuertemente no lineal
(con +100% la partida media pasa de 30 a 134 turnos y el ganador dominante
cambia de Galia a Cartago); el desgaste φ decide el ganador (φ=0.5 → Grecia
100%, φ≥0.7 → Galia ~99%); y sin revueltas (`revoltRiskK=0`) las 100 partidas
son idénticas — confirmación de que las revueltas son la única fuente de azar
del modelo.

## Estructura

```
src/
  model/       Estado puro de la simulación (sin dependencias de UI ni motor)
  map/         Carga y validación de escenarios JSON
  engine/      Motor WEGO: órdenes, validación, combate y resolución del turno
  ai/          Jugadores artificiales (interfaz Agent + GreedyAgent heurística)
  sim/         Simulación por lotes y experimentos de sensibilidad (CSV)
  ui/          Interfaz gráfica Swing (MapPanel, SwingGame) y partida por consola
  App.java     Punto de entrada: GUI por defecto, --consola para la terminal
test/          Pruebas JUnit 5 (espejo de los paquetes de src)
scenarios/     Escenarios en JSON
lib/           Dependencias: gson (JSON) y junit-platform-console-standalone (pruebas)
```

## Compilar y ejecutar

Requiere **Java 21+** (probado con Java 25). Desde la raíz del proyecto:

```bash
# Compilar (fuentes + pruebas)
javac -encoding UTF-8 -cp "lib/gson-2.11.0.jar:lib/junit-platform-console-standalone-1.10.2.jar" \
      -d bin $(find src test -name '*.java')

# Jugar con interfaz gráfica (escenario por defecto: scenarios/europa_antigua.json)
java -cp "lib/gson-2.11.0.jar:bin" App

# Jugar en la terminal / con otro escenario / solo el resumen inicial
java -cp "lib/gson-2.11.0.jar:bin" App --consola
java -cp "lib/gson-2.11.0.jar:bin" App scenarios/mi_escenario.json
java -cp "lib/gson-2.11.0.jar:bin" App --solo-resumen

# Ejecutar las pruebas
java -jar lib/junit-platform-console-standalone-1.10.2.jar execute \
     --class-path "bin:lib/gson-2.11.0.jar" --scan-class-path
```

En VS Code (extensión *Extension Pack for Java*) el proyecto compila solo; las pruebas
aparecen en el panel *Testing*.

## Formato de escenario

```jsonc
{
  "nombre": "Mi Escenario",
  "provincias": [
    // Las adyacencias se declaran en una sola dirección; el cargador las simetriza.
    {"id": "roma", "nombre": "Roma", "poblacion": 800000, "adyacentes": ["etruria"]},
    {"id": "mar_tirreno", "agua": true, "adyacentes": ["roma"]},   // zona marítima
    {"id": "iberia", "poblacion": 300000, "tropas": 30, "adyacentes": ["..."]}  // neutral con guarnición
  ],
  "naciones": [
    {"id": "imperio_romano", "nombre": "Imperio Romano", "oro": 100, "ia": false,
     "rey": "roma",                        // opcional: provincia inicial del rey
     "provincias": {"roma": 100},          // id de provincia -> tropas iniciales
     "relaciones": {"cartago": "GUERRA"}}  // GUERRA | NEUTRAL | ALIANZA
  ]
}
```

Toda provincia sin dueño es neutral. El mapa debe ser conexo; los errores de formato
se reportan con `ScenarioException` y un mensaje descriptivo.
