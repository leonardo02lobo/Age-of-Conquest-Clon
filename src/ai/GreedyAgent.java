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
import model.LCG;
import model.Nation;
import model.Province;
import model.Rules;
import model.TerrainType;

/**
 * IA con las 4 estrategias del modelo formal (tabla §1.9):
 *
 *   AGRESIVA  — θ=125%, f_rec=0.90, γ_atq=1.1, γ_σ=1.2, f_gua=0.15, f_fort=0.05
 *   DEFENSIVA — θ=100%, f_rec=0.60, γ_atq=1.8, γ_σ=2.5, f_gua=0.50, f_fort=0.40
 *   ECONÓMICA — θ=θ_eq, f_rec=0.30, γ_atq=2.0, γ_σ=3.0, f_gua=0.40, f_fort=0.25
 *   EQUILIBRADA — θ=½(100+θ_eq), f_rec=0.70, γ_atq=1.4, γ_σ=1.8, f_gua=0.30, f_fort=0.20
 *
 * La estrategia de cada nación se asigna por su tasa impositiva inicial
 * (o se puede fijar externamente).
 */
public class GreedyAgent implements Agent {

    private final LCG lcg = new LCG(20260805L);

    @Override
    public void plan(TurnEngine engine, Nation nation) {
        GameState state = engine.state();
        Rules rules = state.rules();
        List<Province> owned = state.provincesOf(nation.id());
        if (owned.isEmpty()) return;

        Rules.Strategy strategy = detectStrategy(nation, rules);

        declareWarsIfNeeded(engine, nation, rules, strategy, owned);

        recruitByStrategy(engine, nation, rules, strategy, owned);
        fortifyByStrategy(engine, nation, rules, strategy, owned);
        attackByStrategy(engine, nation, rules, strategy, owned);
        adjustTaxesByStrategy(engine, nation, rules, strategy, owned);
    }

    /**
     * Detecta la estrategia de una nación según su tasa impositiva.
     */
    private Rules.Strategy detectStrategy(Nation nation, Rules rules) {
        int tax = nation.taxRate();
        if (tax >= 125) return Rules.Strategy.AGRESIVA;
        if (tax <= 50) return Rules.Strategy.ECONOMICA;
        if (tax == 100) return Rules.Strategy.DEFENSIVA;
        return Rules.Strategy.EQUILIBRADA;
    }

    // -------------------------------------------------------------- reclutamiento

    /**
     * F2: u_i(t) = floor(f_rec(σ) · G_i(t) / c_u) — ec. 3.11
     * Se recluta en la capital (o provincia con más población).
     */
    private void recruitByStrategy(TurnEngine engine, Nation nation, Rules rules,
                                   Rules.Strategy strategy, List<Province> owned) {
        double budget = nation.gold();
        double fRec = rules.fRecForStrategy(strategy);
        int units = (int) Math.floor(fRec * budget / rules.cU);
        if (units < 1) return;

        // Reclutar donde está el rey (capital) o la provincia más poblada
        Province where = null;
        if (nation.kingProvinceId() != null) {
            where = state(engine).province(nation.kingProvinceId());
        }
        if (where == null) {
            for (Province p : owned) {
                if (where == null || p.population() > where.population()) {
                    where = p;
                }
            }
        }
        if (where == null) return;

        // Capacidad de población: L_p / ϱ
        int byPopulation = (int) (where.population() / Math.max(1, rules.rho));
        int soldiers = Math.min(units, byPopulation);
        if (soldiers >= 1) {
            tryOrder(engine, new Order.Recruit(nation.id(), where.id(), soldiers));
        }
    }

    // -------------------------------------------------------------- fortificación

    /**
     * Fortificar la frontera con menor fuerza defensiva.
     * Prioridad = f_fort(σ) de la tabla 1.9.
     */
    private void fortifyByStrategy(TurnEngine engine, Nation nation, Rules rules,
                                   Rules.Strategy strategy, List<Province> owned) {
        double fFort = rules.fFortForStrategy(strategy);
        if (fFort <= 0) return;
        if (nation.gold() < rules.cPhi) return;

        // Buscar provincia fronteriza con menor defensa
        Province best = null;
        int bestDefense = Integer.MAX_VALUE;
        for (Province p : owned) {
            if (p.fortification() >= rules.phiMax) continue;
            int defense = p.troops() + p.fortification() * 5;
            if (defense < bestDefense) {
                // Verificar si es frontera
                for (Province adj : state(engine).reachableFrom(p.id())) {
                    if (!nation.id().equals(adj.ownerId())) {
                        best = p;
                        bestDefense = defense;
                        break;
                    }
                }
            }
        }
        if (best != null) {
            tryOrder(engine, new Order.Fortify(nation.id(), best.id()));
        }
    }

    // -------------------------------------------------------------- ataque

