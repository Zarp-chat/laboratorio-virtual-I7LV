package laboratoriovirtual.gui;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Pestaña de una señal: selectores de canal, de ventana visible y de escala
 * vertical, zona de la gráfica y botón para guardar la señal. La ventana usa
 * dos: una para las entradas analógicas y otra para las digitales (en esa,
 * el controlador oculta el selector de escala).
 *
 * Diseñada con el editor visual de NetBeans (PanelSenal.form). No tiene
 * lógica: el controlador correspondiente usa los métodos get. La gráfica
 * no está en el formulario: la agrega en código, dentro de panelGrafica,
 * la gráfica de la pestaña (GraficaAnalogica o GraficaDigital, del paquete
 * laboratoriovirtual.control).
 */
public class PanelSenal extends javax.swing.JPanel {

    /**
     * Crea el panel con el selector de canal vacío. Lo necesita el editor
     * visual de NetBeans; el programa usa {@link #PanelSenal(String[])}.
     */
    public PanelSenal() {
        initComponents();
    }

    /**
     * Crea el panel y carga los nombres de los canales en el selector.
     *
     * @param nombresCanales nombres que muestra el selector, en orden de canal
     */
    public PanelSenal(String[] nombresCanales) {
        this();
        comboCanal.setModel(new DefaultComboBoxModel<>(nombresCanales));
    }

    /**
     * Crea los componentes del formulario. Lo llama el constructor.
     * ADVERTENCIA: no modifique este código a mano. NetBeans lo regenera
     * siempre a partir del archivo .form.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        panelSelector = new javax.swing.JPanel();
        lblCanal = new javax.swing.JLabel();
        comboCanal = new javax.swing.JComboBox<>();
        lblVentana = new javax.swing.JLabel();
        comboVentana = new javax.swing.JComboBox<>();
        lblEscala = new javax.swing.JLabel();
        comboEscala = new javax.swing.JComboBox<>();
        panelGrafica = new javax.swing.JPanel();
        panelPie = new javax.swing.JPanel();
        btnGuardar = new javax.swing.JButton();

        setBackground(new java.awt.Color(245, 245, 245));
        setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setLayout(new java.awt.BorderLayout());

        panelSelector.setBackground(new java.awt.Color(245, 245, 245));
        panelSelector.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 10, 0));
        panelSelector.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 0, 0));

        lblCanal.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 8));
        lblCanal.setFont(new java.awt.Font("SansSerif", 0, 13)); // NOI18N
        lblCanal.setText("Canal:");
        panelSelector.add(lblCanal);

        comboCanal.setFont(new java.awt.Font("SansSerif", 0, 13)); // NOI18N
        panelSelector.add(comboCanal);

        lblVentana.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 24, 0, 8));
        lblVentana.setFont(new java.awt.Font("SansSerif", 0, 13)); // NOI18N
        lblVentana.setText("Ventana:");
        panelSelector.add(lblVentana);

        comboVentana.setFont(new java.awt.Font("SansSerif", 0, 13)); // NOI18N
        comboVentana.setToolTipText("Tiempo que se ve en la gráfica");
        panelSelector.add(comboVentana);

        lblEscala.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 24, 0, 8));
        lblEscala.setFont(new java.awt.Font("SansSerif", 0, 13)); // NOI18N
        lblEscala.setText("Escala:");
        panelSelector.add(lblEscala);

        comboEscala.setFont(new java.awt.Font("SansSerif", 0, 13)); // NOI18N
        comboEscala.setToolTipText("Rango del eje vertical");
        panelSelector.add(comboEscala);

        add(panelSelector, java.awt.BorderLayout.NORTH);

        panelGrafica.setBackground(new java.awt.Color(255, 255, 255));
        panelGrafica.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(180, 180, 180)));
        panelGrafica.setLayout(new java.awt.BorderLayout());
        add(panelGrafica, java.awt.BorderLayout.CENTER);

        panelPie.setBackground(new java.awt.Color(245, 245, 245));
        panelPie.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createEmptyBorder(10, 0, 0, 0), javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createMatteBorder(1, 0, 0, 0, new java.awt.Color(180, 180, 180)), javax.swing.BorderFactory.createEmptyBorder(8, 0, 0, 0))));
        panelPie.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 0, 0));

        btnGuardar.setFont(new java.awt.Font("SansSerif", 1, 12)); // NOI18N
        btnGuardar.setText("Guardar esta señal…");
        panelPie.add(btnGuardar);

        add(panelPie, java.awt.BorderLayout.SOUTH);
    }// </editor-fold>//GEN-END:initComponents

    /** Selector del canal que se muestra en la gráfica. */
    public JComboBox<String> getComboCanal() {
        return comboCanal;
    }

    /**
     * Selector de la ventana visible: cuánto tiempo abarca el eje X. Está
     * vacío en el formulario; sus opciones las pone el controlador.
     */
    public JComboBox<String> getComboVentana() {
        return comboVentana;
    }

    /**
     * Selector de la escala vertical (el rango del eje Y). Está vacío en el
     * formulario; sus opciones las pone el controlador, que lo oculta si la
     * gráfica de la pestaña no tiene escala ajustable.
     */
    public JComboBox<String> getComboEscala() {
        return comboEscala;
    }

    /** Etiqueta "Escala:", que se oculta junto con su selector. */
    public JLabel getLblEscala() {
        return lblEscala;
    }

    /** Panel con BorderLayout donde va la gráfica, en el centro. */
    public JPanel getPanelGrafica() {
        return panelGrafica;
    }

    /** Botón para guardar en un archivo la señal de esta pestaña. */
    public JButton getBtnGuardar() {
        return btnGuardar;
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnGuardar;
    private javax.swing.JComboBox<String> comboCanal;
    private javax.swing.JComboBox<String> comboEscala;
    private javax.swing.JComboBox<String> comboVentana;
    private javax.swing.JLabel lblCanal;
    private javax.swing.JLabel lblEscala;
    private javax.swing.JLabel lblVentana;
    private javax.swing.JPanel panelGrafica;
    private javax.swing.JPanel panelPie;
    private javax.swing.JPanel panelSelector;
    // End of variables declaration//GEN-END:variables
}
