package laboratoriovirtual.control;

import java.awt.Component;
import java.util.Locale;
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
 *
 * Iniciar y Detener solo arrancan y detienen la lectura periódica: no
 * conectan ni desconectan la fuente (eso lo hace Main en el Laboratorio 1).
 * Por eso se pueden pulsar varias veces seguidas sin reconectar.
 */
public class ControlMuestreo implements OyenteMuestras {

    /**
     * Formato regional de Colombia para el contador de muestras: separa los
     * miles con punto (12.345). Es fijo, no el del equipo, para que el texto
     * sea el mismo en cualquier computador.
     */
    private static final Locale FORMATO_REGIONAL = Locale.of("es", "CO");

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

    // Falso si la fuente no está conectada: Iniciar y el cambio de tiempo
    // la usan, así que quedan deshabilitados (ver setHabilitado).
    private boolean habilitado = true;

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

    /**
     * Habilita o deshabilita los controles que usan la fuente: Iniciar y el
     * cambio del tiempo de muestreo. Main los deshabilita si no pudo conectar
     * la fuente; en el Laboratorio 2 lo hará la pestaña Conexión al abrir y
     * cerrar el puerto serie. Detener sigue disponible mientras el muestreo
     * esté corriendo. Se llama en el hilo de Swing.
     */
    public void setHabilitado(boolean habilitado) {
        this.habilitado = habilitado;
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
        btnIniciar.setEnabled(habilitado && !corriendo);
        btnDetener.setEnabled(corriendo);
        txtNuevoTiempo.setEnabled(habilitado);
        btnAplicar.setEnabled(habilitado);

        lblTiempoActual.setText(periodo + " ms");
        lblEstado.setText(textoEstado(corriendo, habilitado, periodo, muestrasRecibidas.get()));
    }

    /**
     * Texto de la barra de estado en cada uno de sus tres estados:
     * - muestreando: "● Muestreando · cada 10 ms · 12.345 muestras";
     * - detenido: "Detenido · cada 10 ms";
     * - sin fuente conectada: "Sin conexión con la fuente de datos".
     * El tiempo de muestreo va sin separador de miles, igual que en la
     * pestaña Muestreo y en su campo, que no acepta puntos.
     *
     * Es público y estático para que VerificacionSprint2 compruebe que el
     * texto más largo posible cabe en la barra, sin tener que recibir
     * millones de muestras.
     *
     * @param corriendo  si el muestreo está corriendo
     * @param habilitado si la fuente está conectada (ver setHabilitado)
     * @param periodoMs  tiempo de muestreo actual
     * @param muestras   muestras recibidas desde el último Iniciar
     * @return el texto que va en la barra
     */
    public static String textoEstado(boolean corriendo, boolean habilitado, int periodoMs, long muestras) {
        if (corriendo) {
            return "● Muestreando · cada " + periodoMs + " ms · "
                    + String.format(FORMATO_REGIONAL, "%,d", muestras)
                    + (muestras == 1 ? " muestra" : " muestras");
        } else if (habilitado) {
            return "Detenido · cada " + periodoMs + " ms";
        } else {
            return "Sin conexión con la fuente de datos";
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