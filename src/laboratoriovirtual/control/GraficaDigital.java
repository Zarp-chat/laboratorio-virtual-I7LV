package laboratoriovirtual.control;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.Timer;
import laboratoriovirtual.datos.FuenteDeDatos;
import laboratoriovirtual.gui.PanelSenal;
import laboratoriovirtual.gui.Tema;
import laboratoriovirtual.muestreo.Muestra;
import laboratoriovirtual.muestreo.Muestreador;
import laboratoriovirtual.muestreo.OyenteMuestras;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYStepRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
 * Gráfica en vivo de las señales digitales (I7LV-20), en la pestaña "Señal
 * digital": un diagrama de tiempos al estilo de Quartus.
 *
 * Para qué: en el Laboratorio 2 las 4 señales vendrán de un DIP-switch leído
 * por un microcontrolador, en 1 byte de cada trama. Esta vista sirve para
 * depurar eso: ver que cada bit corresponde a su interruptor, que el orden de
 * los bits es correcto y cómo afecta la frecuencia de muestreo a los flancos.
 *
 * Cinco carriles apilados que comparten el eje X "Tiempo (s)", de arriba abajo:
 * - D0, D1, D2 y D3: onda cuadrada en escalones, 0 abajo y 1 arriba. La señal
 *   seleccionada va en rojo y con trazo más grueso; las demás, en gris oscuro.
 *   El nombre del carril, a la izquierda, va en rojo en la seleccionada y en
 *   negro en las demás (I7LV-19).
 * - Valor: los 4 bits combinados como un dígito hexadecimal, estilo bus
 *   (RendererBus). D3 es el bit más significativo: D3 = 1, D2 = 0, D1 = 1 y
 *   D0 = 0 es "A".
 * La ventana de 30 s es la misma de la gráfica analógica, y el título dice
 * cuál es la señal seleccionada ("Seleccionada: D0"). La elige el selector
 * de la pestaña, conectado en Main con ControlSeleccion (I7LV-19).
 *
 * Por qué no hereda de GraficaSenal: esa clase es para una sola señal, y su
 * cambio de canal vacía la gráfica y el historial. Aquí se registran siempre
 * los cuatro canales y cambiar la selección solo cambia el resaltado. Heredar
 * obligaría a anular la mitad de la clase base. Se reutilizan sus piezas:
 * la ventana de 30 s y el periodo del Timer (sus constantes), el tipo de
 * punto del historial (SerieEnVivo.Punto), el estilo y el ChartPanel sin
 * zoom (EstiloGrafica) y la interfaz CanalSeleccionable, con la que
 * ControlSeleccion la conecta con el selector de la pestaña (I7LV-19).
 *
 * Hilos: el mismo esquema de GraficaSenal.
 * - muestraRecibida() llega desde el hilo del Muestreador. Combina los bits
 *   en un número y lo deja en la cola, con el candado de
 *   SenalesDigitalesEnVivo: trabajo trivial, sin tocar JFreeChart ni Swing.
 * - Un javax.swing.Timer de 50 ms, en el hilo de Swing, pasa las lecturas
 *   pendientes a los cinco carriles y redibuja la gráfica una sola vez.
 * - cambiarCanal() se llama desde el hilo de Swing. Solo cambia colores (de
 *   las líneas y de los nombres), trazos y título, que únicamente se tocan
 *   en ese hilo; no toca los datos, así que
 *   no necesita el candado y no puede mezclar muestras de nada.
 *
 * Es final: así se registra como oyente al final del constructor sin que una
 * subclase pueda quedar a medio construir cuando llegue la primera muestra.
 */
public final class GraficaDigital implements OyenteMuestras, CanalSeleccionable {

    /** Cantidad de señales digitales: los bits de cada lectura. */
    private static final int CANALES = FuenteDeDatos.NUM_DIGITALES_ENTRADA;

    /** Nombre del carril con los bits combinados. */
    private static final String NOMBRE_VALOR = "Valor";

    /** Margen del eje Y de cada carril: va de -0,3 a 1,3, así el 0 y el 1 no tocan los bordes. */
    private static final double MARGEN_Y = 0.3;

    /** Espacio vertical entre carriles, en píxeles. */
    private static final double ESPACIO_ENTRE_CARRILES = 6.0;

    /** Trazo de la señal seleccionada. */
    private static final BasicStroke TRAZO_SELECCIONADA = new BasicStroke(2.5f);

    /** Trazo de las demás señales. */
    private static final BasicStroke TRAZO_NORMAL = new BasicStroke(1.2f);

    // Nombres de los canales ("D0"...), copiados del selector de la pestaña,
    // como en GraficaSenal: los carriles, el título y el selector siempre
    // dicen lo mismo.
    private final List<String> nombresCanales;

    private final SenalesDigitalesEnVivo datos =
            new SenalesDigitalesEnVivo(CANALES, GraficaSenal.VENTANA_VISIBLE_S);
    private final NumberAxis ejeTiempo;

