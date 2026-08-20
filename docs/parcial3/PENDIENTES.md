# Cierre del Parcial III — análisis de conformidad y plan de desarrollo

> **Actualizado el 19-ago-2026 tras el commit `6e6fa6e`.** Cambios de estado:
> **B8 publicación → resuelto** (`main` sincronizado con `origin/main`; queda Moodle) ·
> **B7 equipo → parcial** (A. Carvajalino ya tiene commit propio; falta
> `CONTRIBUCIONES.md`). B1–B6 sin cambios. La auditoría del motor Java reescrito y las
> seis correcciones aplicadas están en [`AUDITORIA.md`](AUDITORIA.md).

**Fuentes contrastadas**

1. `Modelo Conceptual Age of Conquest — Arturo Carvajalino, Leonardo Lobo` (14 pp., Parcial I) —
   entidades, atributos, métodos, E1–E10, bucles causales R1/B1/B2/B3, objetivos **O1–O5**,
   estructura de la LEF y pseudocódigo abierto.
2. `PARCIAL2 Simulacion de Sistema.pdf` (58 pp., Parcial II) — modelo formal cerrado:
   ~35 parámetros con valor, ecuaciones (3.1)–(3.32), tabla de fases (4.1), orden total de la
   LEF (4.2), predicados de validez (tabla 4.3), pseudocódigo definitivo (4.5), árbol de
   decisión de la IA (4.6), traza de escritorio (4.7), condiciones de frontera (cap. 5),
   trazabilidad (6.2), factores de experimentación (6.3) y Anexos A/B/C.
   *Nota:* es el mismo documento que `docs/parcial2/PARCIAL2.md`, reexportado
   (58 pp. vs 55 pp.; solo difiere la paginación del renderizador). El código del Parcial III
   se construyó sobre la fuente correcta.
3. Enunciado del Parcial III (simulador + informe + defensa).

**Método:** lectura íntegra de ambos PDF, extracción de los requisitos verificables y
contraste uno a uno contra `sim/`, `tests/`, `escenarios/`, `resultados/` y
`docs/parcial3/PARCIAL3.md`, ejecutando el simulador.

---

## 0. Veredicto

**El simulador cumple el modelo formal con un grado de fidelidad alto y verificado.**
No hay ningún subsistema ausente, ningún parámetro con valor distinto al del Anexo A y
ningún evento sin implementar. Lo que falta son **ocho brechas concretas**, ninguna de
arquitectura: dos son métricas y salidas que el modelo exige y no se producen, una es una
no conformidad puntual con la tabla 4.3, una es alcance experimental recortado, dos son de
presentación del informe y dos son administrativas de entrega.

| Bloque | Conformidad |
|:--|:--|
| Entidades, atributos y variables de estado (cap. 2) | ✅ completa |
| 35 parámetros del Anexo A | ✅ **valor por valor idéntico** |
| Escenario del Anexo C | ✅ exacto: N=24, grado medio 3.50, 10 LLA / 6 BOS / 4 MON / 4 COS, 4 imperios × 3 provincias, 12 neutrales, s₀=20 260 805 |
| Funciones $\mathcal{F}_1$–$\mathcal{F}_{13}$ (tabla 6.2) | ✅ las 13 implementadas |
| Eventos E1–E10 (§4.5) | ✅ los 10 (E7/E8 unificados en un evento DIPLOMACIA, como en el propio §4.5) |
| Predicados de validez (tabla 4.3) | ⚠️ 5 de 6 conformes — **E5 incompleto** (brecha B3) |
| Reloj τ = t + φ, ε = 10⁻³, Δ = 0.746, orden total | ✅ con aserción en `sim/reloj.py:37` |
| Bucles causales R1, B1, B2, B3 (§3.8) | ✅ los cuatro con mecanismo |
| Condiciones de frontera (cap. 5) | ✅ 34/34 comprobaciones |
| Traza de escritorio §4.7 | ✅ 44/44, error relativo máx. 5.1×10⁻⁴ |
| Objetivos del modelo **O1, O2, O4, O5** | ✅ medidos y reportados con IC 95 % |
| Objetivo **O3 — efecto bola de nieve** | ❌ **no se calcula ni se reporta** (brecha B1) |
| Series por turno exportadas | ❌ **no existen**, pero el informe afirma que sí (brecha B2) |
| Diseño de experimentos de §6.3 (4 factores) | ⚠️ **solo 1 de 4** barridos (brecha B4) |
| Validación contra el juego real | ⚠️ declinada y justificada (brecha B5) |
| Figuras en el informe | ❌ cero (brecha B6) |
| Historial de contribuciones del equipo | ❌ 2 autores, 1 con commits (brecha B7) |
| Publicación y entrega | ❌ 2 commits sin push, PDF sin subir (brecha B8) |

**Evidencia de ejecución (verificada):**

```
python3 tests/test_traza_dorada.py   → 44/44   rc=0
python3 tests/test_fronteras.py      → 34/34   rc=0
python3 -m sim --turnos 5            → 81 eventos, Teoremas 1 y 2 sin violaciones, rc=0
python3 -m sim --partida             → 1448 eventos, aleatorios=30=2ν, rc=0
python3 -m sim --caso todos          → los 5 casos borde, rc=0
python3 -m sim --interactivo         → 14 comandos, rc=0
```

