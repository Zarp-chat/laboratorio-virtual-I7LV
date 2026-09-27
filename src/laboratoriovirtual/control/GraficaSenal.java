package laboratoriovirtual.control;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.Timer;
import laboratoriovirtual.gui.PanelSenal;
import laboratoriovirtual.gui.Tema;
import laboratoriovirtual.muestreo.Muestra;
import laboratoriovirtual.muestreo.Muestreador;
import laboratoriovirtual.muestreo.OyenteMuestras;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.data.xy.XYSeriesCollection;

/**
 * Base de las gráficas en vivo de una señal contra el tiempo, dentro de una
 * pestaña PanelSenal. La usa GraficaAnalogica (I7LV-17) y la usará la
 * gráfica digital en escalones (I7LV-20).
 *
 * Por qué una clase base: las dos gráficas solo se diferencian en tres
 * cosas: el valor que toman de cada Muestra, el eje Y y la forma de la línea
 * (continua o en escalones). Todo lo demás es común y queda aquí: el reparto
 * entre hilos, la ventana deslizante de 30 s, el historial, el estilo de Tema
 * y el ChartPanel sin zoom ni menú. La subclase entrega esas tres piezas.
 *
 * Hilos:
 * - muestraRecibida() llega desde el hilo del Muestreador. Solo guarda el
 *   punto en SerieEnVivo (una cola segura entre hilos); no toca JFreeChart
 *   ni Swing.
 * - Un javax.swing.Timer, que corre en el hilo de Swing, pasa cada 50 ms los
 *   puntos pendientes a la serie y redibuja la gráfica una sola vez. Con el
 *   muestreo mínimo (10 ms, 100 muestras por segundo) la gráfica se redibuja
 *   20 veces por segundo y no 100, y el hilo de Swing queda libre para
 *   atender los clics.
 */
public abstract class GraficaSenal implements OyenteMuestras {

    /** Ancho de la ventana visible del eje X, en segundos. */
    public static final double VENTANA_VISIBLE_S = 30.0;

    /** Cada cuánto se pasan los puntos a la gráfica y se redibuja, en milisegundos. */
    private static final int PERIODO_REFRESCO_MS = 50;

    private final int canal;
    private final SerieEnVivo datos = new SerieEnVivo(VENTANA_VISIBLE_S);
    private final NumberAxis ejeTiempo;
    private final JFreeChart grafica;

    /**
     * Crea la gráfica, la pone en el centro de panel.getPanelGrafica(),
     * arranca el Timer de refresco y se registra como oyente del muestreador.
     * Se llama desde el hilo de Swing.
     *
     * @param muestreador de donde llegan las muestras
     * @param panel       pestaña donde va la gráfica
     * @param canal       canal a graficar: posición en el selector de la pestaña
     * @param ejeValor    eje Y, con su rótulo y su rango; aquí se le aplica el estilo
     * @param linea       renderer que decide cómo se dibuja la señal; aquí se le
     *                    aplican el color y el grosor
     */
    protected GraficaSenal(Muestreador muestreador, PanelSenal panel, int canal,
                           NumberAxis ejeValor, XYItemRenderer linea) {
        JComboBox<String> selector = panel.getComboCanal();
        if (canal < 0 || canal >= selector.getItemCount()) {
            throw new IllegalArgumentException("No existe el canal " + canal
                    + " en esta pestaña");
        }
        this.canal = canal;

        // El nombre del canal ("A0", "D2"...) se toma del selector de la
        // pestaña, así la gráfica y el selector siempre dicen lo mismo.
        String titulo = "Canal " + selector.getItemAt(canal);

        ejeTiempo = new NumberAxis("Tiempo (s)");
        // Rango fijo que refrescar() mueve a mano. Al comenzar va de 0 a 30 s.
        ejeTiempo.setRange(0.0, VENTANA_VISIBLE_S);

        grafica = crearGrafica(titulo, ejeValor, linea);
        panel.getPanelGrafica().add(crearPanelGrafica(grafica), BorderLayout.CENTER);

        new Timer(PERIODO_REFRESCO_MS, e -> refrescar()).start();

        // Al final, con todo listo: desde aquí pueden empezar a llegar muestras.
        muestreador.agregarOyente(this);
    }

    /**
     * Toma de la muestra el valor que se grafica. Se llama desde el hilo del
     * Muestreador, así que no debe tocar Swing. Puede llamarse antes de que
     * termine el constructor de la subclase (el oyente se registra en el de
     * esta clase), así que no debe usar campos propios de la subclase.
     *
     * @param muestra lectura completa de todas las entradas
     * @param canal   canal que muestra esta gráfica
     * @return valor del canal, en las unidades del eje Y
     */
    protected abstract double valorDe(Muestra muestra, int canal);

