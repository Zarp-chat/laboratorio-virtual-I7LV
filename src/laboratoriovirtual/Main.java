package laboratoriovirtual;

import javax.swing.SwingUtilities;
import laboratoriovirtual.gui.Tema;
import laboratoriovirtual.gui.VentanaPrincipal;

/**
 * Punto de arranque del laboratorio virtual.
 */
public class Main {

    /**
     * @param args no se usan
     */
    public static void main(String[] args) {
        // Swing solo se debe tocar desde su propio hilo (Event Dispatch Thread)
        SwingUtilities.invokeLater(() -> {
            Tema.aplicar();

            // En las tareas siguientes aquí se crearán la fuente de datos
            // (FuenteAleatoria; en el Laboratorio 2, FuenteSerial), el Muestreador
            // y los controladores que conectan la ventana con ellos.

            VentanaPrincipal ventana = new VentanaPrincipal();
            ventana.setLocationRelativeTo(null); // centrada en la pantalla
            ventana.setVisible(true);
        });
    }
}
