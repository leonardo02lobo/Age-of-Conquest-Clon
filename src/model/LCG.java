package model;

/**
 * Generador congruencial lineal (LCG) de 48 bits (ec. 3.26 del modelo formal).
 *
 *   X_{k+1} = (a * X_k + c) mod m
 *   R_k     = X_k / m
 *
 * Parámetros: a = 25214903917, c = 11, m = 2^48.
 * Semilla por defecto: s_0 = 20260805.
 * Periodo completo: m = 2^48 ≈ 2.81 × 10^14 (satisface Hull–Dobell).
 */
public final class LCG {

    private static final long A = 25_214_903_917L;
    private static final long C = 11L;
    private static final long M = 1L << 48;

    private long state;

    /** Crea un LCG con la semilla especificada. */
    public LCG(long seed) {
        this.state = seed % M;
        if (this.state < 0) {
            this.state += M;
        }
    }

    /** Genera el siguiente valor uniforme R ∈ [0, 1). */
    public double nextDouble() {
        state = (A * state + C) % M;
        if (state < 0) {
            state += M;
        }
        return (double) state / M;
    }

    /**
     * Genera una variable aleatoria U ~ Triangular(a, c, b)
     * usando la transformada inversa (ec. 3.25):
     *
     *   U = 0.8 + sqrt(0.08 * R)         si R < 0.5
     *   U = 1.2 - sqrt(0.08 * (1 - R))   si R >= 0.5
     */
    public double nextTriangular() {
        double r = nextDouble();
        if (r < 0.5) {
            return 0.8 + Math.sqrt(0.08 * r);
        } else {
            return 1.2 - Math.sqrt(0.08 * (1.0 - r));
        }
    }

    /** Resetea la semilla del generador. */
    public void setSeed(long seed) {
        this.state = seed % M;
        if (this.state < 0) {
            this.state += M;
        }
    }

    /** Semilla actual (para depuración / reproducibilidad). */
    public long getSeed() {
        return state;
    }
}
