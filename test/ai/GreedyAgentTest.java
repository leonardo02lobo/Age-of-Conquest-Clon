package ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import engine.TurnEngine;
import engine.TurnReport;
import java.nio.file.Path;
import map.ScenarioLoader;
import model.GameState;
import model.Nation;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de la IA heurística sobre un mini-mapa:
 *
 *   capital(ia1, 30, rey) — frontera(ia1, 60) — debil(neutral, 10)
 *                                             — fuerte(neutral, 500) — lejos(neutral, 5) — rincon(otro, 10)
 */
class GreedyAgentTest {

    private static final String IA_MAP = """
            {"nombre": "IA",
             "provincias": [
               {"id": "capital", "poblacion": 500000, "adyacentes": ["frontera"]},
               {"id": "frontera", "poblacion": 200000, "adyacentes": ["debil", "fuerte"]},
               {"id": "debil", "poblacion": 300000, "tropas": 10},
               {"id": "fuerte", "poblacion": 300000, "tropas": 500, "adyacentes": ["lejos"]},
               {"id": "lejos", "poblacion": 100000, "tropas": 5, "adyacentes": ["rincon"]},
               {"id": "rincon", "poblacion": 100000}],
             "naciones": [
               {"id": "ia1", "oro": 100, "ia": true, "rey": "capital",
                "provincias": {"capital": 30, "frontera": 60}},
               {"id": "otro", "oro": 100, "ia": false, "provincias": {"rincon": 10}}]}
            """;

    private final GreedyAgent agent = new GreedyAgent();

    private TurnEngine engine(String json) {
        GameState state = ScenarioLoader.fromJson(json);
        state.rules().revoltRiskK = 0; // sin revueltas: la IA se prueba en determinista puro
        return new TurnEngine(state);
    }

    @Test
    void conquistaAlVecinoDebilYRespetaAlFuerte() {
        TurnEngine engine = engine(IA_MAP);
        agent.plan(engine, engine.state().nation("ia1"));
        engine.endTurn();
        // frontera (59 disponibles) ≥ 1.5 · 10 → ataca a debil; 59 < 1.5·500 → no toca a fuerte.
        assertEquals("ia1", engine.state().province("debil").ownerId());
        assertNull(engine.state().province("fuerte").ownerId());
    }

    @Test
    void refuerzaLaFronteraDesdeElInterior() {
        TurnEngine engine = engine(IA_MAP);
        agent.plan(engine, engine.state().nation("ia1"));
        engine.endTurn();
        // capital es interior: su excedente (30 − 1 de guarnición) avanza a frontera,
        // y además el reclutamiento del excedente de oro cae en la capital (sede del rey).
        assertTrue(engine.state().province("capital").troops() != 30,
                "la capital debería haber movido su excedente y/o reclutado");
        int fronteraTroops = engine.state().province("frontera").troops();
        assertTrue(fronteraTroops >= 29, "frontera debería recibir refuerzos, tiene " + fronteraTroops);
    }

    @Test
    void reclutaConElExcedenteDeOroYFortificaAlRey() {
        TurnEngine engine = engine(IA_MAP);
        agent.plan(engine, engine.state().nation("ia1"));
        engine.endTurn();
        // Presupuesto: 100 − 20 (fortificar) − 30 (reserva) = 50 → 500 soldados en la capital.
        assertTrue(engine.state().province("capital").isFortified());
        assertTrue(engine.state().province("capital").troops() >= 500,
                "debería haber reclutado ~500 soldados");
    }

    @Test
    void decretaFiestasEnProvinciasDescontentas() {
        TurnEngine engine = engine(IA_MAP.replace(
                "{\"id\": \"frontera\", \"poblacion\": 200000,",
                "{\"id\": \"frontera\", \"poblacion\": 200000, \"felicidad\": 30,"));
        agent.plan(engine, engine.state().nation("ia1"));
        engine.endTurn();
        // Fiesta (+20) y evolución del turno: muy por encima del 30 inicial.
        assertTrue(engine.state().province("frontera").happiness() > 45,
                "la fiesta debería subir la felicidad, está en "
                        + engine.state().province("frontera").happiness());
    }

    @Test
    void bajaLosImpuestosCuandoElPuebloEstaDescontento() {
        TurnEngine engine = engine(IA_MAP
                .replace("{\"id\": \"capital\", \"poblacion\": 500000,",
                         "{\"id\": \"capital\", \"poblacion\": 500000, \"felicidad\": 20,")
                .replace("{\"id\": \"frontera\", \"poblacion\": 200000,",
                         "{\"id\": \"frontera\", \"poblacion\": 200000, \"felicidad\": 20,"));
        Nation ia1 = engine.state().nation("ia1");
        agent.plan(engine, ia1);
        assertEquals(50, ia1.taxRate()); // media 20 < 50 → baja un escalón (100 → 50)
    }

    @Test
    void subeLosImpuestosCuandoElPuebloEstaFeliz() {
        TurnEngine engine = engine(IA_MAP
                .replace("{\"id\": \"capital\", \"poblacion\": 500000,",
                         "{\"id\": \"capital\", \"poblacion\": 500000, \"felicidad\": 95,")
                .replace("{\"id\": \"frontera\", \"poblacion\": 200000,",
                         "{\"id\": \"frontera\", \"poblacion\": 200000, \"felicidad\": 95,"));
        Nation ia1 = engine.state().nation("ia1");
        agent.plan(engine, ia1);
        assertEquals(150, ia1.taxRate());
    }

    @Test
    void declaraLaGuerraAlVecinoDebilCuandoNoQuedanNeutrales() {
        // ia1 pegada a "otro" y sin neutrales alcanzables: mapa de 2 provincias.
        String json = """
                {"nombre": "Duelo",
                 "provincias": [
                   {"id": "a", "poblacion": 500000, "adyacentes": ["b"]},
                   {"id": "b", "poblacion": 100000}],
                 "naciones": [
                   {"id": "ia1", "oro": 100, "ia": true, "rey": "a", "provincias": {"a": 100}},
                   {"id": "otro", "oro": 100, "ia": false, "provincias": {"b": 10}}]}
                """;
        TurnEngine engine = engine(json);
        agent.plan(engine, engine.state().nation("ia1"));
        engine.endTurn();
        // 100 ≥ 2·10 → declara la guerra y conquista b en el mismo turno → victoria.
        assertEquals("ia1", engine.state().province("b").ownerId());
        assertTrue(engine.state().nation("otro").isEliminated());
    }

    // ------------------------------------------------- partida completa IA vs IA

    private TurnReport playFullGame() throws Exception {
        GameState state = ScenarioLoader.load(Path.of("scenarios/europa_antigua.json"));
        state.rules().maxTurns = 80;
        TurnEngine engine = new TurnEngine(state);
        GreedyAgent ai = new GreedyAgent();
        TurnReport last = null;
        while (!engine.isGameOver()) {
            for (Nation nation : state.livingNations()) {
                ai.plan(engine, nation); // todas las naciones juegan con la IA
            }
            last = engine.endTurn();
        }
        return last;
    }

    @Test
    void unaPartidaCompletaIAcontraIATermina() throws Exception {
        TurnReport last = playFullGame();
        assertNotNull(last);
        assertNotNull(last.winnerId(), "la partida debe producir un ganador");
        assertTrue(last.turn() <= 80);
    }

    @Test
    void laPartidaCompletaEsReproducibleConLaMismaSemilla() throws Exception {
        TurnReport first = playFullGame();
        TurnReport second = playFullGame();
        assertEquals(first.winnerId(), second.winnerId());
        assertEquals(first.turn(), second.turn());
    }
}
