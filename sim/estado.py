"""Vector de estado del sistema S(τ) — capítulo 2 del documento del Parcial II.

Las entidades reproducen §2.1: Imperio, Provincia, Ejército y Combate, más el
estado global de §2.1.5. Los dominios de §5.1 se hacen cumplir en los
mutadores: se **satura** (clamp), nunca se desborda ni se lanza excepción.

Reparación del defecto D8 (doble contabilidad de la fuerza): guarnición y
ejército son conceptos distintos y no intercambiables. La guarnición g_p es
fuerza estacionada permanente; el ejército es fuerza móvil persistente que NO
se disuelve al llegar a destino. Ambos defienden la provincia donde están
—ec. (3.12b)— y cada uno computa exactamente una vez en M_i —ec. (3.12)—.
"""

from __future__ import annotations

from collections import deque
from dataclasses import dataclass, field

from .azar import GeneradorLcg
from .parametros import EstadoDiplomatico, Parametros, Terreno


def clamp(x: float, lo: float, hi: float) -> float:
    return min(max(x, lo), hi)


@dataclass
class Provincia:
    """Entidad Provincia — §2.1.2."""

    id: str
    nombre: str
    terreno: Terreno                    # T_p, constante
    poblacion: float                    # L_p ∈ [0, L_max]
    adyacentes: set[str] = field(default_factory=set)  # V_p, constante
    propietario: str | None = None      # π_p; None = NEUTRAL
    fortificacion: int = 0              # φ_p ∈ {0..Φ_max}
    guarnicion: float = 0.0             # g_p ≥ 0
    descontento: float = 20.0           # D_p ∈ [0,100]
    en_conflicto: bool = False          # χ_p
    bajas_turno: float = 0.0            # β_p(t): acumulador consumido en E9

    @property
    def es_neutral(self) -> bool:
        return self.propietario is None


@dataclass
class Ejercito:
    """Entidad Ejército — §2.1.3. Fuerza móvil persistente."""

    id: int
    propietario: str                    # π_a, constante
    fuerza: float                       # F_a ≥ 0
    ubicacion: str                      # u_a
    moral: float = 1.0                  # μ_a ∈ [μ_min, 1]
    en_combate: bool = False            # ω_a


@dataclass
class Combate:
    """Entidad temporal — §2.1.4. Se crea en E3 y se destruye al final de E4."""

    id: int
    provincia: str                      # p_C
    atacante: int                       # a_C (id de ejército)
    defensa: float                      # g_C, la fuerza defensiva al llegar
    tau_inicio: int                     # τ_C
    defensor: str | None = None         # propietario en el momento de crearse


@dataclass
class Imperio:
    """Entidad Imperio — §2.1.1."""

    id: str
    nombre: str
    estrategia: str                     # σ_i ∈ {AGR, DEF, ECO, EQU}
    oro: float = 200.0                  # G_i ≥ 0
    tasa: float = 100.0                 # θ_i ∈ [0, θ_max]
    capital: str | None = None          # c_i
    activo: bool = True                 # α_i
    poder_militar: float = 0.0          # M_i, recalculado en E9
    relaciones: dict[str, EstadoDiplomatico] = field(default_factory=dict)


