# Age of Conquest — Simulación de Sistemas (UNET)

Este repositorio contiene **dos artefactos distintos**, y conviene no confundirlos:

| | `sim/` (Python) | `src/` (Java) |
|:--|:--|:--|
| Qué es | **Simulador operacional del Parcial III** | Clon jugable de *Age of Conquest IV* |
| Modelo | El modelo formal de `docs/parcial2/PARCIAL2.md` | El mismo modelo formal, adaptado a un motor WEGO |
| Tiempo | Eventos discretos con LEF, τ = t + φ | Incremento fijo por turnos (WEGO) |
| Combate | Estocástico, triangular, ley lineal de Lanchester | Estocástico, triangular (mismo LCG y misma ec. 3.14–3.21) |
| Moral (3.27–3.29) | Implementada | **No**: sin entidad Ejército, μ ≡ 1.0 |
| Interfaz | Consola: traza, modo interactivo, lotes | Swing con mapa clicable, y consola |
| Estado | **Es lo que se entrega y se defiende** | Segunda implementación del modelo; sirve de contraste |

Que existan dos implementaciones independientes del mismo modelo formal, con paradigmas
de tiempo distintos, permite contrastarlas entre sí (*model-to-model validation*).

El informe del Parcial III es [`docs/parcial3/PARCIAL3.pdf`](docs/parcial3/PARCIAL3.pdf);
el guion de la defensa, [`docs/parcial3/DEFENSA.md`](docs/parcial3/DEFENSA.md).

## El simulador operacional (`sim/`)

Implementa íntegramente el modelo por eventos discretos del Parcial II: reloj τ = t + φ
con lista de eventos futuros, los diez eventos E1–E10, subsistemas económico,
demográfico, militar y diplomático, cuatro estrategias de IA, y las condiciones de
frontera del capítulo 5. Requiere **Python 3.11+** y ninguna dependencia externa.

```bash
python3 -m sim --traza-dorada   # verifica la traza de escritorio de §4.7 (44/44)
python3 -m sim --turnos 5       # cinco fases consecutivas con traza de eventos
python3 -m sim --interactivo    # modo interactivo: entrada de variables e inspección
python3 -m sim --partida        # partida completa hasta victoria o t_max
python3 -m sim --caso todos     # los cinco casos borde del capítulo 5
python3 -m sim --lote 40        # 40 réplicas con intervalos de confianza
python3 -m sim --barrido        # sensibilidad de K_B con semillas apareadas
```

Todo es exactamente reproducible: fijados la semilla s₀ y el orden total de la LEF, la
trayectoria de la simulación es única.

### Estructura

```
sim/
  parametros.py   matriz de terreno y los ~35 parámetros del capítulo 2
  azar.py         LCG de 48 bits y transformada inversa triangular
  reloj.py        micro-fases, catálogo de eventos, LEF, función de llegada
  estado.py       vector de estado S(τ) y variables auxiliares
  economia.py     renta, tesoro, insolvencia, descontento, población
  militar.py      movimiento, reclutamiento, combate, moral
  diplomacia.py   guarda diplomática, guerra y alianzas
  agentes.py      las cuatro estrategias y el árbol de decisión
  eventos.py      E1–E10, predicados de validez y despachador
  recolector.py   métricas O1–O5
  escenario.py    generación y validación del mapa del Anexo C
  lote.py         réplicas, intervalos de confianza, barridos
  casos_borde.py  demostraciones de las condiciones de frontera
  cli.py          interfaz operacional
escenarios/referencia24.json   mapa congelado: 24 provincias, grado medio 3.50
tests/test_traza_dorada.py     prueba dorada contra §4.7
resultados/p3/                 salidas CSV de los experimentos
```

### Resultados principales

- **Verificación:** 44/44 comprobaciones de la traza de escritorio de §4.7, error
  relativo máximo 5.1×10⁻⁴. Teoremas 1 y 2 sin violaciones en partidas completas.
