package model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Una provincia del mapa: la unidad territorial básica, tipo Risk.
 * Puede ser tierra (con población, felicidad, dueño y guarnición) o una zona
 * marítima (solo transitable por tropas embarcadas; sin población ni dueño).
 */
public class Province {

    private final String id;
    private final String name;
    private final boolean water;
    private final Set<String> adjacent = new LinkedHashSet<>();

    private long population;   // habitantes (siempre 0 en zonas marítimas)
    private double happiness;  // 0..100, solo relevante en tierra
    private boolean fortified;
    private String ownerId;    // id de la nación dueña; null = neutral
    private int troops;        // guarnición actual (neutral o del dueño)
    private int[] polygon;     // vértices [x1,y1, x2,y2, …] para la interfaz gráfica (opcional)

    public Province(String id, String name, boolean water) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("La provincia necesita un id no vacío");
        }
        this.id = id;
        this.name = (name == null || name.isBlank()) ? id : name;
        this.water = water;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public boolean isWater() {
        return water;
    }

    /** Ids de las provincias adyacentes (grafo no dirigido; siempre simétrico tras la carga). */
    public Set<String> adjacent() {
        return Collections.unmodifiableSet(adjacent);
    }

    /** Añade una adyacencia (solo en una dirección; la simetría la garantiza el cargador). */
    public void addAdjacent(String provinceId) {
        if (provinceId.equals(id)) {
            throw new IllegalArgumentException("La provincia '" + id + "' no puede ser adyacente a sí misma");
        }
        adjacent.add(provinceId);
    }

    public long population() {
        return population;
    }

    public void setPopulation(long population) {
        if (water && population > 0) {
            throw new IllegalStateException("La zona marítima '" + id + "' no puede tener población");
        }
        this.population = Math.max(0, population);
    }

    public double happiness() {
        return happiness;
    }

    /** Fija la felicidad, acotada al rango [0, 100]. */
    public void setHappiness(double happiness) {
        this.happiness = Math.clamp(happiness, 0.0, 100.0);
    }

    public boolean isFortified() {
        return fortified;
    }

    public void setFortified(boolean fortified) {
        if (water && fortified) {
            throw new IllegalStateException("La zona marítima '" + id + "' no puede fortificarse");
        }
        this.fortified = fortified;
    }

    /** Id de la nación dueña, o {@code null} si la provincia es neutral. */
    public String ownerId() {
        return ownerId;
    }

    public boolean isNeutral() {
        return ownerId == null;
    }

    public void setOwnerId(String ownerId) {
        if (water && ownerId != null) {
            throw new IllegalStateException("La zona marítima '" + id + "' no puede tener dueño");
        }
        this.ownerId = ownerId;
    }

    public int troops() {
        return troops;
    }

    public void setTroops(int troops) {
        this.troops = Math.max(0, troops);
    }

    /** Vértices del polígono del mapa ([x1,y1, x2,y2, …]), o {@code null} si el escenario no los define. */
    public int[] polygon() {
        return polygon;
    }

    public void setPolygon(int[] polygon) {
        if (polygon != null && (polygon.length < 6 || polygon.length % 2 != 0)) {
            throw new IllegalArgumentException(
                    "El polígono de '" + id + "' necesita una lista par de al menos 6 coordenadas");
        }
        this.polygon = polygon;
    }

    @Override
    public String toString() {
        return id + (water ? " [agua]" : " [" + (ownerId == null ? "neutral" : ownerId) + ", tropas=" + troops + "]");
    }
}