    // Renderer de cada carril de señal, para cambiar su color y su trazo
    private final XYItemRenderer[] escalones = new XYItemRenderer[CANALES];

    // Eje Y de cada carril de señal, para cambiar el color de su nombre
    private final ValueAxis[] ejesCarriles = new ValueAxis[CANALES];

    private final JFreeChart grafica;

    // Señal seleccionada (resaltada). Solo se cambia en el hilo de Swing;
    // es volatile para que getCanal() la lea al día desde cualquier hilo.
    private volatile int canal;

    /**
     * Crea la gráfica en el centro de panel.getPanelGrafica(), arranca el
     * Timer de refresco y se registra como oyente del muestreador. Se llama
     * desde el hilo de Swing.
     *
     * @param muestreador de donde llegan las muestras
     * @param panel       pestaña "Señal digital"; su selector debe tener un
     *                    nombre por cada señal digital (D0 a D3)
     * @param canal       señal seleccionada al comenzar (0 es D0)
     * @throws IllegalArgumentException si el selector no tiene 4 canales o
     *                                  si el canal no existe
     */
    public GraficaDigital(Muestreador muestreador, PanelSenal panel, int canal) {
        JComboBox<String> selector = panel.getComboCanal();
        // Si no coincidieran, el error saltaría después, en el hilo del
        // Muestreador, y lo detendría sin aviso
        if (selector.getItemCount() != CANALES) {
            throw new IllegalArgumentException("El selector de la pestaña debe tener "
                    + CANALES + " canales y tiene " + selector.getItemCount());
        }
        List<String> nombres = new ArrayList<>();
        for (int i = 0; i < CANALES; i++) {
            nombres.add(selector.getItemAt(i));
        }
        nombresCanales = List.copyOf(nombres);

        validarCanal(canal);
        this.canal = canal;

        ejeTiempo = new NumberAxis("Tiempo (s)");
        // Rango fijo que refrescar() mueve a mano. Al comenzar va de 0 a 30 s.
        ejeTiempo.setRange(0.0, GraficaSenal.VENTANA_VISIBLE_S);
        EstiloGrafica.aplicarAEje(ejeTiempo);

        // Un XYPlot por carril, apilados sobre el mismo eje X
        CombinedDomainXYPlot areaDibujo = new CombinedDomainXYPlot(ejeTiempo);
        areaDibujo.setGap(ESPACIO_ENTRE_CARRILES);
        for (int i = 0; i < CANALES; i++) {
            escalones[i] = new XYStepRenderer();
            XYPlot carril = crearCarril(nombresCanales.get(i), datos.getSerieCanal(i), escalones[i]);
            ejesCarriles[i] = carril.getRangeAxis();
            resaltar(i, i == canal);
            areaDibujo.add(carril);
        }
        areaDibujo.add(crearCarril(NOMBRE_VALOR, datos.getSerieValor(), new RendererBus()));

        grafica = EstiloGrafica.crearGrafica(titulo(canal), areaDibujo);
        panel.getPanelGrafica().add(EstiloGrafica.crearPanelGrafica(grafica),
                BorderLayout.CENTER);

        new Timer(GraficaSenal.PERIODO_REFRESCO_MS, e -> refrescar()).start();

        // Al final, con todo listo: desde aquí pueden empezar a llegar muestras.
        muestreador.agregarOyente(this);
    }

    /** Señal seleccionada: su posición en el selector de la pestaña (0 es D0). */
    @Override
    public int getCanal() {
        return canal;
    }

    /**
     * Copia inmutable del historial de la señal seleccionada: un punto
     * (tiempo, 0 o 1) por cada muestra desde el último Iniciar, aunque ya no
     * se dibuje. Pensado para guardar la señal en un archivo (Sprint 3).
     *
     * Se llama desde el hilo de Swing (por ejemplo, desde el botón "Guardar
     * esta señal…"), en la misma tarea en que se lea getCanal() para saber de
     * qué canal es. Es la regla de I7LV-16: la selección solo cambia en el
     * hilo de Swing, así que entre las dos llamadas no puede cambiar y el
     * historial es del canal que dijo getCanal(). Desde otro hilo la copia
     * sale igual completa y de un solo canal, pero la selección podría
     * cambiar entre una llamada y otra.
     *
     * Cambiar la selección no borra nada: los cuatro canales se registran
     * siempre, cada uno con su historial completo.
     */
    public List<SerieEnVivo.Punto> getHistorial() {
        return datos.getHistorial(canal);
    }

    // ===================== Avisos del Muestreador =====================
    // Llegan desde el hilo de muestreo, NO desde Swing.

    @Override
    public void muestraRecibida(Muestra muestra) {
        datos.agregar(muestra.getTiempo(), combinar(muestra));
    }

    // errorEnFuente() no se sobrescribe: si la fuente falla, la gráfica
    // conserva lo que ya dibujó. ControlMuestreo es quien avisa al usuario.

