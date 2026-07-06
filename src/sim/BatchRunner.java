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
 * Ejecuta partidas completas IA contra IA por lotes: el instrumento de
 * experimentación del estudio de simulación. Cada partida se juega sobre una
 * copia fresca del escenario, con su propia semilla y su propia configuración
 * de {@link Rules}; todas las naciones las lleva {@link GreedyAgent}, así que
 * la única fuente de variabilidad entre semillas son las revueltas.
 */
public class BatchRunner {

    /** Tope de turnos por partida: garantiza la terminación del experimento. */
    public int maxTurnsPerGame = 150;

    private final String scenarioJson;

    public BatchRunner(Path scenarioPath) throws IOException {
        this.scenarioJson = Files.readString(scenarioPath);
    }

    public BatchRunner(String scenarioJson) {
        this.scenarioJson = scenarioJson;
    }

    /**
     * Juega {@code n} partidas con semillas {@code baseSeed}, {@code baseSeed+1}, …
     * Usar las mismas semillas entre variantes de un experimento produce
     * comparaciones apareadas (misma secuencia de azar, distinto parámetro).
     */
    public List<GameResult> run(String experiment, String parameter, double value,
                                Consumer<Rules> tweak, int n, long baseSeed) {
        List<GameResult> results = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            results.add(playOne(experiment, parameter, value, tweak, baseSeed + i));
        }
        return results;
    }

    /** Juega una única partida completa y devuelve sus métricas. */
    public GameResult playOne(String experiment, String parameter, double value,
                              Consumer<Rules> tweak, long seed) {
        Rules rules = new Rules();
        rules.randomSeed = seed;
        rules.maxTurns = maxTurnsPerGame;
        tweak.accept(rules);

        GameState state = ScenarioLoader.fromJson(scenarioJson, rules);
        TurnEngine engine = new TurnEngine(state);
        GreedyAgent agent = new GreedyAgent();

        int revolts = 0;
        TurnReport last = null;
        while (!engine.isGameOver()) {
            for (Nation nation : state.livingNations()) {
                agent.plan(engine, nation); // todas las naciones juegan con la IA
            }
            last = engine.endTurn();
            for (String event : last.events()) {
                if (event.startsWith("¡Revuelta")) {
                    revolts++;
                }
            }
        }

        String winnerId = last.winnerId();
        return new GameResult(experiment, parameter, value, seed, winnerId, last.turn(),
                revolts, state.provincesOf(winnerId).size(), state.totalTroops(winnerId));
    }
}
