package engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Crónica de la resolución de un turno: eventos legibles y ganador (si lo hay). */
public class TurnReport {

    private final int turn;
    private final List<String> events = new ArrayList<>();
    private String winnerId;

    public TurnReport(int turn) {
        this.turn = turn;
    }

    public int turn() {
        return turn;
    }

    void add(String event) {
        events.add(event);
    }

    public List<String> events() {
        return Collections.unmodifiableList(events);
    }

    void setWinnerId(String winnerId) {
        this.winnerId = winnerId;
    }

    /** Id de la nación ganadora, o {@code null} si la partida continúa. */
    public String winnerId() {
        return winnerId;
    }

    public boolean gameOver() {
        return winnerId != null;
    }
}