- **Validación predictiva:** el modelo predijo en §3.2.4 que un imperio en guerra
  permanente no puede alcanzar la victoria (n^max = 13 < 15). En 40 réplicas,
  **0 ganadores estuvieron en guerra continua**.
- **Sensibilidad:** K_B afecta a la intensidad bélica de forma monótona (535 → 789
  bajas) pero no a la duración de la partida.

---

## El clon jugable en Java (`src/`)

Clon del juego de estrategia por turnos *Age of Conquest IV* (Noble Master Games),
desarrollado como proyecto de **Simulación de Sistemas (UNET)**. Nació por ingeniería
inversa del juego (hoja de ruta en [PLAN.md](PLAN.md)) y en agosto de 2026 se reescribió
para implementar las ecuaciones del modelo formal del Parcial II sobre su motor WEGO.

## Estado actual — fases M1 a M6, reescritas sobre el modelo formal

- **M1 — Modelo de dominio** (`src/model/`): `Province` (provincias de tierra y zonas
  marítimas, población, felicidad, guarnición), `Nation` (oro, puntos de acción, rey,
  relaciones), `DiplomaticState` (GUERRA/NEUTRAL/ALIANZA, siempre simétrico),
  `GameState` y `Rules` (parámetros calibrables de la simulación).
- **M1 — Cargador de escenarios** (`src/map/ScenarioLoader.java`): lee escenarios JSON
  y valida ids únicos, adyacencias (las simetriza), conectividad del mapa, zonas
  marítimas sin dueño/población, reyes en provincia propia y relaciones sin conflictos.
- **M2 — Motor de turnos WEGO** (`src/engine/`): órdenes (`mover`, `reclutar`,
  `fortificar`, `guerra`) con cobro de oro y población al emitirlas; resolución
  simultánea al cerrar el turno (fortificación → reclutamiento → movimientos con los
  reyes primero y luego por orden de emisión → combates); **combate estocástico**
  según las ecuaciones (3.14)–(3.21) —potencias con terreno, fortificación Φ(φ),
  respaldo civil Ψ(D_p) y factor triangular U— con el empate para el defensor;
  guarda diplomática en el movimiento; viaje naval cruzando una zona marítima;
  muerte del rey (pérdida del 90% del territorio); eliminaciones y victoria por
  cuota territorial Θ_V = 0.60, por dominación o por límite de turnos.
- **M2 — Partida jugable por consola** (`src/ui/ConsoleGame.java`): hotseat con todas
  las naciones (la IA llega en M4).
- **M3 — Economía y población** (ec. 3.1–3.10, 5.2): renta provincial
  I_p = ι·L_p·(θ/100)·(1+β_φ·φ_p) cobrada solo si D_p < D\*; coste
  C_i = c_adm·n_i + c_up·M_i; **insolvencia** con deserción forzosa cuando el tesoro
  caería por debajo de cero; **descontento D_p** (sustituye a la felicidad) con
  presión fiscal, guerra, sobreextensión y recuperación; crecimiento poblacional
  geométrico con saturación en L_max y daño de guerra. La tasa impositiva es
  continua en [0, 150] y puede fijarse en cualquier turno. Se conservan el saqueo
  y los decretos como órdenes del jugador.
- **M4 — IA por estrategias** (`src/ai/GreedyAgent.java`): las naciones `ia: true`
  juegan con una de las cuatro estrategias de la tabla §2.4.9 —AGRESIVA, DEFENSIVA,
  ECONÓMICA, EQUILIBRADA—, cada una un vector (θ_σ, f_rec, γ_atq, γ_σ, f_gua,
  f_fort). ECONÓMICA y EQUILIBRADA fijan su tasa con la θ_eq de la ec. (3.8);
  atacan si la ventaja supera su γ_atq; mueven tropas del interior a la frontera
  (BFS), fortifican al rey y reclutan con el excedente. La diplomacia es automática:
  coalición anti-líder cuando q_ℓ ≥ θ_am y agresión oportunista (ec. 3.30–3.32).
  El único azar de la partida es el factor triangular del combate.
