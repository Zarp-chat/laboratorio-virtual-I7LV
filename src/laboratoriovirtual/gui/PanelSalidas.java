package laboratoriovirtual.gui;

import javax.swing.JLabel;
import javax.swing.JToggleButton;

/**
 * Pestaña de las salidas digitales: un interruptor y un texto de estado por
 * cada salida (FuenteDeDatos.NUM_SALIDAS_DIGITALES, es decir, 4).
 *
 * Diseñada con el editor visual de NetBeans (PanelSalidas.form). No tiene
 * lógica: el controlador correspondiente usa los métodos get.
 */
public class PanelSalidas extends javax.swing.JPanel {

    /**
     * Crea el panel de salidas.
     */
    public PanelSalidas() {
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

        lblInstrucciones = new javax.swing.JLabel();
        panelCentro = new javax.swing.JPanel();
        panelInterruptores = new javax.swing.JPanel();
        tglSalida0 = new javax.swing.JToggleButton();
        lblEstado0 = new javax.swing.JLabel();
        tglSalida1 = new javax.swing.JToggleButton();
        lblEstado1 = new javax.swing.JLabel();
        tglSalida2 = new javax.swing.JToggleButton();
        lblEstado2 = new javax.swing.JLabel();
        tglSalida3 = new javax.swing.JToggleButton();
        lblEstado3 = new javax.swing.JLabel();

        setBackground(new java.awt.Color(245, 245, 245));
        setBorder(javax.swing.BorderFactory.createEmptyBorder(16, 16, 16, 16));
        setLayout(new java.awt.BorderLayout());

        lblInstrucciones.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 16, 0));
        lblInstrucciones.setFont(new java.awt.Font("SansSerif", 0, 13)); // NOI18N
        lblInstrucciones.setText("Activa o desactiva cada salida digital.");
        add(lblInstrucciones, java.awt.BorderLayout.NORTH);

        panelCentro.setBackground(new java.awt.Color(245, 245, 245));

        panelInterruptores.setBackground(new java.awt.Color(255, 255, 255));
        panelInterruptores.setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createLineBorder(new java.awt.Color(180, 180, 180)), javax.swing.BorderFactory.createEmptyBorder(16, 20, 16, 20)));
        panelInterruptores.setLayout(new java.awt.GridLayout(4, 2, 24, 12));

        tglSalida0.setFont(new java.awt.Font("SansSerif", 1, 12)); // NOI18N
        tglSalida0.setText("Salida 0");
        panelInterruptores.add(tglSalida0);

        lblEstado0.setFont(new java.awt.Font("SansSerif", 0, 13)); // NOI18N
        lblEstado0.setText("Apagada");
        panelInterruptores.add(lblEstado0);

        tglSalida1.setFont(new java.awt.Font("SansSerif", 1, 12)); // NOI18N
        tglSalida1.setText("Salida 1");
        panelInterruptores.add(tglSalida1);

        lblEstado1.setFont(new java.awt.Font("SansSerif", 0, 13)); // NOI18N
        lblEstado1.setText("Apagada");
        panelInterruptores.add(lblEstado1);

        tglSalida2.setFont(new java.awt.Font("SansSerif", 1, 12)); // NOI18N
        tglSalida2.setText("Salida 2");
        panelInterruptores.add(tglSalida2);

        lblEstado2.setFont(new java.awt.Font("SansSerif", 0, 13)); // NOI18N
        lblEstado2.setText("Apagada");
        panelInterruptores.add(lblEstado2);

        tglSalida3.setFont(new java.awt.Font("SansSerif", 1, 12)); // NOI18N
        tglSalida3.setText("Salida 3");
        panelInterruptores.add(tglSalida3);

        lblEstado3.setFont(new java.awt.Font("SansSerif", 0, 13)); // NOI18N
        lblEstado3.setText("Apagada");
        panelInterruptores.add(lblEstado3);

        panelCentro.add(panelInterruptores);

        add(panelCentro, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    /**
     * Interruptores de las salidas, en orden: Salida 0 a Salida 3.
     *
     * @return arreglo nuevo; la posición i corresponde a la salida i
     */
    public JToggleButton[] getInterruptores() {
        return new JToggleButton[] { tglSalida0, tglSalida1, tglSalida2, tglSalida3 };
    }

    /**
     * Textos de estado de las salidas, en el mismo orden que {@link #getInterruptores()}.
     *
     * @return arreglo nuevo; la posición i corresponde a la salida i
     */
    public JLabel[] getEstados() {
        return new JLabel[] { lblEstado0, lblEstado1, lblEstado2, lblEstado3 };
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel lblEstado0;
    private javax.swing.JLabel lblEstado1;
    private javax.swing.JLabel lblEstado2;
    private javax.swing.JLabel lblEstado3;
    private javax.swing.JLabel lblInstrucciones;
    private javax.swing.JPanel panelCentro;
    private javax.swing.JPanel panelInterruptores;
    private javax.swing.JToggleButton tglSalida0;
    private javax.swing.JToggleButton tglSalida1;
    private javax.swing.JToggleButton tglSalida2;
    private javax.swing.JToggleButton tglSalida3;
    // End of variables declaration//GEN-END:variables
}
