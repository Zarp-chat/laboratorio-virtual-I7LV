package laboratoriovirtual.datos;

/**
 * Contrato entre la aplicación y el origen de los datos.
 *
 * Laboratorio 1: la implementa FuenteAleatoria (números aleatorios).
 * Laboratorio 2: la implementará FuenteSerial (puerto serie con jSerialComm).
 *
 * Ninguna otra clase del programa debe conocer qué implementación se usa.
 */
public interface FuenteDeDatos {

    /** Cantidad de señales analógicas de entrada (R1). */
    int NUM_ANALOGICAS = 8;

    /** Cantidad de señales digitales de entrada (R4). */
    int NUM_DIGITALES_ENTRADA = 4;

    /** Cantidad de salidas digitales (R7). */
    int NUM_SALIDAS_DIGITALES = 4;

    /**
     * Prepara la fuente para entregar datos.
     * En el Laboratorio 2 aquí se abre el puerto serie.
     */
    void iniciar() throws FuenteDeDatosException;

    /**
     * Libera los recursos de la fuente.
     * En el Laboratorio 2 aquí se cierra el puerto serie.
     */
    void detener();

    /** Devuelve los valores actuales de las 8 entradas analógicas, en voltios. */
    double[] leerAnalogicas() throws FuenteDeDatosException;

    /** Devuelve el estado actual de las 4 entradas digitales. */
    boolean[] leerDigitales() throws FuenteDeDatosException;

    /**
     * Enciende o apaga una salida digital.
     *
     * @param canal      índice de la salida, de 0 a NUM_SALIDAS_DIGITALES - 1
     * @param encendida  true para activar, false para desactivar
     */
    void escribirSalida(int canal, boolean encendida) throws FuenteDeDatosException;

    /**
     * Informa a la fuente el tiempo de muestreo.
     * En el Laboratorio 2 se le envía al hardware.
     *
     * @param milisegundos periodo de muestreo en milisegundos
     */
    void fijarTiempoMuestreo(int milisegundos) throws FuenteDeDatosException;
}
