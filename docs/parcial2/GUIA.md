# Guía de construcción del documento — Parcial II

**Entrada:** `Simulación (3).pdf` — *Modelo conceptual de Age of Conquest* (Parcial I,
Arturo Carvajalino y Leonardo Lobo, junio 2026).
**Salida esperada:** documento técnico del Parcial II — modelo definitivo mediante
funciones matemáticas, ecuaciones de estado y diseño de la LEF.
**Estado:** guía previa. Aún no se ha escrito el documento.

---

## 0. Idea central de esta guía

El Parcial I no dejó un texto descriptivo: dejó **un algoritmo con agujeros**.
El pseudocódigo de los eventos E1–E10 ya está escrito, pero invoca trece
funciones que nunca define (`calcularIngreso()`, `Aleatorio()`, `Terreno(p, ATAQUE)`,
`SeleccionarObjetivos()`, `RegenerarMoral()`…) y nueve parámetros sin valor
(`K_BAJAS`, `UMBRAL_VICTORIA`, `ε`…).

> **El Parcial II es exactamente eso: rellenar esos agujeros con matemáticas,
> reparar las incoherencias que se ven al hacerlo, y demostrar que el conjunto
> queda cerrado.**

Ese encuadre importa porque decide la estructura del documento: no se reescribe
el modelo conceptual, se **completa y se cierra**. Y hace que la sección más
temida (la LEF) sea casi trabajo hecho.

---

## 1. Inventario: qué queda heredado del Parcial I

| Requisito del Parcial II | Qué aporta ya el Parcial I | Qué falta |
|---|---|---|
| **1. Diccionario de variables** | Tablas de atributos completas de Imperio, Provincia, Ejército, Combate + variables de estado del sistema | Clasificar en estado/flujo/auxiliar, asignar **unidades**, tabular parámetros con valor y procedencia |
| **2. Ecuaciones dinámicas** | Estructura de los cambios de estado por evento; esqueleto de la fórmula de bajas (`fuerza × (Pd/Pa) × K_BAJAS`) | **Todas las funciones** (F1–F13 §2) y la formalización de `Aleatorio()` |
| **3.1 Ciclo de turno** | Grafo de eventos (Fig. 2) + pseudocódigo de E1–E6, E9, E10 | Diagrama de flujo del ciclo completo, justificación del orden de fases |
| **3.2 Árbol de decisión IA** | Enum de 4 estrategias `{AGRESIVA, DEFENSIVA, ECONÓMICA, EQUILIBRADA}` y los puntos donde se consulta | **El árbol entero**: los criterios de cada estrategia no existen |
| **3.3 LEF y reloj** | ✅ Reloj `turno.fase`, cola de prioridad, estructura del registro, traza de ejemplo del turno 3, bucle principal | Formalizar la función de fase, la relación de orden y probar los invariantes (§3 D3) |
| **4. Fronteras y puntos críticos** | Nada explícito | **Toda la sección** |

**Balance:** la sección 3 está al 70 %, la sección 1 al 60 %, la sección 2 al 20 %
y la sección 4 al 0 %. El grueso del esfuerzo es §2 y §4.

**Activos adicionales del Parcial I que conviene explotar en el Parcial II:**

- Los **5 objetivos O1–O5 con métricas asociadas** dan la justificación de por qué
  cada variable existe: toda variable de estado debería poder rastrearse hasta una
  métrica. Es un argumento fuerte de coherencia sistémica.
- El **diagrama de bucles causales** (R1 bola de nieve, B1 sobreextensión, B2 atrición,
  B3 coalición anti-líder) es la hipótesis de comportamiento. Cada bucle debería
  quedar **cerrado por ecuaciones** en el Parcial II — ver defecto D5.
- La frase *"El registro 7 ilustra un evento programado para un turno futuro […]
  Esto justifica el uso de una LEF auténtica frente a un simple bucle de fases"*
  es la tesis metodológica del modelo. La sección 3.3 debe demostrarla formalmente.

---

## 2. Las funciones huecas — el trabajo real de la sección 2

Cada fila es una función que el pseudocódigo del Parcial I **llama sin definir**.
Definirlas es literalmente el entregable 2.

