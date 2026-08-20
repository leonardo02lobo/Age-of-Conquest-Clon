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
import model.LCG;
import model.Nation;
import model.Province;
import model.Rules;
import model.TerrainType;

/**
 * Motor de turnos WEGO: recoge las órdenes de todas las naciones durante la
 * fase de planificación ({@link #submit}) y las resuelve simultáneamente al
 * cerrar el turno ({@link #endTurn}).
 *
 * Implementa las ecuaciones del modelo formal:
 *   - E1 (Inicio de Turno): ec. 3.1–3.5 (economía), ec. 5.2 (insolvencia)
 *   - E2 (Planificación): ec. 3.11 (reclutamiento), ec. 3.5 (gasto discrecional)
 *   - E4 (Combate): ec. 3.14–3.21 (resolución estocástica con terreno)
 *   - E7/E8 (Diplomacia): ec. 3.30–3.32 (coalición, alianzas)
 *   - E9 (Fin de Turno): ec. 3.6–3.7 (descontento), ec. 3.10 (población),
 *                         victoria por cuota Θ_V
 *
 * Limitación declarada: el subsistema de moral (ec. 3.27–3.29) NO está
 * implementado. Este motor no tiene la entidad Ejército con identidad propia
 * que μ_a requiere: las tropas son un escalar por provincia. El combate usa
 * por tanto μ_a ≡ 1.0, y los parámetros μ_min, λ_d, ρ_μ y γ_μ de Rules quedan
 * reservados. La implementación completa de la moral está en el simulador
 * operacional del Parcial III (`sim/militar.py`).
 */
public class TurnEngine {

    private final GameState state;
    private final Rules rules;
    private final LCG lcg;

    private final List<Order.Decree> pendingDecrees = new ArrayList<>();
    private final List<Order.Fortify> pendingFortifies = new ArrayList<>();
    private final List<Order.Recruit> pendingRecruits = new ArrayList<>();
    private final List<Order.Move> pendingMoves = new ArrayList<>();
    private final List<Order.Pillage> pendingPillages = new ArrayList<>();

    private final Map<String, Integer> committedTroops = new HashMap<>();
    private final Set<String> committedKings = new HashSet<>();
    private final Set<String> pendingFortifiedProvinces = new HashSet<>();
    private final Set<String> pendingPillagedProvinces = new HashSet<>();

    /** Daño de guerra acumulado por provincia en el turno (β_p(t), ec. 3.10). */
    private final Map<String, Double> warDamage = new HashMap<>();

    private boolean gameOver;

    public TurnEngine(GameState state) {
        this.state = state;
        this.rules = state.rules();
        this.lcg = new LCG(rules.randomSeed);
    }

    public GameState state() { return state; }
    public boolean isGameOver() { return gameOver; }

