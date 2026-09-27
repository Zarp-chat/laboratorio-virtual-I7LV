package laboratoriovirtual.control;

import java.awt.BasicStroke;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.util.Locale;
import laboratoriovirtual.gui.Tema;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CrosshairState;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.AbstractXYItemRenderer;
import org.jfree.chart.renderer.xy.XYItemRendererState;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.data.xy.XYDataset;

/**
 * Renderer del carril "Valor" de la gráfica digital (I7LV-20): dibuja una
 * serie de números enteros como un bus del diagrama de tiempos de Quartus.
 * JFreeChart no trae nada parecido.
 *
 * Cada tramo en que el valor no cambia es un hexágono alargado con contorno
 * negro: dos líneas horizontales que se juntan en punta en cada cambio, así
 * que entre dos tramos se ve una X. Dentro va el valor en hexadecimal,
 * centrado en la parte visible del tramo, solo si cabe. El comienzo de los
 * datos y la última lectura no son cambios: ahí el tramo se cierra con un
 * borde vertical.
 *
 * Los tramos angostos se ven solo como transiciones: con menos de 6 px las
 * puntas se acortan y el tramo queda como un rombo entre dos X; con menos de
 * 2 px, es solo la raya vertical del cambio.
 *
 * La serie debe tener un punto donde empieza cada tramo, más un punto final
 * (así son las de SenalesDigitalesEnVivo): el tramo i va del punto i al
 * i + 1 y vale lo que vale el punto i. El valor no decide la altura: todos
 * los tramos van de 0 a 1 en el eje Y del carril, a la misma altura que los
 * escalones de los otros carriles.
 *
 * Solo sirve con el eje X horizontal, la orientación normal de XYPlot.
 */
public class RendererBus extends AbstractXYItemRenderer {

    // JFreeChart hace serializables sus renderers; sin este número el
    // compilador da una advertencia.
    private static final long serialVersionUID = 1L;

    /** Largo horizontal de cada punta, en píxeles: media X de un cambio. */
    private static final double PUNTA_PX = 3.0;

    /** Espacio libre mínimo a cada lado del texto, en píxeles. */
    private static final double MARGEN_TEXTO_PX = 1.5;

    /** Un tramo más angosto que esto se dibuja solo como la raya de su transición, en píxeles. */
    private static final double ANCHO_MINIMO_PX = 2.0;

    /** Trazo del contorno. */
    private static final BasicStroke TRAZO = new BasicStroke(1.2f);

    /**
     * Lo que el renderer recuerda mientras dibuja el carril, de un tramo al
     * siguiente: la última columna de píxeles donde dibujó la raya de un
     * tramo angosto. JFreeChart crea uno nuevo en cada dibujo (initialise()).
     */
    private static final class EstadoBus extends XYItemRendererState {

        long ultimaColumna = Long.MIN_VALUE;

        EstadoBus(PlotRenderingInfo info) {
            super(info);
        }
    }

    /**
     * El valor en hexadecimal, con letras mayúsculas: 10 es "A". Es el texto
     * de cada tramo.
     */
    public static String hexadecimal(int valor) {
        return Integer.toHexString(valor).toUpperCase(Locale.ROOT);
    }

    /** JFreeChart lo llama al empezar a dibujar el carril. */
    @Override
    public XYItemRendererState initialise(Graphics2D g2, Rectangle2D dataArea,
                                          XYPlot plot, XYDataset dataset,
                                          PlotRenderingInfo info) {
        return new EstadoBus(info);
    }

