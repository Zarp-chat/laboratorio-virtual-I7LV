package laboratoriovirtual.gui;

import javax.swing.JButton;
import javax.swing.JLabel;

/**
 * Franja inferior fija de la ventana: botones para iniciar y detener el
 * muestreo y un texto con su estado.
 *
 * El texto ocupa todo el ancho que dejan los botones (centro del
 * BorderLayout) y va alineado a la derecha. Así su ancho depende solo del de
 * la ventana: no cambia cuando el texto crece, por ejemplo con el contador
 * de muestras, y no mueve nada.
 *
 * Diseñada con el editor visual de NetBeans (BarraEstado.form). No tiene
 * lógica: el controlador correspondiente usa los métodos get.
 */
public class BarraEstado extends javax.swing.JPanel {

    /**
     * Crea la barra de estado.
     */
    public BarraEstado() {
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

        panelBotones = new javax.swing.JPanel();
        btnIniciar = new javax.swing.JButton();
        btnDetener = new javax.swing.JButton();
        lblEstado = new javax.swing.JLabel();

        setBackground(new java.awt.Color(255, 255, 255));
        setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createMatteBorder(1, 0, 0, 0, new java.awt.Color(0, 0, 0)), javax.swing.BorderFactory.createEmptyBorder(6, 12, 6, 12)));
        setLayout(new java.awt.BorderLayout());

        panelBotones.setBackground(new java.awt.Color(255, 255, 255));
        panelBotones.setLayout(new java.awt.GridLayout(1, 2, 8, 0));

        btnIniciar.setFont(new java.awt.Font("SansSerif", 1, 12)); // NOI18N
        btnIniciar.setText("Iniciar");
        panelBotones.add(btnIniciar);

        btnDetener.setFont(new java.awt.Font("SansSerif", 1, 12)); // NOI18N
        btnDetener.setText("Detener");
        panelBotones.add(btnDetener);

        add(panelBotones, java.awt.BorderLayout.WEST);

        lblEstado.setFont(new java.awt.Font("SansSerif", 0, 13)); // NOI18N
        lblEstado.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        lblEstado.setText("Detenido · cada 500 ms");
        add(lblEstado, java.awt.BorderLayout.CENTER);
    }// </editor-fold>//GEN-END:initComponents

    /** Botón que inicia el muestreo. */
    public JButton getBtnIniciar() {
        return btnIniciar;
    }

    /** Botón que detiene el muestreo. */
    public JButton getBtnDetener() {
        return btnDetener;
    }

    /** Texto con el estado del muestreo y el tiempo de muestreo actual. */
    public JLabel getLblEstado() {
        return lblEstado;
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnDetener;
    private javax.swing.JButton btnIniciar;
    private javax.swing.JLabel lblEstado;
    private javax.swing.JPanel panelBotones;
    // End of variables declaration//GEN-END:variables
}
