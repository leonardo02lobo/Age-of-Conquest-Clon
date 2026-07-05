import java.nio.file.Path;
import map.ScenarioLoader;
import model.GameState;
import model.Nation;
import model.Province;

/**
 * Punto de entrada del clon de Age of Conquest.
 * Fase M1: carga un escenario y muestra el estado inicial de la partida.
 */
public class App {

    public static void main(String[] args) throws Exception {
        Path scenario = Path.of(args.length > 0 ? args[0] : "scenarios/europa_antigua.json");
        GameState state = ScenarioLoader.load(scenario);

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
