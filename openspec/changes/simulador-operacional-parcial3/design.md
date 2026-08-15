## Context

El repositorio contiene dos modelos del mismo sistema, construidos por caminos independientes:

- **`PLAN.md` + `src/`** — el clon de *Age of Conquest IV* (fases M1–M6, 4.470 LOC, 98 pruebas).
  Motor WEGO de incremento fijo, combate determinista, felicidad, revueltas Monte Carlo, rey,
  puntos de acción, agua y viaje naval, saqueo, decretos, GUI Swing.
- **`docs/parcial2/PARCIAL2.md`** — el modelo formal ya entregado y calificado. Simulación por
  eventos discretos con LEF, descontento, terreno, ejércitos móviles con moral, combate con factor
  aleatorio triangular, cuatro estrategias de IA, coalición anti-líder, y garantías demostradas.

El Parcial III exige implementar **el segundo**. La tabla comparativa del `proposal.md` enumera 18
puntos de divergencia; ninguno es cosmético. El documento del Parcial II es, sin embargo, una
especificación excepcionalmente cerrada: 13 funciones definidas, 0 parámetros sin valor, pseudocódigo
de los 10 eventos, 7 funciones por partes, 9 casos degenerados, 3 teoremas y una traza numérica
verificable a mano. Implementarlo es en gran medida transcripción disciplinada.

**Restricciones:** Java 21+ (probado con 25), sin dependencias nuevas más allá de `gson` y JUnit 5 ya
presentes en `lib/`; compilación por `javac` desde la raíz sin sistema de build; el clon existente y
sus 98 pruebas deben seguir funcionando; el informe se genera con la cadena de `docs/parcial2/.build/`
que ya produce PDF con KaTeX y Mermaid sin acceso a red.

**Interesados:** los dos integrantes del equipo (el enunciado evalúa el historial de contribuciones) y
el evaluador, que juzgará en vivo la correspondencia entre el documento entregado y el código.

## Goals / Non-Goals

**Goals:**

- Implementar el modelo del Parcial II con **fidelidad verificable**: cada ecuación numerada y cada
  evento con su método correspondiente, y una prueba dorada que reproduzca la traza de §4.7 exacta.
- Que la correspondencia documento ↔ código sea **navegable en vivo** durante la sustentación: nombres
  de clases, métodos y constantes que reflejen la notación del documento.
- Cubrir los tres entregables del enunciado: simulador ejecutable e interactivo, informe PDF con
  arquitectura/validación/casos borde, y material de demostración reproducible.
- Preservar el clon M1–M6 sin tocarlo, como contexto del proyecto y demo secundaria.

**Non-Goals:**

- Refactorizar, migrar o unificar el motor existente con el nuevo. Coexisten.
- Reproducir las mecánicas que el Parcial II deliberadamente no modela (rey, puntos de acción, agua y
  viaje naval, saqueo, decretos, revueltas, temporada fiscal discreta). Su ausencia se declara.
- Interfaz gráfica nueva. El enunciado admite explícitamente consola; la GUI del clon ya existe si se
  quiere mostrar el mapa.
- Multijugador, red, persistencia de partidas, editor de mapas.
- Modificar el documento del Parcial II. Está entregado; el código se adapta a él, no al revés.

## Decisions

### D1 — Motor nuevo en `src/des/`, clon intacto

**Decisión:** el simulador operacional vive en un árbol de paquetes propio bajo `src/des/`, con su
propio punto de entrada. No se importa nada de `model`, `engine`, `ai`, `ui` ni `sim`.

**Alternativas:** (a) refactorizar `TurnEngine` sobre una `PriorityQueue<Evento>` y sustituir
felicidad por descontento, añadiendo terreno, ejércitos, moral y azar de combate; (b) borrar el clon
y quedarse solo con el motor nuevo.

**Por qué:** (a) toca las 98 pruebas, el formato de escenario, el `BatchRunner` y la GUI; el resultado
final sería indistinguible del motor nuevo, pero con el riesgo de quedarse a medio camino y sin
ninguno de los dos motores funcionando. Además el clon es evidencia del recorrido del proyecto y
material de demostración. (b) destruye trabajo válido sin ganancia. El coste real de la separación es
duplicar `ScenarioLoader` y `BatchRunner`, unas 200 líneas, muy por debajo del coste de (a).

**Consecuencia:** hay dos formatos de escenario en el repositorio. Se documenta explícitamente cuál
consume cada motor.

