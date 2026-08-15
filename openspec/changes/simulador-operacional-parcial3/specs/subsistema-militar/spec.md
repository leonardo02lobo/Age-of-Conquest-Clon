## ADDED Requirements

### Requirement: Matriz de terreno

El simulador SHALL asociar a cada provincia un terreno $T_p$ y aplicar la matriz $\mathcal{T}$ de
§2.4.6: LLANURA $(1.00,\ 1.00,\ w{=}1.0)$, BOSQUE $(0.90,\ 1.15,\ w{=}1.4)$, MONTAÑA
$(0.80,\ 1.30,\ w{=}2.0)$, COSTA $(0.95,\ 1.10,\ w{=}1.2)$, en el orden (ataque, defensa, coste de cruce).

#### Scenario: El terreno modifica el combate
- **WHEN** se resuelve un combate en una MONTAÑA
- **THEN** la potencia del atacante se multiplica por $0.80$ y la del defensor por $1.30$

#### Scenario: El terreno modifica el movimiento
- **WHEN** un ejército con $v_a = 1.5$ entra en un BOSQUE
- **THEN** el coste es $c = 1.4/1.5 = 0.933$ turnos

### Requirement: Reclutamiento y levantamiento de ejércitos

En el evento E2 el simulador SHALL reclutar $u_i = \lfloor f_{\text{rec}}^{\sigma_i} G_i / c_u \rfloor$
unidades (3.11) que se incorporan a la guarnición de la capital, descontando $c_u u_i$ del tesoro.
Cuando la guarnición de la capital supere $g_{\text{ret}}$ y el imperio tenga menos de $A_{\max}$
ejércitos, SHALL levantar un ejército nuevo con el excedente.

#### Scenario: Reclutamiento de una estrategia agresiva
- **WHEN** un imperio AGRESIVA tiene $G_i = 264.375$ y $f_{\text{rec}} = 0.90$
- **THEN** recluta $\lfloor 0.90\cdot264.375/1.5 \rfloor = 158$ unidades y su tesoro queda en $27.375$

#### Scenario: Levantamiento de ejército
- **WHEN** la capital acumula $g_{c_i} = 178 > g_{\text{ret}} = 30$ y el imperio tiene $1 < A_{\max} = 4$ ejércitos
- **THEN** se crea un ejército de $F = 148$ en la capital, $g_{c_i}$ queda en $30$ y $M_i$ no cambia

#### Scenario: Tope de ejércitos
- **WHEN** un imperio ya tiene $A_{\max} = 4$ ejércitos
- **THEN** el excedente permanece como guarnición de la capital y no se crea un quinto ejército

#### Scenario: Tesoro insuficiente
- **WHEN** $f_{\text{rec}} G_i / c_u < 1$
- **THEN** no se recluta ninguna unidad y el tesoro no cambia

### Requirement: Poder militar y fuerza defensiva

El simulador SHALL calcular el poder militar como $M_i = \sum_{a\in\mathcal{A}_i} F_a + \sum_{p\in\mathcal{P}_i} g_p$
(3.12) y la fuerza defensiva de una provincia como $\mathcal{D}_p = g_p + \sum_{a: u_a=p,\ \pi_a=\pi_p} F_a$
(3.12b). Cuando la defensa vence, las bajas SHALL repartirse proporcionalmente entre guarnición y
ejércitos estacionados según (3.12c).

#### Scenario: Cada fuerza cuenta una sola vez
- **WHEN** un imperio tiene ejércitos de $48.11$ y $148$ y guarniciones de $30+28+5+0$
- **THEN** $M_i = 259.11$

#### Scenario: Reparto proporcional de bajas
- **WHEN** una provincia con $g_p = 40$ y un ejército de $F_a = 60$ defiende con éxito sufriendo $b_{\text{gan}} = 25$
- **THEN** la guarnición pierde $10$ y el ejército $15$

#### Scenario: Derrota total de la defensa
- **WHEN** la defensa de una provincia es vencida
- **THEN** la totalidad de $\mathcal{D}_p$ se destruye: $g_p \leftarrow 0$ y todos los ejércitos estacionados propios se eliminan

### Requirement: Generación de variables aleatorias

