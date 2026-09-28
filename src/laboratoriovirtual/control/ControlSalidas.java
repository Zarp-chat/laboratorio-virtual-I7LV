package laboratoriovirtual.control;

import java.awt.Component;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JToggleButton;
import laboratoriovirtual.datos.FuenteDeDatos;
import laboratoriovirtual.datos.FuenteDeDatosException;
import laboratoriovirtual.gui.LedIndicador;
import laboratoriovirtual.gui.PanelSalidas;
import laboratoriovirtual.gui.Tema;

/**
 * Controla las salidas digitales desde la pestaña Salidas (I7LV-21, I7LV-22).
 *
 * Cada interruptor enciende o apaga su salida con fuente.escribirSalida().
 * La etiqueta de al lado dice el último estado que la fuente aceptó:
 * "Encendida" en rojo o "Apagada" en negro. El LED de la izquierda
 * (I7LV-18) cambia junto con la etiqueta, nunca por su cuenta, y su ayuda
 * emergente dice su estado, por ejemplo "Salida 2: encendida". Si la fuente
 * rechaza la escritura, el interruptor vuelve a como estaba, la etiqueta y
 * el LED no cambian y se avisa.
 *
 * Solo habla con la interfaz FuenteDeDatos, así que funciona igual con la
 * fuente aleatoria y con el puerto serie. No depende del muestreo: las
 * salidas se pueden cambiar con el muestreo detenido, y detenerlo no las
 * cambia. Lo único que necesita es la fuente conectada (fuente.iniciar()).
 *
 * Hilos: las escrituras se hacen en el hilo de Swing, porque vienen de clics.
 * En el Laboratorio 1 escribir es instantáneo. Si en el Laboratorio 2 la
 * escritura por el puerto serie resultara lenta (por ejemplo, si espera la
 * respuesta de la tarjeta), la ventana se congelaría mientras tanto: habría
 * que sacarla de este hilo (por ejemplo, con un SwingWorker) y deshabilitar
 * el interruptor hasta que termine.
 */
public class ControlSalidas {

    private final FuenteDeDatos fuente;
    private final Component ventana; // para centrar los mensajes de aviso

    // Componentes de la pestaña Salidas: la posición i corresponde a la salida i
    private final JToggleButton[] interruptores;
    private final JLabel[] estados;
    private final LedIndicador[] leds;

    /**
     * Conecta los interruptores con la fuente y le envía el estado que
     * muestran (al abrir el programa, todas apagadas), para que pantalla y
     * fuente empiecen de acuerdo. Por eso se crea después de conectar la
     * fuente.
     *
     * @param fuente  fuente de datos, ya conectada
     * @param panel   pestaña Salidas de la ventana
     * @param ventana componente sobre el que se centran los avisos
     */
    public ControlSalidas(FuenteDeDatos fuente, PanelSalidas panel, Component ventana) {
        this.fuente = fuente;
        this.ventana = ventana;
        interruptores = panel.getInterruptores();
        estados = panel.getEstados();
        leds = panel.getLeds();

        if (interruptores.length != FuenteDeDatos.NUM_SALIDAS_DIGITALES
                || estados.length != FuenteDeDatos.NUM_SALIDAS_DIGITALES
                || leds.length != FuenteDeDatos.NUM_SALIDAS_DIGITALES) {
            throw new IllegalArgumentException("La pestaña debe tener "
                    + FuenteDeDatos.NUM_SALIDAS_DIGITALES + " interruptores, "
                    + FuenteDeDatos.NUM_SALIDAS_DIGITALES + " etiquetas de estado y "
                    + FuenteDeDatos.NUM_SALIDAS_DIGITALES + " LEDs");
        }

        // La ayuda emergente de cada LED dice lo que muestra desde el
        // comienzo, también si el estado inicial no se puede enviar.
        for (int i = 0; i < leds.length; i++) {
            ponerAyudaLed(i);
        }

        // ActionEvent solo lo producen el clic y el teclado, no setSelected().
        // Así, devolver un interruptor a su estado anterior no vuelve a escribir.
        for (int i = 0; i < interruptores.length; i++) {
            int salida = i;
            interruptores[i].addActionListener(e -> cambiarSalida(salida));
        }

        enviarEstadoInicial();
    }