---

# Parte I — Las ocho brechas

## B1 — El objetivo O3 no se mide *(severidad: alta — es un objetivo declarado del modelo)*

**Qué exigen los documentos.**
El Parcial I fija cinco objetivos y sus métricas:

> **O3** Cuantificar el efecto "bola de nieve" — *Correlación provincias↔oro↔poder militar en el tiempo*
> **Índice de bola de nieve** — *pendiente de provincias(t) tras el punto de inflexión*

El Parcial II §2.5 lo traza a las variables de estado $n_i$, $G_i$, $M_i$, y §3.8 hace de R1
—el bucle que O3 mide— el motor central del modelo, con la cadena
$n_i \uparrow \to R_i \uparrow \to G_i \uparrow \to u_i \uparrow \to M_i \uparrow \to P_a \uparrow \to n_i \uparrow$.

**Qué hay hoy.**
`Recolector.series` (`sim/recolector.py:25`) sí acumula por turno la tupla
`(turno, imperio, estrategia, n, oro, poder, cuota, descontento_medio, tasa, Δdescontento, en_guerra)`
—es decir, **las tres variables de O3 ya están registradas**— pero nada las procesa.
`ResultadoPartida` no tiene ningún campo de O3, `lote.resumir` no lo agrega y el informe
§4.3 presenta O1, O2, O4 y O5 y **omite O3 sin mencionarlo**. El bucle R1, que es la tesis
dinámica de todo el trabajo, es el único que no se cuantifica.

**Por qué importa en la defensa.** Es la pregunta más fácil de hacer: *"su modelo se apoya
en el bucle bola de nieve; ¿cuánto vale?"*. Hoy no hay número.

---

## B2 — Las series por turno no se exportan, y el informe afirma que sí *(severidad: alta — es una afirmación falsable)*

**Qué dice el informe.** `docs/parcial3/PARCIAL3.md`, tabla de §4.1:

> | Gráficas operacionales | ✔ | series por turno exportadas a CSV |

**Qué hay.** `lote.exportar_csv` (`sim/lote.py:123`) escribe **una fila por réplica**, no
por turno. `resultados/p3/` contiene exactamente dos ficheros —`base.csv` (40 filas) y
`barrido_kb.csv`— ambos con el esquema de `ResultadoPartida`. `rec.series` se usa en un
único punto (`sim/lote.py:43`) para decidir si el ganador estuvo en guerra continua, y se
descarta. **No existe ninguna serie temporal en disco.**

Un jurado que abra `resultados/p3/` durante la demo ve que la fila de la tabla no se
sostiene. O se implementa la exportación (recomendado: es media hora y además desbloquea
B1 y las figuras de B6) o se corrige la tabla.

---

## B3 — El predicado de validez de E5 no es conforme con la tabla 4.3 *(severidad: alta — reabre un defecto declarado reparado)*

**Qué exige el Parcial II**, tabla 4.3 §4.3.3:

> | E5 CONQUISTA | el conquistador sigue activo **∧ no ha perdido la provincia entretanto** |

**Qué implementa el código** (`sim/eventos.py:59`):

```python
if t is TipoEvento.CONQUISTA:
    imp = estado.imperios.get(d["conquistador"])
    return imp is not None and imp.activo        # falta la segunda condición
```

**Consecuencia observable.** En la salida de `python3 -m sim --turnos 5` —que es el bloque 2
del guion de defensa, *"el requisito literal del enunciado"*— aparece:

```
τ=5.738  E5 CONQUISTA p22 (NEUTRAL → cimeria) φ=0 D=30
τ=5.738  E5 CONQUISTA p22 (cimeria → cimeria) φ=0 D=40
τ=5.818  E5 CONQUISTA p13 (NEUTRAL → borealis) φ=0 D=30
τ=5.818  E5 CONQUISTA p13 (borealis → borealis) φ=0 D=40
```

Dos ejércitos del mismo imperio llegan a la misma provincia neutral en el mismo instante;
ambos programan E5 antes de que ninguno se ejecute; el segundo **se autoconquista**. Como
`e5_conquista` (`sim/eventos.py:218`) aplica incondicionalmente

```python
p.fortificacion = max(0, p.fortificacion - 1)
p.descontento   = min(100.0, p.descontento + 10.0)   # ← +10 duplicado
p.guarnicion    = 0.0                                # ← guarnición anulada dos veces
```

el efecto **no es cosmético**: altera el estado (descontento 30 → 40) y, a través de
(3.6)–(3.9), la renta, la política fiscal y toda la trayectoria posterior.

**Por qué es grave más allá del bug.** El propio Parcial II §1.3 declara este patrón como
el defecto **D4** del modelo conceptual —*"llegadas simultáneas indefinidas: dos ejércitos
que alcanzan la misma provincia en el mismo instante"*— y afirma haberlo reparado con la
clave de orden total y los predicados de revalidación. La implementación reparó la mitad
del caso (E4) y dejó E5 abierto. Es una **no conformidad documentada contra un defecto
que el documento declara cerrado**, y está impresa en la salida que se va a proyectar.

---

## B4 — El diseño de experimentos cubre 1 de los 4 factores que el Parcial II designó *(severidad: alta — es el hueco de contenido más grande)*