### D2 — Estructura de paquetes espejo del documento

```
src/des/
  modelo/     Imperio, Provincia, Ejercito, Combate, Terreno, EstadoDiplomatico,
              Estado (vector S(τ)), Parametros (capítulo 2 con procedencia [D]/[M]/[C])
  reloj/      Reloj (τ = t + φ), Fase, TipoEvento, Evento, ListaEventosFuturos,
              FuncionLlegada (4.4)
  eventos/    E1InicioTurno … E10FinJuego, Despachador, PredicadosValidez
  economia/   Renta (3.1)–(3.3), Tesoro (3.4)–(3.5), Descontento (3.6)–(3.9),
              Poblacion (3.10), Insolvencia (5.2)
  militar/    Movimiento (3.13), Reclutamiento (3.11), PoderMilitar (3.12),
              Combate (3.14)–(3.21), Moral (3.27)–(3.29)
  diplomacia/ MatrizDiplomatica, GuardaDiplomatica, Coalicion (3.30)–(3.32)
  agentes/    Estrategia (enum con vector de parámetros), PoliticaImperio,
              SeleccionObjetivos, BfsFrontera
  azar/       GeneradorLcg (3.26), Triangular (3.23)–(3.25)
  escenario/  CargadorEscenario, ValidacionEscenario
  sim/        Recolector (métricas O1–O5), CorredorLotes, ExportadorCsv
  ui/         ConsolaOperacional, DemoCasosBorde
  Simulador.java   punto de entrada
```

**Por qué:** un paquete por capítulo del documento hace trivial el recorrido del código en la defensa
("la ecuación 3.6 está en `des/economia/Descontento.java`"). Los nombres en español mantienen la
coherencia con el documento y con el clon existente.

### D3 — Aritmética del reloj en enteros, no en `double`

**Decisión:** el instante $\tau$ se representa internamente como un entero de micro-fases
($\tau_{\text{int}} = \text{turno}\cdot 10^6 + \text{fase}\cdot 10^6$), y se expone como `double` solo
para mostrar y para comparar contra el documento.

**Alternativa:** usar `double` directamente, como escribe el documento.

**Por qué:** la clave de la LEF debe ser un **orden total estricto** (ec. 4.2) y la simulación debe ser
exactamente reproducible. Con `double`, sumas repetidas de $\varepsilon = 10^{-3}$ y de restos
$c \bmod \Delta$ acumulan error de redondeo binario, y dos eventos que deberían caer en el mismo
instante pueden separarse —o dos que no, colisionar— según el orden de las operaciones. Con enteros,
$\varepsilon = 1000$ micro-fases y $\Delta = 746000$, la comparación es exacta. El coste es una
conversión en los puntos de entrada y salida.

**Riesgo controlado:** el resto real $c \bmod \Delta$ sí se calcula en punto flotante a partir de
$w(T)/v_a$; se redondea a micro-fases una sola vez, en la función de llegada, y ese redondeo se
documenta como la única fuente de discretización del reloj.

### D4 — Fuerzas en punto flotante, no en enteros

**Decisión:** $F_a$, $g_p$, $\mathcal{D}_p$, $M_i$, $b_{\text{gan}}$ y $\beta$ son `double`. Solo
$u_i$ (unidades reclutadas) y $\Delta M_i$ (deserción) son enteros, por llevar suelo y techo explícitos
en (3.11) y (5.2).

**Alternativa:** enteros como en el clon (`int troops`).

**Por qué:** el documento declara el dominio de $F_a$ y $g_p$ como $\mathbb{R}_{\ge 0}$, y la traza de
§4.7 opera con valores como $53.89$, $48.11$ y $0.692$. Redondear a enteros haría imposible reproducir
la traza dorada y alteraría el reparto proporcional de bajas (3.12c). La población $L_p$ sí se
mantiene en enteros de habitantes, coherente con la traza ($3792$).

### D5 — Generador aleatorio propio, no `java.util.Random`

**Decisión:** implementar el LCG de (3.26) explícitamente, exponiendo el contador de números
consumidos y permitiendo inyectar una secuencia fija de uniformes para las pruebas.

**Alternativa:** usar `java.util.Random`, cuyos parámetros son los mismos ($a$, $c$, $m$).

