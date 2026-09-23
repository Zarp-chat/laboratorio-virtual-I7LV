package laboratoriovirtual.datos;

/**
 * Implementación de FuenteDeDatos para el Laboratorio 1.
 * Simula el hardware usando los generadores aleatorios.
 *
 * Los métodos son synchronized porque se llaman desde dos hilos:
 * el hilo de muestreo lee las entradas, y el hilo de la interfaz
 * escribe las salidas o cambia el tiempo de muestreo.
 */
public class FuenteAleatoria implements FuenteDeDatos {

    private final GeneradorAnalogico analogicas = new GeneradorAnalogico(NUM_ANALOGICAS);
    private final GeneradorDigital digitales = new GeneradorDigital(NUM_DIGITALES_ENTRADA, 0.1);

    // Estado simulado de las salidas: aquí no van a ningún lado,
    // en el Laboratorio 2 se enviarán al hardware.
    private final boolean[] salidas = new boolean[NUM_SALIDAS_DIGITALES];
    private int tiempoMuestreoMs;
    private boolean iniciada = false;

    @Override
    public synchronized void iniciar() {
        iniciada = true;
    }

    @Override
    public synchronized void detener() {
        iniciada = false;
    }

    @Override
    public synchronized double[] leerAnalogicas() throws FuenteDeDatosException {
        verificarIniciada();
        return analogicas.siguiente();
    }

    @Override
    public synchronized boolean[] leerDigitales() throws FuenteDeDatosException {
        verificarIniciada();
        return digitales.siguiente();
    }

    @Override
    public synchronized void escribirSalida(int canal, boolean encendida) throws FuenteDeDatosException {
        if (canal < 0 || canal >= NUM_SALIDAS_DIGITALES) {
            throw new IllegalArgumentException("Canal de salida inválido: " + canal);
        }
        verificarIniciada();
        salidas[canal] = encendida;
    }

    @Override
    public synchronized void fijarTiempoMuestreo(int milisegundos) {
        tiempoMuestreoMs = milisegundos;
    }

    /** Obliga a llamar iniciar() antes de usar la fuente, igual que exigirá el puerto serie. */
    private void verificarIniciada() throws FuenteDeDatosException {
        if (!iniciada) {
            throw new FuenteDeDatosException("La fuente de datos no ha sido iniciada");
        }
    }
}
