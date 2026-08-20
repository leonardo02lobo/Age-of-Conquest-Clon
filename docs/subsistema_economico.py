"""
Subsistema económico y demográfico — Age of Conquest (Parcial II)
Implementa las ecuaciones (3.1) a (3.9) del documento "Modelo formal de AGE OF CONQUEST".

Referencia de ecuaciones:
    (3.1) Renta de una provincia (calcularIngreso)
    (3.2) Recaudación total del imperio
    (3.3) Coste de mantenimiento del imperio
    (3.4) Ecuación de estado del tesoro (evento E1)
    (3.5) Gasto discrecional (evento E2)
    (3.6) Variación del descontento provincial
    (3.7) Clamp del descontento a [0, 100]
    (3.8) Tasa impositiva de equilibrio
    (3.9) Tamaño máximo sostenible del imperio
"""

from dataclasses import dataclass, field
from typing import Optional


# ----------------------------------------------------------------------
# PARÁMETROS FIJOS (tabla 2.4.1 a 2.4.3 del documento)
# ----------------------------------------------------------------------

# --- Subsistema económico (§2.4.2) ---
IOTA = 0.01          # ι  — renta unitaria a tasa 100%  [oro/(hab·turno)]
BETA_PHI = 0.05       # β_φ — bonificación de renta por fortificación [1/nivel]
C_ADM = 2.0           # c_adm — coste administrativo por provincia [oro/(prov·turno)]
C_UP = 0.05           # c_up  — mantenimiento militar unitario [oro/(unidad·turno)]
C_U = 1.5             # c_u   — coste de reclutamiento (COSTO_UNIDAD) [oro/unidad]
C_PHI = 40            # c_φ   — coste de un nivel de fortificación [oro/nivel]
THETA_MAX = 150       # θ_max — tasa impositiva máxima [%]
THETA_0 = 50          # θ_0   — tasa fiscalmente neutra [%]

# --- Descontento (§2.4.3) ---
ETA_THETA = 0.06      # η_θ — sensibilidad a la presión fiscal [pts/(turno·%)]
ETA_W = 2.0           # η_w — descontento por estado de guerra [pts/turno]
ETA_N = 0.5           # η_n — descontento por sobreextensión [pts/(turno·prov)]
N_STAR = 8            # n*  — umbral administrativo de provincias
ETA_R = 1.5           # η_r — recuperación base [pts/turno]
D_STAR = 60           # D*  — umbral fiscal de descontento [puntos]  ← el "salto"


# ----------------------------------------------------------------------
# ESTRUCTURAS DE DATOS MÍNIMAS
# ----------------------------------------------------------------------

@dataclass
class Provincia:
    id: int
    poblacion: float          # L_p
    fortificacion: int = 0    # φ_p  (nivel entero, 0..Φmax)
    descontento: float = 20.0 # D_p  (dominio [0, 100], inicial = D0 = 20)


@dataclass
class Imperio:
    id: int
    oro: float = 200.0        # G_i  (ORO_INICIAL = 200)
    tasa_impositiva: float = 100.0  # θ_i  (%), fijada por la estrategia de IA
    provincias: list = field(default_factory=list)  # lista de Provincia
    en_guerra: bool = False   # ∃ j : δ_ij = GUERRA
    poder_militar: float = 0.0  # M_i (suma de fuerzas + guarniciones, ec. 3.12)


# ----------------------------------------------------------------------
# (3.1) — Renta de una provincia (calcularIngreso)
# ----------------------------------------------------------------------

def renta_provincia(provincia: Provincia, tasa_impositiva: float) -> float:
    """
    Ecuación (3.1):
        I_p(t) = ι · L_p · (θ_i/100) · (1 + β_φ · φ_p)   si D_p < D*
        I_p(t) = 0                                        si D_p ≥ D*

    Esto es EXACTAMENTE lo que pediste:
        oro = población × 1% × (tasa_impuesto/100) × bono_fortificación
    con el salto duro en D* = 60.
    """
    if provincia.descontento >= D_STAR:
        return 0.0  # provincia "en huelga fiscal": no tributa nada

    bono_fortificacion = 1 + BETA_PHI * provincia.fortificacion
    return IOTA * provincia.poblacion * (tasa_impositiva / 100.0) * bono_fortificacion