**Qué exige el Parcial II**, §6.3 *Parámetros pendientes de calibración experimental*:

> Los marcados `[C]` en el capítulo 2 son **los factores del diseño de experimentos del Parcial III**.

| Factor | Bucle | Efecto esperado | ¿Barrido? |
|:--|:--|:--|:--:|
| $K_B$ | B2 atrición | duración (O1) e intensidad bélica (O4) | ✅ §4.4 |
| $\eta_n$, $n^\ast$ | B1d sobreextensión | tamaño máximo sostenible (3.9); **decide si la victoria es alcanzable** | ❌ |
| $\theta_{\text{am}}$ | B3 coalición | turno del punto de inflexión (O5) | ❌ |
| $\gamma_{\text{atq}}$ | política de IA | tasa de victoria por estrategia (O2) | ❌ |

**Qué agrava la omisión.** El informe §4.3 documenta que **O2 está degenerado**: ECONÓMICA
gana 40 de 40 réplicas y las otras tres estrategias 0 %. El propio informe diagnostica la
causa —*"$\eta_\theta$, $\eta_n$ y $n^\ast$ están calibrados de forma que la ventaja de la
política adaptativa domina"*— y concluye: *"Reequilibrarlos es el trabajo de calibración
natural de una fase posterior."*

Esa fase posterior **es este parcial**. §6.3 ya había nombrado exactamente esos parámetros
como los factores a barrer, y el enunciado del Parcial III pide "Validación y
**Calibración**". Dejar el diagnóstico escrito sin ejecutar el barrido que lo resolvería es
la crítica más previsible de la defensa, y la más fácil de cerrar: la maquinaria de
`lote.barrido_k_bajas` ya existe y solo hay que generalizarla.

---

## B5 — Validación contra el juego real *(severidad: media — decisión defendible, pero es literal en el enunciado)*

El enunciado pide *"tablas o gráficas comparativas que demuestren que los resultados de su
simulador coinciden con los del juego real bajo las mismas condiciones iniciales"*.
El informe §4.1 **declina esa comparación y la argumenta**: de los 35 parámetros, solo
$\beta_F$ está marcado `[D]`; los otros 34 son `[M]` o `[C]`; una coincidencia numérica
sería un artefacto de calibración. Metodológicamente el argumento es correcto y honesto, y
la respuesta ya está preparada en `DEFENSA.md:118`.

Aun así se entrega **cero comparación de ninguna clase**, y la casilla del rubro queda
vacía. Hay dos formas de llenarla sin falsear nada (Parte II, plan B5).

---

## B6 — El informe no tiene una sola figura *(severidad: media)*

157 líneas de tabla, ningún diagrama y ninguna gráfica. Los Parciales I y II sí traían
figuras (diagrama de bucles causales, grafo de eventos, diagramas mermaid del ciclo de
turno). El pipeline `docs/parcial3/.build/` ya incluye `mermaid.js` y renderiza vía
Chrome headless, y los datos de las gráficas ya están en `resultados/p3/`. Un informe de
Simulación sin la curva del barrido de sensibilidad ni un diagrama de arquitectura pierde
puntos que están al alcance de la mano.

---

## B7 — El historial no muestra al equipo *(severidad: media — se evalúa explícitamente)*

Ambos PDF están firmados por **Arturo Carvajalino (V-30.889.966)** y **Leonardo Lobo
(V-31.489.733)**, y `PARCIAL3.md` los declara a los dos como autores. El repositorio, en
cambio, tiene 9 commits y **todos son de Leonardo** (bajo tres identidades de git distintas:
`Leonardo Jose Lobo Candelario`, `LEONARDO LOBO`, `leonardo02lobo`).

El enunciado dice: *"entregarse a través de un repositorio de control de versiones para
evaluar **el historial de contribuciones del equipo**"*. Hoy ese historial muestra un
equipo de una persona.

---

## B8 — La entrega no está hecha *(severidad: bloqueante)*

- `main` está **2 commits por delante de `origin/main`**. Los dos commits que contienen
  *todo* el Parcial III (`41ec51e` el simulador, `c1ec383` los casos borde) **no están
  publicados**. Quien clone el repositorio hoy no ve el Parcial III.
- `docs/parcial3/PARCIAL3.pdf` no consta subido a Moodle.

---

# Parte II — Plan de desarrollo

Orden pensado para que nada se rehaga: primero lo que cambia números (B3), luego lo que
produce datos (B2, B1, B4), luego lo que los presenta (B6, B5), y el PDF y la entrega al
final.

## Paso 1 · B3 — Conformar el predicado de E5

**Archivo:** `sim/eventos.py`, función `es_valido`, rama `TipoEvento.CONQUISTA` (línea 59).

```python
if t is TipoEvento.CONQUISTA:
    imp = estado.imperios.get(d["conquistador"])
    if imp is None or not imp.activo:
        return False
    # Tabla 4.3: "…∧ no ha perdido la provincia entretanto".
    # Cancela el E5 redundante cuando un E5 hermano, programado en el mismo τ
    # por otro ejército del mismo imperio, ya transfirió la provincia (defecto D4).
    return estado.provincia(d["provincia"]).propietario != d["conquistador"]
```

