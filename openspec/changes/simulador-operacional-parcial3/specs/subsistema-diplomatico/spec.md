## ADDED Requirements

### Requirement: Guarda diplomática de los movimientos

El simulador SHALL considerar legal un movimiento del imperio $i$ hacia la provincia $q$ si y solo si
$\pi_q = i$, o $\pi_q = \varnothing$, o $\delta_{i,\pi_q} = \text{GUERRA}$, o
$\delta_{i,\pi_q} = \text{ALIANZA}$ en régimen de tránsito. Un movimiento hacia territorio de un
imperio con el que se está en PAZ SHALL rechazarse (reparación D1).

#### Scenario: Movimiento hacia territorio en paz
- **WHEN** un imperio intenta planificar un movimiento hacia provincia de otro con el que está en PAZ
- **THEN** el destino no es elegible y no se programa ningún MOVIMIENTO

#### Scenario: Movimiento hacia territorio aliado
- **WHEN** un ejército entra en provincia de un aliado
- **THEN** transita sin combate y la provincia no cambia de dueño

#### Scenario: Movimiento hacia territorio enemigo
- **WHEN** un ejército entra en provincia de un imperio con el que está en GUERRA
- **THEN** el movimiento es legal y desencadena combate

#### Scenario: Paz firmada durante el tránsito
- **WHEN** un MOVIMIENTO programado hacia territorio enemigo llega a su instante y entretanto la relación pasó a PAZ
- **THEN** la revalidación de E3 lo cancela sin efecto

### Requirement: Declaración de guerra

En el evento E7, ejecutado en la fase $\varphi_{\text{DIP}} = 0.05$, el simulador SHALL declarar la
guerra según la ec. (3.30): coalición anti-líder cuando $q_\ell \ge \theta_{\text{am}}$, o agresión
oportunista cuando $M_i/\max(M_j,1) \ge \gamma_\sigma^{\sigma_i}$ y los imperios son adyacentes.
Cada imperio SHALL declarar como máximo una guerra oportunista nueva por turno.

#### Scenario: Coalición anti-líder
- **WHEN** el líder alcanza $q_\ell = 0.42 \ge \theta_{\text{am}} = 0.40$
- **THEN** todos los demás imperios activos que estén en PAZ con él le declaran la guerra en ese mismo turno

#### Scenario: Agresión oportunista de una estrategia agresiva
- **WHEN** un imperio AGRESIVA ($\gamma_\sigma = 1.2$) es adyacente a otro con el que está en PAZ y tiene $M_i/M_j = 1.3$
- **THEN** le declara la guerra

#### Scenario: Estrategia económica se abstiene
- **WHEN** un imperio ECONÓMICA ($\gamma_\sigma = 3.0$) tiene $M_i/M_j = 2.0$ frente a un vecino
- **THEN** no declara la guerra

#### Scenario: No hay agresión sin adyacencia
- **WHEN** un imperio tiene superioridad militar aplastante sobre otro con el que no comparte frontera
- **THEN** no le declara la guerra por oportunismo

#### Scenario: Una guerra oportunista por turno
- **WHEN** un imperio cumple el criterio de oportunismo frente a dos vecinos a la vez
- **THEN** declara la guerra solo al primero según el orden determinista de recorrido

#### Scenario: Simetría de la declaración
- **WHEN** se declara $\delta_{ij} \leftarrow \text{GUERRA}$
- **THEN** también $\delta_{ji} \leftarrow \text{GUERRA}$

### Requirement: Formación y ruptura de alianzas

En el evento E8 el simulador SHALL formar alianza entre dos imperios en PAZ que estén ambos en guerra
con el líder amenazante (3.31), y disolverla con histéresis cuando $q_\ell < \theta_{\text{am}} - \varsigma_h$
(3.32). NO SHALL ser posible declarar la guerra a un aliado.

#### Scenario: Alianza contra el líder
- **WHEN** dos imperios en PAZ entre sí están ambos en GUERRA con el líder y $q_\ell \ge 0.40$
- **THEN** se establece $\delta_{ij} = \text{ALIANZA}$ en ambos sentidos

#### Scenario: Ruptura con histéresis
- **WHEN** la cuota del líder cae a $q_\ell = 0.34 < 0.40 - 0.05$
- **THEN** las alianzas existentes se disuelven y pasan a PAZ

#### Scenario: La histéresis impide la oscilación
- **WHEN** la cuota del líder oscila entre $0.38$ y $0.41$ en turnos consecutivos
- **THEN** las alianzas ya formadas se mantienen mientras $q_\ell \ge 0.35$, sin alternar formación y disolución cada turno

#### Scenario: No se puede atacar a un aliado
- **WHEN** un imperio evalúa la declaración de guerra contra un aliado
- **THEN** la condición $\delta_{ij} = \text{PAZ}$ de (3.30) falla y no se declara la guerra

### Requirement: Determinación del líder y de la cuota territorial

El simulador SHALL calcular en cada evento de diplomacia y en E9 la cuota $q_i = n_i/N$ de cada
imperio activo y el líder $\ell = \arg\max_i q_i$, resolviendo empates por menor identificador.

#### Scenario: Cuota de un imperio
- **WHEN** un imperio controla $4$ de las $24$ provincias del mapa
- **THEN** su cuota es $q_i = 0.167$

#### Scenario: Desempate determinista del líder
- **WHEN** dos imperios activos empatan en el número máximo de provincias
- **THEN** el líder es el de menor identificador, de forma reproducible entre ejecuciones

### Requirement: Efecto diplomático de la conquista y la eliminación

Toda conquista de una provincia a un imperio no neutral SHALL fijar la relación entre conquistador y
antiguo dueño en GUERRA. Toda eliminación SHALL poner en PAZ todas las relaciones del imperio
eliminado.

#### Scenario: Conquista genera estado de guerra
- **WHEN** un imperio conquista una provincia a otro con el que estaba en PAZ por vía de una cadena de eventos previa
- **THEN** ambas entradas de la matriz pasan a GUERRA

#### Scenario: Eliminación limpia la matriz
- **WHEN** un imperio es eliminado en E6
- **THEN** todas sus relaciones quedan en PAZ y deja de contarse como enemigo a efectos del término $\eta_w$ del descontento
