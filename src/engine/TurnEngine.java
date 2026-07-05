package engine;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import model.DiplomaticState;
import model.GameState;
import model.Nation;
import model.Province;
import model.Rules;

/**
 * Motor de turnos WEGO: recoge las órdenes de todas las naciones durante la
 * fase de planificación ({@link #submit}) y las resuelve simultáneamente al
 * cerrar el turno ({@link #endTurn}).
 *
 * Los costes (AP, oro, población) se cobran al emitir la orden, como en el
 * juego original; los efectos se aplican en la resolución. Orden de fases:
 * fortificación → reclutamiento → movimientos y combates → eliminaciones →
 * victoria. Los movimientos se resuelven con los reyes primero y después por
 * orden de emisión ("quien ejecutó la acción primero" del juego original).
 */
public class TurnEngine {

    private final GameState state;
    private final Rules rules;

    private final List<Order.Fortify> pendingFortifies = new ArrayList<>();
    private final List<Order.Recruit> pendingRecruits = new ArrayList<>();
    private final List<Order.Move> pendingMoves = new ArrayList<>();

    /** Tropas ya comprometidas en movimientos salientes, por provincia de origen. */
    private final Map<String, Integer> committedTroops = new HashMap<>();
    /** Naciones cuyo rey ya está comprometido en un movimiento este turno. */
    private final Set<String> committedKings = new HashSet<>();
    /** Provincias con fortificación ya encargada este turno. */
    private final Set<String> pendingFortifiedProvinces = new HashSet<>();

    private boolean gameOver;

    public TurnEngine(GameState state) {
        this.state = state;
        this.rules = state.rules();
    }