**Comentario a dejar en el código:** citar tabla 4.3 y el defecto D4 de §1.3 — es la clase
de trazabilidad que el informe §2.5 promete.

**Efecto colateral que hay que asumir.** El arreglo **cambia las trayectorias**: la
provincia deja de recibir +10 de descontento espurio y de perder la guarnición dos veces,
lo que se propaga por (3.6)–(3.9) y por la política fiscal. La conquista no consume
aleatorios, así que las secuencias por semilla no se desplazan, pero **todos los números
publicados en §4.2, §4.3 y §4.4 del informe se moverán**. Por eso este paso va primero.

**Verificación:**

```bash
python3 tests/test_traza_dorada.py    # debe seguir en 44/44
python3 tests/test_fronteras.py       # debe seguir en 34/34
python3 -m sim --turnos 5 | grep CONQUISTA   # ya no debe haber pares (X → X)
```

**Criterio de aceptación:** ninguna línea E5 con `(imperio → mismo imperio)`; las dos
suites intactas; contador `eventos cancelados` mayor o igual que antes.

**Añadir al informe:** una entrada en §3.3 o en el anexo de decisiones — *"defecto
detectado por la propia traza y corregido: el predicado de E5 no implementaba la segunda
condición de la tabla 4.3"*. Encontrar y documentar un error propio suma en la defensa;
`DEFENSA.md:133` ya tiene la pregunta *"¿Encontraron algún error en su propio modelo?"*
preparada, y esto es material de primera para esa respuesta.

---

## Paso 2 · B2 — Exportar las series por turno

**Archivo:** `sim/lote.py`, junto a `exportar_csv`.

```python
CABECERA_SERIES = ("turno,imperio,estrategia,n,oro,poder,cuota,"
                   "descontento_medio,tasa,delta_descontento,en_guerra")

def exportar_series_csv(rec, ruta: Path) -> None:
    """Serie temporal por turno e imperio — insumo de O3 y de las gráficas
    operacionales declaradas en §4.1 del informe."""
    ruta.parent.mkdir(parents=True, exist_ok=True)
    with ruta.open("w", encoding="utf-8") as f:
        f.write(CABECERA_SERIES + "\n")
        for (turno, iid, estr, n, oro, poder, cuota,
             desc, tasa, dd, guerra) in rec.series:
            f.write(f"{turno},{iid},{estr},{n},{oro:.4f},{poder:.4f},"
                    f"{cuota:.4f},{desc:.4f},{tasa:.1f},{dd:.4f},{int(guerra)}\n")
```

**Enganches en `sim/cli.py`:**
- en `--partida`, escribir `resultados/p3/series_referencia.csv`;
- en `--lote N`, escribir además la serie de la **réplica de la semilla base** (`s₀`), que
  es la partida que el informe narra en §4.2.

**Criterio de aceptación:** el fichero existe y tiene
`turnos × imperios_activos_por_turno + 1` filas; la fila de §4.1 del informe pasa a ser
cierta; `--partida` sigue en rc=0.

---

## Paso 3 · B1 — Calcular y reportar O3

**3a. Estadísticos (stdlib pura, sin dependencias).** Añadir a `sim/recolector.py`:

```python
def _pendiente_ols(xs: list[float], ys: list[float]) -> float | None:
    """Pendiente de la recta de mínimos cuadrados. None si hay <3 puntos
    o varianza nula en x (caso degenerado: partida sin inflexión)."""
    n = len(xs)
    if n < 3:
        return None
    mx = sum(xs) / n
    my = sum(ys) / n
    sxx = sum((x - mx) ** 2 for x in xs)
    if sxx == 0.0:
        return None
    return sum((x - mx) * (y - my) for x, y in zip(xs, ys)) / sxx


def _pearson(xs: list[float], ys: list[float]) -> float | None:
    """Coeficiente de correlación lineal. None si alguna serie es constante."""
    n = len(xs)
    if n < 3:
        return None
    mx, my = sum(xs) / n, sum(ys) / n
    sxx = sum((x - mx) ** 2 for x in xs)
    syy = sum((y - my) ** 2 for y in ys)
    if sxx == 0.0 or syy == 0.0:
        return None
    sxy = sum((x - mx) * (y - my) for x, y in zip(xs, ys))
    return sxy / (sxx * syy) ** 0.5
```

**3b. Las tres cifras de O3**, calculadas sobre la serie del **imperio líder al final de la
partida** (el que da sentido a "bola de nieve"):

| Cifra | Definición | Fuente documental |
|:--|:--|:--|
| `pendiente_bola_nieve` | pendiente OLS de $n_\ell(t)$ para $t \ge$ `turno_inflexion` | Parcial I, *"pendiente de provincias(t) tras el punto de inflexión"* |
| `corr_n_oro` | Pearson$(n_\ell(t),\,G_\ell(t))$ sobre toda la partida | Parcial I / P2 §2.5, *"correlación provincias↔oro"* |
| `corr_n_poder` | Pearson$(n_\ell(t),\,M_\ell(t))$ sobre toda la partida | Parcial I / P2 §2.5, *"correlación provincias↔poder militar"* |

