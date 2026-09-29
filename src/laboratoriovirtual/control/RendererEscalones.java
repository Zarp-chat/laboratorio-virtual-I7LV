package laboratoriovirtual.control;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CrosshairState;
import org.jfree.chart.plot.PlotRenderingInfo;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.AbstractXYItemRenderer;
import org.jfree.chart.renderer.xy.XYItemRendererState;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.data.xy.XYDataset;

/**
 * Renderer de los carriles D0 a D3 de la gráfica digital (I7LV-18): dibuja
 * la señal como una onda en escalones, igual que XYStepRenderer de
 * JFreeChart (que usaba I7LV-20), pero rápido aunque haya miles de cambios.
 *
 * El tramo i va del punto i al i + 1: una línea horizontal a la altura del
 * valor del punto i y, si el valor cambia, una línea vertical en el punto
 * i + 1 (el flanco). El color y el trazo son los de la serie
 * (setSeriesPaint() y setSeriesStroke()), así que GraficaDigital resalta la
 * señal seleccionada igual que antes.
 *
 * Por qué no XYStepRenderer: con la ventana de 1 min a 10 ms y los 4 bits
 * cambiando en cada muestra, cada carril tiene 6000 flancos, varios por
 * columna de píxeles. XYStepRenderer dibuja dos líneas por punto y guarda un
 * área para tooltips por punto; la gráfica tardaba unos 40 ms en dibujarse
 * con la ventana de 1000 px y 55 ms maximizada, más que el ciclo de 50 ms
 * del Timer. Aquí, como en RendererBus, un tramo de menos de 1 px se dibuja
 * solo como la raya vertical de su flanco, y a lo sumo una raya por columna
 * de píxeles: se ve igual (una franja de la altura del carril) y cuesta a lo
 * sumo una línea por columna. Los tramos más anchos se dibujan completos. No
 * se guardan áreas para tooltips: la gráfica no los muestra.
 *
 * Solo sirve con el eje X horizontal, la orientación normal de XYPlot.
 */
public class RendererEscalones extends AbstractXYItemRenderer {

    // JFreeChart hace serializables sus renderers; sin este número el
    // compilador da una advertencia.
    private static final long serialVersionUID = 1L;

    /** Un tramo más angosto que esto se dibuja solo como la raya de su flanco, en píxeles. */
    private static final double ANCHO_MINIMO_PX = 1.0;

    /**
     * Lo que el renderer recuerda mientras dibuja el carril, de un tramo al
     * siguiente: la última columna de píxeles donde dibujó la raya de un
     * flanco. JFreeChart crea uno nuevo en cada dibujo (initialise()).
     */
    private static final class EstadoEscalones extends XYItemRendererState {

        long ultimaColumna = Long.MIN_VALUE;

        EstadoEscalones(PlotRenderingInfo info) {
            super(info);
        }
    }

    /** JFreeChart lo llama al empezar a dibujar el carril. */
    @Override
    public XYItemRendererState initialise(Graphics2D g2, Rectangle2D dataArea,
                                          XYPlot plot, XYDataset dataset,
                                          PlotRenderingInfo info) {
        return new EstadoEscalones(info);
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
        double y0 = rangeAxis.valueToJava2D(dataset.getYValue(series, item), dataArea, bordeY);
        double y1 = rangeAxis.valueToJava2D(dataset.getYValue(series, item + 1), dataArea, bordeY);
        g2.setPaint(getItemPaint(series, item));
        g2.setStroke(getItemStroke(series, item));
        EstadoEscalones estado = (EstadoEscalones) state;

        // Tramo ancho: la línea horizontal completa
        if (x1 - x0 >= ANCHO_MINIMO_PX) {
            state.workingLine.setLine(x0, y0, x1, y0);
            g2.draw(state.workingLine);
        }
        // El flanco al final del tramo, si el valor cambia (el punto final
        // de la serie repite el valor: ahí no hay flanco). En una columna que
        // ya tiene la raya de un flanco no se dibuja otra: todas van de 0 a 1.
        if (y1 != y0) {
            long columna = (long) Math.floor(x1);
            if (x1 - x0 >= ANCHO_MINIMO_PX) {
                estado.ultimaColumna = columna;
                state.workingLine.setLine(x1, y0, x1, y1);
                g2.draw(state.workingLine);
            } else if (columna != estado.ultimaColumna) {
                // Raya de un tramo angosto: centrada en su columna, así una
                // seguidilla de columnas queda como una franja pareja y no
                // rayada por el suavizado
                estado.ultimaColumna = columna;
                double centro = columna + 0.5;
                state.workingLine.setLine(centro, y0, centro, y1);
                g2.draw(state.workingLine);
            }
        }
    }
}