    /**
     * Habilita o deshabilita los 4 interruptores. En el Laboratorio 1 quedan
     * habilitados siempre, salvo que no se haya podido conectar la fuente. En
     * el Laboratorio 2 lo usará la pestaña Conexión: habilitarlos al abrir el
     * puerto serie y deshabilitarlos al cerrarlo. Se llama en el hilo de Swing.
     */
    public void setHabilitado(boolean habilitado) {
        for (JToggleButton interruptor : interruptores) {
            interruptor.setEnabled(habilitado);
        }
    }

    // ===================== Acciones del usuario =====================
    // Se ejecuta en el hilo de Swing (viene de un clic).

    /** Se pulsó el interruptor de la salida: ya muestra el estado pedido. */
    private void cambiarSalida(int salida) {
        JToggleButton interruptor = interruptores[salida];
        boolean encendida = interruptor.isSelected();
        try {
            fuente.escribirSalida(salida, encendida);
            mostrarEstado(salida, encendida);
        } catch (FuenteDeDatosException e) {
            // La salida sigue como estaba: el interruptor vuelve atrás y la
            // etiqueta no cambia. setSelected() no produce ActionEvent, así
            // que esto no vuelve a llamar a cambiarSalida().
            interruptor.setSelected(!encendida);
            avisar("No se pudo " + (encendida ? "encender" : "apagar")
                    + " la salida " + salida + ":\n" + e.getMessage());
        }
    }

    // ===================== Utilidades =====================

    /**
     * Envía a la fuente el estado de cada interruptor y lo muestra en su
     * etiqueta y en su LED. Si la fuente no lo acepta (por ejemplo, porque no se pudo
     * conectar), los interruptores quedan deshabilitados: la pantalla podría
     * no coincidir con las salidas reales. No se avisa aquí porque el aviso
     * de la conexión lo da quien conecta la fuente (Main).
     */
    private void enviarEstadoInicial() {
        try {
            for (int i = 0; i < interruptores.length; i++) {
                boolean encendida = interruptores[i].isSelected();
                fuente.escribirSalida(i, encendida);
                mostrarEstado(i, encendida);
            }
        } catch (FuenteDeDatosException e) {
            setHabilitado(false);
        }
    }

    /**
     * Muestra el estado de la salida: la etiqueta dice "Encendida" en rojo o
     * "Apagada" en negro, y el LED se enciende o se apaga. Es el único lugar
     * que cambia el LED: así siempre coincide con la etiqueta.
     */
    private void mostrarEstado(int salida, boolean encendida) {
        estados[salida].setText(encendida ? "Encendida" : "Apagada");
        estados[salida].setForeground(encendida ? Tema.ROJO : Tema.NEGRO);
        leds[salida].setEncendido(encendida);
        ponerAyudaLed(salida);
    }

    /** Pone en la ayuda emergente del LED lo que muestra, por ejemplo "Salida 2: encendida". */
    private void ponerAyudaLed(int salida) {
        leds[salida].setToolTipText("Salida " + salida + ": "
                + (leds[salida].isEncendido() ? "encendida" : "apagada"));
    }

    /**
     * Muestra un aviso centrado en la ventana. Es protected solo para que
     * VerificacionSprint2, que prueba este control sin ventana, lo reemplace
     * por uno que no abre un cuadro de diálogo.
     */
    protected void avisar(String mensaje) {
        JOptionPane.showMessageDialog(ventana, mensaje,
                "Salidas digitales", JOptionPane.WARNING_MESSAGE);
    }
}
