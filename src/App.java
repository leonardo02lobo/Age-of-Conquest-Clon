import java.nio.file.Path;
import map.ScenarioLoader;
import model.GameState;
import model.Nation;
import model.Province;
import ui.ConsoleGame;

/**
 * Punto de entrada del clon de Age of Conquest.
 * Carga un escenario, muestra el estado inicial y arranca una partida hotseat
 * por consola (fase M2).
 *
 * Uso: java App [escenario.json] [--solo-resumen]
 */
public class App {

    public static void main(String[] args) throws Exception {
        Path scenarioPath = Path.of("scenarios/europa_antigua.json");
        boolean summaryOnly = false;
        for (String arg : args) {
            if (arg.equals("--solo-resumen")) {
                summaryOnly = true;
            } else {
                scenarioPath = Path.of(arg);
            }
        }

        GameState state = ScenarioLoader.load(scenarioPath);
        printSummary(state);

        if (!summaryOnly) {
            new ConsoleGame(state).run();
        }
    }

    private static void printSummary(GameState state) {
        System.out.println("=== " + state.scenarioName() + " — turno " + state.turn() + " ===");
        System.out.println();

        for (Nation nation : state.nations()) {
            System.out.printf("%s%s — oro: %.0f, AP: %.1f, tropas: %d%n",
                    nation.name(),
                    nation.isAI() ? " [IA]" : "",
                    nation.gold(),
                    nation.actionPoints(),
                    state.totalTroops(nation.id()));
            for (Province p : state.provincesOf(nation.id())) {
                String king = p.id().equals(nation.kingProvinceId()) ? " ♔" : "";
                System.out.printf("    %-18s pob: %,8d  felicidad: %3.0f%%  tropas: %3d%s%n",
                        p.name(), p.population(), p.happiness(), p.troops(), king);
            }
            System.out.println();
        }

        System.out.println("Provincias neutrales:");
        for (Province p : state.provinces()) {
            if (!p.isWater() && p.isNeutral()) {
                System.out.printf("    %-18s pob: %,8d  guarnición: %3d%n",
                        p.name(), p.population(), p.troops());
            }
        }

        System.out.println();
        long waters = state.provinces().stream().filter(Province::isWater).count();
        System.out.printf("Mapa: %d provincias (%d de tierra, %d marítimas), %d naciones%n",
                state.provinces().size(), state.provinces().size() - waters, waters,
                state.nations().size());
    }
}
