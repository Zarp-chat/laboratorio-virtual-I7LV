package laboratoriovirtual.gui;

/**
 * Franja superior fija de la ventana: logo de la Universidad del Valle,
 * título del laboratorio y nombres de los creadores.
 *
 * Diseñada con el editor visual de NetBeans (Encabezado.form). Solo muestra
 * información fija, por eso no ofrece métodos get.
 */
public class Encabezado extends javax.swing.JPanel {

    /**
     * Crea el encabezado.
     */
    public Encabezado() {
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

        lblLogo = new javax.swing.JLabel();
        panelTitulos = new javax.swing.JPanel();
        lblTitulo = new javax.swing.JLabel();
        lblSubtitulo = new javax.swing.JLabel();
        panelCreadores = new javax.swing.JPanel();
        lblCreador1 = new javax.swing.JLabel();
        lblCreador2 = new javax.swing.JLabel();
        lblCreador3 = new javax.swing.JLabel();

        setBackground(new java.awt.Color(204, 0, 0));
        setBorder(javax.swing.BorderFactory.createCompoundBorder(javax.swing.BorderFactory.createMatteBorder(0, 0, 2, 0, new java.awt.Color(0, 0, 0)), javax.swing.BorderFactory.createEmptyBorder(10, 16, 10, 16)));
        setLayout(new java.awt.BorderLayout());

        lblLogo.setIcon(new javax.swing.ImageIcon(getClass().getResource("/laboratoriovirtual/recursos/logo-univalle.png"))); // NOI18N
        add(lblLogo, java.awt.BorderLayout.WEST);

        panelTitulos.setBackground(new java.awt.Color(204, 0, 0));
        panelTitulos.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 16, 0, 16));
        panelTitulos.setLayout(new java.awt.GridLayout(0, 1));

        lblTitulo.setFont(new java.awt.Font("SansSerif", 1, 26)); // NOI18N
        lblTitulo.setForeground(new java.awt.Color(255, 255, 255));
        lblTitulo.setText("LABORATORIO VIRTUAL");
        lblTitulo.setVerticalAlignment(javax.swing.SwingConstants.BOTTOM);
        panelTitulos.add(lblTitulo);

        lblSubtitulo.setFont(new java.awt.Font("SansSerif", 0, 13)); // NOI18N
        lblSubtitulo.setForeground(new java.awt.Color(255, 255, 255));
        lblSubtitulo.setText("Interfaces 710044M · Ingeniería Electrónica · Universidad del Valle");
        lblSubtitulo.setVerticalAlignment(javax.swing.SwingConstants.TOP);
        panelTitulos.add(lblSubtitulo);

        add(panelTitulos, java.awt.BorderLayout.CENTER);

        panelCreadores.setBackground(new java.awt.Color(204, 0, 0));
        panelCreadores.setLayout(new java.awt.GridLayout(0, 1));

        lblCreador1.setFont(new java.awt.Font("SansSerif", 1, 12)); // NOI18N
        lblCreador1.setForeground(new java.awt.Color(255, 255, 255));
        lblCreador1.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        lblCreador1.setText("ESTEBAN SALAZAR");
        panelCreadores.add(lblCreador1);

        lblCreador2.setFont(new java.awt.Font("SansSerif", 1, 12)); // NOI18N
        lblCreador2.setForeground(new java.awt.Color(255, 255, 255));
        lblCreador2.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        lblCreador2.setText("OMAR STEVEN HERNANDEZ SUAREZ");
        panelCreadores.add(lblCreador2);

        lblCreador3.setFont(new java.awt.Font("SansSerif", 1, 12)); // NOI18N
        lblCreador3.setForeground(new java.awt.Color(255, 255, 255));
        lblCreador3.setHorizontalAlignment(javax.swing.SwingConstants.RIGHT);
        lblCreador3.setText("JHON BIRON GUADIR ALPALA");
        panelCreadores.add(lblCreador3);

        add(panelCreadores, java.awt.BorderLayout.EAST);
    }// </editor-fold>//GEN-END:initComponents


    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JLabel lblCreador1;
    private javax.swing.JLabel lblCreador2;
    private javax.swing.JLabel lblCreador3;
    private javax.swing.JLabel lblLogo;
    private javax.swing.JLabel lblSubtitulo;
    private javax.swing.JLabel lblTitulo;
    private javax.swing.JPanel panelCreadores;
    private javax.swing.JPanel panelTitulos;
    // End of variables declaration//GEN-END:variables
}
