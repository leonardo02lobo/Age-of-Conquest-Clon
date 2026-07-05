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
 * Pruebas del motor WEGO sobre un mini-mapa controlado:
 *
 *   a(n1,50,rey) — e(n1,10)        n1 y n2 empiezan en GUERRA
 *   a — b(n2,30,rey)               c: neutral con guarnición 10
 *   a — c — b                      d: vacía, alcanzable desde a cruzando el mar w
 *   a — w(mar) — d
 */
class TurnEngineTest {

    private static final String MINI = """
            {"nombre": "Mini",
             "provincias": [
               {"id": "a", "poblacion": 100000, "adyacentes": ["e", "b", "c", "w"]},
               {"id": "e", "poblacion": 100000},
               {"id": "b", "poblacion": 100000, "adyacentes": ["c"]},
               {"id": "c", "poblacion": 100000, "tropas": 10},
               {"id": "d", "poblacion": 100000},
               {"id": "w", "agua": true, "adyacentes": ["a", "d"]}],
             "naciones": [
               {"id": "n1", "oro": 100, "ia": false, "rey": "a", "provincias": {"a": 50, "e": 10}},
               {"id": "n2", "oro": 100, "ia": false, "rey": "b", "provincias": {"b": 30},
                "relaciones": {"n1": "GUERRA"}}]}
            """;

    private GameState state;
    private TurnEngine engine;

