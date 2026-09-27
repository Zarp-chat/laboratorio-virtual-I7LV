package laboratoriovirtual;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.SwingUtilities;
import laboratoriovirtual.control.ControlMuestreo;
import laboratoriovirtual.control.ControlSeleccion;
import laboratoriovirtual.control.GraficaAnalogica;
import laboratoriovirtual.control.GraficaDigital;
import laboratoriovirtual.datos.FuenteAleatoria;
import laboratoriovirtual.datos.FuenteDeDatos;
import laboratoriovirtual.gui.Tema;
import laboratoriovirtual.gui.VentanaPrincipal;
import laboratoriovirtual.muestreo.Muestreador;

/**
 * Punto de arranque del programa: crea las piezas y las conecta.
 */
public class Main {

    /** Tiempo de muestreo con el que arranca el programa. */
    private static final int PERIODO_INICIAL_MS = 500;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            Tema.aplicar();

            // ÚNICO lugar donde se elige la fuente de datos.
            // En el Laboratorio 2 esta línea pasa a ser: new FuenteSerial(...)
            FuenteDeDatos fuente = new FuenteAleatoria();
            Muestreador muestreador = new Muestreador(fuente, PERIODO_INICIAL_MS);

            VentanaPrincipal ventana = new VentanaPrincipal();

            // Controladores: cada uno maneja una parte de la ventana.
            // En tareas siguientes se agregan aquí ControlSalidas y
            // ControlGuardado.
            new ControlMuestreo(muestreador, ventana.getBarraEstado(),
                    ventana.getPanelMuestreo(), ventana);

            // Gráfica en vivo de la pestaña "Señal analógica". Arranca en el
            // canal 0 (A0) y el selector de la pestaña la cambia de canal.
            // Queda en una variable porque el guardado de la señal
            // (Sprint 3) la necesitará.
            GraficaAnalogica graficaAnalogica = new GraficaAnalogica(
                    muestreador, ventana.getPanelAnalogica(), 0);
            new ControlSeleccion(ventana.getPanelAnalogica().getComboCanal(),
                    graficaAnalogica);

            // Gráfica en vivo de la pestaña "Señal digital": diagrama de
            // tiempos con D0 a D3 y el valor en hexadecimal. Arranca con D0
            // resaltada. Queda en una variable porque la conexión con el
            // selector de la pestaña (I7LV-19) y el guardado de la señal
            // (Sprint 3) la necesitarán.
            GraficaDigital graficaDigital = new GraficaDigital(
                    muestreador, ventana.getPanelDigital(), 0);

            // Al cerrar la ventana se detiene el muestreo y se libera la fuente.
            // En el Laboratorio 2 esto cierra el puerto serie.
            ventana.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    muestreador.detener();
                }
            });

            ventana.setLocationRelativeTo(null);
            ventana.setVisible(true);
        });
    }
}