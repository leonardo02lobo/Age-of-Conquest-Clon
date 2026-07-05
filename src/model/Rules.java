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
}
