# Guion de la defensa — Parcial III

Todo es reproducible: misma semilla → misma salida. Ensayar dos veces antes de la sesión.
Ejecutar siempre desde la raíz del repositorio.

---

## Demostración en vivo (≈ 8 min)

### 1. Verificación contra el documento (1 min) — **abrir con esto**

```bash
python3 -m sim --traza-dorada
```

> «El Parcial II incluye en §4.7 un turno completo calculado a mano. Lo hemos convertido
> en prueba automatizada: 44 magnitudes comparadas contra el documento, 44 coinciden.
> Ejercita seis de los diez eventos y en torno al 70 % del modelo.»

Si preguntan por la tolerancia: los residuos son del documento, que imprime valores
intermedios redondeados a 2–4 decimales; el error relativo máximo es 5.1×10⁻⁴.

### 2. Cinco fases consecutivas (2 min) — **el requisito literal del enunciado**

```bash
python3 -m sim --turnos 5
```

Señalar en la traza:
- El reloj **salta**: 1.000 → 1.100 → 1.900 → 2.000. No avanza con paso fijo.
- Las cuatro estrategias se comportan distinto ya en el turno 1: AGRESIVA θ=125,
  DEFENSIVA θ=100 y fortifica, ECONÓMICA θ=75 adaptativa, EQUILIBRADA θ=87.5.
- En el turno 2 salta la diplomacia: guerra por oportunismo con M_i/M_j = 1.54.

### 3. La LEF, que es la aportación técnica central (2 min)

```bash
python3 -m sim --interactivo
> avanzar 1
> ver lef
```

> «Aquí está la cola. Los movimientos ordenados en el turno 1 están programados para
> τ = 2.337, no para el turno 1. Cruzar un BOSQUE cuesta 1.4/1.5 = 0.933 turnos, y
> Δ = 0.746 — así que la llegada se desborda al turno siguiente. Con 10 llanuras de 24
> provincias, el 58 % de los destinos difieren la llegada: por eso el modelo **necesita**
> una lista de eventos futuros y no le basta un bucle de fases.»

Mostrar la clave (τ, π, ς): el orden total estricto que hace la simulación reproducible.

### 4. Entrada de variables (1 min)

```bash
> ver imperio cimeria
> fijar tasa cimeria 150
> avanzar 1          # la renta se dispara
> fijar tasa cimeria 300
```

La última se rechaza indicando el dominio [0, 150] — §5.1.

```bash
> evaluar p00 p01
```

Muestra P_a, P_d, k y el régimen, y qué haría cada una de las cuatro estrategias.
**Sin consumir un solo número aleatorio.**

### 5. Un caso borde (2 min) — elegir bancarrota o impuestos

```bash
python3 -m sim --caso impuestos
```

> «El descontento sube 4.5 puntos por turno. En el turno 13 todas las provincias han
> cruzado D* = 60 y la renta cae **de golpe** a cero: no es degradación, es un salto.
> Un turno después el imperio entra en insolvencia y su ejército pasa de 759 a 291
> unidades; al siguiente, a cero. De 7 provincias a 1 en seis turnos.»

---

## Sustentación técnica — preguntas previsibles

**¿Por qué eventos discretos y no un bucle de fases por turno?**
Porque el 58 % de los movimientos no se resuelven en el turno en que se ordenan: solo
la llanura (c = 0.667) cabe en Δ = 0.746. Un ejército que cruza una montaña tarda 1.333
turnos y debe llegar con el estado del mapa que exista entonces, no con el de cuando se
ordenó. Es la justificación formal de la LEF, y ahora está cuantificada.

**¿Por qué la ley lineal de Lanchester y no la cuadrática?**
Porque b_gan = K_B·(m_d/m_a)·D_p es proporcional a la fuerza inicial del perdedor e
**independiente de la propia** — esa es la firma de la ley lineal. Lanchester dedujo la
cuadrática para el fuego dirigido moderno, donde la superioridad numérica se multiplica,
y la lineal para el combate antiguo, donde solo las unidades en contacto con la línea de
frente combaten. Age of Conquest es guerra premoderna. Además la fórmula (3.21) es la
**solución cerrada exacta** de la EDO, así que resolver la batalla en un solo evento no
es una aproximación.

