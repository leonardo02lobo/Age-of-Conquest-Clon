package engine;

/** Orden inválida: la razón (en español) está pensada para mostrarse al jugador. */
public class OrderException extends RuntimeException {

    public OrderException(String message) {
        super(message);
    }
}
