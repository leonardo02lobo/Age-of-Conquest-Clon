## ADDED Requirements

### Requirement: Vector de estado del sistema

El simulador SHALL representar explícitamente el vector de estado $S(\tau)$ de §2.1.5, con las
entidades Imperio ($G_i$, $\theta_i$, $\sigma_i$, $\mathcal{P}_i$, $n_i$, $\mathcal{A}_i$, $M_i$,
$\delta_{ij}$, $c_i$, $\alpha_i$), Provincia ($\pi_p$, $L_p$, $\phi_p$, $g_p$, $D_p$, $T_p$,
$\mathcal{V}_p$, $\chi_p$), Ejército ($F_a$, $u_a$, $v_a$, $\mu_a$, $\omega_a$, $\pi_a$) y el estado
global ($\tau$, $t$, $\mathcal{L}$, $m$, $N$, $Z$, $\nu$, $\beta$, $\varsigma$). Ningún símbolo del
documento del Parcial II puede quedar sin representación.

#### Scenario: Guarnición y ejército son conceptos separados
- **WHEN** un ejército de fuerza $F_a$ llega a una provincia propia con guarnición $g_p$
- **THEN** el ejército NO se disuelve en la guarnición, y la fuerza defensiva de la provincia es $\mathcal{D}_p = g_p + \sum_{a: u_a=p} F_a$ contando cada fuerza exactamente una vez (reparación D8)

#### Scenario: La matriz diplomática es simétrica
- **WHEN** se fija $\delta_{ij} \leftarrow \text{GUERRA}$
- **THEN** $\delta_{ji}$ vale también GUERRA, y $\delta_{ii}$ no está definida

#### Scenario: La capital es propiedad del imperio
- **WHEN** se consulta la capital de un imperio
- **THEN** se obtiene $c_i$, una referencia a provincia reasignable, y no un atributo booleano de la provincia

### Requirement: Tabla de parámetros con procedencia

El simulador SHALL centralizar en un único punto de configuración los parámetros del capítulo 2 con
sus valores por defecto y su procedencia `[D]` documentado / `[M]` modelado / `[C]` calibrable. Los
parámetros marcados `[C]` SHALL ser modificables por experimento sin recompilar.

#### Scenario: Valores por defecto del documento
- **WHEN** se instancian los parámetros sin sobrescribir nada
- **THEN** valen $\iota=0.01$, $c_{\text{adm}}=2.0$, $c_{\text{up}}=0.05$, $c_u=1.5$, $c_\phi=40$, $\theta_{\max}=150$, $\theta_0=50$, $\eta_\theta=0.06$, $\eta_w=2.0$, $\eta_n=0.5$, $n^\ast=8$, $\eta_r=1.5$, $D^\ast=60$, $\psi=0.4$, $\mu_{\min}=0.40$, $\lambda_d=0.06$, $\rho_\mu=0.10$, $\gamma_\mu=0.50$, $K_B=0.70$, $\beta_F=0.15$, $\Phi_{\max}=4$, $F_{\min}=5$, $g_{\text{ref}}=50$, $v_a=1.5$, $g_{\text{ret}}=30$, $A_{\max}=4$, $g_L=0.01$, $L_{\max}=20000$, $\varrho=2$, $\Theta_V=0.60$, $t_{\max}=200$, $\theta_{\text{am}}=0.40$, $\varsigma_h=0.05$, $s_0=20260805$

#### Scenario: Variación de un parámetro calibrable
- **WHEN** un experimento fija $K_B = 0.5$ para una tanda de réplicas
- **THEN** el motor usa ese valor sin afectar al resto de parámetros ni requerir recompilación

### Requirement: Dominios y saturaciones de las variables de estado

El simulador SHALL hacer cumplir los dominios de la tabla §5.1 para las doce variables acotadas, y
saturar en lugar de desbordar.

#### Scenario: Descontento acotado
- **WHEN** $D_p + \Delta D_p$ sale de $[0,100]$
- **THEN** el valor se recorta a $\operatorname{clamp}(\cdot,0,100)$

#### Scenario: Tasa impositiva acotada
- **WHEN** una política fiscal calcula $\theta_i$ fuera de $[0,\theta_{\max}]$
- **THEN** el valor se recorta al intervalo

#### Scenario: Población no negativa y con techo
- **WHEN** el crecimiento supera $L_{\max}$ o el daño de guerra la llevaría bajo cero
- **THEN** $L_p$ se satura en $L_{\max}$ antes del daño y en $0$ por abajo

#### Scenario: Moral acotada
- **WHEN** el desgaste de combate o la regeneración llevarían $\mu_a$ fuera de $[\mu_{\min},1]$
- **THEN** el valor se recorta a $[0.40,\ 1]$

