package laboratoriovirtual.control;

import java.awt.event.ItemEvent;
import javax.swing.JComboBox;

/**
 * Conecta el selector de canal de una pestaña con su gráfica (I7LV-16, R1):
 * al elegir otro canal en el selector, la gráfica pasa a mostrar ese canal.
 *
 * Es genérica: solo conoce un JComboBox<String> y la interfaz
 * CanalSeleccionable, así que sirve igual para la pestaña "Señal analógica"
 * (GraficaAnalogica) y, sin cambios, para la "Señal digital" (GraficaDigital,
 * I7LV-19), donde elegir un canal cambia la señal resaltada. La posición del
 * canal en el selector es su número: la gráfica toma los nombres de los
 * canales de ese mismo selector.
 *
 * Todo ocurre en el hilo de Swing: los avisos del selector llegan ahí, y ahí
 * se debe llamar cambiarCanal().
 */
public class ControlSeleccion {

    private final JComboBox<String> selector;
    private final CanalSeleccionable grafica;

    /**
     * Deja el selector mostrando el canal actual de la gráfica y lo conecta
     * con ella. Se llama desde el hilo de Swing.
     *
     * @param selector selector de canal de la pestaña
     * @param grafica  gráfica de esa misma pestaña
     */
    public ControlSeleccion(JComboBox<String> selector, CanalSeleccionable grafica) {
        this.selector = selector;
        this.grafica = grafica;

        // Primero el selector muestra el canal que ya tiene la gráfica, y
        // después se conecta el oyente: así este ajuste inicial no se toma
        // como una elección del usuario y no vacía la gráfica.
        selector.setSelectedIndex(grafica.getCanal());

        // ItemListener y no ActionListener: el JComboBox dispara un
        // ActionEvent cada vez que se elige un canal, aunque sea el mismo que
        // ya estaba. El ItemEvent solo llega cuando la selección de verdad
        // cambia.
        selector.addItemListener(this::seleccionCambiada);
    }

    private void seleccionCambiada(ItemEvent e) {
        // Cada cambio avisa dos veces: DESELECTED por el canal que se deja y
        // SELECTED por el nuevo. Basta con el segundo.
        if (e.getStateChange() == ItemEvent.SELECTED) {
            grafica.cambiarCanal(selector.getSelectedIndex());
        }
    }
}