    @BeforeEach
    void setUp() {
        state = ScenarioLoader.fromJson(MINI);
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
    void ocupaUnaProvinciaVaciaCruzandoElMar() {
        // a y d no son adyacentes por tierra, pero comparten el mar w.
        engine.submit(new Order.Move("n1", "a", "d", 15, false));
        engine.endTurn();
        assertEquals("n1", state.province("d").ownerId());
        assertEquals(15, state.province("d").troops());
    }

    @Test
    void noSePuedeSaltarDosMares() {
        // e no toca el mar w: d queda fuera de su alcance.
        assertThrows(OrderException.class,
                () -> engine.submit(new Order.Move("n1", "e", "d", 5, false)));
    }

    @Test
    void noSePuedeDetenerEnElMar() {
        assertThrows(OrderException.class,
                () -> engine.submit(new Order.Move("n1", "a", "w", 5, false)));
    }

    @Test
    void conquistaUnaGuarnicionNeutral() {
        // FA=30 vs FD=10 → supervivientes = 30·(1 − (10/30)·0.7) = 23
        engine.submit(new Order.Move("n1", "a", "c", 30, false));
        engine.endTurn();
        assertEquals("n1", state.province("c").ownerId());
        assertEquals(23, state.province("c").troops());
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
    void laFortificacionSeAplicaAntesQueLosMovimientosDelMismoTurno() {
        // n2 fortifica b y n1 ataca en el mismo turno:
        // FA = 30; FD = 30·(1 + 0.5 + 0.3 rey) = 54 → la defensa resiste;
        // defensores = 30·(1 − (30/54)·0.7) = 18.33 → 18. El atacante se aniquila.
        engine.submit(new Order.Fortify("n2", "b"));
        engine.submit(new Order.Move("n1", "a", "b", 30, false));
        engine.endTurn();
        assertEquals("n2", state.province("b").ownerId());
        assertEquals(18, state.province("b").troops());
        assertTrue(state.province("b").isFortified());
        assertEquals(20, state.province("a").troops());
    }

    @Test
    void siElAtaqueFracasaConElReyLaNacionPierdeTerritorio() {
        // FA = 10·1.3 = 13 vs FD = 30·1.3 = 39 → derrota y muerte del rey de n1.
        // n1 tenía 2 provincias: conserva max(1, floor(2·0.1)) = 1 (la de más tropas: a).
        engine.submit(new Order.Move("n1", "a", "b", 10, true));
        engine.endTurn();
        assertNull(state.nation("n1").kingProvinceId());
        assertEquals("n1", state.province("a").ownerId());
        assertNull(state.province("e").ownerId());     // e deserta y queda neutral
        assertEquals(10, state.province("e").troops()); // conservando su guarnición
        assertFalse(state.nation("n1").isEliminated());
    }

    @Test
    void matarAlReyDeUnaNacionDeUnaProvinciaLaElimina() {
        // FA = 45 vs FD = 30·1.3 (rey defensor) = 39 → conquista de b;
        // n2 pierde a su rey y no tiene más provincias → eliminada → gana n1.
        engine.submit(new Order.Move("n1", "a", "b", 45, false));
        TurnReport report = engine.endTurn();
        assertEquals("n1", state.province("b").ownerId());
        assertTrue(state.nation("n2").isEliminated());
        assertEquals("n1", report.winnerId());
        assertTrue(engine.isGameOver());
        assertThrows(OrderException.class,
                () -> engine.submit(new Order.Move("n1", "a", "c", 1, false)));
    }

    @Test
    void quienLlegaPrimeroTomaLaProvinciaYElSegundoDebeCombatirla() {
        // n1 llega primero a c (orden de emisión): la conquista con 23 supervivientes.
        // n2 llega después y ataca a n1: FA=20 vs FD=23 → defensa; 23·(1−(20/23)·0.7)=9
        engine.submit(new Order.Move("n1", "a", "c", 30, false));
        engine.submit(new Order.Move("n2", "b", "c", 20, false));
        engine.endTurn();
        assertEquals("n1", state.province("c").ownerId());
        assertEquals(9, state.province("c").troops());
    }

    @Test
    void losReyesMuevenAntesQueLasTropasNormales() {
        // n2 emite primero, pero n1 mueve con su rey: n1 llega antes a c.
        // n1: FA = 30·1.3 = 39 vs 10 → conquista, 30·(1−(10/39)·0.7) = 24.6 → 25
        // n2 después: FA = 20 vs FD = 25·1.3 (rey presente) = 32.5 → defensa;
        //             defensores = 25·(1−(20/32.5)·0.7) = 14.2 → 14
        engine.submit(new Order.Move("n2", "b", "c", 20, false));
        engine.submit(new Order.Move("n1", "a", "c", 30, true));
        engine.endTurn();
        assertEquals("n1", state.province("c").ownerId());
        assertEquals(14, state.province("c").troops());
        assertEquals("c", state.nation("n1").kingProvinceId());
    }

    // ------------------------------------------------------ economía de órdenes

    @Test
    void reclutarCobraOroYPoblacionYEntregaTropasAlResolver() {
        engine.submit(new Order.Recruit("n1", "a", 100));
        assertEquals(90.0, state.nation("n1").gold(), 1e-9);          // 100 · 0.1 de oro
        assertEquals(99_800, state.province("a").population());       // 100 · 2 habitantes
        assertEquals(50, state.province("a").troops());               // aún sin entregar
        engine.endTurn();
        assertEquals(150, state.province("a").troops());
    }

    @Test
    void reclutarSinOroSuficienteEsIlegal() {
        assertThrows(OrderException.class,
                () -> engine.submit(new Order.Recruit("n1", "a", 1001))); // costaría 100.1
    }

    @Test
    void fortificarCuestaOroYSoloUnaVez() {
        engine.submit(new Order.Fortify("n1", "a"));
        assertEquals(80.0, state.nation("n1").gold(), 1e-9);
        assertThrows(OrderException.class, () -> engine.submit(new Order.Fortify("n1", "a")));
        engine.endTurn();
        assertTrue(state.province("a").isFortified());
    }

    @Test
    void losPuntosDeAccionLimitanLasOrdenesPorTurno() {
        // n1: 2 provincias → 3 + 0.5·2 = 4 AP → 8 órdenes de 0.5 AP.
        for (int i = 0; i < 8; i++) {
            engine.submit(new Order.Move("n1", "a", "e", 1, false));
        }
        assertThrows(OrderException.class,
                () -> engine.submit(new Order.Move("n1", "a", "e", 1, false)));
    }

    @Test
    void losPuntosDeAccionSeRenuevanAlCambiarDeTurno() {
        engine.submit(new Order.Move("n1", "a", "c", 30, false)); // conquista c
        engine.endTurn();
        // n1 pasa a tener 3 provincias → 3 + 0.5·3 = 4.5 AP
        assertEquals(4.5, state.nation("n1").actionPoints(), 1e-9);
        assertEquals(2, state.turn());
    }

    // -------------------------------------------------------------- diplomacia

    @Test
    void sinGuerraNoSePuedeAtacarYConDeclaracionSi() {
        // El mismo mapa pero con n1 y n2 en paz.
        GameState pacifico = ScenarioLoader.fromJson(MINI.replace("\"GUERRA\"", "\"NEUTRAL\""));
        TurnEngine engine2 = new TurnEngine(pacifico);

        assertThrows(OrderException.class,
                () -> engine2.submit(new Order.Move("n1", "a", "b", 20, false)));

        engine2.submit(new Order.DeclareWar("n1", "n2"));
        assertEquals(DiplomaticState.GUERRA, pacifico.relation("n2", "n1"));
        engine2.submit(new Order.Move("n1", "a", "b", 20, false)); // ya es legal
    }

    @Test
    void noSePuedeDeclararLaGuerraDosVeces() {
        assertThrows(OrderException.class, () -> engine.submit(new Order.DeclareWar("n1", "n2")));
    }

    // ---------------------------------------------------------------- victoria

    @Test
    void conLimiteDeTurnosGanaElMejorClasificado() {
        state.rules().maxTurns = 1;
        TurnReport report = engine.endTurn(); // sin órdenes
        assertEquals("n1", report.winnerId()); // n1: 2 provincias vs 1 de n2
        assertTrue(engine.isGameOver());
    }

    @Test
    void ordenesDeOtrasProvinciasYNacionesSonRechazadas() {
        assertThrows(OrderException.class,
                () -> engine.submit(new Order.Move("n1", "b", "c", 5, false))); // b es de n2
        assertThrows(OrderException.class,
                () -> engine.submit(new Order.Fortify("n1", "c")));             // c es neutral
        assertThrows(OrderException.class,
                () -> engine.submit(new Order.Recruit("nx", "a", 5)));          // nación inexistente
    }

    @Test
    void elReySoloPuedeComprometerseAUnMovimientoPorTurno() {
        engine.submit(new Order.Move("n1", "a", "e", 5, true));
        assertThrows(OrderException.class,
                () -> engine.submit(new Order.Move("n1", "a", "c", 5, true)));
    }
}
