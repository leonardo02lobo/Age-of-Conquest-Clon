package model;

/**
 * Tipo de terreno de una provincia (tabla §1.6 del modelo formal).
 * Cada tipo define modificadores de ataque y defensa adimensionales,
 * y un coste de cruce para el cálculo de llegada (ec. 4.4).
 */
public enum TerrainType {

    LLANURA(1.00, 1.00, 1.0),
    BOSQUE(0.90, 1.15, 1.4),
    MONTANA(0.80, 1.30, 2.0),
    COSTA(0.95, 1.10, 1.2);

    /** Modificador de ataque T(T_p, ATQ). */
    public final double attackModifier;

    /** Modificador de defensa T(T_p, DEF). */
    public final double defenseModifier;

    /** Coste de cruce w(T_q) en turnos (para ec. 4.4). */
    public final double moveCost;

    TerrainType(double attackModifier, double defenseModifier, double moveCost) {
        this.attackModifier = attackModifier;
        this.defenseModifier = defenseModifier;
        this.moveCost = moveCost;
    }
}
