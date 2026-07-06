package engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import map.ScenarioException;
import map.ScenarioLoader;
import model.GameState;
import org.junit.jupiter.api.Test;

/**
 * Pruebas de la fase económica (M3) sobre un mini-mapa de números redondos:
 *
 *   a(n1, 100 tropas, 1M hab.) — b(n1, 0 tropas, 100k hab.) — c(n2, 10 tropas, 50k hab.)
 *
 * Con las reglas por defecto (tasa 100%, sin guerra):
 *   ingresos n1 = 250·(1M/1M) + 250·(100k/1M) = 275
 *   gastos  n1 = ceil(100/20) + 2 provincias·1 = 7
 */
class EconomyTest {

    private static final String ECO = """
            {"nombre": "Eco",
             "provincias": [
               {"id": "a", "poblacion": 1000000, "adyacentes": ["b"]},
               {"id": "b", "poblacion": 100000, "adyacentes": ["c"]},
               {"id": "c", "poblacion": 50000}],
             "naciones": [
               {"id": "n1", "oro": 100, "ia": false, "provincias": {"a": 100, "b": 0}},
               {"id": "n2", "oro": 100, "ia": false, "provincias": {"c": 10}}]}
            """;

    /** Carga el escenario con las revueltas desactivadas (se prueban aparte). */
    private TurnEngine engine(String json) {
        GameState state = ScenarioLoader.fromJson(json);
        state.rules().revoltRiskK = 0;
        return new TurnEngine(state);
    }

    // ------------------------------------------------------ impuestos y gastos

    @Test
    void recaudaImpuestosYPagaMantenimiento() {
        TurnEngine engine = engine(ECO);
        engine.endTurn();
        assertEquals(368.0, engine.state().nation("n1").gold(), 1e-6); // 100 + 275 − 7
        assertEquals(110.5, engine.state().nation("n2").gold(), 1e-6); // 100 + 12.5 − 2
    }

    @Test
    void laPoblacionCreceYLaFelicidadSeRecupera() {
        TurnEngine engine = engine(ECO);
        engine.endTurn();
        GameState state = engine.state();
        assertEquals(1_000_000, state.province("a").population()); // tope alcanzado
        assertEquals(101_500, state.province("b").population());   // 100000 · 1.015
        assertEquals(76.0, state.province("a").happiness(), 1e-9); // +1 base, tasa 100% neutra
    }

    @Test
    void conTasaCeroNoHayIngresosPeroSubeLaFelicidad() {
        TurnEngine engine = engine(ECO);
        engine.submit(new Order.SetTaxRate("n1", 0));
        engine.endTurn();
        assertEquals(93.0, engine.state().nation("n1").gold(), 1e-6);       // 100 − 7
        assertEquals(80.0, engine.state().province("a").happiness(), 1e-9); // +1 +4
    }

    @Test
    void conTasaDobleSeRecaudaElDobleYCaeLaFelicidad() {
        TurnEngine engine = engine(ECO);
        engine.submit(new Order.SetTaxRate("n1", 200));
        engine.endTurn();
        assertEquals(643.0, engine.state().nation("n1").gold(), 1e-6);      // 100 + 550 − 7
        assertEquals(72.0, engine.state().province("a").happiness(), 1e-9); // +1 −4
    }

    @Test
    void lasProvinciasDescontentasNoPaganImpuestos() {
        TurnEngine engine = engine(ECO.replace(
                "{\"id\": \"a\", \"poblacion\": 1000000,",
                "{\"id\": \"a\", \"poblacion\": 1000000, \"felicidad\": 40,"));
        engine.endTurn();
        // Solo paga b (25): 100 + 25 − 7 = 118
        assertEquals(118.0, engine.state().nation("n1").gold(), 1e-6);
    }

    @Test
    void laGuerraErosionaLaFelicidad() {
        TurnEngine engine = engine(ECO.replace(
                "\"provincias\": {\"c\": 10}}",
                "\"provincias\": {\"c\": 10}, \"relaciones\": {\"n1\": \"GUERRA\"}}"));
        engine.endTurn();
        assertEquals(74.0, engine.state().province("a").happiness(), 1e-9); // +1 −2
    }

    // -------------------------------------------------------- temporada fiscal

    @Test
    void laTasaSoloCambiaEnTemporadaFiscal() {
        TurnEngine engine = engine(ECO);
        engine.endTurn(); // turno 2: ya no es temporada (turnos 1, 6, 11…)
        OrderException e = assertThrows(OrderException.class,
                () -> engine.submit(new Order.SetTaxRate("n1", 0)));
        assertTrue(e.getMessage().contains("turno 6"));
    }

    @Test
    void soloSeAdmitenLasTasasDocumentadas() {
        TurnEngine engine = engine(ECO);
        assertThrows(OrderException.class, () -> engine.submit(new Order.SetTaxRate("n1", 75)));
    }

    @Test
    void elEscenarioPuedeFijarLaTasaInicial() {
        GameState state = ScenarioLoader.fromJson(ECO.replace(
                "{\"id\": \"n1\", \"oro\": 100,", "{\"id\": \"n1\", \"oro\": 100, \"tasa\": 150,"));
        assertEquals(150, state.nation("n1").taxRate());
        assertThrows(ScenarioException.class, () -> ScenarioLoader.fromJson(ECO.replace(
                "{\"id\": \"n1\", \"oro\": 100,", "{\"id\": \"n1\", \"oro\": 100, \"tasa\": 75,")));
    }

    // ------------------------------------------------------- saqueo y decretos

