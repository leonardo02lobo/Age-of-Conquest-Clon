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

    private final List<Order.Decree> pendingDecrees = new ArrayList<>();
    private final List<Order.Fortify> pendingFortifies = new ArrayList<>();
    private final List<Order.Recruit> pendingRecruits = new ArrayList<>();
    private final List<Order.Move> pendingMoves = new ArrayList<>();
    private final List<Order.Pillage> pendingPillages = new ArrayList<>();

    /** Tropas ya comprometidas en movimientos salientes, por provincia de origen. */
    private final Map<String, Integer> committedTroops = new HashMap<>();
    /** Naciones cuyo rey ya está comprometido en un movimiento este turno. */
    private final Set<String> committedKings = new HashSet<>();
    /** Provincias con fortificación ya encargada este turno. */
    private final Set<String> pendingFortifiedProvinces = new HashSet<>();
    /** Provincias con saqueo ya encargado este turno. */
    private final Set<String> pendingPillagedProvinces = new HashSet<>();

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
            case Order.Pillage pillage -> submitPillage(nation, pillage);
            case Order.Decree decree -> submitDecree(nation, decree);
            case Order.SetTaxRate tax -> submitSetTaxRate(nation, tax);
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
        for (Province candidate : state.reachableFrom(from.id())) {
            if (candidate.id().equals(to.id())) {
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

    private void submitPillage(Nation nation, Order.Pillage pillage) {
        Province province = ownProvince(nation, pillage.provinceId());
        if (province.population() < 1) {
            throw new OrderException("'" + province.id() + "' no tiene población que saquear");
        }
        if (pendingPillagedProvinces.contains(province.id())) {
            throw new OrderException("'" + province.id() + "' ya será saqueada este turno");
        }
        chargeActionPoints(nation, rules.apCostPillage, "saquear");
        pendingPillagedProvinces.add(province.id());
        pendingPillages.add(pillage);
    }

    private void submitDecree(Nation nation, Order.Decree decree) {
        Province province = ownProvince(nation, decree.provinceId());
        if (decree.type() == Order.DecreeType.FESTIVAL && province.population() < 1) {
            throw new OrderException("'" + province.id() + "' no tiene población para un festival");
        }
        double apCost = switch (decree.type()) {
            case REPARTIR -> rules.apCostDecreeShare;
            case FIESTA -> rules.apCostDecreeParty;
            case FESTIVAL -> rules.apCostFestival;
        };
        double goldCost = switch (decree.type()) {
            case REPARTIR -> rules.goldCostDecreeShare;
            case FIESTA -> rules.goldCostDecreeParty;
            case FESTIVAL -> rules.goldCostFestival;
        };
        if (nation.gold() < goldCost) {
            throw new OrderException(String.format(
                    "El decreto cuesta %.0f de oro y solo tienes %.1f", goldCost, nation.gold()));
        }
        chargeActionPoints(nation, apCost, "emitir un decreto");
        nation.setGold(nation.gold() - goldCost);
        pendingDecrees.add(decree);
    }

    private void submitSetTaxRate(Nation nation, Order.SetTaxRate tax) {
        if (!rules.isTaxSeason(state.turn())) {
            int next = state.turn() + (rules.taxSeasonInterval - (state.turn() - 1) % rules.taxSeasonInterval);
            throw new OrderException("La tasa solo puede cambiarse en temporada fiscal (próxima: turno " + next + ")");
        }
        if (!rules.isAllowedTaxRate(tax.rate())) {
            StringBuilder allowed = new StringBuilder();
            for (int rate : rules.allowedTaxRates) {
                allowed.append(allowed.isEmpty() ? "" : "/").append(rate);
            }
            throw new OrderException("Tasa inválida: " + tax.rate() + " (permitidas: " + allowed + ")");
        }
        // Efecto inmediato: la recaudación de este mismo turno usa la nueva tasa.
        nation.setTaxRate(tax.rate());
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

        resolveDecrees(report);
        resolveFortifications(report);
        resolveRecruitments(report);
        resolveMoves(report);
        resolvePillages(report);
        resolveEconomy(report);
        resolveRevolts(report);
        sweepEliminations(report);
        checkVictory(report);

        pendingDecrees.clear();
        pendingFortifies.clear();
        pendingRecruits.clear();
        pendingMoves.clear();
        pendingPillages.clear();
        committedTroops.clear();
        committedKings.clear();
        pendingFortifiedProvinces.clear();
        pendingPillagedProvinces.clear();

        if (!report.gameOver()) {
            state.advanceTurn();
            refreshActionPoints();
        } else {
            gameOver = true;
        }
        return report;
    }

    private void resolveDecrees(TurnReport report) {
        for (Order.Decree order : pendingDecrees) {
            Province province = state.province(order.provinceId());
            if (!order.nationId().equals(province.ownerId())) {
                continue;
            }
            switch (order.type()) {
                case REPARTIR -> {
                    province.setHappiness(province.happiness() + rules.decreeShareHappiness);
                    report.add(nameOf(order.nationId()) + " reparte dinero en " + province.name()
                            + " (felicidad: " + Math.round(province.happiness()) + "%)");
                }
                case FIESTA -> {
                    province.setHappiness(province.happiness() + rules.decreePartyHappiness);
                    report.add(nameOf(order.nationId()) + " celebra una fiesta en " + province.name()
                            + " (felicidad: " + Math.round(province.happiness()) + "%)");
                }
                case FESTIVAL -> {
                    long boosted = Math.min(rules.maxPopulation,
                            Math.round(province.population() * (1 + rules.festivalPopulationBoost)));
                    province.setPopulation(boosted);
                    report.add(nameOf(order.nationId()) + " celebra un festival de fertilidad en "
                            + province.name() + String.format(" (población: %,d)", boosted));
                }
            }
        }
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

    private void resolvePillages(TurnReport report) {
        for (Order.Pillage order : pendingPillages) {
            Province province = state.province(order.provinceId());
            if (!order.nationId().equals(province.ownerId())) {
                continue; // la provincia cayó en combate antes del saqueo
            }
            Nation nation = state.nation(order.nationId());
            long destroyed = Math.round(province.population() * rules.pillagePopulationLoss);
            double loot = destroyed * rules.pillageGoldPerInhabitant;
            province.setPopulation(province.population() - destroyed);
            province.setHappiness(province.happiness() - rules.pillageHappinessLoss);
            nation.setGold(nation.gold() + loot);
            report.add(String.format("%s saquea %s: +%.1f de oro (%,d habitantes menos, felicidad: %d%%)",
                    nameOf(nation.id()), province.name(), loot, destroyed,
                    Math.round(province.happiness())));
        }
    }

    /**
     * Fase económica de fin de turno, por nación viva: recaudación (solo las
     * provincias con felicidad suficiente pagan), mantenimiento militar y
     * administración; después crecimiento poblacional y evolución de la
     * felicidad de cada provincia.
     */
    private void resolveEconomy(TurnReport report) {
        for (Nation nation : state.livingNations()) {
            List<Province> owned = state.provincesOf(nation.id());

            double income = 0;
            for (Province province : owned) {
                if (province.happiness() >= rules.happinessRevoltThreshold) {
                    income += rules.maxTaxGoldPerProvince
                            * ((double) province.population() / rules.maxPopulation)
                            * (nation.taxRate() / 100.0);
                }
            }
            double upkeep = Math.ceil((double) state.totalTroops(nation.id()) / rules.troopsPerUpkeepGold)
                    + rules.adminGoldPerProvince * owned.size();
            nation.setGold(nation.gold() + income - upkeep);
            report.add(String.format("Hacienda de %s: +%.1f impuestos, −%.1f mantenimiento (oro: %.1f)",
                    nation.name(), income, upkeep, nation.gold()));

            boolean atWar = isAtWar(nation);
            double happinessDelta = rules.happinessBaseRecovery
                    + (100 - nation.taxRate()) * rules.taxHappinessPerPoint
                    - (atWar ? rules.warUnhappiness : 0);
            for (Province province : owned) {
                province.setPopulation(Math.min(rules.maxPopulation,
                        Math.round(province.population() * (1 + rules.populationGrowth))));
                province.setHappiness(province.happiness() + happinessDelta);
            }
        }
    }

    private boolean isAtWar(Nation nation) {
        for (Nation other : state.livingNations()) {
            if (!other.id().equals(nation.id())
                    && nation.relation(other.id()) == DiplomaticState.GUERRA) {
                return true;
            }
        }
        return false;
    }

    /**
     * Fase estocástica (Monte Carlo): toda provincia con felicidad bajo el
     * umbral puede rebelarse con probabilidad
     * {@code min(máx, k·(umbral − felicidad)²)}, reducida si hay guarnición.
     * La provincia rebelde se vuelve neutral con una milicia proporcional a su
     * población. El sorteo usa el generador sembrado de la partida.
     */
    private void resolveRevolts(TurnReport report) {
        for (Nation nation : state.livingNations()) {
            for (Province province : state.provincesOf(nation.id())) {
                double discontent = rules.happinessRevoltThreshold - province.happiness();
                if (discontent <= 0) {
                    continue;
                }
                double chance = Math.min(rules.revoltMaxChance,
                        rules.revoltRiskK * discontent * discontent);
                if (province.troops() >= 1) {
                    chance *= rules.revoltGarrisonSuppression;
                }
                if (state.random().nextDouble() >= chance) {
                    continue;
                }
                int rebels = (int) Math.max(1, Math.round(province.population() * rules.rebelsPerPopulation));
                province.setOwnerId(null);
                province.setTroops(rebels);
                province.setFortified(false);
                province.setHappiness(rules.revoltHappinessAfter);
                report.add("¡Revuelta en " + province.name() + "! La provincia se independiza de "
                        + nation.name() + " con " + rebels + " milicianos");
                if (province.id().equals(nation.kingProvinceId())) {
                    relocateKing(nation, report);
                }
            }
        }
    }

    /** El rey huye de una revuelta a la provincia propia con más tropas (si existe). */
    private void relocateKing(Nation nation, TurnReport report) {
        List<Province> owned = state.provincesOf(nation.id());
        if (owned.isEmpty()) {
            nation.setKingProvinceId(null); // sin refugio: la nación caerá en el barrido
            return;
        }
        owned.sort(Comparator.comparingInt(Province::troops).reversed());
        nation.setKingProvinceId(owned.get(0).id());
        report.add("El rey de " + nation.name() + " huye a " + owned.get(0).name());
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
