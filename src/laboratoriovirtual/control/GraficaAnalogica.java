package laboratoriovirtual.control;

import laboratoriovirtual.almacenamiento.HistorialSenal;
import laboratoriovirtual.gui.PanelSenal;
import laboratoriovirtual.muestreo.Muestra;
import laboratoriovirtual.muestreo.Muestreador;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.Range;

/**
 * Gráfica en vivo de una señal analógica contra el tiempo (I7LV-17), en la
 * pestaña "Señal analógica": línea continua roja y eje Y "Voltaje (V)".
 *
 * El funcionamiento (hilos, ventana visible, historial, cambio de canal y
 * estilo) está en GraficaSenal; aquí solo va lo propio de las señales
 * analógicas. El selector de la pestaña cambia el canal a través de
 * ControlSeleccion (I7LV-16).
 *
 * Escala vertical (I7LV-18), elegida con setEscala() desde el selector
 * "Escala:" (ControlVisualizacion):
 * - "0 a 5 V", la escala al comenzar, y "0 a 3,3 V": límites fijos. Con
 *   "0 a 3,3 V", los valores por encima de 3,3 V quedan fuera del área
 *   visible y la línea se corta en el borde superior; es lo esperado.
 * - "Automática": el eje Y se ajusta a los valores visibles en cada ciclo
 *   del Timer que trae puntos nuevos y al cambiar la ventana, dentro del
 *   mismo redibujo (ajustarEjeValor()). Deja un margen del 5 % del rango a
 *   cada lado y un rango de al menos 0,1 V: sin ese mínimo, una señal casi
 *   plana (por ejemplo, 2,500 V con variaciones de 1 mV) ocuparía toda la
 *   altura y parecería un ruido enorme. Si no se ve ningún punto (por
 *   ejemplo, justo después de un cambio de canal), el eje conserva el rango
 *   que tenía: así no salta a otro valor y de vuelta cuando llega la primera
 *   muestra.
 *
 * Guardado (Sprint 3): getHistorialParaGuardar() entrega el canal que se
 * grafica y su historial en voltios, tomados juntos, para EscritorArchivo.
 */
public class GraficaAnalogica extends GraficaSenal implements EscalaAjustable, SenalGuardable {

    /** Margen de la escala automática, a cada lado: 5 % del rango de los valores visibles. */
    private static final double MARGEN_AUTOMATICA = 0.05;

    /** Rango mínimo de la escala automática, en voltios. */
    private static final double RANGO_MINIMO_V = 0.1;

    /** Escala al comenzar. */
    private static final EscalaVertical ESCALA_INICIAL = EscalaVertical.CERO_A_5_V;

    private final NumberAxis ejeVoltaje;

    // Escala elegida. Solo se usa en el hilo de Swing.
    private EscalaVertical escala = ESCALA_INICIAL;

    /**
     * Crea la gráfica en el centro de la zona de gráfica del panel y la
     * registra como oyente del muestreador. Se llama desde el hilo de Swing.
     *
     * @param muestreador de donde llegan las muestras
     * @param panel       pestaña "Señal analógica"
     * @param canal       canal analógico con el que arranca (0 es A0)
     */
    public GraficaAnalogica(Muestreador muestreador, PanelSenal panel, int canal) {
        this(muestreador, panel, canal, crearEjeVoltaje());
    }

    // Recibe el eje ya creado para poder guardarlo: la clase base lo
    // necesita en su constructor, antes de que esta clase pueda guardar nada.
    private GraficaAnalogica(Muestreador muestreador, PanelSenal panel, int canal, NumberAxis eje) {
        super(muestreador, panel, canal, eje, crearLinea());
        this.ejeVoltaje = eje;
    }

    @Override
    protected double valorDe(Muestra muestra, int canal) {
        return muestra.getAnalogica(canal);
    }

    /**
     * El canal que se grafica (por ejemplo "A3") y la copia de su historial
     * en voltios, tomados juntos con el candado de la gráfica (ver
     * historialParaGuardar()). Se llama desde el hilo de Swing, al pulsar
     * "Guardar esta señal…" (ver SenalGuardable). Justo después de un cambio
     * de canal viene vacío: el cambio descarta lo del canal anterior.
     */
    @Override
    public HistorialSenal getHistorialParaGuardar() {
        return historialParaGuardar(HistorialSenal.Tipo.ANALOGICA);
    }

    /** Escala vertical actual. Se llama en el hilo de Swing. */
    @Override
    public EscalaVertical getEscala() {
        return escala;
    }

    /**
     * Cambia la escala vertical. Una escala fija se aplica en el acto; la
     * automática se ajusta a lo que se ve ahora (si no se ve nada, espera al
     * primer punto). Un solo cambio del eje: un solo redibujo. Elegir la que
     * ya tiene no hace nada. Se llama desde el hilo de Swing.
     *
     * @param nueva escala elegida
     * @throws NullPointerException si es null
     */
    @Override
    public void setEscala(EscalaVertical nueva) {
        if (nueva == null) {
            throw new NullPointerException("La escala no puede ser null");
        }
        if (nueva == escala) {
            return;
        }
        escala = nueva;
        if (nueva.esAutomatica()) {
            ajustarEjeValor();
        } else {
            ejeVoltaje.setRange(nueva.getMinimo(), nueva.getMaximo());
        }
    }

    /** Con la escala automática, ajusta el eje Y a los valores visibles. */
    @Override
    protected void ajustarEjeValor() {
        if (!escala.esAutomatica()) {
            return; // las escalas fijas no cambian
        }
        Range visible = rangoVisible();
        if (visible != null) {
            ejeVoltaje.setRange(rangoAutomatico(visible));
        }
    }

    /**
     * Rango del eje Y de la escala automática para esos valores visibles: el
     * rango de los valores más un margen del 5 % a cada lado; si eso mide
     * menos de 0,1 V, 0,1 V centrado en los valores.
     */
    private static Range rangoAutomatico(Range valores) {
        double margen = valores.getLength() * MARGEN_AUTOMATICA;
        double inferior = valores.getLowerBound() - margen;
        double superior = valores.getUpperBound() + margen;
        if (superior - inferior < RANGO_MINIMO_V) {
            double centro = valores.getCentralValue();
            inferior = centro - RANGO_MINIMO_V / 2;
            superior = centro + RANGO_MINIMO_V / 2;
        }
        return new Range(inferior, superior);
    }

    private static NumberAxis crearEjeVoltaje() {
        NumberAxis eje = new NumberAxis("Voltaje (V)");
        // setRange fija el eje y apaga el rango automático de JFreeChart: la
        // escala no salta aunque la señal no llegue a los extremos. La
        // automática de esta clase también usa setRange, con sus propios
        // margen y rango mínimo.
        eje.setRange(ESCALA_INICIAL.getMinimo(), ESCALA_INICIAL.getMaximo());
        return eje;
    }

    private static XYLineAndShapeRenderer crearLinea() {
        // Líneas entre puntos y ninguna figura sobre cada punto. Se deja el
        // dibujo por defecto, un segmento por cada par de puntos: medido con
        // 3000 puntos, es cerca de un 40 % más rápido que dibujar la señal
        // como un solo trazo (setDrawSeriesLineAsPath(true)).
        return new XYLineAndShapeRenderer(true, false);
    }
}
