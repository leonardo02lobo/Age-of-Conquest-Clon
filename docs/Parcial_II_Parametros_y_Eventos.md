# Parámetros y Pseudocódigo de Eventos — Age of Conquest (Parcial II)

Complemento de `Ecuaciones_Parcial_II_Age_of_Conquest.md`. Este archivo contiene los **valores numéricos exactos** de los parámetros y el **pseudocódigo de los 10 eventos**, necesarios para verificar no solo si las fórmulas están bien, sino si el motor las ejecuta en el orden y con los valores correctos.

---

## 1. Tabla completa de parámetros (§2.4)

### 1.1 Escenario e inicialización
| Símbolo | Valor | Unidad |
|---|---|---|
| N (provincias del mapa) | 24 | provincias |
| \|I\| (imperios iniciales) | 4 | imperios |
| G⁰ (oro inicial) | 200 | oro |
| F⁰ (fuerza inicial) | 100 | unidades de fuerza |
| L⁰p (población inicial) | [1000, 10000] | habitantes |
| D⁰p (descontento inicial) | 20 | puntos |
| φ⁰p (fortificación inicial) | 0; capital 1 | niveles |
| μ⁰a (moral inicial) | 1.0 | adimensional |
| s₀ (semilla) | 20260805 | — |

### 1.2 Subsistema económico
| Símbolo | Valor | Unidad |
|---|---|---|
| ι (renta unitaria a tasa 100%) | 0.01 | oro/(habitante·turno) |
| βφ (bonificación por fortificación) | 0.05 | 1/nivel |
| c_adm (coste administrativo) | 2.0 | oro/(provincia·turno) |
| c_up (mantenimiento militar unitario) | 0.05 | oro/(unidad·turno) |
| c_u (coste de reclutamiento) | 1.5 | oro/unidad |
| c_φ (coste fortificación) | 40 | oro/nivel |
| θmax (tasa impositiva máxima) | 150 | % |
| θ₀ (tasa fiscalmente neutra) | 50 | % |

### 1.3 Descontento
| Símbolo | Valor | Unidad |
|---|---|---|
| ηθ (sensibilidad a presión fiscal) | 0.06 | pts/(turno·%) |
| ηw (descontento por guerra) | 2.0 | pts/turno |
| ηn (descontento por sobreextensión) | 0.5 | pts/(turno·provincia) |
| n* (umbral administrativo) | 8 | provincias |
| ηr (recuperación base) | 1.5 | pts/turno |
| D* (umbral fiscal de descontento) | 60 | puntos |
| ψ (penalización defensiva máx.) | 0.4 | adimensional |

### 1.4 Moral
| Símbolo | Valor | Unidad |
|---|---|---|
| μmin (moral mínima) | 0.40 | adimensional |
| λd (decaimiento por distancia) | 0.06 | 1/provincia |
| ρμ (regeneración por turno) | 0.10 | 1/turno |
| γμ (desgaste por bajas) | 0.50 | adimensional |

### 1.5 Combate
| Símbolo | Valor | Unidad |
|---|---|---|
| KB (coeficiente de bajas) | 0.70 | adimensional |
| βF (bonificación defensiva por fortif.) | 0.15 | 1/nivel |
| Φmax (nivel máximo de fortificación) | 4 | niveles |
| Fmin (fuerza mínima viable) | 5 | unidades de fuerza |
| gref (guarnición de referencia) | 50 | unidades de fuerza |
| (aU, cU, bU) (factor aleatorio) | (0.8, 1.0, 1.2) | adimensional |

### 1.6 Matriz de terreno
| Terreno | T(ATQ) | T(DEF) | w (coste cruce) |
|---|---|---|---|
| LLANURA | 1.00 | 1.00 | 1.0 |
| BOSQUE | 0.90 | 1.15 | 1.4 |
| MONTAÑA | 0.80 | 1.30 | 2.0 |
| COSTA | 0.95 | 1.10 | 1.2 |

### 1.7 Reloj y Lista de Eventos Futuros
| Símbolo | Valor | Descripción |
|---|---|---|
| φINI | 0.00 | Fase INICIO_TURNO |
| φDIP | 0.05 | Fase evaluación diplomática |
| φPLA | 0.10 | Fase PLANIFICACIÓN |
| φMOV | 0.15 | Inicio ventana de movimiento |
| φFIN | 0.90 | Fase FIN_TURNO |
| φJUE | 0.95 | Fase FIN_JUEGO |
| ε (paso infinitesimal) | 10⁻³ | fracción de turno |
| Δ (anchura ventana movimiento) | 0.746 | fracción de turno |