| ID | Función | Dónde se invoca | Qué hay que definir |
|---|---|---|---|
| **F1** | `calcularIngreso(p)` | E1 | $\text{ingreso}(p) = f(\text{poblacion}, \text{nivelFortificacion})$. ¿Lineal en población? ¿La fortificación suma o resta renta? ¿Hay techo? |
| **F2** | `DecidirReclutamiento(i, estrategia, oro)` | E2 | Cuánto oro convierte en unidades cada una de las **4 estrategias**. Una regla por estrategia |
| **F3** | `SeleccionarObjetivos(i, estrategia)` | E2 | El **árbol de decisión de la IA** completo: criterio de elección de provincia objetivo y de asignación de ejércitos, por estrategia |
| **F4** | `CosteMovimiento(origen, destino)` | E2 | Función del terreno y de `puntosMovimiento`. Es la que genera los movimientos multi-turno que justifican la LEF |
| **F5** | `moral(ejercito)` | E4 | Dinámica de la moral $\in [0,1]$: cómo baja (combate, distancia a la capital) — hoy solo se regenera |
| **F6** | `Terreno(p, rol)` | E4 | **Matriz 4×2**: {LLANURA, MONTAÑA, BOSQUE, COSTA} × {ATAQUE, DEFENSA} |
| **F7** | `Aleatorio()` | E4 | ⭐ **Distribución, soporte, media y método de generación.** El enunciado lo exige explícitamente ("Leer generación de variables aleatorias") |
| **F8** | `Fortificacion(p)` | E4 | Multiplicador defensivo como función de `nivelFortificacion ∈ [0..N]`. ¿Lineal $1+kn$? ¿Rendimientos decrecientes? |
| **F9** | `RegenerarMoral(i)` | E9 | Tasa de recuperación por turno y su techo |
| **F10** | `ActualizarPoderMilitar(i)` | E9 | Definición exacta de `poderMilitarTotal` (¿suma de fuerzas? ¿ponderada por moral?) |
| **F11** | `SeleccionarEstrategia()` | Inicializar | Asignación de estrategia a cada imperio: fija, aleatoria o por escenario |
| **F12** | `evaluarDiplomacia()` | Método declarado | **No tiene pseudocódigo.** Criterios de E7 (guerra) y E8 (alianza/ruptura) |
| **F13** | $\varphi(\text{fase})$ | Todo el motor | La codificación `turno.fase` está implícita en los literales `0.10`, `0.15`, `0.90`, `0.95`, `ε`. Hay que definirla como función y probar sus propiedades |

### Parámetros nombrados sin valor

| ID | Parámetro | Aparece en | Nota |
|---|---|---|---|
| P1 | `ORO_INICIAL` | Inicializar | |
| P2 | `FUERZA_INICIAL` | Inicializar | |
| P3 | `COSTO_UNIDAD` | E2 | Une economía y militar: es el tipo de cambio del modelo |
| P4 | `K_BAJAS` | E4 | Controla B2 (atrición) → gobierna la duración de la partida |
| P5 | `UMBRAL_VICTORIA` | E9 | Cuota de provincias para ganar |
| P6 | `ε` | E3, E4, E5, E6 | Paso infinitesimal del reloj. **Necesita cota superior demostrada** |
| P7 | offsets de fase | E1, E2, E9 | `0.10` planificación, `0.15` movimiento, `0.90` fin de turno, `0.95` fin de juego |
| P8 | Escenario | Inicializar | N provincias, nº de imperios, matriz de adyacencia, reparto de terrenos |
| P9 | `N` de fortificación | Provincia | Nivel máximo |

Cada uno necesita: **símbolo, valor, unidad, y procedencia** (`[D]` documentado del
juego real / `[M]` decisión de modelado / `[C]` calibrado por experimento).

---

## 3. Defectos del modelo conceptual que el Parcial II debe reparar

Estos salen de leer el pseudocódigo con lupa. Detectarlos y repararlos por escrito
**suma nota** en los cuatro criterios; ignorarlos la resta.

### D1 — La diplomacia no tiene ningún efecto mecánico ⚠️ grave
`estadoDiplomatico` se declara, se inicializa a `PAZ`, se actualiza en E5, E7 y E8…
y **nunca se consulta**. E3 decide entre mover y combatir mirando solo el propietario:

```
SI propietario(pDestino) = NEUTRAL O propietario(pDestino) = propietario(a)
```

Consecuencia: se puede atacar a un aliado sin romper la alianza, y las alianzas no
cambian nada. **Reparación:** añadir la guarda diplomática en E3 y definir qué
implica una alianza (paso de tropas, defensa conjunta, prohibición de ataque).

