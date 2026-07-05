package model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Una nación (facción) de la partida: tesoro, puntos de acción, ubicación del
 * rey y relaciones diplomáticas con las demás naciones.
 */
public class Nation {

    private final String id;
    private final String name;
    private final boolean ai;

    private double gold;
    private double actionPoints;
    private String kingProvinceId; // null = el rey ha muerto (o la regla está desactivada)
    private boolean eliminated;
    private final Map<String, DiplomaticState> relations = new HashMap<>();

    public Nation(String id, String name, boolean ai) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("La nación necesita un id no vacío");
        }
        this.id = id;
        this.name = (name == null || name.isBlank()) ? id : name;
        this.ai = ai;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public boolean isAI() {
        return ai;
    }

    public double gold() {
        return gold;
    }

    public void setGold(double gold) {
        this.gold = gold; // puede ser negativa: deuda (espiral documentada en el juego real)
    }

    public double actionPoints() {
        return actionPoints;
    }

    public void setActionPoints(double actionPoints) {
        this.actionPoints = Math.max(0, actionPoints);
    }

    /** Provincia donde está el rey, o {@code null} si no tiene. */
    public String kingProvinceId() {
        return kingProvinceId;
    }

    public void setKingProvinceId(String kingProvinceId) {
        this.kingProvinceId = kingProvinceId;
    }

    public boolean isEliminated() {
        return eliminated;
    }

    public void setEliminated(boolean eliminated) {
        this.eliminated = eliminated;
    }

    /** Relación con otra nación; NEUTRAL si nunca se ha fijado. */
    public DiplomaticState relation(String otherNationId) {
        return relations.getOrDefault(otherNationId, DiplomaticState.NEUTRAL);
    }

    /**
     * Fija la relación con otra nación. Solo un lado del par: para mantener la
     * simetría usar {@link GameState#setRelation}.
     */
    void setRelation(String otherNationId, DiplomaticState state) {
        if (otherNationId.equals(id)) {
            throw new IllegalArgumentException("La nación '" + id + "' no puede tener relación consigo misma");
        }
        if (state == DiplomaticState.NEUTRAL) {
            relations.remove(otherNationId);
        } else {
            relations.put(otherNationId, state);
        }
    }

    /** Vista de solo lectura de las relaciones no neutrales. */
    public Map<String, DiplomaticState> relations() {
        return Collections.unmodifiableMap(relations);
    }

    @Override
    public String toString() {
        return id + " (oro=" + gold + ", AP=" + actionPoints + ")";
    }
}
