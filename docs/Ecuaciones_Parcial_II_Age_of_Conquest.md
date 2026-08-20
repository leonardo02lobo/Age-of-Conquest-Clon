# Ecuaciones del Modelo Formal — Age of Conquest (Parcial II)

Compilación de todas las ecuaciones numeradas del documento *"Modelo formal de AGE OF CONQUEST"*, organizadas por sección. La numeración (3.x, 4.x, 5.x) coincide con la del documento original para facilitar la referencia cruzada.

---

## 3.1 Ecuación de estado general (eventos discretos)

$$
S(\tau^+) = \Psi_e(S(\tau^-), \Theta, \mathbf{U}), \qquad S(\tau) = S(\tau^+) \; \forall \tau \in [\tau_k, \tau_{k+1}) \tag{3.0}
$$

Donde $e$ es el evento extraído de la LEF en el instante $\tau_k$, $\Theta$ el vector de parámetros fijos, y $\mathbf{U}$ las variables aleatorias que consume el evento. Expresa que el estado permanece constante entre eventos y solo "salta" cuando se procesa uno.

---

## 3.2 Subsistema económico y demográfico

**Renta de una provincia** (define $F_1$, `calcularIngreso`):

$$
I_p(t) = \begin{cases} \iota \, L_p(t) \, \dfrac{\theta_i}{100} \, (1 + \beta_\phi \phi_p) \, , & D_p(t) < D^* \\ 0, & D_p(t) \ge D^* \end{cases} \qquad i = \pi_p \tag{3.1}
$$

**Recaudación total del imperio:**

$$
R_i(t) = \sum_{p \in P_i(t)} I_p(t) \tag{3.2}
$$

**Coste de mantenimiento del imperio:**

$$
C_i(t) = c_{\text{adm}} \, n_i(t) + c_{\text{up}} \, M_i(t) \tag{3.3}
$$

**Ecuación de estado del tesoro** (evento E1):

$$
G_i(t+1) = \max\big(0,\; G_i(t) + R_i(t) - C_i(t) - X_i(t)\big) \tag{3.4}
$$

**Gasto discrecional** (ejecutado en E2):

$$
X_i(t) = c_u \, u_i(t) + c_\phi \, z_i(t) \tag{3.5}
$$

con $u_i$ unidades reclutadas y $z_i$ niveles de fortificación adquiridos ese turno.

**Variación del descontento provincial** (evento E9):

$$
\Delta D_p(t) = \eta_\theta(\theta_i(t) - \theta_0) + \eta_w \, \mathbb{1}[\exists j : \delta_{ij} = \text{GUERRA}] + \eta_n \max(0, n_i(t) - n^*) - \eta_r \tag{3.6}
$$

$$
D_p(t+1) = \text{clamp}\big(D_p(t) + \Delta D_p(t),\; 0,\; 100\big) \tag{3.7}
$$

**Tasa impositiva de equilibrio** (imponiendo $\Delta D_p = 0$):

$$
\theta_i^{\text{eq}}(t) = \theta_0 + \frac{\eta_r - \eta_w \, \mathbb{1}[\text{guerra}] - \eta_n \max(0, n_i - n^*)}{\eta_\theta} \tag{3.8}
$$

**Tamaño máximo sostenible del imperio** (imponiendo $\theta_i^{\text{eq}} \ge 0$):

$$
n^{\max} = n^* + \frac{\eta_r - \eta_w \, \mathbb{1}[\text{guerra}] + \eta_\theta \, \theta_0}{\eta_n} \tag{3.9}
$$

Resultado numérico: $n^{\max}_{\text{paz}} = 17$ provincias; $n^{\max}_{\text{guerra}} = 13$ provincias.

**Dinámica de la población** (evento E9):

$$
L_p(t+1) = \min\big(L_{\max},\; L_p(t)(1 + g_L)\big) - \varrho \, \beta_p(t) \tag{3.10}
$$

donde $\beta_p(t)$ son las bajas totales causadas en combates en la provincia $p$ durante el turno $t$.

