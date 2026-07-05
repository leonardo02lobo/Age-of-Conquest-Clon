package engine;

/**
 * Resolución de combate: determinista, tipo Lanchester discreto (PLAN.md §5.3).
 *
 * Fuerza = tropas · (1 + bonos). Gana el bando con más fuerza y sus
 * supervivientes son {@code tropas · (1 − F_perdedor/F_ganador · φ)}, donde φ
 * es el factor de desgaste ({@code Rules.combatAttrition}). El empate exacto
 * lo retiene el defensor. El bando perdedor se destruye por completo.
 */
public final class CombatResolver {

    /** Resultado de una batalla. */
    public record Outcome(boolean attackerWon, int survivors) {
    }

    private CombatResolver() {
    }

    /**
     * @param attackers    soldados atacantes (&gt; 0)
     * @param attackBonus  suma de bonos del atacante (p. ej. 0.30 si va el rey)
     * @param defenders    soldados defensores (0 = provincia sin guarnición)
     * @param defenseBonus suma de bonos del defensor (rey, fortificación)
     * @param attrition    factor de desgaste φ del ganador
     */
    public static Outcome resolve(int attackers, double attackBonus,
                                  int defenders, double defenseBonus, double attrition) {
        if (attackers <= 0) {
            throw new IllegalArgumentException("Un ataque necesita al menos 1 soldado");
        }
        if (defenders == 0) {
            return new Outcome(true, attackers); // provincia vacía: se ocupa sin bajas
        }

        double attackForce = attackers * (1 + attackBonus);
        double defenseForce = defenders * (1 + defenseBonus);

        if (attackForce > defenseForce) {
            return new Outcome(true, survivors(attackers, defenseForce / attackForce, attrition));
        }
        // Empate exacto: el defensor retiene la provincia.
        return new Outcome(false, survivors(defenders, attackForce / defenseForce, attrition));
    }

    /** Supervivientes del ganador; siempre queda al menos 1 soldado. */
    private static int survivors(int troops, double forceRatio, double attrition) {
        return Math.max(1, (int) Math.round(troops * (1 - forceRatio * attrition)));
    }
}
