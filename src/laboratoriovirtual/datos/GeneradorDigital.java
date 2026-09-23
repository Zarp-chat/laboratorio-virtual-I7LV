package laboratoriovirtual.datos;

import java.util.Random;

/**
 * Genera señales digitales simuladas (I7LV-12).
 *
 * En cada muestra, cada canal tiene una probabilidad pequeña de cambiar
 * de estado. Así se ven pulsos de duración variable, como una señal real,
 * en vez de un valor que cambia en cada muestra.
 *
 * Solo la usa FuenteAleatoria; el resto del programa no la conoce.
 */
public class GeneradorDigital {

    private final Random azar = new Random();
    private final boolean[] estados;
    private final double probabilidadCambio;

    /**
     * @param canales             cantidad de señales digitales
     * @param probabilidadCambio  probabilidad (0 a 1) de cambiar de estado en cada muestra
     */
    public GeneradorDigital(int canales, double probabilidadCambio) {
        this.estados = new boolean[canales];
        this.probabilidadCambio = probabilidadCambio;
        for (int i = 0; i < canales; i++) {
            estados[i] = azar.nextBoolean();
        }
    }

    /** Avanza un paso y devuelve una copia de los estados nuevos. */
    public boolean[] siguiente() {
        for (int i = 0; i < estados.length; i++) {
            if (azar.nextDouble() < probabilidadCambio) {
                estados[i] = !estados[i];
            }
        }
        return estados.clone();
    }
}
