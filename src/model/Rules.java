package model;

/**
 * Parámetros calibrables del modelo de simulación (ver PLAN.md §5.3).
 *
 * Age of Conquest IV no publica varias de sus fórmulas exactas; estos valores
 * son decisiones de modelado propias y constituyen los factores a variar en
 * los experimentos de simulación (análisis de sensibilidad).
 */
public class Rules {

    /** Puntos de acción base por turno. */
    public double apBase = 3.0;

    /** Puntos de acción adicionales por provincia controlada. */
    public double apPerProvince = 0.5;

    /** Bono de combate si el rey lucha con las tropas (+30%, documentado del juego real). */
    public double kingCombatBonus = 0.30;

    /** Bono del defensor en provincia fortificada (+50%, documentado del juego real). */
    public double fortDefenseBonus = 0.50;

    /** Factor de desgaste del ganador en combate (φ del modelo Lanchester discreto). */
    public double combatAttrition = 0.70;

    /** Población máxima por provincia (documentado: 1 millón). */
    public long maxPopulation = 1_000_000;

    /** Oro máximo por provincia y turno con población llena y tasa 100% (documentado: 250). */
    public double maxTaxGoldPerProvince = 250.0;

    /** Soldados mantenidos por cada 1 de oro por turno (documentado: ~20). */
    public int troopsPerUpkeepGold = 20;

    /** Tasa de crecimiento poblacional por turno (no documentada; calibrable). */
    public double populationGrowth = 0.015;

    /** Constante k del riesgo de revuelta: P = min(0.9, k·(50−felicidad)²). */
    public double revoltRiskK = 4e-4;

    /** Umbral de felicidad bajo el cual la provincia no paga impuestos y puede rebelarse. */
    public double happinessRevoltThreshold = 50.0;

    /** Felicidad inicial de las provincias si el escenario no la especifica. */
    public double initialHappiness = 75.0;

    // ------------------------------------------------ costes de las órdenes (M2)

    /** Coste en AP de mover un ejército. */
    public double apCostMove = 0.5;

    /** Coste en AP de reclutar en una provincia. */
    public double apCostRecruit = 0.5;

    /** Coste en AP de fortificar una provincia (documentado del juego real: 0.5). */
    public double apCostFortify = 0.5;

    /** Coste en AP de declarar la guerra. */
    public double apCostDeclareWar = 0.5;

    /** Coste en oro de fortificar una provincia. */
    public double goldCostFortify = 20.0;

    /** Coste en oro por soldado reclutado. */
    public double recruitGoldPerSoldier = 0.1;

    /** Habitantes que consume cada soldado reclutado. */
    public double recruitPopulationPerSoldier = 2.0;

    // ------------------------------------------------ economía por turno (M3)

    /** Oro de administración por provincia y turno. */
    public double adminGoldPerProvince = 1.0;

    /** Recuperación base de felicidad por turno. */
    public double happinessBaseRecovery = 1.0;

    /**
     * Felicidad por turno según la tasa impositiva: (100 − tasa) · este factor.
     * Con 0.04: tasa 0% → +4, 100% → 0, 200% → −4.
     */
    public double taxHappinessPerPoint = 0.04;

    /** Pérdida de felicidad por turno mientras se está en guerra. */
    public double warUnhappiness = 2.0;

    /** Tasas impositivas permitidas (documentadas del juego real). */
    public int[] allowedTaxRates = {0, 50, 100, 150, 200};

    /** La tasa solo puede cambiarse en la temporada fiscal: turnos 1, 1+N, 1+2N… */
    public int taxSeasonInterval = 5;

    /** Probabilidad máxima de revuelta por turno. */
    public double revoltMaxChance = 0.9;

    /** Factor de supresión de revueltas si la provincia tiene guarnición (≥1 soldado). */
    public double revoltGarrisonSuppression = 0.5;

    /** Milicia rebelde al triunfar una revuelta: habitantes · este factor (mínimo 1). */
    public double rebelsPerPopulation = 0.0002;

    /** Felicidad de la provincia tras una revuelta triunfante. */
    public double revoltHappinessAfter = 60.0;

    /** Semilla del generador aleatorio (reproducibilidad de los experimentos). */
    public long randomSeed = 20260705L;

    // ----------------------------------------------- saqueo y decretos (M3)

    /** Coste en AP de saquear una provincia (documentado del juego real: 1). */
    public double apCostPillage = 1.0;

    /** Fracción de la población destruida por un saqueo. */
    public double pillagePopulationLoss = 0.20;

    /** Oro obtenido por habitante destruido al saquear. */
    public double pillageGoldPerInhabitant = 0.001;

    /** Felicidad perdida por la provincia saqueada. */
    public double pillageHappinessLoss = 30.0;

    /** Decreto "repartir dinero": +10 de felicidad (documentado). */
    public double apCostDecreeShare = 0.5;
    public double goldCostDecreeShare = 10.0;
    public double decreeShareHappiness = 10.0;

    /** Decreto "fiesta de inauguración": 25 de oro y +20 de felicidad (documentados). */
    public double apCostDecreeParty = 0.2;
    public double goldCostDecreeParty = 25.0;
    public double decreePartyHappiness = 20.0;

    /** Festival de fertilidad: +20% de población de una vez (documentado; 0.5 AP). */
    public double apCostFestival = 0.5;
    public double goldCostFestival = 15.0;
    public double festivalPopulationBoost = 0.20;

    // -------------------------------------------------------- reglas de partida

    /**
     * Fracción del territorio que se pierde al morir el rey (documentado del
     * juego real: ~90%). Con 1.0 la muerte del rey elimina a la nación.
     */
    public double kingDeathTerritoryLoss = 0.9;

    /** Límite de turnos de la partida; 0 = sin límite (gana el último en pie). */
    public int maxTurns = 0;

    /** Puntos de acción disponibles para una nación con {@code provinceCount} provincias. */
    public double actionPointsFor(int provinceCount) {
        return apBase + apPerProvince * provinceCount;
    }

    /** ¿Es temporada fiscal (se puede cambiar la tasa impositiva) en este turno? */
    public boolean isTaxSeason(int turn) {
        return (turn - 1) % taxSeasonInterval == 0;
    }

    /** ¿Está permitida esta tasa impositiva? */
    public boolean isAllowedTaxRate(int rate) {
        for (int allowed : allowedTaxRates) {
            if (allowed == rate) {
                return true;
            }
        }
        return false;
    }
}