---

## 3.3 Subsistema militar y de movimiento

**Reclutamiento** (define $F_2$, `DecidirReclutamiento`):

$$
u_i(t) = \left\lfloor \frac{f^\sigma_{\text{rec}} \, G_i(t)}{c_u} \right\rfloor \tag{3.11}
$$

**Poder militar total** (define $F_{10}$):

$$
M_i(t) = \sum_{a \in A_i} F_a(t) + \sum_{p \in P_i} g_p(t) \tag{3.12}
$$

**Fuerza defensiva de una provincia:**

$$
\mathcal{D}_p(\tau) = g_p(\tau) + \sum_{a \,:\, u_a = p,\; \pi_a \ne \pi_p} F_a(\tau) \tag{3.12b}
$$

**Reparto de bajas del bando defensor vencedor:**

$$
\Delta g_p = b_{\text{gan}} \frac{g_p}{\mathcal{D}_p}, \qquad \Delta F_a = b_{\text{gan}} \frac{F_a}{\mathcal{D}_p} \tag{3.12c}
$$

**Coste de movimiento** (define $F_4$, `CosteMovimiento`):

$$
c(a, p \to q) = \frac{w(T_q)}{v_a} \quad [\text{turnos}] \tag{3.13}
$$

---

## 3.4 Modelo de resolución de combate

**Potencia de combate del atacante:**

$$
P_a = F_a \, \mu_a \, T(T_p, \text{ATQ}) \, U_a \tag{3.14}
$$

**Potencia de combate del defensor:**

$$
P_d = \mathcal{D}_p \, \Phi(\phi_p) \, T(T_p, \text{DEF}) \, \Psi(D_p) \, U_d \tag{3.15}
$$

con $U_a, U_d$ i.i.d. según §3.5.

**Función de fortificación** (define $F_8$):

$$
\Phi(\phi) = 1 + \beta_F \, \phi, \qquad \phi \in \{0, 1, \dots, \Phi_{\max}\}, \qquad \Phi \in [1.00, 1.60] \tag{3.16}
$$

**Función de respaldo civil:**

$$
\Psi(D_p) = 1 - \psi \frac{D_p}{100}, \qquad \Psi \in [1-\psi, 1] = [0.60, 1.00] \tag{3.17}
$$

**Criterio de victoria en combate:**

$$
\text{vence el atacante} \iff P_a > P_d \quad \text{(el empate lo retiene el defensor)} \tag{3.18}
$$

**Cociente de potencias deterministas** (con $U_a = U_d = \mathbb{E}[U] = 1$):

$$
k = \frac{\mathcal{D}_p \, \Phi(\phi_p) \, T(T_p, \text{DEF}) \, \Psi(D_p)}{F_a \, \mu_a \, T(T_p, \text{ATQ})}
$$

**Proposición 1 (regímenes de combate):**

$$
\Pr[\text{gana el atacante}] \begin{cases} = 1, & k \le 2/3 \quad \text{(superioridad} \ge 1.5\times\text{)} \\ \in (0,1), & 2/3 < k < 3/2 \\ = 0, & k \ge 3/2 \quad \text{(inferioridad} \ge 1.5\times\text{)} \end{cases}
$$

**Bajas del bando vencedor** (fórmula base, Parcial I conservada):

$$
b_{\text{gan}} = F_{\text{gan}} \, \frac{P_{\text{perd}}}{P_{\text{gan}}} \, K_B \tag{3.19}
$$

**Bajas del bando perdedor** (aniquilación total):

$$
b_{\text{perd}} = F_{\text{perd}} \tag{3.20}
$$

**Supervivientes del bando vencedor:**

$$
F_{\text{gan}}(\tau^+) = F_{\text{gan}}(\tau^-)\left(1 - K_B \, \frac{P_{\text{perd}}}{P_{\text{gan}}}\right) \tag{3.21}
$$

**Reescritura en forma de Lanchester lineal** (con $P_a = F_a m_a$, $P_d = \mathcal{D}_p m_d$):

