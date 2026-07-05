package model;

/**
 * Estado diplomático entre dos naciones. Es siempre simétrico: si A está en
 * GUERRA con B, B está en GUERRA con A (ver {@link GameState#setRelation}).
 * Los nombres coinciden con los valores usados en los archivos de escenario JSON.
 */
public enum DiplomaticState {
    GUERRA,
    NEUTRAL,
    ALIANZA
}
