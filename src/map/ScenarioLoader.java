package map;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import model.DiplomaticState;
import model.GameState;
import model.Nation;
import model.Province;
import model.Rules;
import model.TerrainType;

/**
 * Carga y valida escenarios en formato JSON.
 *
 * Estructura extendida con soporte para terreno y descontento:
 *   {"id": "roma", "nombre": "Roma", "poblacion": 800000,
 *    "terreno": "LLANURA", "descontento": 20,
 *    "adyacentes": ["etruria"], "tropas": 100}
 */
public final class ScenarioLoader {

    private ScenarioLoader() {
    }

    public static GameState load(Path path) throws IOException {
        return load(path, new Rules());
    }

    public static GameState load(Path path, Rules rules) throws IOException {
        return fromJson(Files.readString(path), rules);
    }

    public static GameState fromJson(String json) {
        return fromJson(json, new Rules());
    }

    public static GameState fromJson(String json, Rules rules) {
        JsonObject root;
        try {
            root = JsonParser.parseString(json).getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw new ScenarioException("El escenario no es un objeto JSON válido: " + e.getMessage(), e);
        }

        String name = requireString(root, "nombre", "el escenario");
        Map<String, Province> provinces = parseProvinces(root, rules);
        Map<String, Nation> nations = parseNations(root, provinces, rules);

        requireConnectedMap(provinces);

        GameState state = new GameState(name, rules, provinces, nations);
        applyRelations(root.getAsJsonArray("naciones"), state);
        return state;
    }

    // ------------------------------------------------------------ provincias

    private static Map<String, Province> parseProvinces(JsonObject root, Rules rules) {
        JsonArray array = requireArray(root, "provincias");
        if (array.isEmpty()) {
            throw new ScenarioException("El escenario no tiene provincias");
        }

        Map<String, Province> provinces = new LinkedHashMap<>();
        for (JsonElement element : array) {
            JsonObject obj = asObject(element, "provincias");
            String id = requireString(obj, "id", "una provincia");
            if (provinces.containsKey(id)) {
                throw new ScenarioException("Provincia duplicada: '" + id + "'");
            }

            boolean water = obj.has("agua") && obj.get("agua").getAsBoolean();
            Province p = new Province(id, optString(obj, "nombre"), water);

            long population = obj.has("poblacion") ? obj.get("poblacion").getAsLong() : 0;
            if (water && population > 0) {
                throw new ScenarioException("La zona marítima '" + id + "' no puede tener población");
            }
            // L_max es el techo poblacional del modelo formal (§2.4.8). Recortar en
            // silencio un escenario fuera de escala igualaría todas las provincias
            // al mismo valor y destruiría la heterogeneidad económica del mapa; se
            // rechaza con un error descriptivo, como el resto de validaciones.
            if (population > rules.lMax) {
                throw new ScenarioException("La provincia '" + id + "' declara "
                        + population + " habitantes y excede L_max = " + rules.lMax
                        + "; reescale el escenario al rango del modelo formal");
            }
            p.setPopulation(population);

            // Terreno (default: LLANURA)
            if (!water && obj.has("terreno")) {
                try {
                    p.setTerrain(TerrainType.valueOf(obj.get("terreno").getAsString().toUpperCase()));
                } catch (IllegalArgumentException e) {
                    throw new ScenarioException("Terreno inválido '" + obj.get("terreno").getAsString()
                            + "' en '" + id + "' (use LLANURA, BOSQUE, MONTANA, COSTA)");
                }
            }

            // Descontento inicial (default: D_0 = 20)
            if (!water) {
                double discontent = obj.has("descontento")
                        ? obj.get("descontento").getAsDouble()
                        : 20.0;
                p.setDiscontent(discontent);
            }

            int troops = obj.has("tropas") ? obj.get("tropas").getAsInt() : 0;
            if (water && troops > 0) {
                throw new ScenarioException("La zona marítima '" + id + "' no puede tener guarnición");
            }
            p.setTroops(troops);

            if (obj.has("poligono")) {
                JsonArray coords = obj.getAsJsonArray("poligono");
                int[] polygon = new int[coords.size()];
                for (int i = 0; i < coords.size(); i++) {
                    polygon[i] = coords.get(i).getAsInt();
                }
                try {
                    p.setPolygon(polygon);
                } catch (IllegalArgumentException e) {
                    throw new ScenarioException(e.getMessage(), e);
                }
            }

            provinces.put(id, p);
        }

        // Adyacencias en segunda pasada
        for (JsonElement element : array) {
            JsonObject obj = element.getAsJsonObject();
            String id = obj.get("id").getAsString();
            if (!obj.has("adyacentes")) continue;
            for (JsonElement adj : obj.getAsJsonArray("adyacentes")) {
                String adjId = adj.getAsString();
                if (adjId.equals(id)) {
                    throw new ScenarioException("La provincia '" + id + "' se declara adyacente a sí misma");
                }
                Province other = provinces.get(adjId);
                if (other == null) {
                    throw new ScenarioException(
                            "Provincia desconocida '" + adjId + "' en los adyacentes de '" + id + "'");
                }
                provinces.get(id).addAdjacent(adjId);
                other.addAdjacent(id);
            }
        }

        for (Province p : provinces.values()) {
            if (p.adjacent().isEmpty()) {
                throw new ScenarioException("La provincia '" + p.id() + "' está aislada");
            }
        }
        return provinces;
    }