$$
b_{\text{gan}} = K_B \, F_a \, \frac{m_d}{m_a} = K_B \, \mathcal{D}_p \, \frac{m_d}{m_a} \tag{3.22}
$$

**Proposición 2 (equivalencia con la ley lineal de Lanchester).** Para
$\dfrac{dA}{dt} = -\beta AD$, $\dfrac{dD}{dt} = -\alpha AD$, si el atacante vence:

$$
A_f = A_0 - \frac{\beta}{\alpha} D_0
$$

Corolario: coincide exactamente con (3.21) salvo el factor $K_B$.

---

## 3.5 Generación de variables aleatorias

**Distribución adoptada** (define $F_7$, `Aleatorio()`):

$$
U \sim \text{Triangular}(a_U, c_U, b_U) = \text{Triangular}(0.8,\, 1.0,\, 1.2)
$$

**Función de densidad:**

$$
f_U(x) = \begin{cases} \dfrac{2(x-a_U)}{(b_U-a_U)(c_U-a_U)} = 25(x-0.8), & 0.8 \le x \le 1.0 \\[6pt] \dfrac{2(b_U-x)}{(b_U-a_U)(b_U-c_U)} = 25(1.2-x), & 1.0 < x \le 1.2 \\[6pt] 0, & \text{en otro caso} \end{cases} \tag{3.23}
$$

**Función de distribución acumulada:**

$$
F_U(x) = \begin{cases} 0, & x < 0.8 \\[4pt] \dfrac{(x-0.8)^2}{0.08}, & 0.8 \le x \le 1.0 \\[6pt] 1 - \dfrac{(1.2-x)^2}{0.08}, & 1.0 < x \le 1.2 \\[4pt] 1, & x > 1.2 \end{cases} \tag{3.24}
$$

**Momentos:**

$$
\mathbb{E}[U] = \frac{a_U + b_U + c_U}{3} = 1.0, \qquad \text{Var}[U] = \frac{a_U^2+b_U^2+c_U^2-a_Ub_U-a_Uc_U-b_Uc_U}{18} = 0.00\overline{6}
$$

$$
\sigma_U = 0.0816, \qquad CV = 8.16\%
$$

**Generación por transformada inversa** (con $R \sim \text{Uniforme}(0,1)$):

$$
U = F_U^{-1}(R) = \begin{cases} 0.8 + \sqrt{0.08\,R}, & R < 0.5 \\[4pt] 1.2 - \sqrt{0.08\,(1-R)}, & R \ge 0.5 \end{cases} \tag{3.25}
$$

**Generador congruencial lineal (LCG) de 48 bits:**

$$
X_{k+1} = (a X_k + c) \bmod m, \qquad R_k = \frac{X_{k-1}}{m} \tag{3.26}
$$

$$
a = 25\,214\,903\,917, \quad c = 11, \quad m = 2^{48}, \quad X_0 = s_0 = 20\,260\,805
$$

Periodo completo: $m = 2^{48} \approx 2.81 \times 10^{14}$ (satisface las condiciones de Hull–Dobell).

---

## 3.6 Dinámica de la moral

**Techo de moral por proyección de fuerza** (define $F_5$):

$$
\bar{\mu}_a(\tau) = \max\big(\mu_{\min},\; 1 - \lambda_d \, d(u_a(\tau), c_{\pi_a}(\tau))\big) \tag{3.27}
$$

con $d(\cdot,\cdot)$ la distancia geodésica en el grafo del mapa.

**Regeneración de moral** (evento E9):

$$
\mu_a(t+1) = \min\big(\bar{\mu}_a(t),\; \mu_a(t) + \rho_\mu\big) \tag{3.28}
$$

**Desgaste de combate** (evento E4):

$$
\mu_a(\tau^+) = \max\left(\mu_{\min},\; \mu_a(\tau^-)\left(1 - \gamma_\mu \, \frac{b_{\text{gan}}}{F_a(\tau^-)}\right)\right) \tag{3.29}
$$

