"""Escenario de referencia y su validación — Anexo C del Parcial II.

El Anexo C especifica el escenario mínimo de verificación:

    Mapa ......... N = 24 provincias, grafo conexo, grado medio ≈ 3.5
    Terrenos ..... 10 LLANURA, 6 BOSQUE, 4 MONTAÑA, 4 COSTA
    Imperios ..... 4, uno por estrategia, 3 provincias cada uno
    Neutrales .... 12
    Inicial ...... G⁰=200, un ejército F⁰=100 en la capital, D⁰=20 en todas,
                   φ=1 solo en las capitales, δ_ij = PAZ
    Semilla ...... s₀ = 20 260 805

El documento afirma que la lista concreta de adyacencias, terrenos y
poblaciones "acompaña a este documento como fichero de datos". Ese fichero no
existía, de modo que el mapa se **genera aquí de forma determinista**
respetando todas las restricciones declaradas, y se congela en
`escenarios/referencia24.json`. La topología concreta es por tanto una
decisión de esta fase, no un dato heredado, y así se declara en el informe.

Topología adoptada: rejilla 4×6 con adyacencia ortogonal (38 aristas) más
4 diagonales, lo que da 42 aristas y grado medio exacto 2·42/24 = 3.5.
"""

from __future__ import annotations

import json
from pathlib import Path

from .estado import Estado, Imperio, Provincia
from .parametros import EstadoDiplomatico, Parametros, Terreno

FILAS, COLUMNAS = 4, 6

# Terrenos por celda (fila, columna) — reparto 10 LLA / 6 BOS / 4 MON / 4 COS.
# Las capitales de los cuatro imperios caen en LLANURA, por simetría de partida.
_TERRENOS = [
    ["LLA", "BOS", "MON", "COS", "BOS", "LLA"],
    ["LLA", "COS", "LLA", "LLA", "MON", "BOS"],
    ["BOS", "LLA", "MON", "BOS", "COS", "LLA"],
    ["LLA", "MON", "BOS", "COS", "LLA", "LLA"],
]

# Aristas diagonales añadidas para alcanzar el grado medio 3.5 del Anexo C.
_DIAGONALES = [((0, 2), (1, 3)), ((1, 1), (2, 2)), ((2, 3), (3, 4)), ((1, 4), (2, 3))]

# Un imperio por estrategia, con 3 provincias en esquinas opuestas.
_IMPERIOS = [
    ("aquilonia", "Aquilonia", "AGR", (0, 0), [(0, 0), (0, 1), (1, 0)]),
    ("borealis", "Borealis", "DEF", (0, 5), [(0, 5), (0, 4), (1, 5)]),
    ("cimeria", "Cimeria", "ECO", (3, 0), [(3, 0), (3, 1), (2, 0)]),
    ("dorania", "Dorania", "EQU", (3, 5), [(3, 5), (3, 4), (2, 5)]),
]


def _id(fila: int, col: int) -> str:
    return f"p{fila}{col}"


def generar_referencia24() -> dict:
    """Construye el escenario del Anexo C como diccionario serializable.

    Las poblaciones iniciales caen en [1000, 10000] y se derivan de la posición
    mediante una fórmula fija: el mapa es reproducible sin depender del
    generador aleatorio de la simulación.
    """
    provincias = []
    for f in range(FILAS):
        for c in range(COLUMNAS):
            adyacentes = []
            for df, dc in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                nf, nc = f + df, c + dc
                if 0 <= nf < FILAS and 0 <= nc < COLUMNAS:
                    adyacentes.append(_id(nf, nc))
            for a, b in _DIAGONALES:
                if (f, c) == a:
                    adyacentes.append(_id(*b))
                elif (f, c) == b:
                    adyacentes.append(_id(*a))
            provincias.append({
                "id": _id(f, c),
                "nombre": f"Provincia {f}{c}",
                "terreno": _TERRENOS[f][c],
                "poblacion": 1000 + ((f * COLUMNAS + c) * 1373) % 9001,
                "adyacentes": sorted(set(adyacentes)),
            })

    imperios = []
    for iid, nombre, estrategia, capital, celdas in _IMPERIOS:
        imperios.append({
            "id": iid,
            "nombre": nombre,
            "estrategia": estrategia,
            "capital": _id(*capital),
            "provincias": [_id(*c) for c in celdas],
        })

    return {
        "nombre": "Referencia 24 (Anexo C)",
        "provincias": provincias,
        "imperios": imperios,
    }


def guardar(datos: dict, ruta: Path) -> None:
    ruta.parent.mkdir(parents=True, exist_ok=True)
    ruta.write_text(json.dumps(datos, indent=2, ensure_ascii=False), encoding="utf-8")