    // ===================== Hilo de Swing =====================

    /**
     * Cambia la señal seleccionada: la nueva pasa a rojo y con trazo grueso,
     * con su nombre en rojo; la anterior vuelve a gris oscuro, con su nombre
     * en negro; y el título pasa a ser, por ejemplo, "Seleccionada: D2". NO
     * borra la gráfica ni los historiales: los cuatro canales se siguen
     * registrando igual. Elegir la que ya está seleccionada no hace nada.
     *
     * Lo llama ControlSeleccion cada vez que se elige otra señal en el
     * selector de la pestaña (I7LV-19). Se llama desde el hilo de Swing.
     *
     * @param nuevoCanal posición de la señal en el selector de la pestaña
     * @throws IllegalArgumentException si el canal no existe; en ese caso la
     *                                  gráfica sigue igual
     */
    @Override
    public void cambiarCanal(int nuevoCanal) {
        validarCanal(nuevoCanal);
        int anterior = canal;
        if (nuevoCanal == anterior) {
            return;
        }
        // Con los avisos de la gráfica apagados, todos los cambios (colores,
        // trazos, nombres y título) llegan al ChartPanel como un solo redibujo
        grafica.setNotify(false);
        try {
            resaltar(anterior, false);
            resaltar(nuevoCanal, true);
            grafica.getTitle().setText(titulo(nuevoCanal));
            canal = nuevoCanal;
        } finally {
            grafica.setNotify(true);
        }
    }

    /** Lo llama el Timer cada 50 ms, en el hilo de Swing. */
    private void refrescar() {
        if (!datos.hayPendientes()) {
            return; // No llegó nada nuevo, por ejemplo con el muestreo detenido
        }
        // Cambian las cinco series y el eje X. Con los avisos de la gráfica
        // apagados mientras tanto, todo llega al ChartPanel como un solo
        // redibujo.
        grafica.setNotify(false);
        try {
            datos.procesarPendientes();
            ejeTiempo.setRange(datos.getInicioVentana(), datos.getFinVentana());
        } finally {
            grafica.setNotify(true);
        }
    }

    // ===================== Canales =====================

    /**
     * Combina los estados de las señales en un número: la señal i es el bit
     * i, así que D0 es el bit menos significativo y D3 el más significativo.
     */
    private static int combinar(Muestra muestra) {
        int bits = 0;
        for (int i = 0; i < CANALES; i++) {
            if (muestra.getDigital(i)) {
                bits |= 1 << i;
            }
        }
        return bits;
    }

    private void validarCanal(int canal) {
        if (canal < 0 || canal >= CANALES) {
            throw new IllegalArgumentException("No existe el canal " + canal
                    + " en esta pestaña (van del 0 al " + (CANALES - 1) + ")");
        }
    }

    /** Título de la gráfica, por ejemplo "Seleccionada: D0". */
    private String titulo(int canal) {
        return "Seleccionada: " + nombresCanales.get(canal);
    }

    /**
     * Resaltado del carril de una señal. La seleccionada: línea roja y
     * gruesa, y su nombre en rojo. Las demás: línea gris oscuro y delgada, y
     * su nombre en negro, como el resto de los textos de la gráfica.
     */
    private void resaltar(int canal, boolean seleccionada) {
        escalones[canal].setSeriesPaint(0, seleccionada ? Tema.ROJO : Tema.GRIS_OSCURO);
        escalones[canal].setSeriesStroke(0, seleccionada ? TRAZO_SELECCIONADA : TRAZO_NORMAL);
        // El nombre es el rótulo del eje Y del carril, y el eje se dibuja
        // aunque no haya datos: se ve también con la gráfica vacía
        ejesCarriles[canal].setLabelPaint(seleccionada ? Tema.ROJO : Tema.NEGRO);
    }

    // ===================== Construcción de la gráfica =====================

    /**
     * Un carril: su propio eje Y de -0,3 a 1,3, sin números, con el nombre
     * del carril como rótulo, y la cuadrícula vertical del tiempo.
     */
    private static XYPlot crearCarril(String nombre, XYSeries serie, XYItemRenderer renderer) {
        NumberAxis eje = new NumberAxis(nombre);
        eje.setRange(-MARGEN_Y, 1.0 + MARGEN_Y);
        EstiloGrafica.aplicarAEje(eje);
        // Solo el nombre, horizontal (el rótulo de un eje vertical va girado
        // por defecto) y sin números ni marcas
        eje.setLabelAngle(Math.PI / 2.0);
        eje.setTickLabelsVisible(false);
        eje.setTickMarksVisible(false);

        // Sin eje X propio: CombinedDomainXYPlot les da a todos el mismo
        XYPlot carril = new XYPlot(new XYSeriesCollection(serie), null, eje, renderer);
        EstiloGrafica.aplicarAAreaDibujo(carril);
        carril.setRangeGridlinesVisible(false);
        return carril;
    }
}
