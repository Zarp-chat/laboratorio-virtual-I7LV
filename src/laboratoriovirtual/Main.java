package laboratoriovirtual;

import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import laboratoriovirtual.control.CarpetaRecordada;
import laboratoriovirtual.control.ControlGuardado;
import laboratoriovirtual.control.ControlMuestreo;
import laboratoriovirtual.control.ControlSalidas;
import laboratoriovirtual.control.ControlSeleccion;
import laboratoriovirtual.control.ControlVisualizacion;
import laboratoriovirtual.control.DialogosGuardado;
import laboratoriovirtual.control.DialogosGuardadoSwing;
import laboratoriovirtual.control.GraficaAnalogica;
import laboratoriovirtual.control.GraficaDigital;
import laboratoriovirtual.datos.FuenteAleatoria;
import laboratoriovirtual.datos.FuenteDeDatos;
import laboratoriovirtual.datos.FuenteDeDatosException;
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

            // Conectar la fuente antes de crear los controladores:
            // ControlSalidas le envía el estado inicial de las salidas.
            // Conexión y muestreo van por separado: la fuente queda conectada
            // hasta cerrar la ventana, e Iniciar y Detener solo arrancan y
            // detienen la lectura periódica. Así las salidas funcionan con el
            // muestreo detenido. En el Laboratorio 2 conectar y desconectar
            // (abrir y cerrar el puerto serie) lo hará la pestaña Conexión.
            FuenteDeDatosException errorAlConectar = null;
            try {
                fuente.iniciar();
            } catch (FuenteDeDatosException e) {
                errorAlConectar = e;
            }
            boolean conectada = errorAlConectar == null;

            // Controladores: cada uno maneja una parte de la ventana.
            ControlMuestreo controlMuestreo = new ControlMuestreo(muestreador,
                    ventana.getBarraEstado(), ventana.getPanelMuestreo(), ventana);
            ControlSalidas controlSalidas = new ControlSalidas(fuente,
                    ventana.getPanelSalidas(), ventana);

            // Sin fuente conectada no se puede muestrear ni cambiar el tiempo
            // de muestreo ni las salidas. El resto de la ventana sigue igual.
            if (!conectada) {
                controlMuestreo.setHabilitado(false);
                controlSalidas.setHabilitado(false);
            }

            // Gráfica en vivo de la pestaña "Señal analógica". Arranca en el
            // canal 0 (A0) y el selector de la pestaña la cambia de canal.
            // Los selectores "Ventana:" y "Escala:" cambian cuánto tiempo se
            // ve y el rango del eje Y.
            GraficaAnalogica graficaAnalogica = new GraficaAnalogica(
                    muestreador, ventana.getPanelAnalogica(), 0);
            new ControlSeleccion(ventana.getPanelAnalogica().getComboCanal(),
                    graficaAnalogica);
            new ControlVisualizacion(ventana.getPanelAnalogica().getComboVentana(),
                    ventana.getPanelAnalogica().getLblEscala(),
                    ventana.getPanelAnalogica().getComboEscala(), graficaAnalogica);

            // Gráfica en vivo de la pestaña "Señal digital": diagrama de
            // tiempos con D0 a D3 y el valor en hexadecimal. Arranca con D0
            // seleccionada y el selector de la pestaña elige cuál se
            // resalta, sin borrar nada. El selector "Ventana:" cambia cuánto
            // tiempo se ve; el de escala queda oculto, porque cada carril va
            // siempre de 0 a 1.
            GraficaDigital graficaDigital = new GraficaDigital(
                    muestreador, ventana.getPanelDigital(), 0);
            new ControlSeleccion(ventana.getPanelDigital().getComboCanal(),
                    graficaDigital);
            new ControlVisualizacion(ventana.getPanelDigital().getComboVentana(),
                    ventana.getPanelDigital().getLblEscala(),
                    ventana.getPanelDigital().getComboEscala(), graficaDigital);

            // Botón "Guardar esta señal…" de cada pestaña: guarda en un
            // archivo la señal seleccionada (en la digital, la resaltada).
            // Las dos pestañas comparten la carpeta recordada: la ventana de
            // guardar abre donde se guardó la última vez, en cualquiera de
            // ellas. Empieza en Documentos y no se guarda al cerrar.
            CarpetaRecordada carpetaGuardado = new CarpetaRecordada();
            DialogosGuardado dialogosGuardado = new DialogosGuardadoSwing(ventana);
            new ControlGuardado(ventana.getPanelAnalogica().getBtnGuardar(),
                    graficaAnalogica, carpetaGuardado, dialogosGuardado);
            new ControlGuardado(ventana.getPanelDigital().getBtnGuardar(),
                    graficaDigital, carpetaGuardado, dialogosGuardado);

            // Al cerrar la ventana: primero se detiene el muestreo, para que
            // no lea una fuente desconectada, y luego se desconecta la fuente.
            // En el Laboratorio 2 esto cierra el puerto serie.
            ventana.addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    muestreador.detener();
                    if (conectada) {
                        fuente.detener();
                    }
                }
            });

            ventana.setLocationRelativeTo(null);
            ventana.setVisible(true);

            // El aviso va después de mostrar la ventana, para que aparezca
            // sobre ella. La aplicación sigue abierta.
            if (!conectada) {
                JOptionPane.showMessageDialog(ventana,
                        "No se pudo conectar con la fuente de datos:\n"
                        + errorAlConectar.getMessage() + "\n\n"
                        + "El muestreo y las salidas quedan deshabilitados.",
                        "Fuente de datos", JOptionPane.WARNING_MESSAGE);
            }
        });
    }
}