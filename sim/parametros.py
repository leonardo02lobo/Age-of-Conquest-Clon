"""Parámetros fijos del modelo — capítulo 2 del documento del Parcial II.

Procedencia de cada parámetro:
  [D] documentado del juego real Age of Conquest IV
  [M] decisión de modelado del equipo
  [C] sujeto a calibración experimental

Todo símbolo que aparece en las ecuaciones de los capítulos 3, 4 y 5 está
declarado aquí o en `sim.estado`; ninguna ecuación introduce magnitudes nuevas.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum


class Terreno(Enum):
    """Tipo de terreno de una provincia — matriz T, §2.4.6.

    Cada entrada es (multiplicador de ataque, multiplicador de defensa,
    coste de cruce w). Los valores son simétricamente coherentes: el terreno
    que más penaliza el ataque es el que más favorece la defensa y el que más
    cuesta cruzar.  Procedencia [M][C].
    """

    LLANURA = ("LLA", 1.00, 1.00, 1.0)
    BOSQUE = ("BOS", 0.90, 1.15, 1.4)
    MONTANA = ("MON", 0.80, 1.30, 2.0)
    COSTA = ("COS", 0.95, 1.10, 1.2)

    def __init__(self, codigo: str, ataque: float, defensa: float, coste: float):
        self.codigo = codigo
        self.ataque = ataque    # T(T_p, ATQ)
        self.defensa = defensa  # T(T_p, DEF)
        self.coste = coste      # w(T_p), multiplicador de coste de cruce

    @classmethod
    def desde_codigo(cls, codigo: str) -> "Terreno":
        for t in cls:
            if t.codigo == codigo.upper():
                return t
        validos = ", ".join(t.codigo for t in cls)
        raise ValueError(f"Terreno desconocido: '{codigo}'. Válidos: {validos}")


class EstadoDiplomatico(Enum):
    """Relación δ_ij entre dos imperios. La matriz es simétrica por construcción."""

    PAZ = "PAZ"
    GUERRA = "GUERRA"
    ALIANZA = "ALIANZA"


@dataclass
class Parametros:
    """Tabla de parámetros del capítulo 2, con sus valores por defecto.

    Los marcados [C] son los factores del diseño de experimentos: se modifican
    por réplica sin tocar el código del motor.
    """

    # --- 2.4.1 Escenario e inicialización ---------------------------------
    oro_inicial: float = 200.0          # G⁰   oro                       [M][C]
    fuerza_inicial: float = 100.0       # F⁰   unidades de fuerza        [M][C]
    descontento_inicial: float = 20.0   # D⁰   puntos                    [M]
    moral_inicial: float = 1.0          # μ⁰   adimensional              [M]
    semilla: int = 20_260_805           # s₀                             [M]

    # --- 2.4.2 Subsistema económico ---------------------------------------
    iota: float = 0.01                  # ι    oro/(habitante·turno)     [M][C]
    beta_fort: float = 0.05             # β_φ  1/nivel                   [M]
    coste_admin: float = 2.0            # c_adm oro/(provincia·turno)    [M][C]
    coste_mantenimiento: float = 0.05   # c_up oro/(unidad·turno)        [M][C]
    coste_unidad: float = 1.5           # c_u  oro/unidad                [M][C]
    coste_fortificacion: float = 40.0   # c_φ  oro/nivel                 [M]
    tasa_maxima: float = 150.0          # θ_max %                        [M]
    tasa_neutra: float = 50.0           # θ₀   %                         [M]

    # --- 2.4.3 Descontento -------------------------------------------------
    eta_fiscal: float = 0.06            # η_θ  puntos/(turno·%)          [M][C]
    eta_guerra: float = 2.0             # η_w  puntos/turno              [M]
    eta_extension: float = 0.5          # η_n  puntos/(turno·provincia)  [M][C]
    umbral_admin: int = 8               # n*   provincias                [M][C]
    eta_recuperacion: float = 1.5       # η_r  puntos/turno              [M]
    umbral_fiscal: float = 60.0         # D*   puntos                    [M][C]
    psi: float = 0.4                    # ψ    adimensional              [M]

    # --- 2.4.4 Moral -------------------------------------------------------
    moral_minima: float = 0.40          # μ_min adimensional             [M]
    lambda_distancia: float = 0.06      # λ_d  1/provincia               [M][C]
    rho_moral: float = 0.10             # ρ_μ  1/turno                   [M]
    gamma_moral: float = 0.50           # γ_μ  adimensional              [M]

    # --- 2.4.5 Combate -----------------------------------------------------
    k_bajas: float = 0.70               # K_B  adimensional              [M][C]
    beta_defensa: float = 0.15          # β_F  1/nivel                   [D][M]
    fortificacion_maxima: int = 4       # Φ_max niveles                  [M]
    fuerza_minima: float = 5.0          # F_min unidades                 [M]
    guarnicion_referencia: float = 50.0  # g_ref unidades                [M]
    tri_a: float = 0.8                  # a_U  adimensional              [M][C]
    tri_c: float = 1.0                  # c_U  adimensional              [M][C]
    tri_b: float = 1.2                  # b_U  adimensional              [M][C]

    # --- 2.4.8 Movimiento, población y terminación -------------------------
    puntos_movimiento: float = 1.5      # v_a  provincias/turno          [M][C]
    guarnicion_reserva: float = 30.0    # g_ret unidades                 [M]
    ejercitos_maximos: int = 4          # A_max ejércitos                [M]
    crecimiento_poblacion: float = 0.01  # g_L  1/turno                  [M][C]
    poblacion_maxima: float = 20_000.0  # L_max habitantes               [M]
    habitantes_por_baja: float = 2.0    # ϱ    habitantes/unidad         [M]
    cuota_victoria: float = 0.60        # Θ_V  adimensional              [M][C]
    turnos_maximos: int = 200           # t_max turnos                   [M]
    cuota_amenaza: float = 0.40         # θ_am adimensional              [M][C]
    histeresis_alianza: float = 0.05    # ς_h  adimensional              [M]

    def copia(self, **cambios) -> "Parametros":
        """Devuelve una copia con los campos indicados sustituidos.

        Es el mecanismo del análisis de sensibilidad: cada variante de un
        experimento es una copia con un parámetro [C] distinto.
        """
        from dataclasses import replace

        return replace(self, **cambios)


# Procedencia declarada de cada parámetro, para la tabla del informe.
PROCEDENCIA: dict[str, str] = {
    "oro_inicial": "[M][C]", "fuerza_inicial": "[M][C]",
    "descontento_inicial": "[M]", "moral_inicial": "[M]", "semilla": "[M]",
    "iota": "[M][C]", "beta_fort": "[M]", "coste_admin": "[M][C]",
    "coste_mantenimiento": "[M][C]", "coste_unidad": "[M][C]",
    "coste_fortificacion": "[M]", "tasa_maxima": "[M]", "tasa_neutra": "[M]",
    "eta_fiscal": "[M][C]", "eta_guerra": "[M]", "eta_extension": "[M][C]",
    "umbral_admin": "[M][C]", "eta_recuperacion": "[M]",
    "umbral_fiscal": "[M][C]", "psi": "[M]",
    "moral_minima": "[M]", "lambda_distancia": "[M][C]", "rho_moral": "[M]",
    "gamma_moral": "[M]",
    "k_bajas": "[M][C]", "beta_defensa": "[D][M]",
    "fortificacion_maxima": "[M]", "fuerza_minima": "[M]",
    "guarnicion_referencia": "[M]", "tri_a": "[M][C]", "tri_c": "[M][C]",
    "tri_b": "[M][C]",
    "puntos_movimiento": "[M][C]", "guarnicion_reserva": "[M]",
    "ejercitos_maximos": "[M]", "crecimiento_poblacion": "[M][C]",
    "poblacion_maxima": "[M]", "habitantes_por_baja": "[M]",
    "cuota_victoria": "[M][C]", "turnos_maximos": "[M]",
    "cuota_amenaza": "[M][C]", "histeresis_alianza": "[M]",
}

UNIDADES: dict[str, str] = {
    "oro_inicial": "oro", "fuerza_inicial": "unidades de fuerza",
    "descontento_inicial": "puntos", "moral_inicial": "—", "semilla": "—",
    "iota": "oro/(hab·turno)", "beta_fort": "1/nivel",
    "coste_admin": "oro/(prov·turno)", "coste_mantenimiento": "oro/(ud·turno)",
    "coste_unidad": "oro/unidad", "coste_fortificacion": "oro/nivel",
    "tasa_maxima": "%", "tasa_neutra": "%",
    "eta_fiscal": "pto/(turno·%)", "eta_guerra": "pto/turno",
    "eta_extension": "pto/(turno·prov)", "umbral_admin": "provincias",
    "eta_recuperacion": "pto/turno", "umbral_fiscal": "puntos", "psi": "—",
    "moral_minima": "—", "lambda_distancia": "1/provincia",
    "rho_moral": "1/turno", "gamma_moral": "—",
    "k_bajas": "—", "beta_defensa": "1/nivel",
    "fortificacion_maxima": "niveles", "fuerza_minima": "unidades",
    "guarnicion_referencia": "unidades", "tri_a": "—", "tri_c": "—",
    "tri_b": "—",
    "puntos_movimiento": "provincias/turno", "guarnicion_reserva": "unidades",
    "ejercitos_maximos": "ejércitos", "crecimiento_poblacion": "1/turno",
    "poblacion_maxima": "habitantes", "habitantes_por_baja": "hab/unidad",
    "cuota_victoria": "—", "turnos_maximos": "turnos",
    "cuota_amenaza": "—", "histeresis_alianza": "—",
}
