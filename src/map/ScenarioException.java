package map;

/** Error de formato o de consistencia en un archivo de escenario. */
public class ScenarioException extends RuntimeException {

    public ScenarioException(String message) {
        super(message);
    }

    public ScenarioException(String message, Throwable cause) {
        super(message, cause);
    }
}
