package model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GameStateTest {

    private GameState state;

    @BeforeEach
    void setUp() {
        Province a = new Province("a", "Alfa", false);
        Province b = new Province("b", "Beta", false);
        Province mar = new Province("mar", "Mar", true);
        a.addAdjacent("b");
        b.addAdjacent("a");
        b.addAdjacent("mar");
        mar.addAdjacent("b");

        a.setOwnerId("n1");
        a.setTroops(50);
        b.setOwnerId("n1");
        b.setTroops(30);

        Map<String, Province> provinces = new LinkedHashMap<>();
        provinces.put("a", a);
        provinces.put("b", b);
        provinces.put("mar", mar);

        Map<String, Nation> nations = new LinkedHashMap<>();
        nations.put("n1", new Nation("n1", "Nación 1", false));
        nations.put("n2", new Nation("n2", "Nación 2", true));

        state = new GameState("Prueba", new Rules(), provinces, nations);
    }

    @Test
    void consultaProvinciasYNaciones() {
        assertEquals(3, state.provinces().size());
        assertEquals(2, state.nations().size());
        assertEquals("Alfa", state.province("a").name());
        assertTrue(state.hasProvince("mar"));
        assertFalse(state.hasProvince("noexiste"));
    }

    @Test
    void idsDesconocidosLanzanExcepcion() {
        assertThrows(IllegalArgumentException.class, () -> state.province("x"));
        assertThrows(IllegalArgumentException.class, () -> state.nation("x"));
        assertThrows(IllegalArgumentException.class, () -> state.provincesOf("x"));
    }

    @Test
    void adyacenciaEnAmbosSentidos() {
        assertTrue(state.areAdjacent("a", "b"));
        assertTrue(state.areAdjacent("b", "a"));
        assertFalse(state.areAdjacent("a", "mar"));
    }

    @Test
    void provinciasYTropasPorNacion() {
        assertEquals(2, state.provincesOf("n1").size());
        assertEquals(80, state.totalTroops("n1"));
        assertEquals(0, state.totalTroops("n2"));
    }

    @Test
    void lasRelacionesSeFijanSimetricamente() {
        assertEquals(DiplomaticState.NEUTRAL, state.relation("n1", "n2"));
        state.setRelation("n1", "n2", DiplomaticState.GUERRA);
        assertEquals(DiplomaticState.GUERRA, state.relation("n1", "n2"));
        assertEquals(DiplomaticState.GUERRA, state.relation("n2", "n1"));
        state.setRelation("n2", "n1", DiplomaticState.NEUTRAL);
        assertEquals(DiplomaticState.NEUTRAL, state.relation("n1", "n2"));
    }

    @Test
    void unaNacionNoSeRelacionaConsigoMisma() {
        assertThrows(IllegalArgumentException.class,
                () -> state.setRelation("n1", "n1", DiplomaticState.ALIANZA));
    }

    @Test
    void elTurnoAvanza() {
        assertEquals(1, state.turn());
        state.advanceTurn();
        assertEquals(2, state.turn());
    }

    @Test
    void nacionesVivas() {
        assertEquals(2, state.livingNations().size());
        state.nation("n2").setEliminated(true);
        assertEquals(1, state.livingNations().size());
    }

    // ------------------------------------------------- invariantes del modelo

    @Test
    void elDescontentoSeAcotaEntre0y100() {
        Province a = state.province("a");
        a.setDiscontent(150);
        assertEquals(100, a.discontent());
        a.setDiscontent(-10);
        assertEquals(0, a.discontent());
    }

    @Test
    void laFelicidadDerivadaEsInversaDelDescontento() {
        Province a = state.province("a");
        a.setDiscontent(20);
        assertEquals(80, a.happiness(), 1e-9);
        a.setDiscontent(100);
        assertEquals(0, a.happiness(), 1e-9);
        a.setDiscontent(0);
        assertEquals(100, a.happiness(), 1e-9);
    }

    @Test
    void lasTropasYPoblacionNoSonNegativas() {
        Province a = state.province("a");
        a.setTroops(-5);
        assertEquals(0, a.troops());
        a.setPopulation(-100);
        assertEquals(0, a.population());
    }

    @Test
    void lasZonasMaritimasRechazanDuenoYPoblacion() {
        Province mar = state.province("mar");
        assertThrows(IllegalStateException.class, () -> mar.setOwnerId("n1"));
        assertThrows(IllegalStateException.class, () -> mar.setPopulation(100));
    }

    @Test
    void laFortificacionSeAcotaEntre0y4() {
        Province a = state.province("a");
        a.setFortification(5);
        assertEquals(4, a.fortification());
        a.setFortification(-1);
        assertEquals(0, a.fortification());
    }

    @Test
    void isFortifiedEsTrueSiNivelMayorQue1() {
        Province a = state.province("a");
        assertFalse(a.isFortified());
        a.setFortification(1);
        assertTrue(a.isFortified());
    }

    @Test
    void lasZonasMaritimasNoPuedenTenerFortificacion() {
        Province mar = state.province("mar");
        assertThrows(IllegalStateException.class, () -> mar.setOwnerId("n1"));
    }
}
