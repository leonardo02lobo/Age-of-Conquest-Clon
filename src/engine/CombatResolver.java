package engine;

import model.LCG;
import model.Rules;
import model.TerrainType;

/**
 * Resolución de combate según el modelo formal (ec. 3.14–3.21).
 *
 * Potencia de ataque:
 *   P_a = F_a · μ_a · T(T_p, ATQ) · U_a          (ec. 3.14)
 *
 * Potencia de defensa:
 *   P_d = D_p · Φ(φ_p) · T(T_p, DEF) · Ψ(D_p) · U_d  (ec. 3.15)
 *
 * U_a, U_d ~ Triangular(0.8, 1.0, 1.2) i.i.d.      (ec. 3.23–3.25)
 *
 * Victoria: P_a > P_d (empate → defensor)            (ec. 3.18)
 *
 * Bajas del ganador:
 *   b_gan = F_gan · (P_perd / P_gan) · K_B          (ec. 3.19)
 *
 * Bajas del perdedor: aniquilación total (ec. 3.20)
 */
public final class CombatResolver {

    /** Resultado de una batalla. */
    public record Outcome(boolean attackerWon, int survivors, double casualtiesAttacker,
                          double casualtiesDefender) {
    }

    private CombatResolver() {
    }

    /**
     * Resuelve un combate completo con todos los modificadores del modelo formal.
     *
     * @param attackers        fuerza del atacante F_a
     * @param attackerMorale   moral del atacante μ_a ∈ [μ_min, 1.0]
     * @param attackersTerrain terreno de la provincia (donde se ubica el atacante)
     * @param defenders        fuerza defensiva de la provincia D_p
     * @param fortLevel        nivel de fortificación φ_p ∈ {0..Φ_max}
     * @param defendersTerrain terreno de la provincia defensora
     * @param discontent       descontento de la provincia D_p ∈ [0, 100]
     * @param rules            parámetros del modelo
     * @param lcg              generador aleatorio para U_a, U_d
     * @return Outcome con resultado y supervivientes
     */
    public static Outcome resolve(int attackers, double attackerMorale,
                                  TerrainType attackersTerrain,
                                  int defenders, int fortLevel,
                                  TerrainType defendersTerrain,
                                  double discontent, Rules rules, LCG lcg) {
        if (attackers <= 0) {
            throw new IllegalArgumentException("Un ataque necesita al menos 1 soldado");
        }
        if (defenders <= 0) {
            return new Outcome(true, attackers, 0, 0);
        }

        // Variables aleatorias U_a, U_d ~ Triangular(0.8, 1.0, 1.2) — ec. 3.25
        double uA = lcg.nextTriangular();
        double uD = lcg.nextTriangular();

        // Φ(φ) = 1 + β_F · φ — ec. 3.16
        double fortBonus = 1.0 + rules.betaF * Math.min(fortLevel, rules.phiMax);

        // Ψ(D_p) = 1 - ψ · D_p / 100 — ec. 3.17, con ψ = 0.4
        double psi = 1.0 - 0.4 * Math.min(discontent, 100.0) / 100.0;

        // P_a = F_a · μ_a · T(T_p, ATQ) · U_a — ec. 3.14
        double pA = attackers * attackerMorale * attackersTerrain.attackModifier * uA;

        // P_d = D_p · Φ(φ_p) · T(T_p, DEF) · Ψ(D_p) · U_d — ec. 3.15
        double pD = defenders * fortBonus * defendersTerrain.defenseModifier * psi * uD;

        if (pA > pD) {
            // Atacante gana — bajas: ec. 3.19
            double bGan = attackers * (pD / pA) * rules.kBeta;
            double bPerd = defenders;
            int survivors = Math.max(1, (int) Math.round(attackers - bGan));
            return new Outcome(true, survivors, bGan, bPerd);
        } else {
            // Defensor gana — bajas del atacante: ec. 3.19 invertida
            double bGan = defenders * (pA / pD) * rules.kBeta;
            double bPerd = attackers;
            int survivors = Math.max(1, (int) Math.round(defenders - bGan));
            return new Outcome(false, survivors, bPerd, bGan);
        }
    }

    /**
     * Forma simplificada para backward compatibility (sin terreno, sin moral).
     * Usa moral = 1.0, terreno LLANURA, descontento 0.
     */
    public static Outcome resolve(int attackers, double attackBonus,
                                  int defenders, double defenseBonus, double attrition,
                                  LCG lcg) {
        // Modificadores de bono traducidos a bonus del modelo formal
        double attackerMorale = 1.0;
        int fortLevel = defenseBonus > 0 ? (int) Math.round((defenseBonus) / 0.15) : 0;
        TerrainType terrain = TerrainType.LLANURA;
        double discontent = 0.0;

        Rules rules = new Rules();
        rules.kBeta = attrition;
        rules.betaF = 0.15;

        return resolve(attackers, attackerMorale, terrain,
                defenders, fortLevel, terrain, discontent, rules, lcg);
    }

    /**
     * Calcula la fuerza defensiva total de una provincia (ec. 3.12b):
     *   D_p = guarnición + Σ ejércitos estacionados
     */
    public static int defensiveForce(int garrison, int stationedTroops) {
        return garrison + stationedTroops;
    }
}