# ----------------------------------------------------------------------
# (3.2) — Recaudación total del imperio
# ----------------------------------------------------------------------

def recaudacion_total(imperio: Imperio) -> float:
    """R_i(t) = Σ_p I_p(t) sobre todas las provincias del imperio."""
    return sum(
        renta_provincia(p, imperio.tasa_impositiva) for p in imperio.provincias
    )


# ----------------------------------------------------------------------
# (3.3) — Coste de mantenimiento del imperio
# ----------------------------------------------------------------------

def coste_mantenimiento(imperio: Imperio) -> float:
    """C_i(t) = c_adm · n_i + c_up · M_i"""
    n_i = len(imperio.provincias)
    return C_ADM * n_i + C_UP * imperio.poder_militar


# ----------------------------------------------------------------------
# (3.4) — Ecuación de estado del tesoro (evento E1)
# ----------------------------------------------------------------------

def actualizar_tesoro(imperio: Imperio, gasto_discrecional: float = 0.0) -> float:
    """
    Ecuación (3.4):
        G_i(t+1) = max(0, G_i(t) + R_i(t) - C_i(t) - X_i(t))

    gasto_discrecional = X_i(t), ec. (3.5): c_u·u_i(t) + c_φ·z_i(t)
    (unidades reclutadas y niveles de fortificación comprados ese turno,
    normalmente se calcula en el evento E2 y se pasa aquí ya sumado).

    Nota importante: si el resultado antes del max(0, ·) es negativo,
    eso dispara la regla de INSOLVENCIA (§5.2b) — deserción forzosa de
    tropas — que se implementa aparte, no en esta función.
    """
    ingreso = recaudacion_total(imperio)
    gasto_mantenimiento = coste_mantenimiento(imperio)

    nuevo_oro = imperio.oro + ingreso - gasto_mantenimiento - gasto_discrecional
    imperio.oro = max(0.0, nuevo_oro)
    return imperio.oro


# ----------------------------------------------------------------------
# (3.5) — Gasto discrecional (para usar junto con 3.4)
# ----------------------------------------------------------------------

def gasto_discrecional(unidades_reclutadas: int, niveles_fortificacion: int) -> float:
    """X_i(t) = c_u · u_i(t) + c_φ · z_i(t)"""
    return C_U * unidades_reclutadas + C_PHI * niveles_fortificacion


# ----------------------------------------------------------------------
# (3.6)-(3.7) — Variación y actualización del descontento
# ----------------------------------------------------------------------

def variacion_descontento(imperio: Imperio) -> float:
    """
    Ecuación (3.6):
        ΔD_p(t) = η_θ·(θ_i - θ_0) + η_w·1[en_guerra] + η_n·max(0, n_i - n*) - η_r

    Nota: pese al subíndice "p", esta fórmula depende SOLO de variables
    del imperio (tasa de impuesto, si está en guerra, número de provincias),
    así que ΔD es igual para todas las provincias del mismo imperio en un
    turno dado.
    """
    n_i = len(imperio.provincias)
    presion_fiscal = ETA_THETA * (imperio.tasa_impositiva - THETA_0)
    esfuerzo_guerra = ETA_W * (1 if imperio.en_guerra else 0)
    sobreextension = ETA_N * max(0, n_i - N_STAR)
    recuperacion = ETA_R

    return presion_fiscal + esfuerzo_guerra + sobreextension - recuperacion


def actualizar_descontento(imperio: Imperio) -> None:
    """
    Ecuación (3.7):
        D_p(t+1) = clamp(D_p(t) + ΔD_p(t), 0, 100)

    Aplica la MISMA variación (delta_d) a todas las provincias del
    imperio, luego recorta cada una al rango [0, 100].
    """
    delta_d = variacion_descontento(imperio)
    for provincia in imperio.provincias:
        nuevo_d = provincia.descontento + delta_d
        provincia.descontento = min(100.0, max(0.0, nuevo_d))  # clamp(x, 0, 100)


# ----------------------------------------------------------------------
# (3.8) — Tasa impositiva de equilibrio (ΔD = 0)
# ----------------------------------------------------------------------