    // -------------------------------------------------- fase de planificación

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
            throw new OrderException("Las tropas no pueden detenerse en el mar");
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
                    + from.id() + "'");
        }
        // Legal(i, q) — ec. 3.7: solo puede entrar en territorio propio, neutral, en guerra, o aliado (tránsito)
        if (to.ownerId() != null && !to.ownerId().equals(nation.id())
                && state.relation(nation.id(), to.ownerId()) != DiplomaticState.GUERRA
                && state.relation(nation.id(), to.ownerId()) != DiplomaticState.ALIANZA) {
            throw new OrderException("No puedes entrar en '" + to.id() + "': no estás en guerra con "
                    + to.ownerId());
        }
        if (move.withKing()) {
            if (!from.id().equals(nation.kingProvinceId())) {
                throw new OrderException("El rey no está en '" + from.id() + "'");
            }
            if (committedKings.contains(nation.id())) {
                throw new OrderException("El rey ya tiene un movimiento ordenado este turno");
            }
        }
        if (move.withKing()) {
            committedKings.add(nation.id());
        }
        committedTroops.merge(from.id(), move.troops(), Integer::sum);
        pendingMoves.add(move);
    }

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
        double goldCost = recruit.soldiers() * rules.cU;
        if (nation.gold() < goldCost) {
            throw new OrderException(String.format(
                    "Reclutar %d soldados cuesta %.1f de oro y solo tienes %.1f",
                    recruit.soldiers(), goldCost, nation.gold()));
        }
        long populationCost = (long) Math.ceil(recruit.soldiers() * rules.rho);
        if (province.population() < populationCost) {
            throw new OrderException("La población de '" + province.id()
                    + "' no alcanza para reclutar " + recruit.soldiers() + " soldados");
        }
        nation.setGold(nation.gold() - goldCost);
        province.setPopulation(province.population() - populationCost);
        pendingRecruits.add(recruit);
    }

    private void submitFortify(Nation nation, Order.Fortify fortify) {
        Province province = ownProvince(nation, fortify.provinceId());
        if (province.fortification() >= rules.phiMax) {
            throw new OrderException("'" + province.id() + "' ya tiene fortificación máxima");
        }
        if (pendingFortifiedProvinces.contains(province.id())) {
            throw new OrderException("'" + province.id() + "' ya será fortificada este turno");
        }
        if (nation.gold() < rules.cPhi) {
            throw new OrderException(String.format(
                    "Fortificar cuesta %.0f de oro y solo tienes %.1f", rules.cPhi, nation.gold()));
        }
        nation.setGold(nation.gold() - rules.cPhi);
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
            throw new OrderException("'" + target.id() + "' es tu aliado; rompe la alianza primero");
        }
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
        pendingPillagedProvinces.add(province.id());
        pendingPillages.add(pillage);
    }

    private void submitDecree(Nation nation, Order.Decree decree) {
        Province province = ownProvince(nation, decree.provinceId());
        pendingDecrees.add(decree);
    }

    private void submitSetTaxRate(Nation nation, Order.SetTaxRate tax) {
        if (tax.rate() < 0 || tax.rate() > rules.thetaMax) {
            throw new OrderException("Tasa inválida: " + tax.rate() + " (máximo " + rules.thetaMax + "%)");
        }
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

    // ---------------------------------------------------- fase de resolución

    public TurnReport endTurn() {
        if (gameOver) {
            throw new IllegalStateException("La partida ha terminado");
        }
        TurnReport report = new TurnReport(state.turn());

        // E7/E8: Diplomacia automática (coalición anti-líder, alianzas) — ANTES de planificación
        resolveAutoDiplomacy(report);

        // E2: Planificación IA (si hay naciones IA)
        resolveAIPlanning(report);

        resolveFortifications(report);
        resolveRecruitments(report);
        resolveMoves(report);
        resolvePillages(report);

        // E1: Economía — ec. 3.1–3.5
        resolveEconomy(report);

        // E9: Fin de turno — ec. 3.6–3.7 (descontento), ec. 3.10 (población)
        resolveEndOfTurn(report);

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
        warDamage.clear();

        if (!report.gameOver()) {
            state.advanceTurn();
        } else {
            gameOver = true;
        }
        return report;
    }

    // --------------------------------------- E7/E8: Diplomacia automática

    /**
     * Implementa E7 (declaración de guerra automática) y E8 (alianzas)
     * según ec. 3.30–3.32.
     */
    private void resolveAutoDiplomacy(TurnReport report) {
        List<Nation> living = state.livingNations();
        if (living.size() < 2) return;

        // Encontrar líder (más provincias)
        Nation leader = null;
        int maxProvinces = 0;
        for (Nation n : living) {
            int provs = state.provincesOf(n.id()).size();
            if (provs > maxProvinces) {
                maxProvinces = provs;
                leader = n;
            }
        }
        if (leader == null) return;

        // q_ℓ sobre provincias de tierra: las marítimas no son conquistables y
        // diluirían la cuota, impidiendo que se alcance nunca θ_am.
        int landCount = state.landProvinceCount();
        double qL = landCount == 0 ? 0.0 : (double) maxProvinces / landCount;

        // E7: coalición anti-líder (B3) — ec. 3.30
        if (qL >= rules.thetaAm) {
            for (Nation n : living) {
                if (n.id().equals(leader.id())) continue;
                if (state.relation(n.id(), leader.id()) == DiplomaticState.NEUTRAL) {
                    state.setRelation(n.id(), leader.id(), DiplomaticState.GUERRA);
                    report.add("COALICIÓN: " + n.name() + " declara guerra a " + leader.name()
                            + " (controla " + Math.round(qL * 100) + "% del mapa)");
                }
            }
        }

        // E7: agresión oportunista — ec. 3.30
        for (Nation n : living) {
            boolean alreadyAtWar = false;
            for (Nation other : living) {
                if (!other.id().equals(n.id())
                        && state.relation(n.id(), other.id()) == DiplomaticState.GUERRA) {
                    alreadyAtWar = true;
                    break;
                }
            }
            if (alreadyAtWar) continue;

            for (Nation target : living) {
                if (target.id().equals(n.id())) continue;
                if (state.relation(n.id(), target.id()) != DiplomaticState.NEUTRAL) continue;
                if (!areAdjacent(n, target)) continue;

                double myTroops = state.totalTroops(n.id());
                double theirTroops = Math.max(1, state.totalTroops(target.id()));
                // γ_σ según estrategia de la nación
                double gammaSigma = rules.gammaSigmaForStrategy(getStrategy(n));
                if (myTroops / theirTroops >= gammaSigma) {
                    state.setRelation(n.id(), target.id(), DiplomaticState.GUERRA);
                    report.add("AGRESIÓN: " + n.name() + " declara guerra a " + target.name());
                    break; // solo una guerra nueva por turno
                }
            }
        }

        // E8: alianzas — ec. 3.31
        for (int i = 0; i < living.size(); i++) {
            for (int j = i + 1; j < living.size(); j++) {
                Nation a = living.get(i);
                Nation b = living.get(j);
                if (state.relation(a.id(), b.id()) != DiplomaticState.NEUTRAL) continue;
                if (state.relation(a.id(), leader.id()) == DiplomaticState.GUERRA
                        && state.relation(b.id(), leader.id()) == DiplomaticState.GUERRA
                        && !a.id().equals(leader.id()) && !b.id().equals(leader.id())
                        && qL >= rules.thetaAm) {
                    state.setRelation(a.id(), b.id(), DiplomaticState.ALIANZA);
                    report.add("ALIANZA: " + a.name() + " y " + b.name()
                            + " se alían contra " + leader.name());
                }
            }
        }

        // E8: ruptura de alianza con histéresis — ec. 3.32
        for (Nation a : living) {
            for (Nation b : living) {
                if (a.id().compareTo(b.id()) >= 0) continue;
                if (state.relation(a.id(), b.id()) == DiplomaticState.ALIANZA
                        && qL < rules.thetaAm - rules.sigmaH) {
                    state.setRelation(a.id(), b.id(), DiplomaticState.NEUTRAL);
                    report.add("RUPTURA: la alianza entre " + a.name() + " y " + b.name()
                            + " se disuelve");
                }
            }
        }
    }

    private boolean areAdjacent(Nation a, Nation b) {
        for (Province pa : state.provincesOf(a.id())) {
            for (Province adj : state.reachableFrom(pa.id())) {
                if (b.id().equals(adj.ownerId())) return true;
            }
        }
        return false;
    }

    private Rules.Strategy getStrategy(Nation n) {
        // Determinar estrategia basándose en la tasa impositiva
        int tax = n.taxRate();
        if (tax >= 125) return Rules.Strategy.AGRESIVA;
        if (tax <= 50) return Rules.Strategy.ECONOMICA;
        if (tax == 100) return Rules.Strategy.DEFENSIVA;
        return Rules.Strategy.EQUILIBRADA;
    }

    // --------------------------------------- IA planning (stub, se llama desde fuera)

    private void resolveAIPlanning(TurnReport report) {
        // La IA se planifica desde fuera (GreedyAgent) antes de endTurn
    }

    // --------------------------------------- Fortificaciones

    private void resolveFortifications(TurnReport report) {
        for (Order.Fortify order : pendingFortifies) {
            Province province = state.province(order.provinceId());
            if (order.nationId().equals(province.ownerId())) {
                province.setFortification(province.fortification() + 1);
                report.add(state.nation(order.nationId()).name() + " fortifica "
                        + province.name() + " (nivel " + province.fortification() + ")");
            }
        }
    }

    // --------------------------------------- Reclutamiento

    private void resolveRecruitments(TurnReport report) {
        for (Order.Recruit order : pendingRecruits) {
            Province province = state.province(order.provinceId());
            if (order.nationId().equals(province.ownerId())) {
                province.setTroops(province.troops() + order.soldiers());
                report.add(state.nation(order.nationId()).name() + " recluta "
                        + order.soldiers() + " soldados en " + province.name());
            }
        }
    }

    // --------------------------------------- Movimientos y combate

    private void resolveMoves(TurnReport report) {
        List<Order.Move> moves = new ArrayList<>(pendingMoves);
        moves.sort(Comparator.comparing(m -> !m.withKing()));

        for (Order.Move move : moves) {
            Nation nation = state.nation(move.nationId());
            if (nation.isEliminated()) continue;

            Province from = state.province(move.from());
            if (!nation.id().equals(from.ownerId())) {
                report.add("Orden anulada: " + nation.name() + " ya no controla " + from.name());
                continue;
            }
            int troops = Math.min(move.troops(), from.troops());
            if (troops < 1) continue;

            boolean kingGoes = move.withKing() && from.id().equals(nation.kingProvinceId());
            Province to = state.province(move.to());

            // Revalidar Legal() — tabla §4.3.3
            if (to.ownerId() != null && !nation.id().equals(to.ownerId())
                    && state.relation(nation.id(), to.ownerId()) != DiplomaticState.GUERRA
                    && state.relation(nation.id(), to.ownerId()) != DiplomaticState.ALIANZA) {
                report.add("Avance detenido: " + nation.name() + " no está en guerra con "
                        + (to.ownerId() == null ? "neutrales" : state.nation(to.ownerId()).name())
                        + " (dueño actual de " + to.name() + ")");
                continue;
            }

            from.setTroops(from.troops() - troops);

            if (nation.id().equals(to.ownerId()) || to.isNeutral()) {
                // Movimiento propio o conquista de neutral
                to.setTroops(to.troops() + troops);
                if (kingGoes) {
                    nation.setKingProvinceId(to.id());
                }
                if (to.isNeutral()) {
                    to.setOwnerId(nation.id());
                    report.add(nation.name() + " ocupa " + to.name() + " (neutral)");
                } else {
                    report.add(nation.name() + " mueve " + troops + " de "
                            + from.name() + " a " + to.name());
                }
            } else {
                // Combate — ec. 3.14–3.21
                String originalOwner = to.ownerId();
                int defendingTroops = to.troops();
                resolveBattle(nation, kingGoes, troops, defendingTroops, originalOwner, to, report);
            }
        }
    }

    private void resolveBattle(Nation attacker, boolean kingGoes, int troops,
                               int defendingTroops, String originalOwner,
                               Province target, TurnReport report) {
        Nation defender = originalOwner == null ? null :
                (state.hasNation(originalOwner) ? state.nation(originalOwner) : null);

        // Moral del atacante: μ = 1.0 base (simplificación inicial)
        double attackerMorale = 1.0;

        // Fortificación: φ_p del target
        int fortLevel = target.fortification();

        // Fuerza defensiva: guarnición + ejércitos estacionados
        int defenders = defendingTroops;

        // Descontento de la provincia
        double discontent = target.discontent();

        // Ambas potencias se evalúan sobre el terreno de la provincia disputada T_p:
        // (3.14) usa T(T_p, ATQ) y (3.15) usa T(T_p, DEF). Pasar aquí un terreno
        // fijo anularía la columna de ataque de la matriz de terreno §2.4.6.
        CombatResolver.Outcome outcome = CombatResolver.resolve(
                troops, attackerMorale, target.terrain(),
                defenders, fortLevel, target.terrain(),
                discontent, rules, lcg);

        String defenderName = defender == null ? "neutrales" : defender.name();
        if (outcome.attackerWon()) {
            target.setOwnerId(attacker.id());
            target.setTroops(outcome.survivors());
            // ec. 5.6: degradación de fortificación por asedio
            target.setFortification(Math.max(0, target.fortification() - 1));
            // ec. (3.10): acumular daño de guerra
            warDamage.merge(target.id(), outcome.casualtiesDefender(), Double::sum);

            if (kingGoes) {
                attacker.setKingProvinceId(target.id());
            }
            report.add(attacker.name() + " conquista " + target.name() + " ("
                    + troops + " vs " + defenderName + "; sobreviven "
                    + outcome.survivors() + ")");

            if (defenderKingPresent(defender, target)) {
                killKing(defender, report);
            } else if (defender != null && state.provincesOf(defender.id()).isEmpty()) {
                eliminate(defender, report);
            }
        } else {
            target.setOwnerId(originalOwner);
            target.setTroops(outcome.survivors());
            // ec. (3.10): acumular daño de guerra
            warDamage.merge(target.id(), outcome.casualtiesAttacker(), Double::sum);

            report.add(defenderName + " defiende " + target.name()
                    + " (" + troops + " atacantes aniquilados; quedan "
                    + outcome.survivors() + " defensores)");
            if (kingGoes) {
                killKing(attacker, report);
            }
        }
    }

    private boolean defenderKingPresent(Nation defender, Province target) {
        return defender != null && target.id().equals(defender.kingProvinceId());
    }

    // --------------------------------------- Saqueo

    private void resolvePillages(TurnReport report) {
        for (Order.Pillage order : pendingPillages) {
            Province province = state.province(order.provinceId());
            if (!order.nationId().equals(province.ownerId())) continue;
            Nation nation = state.nation(order.nationId());
            long destroyed = Math.round(province.population() * 0.20);
            double loot = destroyed * 0.001;
            province.setPopulation(province.population() - destroyed);
            nation.setGold(nation.gold() + loot);
            report.add(String.format("%s saquea %s: +%.1f oro (−%,d hab.)",
                    nation.name(), province.name(), loot, destroyed));
        }
    }

    // --------------------------------------- E1: Economía (ec. 3.1–3.5)

    /**
     * E1 — Fase económica de inicio de turno:
     *   I_p(t) = ι · L_p · (θ_i/100) · (1 + β_φ · φ_p) si D_p < D* (ec. 3.1)
     *   I_p(t) = 0                                         si D_p ≥ D*
     *   R_i = Σ_p I_p                                      (ec. 3.2)
     *   C_i = c_adm · n_i + c_up · M_i                     (ec. 3.3)
     *   G_i = max(0, G_i + R_i − C_i − X_i)               (ec. 3.4)
     *
     * Si el resultado es negativo → insolvencia (ec. 5.2)
     */
    private void resolveEconomy(TurnReport report) {
        for (Nation nation : state.livingNations()) {
            List<Province> owned = state.provincesOf(nation.id());
            int nI = owned.size();

            // R_i(t) = Σ_p I_p(t) — ec. 3.2
            double income = 0;
            for (Province p : owned) {
                if (p.discontent() < rules.dStar) {
                    // I_p = ι · L_p · (θ/100) · (1 + β_φ · φ_p) — ec. 3.1
                    double bono = 1.0 + rules.betaPhi * p.fortification();
                    double ip = rules.iota * p.population() * (nation.taxRate() / 100.0) * bono;
                    income += ip;
                }
            }

            // M_i = Σ tropas + Σ guarniciones — ec. 3.12
            double mI = state.totalTroops(nation.id());

            // C_i = c_adm · n_i + c_up · M_i — ec. 3.3
            double cost = rules.cAdm * nI + rules.cUp * mI;

            // G_i = max(0, G_i + R_i − C_i) — ec. 3.4
            double neto = nation.gold() + income - cost;
            if (neto < 0) {
                // Insolvencia — ec. 5.2: deserción forzosa
                double deficit = Math.abs(neto);
                int deserters = (int) Math.ceil(deficit / rules.cUp);
                nation.setGold(0);
                // Reducir tropas proporcionalmente
                for (Province p : owned) {
                    if (p.troops() > 0 && deserters > 0) {
                        int reduce = Math.min(p.troops(), deserters);
                        p.setTroops(p.troops() - reduce);
                        deserters -= reduce;
                    }
                }
                report.add("INSOLVENCIA de " + nation.name()
                        + ": " + (int) Math.ceil(deficit) + " soldados desertan por deudas");
            } else {
                nation.setGold(neto);
            }

            report.add(String.format("Hacienda de %s: +%.1f impuestos, −%.1f mantenimiento (oro: %.1f)",
                    nation.name(), income, cost, nation.gold()));
        }
    }

    // --------------------------------------- E9: Fin de turno (ec. 3.6–3.7, 3.10)

    /**
     * E9 — Fin de turno:
     *   ΔD_p = η_θ(θ−θ_0) + η_w·1[guerra] + η_n·max(0, n_i−n*) − η_r  (ec. 3.6)
     *   D_p = clamp(D_p + ΔD, 0, 100)                                     (ec. 3.7)
     *   L_p = min(L_max, L_p·(1+g_L)) − ϱ·β_p                             (ec. 3.10)
     *
     * La regeneración de moral (ec. 3.28) no se aplica: ver la limitación
     * declarada en la cabecera de la clase.
     */
    private void resolveEndOfTurn(TurnReport report) {
        for (Nation nation : state.livingNations()) {
            List<Province> owned = state.provincesOf(nation.id());

            // ¿Está en guerra?
            boolean enGuerra = false;
            for (Nation other : state.livingNations()) {
                if (!other.id().equals(nation.id())
                        && state.relation(nation.id(), other.id()) == DiplomaticState.GUERRA) {
                    enGuerra = true;
                    break;
                }
            }

            // ΔD_p — ec. 3.6
            double deltaD = rules.etaTheta * (nation.taxRate() - rules.theta0)
                    + rules.etaW * (enGuerra ? 1.0 : 0.0)
                    + rules.etaN * Math.max(0, owned.size() - rules.nStar)
                    - rules.etaR;

            for (Province p : owned) {
                // D_p = clamp(D_p + ΔD, 0, 100) — ec. 3.7
                p.setDiscontent(p.discontent() + deltaD);

                // L_p = min(L_max, L_p·(1+g_L)) − ϱ·β_p — ec. 3.10
                double warDmg = warDamage.getOrDefault(p.id(), 0.0);
                long newPop = Math.min(rules.lMax,
                        Math.round(p.population() * (1.0 + rules.gL)));
                newPop = Math.max(0, Math.round(newPop - rules.rho * warDmg));
                p.setPopulation(newPop);
            }
        }
    }

    // --------------------------------------- Eliminaciones

    private void sweepEliminations(TurnReport report) {
        for (Nation nation : state.livingNations()) {
            if (state.provincesOf(nation.id()).isEmpty()) {
                eliminate(nation, report);
            }
        }
    }

    private void eliminate(Nation nation, TurnReport report) {
        nation.setEliminated(true);
        nation.setKingProvinceId(null);
        report.add("*** " + nation.name() + " ha sido eliminada ***");
    }

    private void killKing(Nation nation, TurnReport report) {
        nation.setKingProvinceId(null);
        report.add("¡El rey de " + nation.name() + " ha muerto!");

        List<Province> owned = state.provincesOf(nation.id());
        int keep = Math.max(1, (int) Math.floor(owned.size() * 0.10));
        if (owned.size() <= keep) {
            return;
        }
        owned.sort(Comparator.comparingInt(Province::troops).reversed());
        for (Province province : owned.subList(keep, owned.size())) {
            province.setOwnerId(null);
        }
        report.add(nation.name() + " pierde " + (owned.size() - keep)
                + " provincias por la muerte de su rey");
        if (keep == 0) {
            eliminate(nation, report);
        }
    }

    // --------------------------------------- Victoria

    private void checkVictory(TurnReport report) {
        List<Nation> living = state.livingNations();
        if (living.size() == 1) {
            report.setWinnerId(living.get(0).id());
            report.add("### " + living.get(0).name() + " domina el mapa: ¡victoria! ###");
            return;
        }

        // E9 — victoria por cuota territorial: q_ℓ = n_ℓ / N ≥ Θ_V.
        // N cuenta solo provincias de tierra: las marítimas no son conquistables.
        int land = state.landProvinceCount();
        if (land > 0) {
            for (Nation nation : living) {
                double quota = (double) state.provincesOf(nation.id()).size() / land;
                if (quota >= rules.thetaV) {
                    report.setWinnerId(nation.id());
                    report.add(String.format(
                            "### %s controla el %.0f%% del mapa (Θ_V = %.0f%%): ¡victoria! ###",
                            nation.name(), quota * 100, rules.thetaV * 100));
                    return;
                }
            }
        }

        if (rules.tMax > 0 && state.turn() >= rules.tMax) {
            Nation winner = living.stream()
                    .max(Comparator
                            .comparingInt((Nation n) -> state.provincesOf(n.id()).size())
                            .thenComparingInt(n -> state.totalTroops(n.id())))
                    .orElseThrow();
            report.setWinnerId(winner.id());
            report.add("### Límite de " + rules.tMax + " turnos alcanzado: gana "
                    + winner.name() + " por clasificación ###");
        }
    }

    private String nameOf(String nationId) {
        return state.nation(nationId).name();
    }
}
