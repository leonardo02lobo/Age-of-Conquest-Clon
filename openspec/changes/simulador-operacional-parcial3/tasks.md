> **REPLANIFICADO — entrega hoy, implementación en Python, sin ejecutable de AoC IV.**
> Los `specs/` siguen siendo la fuente de requisitos (son independientes del lenguaje).
> `design.md` describe una estructura Java que se sustituye por el equivalente Python:
> un módulo por capítulo del documento del Parcial II, bajo `sim/`.
>
> **Recortes deliberados frente al plan original** (declarados en el informe):
> factorial 2⁴ → un barrido + réplicas con IC · suite por ecuación → solo traza dorada
> + fronteras · REPL de 6 comandos → flags de CLI · trazabilidad autogenerada → a mano.

## 1. Núcleo del modelo

- [x] 1.1 `sim/parametros.py` — los ~35 parámetros del capítulo 2 con unidad y procedencia `[D]`/`[M]`/`[C]`, y la matriz de terreno §2.4.6
- [x] 1.2 `sim/azar.py` — LCG de 48 bits (3.26), transformada inversa triangular (3.25) y generador de secuencia fija para pruebas
- [x] 1.3 `sim/reloj.py` — reloj en micro-fases, tabla de fases y prioridades (4.1), evento, LEF sobre `heapq` con clave (4.2), función de llegada (4.4)
- [x] 1.4 `sim/estado.py` — Imperio, Provincia, Ejercito, Combate, Estado y auxiliares ($q_i$, $\ell$, $\partial\mathcal{P}_i$, $\mathcal{D}_p$, $M_i$, $d(p,q)$)
- [x] 1.5 `sim/escenario.py` — generador determinista del mapa de 24 provincias del Anexo C, validación y carga JSON; congelar `escenarios/referencia24.json`

## 2. Subsistemas

- [x] 2.1 `sim/economia.py` — renta (3.1)–(3.3), tesoro (3.4)–(3.5), insolvencia (5.2), descontento (3.6)–(3.9), población (3.10)/(5.5)
- [x] 2.2 `sim/militar.py` — poder militar (3.12), defensa (3.12b), reparto (3.12c), coste de movimiento (3.13), reclutamiento (3.11), combate (3.14)–(3.21), moral (3.27)–(3.29)
- [x] 2.3 `sim/diplomacia.py` — guarda diplomática §3.7.1, guerra (3.30), alianzas (3.31)–(3.32)
- [x] 2.4 `sim/agentes.py` — las 4 estrategias §2.4.9, política fiscal, fortificación, selección de objetivos y BFS de frontera §4.6.5

## 3. Motor de eventos

- [x] 3.1 `sim/eventos.py` — E1, E7/E8, E2, E3, E4, E5, E6, E9, E10 con los pseudocódigos de §4.5
- [x] 3.2 `sim/eventos.py` — predicados de validez (tabla 4.3) y despachador con el bucle de §4.1
- [x] 3.3 `sim/recolector.py` — métricas O1–O5 y traza legible del turno

## 4. Verificación

- [x] 4.1 `tests/test_traza_dorada.py` — reproducción exacta de la traza de escritorio §4.7 con $R_1=0.7314$, $R_2=0.2891$
- [ ] 4.2 `tests/test_fronteras.py` — dominios §5.1 y casos degenerados §5.3
- [x] 4.3 Partida completa reproducible sobre `referencia24.json` con $s_0 = 20260805$

## 5. Interfaz y experimentos

- [x] 5.1 `sim/cli.py` — `--turnos`, `--interactivo`, `--partida`, `--caso`, `--semilla`, `--traza-dorada`
- [x] 5.2 `sim/casos_borde.py` — impuestos al máximo, bancarrota, moral al mínimo, provincia indefensa, pérdida de capital
- [x] 5.3 `sim/lote.py` — réplicas con semillas apareadas, barrido de $K_B$, exportación CSV e intervalos de confianza
- [x] 5.4 Validación predictiva: contrastar $n^{\max}_{\text{guerra}}=13 < 15$ (§3.2.4) contra las réplicas

## 6. Informe y defensa

- [x] 6.1 `docs/parcial3/` con el pipeline de `docs/parcial2/.build/` copiado y funcionando
- [x] 6.2 Secciones de arquitectura y trazabilidad documento ↔ código
- [x] 6.3 Sección de validación (verificación, condiciones extremas, degenerados, predictiva, validez interna, sensibilidad) y declaración del alcance de la validación histórica
- [x] 6.4 Sección de casos borde con las trazas reales del simulador
- [x] 6.5 Sección de alcance (se escribe **al final**, describiendo lo que existe) y generación del PDF
- [x] 6.6 Guion de demostración en vivo y anexo de justificación de decisiones
