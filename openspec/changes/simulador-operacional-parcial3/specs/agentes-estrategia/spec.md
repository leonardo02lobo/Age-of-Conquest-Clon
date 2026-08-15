## ADDED Requirements

### Requirement: Las cuatro estrategias como vector de parámetros

El simulador SHALL definir las estrategias AGRESIVA, DEFENSIVA, ECONÓMICA y EQUILIBRADA como un
vector de seis parámetros ($\theta^\sigma$, $f_{\text{rec}}$, $\gamma_{\text{atq}}$, $\gamma_\sigma$,
$f_{\text{gua}}$, $f_{\text{fort}}$) con los valores de la tabla §2.4.9, y SHALL asignar a cada
imperio exactamente una estrategia constante durante toda la partida.

#### Scenario: Valores de la tabla de estrategias
- **WHEN** se consultan los parámetros de cada estrategia
- **THEN** AGRESIVA vale $(125,\ 0.90,\ 1.1,\ 1.2,\ 0.15,\ 0.05)$, DEFENSIVA $(100,\ 0.60,\ 1.8,\ 2.5,\ 0.50,\ 0.40)$, ECONÓMICA $(\theta^{\text{eq}},\ 0.30,\ 2.0,\ 3.0,\ 0.40,\ 0.25)$ y EQUILIBRADA $(\tfrac12(100+\theta^{\text{eq}}),\ 0.70,\ 1.4,\ 1.8,\ 0.30,\ 0.20)$

#### Scenario: La estrategia no cambia durante la partida
- **WHEN** transcurre una partida completa
- **THEN** $\sigma_i$ conserva el valor asignado en la inicialización

### Requirement: Política de decisión determinista

La política de cada imperio SHALL ser determinista: dado el mismo estado, produce las mismas
decisiones. Toda la aleatoriedad del modelo reside en $U_a$ y $U_d$; ningún agente SHALL consumir
números del generador aleatorio.

#### Scenario: Misma decisión ante el mismo estado
- **WHEN** se evalúa la planificación de un imperio dos veces sobre estados idénticos
- **THEN** se obtienen exactamente las mismas órdenes

#### Scenario: El agente no toca el generador
- **WHEN** se ejecuta un turno completo sin ningún combate
- **THEN** el contador de números consumidos del LCG no avanza

### Requirement: Política fiscal por estrategia

En el evento E2 el simulador SHALL fijar la tasa impositiva según la estrategia: valor fijo para
AGRESIVA ($125$) y DEFENSIVA ($100$); $\theta^{\text{eq}}$ recalculada cada turno para ECONÓMICA; y
$\tfrac12(100+\theta^{\text{eq}})$ para EQUILIBRADA. El resultado SHALL recortarse a
$[0,\ \theta_{\max}]$.

#### Scenario: La estrategia económica baja la tasa al entrar en guerra
- **WHEN** un imperio ECONÓMICA en paz con $\theta = 75$ entra en guerra
- **THEN** su tasa pasa a $\approx 41.7$ en la siguiente planificación

#### Scenario: La estrategia económica baja la tasa al sobreextenderse
- **WHEN** un imperio ECONÓMICA en paz supera $n^\ast = 8$ provincias llegando a $n_i = 10$
- **THEN** su tasa objetivo baja en $\eta_n\cdot2/\eta_\theta \approx 16.7$ puntos

#### Scenario: Recorte al mínimo
- **WHEN** la fórmula de equilibrio devuelve un valor negativo
- **THEN** la tasa aplicada es $0$

#### Scenario: La estrategia agresiva no se adapta
- **WHEN** un imperio AGRESIVA entra en guerra y se sobreextiende
- **THEN** su tasa sigue siendo $125$ y su descontento crece sin freno

### Requirement: Decisión de fortificación

En el evento E2 el simulador SHALL fortificar cuando $G_i \ge c_\phi / f_{\text{fort}}^{\sigma_i}$,
eligiendo la provincia fronteriza con menor fuerza defensiva $\mathcal{D}_p$ entre las que tengan
$\phi_p < \Phi_{\max}$, y descontando $c_\phi$ del tesoro.

