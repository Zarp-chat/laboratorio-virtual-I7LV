package laboratoriovirtual.control;

/**
 * Una gráfica en vivo cuya ventana visible (la duración que abarca el eje X)
 * se puede cambiar (I7LV-18). ControlVisualizacion la conecta con el
 * selector "Ventana:" de su pestaña.
 *
 * La implementan las dos gráficas: GraficaSenal (y con ella
 * GraficaAnalogica) y GraficaDigital. Existe por la misma razón que
 * CanalSeleccionable: la gráfica digital no hereda de GraficaSenal.
 */
public interface VentanaAjustable {

    /** Duración de la ventana visible, en segundos. Al comenzar es de 30 s. */
    double getVentanaVisible();

    /**
     * Cambia la duración de la ventana visible. Al agrandarla, la gráfica se
     * reconstruye desde el historial completo, así que reaparecen los puntos
     * que ya habían salido; al achicarla, se recorta. El historial no cambia.
     * Pedir la duración que ya tiene no hace nada. Se llama desde el hilo de
     * Swing.
     *
     * @param segundos nueva duración, mayor que cero
     * @throws IllegalArgumentException si no es un número mayor que cero; en
     *                                  ese caso la gráfica sigue igual
     */
    void setVentanaVisible(double segundos);
}