#### Scenario: Fortificación acotada
- **WHEN** se intenta fortificar una provincia con $\phi_p = \Phi_{\max} = 4$
- **THEN** la acción no es elegible y $\phi_p$ no cambia

### Requirement: Funciones por partes del modelo

El simulador SHALL implementar como funciones por partes explícitas las siete discontinuidades de
§5.2: umbral fiscal del descontento (5.1), insolvencia del tesoro (5.2), resultado del combate (5.3),
techo de moral por proyección de fuerza (5.4), saturación poblacional con daño de guerra (5.5),
degradación de fortificación por asedio (5.6) y saturación de la ventana de fases (5.7).

#### Scenario: Salto del umbral fiscal
- **WHEN** el descontento de una provincia pasa de $D_p = 59$ a $D_p = 60$
- **THEN** su renta cae de golpe de $I_p$ a $0$, sin degradación gradual

#### Scenario: Asedio degrada la fortificación
- **WHEN** una provincia con $\phi_p = 2$ es conquistada
- **THEN** pasa a $\phi_p = \max(0, 2-1) = 1$

#### Scenario: Techo de moral saturado
- **WHEN** un ejército opera a distancia geodésica $d \ge 10$ de su capital
- **THEN** su techo de moral es exactamente $\mu_{\min} = 0.40$ y no decrece más con la distancia

### Requirement: Casos degenerados

El simulador SHALL tratar de forma determinista y documentada los nueve casos degenerados de §5.3.

#### Scenario: Provincia sin defensa
- **WHEN** un ejército entra en provincia enemiga con $\mathcal{D}_p = 0$
- **THEN** se ocupa sin bajas resolviendo directamente por E5, sin crear entidad Combate

#### Scenario: Ejército por debajo del mínimo viable
- **WHEN** tras un combate resulta $F_a < F_{\min} = 5$
- **THEN** el ejército se disuelve; su fuerza residual se incorpora a la guarnición si está en provincia propia y se pierde si está en tránsito

#### Scenario: Pérdida de la capital
- **WHEN** un imperio pierde la provincia que era su capital y conserva otras
- **THEN** $c_i \leftarrow \arg\max_{p\in\mathcal{P}_i} L_p$ con desempate por menor id, y todos los techos de moral se recalculan con las nuevas distancias

#### Scenario: Imperio con provincias pero sin ejércitos
- **WHEN** un imperio pierde todos sus ejércitos pero conserva al menos una provincia
- **THEN** NO es eliminado: se defiende con guarniciones y levanta un ejército nuevo cuando $g_{c_i} > g_{\text{ret}}$

#### Scenario: Empate en la cuota del líder
- **WHEN** dos o más imperios activos empatan en el máximo de $q_i$
- **THEN** se elige como líder $\ell$ el de menor identificador

### Requirement: Formato y validación del escenario de referencia

El simulador SHALL cargar el escenario desde un fichero JSON que declare, por provincia, su id,
nombre, terreno $T_p \in \{\text{LLA},\text{BOS},\text{MON},\text{COS}\}$, población inicial
$L^0_p \in [1000,10000]$ y adyacencias; y por imperio, su id, nombre, estrategia $\sigma_i$, capital
y provincias iniciales. La carga SHALL rechazar con un error descriptivo todo escenario inválido.

#### Scenario: Escenario del Anexo C
- **WHEN** se carga `scenarios/referencia24.json`
- **THEN** contiene $N = 24$ provincias con grafo conexo y grado medio $\approx 3.5$, terrenos repartidos 10 LLANURA / 6 BOSQUE / 4 MONTAÑA / 4 COSTA, 4 imperios (uno por estrategia) con 3 provincias cada uno y 12 provincias neutrales

#### Scenario: Simetría de las adyacencias
- **WHEN** el fichero declara la adyacencia $p \to q$ en una sola dirección
- **THEN** el cargador la simetriza y $q \in \mathcal{V}_p$ junto con $p \in \mathcal{V}_q$

#### Scenario: Rechazo de escenario desconectado
- **WHEN** el grafo del mapa tiene más de una componente conexa
- **THEN** la carga falla con un mensaje que identifica las provincias inalcanzables

#### Scenario: Rechazo de terreno desconocido
- **WHEN** una provincia declara un terreno fuera de los cuatro tipos definidos
- **THEN** la carga falla indicando la provincia y el valor inválido

#### Scenario: Estado inicial conforme al Anexo C
- **WHEN** se inicializa una partida sobre el escenario de referencia
- **THEN** cada imperio tiene $G^0 = 200$, un ejército de $F^0 = 100$ en su capital, todas las provincias $D^0 = 20$, $\phi = 1$ solo en las capitales y $\phi = 0$ en el resto, y $\delta_{ij} = \text{PAZ}$ para todo par
