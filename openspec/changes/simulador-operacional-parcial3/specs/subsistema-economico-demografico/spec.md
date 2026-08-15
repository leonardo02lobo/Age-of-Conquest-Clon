## ADDED Requirements

### Requirement: Renta provincial con umbral fiscal

El simulador SHALL calcular la renta de cada provincia como
$I_p(t) = \iota L_p \frac{\theta_i}{100}(1+\beta_\phi\phi_p)\,\mathbb{1}[D_p < D^\ast]$ (ec. 3.1),
donde $i = \pi_p$ es el imperio propietario. Una provincia con $D_p \ge D^\ast$ NO tributa.

#### Scenario: Provincia leal y fortificada
- **WHEN** una provincia tiene $L_p = 5000$, $\theta_i = 125$, $\phi_p = 1$ y $D_p = 35$
- **THEN** su renta es $I_p = 0.01\cdot5000\cdot1.25\cdot1.05 = 65.625$ oro/turno

#### Scenario: Provincia por encima del umbral fiscal
- **WHEN** una provincia tiene $D_p = 60$ o más
- **THEN** su renta es exactamente $0$, con independencia de su población, tasa y fortificación

#### Scenario: Provincia despoblada
- **WHEN** una provincia tiene $L_p = 0$
- **THEN** su renta es $0$ y solo conserva valor militar

### Requirement: Ecuación de estado del tesoro

En el evento E1 el simulador SHALL actualizar el tesoro de cada imperio activo como
$G_i(t+1) = G_i(t) + R_i(t) - C_i(t)$, con $R_i = \sum_{p\in\mathcal{P}_i} I_p$ (3.2) y
$C_i = c_{\text{adm}} n_i + c_{\text{up}} M_i$ (3.3), aplicando después la regla de insolvencia. El
gasto discrecional $X_i = c_u u_i + c_\phi z_i$ (3.5) se descuenta en E2, no en E1.

#### Scenario: Balance de un turno
- **WHEN** un imperio con $n_i = 3$, $M_i = 155$ y $G_i = 150$ recauda $R_i = 128.125$
- **THEN** su coste es $C_i = 2\cdot3 + 0.05\cdot155 = 13.75$ y el tesoro queda en $264.375$

#### Scenario: La recaudación usa la población de inicio de turno
- **WHEN** se ejecuta E1 en el turno $t$
- **THEN** se usa $L_p$ tal como quedó al cerrar el turno anterior, antes del crecimiento de E9 del turno actual

#### Scenario: El mantenimiento cubre ejércitos y guarniciones
- **WHEN** se calcula $C_i$
- **THEN** $M_i$ incluye tanto la fuerza de los ejércitos como la de las guarniciones, cada una contada una sola vez

### Requirement: Regla de insolvencia con deserción determinista

Cuando tras aplicar (3.4) resulte $G_i < 0$, el simulador SHALL fijar $G_i \leftarrow 0$ y forzar la
deserción de $\Delta M_i = \lceil |G_i| / c_{\text{up}} \rceil$ unidades de fuerza (ec. 5.2). El orden
de deserción SHALL ser: primero los ejércitos más alejados de la capital, a igual distancia los de
menor moral, a igual moral los de menor identificador; las guarniciones desertan en último lugar.

#### Scenario: Deserción tras un déficit
- **WHEN** un imperio cierra E1 con $G_i = -3$ y $c_{\text{up}} = 0.05$
- **THEN** desertan $\lceil 3/0.05 \rceil = 60$ unidades de fuerza y el tesoro queda en $0$

#### Scenario: La regla no oscila
- **WHEN** se aplica la deserción en el turno $t$ y el imperio conserva sus provincias
- **THEN** el coste del turno $t+1$ se reduce en al menos el déficit incurrido y el imperio vuelve a ser solvente (Proposición 3)

#### Scenario: Caso terminal por administración
- **WHEN** la renta de un imperio cumple $R_i < c_{\text{adm}} n_i$ de forma sostenida
- **THEN** su fuerza militar converge a $M_i \to 0$ y sobrevive sin ejército hasta ser conquistado, sin que el motor entre en bucle infinito

