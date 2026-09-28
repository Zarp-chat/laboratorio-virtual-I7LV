package laboratoriovirtual.gui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Point2D;

/**
 * LED redondo de unos 20 px que se enciende o se apaga (I7LV-18). Lo usa la
 * pestaña Salidas, uno a la izquierda de cada interruptor.
 *
 * - Encendido: el rojo de Tema, con un brillo arriba a la izquierda y el
 *   borde un poco más oscuro, para que parezca iluminado. El centro queda
 *   exactamente en Tema.ROJO.
 * - Apagado: gris claro (Tema.GRIS_BORDE), sin brillo.
 * - En los dos casos, contorno negro de 1 px.
 *
 * No tiene lógica: lo enciende y lo apaga el controlador de la pestaña con
 * setEncendido(), que lo redibuja solo.
 *
 * Tiene constructor sin parámetros para que el diseñador de NetBeans pueda
 * crearlo y dibujarlo en la vista Design. Extiende JPanel, igual que
 * Encabezado y BarraEstado, para que un .form lo coloque como a ellos en
 * VentanaPrincipal.form: un contenedor con su FlowLayout por defecto, que no
 * genera código. NetBeans 22 trata como contenedor a toda subclase de
 * JComponent sin BeanInfo, y un JComponent no trae layout (null), que en el
 * diseñador sería "Null Layout", prohibido en este proyecto.
 */
public class LedIndicador extends javax.swing.JPanel {

    /** Diámetro del LED, en píxeles: es su tamaño preferido. */
    public static final int DIAMETRO = 20;

    private boolean encendido = false;

    /**
     * Crea el LED apagado. Es transparente: alrededor del círculo se ve el
     * fondo del panel que lo contiene.
     */
    public LedIndicador() {
        setOpaque(false);
        setPreferredSize(new Dimension(DIAMETRO, DIAMETRO));
    }

    /** Si el LED está encendido. */
    public boolean isEncendido() {
        return encendido;
    }

    /**
     * Enciende o apaga el LED y lo redibuja. Se llama en el hilo de Swing.
     *
     * @param encendido true para encenderlo, false para apagarlo
     */
    public void setEncendido(boolean encendido) {
        if (this.encendido != encendido) {
            this.encendido = encendido;
            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        // El círculo más grande que cabe, centrado. Se deja 1 px para el
        // contorno, que se dibuja mitad por dentro y mitad por fuera.
        Insets bordes = getInsets();
        int ancho = getWidth() - bordes.left - bordes.right;
        int alto = getHeight() - bordes.top - bordes.bottom;
        float diametro = Math.min(ancho, alto) - 1f;
        if (diametro <= 0) {
            return;
        }
        float x = bordes.left + (ancho - diametro) / 2f;
        float y = bordes.top + (alto - diametro) / 2f;
        Ellipse2D circulo = new Ellipse2D.Float(x, y, diametro, diametro);

        Graphics2D g2 = (Graphics2D) g.create();
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

            if (encendido) {
                // Rojo de Tema en el centro, que se oscurece hacia el borde
                float radio = diametro / 2f;
                g2.setPaint(new RadialGradientPaint(
                        new Point2D.Float(x + radio, y + radio), radio,
                        new float[] { 0f, 0.55f, 1f },
                        new Color[] { Tema.ROJO, Tema.ROJO, Tema.ROJO.darker() }));
                g2.fill(circulo);

                // Brillo: una mancha blanca y difusa arriba a la izquierda.
                // No llega al centro, que sigue siendo Tema.ROJO.
                float radioBrillo = diametro * 0.25f;
                Point2D.Float centroBrillo = new Point2D.Float(x + diametro * 0.33f, y + diametro * 0.30f);
                g2.setPaint(new RadialGradientPaint(centroBrillo, radioBrillo,
                        new float[] { 0f, 1f },
                        new Color[] { conAlfa(Tema.BLANCO, 200), conAlfa(Tema.BLANCO, 0) }));
                g2.fill(new Ellipse2D.Float(centroBrillo.x - radioBrillo, centroBrillo.y - radioBrillo,
                        2 * radioBrillo, 2 * radioBrillo));
            } else {
                g2.setColor(Tema.GRIS_BORDE);
                g2.fill(circulo);
            }

            g2.setColor(Tema.NEGRO);
            g2.setStroke(new BasicStroke(1f));
            g2.draw(circulo);
        } finally {
            g2.dispose();
        }
    }

    /** El mismo color con otra transparencia (0 transparente, 255 opaco). */
    private static Color conAlfa(Color color, int alfa) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alfa);
    }
}