- **Escenario de prueba** (`scenarios/europa_antigua.json`): Europa antigua con
  23 provincias (18 de tierra + 5 marítimas), 4 naciones (Imperio Romano, Cartago,
  Galia, Grecia) y 4 provincias neutrales; Roma y Cartago empiezan en guerra.
  Por defecto el humano lleva al Imperio Romano contra tres IA.
- **M5 — Simulación por lotes** (`src/sim/`): `BatchRunner` juega N partidas
  completas IA contra IA, cada una con su semilla y su configuración de `Rules`
  (mismas semillas entre variantes → comparaciones apareadas); `sim.Simulacion`
  trae 5 experimentos predefinidos, imprime el resumen estadístico y exporta
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
- **86 pruebas JUnit** (`test/`) del modelo, el cargador, el combate, el motor,
  la economía, la IA y el runner por lotes.

> **Limitación declarada.** El subsistema de moral (ec. 3.27–3.29) no está
> implementado en el motor Java: no existe la entidad Ejército con identidad propia
> que μ_a requiere —las tropas son un escalar por provincia—, así que el combate usa
> μ_a ≡ 1.0. La moral completa está en `sim/militar.py`.

Próximas fases (ver PLAN.md §6): M7 diplomacia avanzada (paz, alianzas, tributo).

## Experimentos de simulación

```bash
java -cp "lib/gson-2.11.0.jar:bin" sim.Simulacion --experimento todos --n 100
```

| Experimento | Parámetro variado | Ecuación | Pregunta |
|---|---|---|---|
| `base` | (ninguno) | — | Distribución de duración y ganador con la config. de referencia |
| `desgaste` | `kBeta` K_B 0.3–1.0 | (3.19) | Letalidad del combate: ¿guerras baratas o victorias pírricas? |
| `fortificacion` | `betaF` β_F 0–0.40 | (3.16) | ¿Cuánto estabiliza o estanca la partida el bono defensivo? |
| `sensibilidad_fiscal` | `etaTheta` η_θ 0.02–0.15 | (3.6) | ¿Cuánto castiga el descontento a la presión fiscal? |
| `crecimiento` | `gL` g_L 0.005–0.05 | (3.10) | ¿Cómo cambia la partida al mover la base imponible? |

Hallazgos con 100 partidas por variante (escenario Europa Antigua, ~2 s en total):

- **η_θ es el parámetro dominante.** Con η_θ ≥ 0.10 *todas* las partidas agotan los
  200 turnos y las insolvencias se disparan (394 y 580 por partida): el descontento
  cruza D\* en todas las provincias, la renta se anula y nadie puede sostener un
  ejército. Es la vía de derrota puramente económica que anticipa §5.2(b).
- **β_F es fuertemente no lineal y no monótono**: β_F = 0.10 alarga la partida media a
  167.6 turnos, pero β_F ≥ 0.25 la acorta a ~40–45. Un bono defensivo intermedio
  atasca el mapa; uno alto concentra el poder antes de que el descontento muerda.
- **K_B mueve al ganador más que a la duración**: de Galia 98 % con K_B = 0.3 a un
  reparto 61/39 con K_B = 1.0.

Estas cifras se regeneran con el comando de arriba y viven en `resultados/*.csv`.

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
    {"id": "roma", "nombre": "Roma", "poblacion": 10000, "adyacentes": ["etruria"]},
    {"id": "mar_tirreno", "agua": true, "adyacentes": ["roma"]},   // zona marítima
    {"id": "iberia", "poblacion": 3750, "tropas": 30, "adyacentes": ["..."]}  // neutral con guarnición
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
se reportan con `ScenarioException` y un mensaje descriptivo. La población de cada
provincia debe estar dentro de **L_max = 20 000 habitantes**, el techo del modelo
formal (§2.4.8): un escenario fuera de escala se rechaza en la carga en lugar de
recortarse en silencio, que igualaría todas las provincias y borraría la
heterogeneidad económica del mapa.