### 1.8 Movimiento, población y terminación
| Símbolo | Valor | Unidad |
|---|---|---|
| va (puntos de movimiento base) | 1.5 | provincias/turno |
| gret (guarnición de reserva capital) | 30 | unidades de fuerza |
| Amax (ejércitos simultáneos) | 4 | ejércitos |
| gL (tasa crecimiento poblacional) | 0.01 | 1/turno |
| Lmax (población máxima por provincia) | 20000 | habitantes |
| ϱ (habitantes destruidos por baja) | 2 | habitantes/unidad |
| ΘV (cuota de victoria) | 0.60 | adimensional |
| tmax (límite de turnos) | 200 | turnos |
| θam (cuota que dispara coalición) | 0.40 | adimensional |
| ςh (histéresis disolución alianzas) | 0.05 | adimensional |

### 1.9 Parámetros por estrategia de IA

| Parámetro | Símbolo | AGRESIVA | DEFENSIVA | ECONÓMICA | EQUILIBRADA |
|---|---|---|---|---|---|
| Tasa impositiva objetivo | θσ | 125 | 100 | θ_eq (adaptativa) | ½(100 + θ_eq) |
| Fracción tesoro a reclutar | f_rec | 0.90 | 0.60 | 0.30 | 0.70 |
| Ventaja mínima para atacar | γ_atq | 1.1 | 1.8 | 2.0 | 1.4 |
| Superioridad para declarar guerra | γσ | 1.2 | 2.5 | 3.0 | 1.8 |
| Fracción fuerza retenida en guarnición | f_gua | 0.15 | 0.50 | 0.40 | 0.30 |
| Prioridad de fortificación | f_fort | 0.05 | 0.40 | 0.25 | 0.20 |

**Nota de verificación:** γ_atq ≥ 1.5 significa que la estrategia solo ataca en el "régimen determinista" (garantía matemática de victoria, ver Proposición 1 del archivo de ecuaciones). Por eso DEFENSIVA (1.8) y ECONÓMICA (2.0) nunca deberían lanzar un ataque cuyo resultado dependa del azar; AGRESIVA (1.1) y EQUILIBRADA (1.4) sí aceptan riesgo.

---

## 2. Tabla de fases y prioridades (§4.2.1)

| Evento | Tipo | φ (fase) | Prioridad π |
|---|---|---|---|
| E1 INICIO_TURNO | periódico | 0.00 | 0 |
| E7/E8 DIPLOMACIA | periódico | 0.05 | 1 |
| E2 PLANIFICACIÓN | periódico | 0.10 | 2 |
| E3 MOVIMIENTO | condicional | [0.15, 0.15+Δ) | 3 |
| E4 RESOLUCIÓN_COMBATE | condicional | φmov + ε | 4 |
| E5 CONQUISTA | condicional | φcomb + ε | 5 |
| E6 ELIMINACIÓN | condicional | φconq + ε | 6 |
| E9 FIN_TURNO | periódico | 0.90 | 7 |
| E10 FIN_JUEGO | condicional | 0.95 | 8 |

**Verificación clave:** la diplomacia (0.05) debe ejecutarse ANTES que la planificación (0.10). Si el motor evalúa la IA antes de actualizar el estado diplomático, un imperio podría planificar un ataque que luego se cancela por la guarda diplomática — es un error de orden común.

---

## 3. Predicados de validez (§4.3.3)

Antes de procesar cualquier evento extraído de la LEF, el motor debe revalidar que el evento sigue siendo aplicable (el mundo pudo haber cambiado entre que se programó y que se ejecuta):

| Evento | Predicado de validez |
|---|---|
| E2 PLANIFICACIÓN | `activo[i] == True AND numProvincias[i] > 0` |
| E3 MOVIMIENTO | `el ejército existe AND activo[propietario] == True AND Legal(propietario, destino)` |
| E4 COMBATE | `el combate existe AND el atacante existe AND el propietario de la provincia sigue siendo el defensor original` |
| E5 CONQUISTA | `el conquistador sigue activo AND no ha perdido la provincia entretanto` |
| E6 ELIMINACIÓN | `numProvincias[i] == 0 AND activo[i] == True` |
| E10 FIN_JUEGO | `finJuego == False` |

**Caso de prueba más importante:** E3 (movimiento). Si entre que un ejército empieza a moverse y llega a destino, su imperio firmó la paz con el dueño del destino, `Legal()` debe pasar a ser falso y la invasión se cancela sin efecto. Si el motor no revalida esto, permite invasiones "fantasma" a territorio en paz.

---