El simulador SHALL generar los factores $U_a$ y $U_d$ como
$U \sim \operatorname{Triangular}(0.8,\ 1.0,\ 1.2)$ por transformada inversa (3.25) sobre un uniforme
$R$ producido por un LCG de 48 bits con $\mathsf{a}=25214903917$, $\mathsf{c}=11$, $\mathsf{m}=2^{48}$
y $X_0 = s_0$ (3.26). El generador SHALL ser el único origen de aleatoriedad del modelo y consumir
exactamente dos números por combate.

#### Scenario: Transformada inversa rama baja
- **WHEN** $R = 0.2891$
- **THEN** $U = 0.8 + \sqrt{0.08\cdot0.2891} = 0.9521$

#### Scenario: Transformada inversa rama alta
- **WHEN** $R = 0.7314$
- **THEN** $U = 1.2 - \sqrt{0.08\cdot0.2686} = 1.0534$

#### Scenario: Continuidad en el punto medio
- **WHEN** $R = 0.5$
- **THEN** ambas ramas devuelven exactamente $1.0$

#### Scenario: Momentos empíricos
- **WHEN** se generan $10^6$ muestras
- **THEN** la media empírica se aproxima a $1.0$ y la desviación típica a $0.0816$ dentro del error de muestreo

#### Scenario: Reproducibilidad
- **WHEN** dos ejecuciones parten de la misma semilla $s_0$
- **THEN** producen exactamente la misma secuencia de valores $U$

### Requirement: Potencias y criterio de resolución del combate

En el evento E4 el simulador SHALL calcular
$P_a = F_a\,\mu_a\,\mathcal{T}(T_p,\text{ATQ})\,U_a$ (3.14) y
$P_d = \mathcal{D}_p\,\Phi(\phi_p)\,\mathcal{T}(T_p,\text{DEF})\,\Psi(D_p)\,U_d$ (3.15), con
$\Phi(\phi) = 1 + \beta_F\phi$ (3.16) y $\Psi(D) = 1 - \psi D/100$ (3.17). Vence el atacante si y solo
si $P_a > P_d$; el empate exacto lo retiene el defensor (3.18).

#### Scenario: Combate de la traza de escritorio
- **WHEN** $F_a = 102$, $\mu_a = 0.94$, terreno LLANURA, $U_a = 1.0534$, $\mathcal{D}_p = 70$, $\phi_p = 2$, $D_p = 30$, $U_d = 0.9521$
- **THEN** $P_a = 101.00$ y $P_d = 76.24$, y vence el atacante

#### Scenario: Empate exacto
- **WHEN** $P_a = P_d$
- **THEN** el defensor retiene la provincia

#### Scenario: Régimen determinista favorable al atacante
- **WHEN** el cociente determinista cumple $k \le 2/3$
- **THEN** el atacante vence con probabilidad $1$, sea cual sea el sorteo

#### Scenario: Régimen determinista favorable al defensor
- **WHEN** $k \ge 3/2$
- **THEN** el atacante pierde con probabilidad $1$

#### Scenario: Régimen estocástico
- **WHEN** $2/3 < k < 3/2$
- **THEN** el resultado depende del sorteo, y con $k = 1$ la probabilidad de victoria del atacante es $1/2$

### Requirement: Bajas, supervivientes y desgaste moral

El simulador SHALL calcular las bajas del vencedor como
$b_{\text{gan}} = F_{\text{gan}}\,\frac{P_{\text{perd}}}{P_{\text{gan}}}\,K_B$ (3.19), destruir por
completo al bando perdedor (3.20) y actualizar la moral del ejército vencedor con
$\mu_a \leftarrow \max(\mu_{\min},\ \mu_a(1 - \gamma_\mu\,b_{\text{gan}}/F_a))$ (3.29).

#### Scenario: Bajas y moral de la traza de escritorio
- **WHEN** $F_a = 102$, $P_d/P_a = 76.24/101.00$, $K_B = 0.70$ y $\mu_a = 0.94$
- **THEN** $b_{\text{gan}} = 53.89$, el ejército queda con $F_a = 48.11$ y su moral en $0.692$

#### Scenario: El vencedor nunca se aniquila
- **WHEN** se resuelve cualquier combate con victoria
- **THEN** los supervivientes del vencedor son estrictamente mayores que $0.30\,F_{\text{gan}}$

#### Scenario: Victoria holgada apenas desgasta
- **WHEN** el cociente $P_{\text{perd}}/P_{\text{gan}}$ tiende a $0$
- **THEN** las bajas y la pérdida de moral del vencedor tienden a $0$