### D2 — E7 y E8 existen en la tabla de eventos pero no tienen pseudocódigo
Los dos eventos diplomáticos aparecen en la tabla de eventos y en el grafo, pero la
sección de pseudocódigo salta de E6 a E9. Sin F12 no hay forma de programarlos.

### D3 — La aritmética del reloj puede desordenar los eventos ⚠️
En E2: `tiempo = turno + 0.15 + CosteMovimiento(...)`. Si el coste supera **0.75**,
el movimiento se programa **después** del `FIN_TURNO` de ese mismo turno (fase 0.90),
es decir, un ejército llegaría después de que el turno haya cerrado y verificado la
victoria. La traza de ejemplo (registro 7, tiempo 4.25) funciona por casualidad,
porque ahí el coste vale 1.10 y cae en la ventana del turno siguiente.

**Reparación:** definir la llegada como función por partes que reparta el coste en
turnos completos más una fase válida, por ejemplo

$$
\tau_{\text{llegada}} = \big(\text{turno} + \lfloor c / \Delta \rfloor\big) + \varphi_{MOV} + (c \bmod \Delta), \qquad \Delta = \varphi_{FIN} - \varphi_{MOV}
$$

y **demostrar** que el resultado siempre cae en la ventana $[\varphi_{MOV}, \varphi_{FIN})$.
Es material de primera para la sección 4 (funciones por partes) y para la 3.3.

### D4 — Llegadas simultáneas no definidas
Si dos ejércitos de imperios distintos llegan a la misma provincia en el mismo
instante, E3 crea **dos combates independientes** sobre la misma guarnición. Y si dos
ejércitos aliados llegan a la vez, el orden de fusión queda indeterminado.
**Reparación:** regla de desempate en la LEF (por `idEjercito`, o por orden de
inserción) y decisión explícita sobre batallas a tres bandas.

### D5 — B1 y B3 del diagrama causal no tienen mecanismo ⚠️
El CLD promete cuatro bucles. En el pseudocódigo solo existen dos:

| Bucle | ¿Implementado? | Dónde |
|---|---|---|
| R1 bola de nieve | ✅ | E1 (ingreso) → E2 (recluta) → E4/E5 (conquista) |
| B2 atrición | ✅ | E4, vía `K_BAJAS` |
| **B1 sobreextensión** | ❌ | *"guarniciones dispersas → vulnerabilidad"* no está en ninguna parte |
| **B3 coalición anti-líder** | ❌ | requiere F12, que no existe |

**Reparación:** o se formalizan (B1 mediante un coste de mantenimiento o una
penalización de moral creciente con el número de provincias o la longitud de
frontera; B3 mediante un criterio de declaración de guerra en F12 que dependa de
`cuotaLider`), o se retiran explícitamente del modelo con justificación. Dejarlos
prometidos y sin ecuación es el fallo de coherencia sistémica más caro del documento.

### D6 — La moral se regenera pero nunca se pierde
E9 llama a `RegenerarMoral(i)`; ningún evento la reduce. Con solo regeneración, la
moral converge a su techo y el factor deja de discriminar. **Reparación:** definir
la pérdida (combate, derrota, distancia a la capital, sobreextensión → conecta con B1).

### D7 — No hay condiciones de frontera en ningún sitio
El Parcial I no dice qué pasa cuando `oro < 0`, cuando `fuerza → 0`, cuando un imperio
pierde su capital (`esCapital` existe pero E5 no lo trata), cuando `moral = 0`, ni
cuando dos imperios empatan en `cuotaLider`. Es la sección 4 completa.

### Oportunidad O1 — alinearse con el ejemplo del enunciado
El enunciado del Parcial II propone como ejemplo una ecuación de tesoro con **tasa
impositiva** $I$. El modelo conceptual no tiene tasa: el ingreso es pasivo. Introducir
$\tau_{i,t}$ como variable de decisión del imperio (elegida en E2 según la estrategia)
alinea el documento con el ejemplo literal del profesor, enriquece la estrategia
ECONÓMICA y da material para la sección 4 (umbral de descontento). Coste bajo,
retorno alto. **Decisión recomendada: incorporarla.**

---

## 4. Decisión de alcance — hay que responderla antes de escribir

Existe un clon en Java (`Age of Conquest - Clon`, fases M1–M6, 98 pruebas verdes)
que implementa un modelo **distinto** del Parcial I:

