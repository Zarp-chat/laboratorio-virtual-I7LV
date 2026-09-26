package laboratoriovirtual;

import java.awt.BasicStroke;
import java.awt.Dimension;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import laboratoriovirtual.gui.Tema;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
 * Prueba aislada de la tarea I7LV-15: confirma que JFreeChart quedó bien
 * agregado al proyecto dibujando una senoidal de ejemplo contra el tiempo.
 *
 * No usa la fuente de datos ni el muestreador: los puntos se calculan aquí.
 * Las tareas I7LV-17 e I7LV-20 parten de esta prueba, por eso cada pieza de
 * JFreeChart se arma por separado y se explica en los comentarios.
 *
 * Cómo encajan las piezas:
 *   XYSeries (puntos) → XYSeriesCollection (dataset) → XYPlot (área de dibujo,
 *   con sus ejes y su renderer) → JFreeChart (gráfica completa) → ChartPanel
 *   (componente Swing que se agrega a la ventana).
 */
public class PruebaJFreeChart {

    /** Duración de la senoidal de ejemplo, en segundos. */
    private static final double DURACION_S = 10.0;

    /** Separación entre puntos, en segundos: 1001 puntos en 10 s. */
    private static final double PASO_S = 0.01;

    /** Frecuencia de la senoidal: 0,5 Hz, es decir, 5 ciclos en 10 s. */
    private static final double FRECUENCIA_HZ = 0.5;

    /** Límites del eje Y, en voltios: el rango de las entradas analógicas. */
    private static final double VOLTAJE_MIN = 0.0;
    private static final double VOLTAJE_MAX = 5.0;

    public static void main(String[] args) {
        // Todo lo que toca Swing (y JFreeChart dibuja sobre Swing) se crea
        // en el hilo de Swing.
        SwingUtilities.invokeLater(() -> {
            Tema.aplicar();

            JFreeChart grafica = crearGrafica();

            // ChartPanel: el componente Swing (un JPanel) que muestra la
            // gráfica y la vuelve a dibujar sola cada vez que cambian los
            // datos. Trae incluidos el zoom con el ratón (arrastrar un
            // rectángulo) y un menú con clic derecho para copiar, guardar
            // como PNG, imprimir y ajustar el zoom.
            ChartPanel panelGrafica = new ChartPanel(grafica);
            panelGrafica.setPreferredSize(new Dimension(800, 450));

            JFrame ventana = new JFrame("Prueba de JFreeChart");
            ventana.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            ventana.add(panelGrafica);
            ventana.pack();
            ventana.setLocationRelativeTo(null);
            ventana.setVisible(true);
        });
    }

