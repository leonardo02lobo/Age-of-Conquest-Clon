package model;

/**
 * Parámetros calibrables del modelo de simulación (tabla §1.2 del modelo formal).
 *
 * Todos los valores coinciden con los definidos en Parcial_II_Parametros_y_Eventos.md.
 * La IA usa estos parámetros vía la tabla 1.9 de estrategias.
 */
public class Rules {

    // ----------------------------------------------- Subsistema económico (§2.4.2)

    /** ι — renta unitaria a tasa 100% [oro/(hab·turno)]. */
    public double iota = 0.01;

    /** β_φ — bonificación de renta por fortificación [1/nivel]. */
    public double betaPhi = 0.05;

    /** c_adm — coste administrativo por provincia [oro/(prov·turno)]. */
    public double cAdm = 2.0;

    /** c_up — mantenimiento militar unitario [oro/(unidad·turno)]. */
    public double cUp = 0.05;

    /** c_u — coste de reclutamiento [oro/unidad]. */
    public double cU = 1.5;

    /** c_φ — coste de un nivel de fortificación [oro/nivel]. */
    public double cPhi = 40.0;

    /** θ_max — tasa impositiva máxima [%]. */
    public int thetaMax = 150;

    /** θ_0 — tasa fiscalmente neutra [%]. */
    public int theta0 = 50;

    // -------------------------------------------------------- Descontento (§2.4.3)

    /** η_θ — sensibilidad a la presión fiscal [pts/(turno·%)]. */
    public double etaTheta = 0.06;

    /** η_w — descontento por estado de guerra [pts/turno]. */
    public double etaW = 2.0;

    /** η_n — descontento por sobreextensión [pts/(turno·prov)]. */
    public double etaN = 0.5;

    /** n* — umbral administrativo de provincias. */
    public int nStar = 8;

    /** η_r — recuperación base [pts/turno]. */
    public double etaR = 1.5;

    /** D* — umbral fiscal de descontento [puntos]. income = 0 cuando D_p ≥ D*. */
    public double dStar = 60.0;

    // ----------------------------------------------------------- Moral (§2.4.4)

    /** μ_min — moral mínima. */
    public double muMin = 0.40;

    /** λ_d — decaimiento por distancia [1/provincia]. */
    public double lambdaD = 0.06;

    /** ρ_μ — regeneración por turno [1/turno]. */
    public double rhoMu = 0.10;

    /** γ_μ — desgaste por bajas. */
    public double gammaMu = 0.50;

    // ----------------------------------------------------------- Combate (§2.4.5)

    /** K_B — coeficiente de bajas. */
    public double kBeta = 0.70;

    /** β_F — bonificación defensiva por fortificación [1/nivel]. */
    public double betaF = 0.15;

    /** Φ_max — nivel máximo de fortificación. */
    public int phiMax = 4;

    /** F_min — fuerza mínima viable [unidades de fuerza]. */
    public int fMin = 5;

    /** g_ref — guarnición de referencia [unidades de fuerza]. */
    public int gRef = 50;

    // -------------------------------------------------- Población y terminación (§2.4.8)

    /** g_L — tasa crecimiento poblacional [1/turno]. */
    public double gL = 0.01;

    /** L_max — población máxima por provincia [habitantes]. */
    public long lMax = 20_000;

    /** ϱ — habitantes destruidos por baja [habitantes/unidad]. */
    public double rho = 2.0;

    /** Θ_V — cuota de victoria. */
    public double thetaV = 0.60;

    /** t_max — límite de turnos. */
    public int tMax = 200;

    /** θ_am — cuota que dispara coalición. */
    public double thetaAm = 0.40;

    /** ς_h — histéresis disolución alianzas. */
    public double sigmaH = 0.05;

    // --------------------------------------------------- Movimiento (§2.4.8)

    /** v_a — puntos de movimiento base [provincias/turno]. */
    public double vA = 1.5;

    /** g_ret — guarnición de reserva capital [unidades de fuerza]. */
    public int gRet = 30;

    /** A_max — ejércitos simultáneos. */
    public int aMax = 4;

    // --------------------------------------------------- IA estratégica (§2.4.9)

    /** Tipo de estrategia de IA para cada nación. */
    public enum Strategy { AGRESIVA, DEFENSIVA, ECONOMICA, EQUILIBRADA }

    /** Tasa impositiva objetivo por estrategia (θ_σ). */
    public int taxRateForStrategy(Strategy s) {
        return switch (s) {
            case AGRESIVA -> 125;
            case DEFENSIVA -> 100;
            case ECONOMICA -> 0;  // se calcula adaptativamente con θ_eq
            case EQUILIBRADA -> 0; // se calcula adaptativamente
        };
    }

    /** Fracción tesoro a reclutar (f_rec). */
    public double fRecForStrategy(Strategy s) {
        return switch (s) {
            case AGRESIVA -> 0.90;
            case DEFENSIVA -> 0.60;
            case ECONOMICA -> 0.30;
            case EQUILIBRADA -> 0.70;
        };
    }

    /** Ventaja mínima para atacar (γ_atq). */
    public double gammaAtqForStrategy(Strategy s) {
        return switch (s) {
            case AGRESIVA -> 1.1;
            case DEFENSIVA -> 1.8;
            case ECONOMICA -> 2.0;
            case EQUILIBRADA -> 1.4;
        };
    }

    /** Superioridad para declarar guerra (γ_σ). */
    public double gammaSigmaForStrategy(Strategy s) {
        return switch (s) {
            case AGRESIVA -> 1.2;
            case DEFENSIVA -> 2.5;
            case ECONOMICA -> 3.0;
            case EQUILIBRADA -> 1.8;
        };
    }

    /** Fracción fuerza retenida en guarnición (f_gua). */
    public double fGuaForStrategy(Strategy s) {
        return switch (s) {
            case AGRESIVA -> 0.15;
            case DEFENSIVA -> 0.50;
            case ECONOMICA -> 0.40;
            case EQUILIBRADA -> 0.30;
        };
    }

    /** Prioridad de fortificación (f_fort). */
    public double fFortForStrategy(Strategy s) {
        return switch (s) {
            case AGRESIVA -> 0.05;
            case DEFENSIVA -> 0.40;
            case ECONOMICA -> 0.25;
            case EQUILIBRADA -> 0.20;
        };
    }

    // --------------------------------------------------------- Semilla

    /** Semilla del generador LCG (reproducibilidad). */
    public long randomSeed = 20260805L;
}