## 4. Pseudocódigo de los eventos (versión ejecutable, §4.5)

### Inicialización

```
PROCEDIMIENTO Inicializar()
    τ ← 0 ; t ← 1 ; Z ← 0 ; ς ← 0 ; ν ← 0 ; β ← 0 ; LEF ← ∅
    X₀ ← s₀   // semilla del LCG

    ConstruirMapa(N=24, adyacencias, terrenos)

    PARA CADA provincia p:
        propietario[p] ← NEUTRAL
        poblacion[p] ← L0[p]
        descontento[p] ← D0 = 20
        fortificacion[p] ← 0
        guarnicion[p] ← 0
        enConflicto[p] ← 0

    PARA CADA imperio i:
        AsignarProvinciasIniciales(i)   // 3 provincias c/u
        oro[i] ← G0 = 200
        activo[i] ← 1
        estrategia[i] ← SeleccionarEstrategia(i)
        theta[i] ← theta_sigma(estrategia[i])   // según tabla 1.9
        fortificacion[capital[i]] ← 1           // la capital nace fortificada
        CrearEjercito(a) CON fuerza=F0=100, ubicacion=capital[i], moral=1.0, propietario=i
        PARA CADA j ≠ i: diplomacia[i][j] ← PAZ

    m ← numImperios
    Programar(INICIO_TURNO, τ = 1 + φINI)
FIN
```

### E1 — Inicio de Turno

```
EVENTO InicioTurno(t)
    PARA CADA imperio i CON activo[i]==1 HACER
        R ← 0
        PARA CADA p EN provincias[i] HACER
            SI descontento[p] < D* = 60 ENTONCES
                R ← R + calcularIngreso(p, theta[i])   // ec. (3.1)
        C ← c_adm * numProvincias[i] + c_up * poderMilitar[i]   // ec. (3.3)
        neto ← R - C
        oro[i] ← oro[i] + neto
        SI oro[i] < 0 ENTONCES
            Insolvencia(i)   // ec. (5.2): desertan tropas, oro → 0
    FIN PARA

    Programar(DIPLOMACIA, τ = t + φDIP)
    PARA CADA i CON activo[i]==1:
        Programar(PLANIFICACION, τ = t + φPLA, imperio=i)
    Programar(FIN_TURNO, τ = t + φFIN)
FIN
```

### E7/E8 — Diplomacia

```
EVENTO Diplomacia(t)
    ℓ ← argmax_{i: activo[i]=1} numProvincias[i]   // líder
    q_ℓ ← numProvincias[ℓ] / N

    // --- E7: declaraciones de guerra ---
    PARA CADA imperio i activo:
        SI q_ℓ >= theta_am=0.40 AND i≠ℓ AND diplomacia[i][ℓ]=PAZ ENTONCES
            diplomacia[i][ℓ] ← GUERRA   // coalición anti-líder (B3)
            diplomacia[ℓ][i] ← GUERRA
        SINO:
            PARA CADA j activo CON diplomacia[i][j]=PAZ AND Adjacente(i,j):
                SI poderMilitar[i]/max(poderMilitar[j],1) >= gamma_sigma(estrategia[i]) ENTONCES
                    diplomacia[i][j] ← GUERRA   // agresión oportunista
                    diplomacia[j][i] ← GUERRA
                    ROMPER  // solo una guerra nueva por turno

    // --- E8: alianzas ---
    PARA CADA par (i,j) CON i<j, ambos activos:
        SI diplomacia[i][j]=PAZ AND diplomacia[i][ℓ]=GUERRA AND diplomacia[j][ℓ]=GUERRA
           AND i≠ℓ AND j≠ℓ AND q_ℓ >= theta_am ENTONCES
            diplomacia[i][j] ← ALIANZA
            diplomacia[j][i] ← ALIANZA
        SINO SI diplomacia[i][j]=ALIANZA AND q_ℓ < theta_am - histeresis(0.05) ENTONCES
            diplomacia[i][j] ← PAZ   // ruptura con histéresis
            diplomacia[j][i] ← PAZ
FIN
```

### E2 — Planificación / Reclutamiento