    /**
     * Copia inmutable de todos los puntos (tiempo, valor) recibidos desde el
     * último Iniciar, aunque ya no se dibujen. Pensado para guardar la señal
     * en un archivo (Sprint 3). Seguro entre hilos: ver
     * {@link SerieEnVivo#getHistorial()}.
     */
    public List<SerieEnVivo.Punto> getHistorial() {
        return datos.getHistorial();
    }

    // ===================== Avisos del Muestreador =====================
    // Llegan desde el hilo de muestreo, NO desde Swing.

    @Override
    public void muestraRecibida(Muestra muestra) {
        datos.agregar(muestra.getTiempo(), valorDe(muestra, canal));
    }

    // errorEnFuente() no se sobrescribe: si la fuente falla, la gráfica
    // conserva lo que ya dibujó. ControlMuestreo es quien avisa al usuario.

    // ===================== Hilo de Swing =====================

    /** Lo llama el Timer cada 50 ms, en el hilo de Swing. */
    private void refrescar() {
        if (!datos.hayPendientes()) {
            return; // No llegó nada nuevo, por ejemplo con el muestreo detenido
        }
        // La serie avisa una sola vez, pero el eje X también cambia y avisaría
        // por su lado. Con los avisos de la gráfica apagados mientras tanto,
        // los dos cambios llegan al ChartPanel como uno solo: un redibujo.
        grafica.setNotify(false);
        try {
            datos.procesarPendientes();
            ejeTiempo.setRange(datos.getInicioVentana(), datos.getFinVentana());
        } finally {
            grafica.setNotify(true);
        }
    }

    // ===================== Construcción de la gráfica =====================
    // Las piezas son las de PruebaJFreeChart (I7LV-15), donde se explican.

    private JFreeChart crearGrafica(String titulo, NumberAxis ejeValor,
                                    XYItemRenderer linea) {
        // Ejes: línea, marcas y textos en negro
        for (NumberAxis eje : new NumberAxis[]{ejeTiempo, ejeValor}) {
            eje.setAxisLinePaint(Tema.NEGRO);
            eje.setTickMarkPaint(Tema.NEGRO);
            eje.setLabelPaint(Tema.NEGRO);
            eje.setTickLabelPaint(Tema.NEGRO);
            eje.setLabelFont(Tema.FUENTE_TEXTO_NEGRITA);
            eje.setTickLabelFont(Tema.FUENTE_NOTA);
        }

        // La señal (serie 0): línea roja de 2 px
        linea.setSeriesPaint(0, Tema.ROJO);
        linea.setSeriesStroke(0, new BasicStroke(2.0f));

        XYSeriesCollection dataset = new XYSeriesCollection(datos.getSerie());
        XYPlot areaDibujo = new XYPlot(dataset, ejeTiempo, ejeValor, linea);
        areaDibujo.setBackgroundPaint(Tema.BLANCO);
        areaDibujo.setOutlinePaint(Tema.GRIS_BORDE);
        BasicStroke lineaCuadricula = new BasicStroke(1.0f);
        areaDibujo.setDomainGridlinePaint(Tema.GRIS_BORDE);
        areaDibujo.setDomainGridlineStroke(lineaCuadricula);
        areaDibujo.setRangeGridlinePaint(Tema.GRIS_BORDE);
        areaDibujo.setRangeGridlineStroke(lineaCuadricula);

        // Título pequeño con el nombre del canal y sin leyenda (una sola señal)
        JFreeChart nueva = new JFreeChart(titulo, Tema.FUENTE_TEXTO_NEGRITA,
                areaDibujo, false);
        nueva.getTitle().setPaint(Tema.NEGRO);
        nueva.setBackgroundPaint(Tema.BLANCO);
        // Más margen a la derecha para que el último número del eje X no
        // quede cortado contra el borde
        nueva.setPadding(new RectangleInsets(8, 8, 8, 20));
        return nueva;
    }

    private static ChartPanel crearPanelGrafica(JFreeChart grafica) {
        ChartPanel panelGrafica = new ChartPanel(grafica);

        // Sin zoom con el ratón ni menú emergente (clic derecho), porque
        // rompen la ventana deslizante:
        // - El zoom cambia el rango de los ejes, pero refrescar() vuelve a
        //   fijar el eje X en la ventana de 30 s cada 50 ms, así que el zoom
        //   se desharía solo. Y "alejar" o "restaurar" encienden el rango
        //   automático: el eje Y dejaría de estar fijo.
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
