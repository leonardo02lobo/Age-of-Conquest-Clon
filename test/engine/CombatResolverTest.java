package engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CombatResolverTest {

    private static final double PHI = 0.7; // desgaste por defecto de Rules

    @Test
    void provinciaVaciaSeOcupaSinBajas() {
        CombatResolver.Outcome outcome = CombatResolver.resolve(20, 0, 0, 0, PHI);
        assertTrue(outcome.attackerWon());
        assertEquals(20, outcome.survivors());
    }

    @Test
    void victoriaDeterministaDelMasFuerte() {
        // FA=50, FD=25 → gana el atacante; supervivientes = 50·(1 − 0.5·0.7) = 32.5 → 33
        CombatResolver.Outcome outcome = CombatResolver.resolve(50, 0, 25, 0, PHI);
        assertTrue(outcome.attackerWon());
        assertEquals(33, outcome.survivors());
    }

    @Test
    void laFortificacionPuedeInvertirElResultado() {
        // Sin fortificar: 30 > 25 ganaría el atacante.
        assertTrue(CombatResolver.resolve(30, 0, 25, 0, PHI).attackerWon());
        // Fortificado: FD = 25·1.5 = 37.5 > 30 → el defensor resiste;
        // supervivientes = 25·(1 − (30/37.5)·0.7) = 11
        CombatResolver.Outcome outcome = CombatResolver.resolve(30, 0, 25, 0.5, PHI);
        assertFalse(outcome.attackerWon());
        assertEquals(11, outcome.survivors());
    }

    @Test
    void elReyInclinaLaBalanza() {
        // Sin rey: FA=30 < FD=37.5 → derrota.
        assertFalse(CombatResolver.resolve(30, 0, 25, 0.5, PHI).attackerWon());
        // Con rey: FA = 30·1.3 = 39 > 37.5 → victoria;
        // supervivientes = 30·(1 − (37.5/39)·0.7) = 9.8 → 10
        CombatResolver.Outcome outcome = CombatResolver.resolve(30, 0.3, 25, 0.5, PHI);
        assertTrue(outcome.attackerWon());
        assertEquals(10, outcome.survivors());
    }

    @Test
    void elEmpateExactoLoRetieneElDefensor() {
        // FA = FD = 20 → defensor; supervivientes = 20·(1 − 0.7) = 6
        CombatResolver.Outcome outcome = CombatResolver.resolve(20, 0, 20, 0, PHI);
        assertFalse(outcome.attackerWon());
        assertEquals(6, outcome.survivors());
    }

    @Test
    void siempreSobreviveAlMenosUnSoldado() {
        // Con φ=1 y fuerzas casi iguales el redondeo daría 0: se fuerza el mínimo de 1.
        assertEquals(1, CombatResolver.resolve(100, 0, 99, 0, 1.0).survivors());
        assertEquals(1, CombatResolver.resolve(1000, 0, 999, 0, 1.0).survivors());
    }

    @Test
    void atacarSinSoldadosEsIlegal() {
        assertThrows(IllegalArgumentException.class,
                () -> CombatResolver.resolve(0, 0, 10, 0, PHI));
    }
}
