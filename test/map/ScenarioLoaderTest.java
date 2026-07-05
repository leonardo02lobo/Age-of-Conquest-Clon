package map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import model.DiplomaticState;
import model.GameState;
import model.Nation;
import model.Province;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ScenarioLoaderTest {

    static GameState europa;

    @BeforeAll
    static void loadScenario() throws Exception {
        europa = ScenarioLoader.load(Path.of("scenarios/europa_antigua.json"));
    }

    // ------------------------------------------------ escenario Europa Antigua

    @Test
    void cargaElEscenarioCompleto() {
        assertEquals("Europa Antigua", europa.scenarioName());
        assertEquals(23, europa.provinces().size());
        assertEquals(4, europa.nations().size());
        assertEquals(1, europa.turn());
    }

    @Test
    void laAdyacenciaEsSimetrica() {
        for (Province p : europa.provinces()) {
            for (String adjId : p.adjacent()) {
                assertTrue(europa.province(adjId).adjacent().contains(p.id()),
                        "'" + adjId + "' no lista de vuelta a '" + p.id() + "'");
            }
        }
    }

    @Test
    void todoElMapaEsAlcanzable() {
        // El cargador valida conectividad; que cargue sin excepción ya lo prueba.
        // Verificación puntual: desde Roma se llega por mar a Cartago.
        assertTrue(europa.areAdjacent("roma", "mar_tirreno"));
        assertTrue(europa.areAdjacent("mar_tirreno", "cartago"));
    }

    @Test
    void asignaDuenosYTropas() {
        Province roma = europa.province("roma");
        assertEquals("imperio_romano", roma.ownerId());
        assertEquals(100, roma.troops());
        assertEquals(200, europa.totalTroops("imperio_romano"));
        assertEquals(4, europa.provincesOf("imperio_romano").size());
    }

    @Test
    void lasProvinciasNeutralesConservanSuGuarnicion() {
        Province tarraconense = europa.province("tarraconense");
        assertTrue(tarraconense.isNeutral());
        assertEquals(30, tarraconense.troops());
    }

    @Test
    void losReyesEmpiezanEnProvinciaPropia() {
        for (Nation nation : europa.nations()) {
            String kingProvince = nation.kingProvinceId();
            assertEquals(nation.id(), europa.province(kingProvince).ownerId(),
                    "El rey de '" + nation.id() + "' no está en provincia propia");
        }
    }

    @Test
    void laGuerraDeclaradaEsSimetrica() {
        assertEquals(DiplomaticState.GUERRA, europa.relation("imperio_romano", "cartago"));
        assertEquals(DiplomaticState.GUERRA, europa.relation("cartago", "imperio_romano"));
    }

    @Test
    void laRelacionPorDefectoEsNeutral() {
        assertEquals(DiplomaticState.NEUTRAL, europa.relation("galia", "grecia"));
    }

    @Test
    void lasZonasMaritimasNoTienenDuenoNiPoblacion() {
        for (Province p : europa.provinces()) {
            if (p.isWater()) {
                assertNull(p.ownerId(), p.id());
                assertEquals(0, p.population(), p.id());
                assertEquals(0, p.troops(), p.id());
            }
        }
    }

    @Test
    void losPuntosDeAccionInicialesSiguenLaFormula() {
        // Imperio Romano: 4 provincias -> 3 + 0.5·4 = 5 AP
        assertEquals(5.0, europa.nation("imperio_romano").actionPoints(), 1e-9);
        // Grecia: 3 provincias -> 4.5 AP
        assertEquals(4.5, europa.nation("grecia").actionPoints(), 1e-9);
    }

    @Test
    void laFelicidadInicialEsLaPorDefecto() {
        assertEquals(europa.rules().initialHappiness, europa.province("roma").happiness());
    }

    // --------------------------------------------------- validación de errores

    @Nested
    class Validacion {

        /** Escenario mínimo válido sobre el que cada prueba introduce un defecto. */
        private String escenario(String provincias, String naciones) {
            return "{\"nombre\": \"Test\", \"provincias\": [" + provincias
                    + "], \"naciones\": [" + naciones + "]}";
        }

        private static final String DOS_PROVINCIAS =
                "{\"id\": \"a\", \"poblacion\": 1000, \"adyacentes\": [\"b\"]},"
                + "{\"id\": \"b\", \"poblacion\": 1000}";

        private static final String UNA_NACION =
                "{\"id\": \"n1\", \"provincias\": {\"a\": 10}}";

        @Test
        void rechazaAdyacenteDesconocido() {
            String json = escenario(
                    "{\"id\": \"a\", \"adyacentes\": [\"noexiste\"]}", UNA_NACION);
            assertThrows(ScenarioException.class, () -> ScenarioLoader.fromJson(json));
        }

        @Test
        void rechazaProvinciaAislada() {
            String json = escenario(
                    "{\"id\": \"a\", \"adyacentes\": [\"b\"]}, {\"id\": \"b\"}, {\"id\": \"suelta\"}",
                    UNA_NACION);
            assertThrows(ScenarioException.class, () -> ScenarioLoader.fromJson(json));
        }

        @Test
        void rechazaMapaNoConexo() {
            String json = escenario(
                    "{\"id\": \"a\", \"adyacentes\": [\"b\"]}, {\"id\": \"b\"},"
                    + "{\"id\": \"c\", \"adyacentes\": [\"d\"]}, {\"id\": \"d\"}",
                    UNA_NACION);
            assertThrows(ScenarioException.class, () -> ScenarioLoader.fromJson(json));
        }

        @Test
        void rechazaProvinciaDuplicada() {
            String json = escenario(
                    "{\"id\": \"a\", \"adyacentes\": [\"b\"]}, {\"id\": \"b\"}, {\"id\": \"a\"}",
                    UNA_NACION);
            assertThrows(ScenarioException.class, () -> ScenarioLoader.fromJson(json));
        }

        @Test
        void rechazaZonaMaritimaConDueno() {
            String json = escenario(
                    "{\"id\": \"a\", \"agua\": true, \"adyacentes\": [\"b\"]}, {\"id\": \"b\"}",
                    "{\"id\": \"n1\", \"provincias\": {\"a\": 10}}");
            assertThrows(ScenarioException.class, () -> ScenarioLoader.fromJson(json));
        }

        @Test
        void rechazaZonaMaritimaConPoblacion() {
            String json = escenario(
                    "{\"id\": \"a\", \"agua\": true, \"poblacion\": 5, \"adyacentes\": [\"b\"]}, {\"id\": \"b\"}",
                    "{\"id\": \"n1\", \"provincias\": {\"b\": 10}}");
            assertThrows(ScenarioException.class, () -> ScenarioLoader.fromJson(json));
        }

        @Test
        void rechazaReyEnProvinciaAjena() {
            String json = escenario(DOS_PROVINCIAS,
                    "{\"id\": \"n1\", \"rey\": \"b\", \"provincias\": {\"a\": 10}}");
            assertThrows(ScenarioException.class, () -> ScenarioLoader.fromJson(json));
        }

        @Test
        void rechazaProvinciaConDosDuenos() {
            String json = escenario(DOS_PROVINCIAS,
                    "{\"id\": \"n1\", \"provincias\": {\"a\": 10}},"
                    + "{\"id\": \"n2\", \"provincias\": {\"a\": 5}}");
            assertThrows(ScenarioException.class, () -> ScenarioLoader.fromJson(json));
        }

        @Test
        void rechazaNacionSinProvincias() {
            String json = escenario(DOS_PROVINCIAS, "{\"id\": \"n1\", \"provincias\": {}}");
            assertThrows(ScenarioException.class, () -> ScenarioLoader.fromJson(json));
        }

        @Test
        void rechazaRelacionContradictoria() {
            String json = escenario(DOS_PROVINCIAS,
                    "{\"id\": \"n1\", \"provincias\": {\"a\": 10}, \"relaciones\": {\"n2\": \"GUERRA\"}},"
                    + "{\"id\": \"n2\", \"provincias\": {\"b\": 10}, \"relaciones\": {\"n1\": \"ALIANZA\"}}");
            assertThrows(ScenarioException.class, () -> ScenarioLoader.fromJson(json));
        }

        @Test
        void rechazaRelacionConNacionDesconocida() {
            String json = escenario(DOS_PROVINCIAS,
                    "{\"id\": \"n1\", \"provincias\": {\"a\": 10}, \"relaciones\": {\"fantasma\": \"GUERRA\"}}");
            assertThrows(ScenarioException.class, () -> ScenarioLoader.fromJson(json));
        }

        @Test
        void rechazaEstadoDiplomaticoInvalido() {
            String json = escenario(DOS_PROVINCIAS,
                    "{\"id\": \"n1\", \"provincias\": {\"a\": 10}, \"relaciones\": {\"n2\": \"AMISTAD\"}},"
                    + "{\"id\": \"n2\", \"provincias\": {\"b\": 10}}");
            assertThrows(ScenarioException.class, () -> ScenarioLoader.fromJson(json));
        }

        @Test
        void aceptaRelacionDeclaradaPorAmbosLadosSinConflicto() {
            String json = escenario(DOS_PROVINCIAS,
                    "{\"id\": \"n1\", \"provincias\": {\"a\": 10}, \"relaciones\": {\"n2\": \"ALIANZA\"}},"
                    + "{\"id\": \"n2\", \"provincias\": {\"b\": 10}, \"relaciones\": {\"n1\": \"ALIANZA\"}}");
            GameState state = ScenarioLoader.fromJson(json);
            assertEquals(DiplomaticState.ALIANZA, state.relation("n1", "n2"));
        }
    }
}