**Por qué:** `java.util.Random` aplica un descarte inicial de bits (`scramble` de la semilla) y devuelve
`nextDouble()` combinando dos extracciones de 26 y 27 bits, de modo que $R_k$ **no** coincide con
$X_{k+1}/m$. El documento define $R_k = X_{k+1}/\mathsf{m}$ y afirma "cada combate consume exactamente
dos números"; solo un generador propio lo cumple literalmente y permite verificar la afirmación. La
inyección de uniformes fijos es además el mecanismo que hace posible la prueba dorada con
$R_1 = 0.7314$ y $R_2 = 0.2891$.

### D6 — Eventos como objetos con `procesar(Estado, Lef)`

**Decisión:** cada tipo de evento es una clase con un predicado `esValido(Estado)` y un método
`procesar(Estado, Lef, Recolector)` que puede programar eventos hijos. El despachador es un bucle
genérico que no conoce los tipos.

**Alternativa:** un `switch` sobre un enum de tipo dentro del motor.

**Por qué:** el documento presenta cada evento con su pseudocódigo y su predicado de validez por
separado; una clase por evento hace la correspondencia 1:1 y permite probar cada evento aislado. El
`switch` concentraría 300 líneas en un método y haría el recorrido del código en la defensa incómodo.

### D7 — Parámetros como objeto mutable inyectado, no constantes estáticas

**Decisión:** `Parametros` es una instancia con campos públicos y valores por defecto del capítulo 2,
que se pasa al motor. Cada campo lleva en su comentario la unidad y la procedencia `[D]`/`[M]`/`[C]`.

**Por qué:** el análisis de sensibilidad exige variar los `[C]` por réplica sin recompilar, y las
réplicas de un lote deben poder correr con configuraciones distintas. Es el mismo patrón que
`Rules.java` ya usa con éxito en el clon, y que `BatchRunner` explota con `Consumer<Rules> tweak`.

### D8 — Escenario en JSON con terreno, cargador propio

**Decisión:** nuevo formato `scenarios/referencia24.json` con `terreno` obligatorio por provincia y
`estrategia` por imperio, cargado por `des/escenario/CargadorEscenario` con validación de conexidad,
tipos de terreno, unicidad de ids, capital dentro del territorio propio y reparto declarado en el
Anexo C.

**Por qué:** el formato del clon no tiene terreno ni estrategia y sí tiene agua, polígonos y reyes,
que el modelo nuevo no usa. Mezclarlos obligaría a campos opcionales ambiguos en ambos motores.

**Nota:** el Anexo C afirma que el fichero de datos del mapa acompaña al documento; no está en el
repositorio. Se construye respetando las restricciones declaradas (24 provincias, conexo, grado medio
≈ 3,5, 10/6/4/4 por terreno, 4 imperios × 3 provincias, 12 neutrales) y se documenta que el mapa
concreto es una decisión de esta fase, no un dato heredado.

### D9 — Estrategia de validación en tres niveles

El enunciado pide demostrar que los resultados "coinciden con los del juego real bajo las mismas
condiciones iniciales". Solo 2 parámetros del modelo están marcados `[D]`; el resto son `[M]` o `[C]`.
Prometer coincidencia numérica global sería insostenible en la sustentación. Se adopta:

| Nivel | Pregunta | Instrumento | Criterio de éxito |
|---|---|---|---|
| **V1 Verificación** | ¿el código implementa el documento? | traza dorada §4.7, prueba por ecuación, aserciones de los Teoremas 1–3 | coincidencia exacta dentro de tolerancia declarada |
| **V2 Validación estructural** | ¿el modelo reproduce el comportamiento cualitativo del juego real? | tabla hecho documentado → ecuación → evidencia observada; comprobación de las tres fases predichas en §3.8 | cada hecho `[D]` tiene mecanismo y evidencia; las ausencias se declaran |
| **V3 Calibración** | ¿qué valores de los `[C]` acercan más el modelo al juego real? | observaciones capturadas del juego real bajo condiciones controladas + barrido con réplicas apareadas | error reportado y valor calibrado justificado; si no hay observaciones, se declara |

**Por qué en tres niveles:** separa lo que es demostrable con certeza (V1) de lo que es defendible
(V2) y de lo que depende de datos externos que pueden no obtenerse (V3). Si V3 falla por no poder
capturar observaciones del juego, V1 y V2 siguen siendo entregables completos.

### D10 — Consola operacional como REPL, no como menú lineal

