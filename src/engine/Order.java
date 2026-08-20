package engine;

/**
 * Una orden emitida por una nación durante la fase de planificación de un
 * turno WEGO. Todas las órdenes de todos los jugadores se recogen primero y
 * se resuelven a la vez al cerrar el turno (ver {@link TurnEngine#endTurn}).
 */
public sealed interface Order {

    String nationId();

    /**
     * Mover un ejército entre provincias. El destino debe ser adyacente por
     * tierra o alcanzable cruzando una única zona marítima compartida (las
     * tropas embarcan automáticamente, como en el juego original).
     *
     * @param withKing si el rey acompaña al ejército (+30% en combate, pero
     *                 muere con él si el ataque fracasa)
     */
    record Move(String nationId, String from, String to, int troops, boolean withKing)
            implements Order {
    }

    /** Reclutar soldados en una provincia propia: consume oro y población. */
    record Recruit(String nationId, String provinceId, int soldiers) implements Order {
    }

    /** Fortificar una provincia propia: +50% para el defensor a partir de este turno. */
    record Fortify(String nationId, String provinceId) implements Order {
    }

    /**
     * Declarar la guerra a otra nación. Surte efecto inmediato (como en el
     * juego original se puede declarar y atacar en el mismo turno).
     */
    record DeclareWar(String nationId, String targetNationId) implements Order {
    }

    /**
     * Saquear una provincia propia: destruye parte de la población a cambio de
     * oro inmediato y hunde la felicidad (la táctica "rampage" del juego real).
     */
    record Pillage(String nationId, String provinceId) implements Order {
    }

    /** Tipos de decreto provincial (costes y efectos en {@link model.Rules}). */
    enum DecreeType {
        /** Repartir dinero: +felicidad moderada. */
        REPARTIR,
        /** Fiesta de inauguración: +felicidad alta. */
        FIESTA,
        /** Festival de fertilidad: +20% de población de una sola vez. */
        FESTIVAL
    }

    /** Emitir un decreto sobre una provincia propia. */
    record Decree(String nationId, String provinceId, DecreeType type) implements Order {
    }

    /**
     * Cambiar la tasa impositiva θ_i de la nación. El modelo formal la trata
     * como variable de decisión continua en [0, θ_max] (refinamiento O1 de
     * §1.3), disponible en cualquier turno. Surte efecto inmediato.
     */
    record SetTaxRate(String nationId, int rate) implements Order {
    }
}