```
EVENTO Planificacion(i, t)
    sigma ← estrategia[i]

    // Política fiscal según estrategia (tabla 1.9)
    SI sigma = ECONOMICA:
        theta[i] ← clamp(theta_eq(i), 0, 150)   // ec. (3.8), adaptativa
    SI sigma = EQUILIBRADA:
        theta[i] ← clamp((100 + theta_eq(i))/2, 0, 150)
    SINO:
        theta[i] ← theta_sigma(sigma)   // fijo: 125 AGRESIVA, 100 DEFENSIVA

    // Reclutamiento — ec. (3.11)
    u ← floor(f_rec(sigma) * oro[i] / c_u)
    SI u >= 1:
        oro[i] ← oro[i] - c_u * u
        guarnicion[capital[i]] += u

    // Fortificación
    SI oro[i] >= c_phi / f_fort(sigma) AND existe frontera sin fortificar máximo:
        p* ← frontera con menor fuerza defensiva
        oro[i] -= c_phi
        fortificacion[p*] += 1

    // Levantar ejército nuevo si hay excedente en la capital
    SI guarnicion[capital[i]] > g_ret=30 AND numEjercitos[i] < A_max=4:
        CrearEjercito CON fuerza = guarnicion[capital[i]] - g_ret
        guarnicion[capital[i]] ← g_ret

    // Selección de objetivos y movimiento — ec. (3.14)-(3.15)
    PARA CADA ejercito a DE i:
        F_env ← fuerza[a] * (1 - f_gua(sigma))
        mejor ← ninguno
        PARA CADA provincia q ADYACENTE a ubicacion[a] CON Legal(i,q):
            Pa_det ← F_env * moral[a] * T(terreno[q], ATQ)
            Pd_det ← fuerzaDefensiva(q) * Phi(fortificacion[q]) * T(terreno[q],DEF) * Psi(descontento[q])
            SI Pa_det >= gamma_atq(sigma) * Pd_det:
                SI mejor=ninguno O poblacion[q] > poblacion[mejor]: mejor ← q
        SI mejor ≠ ninguno:
            Programar(MOVIMIENTO, τ=Llegada(t, coste(terreno[mejor])), ejercito=a, destino=mejor)
        SINO:
            q ← SiguientePasoHaciaFrontera(a)   // BFS multifuente
            SI q ≠ ninguno: Programar(MOVIMIENTO, τ=Llegada(t,...), ejercito=a, destino=q)
FIN
```

### E3 — Movimiento de Ejército

```
EVENTO Movimiento(a, q, τ)
    i ← propietario[a]
    SI ¬Valido(evento): descartar   // ver tabla 3

    SI propietario[q]=i OR diplomacia[i][propietario[q]]=ALIANZA:
        ubicacion[a] ← q   // avance propio o tránsito aliado, sin combate
    SINO SI propietario[q]=NEUTRAL:
        ubicacion[a] ← q
        Programar(CONQUISTA, τ+ε, provincia=q, conquistador=i)
    SINO:  // enemigo en guerra (garantizado por Legal())
        C ← CrearCombate(atacante=a, provincia=q, defensa=fuerzaDefensiva(q))
        enConflicto[q] ← 1
        Programar(RESOLUCION_COMBATE, τ+ε, combate=C)
FIN
```

### E4 — Resolución de Combate

```
EVENTO ResolucionCombate(C, τ)
    p ← C.provincia ; a ← C.atacante ; D ← fuerzaDefensiva(p)

    Ua ← TriangularInversa(SiguienteUniforme())   // ec. (3.25)-(3.26)
    Ud ← TriangularInversa(SiguienteUniforme())

    Pa ← fuerza[a] * moral[a] * T(terreno[p],ATQ) * Ua        // ec. (3.14)
    Pd ← D * Phi(fortificacion[p]) * T(terreno[p],DEF) * Psi(descontento[p]) * Ud  // ec. (3.15)

    SI Pa > Pd:  // vence atacante
        b_gan ← fuerza[a] * (Pd/Pa) * K_B     // ec. (3.19)
        b_perd ← D
        moral[a] ← max(mu_min, moral[a]*(1 - gamma_mu * b_gan/fuerza[a]))
        fuerza[a] ← fuerza[a] - b_gan
        guarnicion[p] ← 0 ; DestruirEjercitosEstacionadosEn(p)
        SI fuerza[a] < F_min=5: DestruirEjercito(a)
        Programar(CONQUISTA, τ+ε, provincia=p, conquistador=i)
    SINO:  // vence defensor
        b_gan ← D * (Pa/Pd) * K_B
        b_perd ← fuerza[a]
        RepartirBajas(p, b_gan)   // ec. (3.12c), proporcional entre guarnición y ejércitos
        DestruirEjercito(a)

    ν ← ν+1 ; β ← β + b_gan + b_perd
    beta_p[t] ← beta_p[t] + b_gan + b_perd   // alimenta ec. (3.10), daño de guerra
    enConflicto[p] ← 0 ; DestruirCombate(C)
FIN
```

### E5 — Conquista de Provincia

