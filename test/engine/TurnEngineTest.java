package engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import map.ScenarioLoader;
import model.DiplomaticState;
import model.GameState;
import model.Province;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Pruebas del motor WEGO sobre un mini-mapa controlado.
 */
class TurnEngineTest {

    private static final String MINI = """
            {"nombre": "Mini",
             "provincias": [
               {"id": "a", "poblacion": 5000, "adyacentes": ["e", "b", "c"]},
               {"id": "e", "poblacion": 5000},
               {"id": "b", "poblacion": 5000, "adyacentes": ["c"]},
               {"id": "c", "poblacion": 5000, "tropas": 10}],
             "naciones": [
               {"id": "n1", "oro": 200, "ia": false, "rey": "a",
                "provincias": {"a": 50, "e": 10}},
               {"id": "n2", "oro": 200, "ia": false, "rey": "b",
                "provincias": {"b": 30},
                "relaciones": {"n1": "GUERRA"}}]}
            """;

    private GameState state;
    private TurnEngine engine;

    @BeforeEach
    void setUp() {
        state = ScenarioLoader.fromJson(MINI);
        state.rules().thetaAm = 10.0;
        engine = new TurnEngine(state);
    }

    // ------------------------------------------------------------ movimientos

    @Test
    void moverEntreProvinciasPropiasFusionaTropas() {
        engine.submit(new Order.Move("n1", "a", "e", 20, false));
        engine.endTurn();
        assertEquals(30, state.province("a").troops());
        assertEquals(30, state.province("e").troops());
    }

    @Test
    void elReyViajaConElEjercitoSiSeIndica() {
        engine.submit(new Order.Move("n1", "a", "e", 5, true));
        engine.endTurn();
        assertEquals("e", state.nation("n1").kingProvinceId());
    }

    @Test
    void conquistaUnaGuarnicionNeutral() {
        engine.submit(new Order.Move("n1", "a", "c", 30, false));
        engine.endTurn();
        assertEquals("n1", state.province("c").ownerId());
        assertTrue(state.province("c").troops() > 0);
        assertEquals(20, state.province("a").troops());
    }

    @Test
    void noSePuedenComprometerMasTropasDeLasDisponibles() {
        engine.submit(new Order.Move("n1", "a", "e", 40, false));
        assertThrows(OrderException.class,
                () -> engine.submit(new Order.Move("n1", "a", "c", 20, false)));
    }

    // --------------------------------------------------------------- combates

    @Test
    void fortificacionProtegeAlDefensor() {
        state.province("b").setFortification(2);
        engine.submit(new Order.Move("n1", "a", "b", 20, false));
        engine.endTurn();
        assertEquals("n2", state.province("b").ownerId());
        assertEquals(2, state.province("b").fortification());
    }

    @Test
    void siElAtaqueFracasaConElReyLaNacionPierdeTerritorio() {
        engine.submit(new Order.Move("n1", "a", "b", 10, true));
        engine.endTurn();
        assertNull(state.nation("n1").kingProvinceId());
        // n1 pierde territorio por muerte del rey
        assertTrue(state.provincesOf("n1").size() <= 2);
    }

    @Test
    void conquistaEliminaAlOponenteSiPierdeTodasLasProvincias() {
        engine.submit(new Order.Move("n1", "a", "b", 50, false));
        engine.endTurn();
        assertTrue(state.nation("n2").isEliminated());
    }

    // ------------------------------------------------------ economía de órdenes

    @Test
    void reclutarCobraOroYPoblacion() {
        engine.submit(new Order.Recruit("n1", "a", 10));
        // c_u = 1.5, ϱ = 2.0
        assertEquals(200 - 10 * 1.5, state.nation("n1").gold(), 1e-9);
        assertEquals(5000 - 10 * 2, state.province("a").population());
        engine.endTurn();
        assertEquals(50 + 10, state.province("a").troops());
    }

    @Test
    void reclutarSinOroSuficienteEsIlegal() {
        assertThrows(OrderException.class,
                () -> engine.submit(new Order.Recruit("n1", "a", 200)));
    }

    @Test
    void fortificarCuestaOro() {
        engine.submit(new Order.Fortify("n1", "a"));
        assertEquals(200 - 40, state.nation("n1").gold(), 1e-9);
        engine.endTurn();
        assertEquals(1, state.province("a").fortification());
    }

    @Test
    void fortificarNivelMaximoEsIlegal() {
        state.province("a").setFortification(4);
        assertThrows(OrderException.class,
                () -> engine.submit(new Order.Fortify("n1", "a")));
    }

    // -------------------------------------------------------------- diplomacia

    @Test
    void sinGuerraNoSePuedeAtacar() {
        GameState pacifico = ScenarioLoader.fromJson(MINI.replace("\"GUERRA\"", "\"NEUTRAL\""));
        TurnEngine engine2 = new TurnEngine(pacifico);
        assertThrows(OrderException.class,
                () -> engine2.submit(new Order.Move("n1", "a", "b", 20, false)));
    }

    @Test
    void conDeclaracionSiSePuedeAtacar() {
        GameState pacifico = ScenarioLoader.fromJson(MINI.replace("\"GUERRA\"", "\"NEUTRAL\""));
        TurnEngine engine2 = new TurnEngine(pacifico);
        engine2.submit(new Order.DeclareWar("n1", "n2"));
        engine2.submit(new Order.Move("n1", "a", "b", 20, false));
    }

    @Test
    void noSePuedeDeclararLaGuerraDosVeces() {
        assertThrows(OrderException.class,
                () -> engine.submit(new Order.DeclareWar("n1", "n2")));
    }

    // ---------------------------------------------------------------- victoria

    @Test
    void conLimiteDeTurnosGanaElMejorClasificado() {
        state.rules().tMax = 1;
        TurnReport report = engine.endTurn();
        assertEquals("n1", report.winnerId());
        assertTrue(engine.isGameOver());
    }

    @Test
    void ordenesDeOtrasNacionesSonRechazadas() {
        assertThrows(OrderException.class,
                () -> engine.submit(new Order.Move("n1", "b", "c", 5, false)));
        assertThrows(OrderException.class,
                () -> engine.submit(new Order.Recruit("nx", "a", 5)));
    }

    @Test
    void elReySoloPuedeComprometerseAUnMovimientoPorTurno() {
        engine.submit(new Order.Move("n1", "a", "e", 5, true));
        assertThrows(OrderException.class,
                () -> engine.submit(new Order.Move("n1", "a", "c", 5, true)));
    }
}