    /**
     * Selecciona objetivos según γ_atq(σ) — la ventaja mínima para atacar.
     * F_env = F_a · (1 − f_gua(σ)) — tropas disponibles tras留守.
     */
    private void attackByStrategy(TurnEngine engine, Nation nation, Rules rules,
                                  Rules.Strategy strategy, List<Province> owned) {
        double gammaAtq = rules.gammaAtqForStrategy(strategy);
        double fGua = rules.fGuaForStrategy(strategy);

        for (Province from : owned) {
            int totalTroops = from.troops();
            int retained = (int) Math.ceil(totalTroops * fGua);
            int available = totalTroops - retained;
            if (available < 1) continue;

            Province best = null;
            for (Province target : state(engine).reachableFrom(from.id())) {
                if (!isAttackable(nation, state(engine), target)) continue;

                // P_a = F_env · μ_a · T(T_p, ATQ) — determinista (μ=1, T=1 para planificar)
                double pA = available * 1.0 * TerrainType.LLANURA.attackModifier;

                // P_d = D_p · Φ(φ) · T(T_p, DEF) · Ψ(D_p)
                double fortBonus = 1.0 + rules.betaF * target.fortification();
                double psi = 1.0 - 0.4 * target.discontent() / 100.0;
                double pD = Math.max(1, target.troops()) * fortBonus
                        * target.terrain().defenseModifier * psi;

                if (pA >= gammaAtq * pD) {
                    if (best == null || target.population() > best.population()) {
                        best = target;
                    }
                }
            }

            if (best != null) {
                tryOrder(engine, new Order.Move(nation.id(), from.id(), best.id(), available, false));
            }
        }
    }

    private boolean isAttackable(Nation nation, GameState state, Province target) {
        if (target.isWater() || nation.id().equals(target.ownerId())) return false;
        return target.ownerId() == null
                || state.relation(nation.id(), target.ownerId()) == DiplomaticState.GUERRA;
    }

    // -------------------------------------------------------------- impuestos

    /**
     * Ajusta la tasa impositiva según la estrategia:
     *   ECONÓMICA: θ = θ_eq (ec. 3.8)
     *   EQUILIBRADA: θ = ½(100 + θ_eq)
     *   AGRESIVA: θ = 125 (fijo)
     *   DEFENSIVA: θ = 100 (fijo)
     */
    private void adjustTaxesByStrategy(TurnEngine engine, Nation nation, Rules rules,
                                       Rules.Strategy strategy, List<Province> owned) {
        int targetTax;
        switch (strategy) {
            case ECONOMICA -> {
                double thetaEq = thetaEq(rules, owned.size(), isAtWar(engine.state(), nation));
                targetTax = (int) Math.round(Math.max(0, Math.min(rules.thetaMax, thetaEq)));
            }
            case EQUILIBRADA -> {
                double thetaEq = thetaEq(rules, owned.size(), isAtWar(engine.state(), nation));
                targetTax = (int) Math.round(Math.max(0, Math.min(rules.thetaMax,
                        (100 + thetaEq) / 2.0)));
            }
            case AGRESIVA -> targetTax = 125;
            case DEFENSIVA -> targetTax = 100;
            default -> targetTax = nation.taxRate();
        }
        if (targetTax != nation.taxRate()) {
            tryOrder(engine, new Order.SetTaxRate(nation.id(), targetTax));
        }
    }

    /**
     * θ_eq = θ_0 + (η_r − η_w·1[guerra] − η_n·max(0, n−n*)) / η_θ — ec. 3.8
     */
    private double thetaEq(Rules rules, int numProvinces, boolean atWar) {
        double guerra = atWar ? rules.etaW : 0;
        double sobreextension = rules.etaN * Math.max(0, numProvinces - rules.nStar);
        return rules.theta0 + (rules.etaR - guerra - sobreextension) / rules.etaTheta;
    }

    private boolean isAtWar(GameState state, Nation nation) {
        for (Nation other : state.livingNations()) {
            if (!other.id().equals(nation.id())
                    && state.relation(nation.id(), other.id()) == DiplomaticState.GUERRA) {
                return true;
            }
        }
        return false;
    }

    private void declareWarsIfNeeded(TurnEngine engine, Nation nation, Rules rules,
                                      Rules.Strategy strategy, List<Province> owned) {
        double gammaSigma = rules.gammaSigmaForStrategy(strategy);
        double myTroops = owned.stream().mapToInt(Province::troops).sum();

        for (Province p : owned) {
            for (Province adj : engine.state().reachableFrom(p.id())) {
                if (adj.isWater() || nation.id().equals(adj.ownerId())) continue;
                if (adj.ownerId() != null
                        && state(engine).relation(nation.id(), adj.ownerId()) != DiplomaticState.NEUTRAL) {
                    continue;
                }
                double theirTroops = adj.ownerId() == null ? 1 : Math.max(1, state(engine).totalTroops(adj.ownerId()));
                if (myTroops / theirTroops >= gammaSigma) {
                    if (adj.ownerId() != null) {
                        tryOrder(engine, new Order.DeclareWar(nation.id(), adj.ownerId()));
                    }
                    break;
                }
            }
        }
    }

    private boolean tryOrder(TurnEngine engine, Order order) {
        try {
            engine.submit(order);
            return true;
        } catch (OrderException e) {
            return false;
        }
    }

    private GameState state(TurnEngine engine) {
        return engine.state();
    }
}