| Aspecto | Parcial I (PDF) | Clon Java |
|---|---|---|
| Paradigma temporal | Eventos discretos con LEF real | Turnos WEGO, incremento fijo |
| Unidad militar | Ejército móvil con ubicación, puntos de movimiento y moral | Tropas enteras por provincia |
| Combate | **Estocástico**: `fuerza × moral × Terreno × Aleatorio()` | Determinista tipo Lanchester |
| Terreno | 4 tipos con modificadores | No existe |
| Fortificación | Nivel entero `[0..N]`, decrece al conquistar (asedio) | Booleano |
| Moral | Del **ejército**, $[0,1]$, multiplicador de combate | Felicidad de la **provincia**, $[0,100]$, gobierna impuestos y revueltas |
| Economía | Ingreso pasivo $f(\text{poblacion}, \text{fortif})$ | Impuestos con tasa y umbral de felicidad |
| Rey | No existe | Central (bono +30 %, muerte → −90 % territorio) |
| Estrategias de IA | 4 enumeradas | 1 (`GreedyAgent`) |
| Victoria | Cuota de provincias **o** último en pie | Último en pie |
| Azar | En cada combate | Solo en las revueltas |

Tres caminos posibles:

**(A) Fidelidad al Parcial I.** Formalizar exactamente el modelo del PDF. Coherencia
perfecta con lo ya entregado y evaluado. El clon queda desalineado y el Parcial III
exigiría refactorizarlo a fondo.

**(B) Fidelidad al clon.** Documentar lo programado. Contradice el Parcial I y obliga
a justificar por qué se abandonó el enfoque de eventos discretos — que es
precisamente lo que el enunciado del Parcial II pide reforzar.

**(C) Modelo reconciliado (recomendado).** El **esqueleto** es el del Parcial I
—LEF, reloj `turno.fase`, eventos E1–E10, ejércitos móviles, combate estocástico con
terreno— porque es lo comprometido y lo que el enunciado premia. Sobre él se
incorporan, **como refinamientos declarados**, las mecánicas que el clon ya validó
empíricamente y que rellenan huecos reales: tasa impositiva (→ O1, F1), umbral de
descontento (→ B1, D5), y los valores de parámetros que ya tienen respaldo de 2100
partidas simuladas (→ P3, P4, P5).

Se declara en una subsección *"Revisiones respecto al modelo conceptual"* con una
tabla cambio → motivo. Esto no es un parche: el refinamiento iterativo del modelo es
metodología estándar de simulación y **demuestra dominio**, siempre que quede
explícito y justificado.

> ⚠️ **Nota sobre la reconciliación:** la moral del ejército (Parcial I) y la
> felicidad de la provincia (clon) son variables **distintas** de subsistemas
> distintos y pueden coexistir sin conflicto: una multiplica la eficacia en combate,
> la otra gobierna la recaudación. No hay que elegir entre ambas.

---

## 5. Método paso a paso

El orden no es negociable: cada paso consume la salida del anterior.

### Paso 1 — Congelar el diccionario *(sección 1)*
Transcribir las tablas de atributos del Parcial I, clasificar cada entrada en
**estado / flujo / auxiliar**, asignar unidad y dominio, y fijar el símbolo
matemático definitivo de cada una. A partir de aquí, **ningún símbolo nuevo** puede
aparecer sin volver a esta tabla.
*Salida:* tabla maestra de variables + tabla de parámetros P1–P9 con valores.

### Paso 2 — Formalizar el reloj y la LEF *(sección 3.3, adelantada)*
Va **antes** que las ecuaciones porque define qué significa el índice temporal en
ellas. Hay que producir:
- la función de fase $\varphi: \text{TipoEvento} \to [0,1)$ y probar que es inyectiva;
- la relación de orden total de la cola, con desempate (→ repara D4);
- la cota de $\varphi$ para $\varepsilon$: si una cadena movimiento → combate →
  conquista → eliminación consume $4\varepsilon$, entonces
  $4\varepsilon < \varphi_{FIN} - \varphi_{MOV}$;
- la función por partes de llegada de movimientos (→ repara D3);
- el invariante de causalidad: **ningún evento se programa a tiempo pasado**,
  $\tau_{\text{hijo}} \ge \tau_{\text{padre}}$.

### Paso 3 — Escribir la ecuación de estado genérica *(cabecera de la sección 2)*
Es el andamiaje que sostiene toda la sección y lo que distingue un modelo de eventos
discretos de un modelo de diferencias:

