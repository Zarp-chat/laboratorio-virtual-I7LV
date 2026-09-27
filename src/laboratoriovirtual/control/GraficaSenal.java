package laboratoriovirtual.control;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.util.ArrayList;
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
 * entre hilos, la ventana deslizante de 30 s, el historial, el cambio de
 * canal, el estilo de Tema y el ChartPanel sin zoom ni menú. La subclase
 * entrega esas tres piezas.
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
 * - cambiarCanal() (I7LV-16) se llama desde el hilo de Swing, con el
 *   muestreo corriendo o detenido.
 *
 * Cambio de canal sin mezclar canales (I7LV-16). Después de un cambio, ni la
 * gráfica ni el historial pueden tener un solo punto del canal anterior,
 * aunque el Muestreador esté registrando una muestra justo en ese momento.
 * Se garantiza con un único candado, el del objeto datos (SerieEnVivo), que
 * usan las dos operaciones:
 * - muestraRecibida() lee el canal, saca el valor de la muestra y guarda el
 *   punto en el historial y en la cola dentro de un solo
 *   synchronized (datos).
 * - cambiarCanal() cambia el canal y vacía el historial y la cola dentro de
 *   otro synchronized (datos).
 * Dos bloques con el mismo candado nunca corren a la vez, así que cada
 * muestra queda del todo antes o del todo después del cambio. Si queda
 * antes, su punto ya está en el historial y en la cola, y el cambio lo borra.
 * Si queda después, ya leyó el canal nuevo. No hay un tercer caso. Sin el
 * candado sí lo habría: el Muestreador lee el canal viejo, el cambio vacía
 * todo y enseguida el Muestreador guarda el punto viejo.
 * La serie dibujada no necesita el candado: solo recibe puntos de la cola, y
 * solo en el hilo de Swing (el Timer). cambiarCanal() corre en ese mismo
 * hilo, así que el Timer no puede estar a mitad de pasar puntos; cuando
 * cambiarCanal() vacía la serie, la cola ya solo tiene puntos del canal nuevo.
 */
public abstract class GraficaSenal implements OyenteMuestras {

    /** Ancho de la ventana visible del eje X, en segundos. */
    public static final double VENTANA_VISIBLE_S = 30.0;

    /** Cada cuánto se pasan los puntos a la gráfica y se redibuja, en milisegundos. */
    private static final int PERIODO_REFRESCO_MS = 50;

    // Nombres de los canales ("A0", "D2"...), copiados del selector de la
    // pestaña: así la gráfica y el selector siempre dicen lo mismo. Su
    // cantidad es el rango de canales válidos.
    private final List<String> nombresCanales;

    // Canal que se grafica. Lo cambia el hilo de Swing y lo lee el del
    // Muestreador, así que, pasado el constructor (que lo fija antes de
    // registrarse como oyente), solo se usa dentro de synchronized (datos).
    private int canal;

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
     * @param canal       canal con el que arranca: posición en el selector de
     *                    la pestaña
     * @param ejeValor    eje Y, con su rótulo y su rango; aquí se le aplica el estilo
     * @param linea       renderer que decide cómo se dibuja la señal; aquí se le
     *                    aplican el color y el grosor
     * @throws IllegalArgumentException si el canal no existe en el selector
     */
    protected GraficaSenal(Muestreador muestreador, PanelSenal panel, int canal,
                           NumberAxis ejeValor, XYItemRenderer linea) {
        JComboBox<String> selector = panel.getComboCanal();
        List<String> nombres = new ArrayList<>();
        for (int i = 0; i < selector.getItemCount(); i++) {
            nombres.add(selector.getItemAt(i));
        }
        nombresCanales = List.copyOf(nombres);

        validarCanal(canal);
        this.canal = canal;

        ejeTiempo = new NumberAxis("Tiempo (s)");
        // Rango fijo que refrescar() mueve a mano. Al comenzar va de 0 a 30 s.
        ejeTiempo.setRange(0.0, VENTANA_VISIBLE_S);

        grafica = crearGrafica(titulo(canal), ejeValor, linea);
        panel.getPanelGrafica().add(crearPanelGrafica(grafica), BorderLayout.CENTER);

        new Timer(PERIODO_REFRESCO_MS, e -> refrescar()).start();

        // Al final, con todo listo: desde aquí pueden empezar a llegar muestras.
        muestreador.agregarOyente(this);
    }

