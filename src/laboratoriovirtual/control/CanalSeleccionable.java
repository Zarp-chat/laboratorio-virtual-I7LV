package laboratoriovirtual.control;

/**
 * Una gráfica en la que se elige uno de los canales de su pestaña.
 * ControlSeleccion la conecta con el selector de canal de esa pestaña: la
 * posición del canal en el selector es su número.
 *
 * Qué significa elegir un canal depende de la gráfica:
 * - GraficaAnalogica (I7LV-16): pasa a graficar ese canal y descarta lo del
 *   anterior.
 * - GraficaDigital (I7LV-20): grafica siempre los cuatro canales; elegir uno
 *   solo cambia cuál se resalta y el título.
 *
 * Existe para que ControlSeleccion sirva sin cambios para las dos pestañas,
 * aunque la gráfica digital no herede de GraficaSenal.
 */
public interface CanalSeleccionable {

    /** Canal elegido: su posición en el selector de la pestaña. */
    int getCanal();

    /**
     * Elige otro canal. Elegir el que ya está elegido no hace nada. Se llama
     * desde el hilo de Swing.
     *
     * @param nuevoCanal posición del canal en el selector de la pestaña
     * @throws IllegalArgumentException si el canal no existe; en ese caso la
     *                                  gráfica sigue igual
     */
    void cambiarCanal(int nuevoCanal);
}