$$
S(\tau^{+}) = \Psi_{e}\big(S(\tau^{-}),\ \theta,\ U\big)
$$

donde $S$ es el vector de estado del sistema, $e$ el evento extraído de la LEF,
$\theta$ los parámetros y $U$ los aleatorios consumidos. Entre eventos consecutivos
$S$ es **constante** — es la definición del paradigma y conviene enunciarla.

De ahí se derivan dos familias:
- **Ecuaciones de diferencia por turno** para lo que evoluciona periódicamente
  (oro en E1, moral y poder militar en E9). Aquí sí cabe la notación $X_{t+1} = \dots$
  del ejemplo del enunciado.
- **Funciones de salto** para los eventos condicionales (E3, E4, E5, E6): el estado
  cambia solo si el evento ocurre.

### Paso 4 — Rellenar F1–F13 en orden de dependencia
1. Deterministas y locales: **F1** ingreso, **F8** fortificación, **F6** matriz de terreno, **F4** coste de movimiento.
2. Estocástica: **F7** `Aleatorio()` — distribución, soporte, media, varianza, **método de generación** (congruencial lineal + transformada inversa) y semilla.
3. Combate completo (E4): ensamblar F5·F6·F7·F8 en las expresiones de $P_a$ y $P_d$, el criterio de victoria y la fórmula de bajas con `K_BAJAS`. Relacionarlo explícitamente con **Lanchester** — el enunciado lo nombra.
4. Moral: **F5** pérdida y **F9** regeneración (→ repara D6).
5. Inteligencia artificial: **F2**, **F3**, **F11** — una regla por cada una de las 4 estrategias. Es la sección 3.2 entera.
6. Diplomacia: **F12** (→ repara D1 y D2, y habilita B3).
7. **F10** poder militar total.

### Paso 5 — Reparar D1–D7 y decidir O1
Cada reparación se documenta en la subsección *"Revisiones respecto al modelo
conceptual"*: defecto detectado → corrección adoptada → efecto sobre el modelo.

### Paso 6 — Cerrar los bucles causales
Volver al CLD del Parcial I y **demostrar, ecuación en mano, que los cuatro bucles
existen**: para cada uno, la cadena de variables y la ecuación que materializa cada
flecha. Los que no se puedan cerrar (B1, B3) se implementan o se retiran (→ D5).
Esta subsección es la que amarra la coherencia sistémica de todo el documento.

### Paso 7 — Condiciones de frontera *(sección 4, íntegra)*
Tabla variable → dominio → qué ocurre en cada extremo, y funciones por partes para
las discontinuidades: `oro < 0`, `fuerza → 0`, `moral = 0`, pérdida de capital,
`nivelFortificacion = 0`, saturación de la ventana de fases (D3), empate en
`cuotaLider`, imperio sin ejércitos pero con provincias.

### Paso 8 — Verificación
- **Balance dimensional** de todas las ecuaciones (tabla).
- **Coherencia de símbolos**: extraer los símbolos de §2, §3 y §4 y comprobar que
  están todos en la tabla de §1.
- **Trazabilidad**: cada evento E1–E10 ↔ sus ecuaciones ↔ su pseudocódigo.
- **Prueba de escritorio**: una traza numérica reproduciendo el ejemplo de LEF del
  turno 3 del Parcial I, pero con números reales.
- **Prueba de autosuficiencia**: buscar frases del tipo *"como en el juego"* —
  cada una es un agujero para el programador independiente que menciona el enunciado.

---

## 6. Índice del documento a entregar

Estructura propuesta, con el criterio de la rúbrica que alimenta cada parte.