    private static void requireConnectedMap(Map<String, Province> provinces) {
        Set<String> visited = new HashSet<>();
        Deque<String> pending = new ArrayDeque<>();
        String start = provinces.keySet().iterator().next();
        pending.push(start);
        visited.add(start);
        while (!pending.isEmpty()) {
            for (String adj : provinces.get(pending.pop()).adjacent()) {
                if (visited.add(adj)) {
                    pending.push(adj);
                }
            }
        }
        if (visited.size() != provinces.size()) {
            Set<String> unreachable = new HashSet<>(provinces.keySet());
            unreachable.removeAll(visited);
            throw new ScenarioException("El mapa no es conexo; provincias inalcanzables: " + unreachable);
        }
    }

    // -------------------------------------------------------------- naciones

    private static Map<String, Nation> parseNations(JsonObject root,
                                                     Map<String, Province> provinces, Rules rules) {
        JsonArray array = requireArray(root, "naciones");
        if (array.isEmpty()) {
            throw new ScenarioException("El escenario no tiene naciones");
        }

        Map<String, Nation> nations = new LinkedHashMap<>();
        for (JsonElement element : array) {
            JsonObject obj = asObject(element, "naciones");
            String id = requireString(obj, "id", "una nación");
            if (nations.containsKey(id)) {
                throw new ScenarioException("Nación duplicada: '" + id + "'");
            }

            boolean ai = !obj.has("ia") || obj.get("ia").getAsBoolean();
            Nation nation = new Nation(id, optString(obj, "nombre"), ai);
            nation.setGold(obj.has("oro") ? obj.get("oro").getAsDouble() : 0);

            if (obj.has("tasa")) {
                int rate = obj.get("tasa").getAsInt();
                if (rate < 0 || rate > rules.thetaMax) {
                    throw new ScenarioException("Tasa impositiva inválida para '" + id + "': " + rate);
                }
                nation.setTaxRate(rate);
            }

            if (!obj.has("provincias") || obj.getAsJsonObject("provincias").isEmpty()) {
                throw new ScenarioException("La nación '" + id + "' no tiene provincias iniciales");
            }
            JsonObject owned = obj.getAsJsonObject("provincias");
            for (String provinceId : owned.keySet()) {
                Province p = provinces.get(provinceId);
                if (p == null) {
                    throw new ScenarioException(
                            "La nación '" + id + "' referencia provincia desconocida: '" + provinceId + "'");
                }
                if (p.isWater()) {
                    throw new ScenarioException(
                            "La nación '" + id + "' no puede poseer zona marítima '" + provinceId + "'");
                }
                if (p.ownerId() != null) {
                    throw new ScenarioException("La provincia '" + provinceId
                            + "' tiene dos dueños: '" + p.ownerId() + "' y '" + id + "'");
                }
                p.setOwnerId(id);
                p.setTroops(owned.get(provinceId).getAsInt());
            }

            if (obj.has("rey")) {
                String kingProvince = obj.get("rey").getAsString();
                Province p = provinces.get(kingProvince);
                if (p == null || !id.equals(p.ownerId())) {
                    throw new ScenarioException("El rey de '" + id
                            + "' debe empezar en una provincia propia; '" + kingProvince + "' no lo es");
                }
                nation.setKingProvinceId(kingProvince);
            }

            nations.put(id, nation);
        }
        return nations;
    }

    private static void applyRelations(JsonArray array, GameState state) {
        Map<String, DiplomaticState> declared = new LinkedHashMap<>();
        for (JsonElement element : array) {
            JsonObject obj = element.getAsJsonObject();
            String id = obj.get("id").getAsString();
            if (!obj.has("relaciones")) continue;
            JsonObject relations = obj.getAsJsonObject("relaciones");
            for (String otherId : relations.keySet()) {
                if (otherId.equals(id)) {
                    throw new ScenarioException("Relación consigo misma");
                }
                if (!state.hasNation(otherId)) {
                    throw new ScenarioException("Nación desconocida '" + otherId + "' en relaciones de '" + id + "'");
                }
                DiplomaticState relation = parseState(relations.get(otherId).getAsString(), id, otherId);
                String key = id.compareTo(otherId) < 0 ? id + "|" + otherId : otherId + "|" + id;
                DiplomaticState previous = declared.putIfAbsent(key, relation);
                if (previous != null && previous != relation) {
                    throw new ScenarioException("Relación contradictoria entre '" + id + "' y '"
                            + otherId + "': " + previous + " y " + relation);
                }
            }
        }
        for (Map.Entry<String, DiplomaticState> entry : declared.entrySet()) {
            String[] pair = entry.getKey().split("\\|");
            state.setRelation(pair[0], pair[1], entry.getValue());
        }
    }

    private static DiplomaticState parseState(String value, String nationA, String nationB) {
        try {
            return DiplomaticState.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ScenarioException("Relación inválida '" + value + "' entre '"
                    + nationA + "' y '" + nationB + "' (use GUERRA, NEUTRAL o ALIANZA)");
        }
    }

    // ------------------------------------------------------------- utilidades

    private static String requireString(JsonObject obj, String field, String context) {
        if (!obj.has(field) || obj.get(field).getAsString().isBlank()) {
            throw new ScenarioException("Falta el campo '" + field + "' en " + context);
        }
        return obj.get(field).getAsString();
    }

    private static String optString(JsonObject obj, String field) {
        return obj.has(field) ? obj.get(field).getAsString() : null;
    }

    private static JsonArray requireArray(JsonObject root, String field) {
        if (!root.has(field) || !root.get(field).isJsonArray()) {
            throw new ScenarioException("El escenario necesita el arreglo '" + field + "'");
        }
        return root.getAsJsonArray(field);
    }

    private static JsonObject asObject(JsonElement element, String arrayName) {
        if (!element.isJsonObject()) {
            throw new ScenarioException("Cada elemento de '" + arrayName + "' debe ser un objeto JSON");
        }
        return element.getAsJsonObject();
    }
}
