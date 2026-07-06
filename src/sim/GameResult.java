package sim;

/**
 * Métricas de una partida simulada IA contra IA.
 *
 * @param experiment       nombre del experimento al que pertenece la partida
 * @param parameter        parámetro de Rules que se está variando ("base" si ninguno)
 * @param value            valor del parámetro en esta partida
 * @param seed             semilla del generador aleatorio (reproducibilidad)
 * @param winnerId         nación ganadora
 * @param turns            turno en el que terminó la partida
 * @param revolts          revueltas triunfantes en toda la partida
 * @param winnerProvinces  provincias del ganador al terminar
 * @param winnerTroops     tropas del ganador al terminar
 */
public record GameResult(String experiment, String parameter, double value, long seed,
                         String winnerId, int turns, int revolts,
                         int winnerProvinces, int winnerTroops) {

    public static String csvHeader() {
        return "experimento,parametro,valor,semilla,ganador,turnos,revueltas,provincias_ganador,tropas_ganador";
    }

    public String toCsvRow() {
        return String.format(java.util.Locale.ROOT, "%s,%s,%s,%d,%s,%d,%d,%d,%d",
                experiment, parameter, trimmed(value), seed, winnerId,
                turns, revolts, winnerProvinces, winnerTroops);
    }

    private static String trimmed(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