def tasa_impositiva_equilibrio(n_i: int, en_guerra: bool) -> float:
    """
    Ecuación (3.8): despejando ΔD_p = 0 en (3.6) respecto a θ_i.

        θ_eq(t) = θ_0 + [η_r - η_w·1[guerra] - η_n·max(0, n_i - n*)] / η_θ

    Es la tasa de impuesto que mantiene el descontento CONSTANTE
    (ni sube ni baja). La estrategia ECONÓMICA la usa directamente
    como su política fiscal (ver F11 en el pseudocódigo del evento E2).
    """
    guerra_term = ETA_W * (1 if en_guerra else 0)
    sobreextension_term = ETA_N * max(0, n_i - N_STAR)
    theta_eq = THETA_0 + (ETA_R - guerra_term - sobreextension_term) / ETA_THETA
    return max(0.0, min(THETA_MAX, theta_eq))  # acotado a [0, θ_max]


# ----------------------------------------------------------------------
# (3.9) — Tamaño máximo sostenible del imperio (θ_eq ≥ 0)
# ----------------------------------------------------------------------

def tamano_maximo_sostenible(en_guerra: bool) -> float:
    """
    Ecuación (3.9): imponiendo θ_eq ≥ 0 en (3.8), se despeja n_i.

        n_max = n* + [η_r - η_w·1[guerra] + η_θ·θ_0] / η_n

    Con los valores del documento:
        n_max (paz)    = 8 + (1.5 + 3) / 0.5   = 17 provincias
        n_max (guerra) = 8 + (1.5 - 2 + 3) / 0.5 = 13 provincias

    Interpretación: por encima de este número de provincias, ya NO
    existe ninguna tasa de impuesto (ni siquiera 0%) que evite que el
    descontento crezca sin control.
    """
    guerra_term = ETA_W * (1 if en_guerra else 0)
    n_max = N_STAR + (ETA_R - guerra_term + ETA_THETA * THETA_0) / ETA_N
    return n_max


# ----------------------------------------------------------------------
# DEMOSTRACIÓN / AUTOTEST — reproduce los números del documento
# ----------------------------------------------------------------------

if __name__ == "__main__":
    print("=== Verificación contra los valores del documento (§3.2.4) ===\n")

    n_max_paz = tamano_maximo_sostenible(en_guerra=False)
    n_max_guerra = tamano_maximo_sostenible(en_guerra=True)
    print(f"n_max en paz    = {n_max_paz:.1f} provincias  (esperado: 17.0)")
    print(f"n_max en guerra = {n_max_guerra:.1f} provincias  (esperado: 13.0)")

    theta_eq_paz = tasa_impositiva_equilibrio(n_i=3, en_guerra=False)
    theta_eq_guerra = tasa_impositiva_equilibrio(n_i=3, en_guerra=True)
    print(f"\nθ_eq (imperio pequeño, paz)    = {theta_eq_paz:.1f}%   (esperado: 75.0%)")
    print(f"θ_eq (imperio pequeño, guerra) = {theta_eq_guerra:.1f}%  (esperado: ≈41.7%)")

    print("\n=== Ejemplo numérico: turno 5 del §4.7 (traza de escritorio) ===\n")

    p1 = Provincia(id=1, poblacion=5000, fortificacion=1, descontento=35)
    p2 = Provincia(id=2, poblacion=3000, fortificacion=0, descontento=45)
    p3 = Provincia(id=3, poblacion=2000, fortificacion=0, descontento=25)

    imperio1 = Imperio(
        id=1,
        oro=150.0,
        tasa_impositiva=125,   # estrategia AGRESIVA
        provincias=[p1, p2, p3],
        en_guerra=True,
        poder_militar=155.0,
    )

    R1 = recaudacion_total(imperio1)
    C1 = coste_mantenimiento(imperio1)
    print(f"R_1 (recaudación) = {R1:.3f} oro/turno   (esperado: 128.125)")
    print(f"C_1 (mantenimiento) = {C1:.2f} oro/turno   (esperado: 13.75)")

    actualizar_tesoro(imperio1)  # sin gasto discrecional en este paso
    print(f"G_1 tras E1 = {imperio1.oro:.3f} oro   (esperado: 264.375)")

    n_max_actual = tamano_maximo_sostenible(en_guerra=True)
    print(f"\n¿Puede I1 sostener 4 provincias en guerra sin colapsar? "
          f"{'Sí' if 4 <= n_max_actual else 'No'} (n_max = {n_max_actual:.0f})")
