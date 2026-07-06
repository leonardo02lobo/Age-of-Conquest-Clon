package ui;

import ai.Agent;
import ai.GreedyAgent;
import engine.Order;
import engine.OrderException;
import engine.TurnEngine;
import engine.TurnReport;
import java.util.Locale;
import java.util.Scanner;
import model.GameState;
import model.Nation;
import model.Province;

/**
 * Partida por consola: las naciones humanas planifican en modo hotseat con el
 * teclado y las naciones marcadas como IA juegan solas con {@link GreedyAgent}.
 */
public class ConsoleGame {

    /** Tope de seguridad para partidas donde solo juegan IAs. */
    private static final int MAX_CONSOLE_TURNS = 1000;

    private final TurnEngine engine;
    private final GameState state;
    private final Scanner in = new Scanner(System.in);
    private final Agent aiAgent = new GreedyAgent();

    public ConsoleGame(GameState state) {
        this.state = state;
        this.engine = new TurnEngine(state);
    }

    public void run() {
        System.out.println();
        System.out.println("=== " + state.scenarioName() + " — nueva partida ===");
        System.out.println("Escribe 'ayuda' para ver los comandos.");

        while (!engine.isGameOver() && state.turn() <= MAX_CONSOLE_TURNS) {
            System.out.println();
            System.out.println("──────── Turno " + state.turn() + " ────────");
            for (Nation nation : state.livingNations()) {
                if (nation.isAI()) {
                    System.out.println("(IA) " + nation.name() + " planifica sus órdenes…");
                    aiAgent.plan(engine, nation);
                } else if (!planningPhase(nation)) {
                    System.out.println("Partida interrumpida.");
                    return;
                }
            }
            TurnReport report = engine.endTurn();
            System.out.println();
            System.out.println("Resolución del turno " + report.turn() + ":");
            if (report.events().isEmpty()) {
                System.out.println("    (sin novedades)");
            }
            for (String event : report.events()) {
                System.out.println("    " + event);
            }
        }
    }

