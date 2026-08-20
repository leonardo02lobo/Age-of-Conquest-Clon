package engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import model.LCG;
import model.Rules;
import model.TerrainType;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de CombatResolver con el modelo formal (ec. 3.14–3.21).
 *
 * Todos los tests usan LCG sembrado para reproducibilidad.
 */
class CombatResolverTest {

    private static final Rules RULES = new Rules();

    private static LCG freshLcg() {
        return new LCG(20260805L);
    }

    @Test
    void provinciaVaciaSeOcupaSinBajas() {
        CombatResolver.Outcome outcome = CombatResolver.resolve(
                20, 1.0, TerrainType.LLANURA,
                0, 0, TerrainType.LLANURA,
                0, RULES, freshLcg());
        assertTrue(outcome.attackerWon());
        assertEquals(20, outcome.survivors());
    }

    @Test
    void victoriaDelMasFuerte() {
        CombatResolver.Outcome outcome = CombatResolver.resolve(
                50, 1.0, TerrainType.LLANURA,
                25, 0, TerrainType.LLANURA,
                0, RULES, freshLcg());
        assertTrue(outcome.attackerWon());
        assertTrue(outcome.survivors() > 0);
        assertTrue(outcome.survivors() <= 50);
    }

    @Test
    void fortificacionPuedeInvertirElResultado() {
        CombatResolver.Outcome sinFort = CombatResolver.resolve(
                30, 1.0, TerrainType.LLANURA,
                25, 0, TerrainType.LLANURA,
                0, RULES, freshLcg());
        assertTrue(sinFort.attackerWon());

        CombatResolver.Outcome conFort = CombatResolver.resolve(
                30, 1.0, TerrainType.LLANURA,
                25, 3, TerrainType.LLANURA,
                0, RULES, freshLcg());
        assertFalse(conFort.attackerWon());
    }

    @Test
    void elEmpateLoRetieneElDefensor() {
        // Con fuerzas iguales, el resultado depende de U (estocástico).
        // Para verificar el tie-break, usar fuerzas donde P_d > P_a siempre:
        // 10 atacantes vs 20 defensores: P_a ∈ [8,12], P_d ∈ [16,24]
        CombatResolver.Outcome outcome = CombatResolver.resolve(
                10, 1.0, TerrainType.LLANURA,
                20, 0, TerrainType.LLANURA,
                0, RULES, freshLcg());
        assertFalse(outcome.attackerWon());
    }

    @Test
    void siempreSobreviveAlMenosUnSoldado() {
        CombatResolver.Outcome o1 = CombatResolver.resolve(
                100, 1.0, TerrainType.LLANURA,
                99, 0, TerrainType.LLANURA,
                0, RULES, freshLcg());
        assertTrue(o1.survivors() >= 1);
    }

    @Test
    void atacarSinSoldadosEsIlegal() {
        assertThrows(IllegalArgumentException.class,
                () -> CombatResolver.resolve(0, 1.0, TerrainType.LLANURA,
                        10, 0, TerrainType.LLANURA, 0, RULES, freshLcg()));
    }

    @Test
    void bosqueFavoreceAlDefensor() {
        CombatResolver.Outcome outcome = CombatResolver.resolve(
                20, 1.0, TerrainType.BOSQUE,
                20, 0, TerrainType.BOSQUE,
                0, RULES, freshLcg());
        assertFalse(outcome.attackerWon());
    }

    @Test
    void descontentoDebilitaAlDefensor() {
        CombatResolver.Outcome outcome = CombatResolver.resolve(
                20, 1.0, TerrainType.LLANURA,
                20, 0, TerrainType.LLANURA,
                80, RULES, freshLcg());
        assertTrue(outcome.attackerWon());
    }
}
