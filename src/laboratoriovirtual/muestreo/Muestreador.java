package laboratoriovirtual.muestreo;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import laboratoriovirtual.datos.FuenteDeDatos;
import laboratoriovirtual.datos.FuenteDeDatosException;

/**
 * Controla el tiempo de muestreo y lee la fuente en un hilo aparte (I7LV-13).
 *
 * Cada periodo lee todas las entradas, arma una Muestra y la reparte
 * a los oyentes registrados. No sabe nada de Swing ni de archivos.
 */
public class Muestreador {

    /** Límites del tiempo de muestreo, en milisegundos. */
    public static final int PERIODO_MIN_MS = 10;
    public static final int PERIODO_MAX_MS = 10000;

    private final FuenteDeDatos fuente;

    // Lista segura para agregar oyentes desde un hilo mientras otro la recorre
    private final List<OyenteMuestras> oyentes = new CopyOnWriteArrayList<>();

    // volatile: se escriben desde el hilo de la interfaz y se leen desde el de muestreo
    private volatile int periodoMs;
    private volatile boolean corriendo = false;

    private Thread hilo;
    private long inicioNanos;

    public Muestreador(FuenteDeDatos fuente, int periodoInicialMs) {
        validarPeriodo(periodoInicialMs);
        this.fuente = fuente;
        this.periodoMs = periodoInicialMs;
    }

    public void agregarOyente(OyenteMuestras oyente) {
        oyentes.add(oyente);
    }

    public void quitarOyente(OyenteMuestras oyente) {
        oyentes.remove(oyente);
    }

    /** Tiempo de muestreo actual, en milisegundos (R8). */
    public int getPeriodoMs() {
        return periodoMs;
    }

    /**
     * Cambia el tiempo de muestreo (R9). Se aplica desde la siguiente muestra.
     * También se le informa a la fuente, porque en el Laboratorio 2
     * el hardware necesita saberlo.
     */
    public void setPeriodoMs(int nuevoPeriodoMs) throws FuenteDeDatosException {
        validarPeriodo(nuevoPeriodoMs);
        fuente.fijarTiempoMuestreo(nuevoPeriodoMs);
        periodoMs = nuevoPeriodoMs;
    }

    public boolean estaCorriendo() {
        return corriendo;
    }

    /** Inicia la fuente y arranca el hilo de muestreo. El tiempo vuelve a cero. */
    public synchronized void iniciar() throws FuenteDeDatosException {
        if (corriendo) {
            return;
        }
        fuente.iniciar();
        fuente.fijarTiempoMuestreo(periodoMs);
        corriendo = true;
        inicioNanos = System.nanoTime();
        hilo = new Thread(this::bucleDeMuestreo, "Muestreador");
        hilo.setDaemon(true); // no impide que el programa se cierre
        hilo.start();
    }

    /** Detiene el hilo de muestreo y libera la fuente. */
    public synchronized void detener() {
        corriendo = false;
        if (hilo != null) {
            hilo.interrupt(); // lo despierta si está dormido
            try {
                hilo.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            hilo = null;
        }
        fuente.detener();
    }

    private void bucleDeMuestreo() {
        long proximaMuestra = System.nanoTime();

        while (corriendo) {
            try {
                double tiempo = (System.nanoTime() - inicioNanos) / 1e9;
                Muestra muestra = new Muestra(tiempo, fuente.leerAnalogicas(), fuente.leerDigitales());

                for (OyenteMuestras oyente : oyentes) {
                    oyente.muestraRecibida(muestra);
                }

                // Se calcula la hora de la siguiente muestra a partir de la anterior,
                // no de "ahora". Así el tiempo que tarda el proceso no se va sumando
                // y el periodo real coincide con el que se muestra en pantalla.
                proximaMuestra += periodoMs * 1_000_000L;
                long esperaNanos = proximaMuestra - System.nanoTime();
                if (esperaNanos > 0) {
                    Thread.sleep(esperaNanos / 1_000_000, (int) (esperaNanos % 1_000_000));
                } else {
                    // Vamos atrasados: se reinicia la referencia en vez de disparar
                    // varias muestras seguidas para "alcanzar".
                    proximaMuestra = System.nanoTime();
                }

            } catch (InterruptedException e) {
                // detener() interrumpió el sueño: se sale del ciclo
                break;
            } catch (FuenteDeDatosException e) {
                corriendo = false;
                for (OyenteMuestras oyente : oyentes) {
                    oyente.errorEnFuente(e);
                }
            }
        }
    }

    private static void validarPeriodo(int ms) {
        if (ms < PERIODO_MIN_MS || ms > PERIODO_MAX_MS) {
            throw new IllegalArgumentException(
                    "El tiempo de muestreo debe estar entre " + PERIODO_MIN_MS
                    + " y " + PERIODO_MAX_MS + " ms");
        }
    }
}
