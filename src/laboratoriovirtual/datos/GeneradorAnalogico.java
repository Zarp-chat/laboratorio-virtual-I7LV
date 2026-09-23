package laboratoriovirtual.datos;

import java.util.Random;

/**
 * Genera señales analógicas simuladas (I7LV-11).
 *
 * Cada canal hace una "caminata aleatoria": el valor nuevo es el anterior
 * más un pequeño salto al azar. Así la gráfica parece un sensor real
 * y no ruido puro que salta de un extremo al otro.
 *
 * Solo la usa FuenteAleatoria; el resto del programa no la conoce.
 */
public class GeneradorAnalogico {

    /** Rango típico de un conversor análogo-digital de microcontrolador. */
    public static final double VOLTAJE_MIN = 0.0;
    public static final double VOLTAJE_MAX = 5.0;

    private final Random azar = new Random();
    private final double[] valores;
    private final double[] tamanoSalto;

    public GeneradorAnalogico(int canales) {
        valores = new double[canales];
        tamanoSalto = new double[canales];
        for (int i = 0; i < canales; i++) {
            // Cada canal arranca en un punto distinto del rango
            valores[i] = VOLTAJE_MIN + azar.nextDouble() * (VOLTAJE_MAX - VOLTAJE_MIN);
            // Cada canal varía con distinta intensidad para que se distingan al graficar
            tamanoSalto[i] = 0.03 + 0.03 * i;
        }
    }

    /** Avanza un paso y devuelve una copia de los valores nuevos. */
    public double[] siguiente() {
        for (int i = 0; i < valores.length; i++) {
            valores[i] += azar.nextGaussian() * tamanoSalto[i];
            valores[i] = Math.max(VOLTAJE_MIN, Math.min(VOLTAJE_MAX, valores[i]));
        }
        return valores.clone();
    }
}