**Casos degenerados a manejar explícitamente** (y a mencionar en el informe, coherente con
el cap. 5): si `turno_inflexion is None` la pendiente es `None`; si la partida acaba antes
de 3 turnos posteriores a la inflexión, `None`; si el líder está eliminado, se toma el de
mayor $n_i$ entre los activos. En el CSV, `None` se escribe como campo vacío, igual que ya
se hace con `turno_inflexion`.

**3c. Propagación:**
- `ResultadoPartida`: tres campos nuevos + tres columnas en `cabecera_csv` / `fila_csv`
  (**al final**, para no romper los CSV ya generados).
- `lote.resumir`: media e IC 95 % de las tres, reusando `intervalo_confianza`, filtrando
  los `None`, y **reportando cuántas réplicas se descartaron** (el informe ya usa ese
  estilo: *"40/40 partidas"* en O5).

**3d. Informe.** En §4.3, añadir a la tabla:

| Métrica | Valor | IC 95 % |
|:--|--:|:--|
| **O3** pendiente de $n_\ell(t)$ tras la inflexión | … prov/turno | ± … |
| **O3** corr$(n_\ell, G_\ell)$ | … | ± … |
| **O3** corr$(n_\ell, M_\ell)$ | … | ± … |

y un párrafo de interpretación que cierre el argumento de §3.8: si la pendiente es positiva
y la correlación $n\!-\!M$ alta, **R1 domina y está cuantificado**; si la correlación
$n\!-\!G$ resulta baja o negativa, el hallazgo es aún más interesante y hay que decirlo —
significaría que B1d (descontento por sobreextensión) está estrangulando el brazo económico
de R1, que es exactamente el mecanismo con el que §4.3 explica la victoria de ECONÓMICA.
Los dos desenlaces son publicables; lo que no es publicable es no medirlo.

**Criterio de aceptación:** `python3 -m sim --lote 40` imprime O3 con IC; `base.csv` trae
las tres columnas; §4.3 del informe cubre O1–O5 **completo**.

---

## Paso 4 · B4 — Completar el diseño de experimentos de §6.3

**4a. Generalizar el barrido.** Sustituir `barrido_k_bajas` por un barrido genérico
conservando las semillas apareadas (números aleatorios comunes), que es lo que ya hace bien:

```python
def barrido(campo: str, valores: list[float], n: int = 30,
            semilla_base: int = 20_260_805) -> list[ResultadoPartida]:
    """Barrido de un parámetro [C] con semillas apareadas entre variantes.
    `campo` es el nombre del atributo en Parametros (§6.3 designa los factores)."""
    salida = []
    for v in valores:
        par = Parametros().copia(**{campo: v})
        for k in range(n):
            r = jugar_una(semilla_base + k, par)
            r.parametro, r.valor = campo, v
            salida.append(r)
    return salida
```

y `barrido_k_bajas(n)` pasa a ser `barrido("k_bajas", [0.3, 0.5, 0.7, 0.9], n)`.

**4b. El factor $\gamma_{\text{atq}}$ necesita un puente.** No vive en `Parametros` sino en
`PARAMETROS_ESTRATEGIA` (`sim/agentes.py:42`), que es la tabla §2.4.9 (AGR 1.1 · DEF 1.8 ·
ECO 2.0 · EQU 1.4). Para barrerlo con la misma maquinaria, añadir a `Parametros`:

```python
escala_gamma_atq: float = 1.0   # multiplicador global de γ_atq — factor de §6.3   [C]
```

y aplicarlo en el único punto donde se usa el umbral, `agentes.seleccionar_objetivo`
(`sim/agentes.py:145`):

```python
if pa >= par.gamma_ataque * estado.parametros.escala_gamma_atq * pd:
```

Con `escala = 1.0` el comportamiento es idéntico al actual, así que la traza dorada y las
fronteras no se ven afectadas — compruébalo.

**4c. Los tres barridos que faltan.**

| Flag nuevo | Factor | Valores sugeridos | Métrica que se contrasta | Predicción de §6.3 a validar |
|:--|:--|:--|:--|:--|
| `--barrido eta_n` | `eta_extension` ($\eta_n$) | 0.25, 0.50, 0.75, 1.00 | cuota final, % de partidas ganadas por $\Theta_V$ frente a $t_{\max}$, O1 | gobierna $n^{\max}$ de (3.9) → **decide si la victoria es alcanzable** |
| `--barrido n_estrella` | `umbral_admin` ($n^\ast$) | 6, 8, 10, 12 | ídem | ídem (es el otro término de (3.9)) |
| `--barrido theta_am` | `cuota_amenaza` ($\theta_{am}$) | 0.30, 0.40, 0.50, 0.60 | **O5** turno de inflexión, nº de coaliciones formadas | gobierna B3 → punto de inflexión |
| `--barrido gamma_atq` | `escala_gamma_atq` | 0.8, 1.0, 1.2, 1.4 | **O2** tasa de victoria por estrategia | política de IA → balance entre estrategias |

30 réplicas por valor, semillas apareadas, salida a
`resultados/p3/barrido_<factor>.csv`. Coste ≈ el mismo que el barrido de $K_B$ ya hecho.

