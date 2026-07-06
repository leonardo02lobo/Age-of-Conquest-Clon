package model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Estado completo de una partida: mapa (provincias), naciones y turno actual.
 * Es el "modelo" puro de la simulación: no conoce la interfaz ni el motor.
 */
public class GameState {

    private final String scenarioName;
    private final Rules rules;
    private final Map<String, Province> provinces; // LinkedHashMap: conserva el orden del escenario
    private final Map<String, Nation> nations;
    private final Random random;
    private int turn = 1;

    public GameState(String scenarioName, Rules rules,
                     Map<String, Province> provinces, Map<String, Nation> nations) {
        this.scenarioName = scenarioName;
        this.rules = rules;
        this.provinces = new LinkedHashMap<>(provinces);
        this.nations = new LinkedHashMap<>(nations);
        this.random = new Random(rules.randomSeed);
    }

    /**
     * Generador aleatorio de la partida, sembrado con {@code Rules.randomSeed}:
     * con la misma semilla y las mismas órdenes, la partida es reproducible
     * (requisito de los experimentos de simulación).
     */
    public Random random() {
        return random;
    }

    public String scenarioName() {
        return scenarioName;
    }

    public Rules rules() {
        return rules;
    }

    public int turn() {
        return turn;
    }

    /** Avanza el reloj de la simulación (lo invoca el motor al cerrar cada turno). */
    public void advanceTurn() {
        turn++;
    }

    // ------------------------------------------------------------------ mapa

    public Province province(String id) {
        Province p = provinces.get(id);
        if (p == null) {
            throw new IllegalArgumentException("Provincia desconocida: '" + id + "'");
        }
        return p;
    }

    public boolean hasProvince(String id) {
        return provinces.containsKey(id);
    }

    public Collection<Province> provinces() {
        return Collections.unmodifiableCollection(provinces.values());
    }

    /** ¿Son adyacentes las dos provincias? (el grafo es simétrico tras la carga). */
    public boolean areAdjacent(String provinceA, String provinceB) {
        return province(provinceA).adjacent().contains(province(provinceB).id());
    }

    // -------------------------------------------------------------- naciones

    public Nation nation(String id) {
        Nation n = nations.get(id);
        if (n == null) {
            throw new IllegalArgumentException("Nación desconocida: '" + id + "'");
        }
        return n;
    }

    public boolean hasNation(String id) {
        return nations.containsKey(id);
    }

    public Collection<Nation> nations() {
        return Collections.unmodifiableCollection(nations.values());
    }

    public List<Nation> livingNations() {
        List<Nation> alive = new ArrayList<>();
        for (Nation n : nations.values()) {
            if (!n.isEliminated()) {
                alive.add(n);
            }
        }
        return alive;
    }

    /** Provincias controladas por una nación, en el orden del mapa. */
    public List<Province> provincesOf(String nationId) {
        nation(nationId); // valida el id
        List<Province> owned = new ArrayList<>();
        for (Province p : provinces.values()) {
            if (nationId.equals(p.ownerId())) {
                owned.add(p);
            }
        }
        return owned;
    }

    /** Total de soldados de una nación en todo el mapa. */
    public int totalTroops(String nationId) {
        int total = 0;
        for (Province p : provincesOf(nationId)) {
            total += p.troops();
        }
        return total;
    }

    // ------------------------------------------------------------ diplomacia

    /** Relación diplomática entre dos naciones (simétrica por construcción). */
    public DiplomaticState relation(String nationA, String nationB) {
        return nation(nationA).relation(nation(nationB).id());
    }

    /** Fija la relación entre dos naciones manteniendo la simetría en ambos lados. */
    public void setRelation(String nationA, String nationB, DiplomaticState state) {
        Nation a = nation(nationA);
        Nation b = nation(nationB);
        a.setRelation(b.id(), state);
        b.setRelation(a.id(), state);
    }
}
