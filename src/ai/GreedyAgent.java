package ai;

import engine.Order;
import engine.OrderException;
import engine.TurnEngine;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import model.DiplomaticState;
import model.GameState;
import model.Nation;
import model.Province;
import model.Rules;

/**
 * IA heurística "codiciosa" (fase M4 del plan):
 *
 *   1. Ajusta la tasa impositiva en temporada fiscal según la felicidad media.
 *   2. Apaga incendios: decreta fiestas en las provincias más descontentas.
 *   3. Ataca neutrales o enemigos solo con ventaja ≥ {@link #attackAdvantage}
 *      sobre la defensa efectiva (fortificación y rey incluidos).
 *   4. Si no queda nada que conquistar y domina militarmente a un vecino,
 *      le declara la guerra.
 *   5. Refuerza la frontera: las tropas del interior avanzan hacia el borde
 *      (BFS multi-fuente sobre el territorio propio).
 *   6. Fortifica la provincia del rey y recluta con el excedente de oro.
 *
 * Es determinista: toda la aleatoriedad de la partida queda en las revueltas.
 * Los umbrales son públicos y calibrables (material para los experimentos de
 * simulación e incluso para el calibrado genético de la fase M8).
 */
public class GreedyAgent implements Agent {

    /** Ventaja mínima (fuerza propia / defensa efectiva) para lanzar un ataque. */
    public double attackAdvantage = 1.5;

    /** Oro que la IA nunca gasta (colchón para el mantenimiento). */
    public double goldReserve = 30.0;

    /** Bajo esta felicidad la IA decreta fiestas en la provincia. */
    public double unhappyThreshold = 45.0;

    /** Felicidad media bajo la cual baja impuestos, y sobre la cual los sube. */
    public double lowerTaxBelow = 50.0;
    public double raiseTaxAbove = 80.0;

    /** Soldados que siempre quedan de guarnición al mover o atacar. */
    public int garrisonLeft = 1;

    /** Superioridad total (tropas propias / ajenas) para declarar una guerra. */
    public double declareWarSuperiority = 2.0;

    /** Máximo de decretos de felicidad por turno. */
    public int maxDecreesPerTurn = 3;

    @Override
    public void plan(TurnEngine engine, Nation nation) {
        GameState state = engine.state();
        List<Province> owned = state.provincesOf(nation.id());
        if (owned.isEmpty()) {
            return;
        }
        adjustTaxes(engine, nation, state, owned);
        calmUnrest(engine, nation, owned);
        boolean conqueredSomething = attack(engine, nation, state, owned);
        if (!conqueredSomething) {
            declareWarIfDominant(engine, nation, state, owned);
            attack(engine, nation, state, owned); // reintenta con la guerra nueva
        }
        reinforceFrontier(engine, nation, state);
        fortifyKingSeat(engine, nation, state);
        recruit(engine, nation, state);
    }

    /** Emite una orden; devuelve false si el motor la rechaza (AP, oro, etc.). */
    private boolean tryOrder(TurnEngine engine, Order order) {
        try {
            engine.submit(order);
            return true;
        } catch (OrderException e) {
            return false;
        }
    }

    // -------------------------------------------------------------- impuestos

    private void adjustTaxes(TurnEngine engine, Nation nation, GameState state, List<Province> owned) {
        if (!state.rules().isTaxSeason(state.turn())) {
            return;
        }
        double totalHappiness = 0;
        for (Province p : owned) {
            totalHappiness += p.happiness();
        }
        double average = totalHappiness / owned.size();
        int[] rates = state.rules().allowedTaxRates;
        int index = 0;
        for (int i = 0; i < rates.length; i++) {
            if (rates[i] == nation.taxRate()) {
                index = i;
            }
        }
        if (average < lowerTaxBelow && index > 0) {
            tryOrder(engine, new Order.SetTaxRate(nation.id(), rates[index - 1]));
        } else if (average > raiseTaxAbove && index < rates.length - 1) {
            tryOrder(engine, new Order.SetTaxRate(nation.id(), rates[index + 1]));
        }
    }

    // -------------------------------------------------------------- felicidad

    private void calmUnrest(TurnEngine engine, Nation nation, List<Province> owned) {
        List<Province> unhappy = new ArrayList<>(owned);
        unhappy.removeIf(p -> p.happiness() >= unhappyThreshold);
        unhappy.sort(Comparator.comparingDouble(Province::happiness));
        int issued = 0;
        for (Province p : unhappy) {
            if (issued >= maxDecreesPerTurn || nation.gold() < goldReserve) {
                break;
            }
            if (tryOrder(engine, new Order.Decree(nation.id(), p.id(), Order.DecreeType.FIESTA))) {
                issued++;
            }
        }
    }

    // ---------------------------------------------------------------- ataques

    /** Lanza los ataques rentables. Devuelve true si ordenó al menos uno. */
    private boolean attack(TurnEngine engine, Nation nation, GameState state, List<Province> owned) {
        boolean attacked = false;
        for (Province from : owned) {
            int available = from.troops() - garrisonLeft;
            if (available < 1) {
                continue;
            }
            Province best = null;
            for (Province target : state.reachableFrom(from.id())) {
                if (!isAttackable(nation, state, target)) {
                    continue;
                }
                if (available < attackAdvantage * effectiveDefense(state, target)) {
                    continue;
                }
                if (best == null || target.population() > best.population()) {
                    best = target; // conquistar población es conquistar impuestos
                }
            }
            if (best != null
                    && tryOrder(engine, new Order.Move(nation.id(), from.id(), best.id(), available, false))) {
                attacked = true;
            }
        }
        return attacked;
    }

