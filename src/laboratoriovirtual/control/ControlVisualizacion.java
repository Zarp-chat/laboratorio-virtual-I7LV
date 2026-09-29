package laboratoriovirtual.control;

import java.awt.event.ItemEvent;
import java.util.List;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JLabel;

/**
 * Conecta los selectores "Ventana:" y "Escala:" de una pestaña de señal con
 * su gráfica (I7LV-18): al elegir una ventana, la gráfica pasa a abarcar esa
 * duración; al elegir una escala, cambia el rango de su eje Y.
 *
 * Es genérica, como ControlSeleccion: solo conoce los componentes que recibe
 * y las interfaces VentanaAjustable y EscalaAjustable, así que sirve igual
 * para la pestaña "Señal analógica" (GraficaAnalogica, con las dos) y la
 * "Señal digital" (GraficaDigital, solo con ventana). Si la gráfica no tiene
 * escala ajustable, oculta la etiqueta y el selector de escala.
 *
 * Las opciones las pone este control en los selectores, que en el
 * formulario están vacíos: así cada texto va siempre junto con su valor
 * ("1 min" son 60 s) en un solo lugar.
 *
 * Todo ocurre en el hilo de Swing: los avisos de los selectores llegan ahí,
 * y ahí se deben cambiar las gráficas.
 */
public class ControlVisualizacion {

    /**
     * Una opción del selector de ventana.
     *
     * @param texto    lo que muestra el selector
     * @param segundos duración de la ventana
     */
    private record OpcionVentana(String texto, double segundos) {
    }

    /** Opciones del selector de ventana, en el orden en que aparecen. */
    private static final List<OpcionVentana> VENTANAS = List.of(
            new OpcionVentana("1 s", 1.0),
            new OpcionVentana("5 s", 5.0),
            new OpcionVentana("10 s", 10.0),
            new OpcionVentana("30 s", 30.0),
            new OpcionVentana("1 min", 60.0));

    /** Opciones del selector de escala, en el orden en que aparecen. */
    private static final List<EscalaVertical> ESCALAS = List.of(EscalaVertical.values());

    private final JComboBox<String> comboVentana;
    private final JComboBox<String> comboEscala;
    private final VentanaAjustable grafica;

    /**
     * Pone las opciones en los selectores, los deja mostrando lo que ya
     * tiene la gráfica y los conecta con ella. Se llama desde el hilo de
     * Swing.
     *
     * @param comboVentana selector de la ventana visible de la pestaña
     * @param lblEscala    etiqueta "Escala:" de la pestaña
     * @param comboEscala  selector de la escala vertical de la pestaña
     * @param grafica      gráfica de esa misma pestaña; si además es
     *                     EscalaAjustable, se conecta también la escala, y si
     *                     no, la etiqueta y el selector de escala se ocultan
     * @throws IllegalArgumentException si la ventana actual de la gráfica no
     *                                  es una de las opciones
     */
    public ControlVisualizacion(JComboBox<String> comboVentana, JLabel lblEscala,
                                JComboBox<String> comboEscala, VentanaAjustable grafica) {
        this.comboVentana = comboVentana;
        this.comboEscala = comboEscala;
        this.grafica = grafica;

        // Como en ControlSeleccion: primero los selectores muestran lo que ya
        // tiene la gráfica, y después se conectan los oyentes. Así este
        // ajuste inicial no se toma como una elección del usuario.
        comboVentana.setModel(new DefaultComboBoxModel<>(
                VENTANAS.stream().map(OpcionVentana::texto).toArray(String[]::new)));
        comboVentana.setSelectedIndex(opcionDeVentana(grafica.getVentanaVisible()));
        // ItemListener y no ActionListener: solo avisa cuando la selección
        // de verdad cambia (ver ControlSeleccion)
        comboVentana.addItemListener(this::ventanaElegida);

        if (grafica instanceof EscalaAjustable conEscala) {
            comboEscala.setModel(new DefaultComboBoxModel<>(
                    ESCALAS.stream().map(EscalaVertical::toString).toArray(String[]::new)));
            comboEscala.setSelectedIndex(ESCALAS.indexOf(conEscala.getEscala()));
            comboEscala.addItemListener(e -> escalaElegida(e, conEscala));
        } else {
            // En la pestaña digital: cada carril va siempre de 0 a 1
            lblEscala.setVisible(false);
            comboEscala.setVisible(false);
        }
    }

    private void ventanaElegida(ItemEvent e) {
        // Cada cambio avisa dos veces: DESELECTED por la opción que se deja y
        // SELECTED por la nueva. Basta con el segundo.
        if (e.getStateChange() == ItemEvent.SELECTED) {
            grafica.setVentanaVisible(VENTANAS.get(comboVentana.getSelectedIndex()).segundos());
        }
    }

    private void escalaElegida(ItemEvent e, EscalaAjustable conEscala) {
        if (e.getStateChange() == ItemEvent.SELECTED) {
            conEscala.setEscala(ESCALAS.get(comboEscala.getSelectedIndex()));
        }
    }

    /** Posición en el selector de la opción con esa duración. */
    private static int opcionDeVentana(double segundos) {
        for (int i = 0; i < VENTANAS.size(); i++) {
            if (VENTANAS.get(i).segundos() == segundos) {
                return i;
            }
        }
        throw new IllegalArgumentException("La ventana de la gráfica (" + segundos
                + " s) no es una de las opciones del selector");
    }
}