#### Scenario: Estrategia defensiva fortifica pronto
- **WHEN** un imperio DEFENSIVA ($f_{\text{fort}} = 0.40$) tiene $G_i \ge 100$ y una frontera sin fortificar
- **THEN** fortifica la provincia fronteriza más débil

#### Scenario: Estrategia agresiva casi nunca fortifica
- **WHEN** un imperio AGRESIVA ($f_{\text{fort}} = 0.05$) tiene $G_i = 200$
- **THEN** no fortifica, porque el umbral exigido es $40/0.05 = 800$

#### Scenario: Sin frontera fortificable
- **WHEN** todas las provincias fronterizas están ya en $\Phi_{\max}$
- **THEN** no se fortifica y el tesoro no cambia

### Requirement: Selección de objetivos

En el evento E2, para cada ejército el simulador SHALL evaluar los destinos legales adyacentes con
las potencias deterministas ($U = 1$) y atacar solo si
$P_a^{\text{det}} \ge \gamma_{\text{atq}}^{\sigma_i} \cdot P_d^{\text{det}}$, eligiendo entre los
candidatos válidos el de mayor población $L_q$. Antes de partir SHALL dejar $f_{\text{gua}}^{\sigma_i} F_a$
como refuerzo de la guarnición de origen.

#### Scenario: Ataque de la traza de escritorio
- **WHEN** un ejército AGRESIVA obtiene $P_a^{\text{det}} = 95.88$ frente a $P_d^{\text{det}} = 80.08$
- **THEN** ataca, porque $95.88 \ge 1.1\cdot80.08 = 88.09$

#### Scenario: La misma situación con estrategia defensiva
- **WHEN** el mismo ejército pertenece a un imperio DEFENSIVA ($\gamma_{\text{atq}} = 1.8$)
- **THEN** no ataca, porque exigiría $144.1$

#### Scenario: Retaguardia al partir
- **WHEN** un ejército AGRESIVA de $F_a = 120$ parte al ataque con $f_{\text{gua}} = 0.15$
- **THEN** la guarnición de origen sube en $18$ y el ejército parte con $F_a = 102$

#### Scenario: Desempate por población
- **WHEN** dos destinos cumplen el criterio de ataque
- **THEN** se elige el de mayor $L_q$, y a igualdad de población el de menor id

#### Scenario: Las estrategias conservadoras operan en régimen determinista
- **WHEN** una estrategia con $\gamma_{\text{atq}} \ge 1.5$ decide atacar
- **THEN** el cociente determinista cumple $k \le 2/3$ y la victoria está garantizada por la Proposición 1

### Requirement: Refuerzo de frontera por BFS multifuente

Cuando un ejército no encuentre ningún objetivo rentable, el simulador SHALL avanzarlo un paso hacia
la frontera usando un BFS multifuente desde $\partial\mathcal{P}_i$ sobre el subgrafo del territorio
propio, con coste $O(|\mathcal{P}_i| + |E_i|)$ por imperio y turno.

#### Scenario: Ejército del interior avanza
- **WHEN** un ejército está en una provincia interior a distancia $2$ de la frontera
- **THEN** se programa un movimiento hacia una provincia propia adyacente a distancia $1$

#### Scenario: Ejército ya en la frontera
- **WHEN** un ejército está en una provincia fronteriza
- **THEN** no se mueve y su fuerza refuerza $\mathcal{D}_p$

#### Scenario: Imperio sin frontera alcanzable
- **WHEN** ninguna provincia propia del ejército tiene vecino ajeno alcanzable
- **THEN** el ejército mantiene posición sin programar movimiento

#### Scenario: Desempate determinista del paso
- **WHEN** dos vecinos propios están a la misma distancia menor de la frontera
- **THEN** se elige de forma determinista y reproducible entre ejecuciones
