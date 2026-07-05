# Age of Conquest — Clon

Clon del juego de estrategia por turnos *Age of Conquest IV* (Noble Master Games),
desarrollado como proyecto de **Simulación de Sistemas (UNET)**.
El diseño completo, el análisis del juego original y la hoja de ruta están en [PLAN.md](PLAN.md).

## Estado actual — fase M1 completada

- **Modelo de dominio** (`src/model/`): `Province` (provincias de tierra y zonas marítimas,
  población, felicidad, guarnición), `Nation` (oro, puntos de acción, rey, relaciones),
  `DiplomaticState` (GUERRA/NEUTRAL/ALIANZA, siempre simétrico), `GameState` (estado
  completo de la partida) y `Rules` (parámetros calibrables de la simulación).
- **Cargador de escenarios** (`src/map/ScenarioLoader.java`): lee escenarios JSON y valida
  ids únicos, adyacencias (las simetriza), conectividad del mapa, zonas marítimas sin
  dueño/población, reyes en provincia propia y relaciones diplomáticas sin conflictos.
- **Escenario de prueba** (`scenarios/europa_antigua.json`): Europa antigua con
  23 provincias (18 de tierra + 5 marítimas), 4 naciones (Imperio Romano, Cartago,
  Galia, Grecia) y 4 provincias neutrales; Roma y Cartago empiezan en guerra.
- **36 pruebas JUnit** (`test/`) del modelo y del cargador.

Próximas fases (ver PLAN.md §6): M2 motor de turnos WEGO y combate, M3 economía y
población, M4 IA, M5 simulación por lotes, M6 interfaz gráfica.

## Estructura

```
src/
  model/       Estado puro de la simulación (sin dependencias de UI ni motor)
  map/         Carga y validación de escenarios JSON
  App.java     Punto de entrada: carga un escenario y muestra el estado inicial
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

# Ejecutar (escenario por defecto: scenarios/europa_antigua.json)
java -cp "lib/gson-2.11.0.jar:bin" App

# Ejecutar con otro escenario
java -cp "lib/gson-2.11.0.jar:bin" App scenarios/mi_escenario.json

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
