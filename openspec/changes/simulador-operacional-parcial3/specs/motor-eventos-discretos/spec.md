## ADDED Requirements

### Requirement: Reloj de simulación por saltos

El motor SHALL mantener un reloj $\tau\in\mathbb{R}_{\ge 0}$ codificado como $\tau = t + \varphi$ con
$t\in\mathbb{N}$ el turno y $\varphi\in[0,1)$ la fase. El reloj NO avanza con paso fijo: SHALL saltar
al instante del evento extraído de la LEF. El estado SHALL permanecer constante entre eventos
(propiedad de constancia, ec. 3.0).

#### Scenario: El reloj salta al instante del próximo evento
- **WHEN** el despachador extrae de la LEF un evento programado en $\tau = 5.817$ estando el reloj en $\tau = 5.10$
- **THEN** el reloj pasa a valer exactamente $5.817$ sin visitar ningún instante intermedio

#### Scenario: El reloj nunca retrocede
- **WHEN** se procesa cualquier secuencia de eventos de una partida completa
- **THEN** la sucesión de valores del reloj es monótona no decreciente (Teorema 2)

#### Scenario: Descomposición turno/fase
- **WHEN** el reloj vale $\tau = 5.817$
- **THEN** el turno consultado es $t = 5$ y la fase es $\varphi = 0.817$

### Requirement: Lista de Eventos Futuros con orden total estricto

El motor SHALL implementar la LEF como un montículo binario ordenado por la clave lexicográfica
$(\tau,\ \pi,\ \varsigma)$ de la ec. (4.2), donde $\pi$ es la prioridad de fase de la tabla 4.1 y
$\varsigma$ es un contador global monótono asignado en el momento de la inserción. La clave SHALL ser
un orden total estricto: no puede haber dos eventos con la misma clave.

#### Scenario: Desempate por prioridad de fase
- **WHEN** dos eventos están programados en el mismo instante $\tau$, uno de tipo FIN_TURNO ($\pi=7$) y otro de tipo CONQUISTA ($\pi=5$)
- **THEN** se extrae primero la CONQUISTA

#### Scenario: Desempate por secuencia de inserción
- **WHEN** dos movimientos de imperios distintos llegan a la misma provincia en el mismo instante y con la misma prioridad
- **THEN** se resuelve primero el insertado antes (menor $\varsigma$), y el segundo encuentra la provincia ya modificada

#### Scenario: Determinismo total dada la semilla
- **WHEN** se ejecutan dos veces la misma partida con el mismo escenario y la misma semilla $s_0$
- **THEN** ambas ejecuciones producen exactamente la misma secuencia de eventos y el mismo estado final

#### Scenario: Coste logarítmico
- **WHEN** se insertan o extraen eventos de la LEF
- **THEN** el coste es $O(\log|\mathcal{L}|)$ y el tamaño de la LEF permanece acotado por $O(|\mathcal{I}| + \sum_i|\mathcal{A}_i|)$

### Requirement: Catálogo de eventos y función de fase

El motor SHALL soportar los diez tipos de evento E1–E10 con las fases y prioridades de la tabla 4.1:
E1 INICIO_TURNO ($\varphi=0.00$, $\pi=0$), E7/E8 DIPLOMACIA ($0.05$, $1$), E2 PLANIFICACIÓN
($0.10$, $2$), E3 MOVIMIENTO ($[0.15,\,0.15+\Delta)$, $3$), E4 RESOLUCIÓN_COMBATE ($+\varepsilon$, $4$),
E5 CONQUISTA ($+\varepsilon$, $5$), E6 ELIMINACIÓN ($+\varepsilon$, $6$), E9 FIN_TURNO ($0.90$, $7$),
E10 FIN_JUEGO ($0.95$, $8$), con $\varepsilon = 10^{-3}$.

#### Scenario: La diplomacia precede a la planificación
- **WHEN** se procesa el turno $t$
- **THEN** el evento de diplomacia se ejecuta en $t{+}0.05$, antes de cualquier PLANIFICACIÓN en $t{+}0.10$

#### Scenario: E1 programa los eventos periódicos del turno
- **WHEN** se ejecuta E1 en el turno $t$
- **THEN** quedan programados un evento DIPLOMACIA en $t{+}0.05$, una PLANIFICACIÓN en $t{+}0.10$ por cada imperio activo, y un FIN_TURNO en $t{+}0.90$

#### Scenario: Cadena condicional de un movimiento
- **WHEN** un MOVIMIENTO hacia provincia enemiga se resuelve en $\tau$
- **THEN** se programa RESOLUCIÓN_COMBATE en $\tau+\varepsilon$, y si vence el atacante, CONQUISTA en $\tau+2\varepsilon$ y, si procede, ELIMINACIÓN en $\tau+3\varepsilon$

### Requirement: Función de llegada dentro de la ventana de movimiento

