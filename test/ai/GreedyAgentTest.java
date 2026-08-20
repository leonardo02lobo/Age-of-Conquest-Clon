package ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import engine.TurnEngine;
import engine.TurnReport;
import java.nio.file.Path;
import map.ScenarioLoader;
import model.GameState;
import model.Nation;
import model.Rules;
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
               {"id": "capital", "poblacion": 10000, "adyacentes": ["frontera"]},
               {"id": "frontera", "poblacion": 4000, "adyacentes": ["debil", "fuerte"]},
               {"id": "debil", "poblacion": 6000, "tropas": 10},
               {"id": "fuerte", "poblacion": 6000, "tropas": 500, "adyacentes": ["lejos"]},
               {"id": "lejos", "poblacion": 2000, "tropas": 5, "adyacentes": ["rincon"]},
               {"id": "rincon", "poblacion": 2000}],
             "naciones": [
               {"id": "ia1", "oro": 100, "ia": true, "rey": "capital",
                "provincias": {"capital": 30, "frontera": 60}},
               {"id": "otro", "oro": 100, "ia": false, "provincias": {"rincon": 10}}]}
            """;

    private final GreedyAgent agent = new GreedyAgent();

    private TurnEngine engine(String json) {
        GameState state = ScenarioLoader.fromJson(json);
        return new TurnEngine(state);
    }

    @Test
    void conquistaAlVecinoDebilYRespetaAlFuerte() {
        TurnEngine engine = engine(IA_MAP);
        agent.plan(engine, engine.state().nation("ia1"));
        engine.endTurn();
        // frontera (59 disponibles) >= 1.5 · 10 → ataca a debil; 59 < 1.5·500 → no toca a fuerte.
        assertEquals("ia1", engine.state().province("debil").ownerId());
    }

    @Test
    void reclutaConElExcedenteDeOro() {
        TurnEngine engine = engine(IA_MAP);
        agent.plan(engine, engine.state().nation("ia1"));
        engine.endTurn();
        // La IA debería haber reclutado soldados con el oro disponible.
        int totalTroops = engine.state().totalTroops("ia1");
        assertTrue(totalTroops >= 90,
                "debería haber reclutado más tropas, tiene " + totalTroops);
    }

    @Test
    void fortificaProvinciasClave() {
        TurnEngine engine = engine(IA_MAP);
        agent.plan(engine, engine.state().nation("ia1"));
        engine.endTurn();
        // La IA debería haber fortificado al menos una provincia.
        int totalFort = 0;
        for (var p : engine.state().provincesOf("ia1")) {
            totalFort += p.fortification();
        }
        assertTrue(totalFort > 0, "debería haber fortificado al menos una provincia");
    }

    @Test
    void bajaLosImpuestosCuandoElPuebloEstaDescontento() {
        TurnEngine engine = engine(IA_MAP
                .replace("{\"id\": \"capital\", \"poblacion\": 500000,",
                         "{\"id\": \"capital\", \"poblacion\": 500000, \"descontento\": 80,")
                .replace("{\"id\": \"frontera\", \"poblacion\": 200000,",
                         "{\"id\": \"frontera\", \"poblacion\": 200000, \"descontento\": 80,"));
        Nation ia1 = engine.state().nation("ia1");
        agent.plan(engine, ia1);
        assertTrue(ia1.taxRate() <= 100,
                "debería bajar impuestos con descontento alto, tasa=" + ia1.taxRate());
    }

    @Test
    void subeLosImpuestosCuandoElPuebloEstaFeliz() {
        TurnEngine engine = engine(IA_MAP
                .replace("{\"id\": \"capital\", \"poblacion\": 500000,",
                         "{\"id\": \"capital\", \"poblacion\": 500000, \"descontento\": 0,")
                .replace("{\"id\": \"frontera\", \"poblacion\": 200000,",
                         "{\"id\": \"frontera\", \"poblacion\": 200000, \"descontento\": 0,"));
        Nation ia1 = engine.state().nation("ia1");
        agent.plan(engine, ia1);
        assertTrue(ia1.taxRate() >= 100,
                "debería subir impuestos con descontento bajo, tasa=" + ia1.taxRate());
    }

    @Test
    void declaraLaGuerraAlVecinoDebilCuandoNoQuedanNeutrales() {
        // ia1 pegada a "otro" y sin neutrales alcanzables: mapa de 2 provincias.
        String json = """
                {"nombre": "Duelo",
                 "provincias": [
                   {"id": "a", "poblacion": 10000, "adyacentes": ["b"]},
                   {"id": "b", "poblacion": 2000}],
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
        state.rules().tMax = 80;
        TurnEngine engine = new TurnEngine(state);
        GreedyAgent ai = new GreedyAgent();
        TurnReport last = null;
        while (!engine.isGameOver()) {
            for (Nation nation : state.livingNations()) {
                ai.plan(engine, nation);
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
