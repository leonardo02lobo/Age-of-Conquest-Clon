package sim;

import ai.GreedyAgent;
import engine.TurnEngine;
import engine.TurnReport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import map.ScenarioLoader;
import model.GameState;
import model.Nation;
import model.Rules;

/**
 * Ejecuta partidas completas IA contra IA por lotes.
 * Cada partida se juega sobre una copia fresca del escenario,
 * con su propia semilla y su propia configuración de Rules.
 */
public class BatchRunner {

    public int maxTurnsPerGame = 200;

    private final String scenarioJson;

    public BatchRunner(Path scenarioPath) throws IOException {
        this.scenarioJson = Files.readString(scenarioPath);
    }

    public BatchRunner(String scenarioJson) {
        this.scenarioJson = scenarioJson;
    }

    public List<GameResult> run(String experiment, String parameter, double value,
                                Consumer<Rules> tweak, int n, long baseSeed) {
        List<GameResult> results = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            results.add(playOne(experiment, parameter, value, tweak, baseSeed + i));
        }
        return results;
    }

    public GameResult playOne(String experiment, String parameter, double value,
                              Consumer<Rules> tweak, long seed) {
        Rules rules = new Rules();
        rules.randomSeed = seed;
        rules.tMax = maxTurnsPerGame;
        tweak.accept(rules);

        GameState state = ScenarioLoader.fromJson(scenarioJson, rules);
        TurnEngine engine = new TurnEngine(state);
        GreedyAgent agent = new GreedyAgent();

        int insolvencies = 0;
        TurnReport last = null;
        while (!engine.isGameOver()) {
            for (Nation nation : state.livingNations()) {
                agent.plan(engine, nation);
            }
            last = engine.endTurn();
            for (String event : last.events()) {
                if (event.contains("insolvencia") || event.contains("INSOLVENCIA")) {
                    insolvencies++;
                }
            }
        }

        String winnerId = last.winnerId();
        return new GameResult(experiment, parameter, value, seed, winnerId, last.turn(),
                insolvencies, state.provincesOf(winnerId).size(), state.totalTroops(winnerId));
    }
}