**Decisión:** la interfaz es un intérprete de comandos (`avanzar turno`, `avanzar evento`, `ver lef`,
`ver imperio <id>`, `fijar tasa <imperio> <valor>`, `evaluar ataque <origen> <destino>`,
`caso-borde <nombre>`, `semilla <n>`).

**Alternativa:** menú numerado por turno, como `ConsoleGame` del clon.

**Por qué:** el enunciado pide "ingresar variables de entrada o simular" y evaluar el estado
resultante; un REPL permite exactamente eso y hace la demostración en vivo mucho más convincente que
un menú de opciones fijas. `avanzar evento` además expone la LEF, que es la aportación técnica central
del modelo, y `evaluar ataque` muestra el régimen determinista/estocástico sin consumir aleatorios.

## Risks / Trade-offs

- **Volumen de trabajo: motor completo desde cero.** → Se ordena por capas verificables: primero
  reloj + LEF + azar (probables aisladamente), después economía, después militar, después diplomacia
  y agentes. La prueba dorada de §4.7 se puede ejecutar en cuanto existan economía y combate, mucho
  antes de tener la partida completa, y es el mejor indicador temprano de fidelidad.

- **La traza dorada de §4.7 puede no cuadrar por ambigüedad del documento.** El documento no declara
  el redondeo de $L_p$ a enteros ni el orden exacto de algunos sub-pasos de E2. → Se fija una
  tolerancia relativa declarada (p. ej. $10^{-3}$) y se documenta cada convención de redondeo adoptada
  como decisión de implementación, en la tabla de trazabilidad. Es material defendible, no un fallo.

- **Dos escenarios y dos formatos en el repositorio pueden confundir al evaluador.** → El README y el
  informe declaran desde el principio los dos artefactos y cuál se defiende.

- **La validación V3 depende de poder ejecutar y observar el juego real.** → V1 y V2 se entregan
  completas con independencia; V3 se declara explícitamente con su alcance real. Nunca se presenta una
  coincidencia que no se haya medido.

- **Divergencia entre lo que se implementa y lo que dice el documento entregado.** Si al implementar
  aparece un defecto en el propio modelo del Parcial II, corregirlo en el código sin declararlo rompe
  la trazabilidad. → Toda desviación se registra en la tabla de trazabilidad con su motivo, y se
  menciona en el informe como hallazgo de la fase de implementación (que es, además, exactamente lo
  que el Parcial II hizo con el Parcial I).

- **Historial de contribuciones de un solo autor.** El enunciado lo evalúa. → El reparto por subsistema
  está en `tasks.md`; los commits de esta fase deben reflejar la autoría real de cada integrante.

- **Sin sistema de build.** Añadir subdirectorios a `src/` funciona con el `javac $(find src test …)`
  documentado en el README, pero la línea de compilación crece. → Se añade un guion de compilación y
  ejecución que encapsule las tres invocaciones (compilar, ejecutar, probar).

## Migration Plan

No hay migración de datos ni despliegue. La secuencia de integración es:

1. `src/des/` se añade sin tocar nada existente; el proyecto sigue compilando en cada paso.
2. `test/des/` crece en paralelo; las 98 pruebas previas siguen ejecutándose en la misma invocación de
   JUnit (`--scan-class-path`).
3. El punto de entrada `des/Simulador.java` es independiente de `App.java`; el clon arranca igual que
   antes.
4. **Rollback:** borrar `src/des/`, `test/des/`, `scenarios/referencia24.json` y `docs/parcial3/`
   devuelve el repositorio a su estado actual sin efectos residuales.

## Open Questions

1. **¿Se dispone de *Age of Conquest IV* ejecutable para capturar observaciones de validación V3?**
   Determina si la sección de calibración del informe compara contra datos reales o se limita a
   justificar los valores `[M]` documentados. Se puede empezar todo lo demás sin resolverla.
2. **¿"Cinco fases consecutivas" del enunciado significa cinco turnos o cinco fases dentro de un
   turno?** Se implementa la lectura estricta (cinco turnos completos, que contienen todas las fases),
   que satisface ambas interpretaciones.
3. **Reparto nominal del trabajo entre Arturo Carvajalino y Leonardo Lobo.** `tasks.md` propone un
   reparto por subsistema; hay que confirmarlo antes de empezar a commitear.
4. **Tolerancia numérica de la prueba dorada.** Propuesta: error relativo $10^{-3}$ frente a los
   valores impresos en §4.7, que están redondeados a 2–3 decimales en el propio documento.
