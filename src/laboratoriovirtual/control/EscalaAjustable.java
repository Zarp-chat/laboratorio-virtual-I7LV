package laboratoriovirtual.control;

/**
 * Una gráfica cuya escala vertical (el rango del eje Y) se puede elegir
 * (I7LV-18). ControlVisualizacion la conecta con el selector "Escala:" de su
 * pestaña; si la gráfica no la implementa, oculta ese selector.
 *
 * Solo la implementa GraficaAnalogica: en la gráfica digital cada carril va
 * siempre de 0 a 1.
 */
public interface EscalaAjustable {

    /** Escala actual. Al comenzar es EscalaVertical.CERO_A_5_V. */
    EscalaVertical getEscala();

    /**
     * Cambia la escala. Elegir la que ya tiene no hace nada. Se llama desde
     * el hilo de Swing.
     *
     * @param escala nueva escala, no null
     */
    void setEscala(EscalaVertical escala);
}
