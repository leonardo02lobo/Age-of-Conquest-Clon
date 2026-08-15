## ADDED Requirements

### Requirement: Informe Técnico Final en PDF

El proyecto SHALL producir un documento académico formal en PDF, generado con la cadena de
construcción de `docs/parcial2/.build/`, que contenga las cuatro secciones exigidas por el enunciado:
descripción general del alcance del simulador, arquitectura del software, validación y calibración, y
análisis de casos borde.

#### Scenario: Generación reproducible del PDF
- **WHEN** se ejecuta el guion de construcción sobre el fuente Markdown del informe
- **THEN** se produce el PDF con fórmulas KaTeX y diagramas Mermaid renderizados, sin dependencias de red

#### Scenario: Sección de alcance
- **WHEN** se lee la sección de alcance
- **THEN** declara qué subsistemas del Parcial II implementa el simulador, qué mecánicas del juego real quedan fuera y por qué, y en qué se diferencia del clon M1–M6 que también vive en el repositorio

#### Scenario: Sección de arquitectura
- **WHEN** se lee la sección de arquitectura
- **THEN** describe los paquetes, las clases principales, la estructura de la LEF, el flujo de un evento desde la extracción hasta la recolección de estadísticas, y el manejo de datos (formato de escenario y exportación CSV)

#### Scenario: Sección de validación y calibración
- **WHEN** se lee la sección de validación
- **THEN** presenta la verificación contra el documento (traza dorada y teoremas), la validación estructural contra el juego real y la calibración de los parámetros `[C]`, con tablas y gráficas

#### Scenario: Sección de casos borde
- **WHEN** se lee la sección de casos borde
- **THEN** cubre al menos impuestos al máximo, moral en el mínimo y bancarrota, cada uno con la ecuación que lo gobierna, la traza del simulador y la interpretación del comportamiento

### Requirement: Tabla de trazabilidad documento ↔ código

El informe SHALL incluir una tabla que asocie cada ecuación, evento y función del documento del
Parcial II con el fichero y el método del simulador que la implementa.

#### Scenario: Cobertura de la trazabilidad
- **WHEN** se revisa la tabla
- **THEN** las trece funciones $\mathcal{F}_1$–$\mathcal{F}_{13}$, los diez eventos E1–E10 y todas las ecuaciones numeradas tienen una entrada con su ubicación exacta en el código

#### Scenario: Divergencias declaradas
- **WHEN** la implementación se aparta del documento en algún punto
- **THEN** la tabla lo marca explícitamente e indica el motivo

### Requirement: Material de la demostración en vivo

El proyecto SHALL preparar un guion de demostración reproducible para la defensa sincrónica, con los
comandos exactos, el escenario y la semilla que se usarán en vivo.

#### Scenario: Guion de la demostración
- **WHEN** se sigue el guion
- **THEN** cubre el arranque del simulador, la ejecución de al menos cinco fases consecutivas, la entrada manual de variables, la demostración de al menos un caso borde y la ejecución de un experimento por lotes con su salida

#### Scenario: Reproducibilidad de la demostración
- **WHEN** se ejecuta el guion dos veces con la misma semilla
- **THEN** se obtiene exactamente la misma salida, de modo que lo mostrado en vivo coincida con lo reportado en el informe

#### Scenario: Duración acotada
- **WHEN** se ensaya el guion completo
- **THEN** cada paso tiene un tiempo estimado y el conjunto cabe en la duración prevista de la sesión

### Requirement: Preparación de la sustentación técnica

El proyecto SHALL preparar un anexo de defensa que anticipe las preguntas previsibles sobre el
código y las decisiones de diseño.

#### Scenario: Justificación de las decisiones de diseño
- **WHEN** se revisa el anexo
- **THEN** justifica al menos: por qué eventos discretos y no incremento fijo, por qué la ley lineal de Lanchester y no la cuadrática, por qué la distribución triangular, por qué el orden de fases dentro del turno, y por qué el motor operacional es código nuevo en lugar de una refactorización del clon existente

#### Scenario: Recorrido del código
- **WHEN** se prepara el recorrido del código para la sustentación
- **THEN** identifica los ficheros y métodos que se mostrarán para cada subsistema, siguiendo la tabla de trazabilidad

### Requirement: Historial de contribuciones del equipo

El repositorio SHALL reflejar la contribución de ambos integrantes del equipo, dado que el enunciado
evalúa explícitamente el historial de contribuciones.

#### Scenario: Reparto por subsistema
- **WHEN** se planifica la implementación
- **THEN** cada subsistema queda asignado nominalmente a un integrante y se registra el reparto

#### Scenario: Autoría en los commits
- **WHEN** se inspecciona el historial de la fase del Parcial III
- **THEN** los commits reflejan la autoría real de cada integrante sobre el trabajo que realizó
