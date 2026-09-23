package laboratoriovirtual.muestreo;

/**
 * Una lectura completa de todas las entradas en un instante.
 *
 * Es inmutable (no cambia después de creada), así que se puede pasar
 * entre el hilo de muestreo y el de la interfaz sin riesgo.
 */
public final class Muestra {

    private final double tiempoSegundos;
    private final double[] analogicas;
    private final boolean[] digitales;

    public Muestra(double tiempoSegundos, double[] analogicas, boolean[] digitales) {
        this.tiempoSegundos = tiempoSegundos;
        // Se copian para que nadie pueda modificar la muestra desde afuera
        this.analogicas = analogicas.clone();
        this.digitales = digitales.clone();
    }

    /** Segundos transcurridos desde que empezó el muestreo. */
    public double getTiempo() {
        return tiempoSegundos;
    }

    /** Valor de un canal analógico, en voltios. */
    public double getAnalogica(int canal) {
        return analogicas[canal];
    }

    /** Estado de un canal digital. */
    public boolean getDigital(int canal) {
        return digitales[canal];
    }
}