    /**
     * Dibuja el tramo que empieza en el punto item. JFreeChart lo llama una
     * vez por punto, en orden de tiempo, en el hilo de Swing y con el dibujo
     * ya recortado al área del carril.
     */
    @Override
    public void drawItem(Graphics2D g2, XYItemRendererState state,
                         Rectangle2D dataArea, PlotRenderingInfo info, XYPlot plot,
                         ValueAxis domainAxis, ValueAxis rangeAxis, XYDataset dataset,
                         int series, int item, CrosshairState crosshairState, int pass) {
        // El tramo va de este punto al siguiente: el último punto no abre ninguno
        if (item + 1 >= dataset.getItemCount(series)) {
            return;
        }
        RectangleEdge bordeX = plot.getDomainAxisEdge();
        double x0 = domainAxis.valueToJava2D(dataset.getXValue(series, item), dataArea, bordeX);
        double x1 = domainAxis.valueToJava2D(dataset.getXValue(series, item + 1), dataArea, bordeX);
        if (x1 < dataArea.getMinX() || x0 > dataArea.getMaxX()) {
            return; // Fuera de la vista
        }
        RectangleEdge bordeY = plot.getRangeAxisEdge();
        double arriba = rangeAxis.valueToJava2D(1.0, dataArea, bordeY);
        double abajo = rangeAxis.valueToJava2D(0.0, dataArea, bordeY);
        double medio = (arriba + abajo) / 2.0;
        g2.setPaint(Tema.NEGRO);
        g2.setStroke(TRAZO);

        // Tramo angosto: solo la raya vertical de la transición con que
        // empieza (la del final la dibuja el tramo siguiente). A 10 ms, con
        // señales que cambian en casi cada muestra, hay miles de tramos así
        // en 30 s, varios por píxel: se dibuja una sola raya por columna de
        // píxeles. Dibujar cada uno como un hexágono de menos de un píxel se
        // ve igual (una franja negra) y, con los 4 bits cambiando en cada
        // muestra, tardaba unos 27 ms por dibujo: más de la mitad del ciclo
        // de 50 ms del Timer.
        if (x1 - x0 < ANCHO_MINIMO_PX) {
            EstadoBus estadoBus = (EstadoBus) state;
            long columna = (long) Math.floor(x0);
            if (columna != estadoBus.ultimaColumna) {
                estadoBus.ultimaColumna = columna;
                state.workingLine.setLine(x0, arriba, x0, abajo);
                g2.draw(state.workingLine);
            }
            return;
        }

        // Punta donde el valor cambia. En un tramo más angosto que dos puntas,
        // las puntas se acortan: el tramo queda como un rombo y solo se ven
        // las X de los cambios.
        double valor = dataset.getYValue(series, item);
        boolean cambiaAlEmpezar = item > 0 && dataset.getYValue(series, item - 1) != valor;
        boolean cambiaAlTerminar = dataset.getYValue(series, item + 1) != valor;
        double punta = Math.min(PUNTA_PX, (x1 - x0) / 2.0);
        double izquierda = x0 + (cambiaAlEmpezar ? punta : 0.0);
        double derecha = x1 - (cambiaAlTerminar ? punta : 0.0);

        // El hexágono. Sin punta, (x0, medio) queda sobre el borde vertical.
        Path2D contorno = new Path2D.Double();
        contorno.moveTo(x0, medio);
        contorno.lineTo(izquierda, arriba);
        contorno.lineTo(derecha, arriba);
        contorno.lineTo(x1, medio);
        contorno.lineTo(derecha, abajo);
        contorno.lineTo(izquierda, abajo);
        contorno.closePath();
        g2.draw(contorno);

        // El texto, centrado en la parte visible del tramo y solo si cabe.
        // El espacio se mide a la altura del texto, no en las esquinas: el
        // texto va en el centro, donde las puntas ya casi se cerraron. Si se
        // midiera entre las esquinas, en los tramos de una sola muestra a
        // 500 ms (unos 15 px con la ventana de 1000 px) no cabría ningún
        // dígito.
        String texto = hexadecimal((int) valor);
        g2.setFont(Tema.FUENTE_NOTA);
        FontMetrics medidas = g2.getFontMetrics();
        double altoTexto = medidas.getAscent() - medidas.getDescent(); // alto de un dígito
        double cierre = Math.min(1.0, altoTexto / (abajo - arriba)); // 0 en el centro, 1 en las esquinas
        double libreIzquierda = x0 + (cambiaAlEmpezar ? punta * cierre : 0.0);
        double libreDerecha = x1 - (cambiaAlTerminar ? punta * cierre : 0.0);
        double visibleIzquierda = Math.max(libreIzquierda, dataArea.getMinX());
        double visibleDerecha = Math.min(libreDerecha, dataArea.getMaxX());
        int anchoTexto = medidas.stringWidth(texto);
        if (anchoTexto + 2 * MARGEN_TEXTO_PX <= visibleDerecha - visibleIzquierda) {
            double x = (visibleIzquierda + visibleDerecha - anchoTexto) / 2.0;
            double lineaBase = medio + altoTexto / 2.0;
            g2.drawString(texto, (float) x, (float) lineaBase);
        }
    }
}
