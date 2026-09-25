package laboratoriovirtual.gui;

import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;

/**
 * Pestaña de una señal: selector de canal, zona de la gráfica y botón para
 * guardar la señal. La ventana usa dos: una para las entradas analógicas y
 * otra para las digitales.
 *
 * Diseñada con el editor visual de NetBeans (PanelSenal.form). No tiene
 * lógica: el controlador correspondiente usa los métodos get. La gráfica
 * se agregará dentro de panelGrafica en otra tarea.
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
        panelGrafica = new javax.swing.JPanel();
        panelPie = new javax.swing.JPanel();
        btnGuardar = new javax.swing.JButton();

        setBackground(new java.awt.Color(245, 245, 245));
        setBorder(javax.swing.BorderFactory.createEmptyBorder(10, 10, 10, 10));
        setLayout(new java.awt.BorderLayout());

        panelSelector.setBackground(new java.awt.Color(245, 245, 245));
        panelSelector.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 10, 0));
        panelSelector.setLayout(new java.awt.FlowLayout(0, 0, 0));

        lblCanal.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 0, 8));
        lblCanal.setFont(new java.awt.Font("SansSerif", 0, 13)); // NOI18N
        lblCanal.setText("Canal:");
        panelSelector.add(lblCanal);

        comboCanal.setFont(new java.awt.Font("SansSerif", 0, 13)); // NOI18N
        panelSelector.add(comboCanal);

        add(panelSelector, java.awt.BorderLayout.NORTH);

        panelGrafica.setBackground(new java.awt.Color(255, 255, 255));
        panelGrafica.setBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(180, 180, 180)));
        panelGrafica.setLayout(new java.awt.BorderLayout());
        add(panelGrafica, java.awt.BorderLayout.CENTER);

        panelPie.setBackground(new java.awt.Color(245, 245, 245));
        panelPie.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createEmptyBorder(10, 0, 0, 0), javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createMatteBorder(1, 0, 0, 0, new java.awt.Color(180, 180, 180)), javax.swing.BorderFactory.createEmptyBorder(8, 0, 0, 0))));
        panelPie.setLayout(new java.awt.FlowLayout(2, 0, 0));

        btnGuardar.setFont(new java.awt.Font("SansSerif", 1, 12)); // NOI18N
        btnGuardar.setText("Guardar esta señal…");
        btnGuardar.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnGuardarActionPerformed(evt);
            }
        });
        panelPie.add(btnGuardar);

        add(panelPie, java.awt.BorderLayout.SOUTH);
    }// </editor-fold>//GEN-END:initComponents

    private void btnGuardarActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnGuardarActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_btnGuardarActionPerformed

    /** Selector del canal que se muestra en la gráfica. */
    public JComboBox<String> getComboCanal() {
        return comboCanal;
    }

    /** Panel vacío, con BorderLayout, donde irá la gráfica. */
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
    private javax.swing.JLabel lblCanal;
    private javax.swing.JPanel panelGrafica;
    private javax.swing.JPanel panelPie;
    private javax.swing.JPanel panelSelector;
    // End of variables declaration//GEN-END:variables
}