El motor SHALL convertir un coste de movimiento $c$ (en turnos) en un instante de la LEF mediante
$\tau_{\text{lleg}}(t,c) = (t + \lfloor c/\Delta\rfloor) + \varphi_{\text{MOV}} + (c \bmod \Delta)$
(ec. 4.4), con $\varphi_{\text{MOV}}=0.15$ y $\Delta = \varphi_{\text{FIN}} - \varphi_{\text{MOV}} - 4\varepsilon = 0.746$.

#### Scenario: Movimiento que se resuelve en el mismo turno
- **WHEN** un ejército con $v_a=1.5$ avanza a una LLANURA ($w=1.0$, $c=0.667$) en el turno $5$
- **THEN** la llegada se programa en $\tau = 5.817$

#### Scenario: Movimiento que se extiende al turno siguiente
- **WHEN** un ejército con $v_a=1.5$ cruza una MONTAÑA ($w=2.0$, $c=1.333$) en el turno $3$
- **THEN** la llegada se programa en $\tau = 4.737$

#### Scenario: Toda llegada cae dentro de la ventana válida
- **WHEN** se calcula $\tau_{\text{lleg}}$ para cualquier $c\ge 0$
- **THEN** la fase resultante pertenece a $[0.15,\ 0.896)$ y la cadena completa de eventos derivada concluye estrictamente antes de $\varphi_{\text{FIN}}=0.90$ (Teorema 1)

### Requirement: Revalidación y cancelación de eventos

Antes de procesar cualquier evento, el despachador SHALL evaluar su predicado de validez de la tabla
4.3; si es falso, el evento SHALL descartarse sin efecto alguno sobre el estado.

#### Scenario: Movimiento cancelado por cambio diplomático
- **WHEN** un MOVIMIENTO hacia territorio enemigo llega a su instante pero entretanto la relación pasó de GUERRA a PAZ
- **THEN** el predicado `Legal` es falso y el evento se descarta sin generar combate

#### Scenario: Movimiento de un imperio eliminado
- **WHEN** un MOVIMIENTO llega a su instante y su imperio propietario tiene $\alpha_i = 0$
- **THEN** el evento se descarta sin efecto

#### Scenario: Conquista de una provincia ya perdida
- **WHEN** un evento CONQUISTA se procesa pero el conquistador ya no controla la posición desde la que atacaba o ha dejado de estar activo
- **THEN** el evento se descarta sin cambiar el propietario

#### Scenario: Combate cuyo defensor ya cambió
- **WHEN** un evento RESOLUCIÓN_COMBATE se procesa y el propietario de la provincia ya no es el defensor original
- **THEN** el evento se descarta sin resolver bajas

### Requirement: Bucle principal, terminación y ausencia de bloqueo

El motor SHALL ejecutar el bucle `MIENTRAS 𝓛 ≠ ∅ ∧ Z = 0` de §4.1: extraer el mínimo, revalidar,
saltar el reloj, despachar y recolectar estadísticas. La simulación SHALL terminar en un número
finito de eventos, acotado por $t_{\max}(3+|\mathcal{I}|+4|\mathcal{I}|A_{\max})$ (Teorema 3).

#### Scenario: Terminación por cuota de victoria
- **WHEN** al ejecutar E9 la cuota del líder cumple $q_\ell \ge \Theta_V = 0.60$
- **THEN** se programa FIN_JUEGO en $t{+}0.95$ en lugar de INICIO_TURNO, y el bucle se detiene al fijar $Z \leftarrow 1$

#### Scenario: Terminación por imperio único
- **WHEN** al ejecutar E9 queda $m = 1$ imperio activo
- **THEN** se programa FIN_JUEGO y la partida concluye

#### Scenario: Terminación forzada por límite de turnos
- **WHEN** al ejecutar E9 se cumple $t \ge t_{\max} = 200$ sin que ningún imperio alcance la cuota
- **THEN** se programa FIN_JUEGO con el líder actual como ganador

#### Scenario: La LEF nunca se vacía antes de terminar
- **WHEN** se procesa cualquier E9 con $Z = 0$
- **THEN** queda programado al menos un evento futuro (INICIO_TURNO o FIN_JUEGO)

### Requirement: Recolector de estadísticas

El motor SHALL acumular durante la ejecución las magnitudes que alimentan las métricas O1–O5:
número de combates $\nu$, bajas totales $\beta$, series por turno de $n_i$, $G_i$, $M_i$, $q_i$, el
turno de eliminación de cada imperio y el primer turno con $q_\ell \ge 0.5$.

#### Scenario: Registro de un combate
- **WHEN** se resuelve un evento E4
- **THEN** $\nu$ se incrementa en 1 y $\beta$ crece en $b_{\text{gan}} + b_{\text{perd}}$

#### Scenario: Reporte final de la partida
- **WHEN** se ejecuta E10
- **THEN** se registra imperio ganador, su estrategia, turno final, $\nu$, $\beta$ y cuota final $n_{\text{gan}}/N$
