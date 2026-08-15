## ADDED Requirements

### Requirement: Ejecución interactiva de al menos cinco fases consecutivas

El simulador SHALL ofrecer un modo interactivo por consola que permita ejecutar y observar al menos
cinco turnos consecutivos completos, mostrando en cada uno los eventos procesados en orden de reloj
con su instante $\tau$, su tipo y su efecto sobre el estado.

#### Scenario: Avance turno a turno
- **WHEN** el usuario ordena avanzar un turno
- **THEN** el simulador procesa todos los eventos de ese turno y muestra la traza ordenada por $\tau$ desde INICIO_TURNO hasta FIN_TURNO

#### Scenario: Avance evento a evento
- **WHEN** el usuario ordena avanzar un solo evento
- **THEN** se procesa exclusivamente el mínimo de la LEF y se muestra el estado resultante

#### Scenario: Cinco turnos consecutivos
- **WHEN** el usuario ejecuta cinco avances de turno seguidos desde la inicialización
- **THEN** el reloj llega al turno $6$ y la traza acumulada cubre las cinco ejecuciones completas de las fases E1, diplomacia, E2, cadena condicional y E9

#### Scenario: Inspección de la LEF
- **WHEN** el usuario pide ver la cola de eventos pendientes
- **THEN** se listan con su clave $(\tau, \pi, \varsigma)$, tipo y entidades afectadas

### Requirement: Entrada de variables por el usuario

El modo interactivo SHALL permitir al usuario fijar directamente variables de entrada del modelo
antes de avanzar: tasa impositiva de un imperio, fuerza de un ejército o guarnición, nivel de
fortificación, descontento y población de una provincia, y la semilla del generador.

#### Scenario: Fijar la tasa impositiva
- **WHEN** el usuario fija $\theta_i = 150$ para un imperio y avanza un turno
- **THEN** la recaudación y el $\Delta D_p$ del turno reflejan esa tasa

#### Scenario: Fijar tropas
- **WHEN** el usuario fija la guarnición de una provincia en $70$ unidades
- **THEN** la fuerza defensiva $\mathcal{D}_p$ y el poder militar $M_i$ se actualizan de inmediato

#### Scenario: Valor fuera de dominio
- **WHEN** el usuario intenta fijar $\theta_i = 300$ o $D_p = -5$
- **THEN** la entrada se rechaza con un mensaje que indica el dominio válido, y el estado no cambia

#### Scenario: Reproducibilidad tras fijar la semilla
- **WHEN** el usuario fija la semilla y repite la misma secuencia de entradas y avances
- **THEN** obtiene exactamente la misma traza

### Requirement: Inspección del estado

El modo interactivo SHALL permitir consultar en cualquier instante el estado completo: por imperio
($G_i$, $\theta_i$, $\sigma_i$, $n_i$, $M_i$, $c_i$, $q_i$, $\alpha_i$ y su fila de $\delta$), por
provincia ($\pi_p$, $T_p$, $L_p$, $\phi_p$, $g_p$, $D_p$, $\mathcal{D}_p$) y por ejército ($F_a$,
$u_a$, $\mu_a$, $\bar\mu_a$).

#### Scenario: Vista de imperio
- **WHEN** el usuario consulta un imperio
- **THEN** se muestran sus variables de estado, sus auxiliares $q_i$, $B_i$, $\bar g_i$, $\theta^{\text{eq}}_i$ y $n^{\max}$, y su matriz diplomática

#### Scenario: Vista de provincia
- **WHEN** el usuario consulta una provincia
- **THEN** se muestran sus variables, su renta del turno $I_p$ y si está por encima del umbral fiscal

#### Scenario: Vista de combate previsible
- **WHEN** el usuario pide evaluar un ataque hipotético entre dos provincias adyacentes
- **THEN** se muestran $P_a^{\text{det}}$, $P_d^{\text{det}}$, el cociente $k$ y el régimen (determinista favorable, estocástico o determinista adverso) sin consumir números aleatorios

### Requirement: Modo de demostración de casos borde

El simulador SHALL ofrecer un modo que sitúe la partida directamente en cada uno de los casos borde
del enunciado y del capítulo 5 del documento, y muestre el comportamiento resultante.

#### Scenario: Impuestos al máximo
- **WHEN** se activa el escenario de impuestos al máximo ($\theta_i = 150$ sostenido)
- **THEN** la traza muestra el crecimiento del descontento, el turno exacto en que cada provincia cruza $D^\ast$ y el colapso de la renta a $0$

#### Scenario: Moral en el mínimo
- **WHEN** se activa el escenario de un ejército a $d \ge 10$ de su capital tras combates encadenados
- **THEN** la traza muestra $\mu_a$ saturada en $0.40$ y la caída correspondiente de $P_a$

#### Scenario: Bancarrota
- **WHEN** se activa el escenario de déficit sostenido
- **THEN** la traza muestra la deserción forzosa, el orden en que desertan las unidades, el retorno a solvencia en el turno siguiente y, si $R_i < c_{\text{adm}} n_i$, la convergencia a $M_i \to 0$

#### Scenario: Provincia indefensa
- **WHEN** se activa el escenario de ataque a una provincia con $\mathcal{D}_p = 0$
- **THEN** la traza muestra la ocupación sin bajas y sin entidad Combate

#### Scenario: Pérdida de la capital
- **WHEN** se activa el escenario de conquista de la capital de un imperio
- **THEN** la traza muestra la reasignación de $c_i$ y el recálculo de todos los techos de moral

### Requirement: Modo lote con semillas apareadas

El simulador SHALL ofrecer un modo por lotes que ejecute $n$ réplicas completas variando un parámetro
`[C]`, usando las mismas semillas entre variantes (números aleatorios comunes), y exporte los
resultados a CSV.

#### Scenario: Réplicas apareadas
- **WHEN** se ejecutan dos variantes de $K_B$ con $n = 100$ réplicas y semilla base común
- **THEN** la réplica $r$ de ambas variantes parte de la misma semilla $s_r$

#### Scenario: Exportación CSV
- **WHEN** termina un experimento
- **THEN** se escribe un fichero con una fila por réplica que incluye experimento, parámetro, valor, semilla, ganador, estrategia ganadora, turno final, $\nu$, $\beta$, cuota final y primer turno con $q_\ell \ge 0.5$

#### Scenario: Resumen estadístico
- **WHEN** termina un experimento
- **THEN** se imprime por variante la media y la desviación típica de la duración, la tasa de victoria por estrategia y la intensidad bélica

#### Scenario: Terminación garantizada del lote
- **WHEN** se ejecuta un lote de cualquier tamaño
- **THEN** ninguna réplica excede $t_{\max}$ turnos y el lote termina

### Requirement: Punto de entrada separado y código comentado

El simulador operacional SHALL tener su propio punto de entrada, independiente del clon M1–M6
existente, que siga funcionando sin cambios. Todo método que implemente una ecuación o un evento del
documento SHALL citar en su comentario la referencia correspondiente.

#### Scenario: Arranque del simulador operacional
- **WHEN** se ejecuta el punto de entrada del simulador con el escenario de referencia
- **THEN** arranca en modo interactivo sobre el mapa de 24 provincias sin tocar el clon existente

#### Scenario: El proyecto existente sigue funcionando
- **WHEN** se compila y ejecuta el proyecto completo tras el cambio
- **THEN** el `App` del clon arranca como antes y las 98 pruebas previas siguen pasando

#### Scenario: Trazabilidad en el código
- **WHEN** se inspecciona el método que calcula la renta provincial
- **THEN** su comentario cita la ecuación (3.1) y el evento E1 del documento del Parcial II