    private boolean isAttackable(Nation nation, GameState state, Province target) {
        if (target.isWater() || nation.id().equals(target.ownerId())) {
            return false;
        }
        return target.ownerId() == null
                || state.relation(nation.id(), target.ownerId()) == DiplomaticState.GUERRA;
    }

    /** Fuerza defensiva efectiva de una provincia (tropas, fortificación y rey). */
    private double effectiveDefense(GameState state, Province target) {
        Rules rules = state.rules();
        double bonus = target.isFortified() ? rules.fortDefenseBonus : 0;
        if (target.ownerId() != null
                && target.id().equals(state.nation(target.ownerId()).kingProvinceId())) {
            bonus += rules.kingCombatBonus;
        }
        return Math.max(1, target.troops()) * (1 + bonus);
    }

    // ------------------------------------------------------------- diplomacia

    private void declareWarIfDominant(TurnEngine engine, Nation nation, GameState state,
                                      List<Province> owned) {
        for (Nation other : state.livingNations()) {
            if (nation.relation(other.id()) == DiplomaticState.GUERRA) {
                return; // una guerra a la vez
            }
        }
        int myTroops = state.totalTroops(nation.id());
        Nation weakest = null;
        for (Province from : owned) {
            for (Province target : state.reachableFrom(from.id())) {
                String otherId = target.ownerId();
                if (otherId == null || otherId.equals(nation.id())) {
                    continue;
                }
                Nation other = state.nation(otherId);
                if (myTroops >= declareWarSuperiority * Math.max(1, state.totalTroops(otherId))
                        && (weakest == null || state.totalTroops(otherId) < state.totalTroops(weakest.id()))) {
                    weakest = other;
                }
            }
        }
        if (weakest != null) {
            tryOrder(engine, new Order.DeclareWar(nation.id(), weakest.id()));
        }
    }

    // ------------------------------------------------------------- movimiento

    /**
     * Mueve las tropas del interior un paso hacia la frontera, siguiendo un
     * BFS multi-fuente desde las provincias fronterizas por territorio propio.
     */
    private void reinforceFrontier(TurnEngine engine, Nation nation, GameState state) {
        List<Province> owned = state.provincesOf(nation.id());
        Map<String, Integer> distance = distanceToBorder(nation, state, owned);
        for (Province p : owned) {
            Integer dist = distance.get(p.id());
            if (dist == null || dist == 0 || p.troops() <= garrisonLeft) {
                continue; // frontera, aislada o sin excedente
            }
            for (Province neighbor : state.reachableFrom(p.id())) {
                Integer neighborDist = distance.get(neighbor.id());
                if (neighborDist != null && neighborDist < dist) {
                    tryOrder(engine, new Order.Move(nation.id(), p.id(), neighbor.id(),
                            p.troops() - garrisonLeft, false));
                    break;
                }
            }
        }
    }

    private Map<String, Integer> distanceToBorder(Nation nation, GameState state, List<Province> owned) {
        Map<String, Integer> distance = new HashMap<>();
        Deque<Province> queue = new ArrayDeque<>();
        for (Province p : owned) {
            for (Province neighbor : state.reachableFrom(p.id())) {
                if (!nation.id().equals(neighbor.ownerId())) {
                    distance.put(p.id(), 0);
                    queue.add(p);
                    break;
                }
            }
        }
        while (!queue.isEmpty()) {
            Province current = queue.poll();
            for (Province neighbor : state.reachableFrom(current.id())) {
                if (nation.id().equals(neighbor.ownerId()) && !distance.containsKey(neighbor.id())) {
                    distance.put(neighbor.id(), distance.get(current.id()) + 1);
                    queue.add(neighbor);
                }
            }
        }
        return distance;
    }

    // ------------------------------------------------------- fortificar y reclutar

    private void fortifyKingSeat(TurnEngine engine, Nation nation, GameState state) {
        String kingSeat = nation.kingProvinceId();
        if (kingSeat == null) {
            return;
        }
        Province seat = state.province(kingSeat);
        if (!seat.isFortified()
                && nation.gold() >= goldReserve + state.rules().goldCostFortify) {
            tryOrder(engine, new Order.Fortify(nation.id(), kingSeat));
        }
    }

    private void recruit(TurnEngine engine, Nation nation, GameState state) {
        Rules rules = state.rules();
        double budget = nation.gold() - goldReserve;
        if (budget < 1) {
            return;
        }
        // Recluta donde está el rey (defensa de la capital); si no, en la
        // provincia propia más poblada.
        Province where = null;
        if (nation.kingProvinceId() != null) {
            where = state.province(nation.kingProvinceId());
        } else {
            for (Province p : state.provincesOf(nation.id())) {
                if (where == null || p.population() > where.population()) {
                    where = p;
                }
            }
        }
        if (where == null) {
            return;
        }
        int affordable = (int) (budget / rules.recruitGoldPerSoldier);
        int byPopulation = (int) (where.population() / Math.max(1, rules.recruitPopulationPerSoldier));
        int soldiers = Math.min(affordable, byPopulation);
        if (soldiers >= 1) {
            tryOrder(engine, new Order.Recruit(nation.id(), where.id(), soldiers));
        }
    }
}
