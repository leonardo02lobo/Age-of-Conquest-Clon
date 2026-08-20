package sim;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import model.Rules;

/**
 * Punto de entrada de los experimentos de simulación por lotes (fase M5).
 *
 * Uso:
 *   java -cp "lib/gson-2.11.0.jar:bin" sim.Simulacion \
 *        [escenario.json] [--experimento base|fortificacion|desgaste|revueltas|todos] \
 *        [--n 100] [--semilla 1000]
 *
 * Cada experimento juega N partidas IA contra IA por valor del parámetro,
 * con las mismas semillas entre valores (comparaciones apareadas), imprime un
 * resumen estadístico y exporta las partidas a resultados/&lt;experimento&gt;.csv.
 */
public final class Simulacion {

    /** Una variante experimental: un valor concreto de un parámetro de Rules. */
    private record Variant(String parameter, double value, Consumer<Rules> tweak) {
    }

    private Simulacion() {
    }

    public static void main(String[] args) throws IOException {
        Path scenario = Path.of("scenarios/europa_antigua.json");
        String experimentName = "base";
        int n = 100;
        long baseSeed = 1000;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--experimento" -> experimentName = args[++i];
                case "--n" -> n = Integer.parseInt(args[++i]);
                case "--semilla" -> baseSeed = Long.parseLong(args[++i]);
                default -> scenario = Path.of(args[i]);
            }
        }

        Map<String, List<Variant>> experiments = experiments();
        List<String> toRun = experimentName.equals("todos")
                ? new ArrayList<>(experiments.keySet())
                : List.of(experimentName);

        BatchRunner runner = new BatchRunner(scenario);
        for (String name : toRun) {
            List<Variant> variants = experiments.get(name);
            if (variants == null) {
                System.err.println("Experimento desconocido: '" + name
                        + "' (disponibles: " + String.join(", ", experiments.keySet()) + ", todos)");
                return;
            }
            runExperiment(runner, name, variants, n, baseSeed);
        }
    }

    /** Experimentos predefinidos: qué parámetro de Rules se varía y con qué valores. */
    private static Map<String, List<Variant>> experiments() {
        Map<String, List<Variant>> experiments = new LinkedHashMap<>();

        experiments.put("base", List.of(new Variant("base", 0, r -> {})));

        // Coeficiente de bajas K_B: letalidad del combate (ec. 3.19)
        List<Variant> kb = new ArrayList<>();
        for (double v : new double[]{0.3, 0.5, 0.7, 0.9, 1.0}) {
            kb.add(new Variant("kBeta", v, r -> r.kBeta = v));
        }
        experiments.put("desgaste", kb);

        // Bonificación defensiva por fortificación β_F (ec. 3.16)
        List<Variant> fort = new ArrayList<>();
        for (double v : new double[]{0.0, 0.10, 0.15, 0.25, 0.40}) {
            fort.add(new Variant("betaF", v, r -> r.betaF = v));
        }
        experiments.put("fortificacion", fort);

        // Sensibilidad fiscal η_θ (ec. 3.6)
        List<Variant> eta = new ArrayList<>();
        for (double v : new double[]{0.02, 0.04, 0.06, 0.10, 0.15}) {
            eta.add(new Variant("etaTheta", v, r -> r.etaTheta = v));
        }
        experiments.put("sensibilidad_fiscal", eta);

        // Tasa de crecimiento poblacional g_L (ec. 3.10)
        List<Variant> growth = new ArrayList<>();
        for (double v : new double[]{0.005, 0.01, 0.02, 0.03, 0.05}) {
            growth.add(new Variant("gL", v, r -> r.gL = v));
        }
        experiments.put("crecimiento", growth);

        return experiments;
    }

    private static void runExperiment(BatchRunner runner, String name, List<Variant> variants,
                                      int n, long baseSeed) throws IOException {
        System.out.printf("%n=== Experimento '%s': %d partidas × %d variante(s) ===%n",
                name, n, variants.size());

        List<GameResult> all = new ArrayList<>();
        for (Variant variant : variants) {
            long start = System.nanoTime();
            List<GameResult> results = runner.run(name, variant.parameter(), variant.value(),
                    variant.tweak(), n, baseSeed);
            all.addAll(results);
            printSummary(variant, results, (System.nanoTime() - start) / 1_000_000);
        }

        Path out = Path.of("resultados", name + ".csv");
        Files.createDirectories(out.getParent());
        List<String> lines = new ArrayList<>();
        lines.add(GameResult.csvHeader());
        for (GameResult result : all) {
            lines.add(result.toCsvRow());
        }
        Files.write(out, lines);
        System.out.println("Resultados exportados a " + out);
    }

    private static void printSummary(Variant variant, List<GameResult> results, long millis) {
        Map<String, Integer> wins = new LinkedHashMap<>();
        double totalTurns = 0;
        int minTurns = Integer.MAX_VALUE;
        int maxTurns = 0;
        double totalRevolts = 0;
        for (GameResult r : results) {
            wins.merge(r.winnerId(), 1, Integer::sum);
            totalTurns += r.turns();
            minTurns = Math.min(minTurns, r.turns());
            maxTurns = Math.max(maxTurns, r.turns());
            totalRevolts += r.revolts();
        }
        int n = results.size();
        StringBuilder winners = new StringBuilder();
        wins.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue())
                .forEach(e -> winners.append(String.format(Locale.ROOT, "%s %.0f%%  ",
                        e.getKey(), 100.0 * e.getValue() / n)));
        System.out.printf(Locale.ROOT,
                "  %s=%-8s turnos: media %.1f [%d–%d]   revueltas/partida: %.1f   victorias: %s(%d ms)%n",
                variant.parameter(), trim(variant.value()), totalTurns / n, minTurns, maxTurns,
                totalRevolts / n, winners, millis);
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