Sustituyendo (3.19), el desgaste resulta $\gamma_\mu K_B \, P_{\text{perd}}/P_{\text{gan}}$.

---

## 3.7 Subsistema diplomático

**Legalidad de un movimiento** (repara defecto D1):

$$
\text{Legal}(i, q) \iff \pi_q = i \;\lor\; \pi_q = \varnothing \;\lor\; \delta_{i,\pi_q} = \text{GUERRA} \;\lor\; (\delta_{i,\pi_q} = \text{ALIANZA} \land \text{tránsito})
$$

**Declaración de guerra** (evento E7):

$$
E7(i \to j) \iff \delta_{ij} = \text{PAZ} \,\land\, \Big[\underbrace{q_\ell \ge \theta_{\text{am}} \land j = \ell \land i \ne \ell}_{\text{coalición anti-líder (B3)}} \;\lor\; \underbrace{\big(M_i / \max(M_j, 1) \ge \gamma_\sigma(\sigma_i) \land \text{Adj}(i,j)\big)}_{\text{oportunismo}}\Big] \tag{3.30}
$$

donde $\text{Adj}(i,j) \iff \exists\, p \in P_i,\, q \in P_j : q \in V_p$.

**Formación de alianza** (evento E8):

$$
E8^+(i,j) \iff \delta_{ij} = \text{PAZ} \land \delta_{i\ell} = \delta_{j\ell} = \text{GUERRA} \land i,j \ne \ell \land q_\ell \ge \theta_{\text{am}} \tag{3.31}
$$

**Ruptura de alianza con histéresis:**

$$
E8^-(i,j) \iff \delta_{ij} = \text{ALIANZA} \land q_\ell < \theta_{\text{am}} - \varsigma_h \tag{3.32}
$$

---

## 3.8 Cierre de los bucles causales

**Longitud de frontera y dispersión de guarniciones** (bucle B1a):

$$
B_i = |\partial P_i|, \qquad \bar{g}_i = \frac{1}{B_i} \sum_{p \in \partial P_i} g_p \tag{3.33}
$$

**Vulnerabilidad defensiva del imperio:**

$$
\text{Vul}_i = 1 - \frac{\bar{g}_i}{\bar{g}_i + g_{\text{ref}}} \in (0,1) \tag{3.34}
$$

---

## 4.2 El reloj de simulación

**Función de fase:**

$$
\varphi : \text{TipoEvento} \longrightarrow [0,1) \tag{4.1}
$$

**Relación de orden total de la LEF** (repara defecto D4):

$$
e_1 < e_2 \iff (\tau_1, \pi_1, \varsigma_1) <_{\text{lex}} (\tau_2, \pi_2, \varsigma_2) \tag{4.2}
$$

donde $\varsigma$ es un contador global monótono de inserción (desempate final, garantiza orden total estricto).

**Anchura de la ventana de movimiento:**

$$
\Delta = \varphi_{\text{FIN}} - \varphi_{\text{MOV}} - 4\varepsilon = 0.90 - 0.15 - 0.004 = 0.746 \tag{4.3}
$$

**Función de llegada** (repara defecto D3):

$$
\tau_{\text{lleg}}(t, c) = \left(t + \left\lfloor \frac{c}{\Delta} \right\rfloor\right) + \varphi_{\text{MOV}} + (c \bmod \Delta) \tag{4.4}
$$

**Teorema 1 (validez de la ventana).** Para todo $c \ge 0$, la fase de $\tau_{\text{lleg}}$ pertenece a $[\varphi_{\text{MOV}}, \varphi_{\text{MOV}} + \Delta)$, y toda la cadena de eventos derivada concluye estrictamente antes de $\varphi_{\text{FIN}}$.

**Teorema 2 (causalidad).** Ningún evento se programa en el pasado: para todo evento hijo $h$ generado por un evento padre $e$, se cumple $\tau_h \ge \tau_e$.

---

## 5.2 Funciones por partes

**(a) Umbral fiscal del descontento** (forma equivalente a 3.1):

