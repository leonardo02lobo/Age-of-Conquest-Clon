## ADDED Requirements

### Requirement: Prueba dorada de la traza de escritorio

El proyecto SHALL incluir una prueba automatizada que reproduzca exactamente la traza de escritorio
de §4.7 del documento del Parcial II: partiendo del estado declarado al inicio del turno $5$ y con
los sorteos $R_1 = 0.7314$ y $R_2 = 0.2891$ inyectados, todos los valores intermedios y finales SHALL
coincidir con los del documento dentro de una tolerancia declarada.

#### Scenario: Fase económica del turno 5
- **WHEN** se ejecuta E1 sobre el estado inicial de la traza
- **THEN** $R_1 = 128.125$, $C_1 = 13.75$ y $G_1 = 264.375$

#### Scenario: Planificación del turno 5
- **WHEN** se ejecuta E2 del imperio AGRESIVA
- **THEN** recluta $158$ unidades, el tesoro queda en $27.375$, se levanta un ejército de $F = 148$, la guarnición de la capital queda en $30$, y se programa el movimiento en $\tau = 5.817$

#### Scenario: Resolución del combate
- **WHEN** se ejecuta E4 con los sorteos declarados
- **THEN** $U_a = 1.0534$, $U_d = 0.9521$, $P_a = 101.00$, $P_d = 76.24$, $b_{\text{gan}} = 53.89$, $F_a = 48.11$ y $\mu_a = 0.692$

#### Scenario: Conquista y cierre de turno
- **WHEN** se ejecutan E5 y E9
- **THEN** la provincia conquistada queda con $\phi = 1$ y $D = 45$ tras el $\Delta D = +5.0$, su población pasa a $3792$, la moral del ejército sube a $0.792$ y $M_1 = 259.11$

#### Scenario: Fallo detectable
- **WHEN** cualquier ecuación del motor se altera
- **THEN** la prueba dorada falla señalando la magnitud divergente

### Requirement: Pruebas unitarias por ecuación

El proyecto SHALL incluir al menos una prueba por cada ecuación numerada del capítulo 3 del documento
y por cada función por partes del capítulo 5, con casos que ejerciten ambos lados de cada
discontinuidad.

#### Scenario: Cobertura de ecuaciones
- **WHEN** se ejecuta la suite de pruebas
- **THEN** existe una prueba identificable para cada una de (3.1)–(3.34) y (5.1)–(5.7)

#### Scenario: Ambos lados del umbral fiscal
- **WHEN** se prueba (5.1)
- **THEN** hay un caso con $D_p = D^\ast - 1$ que tributa y otro con $D_p = D^\ast$ que no tributa

#### Scenario: Los tres regímenes del combate
- **WHEN** se prueba (5.3)
- **THEN** hay casos con $k \le 2/3$, $2/3 < k < 3/2$ y $k \ge 3/2$, verificando victoria garantizada, resultado dependiente del sorteo y derrota garantizada

### Requirement: Aserciones de las garantías demostradas

El proyecto SHALL verificar automáticamente los tres teoremas del documento sobre partidas completas.

#### Scenario: Validez de la ventana de movimiento
- **WHEN** se ejecutan partidas completas registrando toda llegada programada
- **THEN** la fase de cada llegada pertenece a $[0.15,\ 0.896)$ y ninguna cadena derivada alcanza $\varphi_{\text{FIN}} = 0.90$ (Teorema 1)

#### Scenario: Causalidad
- **WHEN** se ejecutan partidas completas registrando padre e hijo de cada programación
- **THEN** ningún evento se programa en un instante anterior al de su evento padre (Teorema 2)

#### Scenario: Terminación
- **WHEN** se ejecutan partidas con configuraciones extremas de los parámetros calibrables
- **THEN** todas terminan antes del límite $t_{\max}(3+|\mathcal{I}|+4|\mathcal{I}|A_{\max})$ eventos (Teorema 3)

#### Scenario: Orden total de la LEF
- **WHEN** se ejecutan partidas completas
- **THEN** no se observa ningún par de eventos con clave $(\tau,\pi,\varsigma)$ idéntica

