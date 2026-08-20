package model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Una provincia del mapa: la unidad territorial básica, tipo Risk.
 * Puede ser tierra (con población, descontento, dueño y guarnición) o una zona
 * marítima (solo transitable por tropas embarcadas; sin población ni dueño).
 *
 * Campo clave del modelo formal: D_p (descontento) ∈ [0, 100], inicial D_0 = 20.
 */
public class Province {

    private final String id;
    private final String name;
    private final boolean water;
    private final Set<String> adjacent = new LinkedHashSet<>();

    private long population;
    private double discontent;    // D_p ∈ [0, 100], inicial D_0 = 20
    private int fortification;    // φ_p ∈ {0, 1, …, Φ_max=4}, niveles enteros
    private String ownerId;       // null = neutral
    private int troops;           // guarnición actual
    private TerrainType terrain;  // tipo de terreno (default: LLANURA)
    private int[] polygon;

    public Province(String id, String name, boolean water) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("La provincia necesita un id no vacío");
        }
        this.id = id;
        this.name = (name == null || name.isBlank()) ? id : name;
        this.water = water;
        this.terrain = TerrainType.LLANURA;
        this.discontent = 20.0; // D_0 según tabla §1.1
    }

    public String id() { return id; }
    public String name() { return name; }
    public boolean isWater() { return water; }

    public Set<String> adjacent() {
        return Collections.unmodifiableSet(adjacent);
    }

    public void addAdjacent(String provinceId) {
        if (provinceId.equals(id)) {
            throw new IllegalArgumentException("La provincia '" + id + "' no puede ser adyacente a sí misma");
        }
        adjacent.add(provinceId);
    }

    // ------------------------------------------------------------------ población

    public long population() { return population; }

    public void setPopulation(long population) {
        if (water && population > 0) {
            throw new IllegalStateException("La zona marítima '" + id + "' no puede tener población");
        }
        this.population = Math.max(0, population);
    }

    // ---------------------------------------------------------- descontento (D_p)

    /** Descontento provincial D_p ∈ [0, 100]. Mayor = más descontento. */
    public double discontent() { return discontent; }

    /** Fija el descontento, acotado a [0, 100]. */
    public void setDiscontent(double discontent) {
        this.discontent = Math.clamp(discontent, 0.0, 100.0);
    }

    /**
     * Felicidad derivada para la UI: 100 − D_p.
     * No se usa en el cálculo del modelo; es solo presentación.
     */
    public double happiness() { return 100.0 - discontent; }

    // ----------------------------------------------------- fortificación (φ_p)

    /**
     * Nivel de fortificación φ_p ∈ {0, 1, …, Φ_max}.
     * La función de fortificación es Φ(φ) = 1 + β_F · φ (ec. 3.16).
     */
    public int fortification() { return fortification; }

    public void setFortification(int fortification) {
        this.fortification = Math.clamp(fortification, 0, 4);
    }

    /** Método de compatibilidad: ¿está fortificada (nivel ≥ 1)? */
    public boolean isFortified() { return fortification >= 1; }

    // ------------------------------------------------------------------ dueño

    public String ownerId() { return ownerId; }
    public boolean isNeutral() { return ownerId == null; }

    public void setOwnerId(String ownerId) {
        if (water && ownerId != null) {
            throw new IllegalStateException("La zona marítima '" + id + "' no puede tener dueño");
        }
        this.ownerId = ownerId;
    }

    // ------------------------------------------------------------------ tropas

    public int troops() { return troops; }

    public void setTroops(int troops) {
        this.troops = Math.max(0, troops);
    }

    // --------------------------------------------------------------- terreno

    public TerrainType terrain() { return terrain; }

    public void setTerrain(TerrainType terrain) {
        this.terrain = terrain == null ? TerrainType.LLANURA : terrain;
    }

    // ---------------------------------------------------------------- polígono

    public int[] polygon() { return polygon; }

    public void setPolygon(int[] polygon) {
        if (polygon != null && (polygon.length < 6 || polygon.length % 2 != 0)) {
            throw new IllegalArgumentException(
                    "El polígono de '" + id + "' necesita una lista par de al menos 6 coordenadas");
        }
        this.polygon = polygon;
    }

    @Override
    public String toString() {
        return id + (water ? " [agua]" : " [" + (ownerId == null ? "neutral" : ownerId)
                + ", tropas=" + troops + ", φ=" + fortification + ", D=" + discontent + "]");
    }
}