**¿Por qué la distribución triangular?**
Porque de la incertidumbre táctica solo conocemos mínimo, moda y máximo plausibles. Tiene
transformada inversa en forma cerrada (sin aceptación-rechazo), E[U] = 1 exacta —así el
azar no sesga la potencia media de ningún bando— y **soporte compacto**: eso es lo que
crea los dos regímenes de la Proposición 1. Con una normal no truncada ninguna batalla
estaría nunca garantizada.

**¿Por qué no usaron `random`?**
Porque `random.random()` mezcla la semilla y compone el valor de dos extracciones, así
que R_k ≠ X_{k+1}/m. El documento define exactamente R_k = X_{k+1}/2⁴⁸ y afirma que cada
combate consume dos números. Con generador propio podemos **verificarlo**: en la partida
de referencia, ν = 15 combates y 30 números consumidos.

**¿Por qué el reloj es entero y no `float`?**
Porque la clave de la LEF debe ser un orden total estricto. Sumando repetidamente
ε = 10⁻³ y restos c mod Δ en coma flotante, dos eventos que deberían caer en el mismo
instante pueden separarse o colisionar según el orden de las operaciones, y la simulación
dejaría de ser reproducible. Un turno son 10⁶ micro-fases enteras.

**¿Por qué no validaron contra el juego real?**
Porque de los 35 parámetros del modelo **uno solo** está documentado del juego; los demás
son decisiones de modelado. El modelo del Parcial II modela el sistema, no el ejecutable,
y abstrae deliberadamente puntos de acción, rey, saqueo y revueltas. Una coincidencia
numérica sería un artefacto de calibración, no evidencia de validez. Usamos en su lugar
nueve técnicas de validación reconocidas, y declaramos cuál falta y por qué.

**¿Cuál es su mejor evidencia de validez, entonces?**
La validación predictiva. El documento dedujo en §3.2.4, antes de existir el simulador,
que n^max en guerra son 13 provincias mientras la victoria exige 15 — y concluyó que un
imperio en guerra permanente no puede ganar. En 40 réplicas, **0 ganadores estuvieron en
guerra continua**. La partida de referencia termina atascada en q = 0.500 con los dos
supervivientes en guerra y en bancarrota, exactamente en la banda que la ecuación (3.9)
predice como insostenible.

**¿Encontraron algún error en su propio modelo?**
Uno. E2 itera sobre todos los ejércitos sin comprobar si alguno está en tránsito; como
la mayoría de los movimientos tardan más de un turno, un ejército recibía órdenes nuevas
mientras viajaba y destacaba retaguardia otra vez, drenándose. El modelo declara ω_a
`enCombate` pero no su equivalente para el movimiento. Lo reparamos con una guarda de
tránsito y está declarado en la tabla de trazabilidad del informe.

**¿Qué no funcionó como esperaban?**
Dos cosas, y las reportamos. (1) El balance entre estrategias no existe: ECONÓMICA gana
40 de 40 réplicas, porque es la única con política fiscal adaptativa y los parámetros de
sobreextensión hacen que eso lo decida todo. (2) K_B afecta a la intensidad bélica como
se predijo (535 → 789 bajas, monótono) pero **no a la duración** — los intervalos se
solapan. La causa es que 12 de las 24 provincias son neutrales con guarnición nula, así
que la mayor parte de la expansión ocurre sin combate y un parámetro del combate no puede
gobernar la duración.

**¿Y el clon en Java del repositorio?**
Es una exploración previa del sistema por ingeniería inversa del juego. Implementa un
modelo distinto —incremento fijo, combate determinista, felicidad, revueltas, rey— y nos
sirvió para entender las mecánicas que fundamentan las abstracciones del modelo formal.
No es el modelo operacional que defendemos; ese es `sim/` en Python.

---

## Recorrido del código, si lo piden

| Piden ver | Abrir |
|:--|:--|
| La LEF y el reloj | `sim/reloj.py` — clave (4.2), función de llegada (4.4) |
| El bucle del motor | `sim/eventos.py` → clase `Motor` |
| Los diez eventos | `sim/eventos.py` → `e1_…` a `e10_…` |
| El combate | `sim/militar.py` → `resolver_combate` (3.14)–(3.21) |
| El azar | `sim/azar.py` — LCG (3.26) y triangular inversa (3.25) |
| Las cuatro estrategias | `sim/agentes.py` → `PARAMETROS_ESTRATEGIA` |
| Las fronteras | `sim/economia.py` → `insolvencia`; `sim/estado.py` → dominios |
| La verificación | `tests/test_traza_dorada.py` |

Cada método cita en su comentario la ecuación y el evento del documento que implementa.
