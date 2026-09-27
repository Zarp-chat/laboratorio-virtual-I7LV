package laboratoriovirtual.control;

import java.awt.BasicStroke;
import laboratoriovirtual.gui.Tema;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.Axis;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.ui.RectangleInsets;

/**
 * Estilo de Tema y ChartPanel comunes a las gráficas en vivo: la analógica
 * (GraficaSenal) y la digital (GraficaDigital, I7LV-20). Así las dos
 * pestañas se ven igual y se comportan igual con el ratón.
 *
 * Estaba dentro de GraficaSenal (I7LV-17) y se sacó aquí cuando la gráfica
 * digital resultó ser una clase aparte. Las piezas son las de
 * PruebaJFreeChart (I7LV-15), donde se explican.
 *
 * Solo la usan las clases de este paquete.
 */
final class EstiloGrafica {

    private EstiloGrafica() {
        // Solo métodos estáticos
    }

    /** Eje: línea, marcas y textos en negro, con las fuentes de Tema. */
    static void aplicarAEje(Axis eje) {
        eje.setAxisLinePaint(Tema.NEGRO);
        eje.setTickMarkPaint(Tema.NEGRO);
        eje.setLabelPaint(Tema.NEGRO);
        eje.setTickLabelPaint(Tema.NEGRO);
        eje.setLabelFont(Tema.FUENTE_TEXTO_NEGRITA);
        eje.setTickLabelFont(Tema.FUENTE_NOTA);
    }

    /**
     * Área de dibujo: fondo blanco, borde gris y cuadrícula gris de 1 px en
     * las dos direcciones. Quien no quiera alguna de las dos cuadrículas la
     * apaga después.
     */
    static void aplicarAAreaDibujo(XYPlot areaDibujo) {
        areaDibujo.setBackgroundPaint(Tema.BLANCO);
        areaDibujo.setOutlinePaint(Tema.GRIS_BORDE);
        BasicStroke lineaCuadricula = new BasicStroke(1.0f);
        areaDibujo.setDomainGridlinePaint(Tema.GRIS_BORDE);
        areaDibujo.setDomainGridlineStroke(lineaCuadricula);
        areaDibujo.setRangeGridlinePaint(Tema.GRIS_BORDE);
        areaDibujo.setRangeGridlineStroke(lineaCuadricula);
    }

    /** Gráfica con título pequeño en negro, fondo blanco y sin leyenda. */
    static JFreeChart crearGrafica(String titulo, Plot areaDibujo) {
        JFreeChart nueva = new JFreeChart(titulo, Tema.FUENTE_TEXTO_NEGRITA,
                areaDibujo, false);
        nueva.getTitle().setPaint(Tema.NEGRO);
        nueva.setBackgroundPaint(Tema.BLANCO);
        // Más margen a la derecha para que el último número del eje X no
        // quede cortado contra el borde
        nueva.setPadding(new RectangleInsets(8, 8, 8, 20));
        return nueva;
    }

    /** Panel que muestra la gráfica en la pestaña, sin zoom ni menú emergente. */
    static ChartPanel crearPanelGrafica(JFreeChart grafica) {
        ChartPanel panelGrafica = new ChartPanel(grafica);

        // Sin zoom con el ratón ni menú emergente (clic derecho), porque
        // rompen la ventana deslizante:
        // - El zoom cambia el rango de los ejes, pero el Timer de la gráfica
        //   vuelve a fijar el eje X en la ventana de 30 s cada 50 ms, así que
        //   el zoom se desharía solo. Y "alejar" o "restaurar" encienden el
        //   rango automático: el eje Y dejaría de estar fijo.
        // - El menú repite esas opciones de zoom y además trae "Guardar
        //   como", que competiría con el botón "Guardar esta señal…" de la
        //   pestaña (que guarda los datos, no una imagen).
        panelGrafica.setMouseZoomable(false);
        panelGrafica.setMouseWheelEnabled(false);
        panelGrafica.setPopupMenu(null);

        // Por defecto ChartPanel dibuja a lo sumo 1024 × 768 px y, si el
        // panel es más grande, estira la imagen (textos deformados con la
        // ventana maximizada). Sin ese límite se dibuja al tamaño real.
        panelGrafica.setMaximumDrawWidth(Integer.MAX_VALUE);
        panelGrafica.setMaximumDrawHeight(Integer.MAX_VALUE);

        return panelGrafica;
    }
}