class Estado:
    """Estado completo del sistema, S(τ) de §2.1.5.

    Concentra además las **variables auxiliares** de §2.2, que se calculan a
    demanda y no se almacenan: cuota territorial, líder, frontera, dispersión
    de guarniciones, fuerza defensiva y distancia geodésica.
    """

    def __init__(
        self,
        nombre: str,
        parametros: Parametros,
        provincias: dict[str, Provincia],
        imperios: dict[str, Imperio],
    ):
        self.nombre = nombre
        self.p = parametros
        self.provincias = provincias
        self.imperios = imperios
        self.ejercitos: dict[int, Ejercito] = {}
        self.combates: dict[int, Combate] = {}
        # Ejércitos con una llegada pendiente en la LEF.
        #
        # DESVIACIÓN DECLARADA respecto al pseudocódigo de §4.5: E2 itera sobre
        # todos los ejércitos sin comprobar si alguno está en tránsito. Como el
        # coste de cruzar COSTA, BOSQUE o MONTAÑA supera Δ = 0.746, la llegada
        # se programa para un turno posterior, y en el turno intermedio E2
        # volvería a emitir una orden para el mismo ejército —destacando otra
        # vez f_gua·F_a de retaguardia y drenando su fuerza—. El modelo
        # conceptual no declara ningún atributo "en tránsito" (tiene ω_a
        # `enCombate` pero no su equivalente para el movimiento). Se repara
        # añadiendo esta guarda, análoga a la que el Parcial II añadió al
        # Parcial I. Documentado en la tabla de trazabilidad del informe.
        self.en_transito: set[int] = set()
        # Única fuente de aleatoriedad del modelo (§3.5). Sustituible por un
        # GeneradorFijo para reproducir la traza de escritorio de §4.7.
        self.rng = GeneradorLcg(parametros.semilla)

        self.turno: int = 1              # t
        self.tau: int = 0                # τ (micro-fases)
        self.fin_juego: bool = False     # Z
        self.num_combates: int = 0       # ν
        self.bajas_totales: float = 0.0  # β
        self.ganador: str | None = None
        self.turno_eliminacion: dict[str, int] = {}

        self._siguiente_ejercito = 1
        self._siguiente_combate = 1
        self._cache_distancias: dict[str, dict[str, int]] = {}

    # ------------------------------------------------------------------ mapa

    @property
    def N(self) -> int:
        """Tamaño del mapa en provincias."""
        return len(self.provincias)

    def provincia(self, pid: str) -> Provincia:
        try:
            return self.provincias[pid]
        except KeyError:
            raise KeyError(f"Provincia desconocida: '{pid}'") from None

    def distancia(self, origen: str, destino: str) -> int:
        """d(p,q): distancia geodésica en el grafo del mapa, en provincias.

        BFS con memoria por origen. Devuelve un valor grande si no hay camino,
        de modo que el techo de moral (3.27) sature en μ_min — caso (f) de §5.3.
        """
        if origen not in self._cache_distancias:
            dist = {origen: 0}
            cola = deque([origen])
            while cola:
                x = cola.popleft()
                for y in sorted(self.provincia(x).adyacentes):
                    if y not in dist:
                        dist[y] = dist[x] + 1
                        cola.append(y)
            self._cache_distancias[origen] = dist
        return self._cache_distancias[origen].get(destino, 10**6)

    # -------------------------------------------------------------- imperios

    def imperio(self, iid: str) -> Imperio:
        try:
            return self.imperios[iid]
        except KeyError:
            raise KeyError(f"Imperio desconocido: '{iid}'") from None

    def imperios_activos(self) -> list[Imperio]:
        """I^act(τ), en orden determinista por id."""
        return [self.imperios[k] for k in sorted(self.imperios) if self.imperios[k].activo]

    @property
    def m(self) -> int:
        """Número de imperios activos."""
        return len(self.imperios_activos())

    def provincias_de(self, iid: str) -> list[Provincia]:
        """P_i(τ), en orden determinista por id."""
        return [self.provincias[k] for k in sorted(self.provincias)
                if self.provincias[k].propietario == iid]

    def n(self, iid: str) -> int:
        """n_i: cardinal de P_i."""
        return sum(1 for p in self.provincias.values() if p.propietario == iid)

    def cuota(self, iid: str) -> float:
        """q_i = n_i / N — §2.2."""
        return self.n(iid) / self.N

    def lider(self) -> str | None:
        """ℓ = argmax_i q_i entre los activos.

        Desempate por **menor identificador** — caso (e) de §5.3. La regla es
        arbitraria pero determinista: sin ella la simulación no sería
        reproducible.
        """
        activos = self.imperios_activos()  # ya vienen ordenados por id ascendente
        if not activos:
            return None
        # max() devuelve el PRIMER elemento maximal: al recorrer en orden
        # ascendente de id, el empate lo gana el de menor identificador.
        return max(activos, key=lambda i: self.n(i.id)).id

    def ejercitos_de(self, iid: str) -> list[Ejercito]:
        """A_i(τ), en orden determinista por id."""
        return [self.ejercitos[k] for k in sorted(self.ejercitos)
                if self.ejercitos[k].propietario == iid]

    def frontera(self, iid: str) -> list[Provincia]:
        """∂P_i = {p ∈ P_i : ∃ q ∈ V_p con π_q ≠ i} — §2.2."""
        propias = self.provincias_de(iid)
        return [p for p in propias
                if any(self.provincia(q).propietario != iid for q in p.adyacentes)]

    def dispersion_guarniciones(self, iid: str) -> float:
        """ḡ_i = (Σ_{p∈∂P_i} g_p) / B_i — ec. (3.33), brazo B1a."""
        frontera = self.frontera(iid)
        if not frontera:
            return 0.0
        return sum(p.guarnicion for p in frontera) / len(frontera)

    def vulnerabilidad(self, iid: str) -> float:
        """Vul_i = 1 − ḡ_i/(ḡ_i + g_ref) ∈ (0,1) — ec. (3.34)."""
        g = self.dispersion_guarniciones(iid)
        return 1.0 - g / (g + self.p.guarnicion_referencia)

    # ------------------------------------------------------------ diplomacia

    def relacion(self, a: str, b: str) -> EstadoDiplomatico:
        """δ_ab. No definida para a == b."""
        if a == b:
            raise ValueError("δ_ii no está definida")
        return self.imperio(a).relaciones.get(b, EstadoDiplomatico.PAZ)

    def fijar_relacion(self, a: str, b: str, estado: EstadoDiplomatico) -> None:
        """Fija δ_ab manteniendo la simetría: todo evento que la modifique
        debe actualizar ambas entradas (nota de §2.1.1)."""
        if a == b:
            raise ValueError("δ_ii no está definida")
        self.imperio(a).relaciones[b] = estado
        self.imperio(b).relaciones[a] = estado

    def en_guerra(self, iid: str) -> bool:
        """¿Existe algún j con δ_ij = GUERRA? Alimenta η_w en (3.6)."""
        return any(e is EstadoDiplomatico.GUERRA
                   for e in self.imperio(iid).relaciones.values())

    def son_adyacentes(self, a: str, b: str) -> bool:
        """Adj(i,j) ⟺ ∃ p ∈ P_i, q ∈ P_j con q ∈ V_p — §3.7.2."""
        for p in self.provincias_de(a):
            for q in p.adyacentes:
                if self.provincia(q).propietario == b:
                    return True
        return False

    # ---------------------------------------------------------- ciclo de vida

    def crear_ejercito(self, propietario: str, fuerza: float, ubicacion: str) -> Ejercito:
        e = Ejercito(
            id=self._siguiente_ejercito,
            propietario=propietario,
            fuerza=fuerza,
            ubicacion=ubicacion,
            moral=self.p.moral_inicial,
        )
        self.ejercitos[e.id] = e
        self._siguiente_ejercito += 1
        return e

    def destruir_ejercito(self, eid: int) -> None:
        self.ejercitos.pop(eid, None)
        self.en_transito.discard(eid)

    def crear_combate(self, provincia: str, atacante: int, defensa: float) -> Combate:
        c = Combate(
            id=self._siguiente_combate,
            provincia=provincia,
            atacante=atacante,
            defensa=defensa,
            tau_inicio=self.tau,
            defensor=self.provincia(provincia).propietario,
        )
        self.combates[c.id] = c
        self._siguiente_combate += 1
        return c

    def destruir_combate(self, cid: int) -> None:
        self.combates.pop(cid, None)