$$
I_p(t) = \begin{cases} \iota L_p \dfrac{\theta_i}{100}(1+\beta_\phi \phi_p), & D_p < D^* = 60 \\ 0, & D_p \ge D^* \end{cases} \tag{5.1}
$$

**Tiempo de recuperación fiscal** (desde $D_p = 100$ hasta volver a tributar):

$$
t_{\text{rec}} = \left\lceil \frac{100 - D^*}{|\Delta D_p|} \right\rceil = \left\lceil \frac{40}{\eta_\theta(\theta_0 - \theta_i) + \eta_r - \eta_w \mathbb{1}[\text{guerra}]} \right\rceil
$$

**(b) Insolvencia del tesoro** (deserción forzosa):

$$
(G_i, M_i) \mapsto \begin{cases} (G_i, M_i), & G_i \ge 0 \\ (0,\; M_i - \Delta M_i), & G_i < 0 \end{cases}, \qquad \Delta M_i = \left\lceil \frac{|G_i|}{c_{\text{up}} \, \Delta t} \right\rceil,\; \Delta t = 1 \text{ turno} \tag{5.2}
$$

**Proposición 3 (la regla de insolvencia no oscila).** Tras aplicar (5.2), el gasto del turno siguiente se reduce en $c_{\text{up}}\Delta M_i \ge |G_i|$, mientras $R_i$ no disminuye, garantizando el retorno a solvencia en el turno siguiente.

**(c) Resultado del combate como función escalón:**

$$
\Pr[\text{gana el atacante}] = \begin{cases} 1, & k \le 2/3 \\ h(k) \in (0,1), & 2/3 < k < 3/2 \\ 0, & k \ge 3/2 \end{cases} \tag{5.3}
$$

**(d) Techo de moral por proyección de fuerza** (forma equivalente a 3.27):

$$
\bar{\mu}_a = \begin{cases} 1 - \lambda_d\, d(u_a, c_i), & d < d_{\max} = \dfrac{1-\mu_{\min}}{\lambda_d} = 10 \\[6pt] \mu_{\min} = 0.40, & d \ge 10 \end{cases} \tag{5.4}
$$

**(e) Saturación poblacional y daño de guerra:**

$$
L_p(t+1) = \begin{cases} L_p(1+g_L) - \varrho\,\beta_p, & L_p(1+g_L) < L_{\max} \\ L_{\max} - \varrho\,\beta_p, & L_p(1+g_L) \ge L_{\max} \end{cases}, \quad \text{siempre } L_p \to \max(0, L_p) \tag{5.5}
$$

**(f) Asedio: degradación de la fortificación** (en cada E5 Conquista):

$$
\phi_p(\tau^+) = \max\big(0,\, \phi_p(\tau^-) - 1\big) \tag{5.6}
$$

**(g) Saturación de la ventana de fases:**

$$
\varphi_{\text{lleg}}(c) = \begin{cases} \varphi_{\text{MOV}} + c, & c < \Delta \\ \varphi_{\text{MOV}} + (c \bmod \Delta) \text{ en el turno } t + \lfloor c/\Delta \rfloor, & c \ge \Delta \end{cases} \tag{5.7}
$$

---

## Teorema 3 (terminación)

La simulación termina en un número finito de eventos, acotado por:

$$
t_{\max}\big(3 + |I| + 4\,|I|\,A_{\max}\big)
$$

---

## Notas de lectura

- Todos los modificadores de combate ($\mu, T, \Phi, \Psi, U$) son **adimensionales por diseño** — es lo que permite que (3.14)–(3.15) comparen "potencias" sin problema de unidades (verificado en §3.9).
- $\theta$ se mide en % y se divide entre 100 solo en (3.1) para adimensionalizarla; en (3.6) y (3.8) se opera directamente en unidades de %.
- El parámetro $K_B$ (coeficiente de bajas) es el que gobierna la fuerza del bucle balanceador B2 (atrición); $\eta_n, n^*$ gobiernan B1 (sobreextensión); $\theta_{\text{am}}$ gobierna B3 (coalición anti-líder).
