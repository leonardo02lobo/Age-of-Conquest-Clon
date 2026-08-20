package sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BatchRunnerTest {

    static BatchRunner runner;

    @BeforeAll
    static void setUp() throws Exception {
        runner = new BatchRunner(Path.of("scenarios/europa_antigua.json"));
    }

    @Test
    void unLoteProduceUnResultadoPorSemilla() {
        List<GameResult> results = runner.run("base", "base", 0, r -> {
        }, 2, 500);
        assertEquals(2, results.size());
        assertEquals(500, results.get(0).seed());
        assertEquals(501, results.get(1).seed());
        for (GameResult result : results) {
            assertNotNull(result.winnerId(), "toda partida debe tener ganador");
            assertTrue(result.turns() >= 1 && result.turns() <= 250);
            assertTrue(result.winnerProvinces() >= 1);
            assertTrue(result.revolts() >= 0);
            assertEquals("base", result.experiment());
        }
    }

    @Test
    void laMismaSemillaProduceExactamenteLaMismaPartida() {
        GameResult first = runner.playOne("base", "base", 0, r -> {
        }, 777);
        GameResult second = runner.playOne("base", "base", 0, r -> {
        }, 777);
        assertEquals(first, second); // el record completo, métrica a métrica
    }

    @Test
    void losParametrosModificadosLleganALaPartida() {
        GameResult result = runner.playOne("kbeta", "kBeta", 0.5,
                r -> r.kBeta = 0.5, 900);
        assertEquals(0.5, result.value());
        assertEquals("kBeta", result.parameter());
        assertNotNull(result.winnerId());
        assertTrue(result.revolts() >= 0);
    }

    @Test
    void elCsvTieneElFormatoEsperado() {
        GameResult result = runner.playOne("base", "base", 0, r -> {
        }, 42);
        assertEquals(8, GameResult.csvHeader().chars().filter(c -> c == ',').count());
        assertEquals(8, result.toCsvRow().chars().filter(c -> c == ',').count());
        assertTrue(result.toCsvRow().startsWith("base,base,0,42,"));
    }
}
