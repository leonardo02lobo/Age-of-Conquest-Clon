package ai;

import engine.TurnEngine;
import model.Nation;

/**
 * Un jugador artificial: durante la fase de planificación emite sus órdenes
 * directamente al motor (que las valida y cobra igual que a un humano).
 */
public interface Agent {

    /** Planifica y emite las órdenes de {@code nation} para el turno actual. */
    void plan(TurnEngine engine, Nation nation);
}