    /**
     * Toma de la muestra el valor que se grafica. Se llama desde el hilo del
     * Muestreador, así que no debe tocar Swing. Se llama con el candado
     * tomado, así que debe ser rápido y no esperar nada. Puede llamarse
     * antes de que termine el constructor de la subclase (el oyente se
     * registra en el de esta clase), así que no debe usar campos propios de
     * la subclase.
     *
     * @param muestra lectura completa de todas las entradas
     * @param canal   canal que muestra esta gráfica
     * @return valor del canal, en las unidades del eje Y
     */
    protected abstract double valorDe(Muestra muestra, int canal);

    /**
     * Copia inmutable de todos los puntos (tiempo, valor) recibidos desde el
     * último Iniciar o el último cambio de canal, aunque ya no se dibujen.
     * Pensado para guardar la señal en un archivo (Sprint 3). Seguro entre
     * hilos: ver {@link SerieEnVivo#getHistorial()}.
     */
    public List<SerieEnVivo.Punto> getHistorial() {
        return datos.getHistorial();
    }

    /** Canal que se grafica: su posición en el selector de la pestaña. */
    public int getCanal() {
        synchronized (datos) {
            return canal;
        }
    }

    // ===================== Avisos del Muestreador =====================
    // Llegan desde el hilo de muestreo, NO desde Swing.

    @Override
    public void muestraRecibida(Muestra muestra) {
        // Leer el canal, sacar el valor y guardar el punto son un solo paso:
        // cambiarCanal() no puede meterse en medio (ver el comentario de la
        // clase).
        synchronized (datos) {
            datos.agregar(muestra.getTiempo(), valorDe(muestra, canal));
        }
    }

    // errorEnFuente() no se sobrescribe: si la fuente falla, la gráfica
    // conserva lo que ya dibujó. ControlMuestreo es quien avisa al usuario.

    // ===================== Hilo de Swing =====================

    /**
     * Pasa a graficar otro canal (I7LV-16). Vacía la serie dibujada, la cola
     * de puntos pendientes y el historial, y cambia el título ("Canal A3").
     * Desde ese momento solo se registra el canal nuevo. El eje X arranca en
     * el tiempo de su primera muestra, con la misma ventana de 30 s: el
     * tiempo no vuelve a cero, porque lo lleva el Muestreador.
     *
     * Funciona igual con el muestreo corriendo o detenido. Si está detenido,
     * la gráfica queda vacía con el título nuevo hasta el siguiente Iniciar.
     * Elegir el canal que ya se grafica no hace nada.
     *
     * Se llama desde el hilo de Swing.
     *
     * @param nuevoCanal posición del canal en el selector de la pestaña
     * @throws IllegalArgumentException si el canal no existe; en ese caso la
     *                                  gráfica sigue igual, con el canal anterior
     */
    public void cambiarCanal(int nuevoCanal) {
        // Antes de tocar nada: si el canal no existe, todo queda como estaba
        validarCanal(nuevoCanal);

        // Paso 1, con el mismo candado de muestraRecibida(): cambiar el canal
        // y descartar lo recibido del anterior, sin que una muestra se meta
        // en medio.
        synchronized (datos) {
            if (nuevoCanal == canal) {
                return;
            }
            canal = nuevoCanal;
            datos.vaciarRecibidos();
        }

        // Paso 2, ya sin el candado: la serie y el título solo se tocan en
        // este hilo, y así el Muestreador no espera mientras JFreeChart avisa
        // los cambios. Con los avisos de la gráfica apagados mientras tanto,
        // los dos cambios llegan al ChartPanel como un solo redibujo.
        grafica.setNotify(false);
        try {
            datos.vaciarSerie();
            grafica.getTitle().setText(titulo(nuevoCanal));
        } finally {
            grafica.setNotify(true);
        }
    }

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

    // ===================== Canales =====================

    private void validarCanal(int canal) {
        if (canal < 0 || canal >= nombresCanales.size()) {
            throw new IllegalArgumentException("No existe el canal " + canal
                    + " en esta pestaña (van del 0 al "
                    + (nombresCanales.size() - 1) + ")");
        }
    }

    /** Título de la gráfica para un canal, por ejemplo "Canal A3". */
    private String titulo(int canal) {
        return "Canal " + nombresCanales.get(canal);
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