#### Scenario: Ocupación sin bajas
- **WHEN** el atacante entra en una provincia con $\mathcal{D}_p = 0$
- **THEN** no hay bajas ni pérdida de moral y la provincia se ocupa directamente

### Requirement: Dinámica de la moral por proyección de fuerza

El simulador SHALL calcular el techo de moral como
$\bar\mu_a = \max(\mu_{\min},\ 1 - \lambda_d\,d(u_a, c_{\pi_a}))$ (3.27) con $d$ la distancia
geodésica en el grafo del mapa, y regenerar en E9 con
$\mu_a \leftarrow \min(\bar\mu_a,\ \mu_a + \rho_\mu)$ (3.28).

#### Scenario: Regeneración normal
- **WHEN** un ejército con $\mu_a = 0.692$ está a distancia $d = 2$ de su capital
- **THEN** su techo es $0.88$ y su moral pasa a $\min(0.88,\ 0.792) = 0.792$

#### Scenario: La regeneración también reduce
- **WHEN** un ejército se aleja de su capital y su nuevo techo $\bar\mu_a$ cae por debajo de su moral actual
- **THEN** (3.28) reduce la moral hasta el nuevo techo

#### Scenario: Recálculo tras perder la capital
- **WHEN** un imperio pierde su capital y se reasigna $c_i$
- **THEN** todos los techos de moral de sus ejércitos se recalculan con las nuevas distancias

### Requirement: Movimiento de ejércitos

En el evento E3 el simulador SHALL mover el ejército a la provincia destino y, según su naturaleza:
avanzar sin más si es propia o de un aliado en tránsito; programar CONQUISTA en $\tau+\varepsilon$ si
es neutral; crear una entidad Combate y programar RESOLUCIÓN_COMBATE en $\tau+\varepsilon$ si es
enemiga en guerra. El ejército NO se disuelve en la guarnición al llegar.

#### Scenario: Avance por territorio propio
- **WHEN** un ejército llega a una provincia de su propio imperio
- **THEN** actualiza su ubicación y conserva íntegra su fuerza como entidad separada de la guarnición

#### Scenario: Tránsito por territorio aliado
- **WHEN** un ejército llega a una provincia de un imperio con el que está en ALIANZA
- **THEN** actualiza su ubicación, no se genera combate y la provincia no cambia de dueño

#### Scenario: Ocupación de provincia neutral
- **WHEN** un ejército llega a una provincia neutral
- **THEN** actualiza su ubicación y se programa CONQUISTA en $\tau + \varepsilon$

#### Scenario: Invasión de provincia enemiga
- **WHEN** un ejército llega a una provincia de un imperio con el que está en GUERRA
- **THEN** se crea la entidad Combate con la defensa $\mathcal{D}_p$ del instante de llegada y se programa E4 en $\tau + \varepsilon$

### Requirement: Conquista de provincia

En el evento E5 el simulador SHALL transferir la provincia al conquistador, degradar su fortificación
en un nivel, sumar $10$ puntos de descontento, declarar la guerra al antiguo dueño, reasignar su
capital si procede y programar ELIMINACIÓN si el antiguo dueño queda sin provincias.

#### Scenario: Conquista de la traza de escritorio
- **WHEN** se conquista una provincia con $\phi_p = 2$ y $D_p = 40$
- **THEN** queda con el nuevo propietario, $\phi_p = 1$, $D_p = 50$ y $g_p = 0$

#### Scenario: Conquista de la última provincia de un imperio
- **WHEN** el antiguo dueño queda con $n_i = 0$
- **THEN** se programa ELIMINACIÓN en $\tau + \varepsilon$

#### Scenario: Conquista de provincia neutral
- **WHEN** la provincia conquistada era neutral
- **THEN** no se altera ninguna relación diplomática

### Requirement: Eliminación de imperio

En el evento E6 el simulador SHALL marcar $\alpha_i \leftarrow 0$, decrementar $m$, destruir todos los
ejércitos del imperio y poner todas sus relaciones diplomáticas en PAZ.

#### Scenario: Eliminación completa
- **WHEN** se ejecuta E6 sobre un imperio con ejércitos supervivientes en territorio ajeno
- **THEN** esos ejércitos dejan de existir y ninguno de sus movimientos pendientes llega a resolverse

#### Scenario: Registro del turno de eliminación
- **WHEN** se elimina un imperio en $\tau = 37.819$
- **THEN** se registra su turno de eliminación como $37$