#### Scenario: Deserción cuando solo quedan guarniciones
- **WHEN** el imperio no tiene ejércitos y debe desertar fuerza
- **THEN** la reducción se aplica sobre las guarniciones, sin permitir valores negativos de $g_p$

### Requirement: Dinámica del descontento provincial

En el evento E9 el simulador SHALL aplicar a todas las provincias de cada imperio activo el mismo
incremento $\Delta D_p = \eta_\theta(\theta_i - \theta_0) + \eta_w \mathbb{1}[\text{guerra}] + \eta_n\max(0, n_i - n^\ast) - \eta_r$
(3.6), y actualizar $D_p \leftarrow \operatorname{clamp}(D_p + \Delta D_p, 0, 100)$ (3.7).

#### Scenario: Imperio agresivo en guerra
- **WHEN** un imperio tiene $\theta_i = 125$, está en guerra y $n_i = 4 \le n^\ast = 8$
- **THEN** $\Delta D_p = 0.06(125-50) + 2 + 0 - 1.5 = +5.0$ puntos/turno en todas sus provincias

#### Scenario: Imperio pequeño en paz con tasa neutra
- **WHEN** un imperio tiene $\theta_i = 50$, está en paz y $n_i \le n^\ast$
- **THEN** $\Delta D_p = -1.5$ puntos/turno y el descontento decrece hasta saturar en $0$

#### Scenario: Penalización por sobreextensión
- **WHEN** un imperio controla $n_i = 12$ provincias con $n^\ast = 8$
- **THEN** el término de sobreextensión aporta $0.5\cdot4 = +2.0$ puntos/turno adicionales

#### Scenario: Ocupación militar tras una conquista
- **WHEN** una provincia cambia de dueño en E5
- **THEN** su descontento sube de golpe $D_p \leftarrow \min(100, D_p + 10)$

### Requirement: Tasa de equilibrio y tamaño máximo sostenible

El simulador SHALL exponer como funciones consultables la tasa impositiva de equilibrio
$\theta^{\text{eq}}_i = \theta_0 + \frac{\eta_r - \eta_w\mathbb{1}[\text{guerra}] - \eta_n\max(0,n_i-n^\ast)}{\eta_\theta}$
(3.8) y el tamaño máximo sostenible $n^{\max}$ (3.9), porque la estrategia ECONÓMICA los usa como
política fiscal.

#### Scenario: Equilibrio en paz
- **WHEN** un imperio pequeño está en paz
- **THEN** $\theta^{\text{eq}} = 50 + 1.5/0.06 = 75\%$

#### Scenario: Equilibrio en guerra
- **WHEN** el mismo imperio entra en guerra
- **THEN** $\theta^{\text{eq}} = 50 + (1.5-2)/0.06 \approx 41.7\%$

#### Scenario: Cota de tamaño
- **WHEN** se evalúa $n^{\max}$ con los parámetros por defecto
- **THEN** vale $17$ provincias en paz y $13$ en guerra, quedando por debajo de las $15$ que exige la cuota de victoria en guerra permanente

### Requirement: Dinámica poblacional con daño de guerra

En el evento E9 el simulador SHALL actualizar la población como
$L_p(t+1) = \min(L_{\max},\ L_p(1+g_L)) - \varrho\,\beta_p(t)$, recortando por abajo en $0$ (3.10 y
5.5), donde $\beta_p(t)$ son las bajas totales causadas en esa provincia durante el turno. El
acumulador $\beta_p$ SHALL reiniciarse a $0$ tras consumirse.

#### Scenario: Crecimiento normal
- **WHEN** una provincia tiene $L_p = 4000$ y no ha sufrido combates
- **THEN** pasa a $L_p = 4040$

#### Scenario: Provincia disputada
- **WHEN** una provincia con $L_p = 4000$ registra $\beta_p = 123.89$ bajas en el turno
- **THEN** pasa a $L_p = 4040 - 2\cdot123.89 = 3792$ (truncado a entero de habitantes)

#### Scenario: Saturación en el techo
- **WHEN** una provincia alcanza $L_p = L_{\max} = 20000$
- **THEN** el crecimiento se detiene y solo el daño de guerra puede reducirla

#### Scenario: Daño superior a la población
- **WHEN** las bajas de un turno superan la población de la provincia
- **THEN** $L_p$ queda en $0$ y no toma valores negativos
