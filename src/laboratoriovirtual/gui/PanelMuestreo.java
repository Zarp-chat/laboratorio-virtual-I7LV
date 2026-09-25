package laboratoriovirtual.gui;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextField;

/**
 * Pestaña del tiempo de muestreo: muestra el tiempo actual y permite
 * escribir y aplicar uno nuevo.
 *
 * Diseñada con el editor visual de NetBeans (PanelMuestreo.form). No tiene
 * lógica: el controlador correspondiente usa los métodos get.
 */
public class PanelMuestreo extends javax.swing.JPanel {

    /**
     * Crea el panel de muestreo.
     */
    public PanelMuestreo() {
        initComponents();
    }

    /**
     * Crea los componentes del formulario. Lo llama el constructor.
     * ADVERTENCIA: no modifique este código a mano. NetBeans lo regenera
     * siempre a partir del archivo .form.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        panelFormulario = new javax.swing.JPanel();
        lblEtiquetaActual = new javax.swing.JLabel();
        lblTiempoActual = new javax.swing.JLabel();
        lblEtiquetaNuevo = new javax.swing.JLabel();
        panelCampo = new javax.swing.JPanel();
        txtNuevoTiempo = new javax.swing.JTextField();
        lblRango = new javax.swing.JLabel();
        panelBoton = new javax.swing.JPanel();
        btnAplicar = new javax.swing.JButton();

        setBackground(new java.awt.Color(245, 245, 245));
        setBorder(javax.swing.BorderFactory.createEmptyBorder(16, 16, 16, 16));

        panelFormulario.setBackground(new java.awt.Color(255, 255, 255));
        panelFormulario.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(180, 180, 180)), javax.swing.BorderFactory.createEmptyBorder(16, 20, 16, 20)));
        panelFormulario.setLayout(new java.awt.GridLayout(3, 2, 16, 10));

        lblEtiquetaActual.setFont(new java.awt.Font("SansSerif", 0, 13)); // NOI18N
        lblEtiquetaActual.setText("Tiempo actual:");
        panelFormulario.add(lblEtiquetaActual);

        lblTiempoActual.setFont(new java.awt.Font("SansSerif", 1, 13)); // NOI18N
        lblTiempoActual.setText("500 ms");
        panelFormulario.add(lblTiempoActual);

        lblEtiquetaNuevo.setFont(new java.awt.Font("SansSerif", 0, 13)); // NOI18N
        lblEtiquetaNuevo.setText("Nuevo tiempo (ms):");
        panelFormulario.add(lblEtiquetaNuevo);

        panelCampo.setBackground(new java.awt.Color(255, 255, 255));
        panelCampo.setLayout(new java.awt.FlowLayout(0, 0, 0));

        txtNuevoTiempo.setColumns(8);
        txtNuevoTiempo.setFont(new java.awt.Font("SansSerif", 0, 13)); // NOI18N
        txtNuevoTiempo.setText("500");
        txtNuevoTiempo.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                txtNuevoTiempoActionPerformed(evt);
            }
        });
        panelCampo.add(txtNuevoTiempo);

        panelFormulario.add(panelCampo);

        lblRango.setFont(new java.awt.Font("SansSerif", 0, 12)); // NOI18N
        lblRango.setText("Permitido: 10 a 10 000 ms");
        panelFormulario.add(lblRango);

        panelBoton.setBackground(new java.awt.Color(255, 255, 255));
        panelBoton.setLayout(new java.awt.FlowLayout(0, 0, 0));

        btnAplicar.setFont(new java.awt.Font("SansSerif", 1, 12)); // NOI18N
        btnAplicar.setText("Aplicar");
        btnAplicar.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnAplicarActionPerformed(evt);
            }
        });
        panelBoton.add(btnAplicar);

        panelFormulario.add(panelBoton);

        add(panelFormulario);
    }// </editor-fold>//GEN-END:initComponents

    private void txtNuevoTiempoActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txtNuevoTiempoActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_txtNuevoTiempoActionPerformed

    private void btnAplicarActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAplicarActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_btnAplicarActionPerformed

    /** Texto con el tiempo de muestreo que se está usando. */
    public JLabel getLblTiempoActual() {
        return lblTiempoActual;
    }

    /** Campo donde se escribe el nuevo tiempo de muestreo, en milisegundos. */
    public JTextField getTxtNuevoTiempo() {
        return txtNuevoTiempo;
    }

    /** Texto con el rango de tiempos permitido. */
    public JLabel getLblRango() {
        return lblRango;
    }

    /** Botón que aplica el tiempo escrito en el campo. */
    public JButton getBtnAplicar() {
        return btnAplicar;
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnAplicar;
    private javax.swing.JLabel lblEtiquetaActual;
    private javax.swing.JLabel lblEtiquetaNuevo;
    private javax.swing.JLabel lblRango;
    private javax.swing.JLabel lblTiempoActual;
    private javax.swing.JPanel panelBoton;
    private javax.swing.JPanel panelCampo;
    private javax.swing.JPanel panelFormulario;
    private javax.swing.JTextField txtNuevoTiempo;
    // End of variables declaration//GEN-END:variables
}