```
Portada, resumen y objetivo del documento

1. Introducción y alcance                                          [—]
   1.1 Del modelo conceptual al modelo formal: qué añade este documento
   1.2 Revisiones respecto al Parcial I (tabla defecto → corrección)   ← D1–D7, O1
   1.3 Convenciones de notación

2. Diccionario formal de variables y parámetros              [Coherencia 15%]
   2.1 Variables de estado (por entidad: Imperio, Provincia, Ejército, Combate)
   2.2 Variables de estado del sistema (reloj, LEF, acumuladores)
   2.3 Variables de flujo
   2.4 Variables auxiliares
   2.5 Parámetros fijos: valor, unidad y procedencia [D]/[M]/[C]     ← P1–P9
   2.6 Trazabilidad variable → objetivo O1–O5 del Parcial I

3. Modelo matemático                                           [Rigor 35%]
   3.1 Ecuación de estado general de un modelo de eventos discretos
   3.2 Subsistema económico            → F1, tasa impositiva (O1)
   3.3 Subsistema militar y de movimiento → F4, F10
   3.4 Modelo de combate               → F5, F6, F7, F8 + Lanchester + K_BAJAS
   3.5 Generación de variables aleatorias → F7 en detalle: distribución,
       método congruencial lineal, transformada inversa, semilla, réplicas
   3.6 Dinámica de la moral            → F5, F9
   3.7 Subsistema diplomático          → F12
   3.8 Cierre de los bucles causales R1, B1, B2, B3
   3.9 Verificación del balance dimensional

4. Diseño algorítmico y lógica de decisión                    [Lógica 35%]
   4.1 Arquitectura del motor de simulación
   4.2 El reloj: función de fase, orden de la cola, invariantes   ← Paso 2
   4.3 La LEF: estructura, operaciones, complejidad, cancelación de eventos
   4.4 Ciclo de resolución del turno (pseudocódigo + diagrama de flujo)
   4.5 Pseudocódigo definitivo de E1–E10 (incluidos E7 y E8, ausentes en P.I)
   4.6 Árbol de decisión de la IA por estrategia                 ← F2, F3, F11
       4.6.1 AGRESIVA   4.6.2 DEFENSIVA
       4.6.3 ECONÓMICA  4.6.4 EQUILIBRADA
   4.7 Traza de escritorio: un turno completo, evento a evento

5. Condiciones de frontera y puntos críticos               [Fronteras 15%]
   5.1 Dominio y límites de cada variable de estado
   5.2 Funciones por partes (discontinuidades del modelo)
   5.3 Casos degenerados y su gestión
   5.4 Estabilidad: garantía de terminación de la simulación

6. Síntesis y enlace con el Parcial III
   6.1 Tabla de trazabilidad ecuación ↔ evento ↔ pseudocódigo
   6.2 Parámetros pendientes de calibración experimental

Anexos
   A. Tabla completa de parámetros con valores
   B. Escenario de referencia (mapa, adyacencias, terrenos, reparto inicial)
   C. Traza numérica extendida
   D. Notación (índice de símbolos)
```

**Ponderación del esfuerzo:** las secciones 3 y 4 valen el **70 %** de la nota.
Las secciones 1 y 6 y los anexos son baratos de producir y sostienen la coherencia.

---

## 7. Criterios de terminado

El documento está listo cuando se cumplen todos:

- [ ] Ninguna función del pseudocódigo se invoca sin estar definida en la sección 3 (F1–F13 cerradas).
- [ ] Ningún parámetro aparece sin valor, unidad y procedencia (P1–P9 cerrados).
- [ ] Todo símbolo de §3, §4 y §5 está declarado en §2.
- [ ] Todas las ecuaciones pasan la verificación dimensional.
- [ ] Los cuatro bucles del CLD están cerrados por ecuaciones o retirados con justificación.
- [ ] Los 10 eventos tienen pseudocódigo, incluidos **E7 y E8**.
- [ ] Está probado que la LEF nunca programa un evento en el pasado y que las fases no colisionan.
- [ ] Cada variable de estado tiene definido su dominio y el comportamiento en ambos extremos.
- [ ] Cada defecto D1–D7 está reparado o declarado como limitación consciente.
- [ ] Existe una traza numérica que un tercero puede reproducir a mano.
- [ ] **Prueba final:** un programador que no haya jugado nunca a Age of Conquest podría implementarlo leyendo solo este documento.

---

## 8. Decisiones pendientes antes de redactar

1. **Alcance** — ¿A, B o C de la §4? *(recomendado: C, modelo reconciliado)*
2. **Tasa impositiva** — ¿se incorpora O1? *(recomendado: sí)*
3. **Distribución de `Aleatorio()`** — Uniforme $U(a,b)$ multiplicativa es lo más
   simple de justificar y calibrar; Normal truncada es más realista;
   Triangular es un término medio habitual en simulación cuando solo se conocen
   mínimo, moda y máximo. *(recomendado: Uniforme $U(0.8, 1.2)$ y discutir las alternativas)*
4. **B1 y B3** — ¿se implementan o se retiran del modelo?
5. **Formato de entrega** — Markdown con LaTeX, PDF, o documento web.