    public GameState state() {
        return state;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    // -------------------------------------------------- fase de planificación

    /**
     * Valida una orden y cobra sus costes. Lanza {@link OrderException} con un
     * mensaje para el jugador si la orden no es válida.
     */
    public void submit(Order order) {
        if (gameOver) {
            throw new OrderException("La partida ha terminado");
        }
        Nation nation = livingNation(order.nationId());
        switch (order) {
            case Order.Move move -> submitMove(nation, move);
            case Order.Recruit recruit -> submitRecruit(nation, recruit);
            case Order.Fortify fortify -> submitFortify(nation, fortify);
            case Order.DeclareWar war -> submitDeclareWar(nation, war);
        }
    }

    private Nation livingNation(String nationId) {
        if (!state.hasNation(nationId)) {
            throw new OrderException("Nación desconocida: '" + nationId + "'");
        }
        Nation nation = state.nation(nationId);
        if (nation.isEliminated()) {
            throw new OrderException("La nación '" + nationId + "' está eliminada");
        }
        return nation;
    }

    private void submitMove(Nation nation, Order.Move move) {
        Province from = ownProvince(nation, move.from());
        if (!state.hasProvince(move.to())) {
            throw new OrderException("Provincia desconocida: '" + move.to() + "'");
        }
        Province to = state.province(move.to());
        if (to.id().equals(from.id())) {
            throw new OrderException("El origen y el destino son la misma provincia");
        }
        if (to.isWater()) {
            throw new OrderException("Las tropas no pueden detenerse en el mar; elige una provincia de tierra");
        }
        if (!isReachable(from, to)) {
            throw new OrderException("'" + to.id() + "' no es adyacente a '" + from.id()
                    + "' (ni por tierra ni cruzando un mar compartido)");
        }
        if (move.troops() < 1) {
            throw new OrderException("Hay que mover al menos 1 soldado");
        }
        int available = from.troops() - committedTroops.getOrDefault(from.id(), 0);
        if (move.troops() > available) {
            throw new OrderException("Solo quedan " + available + " soldados disponibles en '"
                    + from.id() + "' (el resto ya tiene órdenes)");
        }
        if (to.ownerId() != null && !to.ownerId().equals(nation.id())
                && state.relation(nation.id(), to.ownerId()) != DiplomaticState.GUERRA) {
            throw new OrderException("No estás en guerra con '" + to.ownerId()
                    + "'; declárale la guerra antes de atacar");
        }
        if (move.withKing()) {
            if (!from.id().equals(nation.kingProvinceId())) {
                throw new OrderException("El rey no está en '" + from.id() + "'");
            }
            if (committedKings.contains(nation.id())) {
                throw new OrderException("El rey ya tiene un movimiento ordenado este turno");
            }
        }
        chargeActionPoints(nation, rules.apCostMove, "mover un ejército");
        if (move.withKing()) {
            committedKings.add(nation.id());
        }
        committedTroops.merge(from.id(), move.troops(), Integer::sum);
        pendingMoves.add(move);
    }

    /** Adyacencia directa por tierra, o un único mar compartido entre origen y destino. */
    private boolean isReachable(Province from, Province to) {
        if (from.adjacent().contains(to.id())) {
            return true;
        }
        for (String adjId : from.adjacent()) {
            Province adj = state.province(adjId);
            if (adj.isWater() && adj.adjacent().contains(to.id())) {
                return true;
            }
        }
        return false;
    }

    private void submitRecruit(Nation nation, Order.Recruit recruit) {
        Province province = ownProvince(nation, recruit.provinceId());
        if (recruit.soldiers() < 1) {
            throw new OrderException("Hay que reclutar al menos 1 soldado");
        }
        double goldCost = recruit.soldiers() * rules.recruitGoldPerSoldier;
        if (nation.gold() < goldCost) {
            throw new OrderException(String.format(
                    "Reclutar %d soldados cuesta %.1f de oro y solo tienes %.1f",
                    recruit.soldiers(), goldCost, nation.gold()));
        }
        long populationCost = (long) Math.ceil(recruit.soldiers() * rules.recruitPopulationPerSoldier);
        if (province.population() < populationCost) {
            throw new OrderException("La población de '" + province.id()
                    + "' no alcanza para reclutar " + recruit.soldiers() + " soldados");
        }
        chargeActionPoints(nation, rules.apCostRecruit, "reclutar");
        nation.setGold(nation.gold() - goldCost);
        province.setPopulation(province.population() - populationCost);
        pendingRecruits.add(recruit);
    }

    private void submitFortify(Nation nation, Order.Fortify fortify) {
        Province province = ownProvince(nation, fortify.provinceId());
        if (province.isFortified() || pendingFortifiedProvinces.contains(province.id())) {
            throw new OrderException("'" + province.id() + "' ya está fortificada");
        }
        if (nation.gold() < rules.goldCostFortify) {
            throw new OrderException(String.format(
                    "Fortificar cuesta %.0f de oro y solo tienes %.1f", rules.goldCostFortify, nation.gold()));
        }
        chargeActionPoints(nation, rules.apCostFortify, "fortificar");
        nation.setGold(nation.gold() - rules.goldCostFortify);
        pendingFortifiedProvinces.add(province.id());
        pendingFortifies.add(fortify);
    }

    private void submitDeclareWar(Nation nation, Order.DeclareWar war) {
        if (!state.hasNation(war.targetNationId())) {
            throw new OrderException("Nación desconocida: '" + war.targetNationId() + "'");
        }
        if (war.targetNationId().equals(nation.id())) {
            throw new OrderException("No puedes declararte la guerra a ti mismo");
        }
        Nation target = state.nation(war.targetNationId());
        if (target.isEliminated()) {
            throw new OrderException("'" + target.id() + "' ya está eliminada");
        }
        DiplomaticState current = state.relation(nation.id(), target.id());
        if (current == DiplomaticState.GUERRA) {
            throw new OrderException("Ya estás en guerra con '" + target.id() + "'");
        }
        if (current == DiplomaticState.ALIANZA) {
            throw new OrderException("'" + target.id() + "' es tu aliado; rompe la alianza primero (fase M7)");
        }
        chargeActionPoints(nation, rules.apCostDeclareWar, "declarar la guerra");
        // La declaración surte efecto inmediato: se puede atacar en el mismo turno.
        state.setRelation(nation.id(), target.id(), DiplomaticState.GUERRA);
    }

    private Province ownProvince(Nation nation, String provinceId) {
        if (!state.hasProvince(provinceId)) {
            throw new OrderException("Provincia desconocida: '" + provinceId + "'");
        }
        Province province = state.province(provinceId);
        if (!nation.id().equals(province.ownerId())) {
            throw new OrderException("'" + provinceId + "' no pertenece a '" + nation.id() + "'");
        }
        return province;
    }

    private void chargeActionPoints(Nation nation, double cost, String action) {
        if (nation.actionPoints() < cost) {
            throw new OrderException(String.format(
                    "No quedan puntos de acción para %s (necesitas %.1f, tienes %.1f)",
                    action, cost, nation.actionPoints()));
        }
        nation.setActionPoints(nation.actionPoints() - cost);
    }

    // ---------------------------------------------------- fase de resolución

    /** Resuelve el turno completo y devuelve la crónica de lo ocurrido. */
    public TurnReport endTurn() {
        if (gameOver) {
            throw new IllegalStateException("La partida ha terminado");
        }
        TurnReport report = new TurnReport(state.turn());

        resolveFortifications(report);
        resolveRecruitments(report);
        resolveMoves(report);
        sweepEliminations(report);
        checkVictory(report);

        pendingFortifies.clear();
        pendingRecruits.clear();
        pendingMoves.clear();
        committedTroops.clear();
        committedKings.clear();
        pendingFortifiedProvinces.clear();

        if (!report.gameOver()) {
            state.advanceTurn();
            refreshActionPoints();
        } else {
            gameOver = true;
        }
        return report;
    }

    private void resolveFortifications(TurnReport report) {
        for (Order.Fortify order : pendingFortifies) {
            Province province = state.province(order.provinceId());
            if (order.nationId().equals(province.ownerId())) {
                province.setFortified(true);
                report.add(nameOf(order.nationId()) + " fortifica " + province.name());
            }
        }
    }

    private void resolveRecruitments(TurnReport report) {
        for (Order.Recruit order : pendingRecruits) {
            Province province = state.province(order.provinceId());
            if (order.nationId().equals(province.ownerId())) {
                province.setTroops(province.troops() + order.soldiers());
                report.add(nameOf(order.nationId()) + " recluta " + order.soldiers()
                        + " soldados en " + province.name());
            }
        }
    }

    private void resolveMoves(TurnReport report) {
        // Los reyes mueven primero (documentado del juego original); dentro de
        // cada grupo se conserva el orden de emisión (sort estable).
        List<Order.Move> moves = new ArrayList<>(pendingMoves);
        moves.sort(Comparator.comparing(m -> !m.withKing()));

        for (Order.Move move : moves) {
            Nation nation = state.nation(move.nationId());
            if (nation.isEliminated()) {
                continue; // las órdenes de una nación eliminada se anulan
            }
            Province from = state.province(move.from());
            if (!nation.id().equals(from.ownerId())) {
                report.add("Orden anulada: " + nameOf(nation.id()) + " ya no controla " + from.name());
                continue;
            }
            int troops = Math.min(move.troops(), from.troops());
            if (troops < 1) {
                continue; // las tropas cayeron defendiendo el origen
            }
            boolean kingGoes = move.withKing() && from.id().equals(nation.kingProvinceId());
            Province to = state.province(move.to());

            if (nation.id().equals(to.ownerId())) {
                from.setTroops(from.troops() - troops);
                to.setTroops(to.troops() + troops);
                if (kingGoes) {
                    nation.setKingProvinceId(to.id());
                }
                report.add(nameOf(nation.id()) + " mueve " + troops + " soldados de "
                        + from.name() + " a " + to.name());
                continue;
            }

            // El destino pudo cambiar de dueño este turno: revalidar la guerra.
            if (to.ownerId() != null
                    && state.relation(nation.id(), to.ownerId()) != DiplomaticState.GUERRA) {
                report.add("Avance detenido: " + nameOf(nation.id()) + " no está en guerra con "
                        + nameOf(to.ownerId()) + " (dueño actual de " + to.name() + ")");
                continue;
            }

            from.setTroops(from.troops() - troops);
            resolveBattle(nation, kingGoes, troops, to, report);
        }
    }

    private void resolveBattle(Nation attacker, boolean kingGoes, int troops,
                               Province target, TurnReport report) {
        Nation defender = target.ownerId() == null ? null : state.nation(target.ownerId());
        boolean defenderKingPresent = defender != null
                && target.id().equals(defender.kingProvinceId());

        double attackBonus = kingGoes ? rules.kingCombatBonus : 0;
        double defenseBonus = (target.isFortified() ? rules.fortDefenseBonus : 0)
                + (defenderKingPresent ? rules.kingCombatBonus : 0);

        CombatResolver.Outcome outcome = CombatResolver.resolve(
                troops, attackBonus, target.troops(), defenseBonus, rules.combatAttrition);

        String defenderName = defender == null ? "neutrales" : nameOf(defender.id());
        if (outcome.attackerWon()) {
            target.setOwnerId(attacker.id());
            target.setTroops(outcome.survivors());
            target.setFortified(false); // la fortificación cae con la conquista
            if (kingGoes) {
                attacker.setKingProvinceId(target.id());
            }
            report.add(nameOf(attacker.id()) + " conquista " + target.name() + " ("
                    + troops + " atacantes vs " + defenderName + "; sobreviven "
                    + outcome.survivors() + ")");
            if (defenderKingPresent) {
                killKing(defender, report);
            } else if (defender != null && state.provincesOf(defender.id()).isEmpty()) {
                eliminate(defender, report);
            }
        } else {
            target.setTroops(outcome.survivors());
            report.add(defenderName + " defiende " + target.name() + " frente a "
                    + nameOf(attacker.id()) + " (" + troops + " atacantes aniquilados; quedan "
                    + outcome.survivors() + " defensores)");
            if (kingGoes) {
                killKing(attacker, report);
            }
        }
    }

    /**
     * Muerte del rey: la nación pierde {@code kingDeathTerritoryLoss} de su
     * territorio (las provincias liberadas se vuelven neutrales y conservan su
     * guarnición). Conserva sus provincias con más tropas. Con pérdida total,
     * o sin provincias restantes, la nación queda eliminada.
     */
    private void killKing(Nation nation, TurnReport report) {
        nation.setKingProvinceId(null);
        report.add("¡El rey de " + nameOf(nation.id()) + " ha muerto!");

        List<Province> owned = state.provincesOf(nation.id());
        int keep = rules.kingDeathTerritoryLoss >= 1.0 ? 0
                : Math.max(1, (int) Math.floor(owned.size() * (1 - rules.kingDeathTerritoryLoss)));
        if (owned.size() <= keep) {
            return;
        }
        owned.sort(Comparator.comparingInt(Province::troops).reversed());
        for (Province province : owned.subList(keep, owned.size())) {
            province.setOwnerId(null); // la provincia deserta; su guarnición se vuelve neutral
        }
        report.add(nameOf(nation.id()) + " pierde " + (owned.size() - keep)
                + " provincias por la muerte de su rey");
        if (keep == 0) {
            eliminate(nation, report);
        }
    }

    private void eliminate(Nation nation, TurnReport report) {
        nation.setEliminated(true);
        nation.setKingProvinceId(null);
        report.add("*** " + nameOf(nation.id()) + " ha sido eliminada ***");
    }

    private void sweepEliminations(TurnReport report) {
        for (Nation nation : state.livingNations()) {
            if (state.provincesOf(nation.id()).isEmpty()) {
                eliminate(nation, report);
            }
        }
    }

    private void checkVictory(TurnReport report) {
        List<Nation> living = state.livingNations();
        if (living.size() == 1) {
            report.setWinnerId(living.get(0).id());
            report.add("### " + nameOf(living.get(0).id()) + " domina el mapa: ¡victoria! ###");
            return;
        }
        if (rules.maxTurns > 0 && state.turn() >= rules.maxTurns) {
            Nation winner = living.stream()
                    .max(Comparator
                            .comparingInt((Nation n) -> state.provincesOf(n.id()).size())
                            .thenComparingInt(n -> state.totalTroops(n.id())))
                    .orElseThrow();
            report.setWinnerId(winner.id());
            report.add("### Límite de " + rules.maxTurns + " turnos alcanzado: gana "
                    + nameOf(winner.id()) + " por clasificación ###");
        }
    }

    private void refreshActionPoints() {
        for (Nation nation : state.livingNations()) {
            nation.setActionPoints(rules.actionPointsFor(state.provincesOf(nation.id()).size()));
        }
    }

    private String nameOf(String nationId) {
        return state.nation(nationId).name();
    }
}
