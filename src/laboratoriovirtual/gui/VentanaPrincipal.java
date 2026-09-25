package laboratoriovirtual.gui;

/**
 * Ventana principal del laboratorio virtual: encabezado arriba, pestañas en
 * el centro y barra de estado abajo.
 *
 * Diseñada con el editor visual de NetBeans (VentanaPrincipal.form). No tiene
 * lógica: cada zona la manejará un controlador del paquete
 * laboratoriovirtual.control a través de los métodos get.
 *
 * No tiene método main: el único punto de arranque es laboratoriovirtual.Main.
 */
public class VentanaPrincipal extends javax.swing.JFrame {

    /**
     * Crea la ventana con todas sus pestañas.
     */
    public VentanaPrincipal() {
        initComponents();
    }

    /**
     * Genera los nombres de los canales para el selector de una pestaña de
     * señal: prefijo0, prefijo1, ... Lo usa el código de creación
     * personalizado ("Custom Creation Code") de panelAnalogica y panelDigital
     * en VentanaPrincipal.form, con las constantes de FuenteDeDatos.
     *
     * @param prefijo  letra del tipo de señal: "A" analógica, "D" digital
     * @param cantidad número de canales
     * @return nombres desde prefijo0 hasta prefijo(cantidad - 1)
     */
    private static String[] nombresCanales(String prefijo, int cantidad) {
        String[] nombres = new String[cantidad];
        for (int i = 0; i < cantidad; i++) {
            nombres[i] = prefijo + i;
        }
        return nombres;
    }

    /**
     * Crea los componentes del formulario. Lo llama el constructor.
     * ADVERTENCIA: no modifique este código a mano. NetBeans lo regenera
     * siempre a partir del archivo .form.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        encabezado = new laboratoriovirtual.gui.Encabezado();
        pestanas = new javax.swing.JTabbedPane();
        panelAnalogica = new laboratoriovirtual.gui.PanelSenal(nombresCanales("A", laboratoriovirtual.datos.FuenteDeDatos.NUM_ANALOGICAS));
        panelDigital = new laboratoriovirtual.gui.PanelSenal(nombresCanales("D", laboratoriovirtual.datos.FuenteDeDatos.NUM_DIGITALES_ENTRADA));
        panelSalidas = new laboratoriovirtual.gui.PanelSalidas();
        panelMuestreo = new laboratoriovirtual.gui.PanelMuestreo();
        barraEstado = new laboratoriovirtual.gui.BarraEstado();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("Laboratorio Virtual — Interfaces 710044M");
        setMinimumSize(new java.awt.Dimension(800, 550));
        setPreferredSize(new java.awt.Dimension(1000, 700));
        getContentPane().setLayout(new java.awt.BorderLayout());
        getContentPane().add(encabezado, java.awt.BorderLayout.NORTH);

        pestanas.setBackground(new java.awt.Color(245, 245, 245));
        pestanas.setFont(new java.awt.Font("SansSerif", 1, 13)); // NOI18N
        pestanas.addTab("Señal analógica", panelAnalogica);
        pestanas.addTab("Señal digital", panelDigital);
        pestanas.addTab("Salidas", panelSalidas);
        pestanas.addTab("Muestreo", panelMuestreo);

        getContentPane().add(pestanas, java.awt.BorderLayout.CENTER);
        getContentPane().add(barraEstado, java.awt.BorderLayout.SOUTH);

        pack();
    }// </editor-fold>//GEN-END:initComponents

    /** Franja superior con el logo, el título y los creadores. */
    public Encabezado getEncabezado() {
        return encabezado;
    }

    /** Franja inferior con los botones Iniciar y Detener y el estado. */
    public BarraEstado getBarraEstado() {
        return barraEstado;
    }

    /** Pestaña "Señal analógica", con los canales A0 a A7. */
    public PanelSenal getPanelAnalogica() {
        return panelAnalogica;
    }

    /** Pestaña "Señal digital", con los canales D0 a D3. */
    public PanelSenal getPanelDigital() {
        return panelDigital;
    }

    /** Pestaña "Salidas". */
    public PanelSalidas getPanelSalidas() {
        return panelSalidas;
    }

    /** Pestaña "Muestreo". */
    public PanelMuestreo getPanelMuestreo() {
        return panelMuestreo;
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private laboratoriovirtual.gui.BarraEstado barraEstado;
    private laboratoriovirtual.gui.Encabezado encabezado;
    private laboratoriovirtual.gui.PanelSenal panelAnalogica;
    private laboratoriovirtual.gui.PanelSenal panelDigital;
    private laboratoriovirtual.gui.PanelMuestreo panelMuestreo;
    private laboratoriovirtual.gui.PanelSalidas panelSalidas;
    private javax.swing.JTabbedPane pestanas;
    // End of variables declaration//GEN-END:variables
}