**4d. Lo que hay que contar en el informe (§4.5 nueva, o §4.4 ampliada).**
Para cada factor: tabla de medias con IC 95 %, y una frase que diga si la predicción de
§6.3 **se confirma o no** — el informe ya usa ese patrón con acierto en §4.4 (*"Sobre O4 la
predicción se confirma… sobre O1 no"*), y esa honestidad es lo mejor que tiene.

**4e. El cierre que conecta con la calibración.** Con el barrido de $\eta_n$/$n^\ast$ en la
mano, buscar si existe una región de parámetros donde **O2 deja de ser degenerado** (donde
AGRESIVA, DEFENSIVA o EQUILIBRADA ganan alguna réplica). Dos desenlaces, ambos válidos:

- *Se encuentra:* se reporta como **calibración** —"con $\eta_n = 0.25$ la tasa de victoria
  pasa a ser X/Y/Z/W"— y la casilla "Calibración" del enunciado queda cubierta con
  evidencia propia.
- *No se encuentra:* se reporta como resultado estructural —"la ventaja de la política
  fiscal adaptativa es robusta a la penalización por sobreextensión en todo el rango
  barrido"— que es un hallazgo más fuerte que la conjetura actual de §4.3.

**Criterio de aceptación:** 4 de 4 factores de §6.3 barridos; §6.3 del Parcial II queda
íntegramente respondida; el informe ya no remite a "una fase posterior".

---

## Paso 5 · B6 — Figuras

El pipeline (`docs/parcial3/.build/`) ya trae `mermaid.js` y compone con Chrome headless,
así que acepta tanto bloques ```mermaid``` como `<img>`/SVG local. Mínimo recomendado:

| Fig. | Qué | Cómo | Dónde |
|:--|:--|:--|:--|
| 1 | Arquitectura de módulos: los 16 módulos de `sim/` y sus dependencias, anotados con el capítulo del Parcial II que implementan | `mermaid` `flowchart LR` | §2.1 |
| 2 | Ciclo del motor: extracción de la LEF → predicado de validez → despacho → reprogramación | `mermaid` `flowchart TD` (adaptar el de §4.4 del Parcial II) | §2.2 |
| 3 | Sensibilidad de $K_B$: bajas acumuladas con barras de IC 95 % | SVG generado desde `barrido_kb.csv` | §4.4 |
| 4 | $n_i(t)$ de los cuatro imperios en la partida de referencia — **la imagen del bucle R1 y del punto de inflexión** | SVG desde `series_referencia.csv` (requiere paso 2) | §4.2 o §4.3 |
| 5 | Histograma de la duración O1 sobre las 40 réplicas, que hace visible la bimodalidad que §4.3 solo describe con σ = 83.26 | SVG desde `base.csv` | §4.3 |

Para 3–5, un `docs/parcial3/.build/figuras.py` que lea el CSV y escriba SVG con la
biblioteca estándar mantiene la promesa de "ninguna dependencia externa" del README. Si se
prefiere `matplotlib`, hay que declararlo en el informe y en el README.

---

## Paso 6 · B5 — Llenar la casilla de comparación sin falsear nada

Tres opciones, de menor a mayor coste. **La (a) es obligatoria; la (b) es la que convierte
la brecha en fortaleza.**

**(a) Tabla de correspondencia estructural** — nueva §4.1.1, ~1 página, sin ejecutar nada.
No afirma coincidencia numérica; afirma **correspondencia mecánica**, que es lo que el
modelo sí puede sostener:

| Mecánica de Age of Conquest IV | ¿Modelada? | Cómo, y con qué ecuación | Procedencia |
|:--|:--:|:--|:--|
| Renta por población de la provincia | sí | (3.1) $I_p = \iota L_p \theta_i/100$ | [M] |
| Bono defensivo por fortificación | **sí, calibrado** | (3.16) $\Phi(\phi)=1+\beta_F\phi$, $\beta_F=0.15$ | **[D]** — único parámetro documentado del juego |
| Degradación de fortificación por asedio | sí | (5.6), E5 | [M] |
| Victoria por cuota de territorio | sí | $\Theta_V = 0.60$, E9 | [M] |
| Eliminación de imperio | sí | E6 | — |
| Estados diplomáticos paz/guerra/alianza | sí | §3.7, matriz simétrica | — |
| Puntos de acción por turno | **no** | fuera de alcance, §1.2 | — |
| Rey / capital como unidad capturable | parcial | capital $c_i$ sin unidad rey; §5.3(c) | — |
| Saqueo, decretos, revueltas | **no** | fuera de alcance, §1.2 | — |
| Tasas impositivas discretas 0–200 % | **no**: continua en [0, 150] | refinamiento O1 de §1.3 | — |

Cerrar con la frase que ya está en §4.1: la comparación numérica no procede porque 34 de
35 parámetros son de modelado; lo comparable es la **estructura**, y esta tabla la exhibe.

**(b) Comparación ordinal con partidas reales** — 2–3 h de trabajo, y es lo que de verdad
llena la casilla. Age of Conquest IV es accesible; el protocolo tiene que quedar escrito en
el informe para que sea reproducible:

- Escenario del juego lo más parecido posible al Anexo C: mapa pequeño, **4 imperios**,
  IA en dificultad media, sin diplomacia manual del jugador (o el jugador como espectador).
- **5 partidas**, registrando por turno: nº de provincias de cada imperio, y el turno en
  que el líder supera el 50 % del mapa.
- Comparar **magnitudes adimensionales**, no absolutas — es lo que hace legítima la
  comparación entre dos sistemas con parámetros distintos:

  | Observable normalizado | Juego real (n=5) | Simulador (n=40) |
  |:--|:--|:--|
  | $t_{\text{inflexión}} / t_{\text{final}}$ | … | 21.65 / 109.12 = 0.198 |
  | Cuota del líder en el turno de inflexión | 0.5 por definición | 0.5 |
  | Nº de imperios eliminados al 50 % de la partida | … | … |
  | ¿La distribución de provincias diverge (bola de nieve) o converge? | … | diverge (O3, paso 3) |

- Declarar explícitamente el alcance: *"se contrasta el acuerdo **ordinal** de la dinámica,
  no la coincidencia numérica; con n = 5 no se afirma significancia estadística"*.
  Esta salvedad es lo que separa una validación honesta de una tabla decorativa, y es
  defendible ante cualquier pregunta.

**(c) Docking contra el clon Java** — el informe §4.1 ya marca "comparación con otros
modelos: **parcial**". Convertirlo en total es barato: `src/sim/BatchRunner` ya juega 100
partidas IA contra IA y exporta CSV. Comparar la **forma** de las dos distribuciones de
duración y la concentración de victorias entre dos modelos del mismo sistema construidos
con paradigmas distintos (eventos discretos vs. incremento fijo) es *model-to-model
validation*, una técnica reconocida, y el repositorio ya tiene las dos mitades.

---

## Paso 7 · B7 — Hacer visible al equipo

1. **Commits de Arturo.** El reparto natural de lo que queda: figuras (paso 5), protocolo y
   ejecución de la comparación con el juego real (paso 6b), redacción de §4.1.1 y §4.5.
   Que los haga y los suba **con su propia cuenta**.
2. Si algún trabajo ya hecho fue conjunto, reflejarlo con
   `Co-Authored-By: Nombre <correo>` en commits futuros. **No reescribir el historial
   pasado**: falsear autoría es peor que tener poca.
3. Unificar las tres identidades de git de Leonardo (`git config user.name` / `user.email`)
   para los commits que quedan.
4. Añadir `CONTRIBUCIONES.md` en la raíz y un anexo breve en el informe con el reparto real
   por capítulo y por módulo.

---

## Paso 8 · B8 — Entregar

En este orden, y solo cuando 1–7 estén cerrados:

```bash
# 1. Regenerar TODOS los resultados con el código ya corregido
python3 -m sim --lote 40
python3 -m sim --barrido            # los cuatro factores

# 2. Actualizar en PARCIAL3.md cada cifra de §4.2, §4.3, §4.4 y las nuevas §4.1.1 y §4.5
# 3. Regenerar el PDF
bash docs/parcial3/.build/generar-pdf.sh

# 4. Verificación final
python3 tests/test_traza_dorada.py && python3 tests/test_fronteras.py

# 5. Publicar
git push origin main

# 6. Subir docs/parcial3/PARCIAL3.pdf a Moodle
```

**Aviso:** el paso 3 (arreglo de E5) invalida todas las cifras publicadas. Regenerar los
CSV **antes** de tocar el texto del informe, y regenerar el PDF **después** de tocarlo.

---

# Parte III — Checklist de entrega

### Simulador
- [ ] B3: predicado de E5 conforme a la tabla 4.3; sin dobles conquistas en la traza
- [ ] B2: `exportar_series_csv` + `resultados/p3/series_referencia.csv`
- [ ] B1: O3 (pendiente + dos correlaciones) calculado, en CSV y con IC en el resumen
- [ ] B4: barrido genérico + `escala_gamma_atq` + los 4 factores de §6.3 barridos
- [ ] Traza dorada 44/44 y fronteras 34/34 tras todos los cambios
- [ ] Las 5 fases consecutivas (`--turnos 5`) siguen ejecutando limpio *(ya cumple)*

### Informe
- [ ] §4.1 fila "gráficas operacionales" verdadera (o corregida)
- [ ] §4.1.1 tabla de correspondencia estructural con el juego real
- [ ] §4.2/4.3/4.4 con las cifras regeneradas
- [ ] §4.3 con **O1–O5 completo** (hoy falta O3)
- [ ] §4.5 con los tres barridos nuevos y el veredicto sobre cada predicción de §6.3
- [ ] Nota sobre el defecto de E5 detectado y corregido *(argumento fuerte de defensa)*
- [ ] 5 figuras integradas
- [ ] Anexo de reparto de trabajo
- [ ] PDF regenerado **al final**
- [ ] PDF subido a Moodle

### Repositorio
- [ ] Identidad de git unificada
- [ ] Commits de Arturo con su propia cuenta
- [ ] `CONTRIBUCIONES.md`
- [ ] **`git push origin main`** ← bloqueante

### Defensa
- [ ] `DEFENSA.md` actualizado con O3, los barridos nuevos y el arreglo de E5
- [ ] Ensayo cronometrado (8 min de demo) en la máquina que se va a compartir
- [ ] Respuesta ensayada a *"¿por qué no compararon con el juego real?"* — `DEFENSA.md:118`,
      ahora reforzada con la tabla §4.1.1 y, si se hace, con la comparación ordinal

---

# Anexo — Verificación requisito ↔ código

Contrastado uno a uno; todo lo listado aquí **ya cumple**.

### Las 13 funciones de la tabla 6.2

| Función | Ecuación | Implementada en |
|:--|:--|:--|
| $\mathcal{F}_1$ `calcularIngreso` | (3.1) | `sim/economia.py` |
| $\mathcal{F}_2$ `DecidirReclutamiento` | (3.11) | `sim/militar.py` + `sim/agentes.py` |
| $\mathcal{F}_3$ `SeleccionarObjetivos` | $\gamma_{atq}$ | `agentes.seleccionar_objetivo:126` |
| $\mathcal{F}_4$ `CosteMovimiento` | (3.13) | `sim/militar.py` |
| $\mathcal{F}_5$ `moral` (techo) | (3.27) | `sim/militar.py` |
| $\mathcal{F}_6$ `Terreno` | matriz $\mathcal{T}$ | `parametros.Terreno` |
| $\mathcal{F}_7$ `Aleatorio` | (3.23)–(3.26) | `sim/azar.py` (LCG 48 bits + triangular inversa) |
| $\mathcal{F}_8$ `Fortificacion` | (3.16) | `sim/militar.py` |
| $\mathcal{F}_9$ `RegenerarMoral` | (3.28) | `sim/militar.py`, E9 |
| $\mathcal{F}_{10}$ `ActualizarPoderMilitar` | (3.12) | `militar.poder_militar` |
| $\mathcal{F}_{11}$ `SeleccionarEstrategia` | §2.4.9 | `agentes.PARAMETROS_ESTRATEGIA:42` |
| $\mathcal{F}_{12}$ `evaluarDiplomacia` | (3.30)–(3.32) | `sim/diplomacia.py` |
| $\mathcal{F}_{13}$ función de fase | (4.1)–(4.4) | `sim/reloj.py` |

### Los 10 eventos

`e1_inicio_turno:75` · `e2_planificacion:117` · `e3_movimiento:138` ·
`e4_resolucion_combate:179` · `e5_conquista:218` · `e6_eliminacion:255` ·
E7/E8 unificados en `TipoEvento.DIPLOMACIA` + `sim/diplomacia.py` ·
`e9_fin_turno:271` · `e10_fin_juego:309`. Todos en `sim/eventos.py`.

### Predicados de validez (tabla 4.3)

E2 ✅ · E3 ✅ (incluye la guarda diplomática, reparación D1) · E4 ✅ · **E5 ⚠️ brecha B3** ·
E6 ✅ · E10 ✅.

### Reparaciones D1–D8 del Parcial II §1.3

D1 guarda diplomática ✅ · D2 E7/E8 con pseudocódigo ✅ · D3 función de llegada por partes
✅ (`reloj.funcion_llegada`, con `assert DELTA == 746_000`) · **D4 orden total ✅ pero con
la instancia residual de E5 (B3)** · D5 bucles B1 y B3 ✅ (`diplomacia.py:46` cierra B3) ·
D6 desgaste y techo de moral ✅ · D7 condiciones de frontera ✅ (34/34) ·
D8 guarnición y ejército separados ✅ (`eventos.py:150`, comentado como tal).

### Anexo A — los 35 parámetros

`sim/parametros.py` los declara **todos con el valor exacto del anexo**, con su unidad y su
procedencia `[D]`/`[M]`/`[C]`. Verificados: ι=0.01 · β_φ=0.05 · c_adm=2.0 · c_up=0.05 ·
c_u=1.5 · c_φ=40 · θmax=150 · θ₀=50 · η_θ=0.06 · η_w=2.0 · η_n=0.5 · n\*=8 · η_r=1.5 ·
D\*=60 · ψ=0.4 · μmin=0.40 · λ_d=0.06 · ρ_μ=0.10 · γ_μ=0.50 · K_B=0.70 · β_F=0.15 ·
Φmax=4 · Fmin=5 · g_ref=50 · (a,c,b)=(0.8,1.0,1.2) · v_a=1.5 · g_ret=30 · A_max=4 ·
g_L=0.01 · L_max=20000 · ϱ=2 · Θ_V=0.60 · t_max=200 · θ_am=0.40 · ς_h=0.05.
ε=10⁻³ y Δ=0.746 viven en `sim/reloj.py` con la aserción de (4.3).

### Anexo C — escenario de referencia

`escenarios/referencia24.json`: N=24 ✅ · grado medio 3.50 ✅ · 10 LLA / 6 BOS / 4 MON /
4 COS ✅ · 4 imperios, uno por estrategia, 3 provincias cada uno ✅ · 12 neutrales ✅ ·
capitales p00/p05/p30/p35 ✅ · s₀=20 260 805 ✅.

### Tabla §2.4.9 — parámetros de estrategia

`agentes.PARAMETROS_ESTRATEGIA` reproduce la tabla exacta:
AGR (125, 0.90, 1.1, 1.2, 0.15, 0.05) · DEF (100, 0.60, 1.8, 2.5, 0.50, 0.40) ·
ECO (θ^eq, 0.30, 2.0, 3.0, 0.40, 0.25) · EQU (½(100+θ^eq), 0.70, 1.4, 1.8, 0.30, 0.20).