    @Test
    void saquearConviertePoblacionEnOro() {
        TurnEngine engine = engine(ECO);
        engine.submit(new Order.Pillage("n1", "b"));
        engine.endTurn();
        GameState state = engine.state();
        // Saqueo: destruye 20000 hab. → +20 de oro; felicidad de b: 75−30 = 45 (+1) = 46.
        // Hacienda: b descontenta no paga → 100 + 20 + 250 − 7 = 363.
        assertEquals(363.0, state.nation("n1").gold(), 1e-6);
        assertEquals(81_200, state.province("b").population()); // 80000 · 1.015
        assertEquals(46.0, state.province("b").happiness(), 1e-9);
    }

    @Test
    void saquearSinPoblacionOAjenoEsIlegal() {
        TurnEngine engine = engine(ECO);
        assertThrows(OrderException.class, () -> engine.submit(new Order.Pillage("n1", "c")));
        engine.submit(new Order.Pillage("n1", "b"));
        assertThrows(OrderException.class, () -> engine.submit(new Order.Pillage("n1", "b")));
    }

    @Test
    void laFiestaSubeLaFelicidadYCuestaOro() {
        TurnEngine engine = engine(ECO);
        engine.submit(new Order.Decree("n1", "b", Order.DecreeType.FIESTA));
        assertEquals(75.0, engine.state().nation("n1").gold(), 1e-6); // 100 − 25 al ordenar
        engine.endTurn();
        assertEquals(96.0, engine.state().province("b").happiness(), 1e-9); // 75+20 (+1)
        assertEquals(343.0, engine.state().nation("n1").gold(), 1e-6);      // 75 + 275 − 7
    }

    @Test
    void repartirDineroSubeLaFelicidadModeradamente() {
        TurnEngine engine = engine(ECO);
        engine.submit(new Order.Decree("n1", "b", Order.DecreeType.REPARTIR));
        engine.endTurn();
        assertEquals(86.0, engine.state().province("b").happiness(), 1e-9); // 75+10 (+1)
        assertEquals(358.0, engine.state().nation("n1").gold(), 1e-6);      // 100−10+275−7
    }

    @Test
    void elFestivalDeFertilidadAumentaLaPoblacion() {
        TurnEngine engine = engine(ECO);
        engine.submit(new Order.Decree("n1", "b", Order.DecreeType.FESTIVAL));
        engine.endTurn();
        // 100000 · 1.2 = 120000, luego crecimiento · 1.015 = 121800.
        assertEquals(121_800, engine.state().province("b").population());
        // Hacienda con b ya al 12% del tope: 100 − 15 + (250 + 30) − 7 = 358.
        assertEquals(358.0, engine.state().nation("n1").gold(), 1e-6);
    }

    // ------------------------------------------------- revueltas (Monte Carlo)

    /** Revueltas convertidas en deterministas: probabilidad 1 sin guarnición, 0 con ella. */
    private TurnEngine revoltEngine(String json) {
        GameState state = ScenarioLoader.fromJson(json);
        state.rules().revoltRiskK = 999;
        state.rules().revoltMaxChance = 1.0;
        state.rules().revoltGarrisonSuppression = 0.0;
        return new TurnEngine(state);
    }

    private static final String REV = ECO
            .replace("{\"id\": \"a\", \"poblacion\": 1000000,",
                     "{\"id\": \"a\", \"poblacion\": 1000000, \"felicidad\": 10,")
            .replace("{\"id\": \"b\", \"poblacion\": 100000,",
                     "{\"id\": \"b\", \"poblacion\": 100000, \"felicidad\": 10,");

    @Test
    void lasProvinciasDescontentasSinGuarnicionSeRebelan() {
        TurnEngine engine = revoltEngine(REV);
        engine.endTurn();
        GameState state = engine.state();
        // b (sin guarnición) se independiza con round(101500·0.0002) = 20 milicianos.
        assertNull(state.province("b").ownerId());
        assertEquals(20, state.province("b").troops());
        assertEquals(60.0, state.province("b").happiness(), 1e-9);
        // a (100 soldados de guarnición) queda suprimida y no se rebela.
        assertEquals("n1", state.province("a").ownerId());
    }

    @Test
    void elReyHuyeDeUnaProvinciaRebelde() {
        TurnEngine engine = revoltEngine(REV.replace(
                "{\"id\": \"n1\", \"oro\": 100, \"ia\": false,",
                "{\"id\": \"n1\", \"oro\": 100, \"ia\": false, \"rey\": \"b\","));
        engine.endTurn();
        assertEquals("a", engine.state().nation("n1").kingProvinceId());
    }

    @Test
    void perderLaUltimaProvinciaPorRevueltaEliminaLaNacion() {
        TurnEngine engine = revoltEngine(ECO.replace(
                "{\"id\": \"c\", \"poblacion\": 50000}",
                "{\"id\": \"c\", \"poblacion\": 50000, \"felicidad\": 10}")
                .replace("\"provincias\": {\"c\": 10}", "\"provincias\": {\"c\": 0}"));
        TurnReport report = engine.endTurn();
        assertTrue(engine.state().nation("n2").isEliminated());
        assertEquals("n1", report.winnerId());
    }

    @Test
    void conLaMismaSemillaLaPartidaEsReproducible() {
        // Probabilidad de revuelta intermedia (~50%): el resultado es incierto,
        // pero con la misma semilla debe ser idéntico en las dos ejecuciones.
        String json = ECO.replace("{\"id\": \"b\", \"poblacion\": 100000,",
                "{\"id\": \"b\", \"poblacion\": 100000, \"felicidad\": 10,");
        TurnEngine first = new TurnEngine(ScenarioLoader.fromJson(json));
        TurnEngine second = new TurnEngine(ScenarioLoader.fromJson(json));
        first.endTurn();
        second.endTurn();
        assertEquals(first.state().province("b").ownerId(),
                second.state().province("b").ownerId());
        assertEquals(first.state().province("b").troops(),
                second.state().province("b").troops());
    }
}