    /**
     * Arma la gráfica XY de línea con los datos de ejemplo y el estilo
     * institucional.
     */
    private static JFreeChart crearGrafica() {
        // --- Serie ------------------------------------------------------
        // XYSeries: la lista de puntos (x, y) de UNA señal, con un nombre
        // que aparece en la leyenda. Aquí x es el tiempo en segundos e y el
        // voltaje. Los dos "false" dicen que no hace falta ordenar los puntos
        // (siempre llegan en orden de tiempo) y que no se permiten dos puntos
        // con el mismo tiempo.
        //
        // Para datos en vivo (I7LV-17 e I7LV-20):
        // - Cada add() avisa a la gráfica y la redibuja, así que agregar
        //   puntos desde un OyenteMuestras debe ir dentro de
        //   SwingUtilities.invokeLater (el Muestreador avisa desde su hilo).
        // - setMaximumItemCount(n) descarta los puntos más viejos al pasar
        //   de n y deja una ventana de tiempo que se desplaza.
        XYSeries senoidal = new XYSeries("Senoidal de ejemplo", false, false);
        int cantidadPuntos = (int) Math.round(DURACION_S / PASO_S);
        for (int i = 0; i <= cantidadPuntos; i++) {
            double t = i * PASO_S;
            // Senoidal centrada en 2,5 V con amplitud de 2,5 V: va de 0 a 5 V.
            double v = 2.5 + 2.5 * Math.sin(2 * Math.PI * FRECUENCIA_HZ * t);
            senoidal.add(t, v);
        }

        // --- Dataset ----------------------------------------------------
        // XYSeriesCollection: el "dataset", es decir, el conjunto de series
        // que se dibujan en la misma gráfica. Cada serie tiene un índice
        // (0, 1, 2...) en el orden en que se agrega; el renderer usa ese
        // índice para saber de qué color pintar cada una.
        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(senoidal);

        // --- Ejes -------------------------------------------------------
        // NumberAxis: un eje numérico con su título, sus marcas y sus números.
        // Eje X (en JFreeChart se llama "domain axis"): el tiempo.
        NumberAxis ejeTiempo = new NumberAxis("Tiempo (s)");
        // El rango del eje X sigue siendo automático (se ajusta a los datos),
        // pero sin el margen del 5 % que JFreeChart deja por defecto a cada
        // lado: así la curva empieza y termina justo en los bordes.
        ejeTiempo.setLowerMargin(0.0);
        ejeTiempo.setUpperMargin(0.0);

        // Eje Y (en JFreeChart se llama "range axis"): el voltaje.
        NumberAxis ejeVoltaje = new NumberAxis("Voltaje (V)");
        // setRange fija el eje entre 0 y 5 V y apaga el rango automático:
        // la escala no salta aunque la señal no llegue a los extremos.
        ejeVoltaje.setRange(VOLTAJE_MIN, VOLTAJE_MAX);

        // Estilo de los dos ejes: línea, marcas y textos en negro.
        for (NumberAxis eje : new NumberAxis[]{ejeTiempo, ejeVoltaje}) {
            eje.setAxisLinePaint(Tema.NEGRO);
            eje.setTickMarkPaint(Tema.NEGRO);
            eje.setLabelPaint(Tema.NEGRO);
            eje.setTickLabelPaint(Tema.NEGRO);
            eje.setLabelFont(Tema.FUENTE_TEXTO_NEGRITA);
            eje.setTickLabelFont(Tema.FUENTE_NOTA);
        }

        // --- Renderer ---------------------------------------------------
        // El renderer decide CÓMO se dibuja cada serie: con líneas, con
        // puntos, con barras... XYLineAndShapeRenderer(true, false) dibuja
        // líneas entre puntos y ninguna figura sobre cada punto.
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        // Color y grosor de la serie 0 (la primera del dataset).
        renderer.setSeriesPaint(0, Tema.ROJO);
        renderer.setSeriesStroke(0, new BasicStroke(2.0f));

        // --- Área de dibujo (plot) --------------------------------------
        // XYPlot: el área donde se dibujan los datos. Junta el dataset, los
        // dos ejes y el renderer, y maneja el fondo y la cuadrícula.
        XYPlot areaDibujo = new XYPlot(dataset, ejeTiempo, ejeVoltaje, renderer);
        areaDibujo.setBackgroundPaint(Tema.BLANCO);
        areaDibujo.setOutlinePaint(Tema.GRIS_BORDE);
        // Cuadrícula en gris claro, con línea continua y delgada. Las líneas
        // verticales son las del eje X (domain) y las horizontales, las del
        // eje Y (range).
        BasicStroke lineaCuadricula = new BasicStroke(1.0f);
        areaDibujo.setDomainGridlinePaint(Tema.GRIS_BORDE);
        areaDibujo.setDomainGridlineStroke(lineaCuadricula);
        areaDibujo.setRangeGridlinePaint(Tema.GRIS_BORDE);
        areaDibujo.setRangeGridlineStroke(lineaCuadricula);

        // --- Gráfica ----------------------------------------------------
        // JFreeChart: la gráfica completa. Envuelve el plot y le agrega el
        // título, la leyenda y el fondo general. El último parámetro indica
        // si se crea la leyenda; con una sola señal no hace falta.
        JFreeChart grafica = new JFreeChart("Senoidal de ejemplo (0 a 5 V)",
                Tema.FUENTE_TEXTO_NEGRITA, areaDibujo, false);
        grafica.getTitle().setPaint(Tema.NEGRO);
        grafica.setBackgroundPaint(Tema.BLANCO);
        // Margen interno (arriba, izquierda, abajo, derecha) en píxeles. El
        // de la derecha es mayor para que el último número del eje X
        // ("10,0") no quede cortado contra el borde.
        grafica.setPadding(new RectangleInsets(8, 8, 8, 20));

        return grafica;
    }
}
