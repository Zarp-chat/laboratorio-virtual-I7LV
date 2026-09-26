package laboratoriovirtual.control;

import java.awt.Component;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import laboratoriovirtual.datos.FuenteDeDatosException;
import laboratoriovirtual.gui.BarraEstado;
import laboratoriovirtual.gui.PanelMuestreo;
import laboratoriovirtual.muestreo.Muestra;
import laboratoriovirtual.muestreo.Muestreador;
import laboratoriovirtual.muestreo.OyenteMuestras;

/**
 * Controla el tiempo de muestreo desde la interfaz (I7LV-23, I7LV-24).
 *
 * Maneja dos partes de la ventana:
 * - La barra inferior: Iniciar, Detener y el estado (R8).
 * - La pestaña Muestreo: tiempo actual (R8) y cambio del tiempo (R9).
 *
 * Solo habla con el Muestreador. No sabe de dónde vienen los datos,
 * así que funciona igual con la fuente aleatoria y con el puerto serie.
 */
public class ControlMuestreo implements OyenteMuestras {

    private final Muestreador muestreador;
    private final Component ventana; // para centrar los mensajes de aviso

    // Componentes de la barra inferior
    private final JButton btnIniciar;
    private final JButton btnDetener;
    private final JLabel lblEstado;

    // Componentes de la pestaña Muestreo
    private final JLabel lblTiempoActual;
    private final JTextField txtNuevoTiempo;
    private final JLabel lblRango;
    private final JButton btnAplicar;

    // Cuenta las muestras desde el último Iniciar. Es atómica porque la
    // incrementa el hilo de muestreo y la lee el hilo de la interfaz.
    private final AtomicLong muestrasRecibidas = new AtomicLong();

    public ControlMuestreo(Muestreador muestreador, BarraEstado barra,
                           PanelMuestreo panel, Component ventana) {
        this.muestreador = muestreador;
        this.ventana = ventana;

        btnIniciar = barra.getBtnIniciar();
        btnDetener = barra.getBtnDetener();
        lblEstado = barra.getLblEstado();

        lblTiempoActual = panel.getLblTiempoActual();
        txtNuevoTiempo = panel.getTxtNuevoTiempo();
        lblRango = panel.getLblRango();
        btnAplicar = panel.getBtnAplicar();

        // El rango se toma de los límites reales del Muestreador,
        // así la pantalla nunca muestra un número desactualizado.
        lblRango.setText("Permitido: " + Muestreador.PERIODO_MIN_MS
                + " a " + Muestreador.PERIODO_MAX_MS + " ms");
        txtNuevoTiempo.setText(String.valueOf(muestreador.getPeriodoMs()));

        // Conectar los botones con las acciones.
        // En un JTextField, pulsar Enter también dispara la acción.
        btnIniciar.addActionListener(e -> iniciar());
        btnDetener.addActionListener(e -> detener());
        btnAplicar.addActionListener(e -> aplicarTiempo());
        txtNuevoTiempo.addActionListener(e -> aplicarTiempo());

        // Recibir aviso de cada muestra, para mostrar que el sistema está vivo
        muestreador.agregarOyente(this);

        actualizarPantalla();
    }

    // ===================== Acciones del usuario =====================
    // Estos métodos se ejecutan en el hilo de Swing (vienen de clics).

    private void iniciar() {
        try {
            muestrasRecibidas.set(0);
            muestreador.iniciar();
        } catch (FuenteDeDatosException e) {
            avisar("No se pudo iniciar el muestreo:\n" + e.getMessage());
        }
        actualizarPantalla();
    }

    private void detener() {
        muestreador.detener();
        actualizarPantalla();
    }

    private void aplicarTiempo() {
        // Se aceptan espacios, por ejemplo "10 000"
        String texto = txtNuevoTiempo.getText().replace(" ", "").trim();

        int nuevo;
        try {
            nuevo = Integer.parseInt(texto);
        } catch (NumberFormatException e) {
            avisar("Escribe un número entero de milisegundos, por ejemplo 250.");
            restaurarCampo();
            return;
        }

        if (nuevo < Muestreador.PERIODO_MIN_MS || nuevo > Muestreador.PERIODO_MAX_MS) {
            avisar("El tiempo debe estar entre " + Muestreador.PERIODO_MIN_MS
                    + " y " + Muestreador.PERIODO_MAX_MS + " ms.\n"
                    + "Se conserva el tiempo actual.");
            restaurarCampo();
            return;
        }

        try {
            muestreador.setPeriodoMs(nuevo);
        } catch (FuenteDeDatosException e) {
            // En el Laboratorio 2 puede fallar al enviarlo a la tarjeta
            avisar("No se pudo cambiar el tiempo de muestreo:\n" + e.getMessage());
            restaurarCampo();
        }
        actualizarPantalla();
    }

    // ===================== Avisos del Muestreador =====================
    // Estos métodos llegan desde el hilo de muestreo, NO desde Swing.
    // Por eso todo lo que toca la pantalla va dentro de invokeLater.

    @Override
    public void muestraRecibida(Muestra muestra) {
        muestrasRecibidas.incrementAndGet();
        SwingUtilities.invokeLater(this::actualizarPantalla);
    }

    @Override
    public void errorEnFuente(FuenteDeDatosException error) {
        SwingUtilities.invokeLater(() -> {
            actualizarPantalla();
            avisar("El muestreo se detuvo por un error de la fuente de datos:\n"
                    + error.getMessage());
        });
    }

    // ===================== Utilidades =====================

    /** Deja la pantalla de acuerdo con el estado real del Muestreador. */
    private void actualizarPantalla() {
        boolean corriendo = muestreador.estaCorriendo();
        int periodo = muestreador.getPeriodoMs();

        // Solo se puede pulsar el botón que tiene sentido en cada momento
        btnIniciar.setEnabled(!corriendo);
        btnDetener.setEnabled(corriendo);

        lblTiempoActual.setText(periodo + " ms");

        if (corriendo) {
            lblEstado.setText("● Muestreando · cada " + periodo + " ms · "
                    + muestrasRecibidas.get() + " muestras");
        } else {
            lblEstado.setText("Detenido · cada " + periodo + " ms");
        }
    }

    /** Vuelve a poner en el campo el tiempo vigente. */
    private void restaurarCampo() {
        txtNuevoTiempo.setText(String.valueOf(muestreador.getPeriodoMs()));
    }

    private void avisar(String mensaje) {
        JOptionPane.showMessageDialog(ventana, mensaje,
                "Tiempo de muestreo", JOptionPane.WARNING_MESSAGE);
    }
}