```
EVENTO Conquista(p, conquistador, τ)
    antiguo ← propietario[p]
    SI antiguo ≠ NEUTRAL:
        provincias[antiguo].remover(p)
        numProvincias[antiguo] -= 1
        diplomacia[conquistador][antiguo] ← GUERRA
        diplomacia[antiguo][conquistador] ← GUERRA

    provincias[conquistador].agregar(p)
    numProvincias[conquistador] += 1
    propietario[p] ← conquistador
    fortificacion[p] ← max(0, fortificacion[p] - 1)   // ec. (5.6), degradación por asedio
    descontento[p] ← min(100, descontento[p] + 10)     // ocupación militar

    SI antiguo ≠ NEUTRAL AND p = capital[antiguo]:
        ReasignarCapital(antiguo)   // §5.3(c)
    SI antiguo ≠ NEUTRAL AND numProvincias[antiguo] == 0:
        Programar(ELIMINACION, τ+ε, imperio=antiguo)
FIN
```

### E6 — Eliminación de Imperio

```
EVENTO Eliminacion(i, τ)
    activo[i] ← 0
    m ← m - 1
    DestruirTodosLosEjercitos(i)
    PARA CADA j ≠ i: diplomacia[i][j] ← PAZ ; diplomacia[j][i] ← PAZ
    Registrar(turnoEliminacion[i] = floor(τ))
FIN
```

### E9 — Fin de Turno

```
EVENTO FinTurno(t)
    PARA CADA imperio i CON activo[i]==1 HACER
        enGuerra ← existe j : diplomacia[i][j] = GUERRA
        deltaD ← eta_theta*(theta[i]-theta0) + eta_w*enGuerra
                  + eta_n*max(0, numProvincias[i]-n_star) - eta_r    // ec. (3.6)
        PARA CADA p EN provincias[i]:
            descontento[p] ← clamp(descontento[p] + deltaD, 0, 100)
            poblacion[p] ← min(Lmax, poblacion[p]*(1+gL)) - rho*beta_p[t]   // ec. (3.10)
            poblacion[p] ← max(0, poblacion[p])
            beta_p[t] ← 0
        PARA CADA ejercito a DE i:
            techo ← max(mu_min, 1 - lambda_d * distancia(ubicacion[a], capital[i]))  // ec.(3.27)
            moral[a] ← min(techo, moral[a] + rho_mu)   // ec. (3.28)
        poderMilitar[i] ← sum(fuerza de ejercitos[i]) + sum(guarnicion de provincias[i])  // ec.(3.12)

    ℓ ← argmax_{i activo} numProvincias[i]
    q_ℓ ← numProvincias[ℓ] / N
    SI q_ℓ >= Theta_V=0.60 OR m==1 OR t >= t_max=200:
        Programar(FIN_JUEGO, τ = t + φJUE, ganador=ℓ)
    SINO:
        Programar(INICIO_TURNO, τ = (t+1) + φINI)
FIN
```

### E10 — Fin de Juego

```
EVENTO FinJuego(ganador, t)
    Z ← 1
    Registrar(imperioGanador=ganador, estrategiaGanadora=estrategia[ganador],
              turnoFinal=floor(t), numCombates=ν, bajasTotales=β,
              cuotaFinal=numProvincias[ganador]/N)
FIN
```

---

## 5. Checklist rápido para pedirle a OpenCode que verifique

Al comparar contra el código real, revisa específicamente:

- [ ] ¿`calcularIngreso` corta a 0 exactamente cuando `descontento >= 60` (no `> 60`)?
- [ ] ¿El tesoro nunca queda negativo (`max(0, ...)`)  y dispara deserción si el neto es negativo?
- [ ] ¿La diplomacia (E7/E8) se evalúa ANTES que la planificación (E2) dentro del mismo turno?
- [ ] ¿Existe la guarda `Legal()` que impide invadir territorio en PAZ?
- [ ] ¿El generador aleatorio usa distribución triangular (0.8, 1.0, 1.2), no uniforme?
- [ ] ¿Las bajas del bando ganador se calculan proporcionales a la fuerza del PERDEDOR (ec. 3.19), no a la propia?
- [ ] ¿La fortificación se degrada (`-1` nivel) cada vez que la provincia es conquistada?
- [ ] ¿Existe el mecanismo de coalición anti-líder cuando un imperio supera 40% del mapa?
- [ ] ¿Cada estrategia de IA usa los parámetros exactos de la tabla 1.9 (no valores inventados)?
- [ ] ¿Los eventos revalidan su condición antes de ejecutarse (tabla 3), para no procesar eventos "fantasma"?
