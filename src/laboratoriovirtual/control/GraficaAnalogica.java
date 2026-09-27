package laboratoriovirtual.control;

import laboratoriovirtual.gui.PanelSenal;
import laboratoriovirtual.muestreo.Muestra;
import laboratoriovirtual.muestreo.Muestreador;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;

/**
 * Gráfica en vivo de una señal analógica contra el tiempo (I7LV-17), en la
 * pestaña "Señal analógica": línea continua roja y eje Y fijo de 0 a 5 V.
 *
 * El funcionamiento (hilos, ventana de 30 s, historial y estilo) está en
 * GraficaSenal; aquí solo va lo propio de las señales analógicas.
 *
 * Por ahora el canal es fijo. El cambio de canal con el selector es la
 * tarea I7LV-16.
 */
public class GraficaAnalogica extends GraficaSenal {

    /** Límites del eje Y, en voltios: el rango de las entradas analógicas. */
    private static final double VOLTAJE_MIN = 0.0;
    private static final double VOLTAJE_MAX = 5.0;

    /**
     * Crea la gráfica en el centro de la zona de gráfica del panel y la
     * registra como oyente del muestreador. Se llama desde el hilo de Swing.
     *
     * @param muestreador de donde llegan las muestras
     * @param panel       pestaña "Señal analógica"
     * @param canal       canal analógico a graficar (0 es A0)
     */
    public GraficaAnalogica(Muestreador muestreador, PanelSenal panel, int canal) {
        super(muestreador, panel, canal, crearEjeVoltaje(), crearLinea());
    }

    @Override
    protected double valorDe(Muestra muestra, int canal) {
        return muestra.getAnalogica(canal);
    }

    private static NumberAxis crearEjeVoltaje() {
        NumberAxis eje = new NumberAxis("Voltaje (V)");
        // setRange fija el eje y apaga el rango automático: la escala no
        // salta aunque la señal no llegue a los extremos
        eje.setRange(VOLTAJE_MIN, VOLTAJE_MAX);
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