### Requirement: Forzado de dominios y casos degenerados

El proyecto SHALL incluir pruebas que fuercen cada una de las doce variables acotadas de §5.1 a sus
fronteras y cada uno de los nueve casos degenerados de §5.3, comprobando que el simulador no produce
estados indefinidos, excepciones no controladas ni bucles infinitos.

#### Scenario: Bancarrota sostenida
- **WHEN** se construye un estado con déficit permanente
- **THEN** el simulador aplica la deserción, no genera oro negativo persistente y la partida termina

#### Scenario: Descontento máximo
- **WHEN** todas las provincias de un imperio alcanzan $D_p = 100$
- **THEN** su renta es $0$, $\Psi = 0.60$ y el imperio sobrevive hasta ser conquistado sin errores

#### Scenario: Imperio sin ejércitos
- **WHEN** un imperio pierde todos sus ejércitos conservando provincias
- **THEN** no es eliminado y levanta un ejército nuevo cuando su capital acumula excedente

#### Scenario: Ejército residual
- **WHEN** un combate deja $F_a < F_{\min}$
- **THEN** el ejército se disuelve y su fuerza se incorpora o se pierde según la titularidad de la provincia

### Requirement: Validación estructural contra el juego real

El proyecto SHALL documentar una tabla que confronte cada hecho documentado del juego real
*Age of Conquest IV* (parámetros marcados `[D]`) con el comportamiento observado del simulador, y
SHALL declarar explícitamente los aspectos que el modelo del Parcial II no reproduce.

#### Scenario: Contraste de hechos documentados
- **WHEN** se elabora la tabla de validación estructural
- **THEN** cada fila contiene el hecho del juego real, su fuente, la ecuación o el parámetro del modelo que lo recoge y la evidencia observada en el simulador

#### Scenario: Declaración de alcance
- **WHEN** el modelo no reproduce una mecánica del juego real (por ejemplo puntos de acción, rey, saqueo, decretos o revueltas)
- **THEN** la ausencia se declara explícitamente como decisión de modelado, no se presenta como equivalencia

#### Scenario: Comportamiento emergente esperado
- **WHEN** se ejecutan partidas completas del escenario de referencia
- **THEN** se comprueba si aparecen las tres fases predichas en §3.8 (expansión dominada por R1, fricción por B1 y B2, contención por B3) y el resultado se reporta tal como se observe

### Requirement: Calibración de los parámetros calibrables

El proyecto SHALL ejecutar un diseño de experimentos sobre los parámetros marcados `[C]`, con
réplicas apareadas, y SHALL reportar el efecto de cada uno sobre las métricas O1–O5 mediante tablas y
gráficas.

#### Scenario: Barrido de un parámetro
- **WHEN** se barre $K_B$ sobre un rango declarado con $n$ réplicas por variante
- **THEN** se reporta la duración media de la partida y la intensidad bélica por variante con su dispersión

#### Scenario: Sensibilidad de la sobreextensión
- **WHEN** se barren $\eta_n$ y $n^\ast$
- **THEN** se reporta el tamaño máximo sostenible observado y si la cuota de victoria resulta alcanzable

#### Scenario: Sensibilidad de la coalición
- **WHEN** se barre $\theta_{\text{am}}$
- **THEN** se reporta el turno del punto de inflexión (primer turno con $q_\ell \ge 0.5$)

#### Scenario: Balance entre estrategias
- **WHEN** se barre $\gamma_{\text{atq}}$ o se ejecuta la configuración de referencia
- **THEN** se reporta la tasa de victoria por estrategia con su intervalo de confianza

#### Scenario: Comparación con observaciones del juego real
- **WHEN** se dispone de observaciones capturadas del juego real bajo condiciones iniciales controladas
- **THEN** se reporta el error del simulador frente a esas observaciones y el valor calibrado de los parámetros `[C]` que lo minimiza; si no fue posible capturarlas, se declara y se justifica el valor adoptado