    /** Fase de planificación de una nación. Devuelve false si se agotó la entrada. */
    private boolean planningPhase(Nation nation) {
        System.out.println();
        String season = state.rules().isTaxSeason(state.turn()) ? " — ¡temporada fiscal!" : "";
        System.out.printf("Turno de %s — oro: %.1f, AP: %.1f, impuestos: %d%%%s%n",
                nation.name(), nation.gold(), nation.actionPoints(), nation.taxRate(), season);
        while (true) {
            System.out.print(nation.id() + "> ");
            if (!in.hasNextLine()) {
                return false;
            }
            String line = in.nextLine().trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] parts = line.split("\\s+");
            try {
                switch (parts[0].toLowerCase(Locale.ROOT)) {
                    case "fin" -> {
                        return true;
                    }
                    case "ayuda" -> printHelp();
                    case "mapa" -> printMap();
                    case "nacion" -> printNation(nation);
                    case "ver" -> printProvince(arg(parts, 1));
                    case "mover" -> {
                        boolean withKing = parts.length > 4 && parts[4].equalsIgnoreCase("rey");
                        engine.submit(new Order.Move(nation.id(), arg(parts, 1), arg(parts, 2),
                                Integer.parseInt(arg(parts, 3)), withKing));
                        System.out.println("    Orden registrada.");
                    }
                    case "reclutar" -> {
                        engine.submit(new Order.Recruit(nation.id(), arg(parts, 1),
                                Integer.parseInt(arg(parts, 2))));
                        System.out.println("    Orden registrada.");
                    }
                    case "fortificar" -> {
                        engine.submit(new Order.Fortify(nation.id(), arg(parts, 1)));
                        System.out.println("    Orden registrada.");
                    }
                    case "guerra" -> {
                        engine.submit(new Order.DeclareWar(nation.id(), arg(parts, 1)));
                        System.out.println("    ¡Guerra declarada!");
                    }
                    case "saquear" -> {
                        engine.submit(new Order.Pillage(nation.id(), arg(parts, 1)));
                        System.out.println("    Orden registrada.");
                    }
                    case "repartir" -> {
                        engine.submit(new Order.Decree(nation.id(), arg(parts, 1),
                                Order.DecreeType.REPARTIR));
                        System.out.println("    Orden registrada.");
                    }
                    case "fiesta" -> {
                        engine.submit(new Order.Decree(nation.id(), arg(parts, 1),
                                Order.DecreeType.FIESTA));
                        System.out.println("    Orden registrada.");
                    }
                    case "festival" -> {
                        engine.submit(new Order.Decree(nation.id(), arg(parts, 1),
                                Order.DecreeType.FESTIVAL));
                        System.out.println("    Orden registrada.");
                    }
                    case "impuestos" -> {
                        engine.submit(new Order.SetTaxRate(nation.id(),
                                Integer.parseInt(arg(parts, 1))));
                        System.out.println("    Tasa fijada al " + arg(parts, 1) + "%.");
                    }
                    default -> System.out.println("    Comando desconocido; escribe 'ayuda'.");
                }
            } catch (OrderException e) {
                System.out.println("    ✗ " + e.getMessage());
            } catch (NumberFormatException e) {
                System.out.println("    ✗ Cantidad inválida: se esperaba un número");
            } catch (IllegalArgumentException e) {
                System.out.println("    ✗ " + e.getMessage());
            }
        }
    }

    private String arg(String[] parts, int index) {
        if (index >= parts.length) {
            throw new OrderException("Faltan argumentos; escribe 'ayuda'");
        }
        return parts[index];
    }

    private void printHelp() {
        System.out.println("""
                    Comandos (los AP y el oro se descuentan al ordenar):
                      mover <origen> <destino> <tropas> [rey]  ataca o refuerza (rey: +30% en combate)
                      reclutar <provincia> <soldados>          consume oro y población
                      fortificar <provincia>                   +50% defensivo permanente
                      guerra <nacion>                          declara la guerra (efecto inmediato)
                      saquear <provincia>                      población propia → oro (−felicidad)
                      repartir <provincia>                     decreto: +10 de felicidad
                      fiesta <provincia>                       decreto: +20 de felicidad
                      festival <provincia>                     decreto: +20% de población
                      impuestos <0|50|100|150|200>             solo en temporada fiscal
                      mapa | nacion | ver <provincia>          información
                      fin                                      termina tu planificación
                """);
    }

    private void printMap() {
        for (Nation nation : state.nations()) {
            if (nation.isEliminated()) {
                continue;
            }
            System.out.printf("    %s%s — oro: %.1f, AP: %.1f, tropas: %d%n",
                    nation.name(), nation.isAI() ? " [IA]" : "",
                    nation.gold(), nation.actionPoints(), state.totalTroops(nation.id()));
            for (Province p : state.provincesOf(nation.id())) {
                System.out.println("        " + provinceLine(p, nation));
            }
        }
        System.out.println("    Neutrales:");
        for (Province p : state.provinces()) {
            if (!p.isWater() && p.isNeutral()) {
                System.out.println("        " + provinceLine(p, null));
            }
        }
    }

    private void printNation(Nation nation) {
        System.out.printf("    %s — oro: %.1f, AP: %.1f, tropas: %d, rey en: %s%n",
                nation.name(), nation.gold(), nation.actionPoints(),
                state.totalTroops(nation.id()),
                nation.kingProvinceId() == null ? "(muerto)" : nation.kingProvinceId());
        for (Province p : state.provincesOf(nation.id())) {
            System.out.println("        " + provinceLine(p, nation));
        }
    }

    private void printProvince(String id) {
        Province p = state.province(id);
        if (p.isWater()) {
            System.out.println("    " + p.name() + " [zona marítima] — costas: "
                    + String.join(", ", p.adjacent()));
            return;
        }
        Nation owner = p.ownerId() == null ? null : state.nation(p.ownerId());
        System.out.println("    " + provinceLine(p, owner));
        System.out.println("        dueño: " + (owner == null ? "neutral" : owner.name())
                + ", adyacentes: " + String.join(", ", p.adjacent()));
    }

    private String provinceLine(Province p, Nation owner) {
        String king = owner != null && p.id().equals(owner.kingProvinceId()) ? " ♔" : "";
        String fort = p.isFortified() ? " ⛨" : "";
        return String.format("%-18s (%s) pob: %,d  felicidad: %.0f%%  tropas: %d%s%s",
                p.name(), p.id(), p.population(), p.happiness(), p.troops(), king, fort);
    }
}