def validar(datos: dict) -> None:
    """Validación del escenario. Rechaza con mensaje descriptivo.

    Comprueba unicidad de ids, existencia de las adyacencias, conexidad del
    grafo (caso (f) de §5.3: una componente aislada sería inalcanzable),
    terrenos válidos, poblaciones en rango, y que cada capital pertenezca al
    territorio de su imperio.
    """
    provincias = datos["provincias"]
    ids = [p["id"] for p in provincias]
    if len(ids) != len(set(ids)):
        raise ValueError("Hay ids de provincia duplicados")
    conjunto = set(ids)

    for p in provincias:
        Terreno.desde_codigo(p["terreno"])  # lanza si es desconocido
        if not 1000 <= p["poblacion"] <= 10_000:
            raise ValueError(
                f"Población inicial de '{p['id']}' fuera de [1000, 10000]: {p['poblacion']}")
        for a in p["adyacentes"]:
            if a not in conjunto:
                raise ValueError(f"'{p['id']}' es adyacente a '{a}', que no existe")
            if a == p["id"]:
                raise ValueError(f"'{p['id']}' no puede ser adyacente a sí misma")

    # Conexidad: BFS desde la primera provincia debe alcanzarlas todas.
    vecinos: dict[str, set[str]] = {i: set() for i in ids}
    for p in provincias:
        for a in p["adyacentes"]:
            vecinos[p["id"]].add(a)
            vecinos[a].add(p["id"])  # el cargador simetriza las adyacencias
    visto = {ids[0]}
    pila = [ids[0]]
    while pila:
        x = pila.pop()
        for y in vecinos[x]:
            if y not in visto:
                visto.add(y)
                pila.append(y)
    if len(visto) != len(ids):
        raise ValueError(
            f"El mapa no es conexo: inalcanzables {sorted(conjunto - visto)}")

    asignadas: set[str] = set()
    for imp in datos["imperios"]:
        if imp["estrategia"] not in ("AGR", "DEF", "ECO", "EQU"):
            raise ValueError(f"Estrategia desconocida en '{imp['id']}': {imp['estrategia']}")
        if imp["capital"] not in imp["provincias"]:
            raise ValueError(
                f"La capital '{imp['capital']}' no pertenece al territorio de '{imp['id']}'")
        for pid in imp["provincias"]:
            if pid not in conjunto:
                raise ValueError(f"'{imp['id']}' reclama la provincia inexistente '{pid}'")
            if pid in asignadas:
                raise ValueError(f"La provincia '{pid}' está asignada a dos imperios")
            asignadas.add(pid)


def construir(datos: dict, parametros: Parametros | None = None) -> Estado:
    """Procedimiento `Inicializar()` de §4.5, a partir de un escenario validado."""
    validar(datos)
    par = parametros or Parametros()

    provincias: dict[str, Provincia] = {}
    for d in datos["provincias"]:
        provincias[d["id"]] = Provincia(
            id=d["id"],
            nombre=d.get("nombre", d["id"]),
            terreno=Terreno.desde_codigo(d["terreno"]),
            poblacion=float(d["poblacion"]),
            adyacentes=set(d["adyacentes"]),
            descontento=par.descontento_inicial,
        )
    # Las adyacencias se declaran en una dirección; aquí se simetrizan.
    for p in provincias.values():
        for a in p.adyacentes:
            provincias[a].adyacentes.add(p.id)

    imperios: dict[str, Imperio] = {}
    for d in datos["imperios"]:
        imperios[d["id"]] = Imperio(
            id=d["id"],
            nombre=d.get("nombre", d["id"]),
            estrategia=d["estrategia"],
            oro=par.oro_inicial,
            capital=d["capital"],
        )

    estado = Estado(datos.get("nombre", "escenario"), par, provincias, imperios)

    for d in datos["imperios"]:
        imp = imperios[d["id"]]
        for pid in d["provincias"]:
            provincias[pid].propietario = imp.id
        provincias[imp.capital].fortificacion = 1   # la capital nace fortificada
        estado.crear_ejercito(imp.id, par.fuerza_inicial, imp.capital)
        imp.tasa = par.tasa_neutra
        for otro in imperios:
            if otro != imp.id:
                imp.relaciones[otro] = EstadoDiplomatico.PAZ

    for imp in imperios.values():
        imp.poder_militar = sum(e.fuerza for e in estado.ejercitos_de(imp.id))

    return estado


def cargar(ruta: Path, parametros: Parametros | None = None) -> Estado:
    """Carga un escenario desde JSON y construye el estado inicial."""
    return construir(json.loads(Path(ruta).read_text(encoding="utf-8")), parametros)


def cargar_referencia(parametros: Parametros | None = None) -> Estado:
    """Escenario de referencia, del fichero congelado si existe."""
    ruta = Path(__file__).resolve().parent.parent / "escenarios" / "referencia24.json"
    if ruta.exists():
        return cargar(ruta, parametros)
    return construir(generar_referencia24(), parametros)
