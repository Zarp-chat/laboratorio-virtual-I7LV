package laboratoriovirtual;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.Paint;
import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.IntToDoubleFunction;
import java.util.function.IntUnaryOperator;
import java.util.function.Supplier;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import laboratoriovirtual.control.CanalSeleccionable;
import laboratoriovirtual.control.ControlMuestreo;
import laboratoriovirtual.control.ControlSalidas;
import laboratoriovirtual.control.ControlSeleccion;
import laboratoriovirtual.control.ControlVisualizacion;
import laboratoriovirtual.control.EscalaAjustable;
import laboratoriovirtual.control.EscalaVertical;
import laboratoriovirtual.control.GraficaAnalogica;
import laboratoriovirtual.control.GraficaDigital;
import laboratoriovirtual.control.GraficaSenal;
import laboratoriovirtual.control.RendererBus;
import laboratoriovirtual.control.SenalesDigitalesEnVivo;
import laboratoriovirtual.control.SerieEnVivo;
import laboratoriovirtual.control.VentanaAjustable;
import laboratoriovirtual.datos.FuenteDeDatos;
import laboratoriovirtual.datos.FuenteDeDatosException;
import laboratoriovirtual.gui.BarraEstado;
import laboratoriovirtual.gui.LedIndicador;
import laboratoriovirtual.gui.PanelMuestreo;
import laboratoriovirtual.gui.PanelSalidas;
import laboratoriovirtual.gui.PanelSenal;
import laboratoriovirtual.gui.Tema;
import laboratoriovirtual.gui.VentanaPrincipal;
import laboratoriovirtual.muestreo.Muestra;
import laboratoriovirtual.muestreo.Muestreador;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.ChartRenderingInfo;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.ValueAxis;
import org.jfree.chart.event.ChartChangeListener;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.data.Range;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
 * Verifica las tareas del Sprint 2 (I7LV-17, I7LV-16, I7LV-20, I7LV-19,
 * I7LV-21, I7LV-22 e I7LV-18 fases A y B) sin abrir ninguna ventana. Se ejecuta con Shift+F6 y muestra una línea
 * [OK] o [FALLA] por criterio, igual que VerificacionSprint1. Una excepción
 * no capturada, en cualquier hilo, se imprime completa y cuenta como FALLA;
 * el programa nunca se queda colgado por ella (ver main()).
 *
 * I7LV-17: alimenta SerieEnVivo (la parte de datos de las gráficas en vivo)
 * con muestras sintéticas y revisa la ventana de 30 s, el historial, el
 * reinicio con un nuevo Iniciar y la carga con 10 000 muestras. No usa el
 * Muestreador ni la fuente: los tiempos se inventan aquí, así la prueba no
 * espera 30 s reales y da siempre el mismo resultado. procesarPendientes()
 * hace lo que en el programa hace el Timer de la gráfica cada 50 ms.
 *
 * I7LV-16: usa la GraficaAnalogica real (con una subclase que solo cuenta
 * los pedidos de cambio de canal), dentro de un PanelSenal que nunca se
 * muestra, con su Timer de 50 ms. El hilo de la prueba hace de
 * Muestreador: envía muestras sintéticas a muestraRecibida(), en las que el
 * canal i siempre vale i × 0,5 V, así cada punto dice de qué canal salió.
 * Lo que toca Swing (cambiar de canal, leer la gráfica) se hace en el hilo de
 * Swing, como en el programa. Lo que se revisa es lo que la gráfica de verdad
 * dibuja: la serie, el título y el eje X del ChartPanel del panel.
 *
 * I7LV-20: usa SenalesDigitalesEnVivo (la parte de datos de la gráfica
 * digital) y la GraficaDigital real, también dentro de un PanelSenal que
 * nunca se muestra. Las muestras son sintéticas con bits conocidos, así se
 * sabe qué debe haber en cada carril, en cada historial y en el carril
 * "Valor" en cada instante.
 *
 * I7LV-19: conecta, como Main, el selector de la pestaña "Señal digital"
 * (el JComboBox<String> de un PanelSenal con D0 a D3) con la GraficaDigital
 * real mediante ControlSeleccion, y elige señales en el selector como lo
 * haría el usuario. Revisa la selección, el título, el historial entregado,
 * que no se borre nada, el color de los nombres de los carriles (también en
 * una imagen de la gráfica dibujada) y que la pestaña analógica siga igual.
 *
 * I7LV-21 e I7LV-22: usa el ControlSalidas real sobre un PanelSalidas que
 * nunca se muestra, con una subclase que guarda los avisos en una lista en
 * vez de abrir un cuadro de diálogo. La fuente es FuenteDePrueba, escrita
 * aquí: registra cada escritura de una salida y se puede configurar para
 * rechazarlas. Los interruptores y los botones se pulsan con doClick(), que
 * produce los mismos avisos que un clic. Con un Muestreador real sobre esa
 * fuente se revisa que conexión y muestreo vayan por separado: las salidas
 * funcionan con el muestreo detenido, y detenerlo no las cambia ni
 * desconecta la fuente.
 *
 * I7LV-18 fase A: arma la ventana real (VentanaPrincipal) sin mostrarla y
 * revisa que el texto de la barra de estado se vea completo en su tamaño
 * mínimo, con el mismo cálculo que usa Swing para dibujar la etiqueta, y que
 * la etiqueta no cambie de ancho con el contador. Con el ControlSalidas real
 * revisa que los LEDs de la pestaña Salidas cambien junto con las etiquetas,
 * y dibuja un LedIndicador en una imagen para revisar sus colores.
 *
 * I7LV-18 fase B: cambia la ventana visible y la escala vertical en los
 * datos (SerieEnVivo, SenalesDigitalesEnVivo) y en las gráficas reales,
 * también a través de ControlVisualizacion como lo haría el usuario. Revisa
 * la reconstrucción desde el historial, los ejes, el punto anterior al borde
 * izquierdo, el eje X después de un cambio de canal (también con un
 * Muestreador real corriendo, a 10 ms y a 1000 ms), la concurrencia y el
 * rendimiento con la ventana de 1 min.
 */
public class VerificacionSprint2 {

    /** Ancho de la ventana visible, el mismo de las gráficas del programa. */
    private static final double VENTANA_S = GraficaSenal.VENTANA_POR_DEFECTO_S;

    /** Muestras por segundo con el tiempo de muestreo mínimo (10 ms). */
    private static final int MUESTRAS_POR_SEGUNDO = 100;

    /** Muestras que llegan entre dos ciclos del Timer: 50 ms / 10 ms. */
    private static final int MUESTRAS_POR_CICLO = 5;

    /** Canales de la pestaña "Señal analógica": A0 a A7. */
    private static final int CANALES = FuenteDeDatos.NUM_ANALOGICAS;

    /** Lectura de las entradas en cada muestra de I7LV-16: el canal i vale i × 0,5 V. */
    private static final double[] ANALOGICAS = new double[CANALES];
    private static final boolean[] DIGITALES = new boolean[FuenteDeDatos.NUM_DIGITALES_ENTRADA];

    static {
        for (int canal = 0; canal < CANALES; canal++) {
            ANALOGICAS[canal] = valorCanal(canal);
        }
    }

    /**
     * Muestreador de las pruebas de I7LV-16. Nunca se inicia: solo está para
     * que las gráficas se registren como oyentes. Las muestras las envía la
     * prueba.
     */
    private static final Muestreador MUESTREADOR = new Muestreador(new FuenteSinUso(), 500);

    /** Rondas de la prueba de cambios de canal con dos hilos. */
    private static final int RONDAS = 5;

    /** Cambios de canal en cada ronda. */
    private static final int CAMBIOS_POR_RONDA = 1000;

    /** Señales de la pestaña "Señal digital": D0 a D3. El carril "Valor" es el que sigue. */
    private static final int CANALES_DIGITALES = FuenteDeDatos.NUM_DIGITALES_ENTRADA;

    /** Número del carril "Valor" en la gráfica digital: el quinto, después de D0 a D3. */
    private static final int CARRIL_VALOR = CANALES_DIGITALES;

    /**
     * Muestras entre dos "Iniciar" en la prueba de concurrencia de I7LV-20:
     * 40 s a 1 ms. Así el historial no pasa de 40 000 puntos y copiarlo en
     * cada uno de los 1000 cambios no hace lenta la prueba.
     */
    private static final int MUESTRAS_POR_CORRIDA = 40_000;

    /**
     * Muestras que el hilo productor de la prueba de concurrencia de I7LV-16
     * puede enviar después de un cambio de canal antes de esperar el
     * siguiente. Sin este tope, cuando el Timer de la gráfica se quedaba
     * atrás, la cola y el historial crecían sin límite hasta agotar la
     * memoria (ver verificarCambiosConcurrentes()). Es menos que los 30 000
     * puntos de la ventana visible (30 s a 1 ms por muestra): así, un punto
     * del canal anterior que se colara en la serie sigue a la vista cuando
     * la revisa el cambio siguiente.
     */
    private static final int MUESTRAS_POR_CAMBIO = 20_000;

    /**
     * Excepciones no capturadas en otros hilos (por ejemplo, en el Timer de
     * una gráfica, que corre en el hilo de Swing). Cada una se imprime
     * completa en cuanto ocurre y queda aquí hasta que la revisión al final
     * de su sección la cuenta como FALLA (revisarErroresEnOtrosHilos()).
     */
    private static final Queue<Throwable> ERRORES_EN_OTROS_HILOS = new ConcurrentLinkedQueue<>();

    /** Sección que se está verificando, para decir dónde ocurrió una excepción. */
    private static volatile String seccionActual = "(antes de la primera sección)";

    private static int aprobados = 0;
    private static int fallidos = 0;

    /**
     * Ejecuta todas las secciones y termina el programa con el resumen.
     *
     * Excepciones no capturadas:
     * - En otro hilo (el Timer de una gráfica, un hilo de prueba): se
     *   imprimen completas al momento, cuentan como FALLA y la verificación
     *   sigue.
     * - En este hilo, incluidas las de una tarea de enSwing() (llegan aquí
     *   envueltas en una InvocationTargetException): la verificación no
     *   puede seguir. Se imprime completa, cuenta como FALLA y el programa
     *   termina con un mensaje claro.
     * Siempre termina con System.exit: las gráficas de prueba dejan corriendo
     * su Timer de refresco, que le manda un evento al hilo de Swing cada
     * 50 ms y así lo mantiene vivo. Sin System.exit el programa no terminaría
     * nunca, ni siquiera después de una excepción.
     */
    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler(VerificacionSprint2::excepcionEnOtroHilo);
        try {
            verificarSecciones();
        } catch (Throwable e) {
            fallidos++;
            String encabezado = "[FALLA] Excepción no capturada en el hilo principal, durante \""
                    + seccionActual + "\".";
            if (e instanceof InvocationTargetException && e.getCause() != null) {
                encabezado += " Ocurrió dentro de una tarea del hilo de Swing: " + e.getCause();
            }
            imprimirExcepcion(encabezado, e);
            System.out.println();
            System.out.println("===================================");
            System.out.println("VERIFICACIÓN DETENIDA por una excepción no capturada: ver la traza de arriba.");
            System.out.println("Resultado hasta ese momento: " + aprobados + " aprobados, " + fallidos
                    + " fallidos. Las pruebas que faltaban no se ejecutaron.");
            System.exit(1);
        }

        System.out.println();
        System.out.println("===================================");
        System.out.println("Resultado: " + aprobados + " aprobados, " + fallidos + " fallidos");
        System.out.println(fallidos == 0
                ? "I7LV-17, I7LV-16, I7LV-20, I7LV-19, I7LV-21, I7LV-22 e I7LV-18 (fases A y B) cumplen sus criterios."
                : "Hay criterios sin cumplir: revisa las líneas [FALLA].");
        System.exit(fallidos == 0 ? 0 : 1);
    }

    private static void verificarSecciones() throws Exception {
        titulo("I7LV-17  Gráfica de la señal analógica (datos, sin ventana)");

        verificarCola();
        verificarVentana();
        verificarHistorial();
        verificarReinicio();
        verificarCarga();

        titulo("I7LV-16  Cambio de canal de la señal analógica (gráfica real, sin ventana)");

        verificarCambioDeCanal();
        verificarCanalFueraDeRango();
        verificarCambioDetenido();
        verificarControlSeleccion();
        verificarCambiosConcurrentes();

        revisarErroresEnOtrosHilos("I7LV-17 e I7LV-16");

        titulo("I7LV-20  Gráfica de la señal digital (datos y gráfica real, sin ventana)");

        verificarRegistroDigital();
        verificarHexadecimal();
        verificarSeleccionDigital();
        verificarVentanaDigital();
        verificarReinicioDigital();
        verificarSoloCambios();
        verificarCargaDigital();
        verificarHistorialConcurrente();
        verificarSeleccionConcurrente();

        revisarErroresEnOtrosHilos("I7LV-20");

        titulo("I7LV-19  Selección de la señal digital con el selector (gráfica real, sin ventana)");

        verificarSelectorDigital();
        verificarNombresCarriles();
        verificarRecorridoConFlechas();
        verificarAnalogicaSigueIgual();

        revisarErroresEnOtrosHilos("I7LV-19");

        titulo("I7LV-21 e I7LV-22  Encendido y apagado de las salidas digitales (control real, sin ventana)");

        verificarSalidasAlCrear();
        verificarPulsarInterruptor();
        verificarEscrituraRechazada();
        verificarSalidasConMuestreoDetenido();
        verificarHabilitarSalidas();
        verificarIniciarDetenerSinReconectar();

        revisarErroresEnOtrosHilos("I7LV-21 e I7LV-22");

        titulo("I7LV-18 fase A  Texto de la barra de estado y LEDs de las salidas (ventana real sin mostrar)");

        verificarTextoBarra();
        verificarTextoDelControl();
        verificarLedsAlCrear();
        verificarLedsSiguenEscrituras();
        verificarLedsConFalla();
        verificarDibujoLed();

        revisarErroresEnOtrosHilos("I7LV-18 fase A");

        titulo("I7LV-18 fase B  Base de tiempo y escala vertical (datos y gráficas reales, sin ventana)");

        verificarValoresPorDefecto();
        verificarControlVisualizacion();
        verificarVentanaEnDatos();
        verificarEjeConVentana();
        verificarEscalas();
        verificarHuecoIzquierda();
        verificarEjeTrasCambioDeCanal();
        verificarVentanaDigitalFaseB();
        verificarVentanaConcurrente();
        verificarRendererEscalones();
        verificarRendimientoVentanaMinuto();

        revisarErroresEnOtrosHilos("I7LV-18 fase B");
    }

    // ===================== Excepciones no capturadas =====================

    /**
     * Manejador de las excepciones no capturadas en otros hilos (ver main()).
     * La imprime completa al momento y la deja para la revisión de la sección.
     */
    private static void excepcionEnOtroHilo(Thread hilo, Throwable e) {
        ERRORES_EN_OTROS_HILOS.add(e);
        imprimirExcepcion("[EXCEPCIÓN] No capturada en el hilo \"" + hilo.getName() + "\", durante \""
                + seccionActual + "\". Cuenta como FALLA en la revisión al final de esta sección.", e);
    }

    /**
     * Manejador para un hilo de prueba cuyo error revisa la propia prueba: lo
     * guarda en donde, para que la prueba lo cuente como FALLA, y lo imprime
     * completo al momento.
     */
    private static Thread.UncaughtExceptionHandler guardarEImprimir(AtomicReference<Throwable> donde) {
        return (hilo, e) -> {
            donde.compareAndSet(null, e);
            imprimirExcepcion("[EXCEPCIÓN] No capturada en el hilo \"" + hilo.getName() + "\", durante \""
                    + seccionActual + "\". Cuenta como FALLA en la prueba que usa ese hilo.", e);
        };
    }

    /**
     * Cuenta como FALLA las excepciones no capturadas en otros hilos desde la
     * revisión anterior (ya se imprimieron completas cuando ocurrieron) y las
     * descarta, así cada revisión solo cuenta las de su sección.
     */
    private static void revisarErroresEnOtrosHilos(String secciones) {
        List<Throwable> errores = new ArrayList<>();
        Throwable error;
        while ((error = ERRORES_EN_OTROS_HILOS.poll()) != null) {
            errores.add(error);
        }
        verificar("Ningún error sin atrapar en el Timer (hilo de Swing) ni en otro hilo durante " + secciones,
                errores.isEmpty(),
                errores.size() + (errores.size() == 1 ? " excepción" : " excepciones")
                        + " (impresas completas arriba); la primera: "
                        + (errores.isEmpty() ? "" : errores.get(0)));
    }

    /**
     * Imprime el encabezado y la traza completa de la excepción en una sola
     * escritura: así no se mezcla con lo que otro hilo imprima al mismo
     * tiempo.
     */
    private static void imprimirExcepcion(String encabezado, Throwable e) {
        StringWriter traza = new StringWriter();
        e.printStackTrace(new PrintWriter(traza));
        System.out.print(encabezado + System.lineSeparator() + traza);
        System.out.flush();
    }

    // ===================== Cola entre hilos =====================

    private static void verificarCola() {
        SerieEnVivo datos = new SerieEnVivo(VENTANA_S);
        XYSeries serie = datos.getSerie();

        for (int i = 0; i < MUESTRAS_POR_CICLO; i++) {
            datos.agregar(tiempo(i), valor(i));
        }
        verificar("agregar() solo guarda en la cola: la serie no cambia todavía",
                serie.getItemCount() == 0 && datos.hayPendientes(),
                "la serie tiene " + serie.getItemCount() + " puntos");

        datos.procesarPendientes();
        verificar("procesarPendientes() pasa a la serie todo lo pendiente",
                serie.getItemCount() == MUESTRAS_POR_CICLO && !datos.hayPendientes(),
                "la serie tiene " + serie.getItemCount() + " puntos");

        // Ya con más de 30 s: en un mismo ciclo se agregan puntos y se quitan viejos
        alimentar(datos, MUESTRAS_POR_CICLO, 40 * MUESTRAS_POR_SEGUNDO);
        int[] avisos = {0};
        serie.addChangeListener(e -> avisos[0]++);
        int desde = 40 * MUESTRAS_POR_SEGUNDO;
        for (int i = desde; i < desde + 50; i++) {
            datos.agregar(tiempo(i), valor(i));
        }
        datos.procesarPendientes();
        verificar("Un ciclo con 50 puntos nuevos y puntos viejos quitados avisa una sola vez a la gráfica",
                avisos[0] == 1, "avisó " + avisos[0] + " veces");

        avisos[0] = 0;
        datos.procesarPendientes();
        verificar("Un ciclo sin puntos nuevos no avisa (no se redibuja)",
                avisos[0] == 0, "avisó " + avisos[0] + " veces");
    }

    // ===================== Ventana de 30 s =====================

    private static void verificarVentana() {
        SerieEnVivo datos = new SerieEnVivo(VENTANA_S);
        XYSeries serie = datos.getSerie();

        verificar("Sin datos, el eje X va de 0 a 30 s",
                datos.getInicioVentana() == 0.0 && datos.getFinVentana() == VENTANA_S,
                rango(datos));

        // 10 s de muestreo: todavía no se llena la ventana
        alimentar(datos, 0, 10 * MUESTRAS_POR_SEGUNDO + 1);
        verificar("A los 10 s, el eje X sigue de 0 a 30 s",
                datos.getInicioVentana() == 0.0 && datos.getFinVentana() == VENTANA_S,
                rango(datos));
        verificar("A los 10 s, la serie tiene todos los puntos (1001)",
                serie.getItemCount() == 1001, "tiene " + serie.getItemCount());

        // 45 s de muestreo: la ventana ya se deslizó 15 s
        int total = 45 * MUESTRAS_POR_SEGUNDO + 1;
        alimentar(datos, 10 * MUESTRAS_POR_SEGUNDO + 1, total);
        verificar("A los 45 s, el eje X se deslizó: va de 15 a 45 s",
                datos.getInicioVentana() == 15.0 && datos.getFinVentana() == 45.0,
                rango(datos));
        verificar("A los 45 s, la serie solo tiene puntos de los últimos 30 s",
                serie.getMinX() >= 45.0 - VENTANA_S && serie.getMaxX() == 45.0,
                String.format("va de %.2f a %.2f s", serie.getMinX(), serie.getMaxX()));
        verificar("... y no le falta ninguno de esos 30 s (3001 puntos, de 15,00 a 45,00 s)",
                serie.getItemCount() == esperadosEnVentana(total), "tiene " + serie.getItemCount());
        verificar("Los puntos de la serie quedan en orden de tiempo",
                ordenada(serie), "hay puntos desordenados");
    }

    // ===================== Historial =====================

    private static void verificarHistorial() {
        SerieEnVivo datos = new SerieEnVivo(VENTANA_S);
        int total = 45 * MUESTRAS_POR_SEGUNDO + 1;
        alimentar(datos, 0, total);

        List<SerieEnVivo.Punto> historial = datos.getHistorial();
        verificar("El historial conserva los 45 s completos (4501 puntos), aunque la serie tenga 3001",
                historial.size() == total && historial.get(0).tiempo() == 0.0
                        && historial.get(total - 1).tiempo() == 45.0,
                "tiene " + historial.size() + " puntos");

        boolean valoresCorrectos = true;
        for (int i = 0; i < historial.size(); i++) {
            SerieEnVivo.Punto p = historial.get(i);
            if (p.tiempo() != tiempo(i) || p.valor() != valor(i)) {
                valoresCorrectos = false;
            }
        }
        verificar("Cada punto del historial tiene su tiempo y su valor, en orden",
                valoresCorrectos, "algún punto no coincide con lo enviado");

        boolean inmutable = false;
        try {
            historial.add(new SerieEnVivo.Punto(99, 1));
        } catch (UnsupportedOperationException e) {
            inmutable = true;
        }
        verificar("La copia del historial no se puede modificar", inmutable, "aceptó un punto nuevo");

        // Llegan puntos que la gráfica todavía no ha dibujado
        datos.agregar(tiempo(total), valor(total));
        datos.agregar(tiempo(total + 1), valor(total + 1));
        verificar("Una copia ya entregada no cambia cuando llegan más muestras",
                historial.size() == total, "pasó de " + total + " a " + historial.size());
        verificar("Una copia nueva incluye los puntos que la gráfica aún no dibuja",
                datos.getHistorial().size() == total + 2,
                "tiene " + datos.getHistorial().size() + " puntos");
    }

    // ===================== Nuevo Iniciar =====================

    private static void verificarReinicio() {
        SerieEnVivo datos = new SerieEnVivo(VENTANA_S);
        XYSeries serie = datos.getSerie();
        int total = 45 * MUESTRAS_POR_SEGUNDO + 1;
        alimentar(datos, 0, total);

        // Nuevo Iniciar: el tiempo vuelve a empezar desde cero
        datos.agregar(0.0, 1.0);
        verificar("Una muestra con tiempo menor vacía el historial en el acto",
                datos.getHistorial().size() == 1, "tiene " + datos.getHistorial().size() + " puntos");
        datos.procesarPendientes();
        verificar("... y vacía la serie antes de agregarla",
                serie.getItemCount() == 1 && serie.getX(0).doubleValue() == 0.0,
                "la serie tiene " + serie.getItemCount() + " puntos");
        verificar("... y el eje X vuelve a ir de 0 a 30 s",
                datos.getInicioVentana() == 0.0 && datos.getFinVentana() == VENTANA_S,
                rango(datos));

        // Puntos de la corrida anterior y de la nueva en la misma cola:
        // Detener e Iniciar entre dos ciclos del Timer
        datos = new SerieEnVivo(VENTANA_S);
        serie = datos.getSerie();
        alimentar(datos, 0, total);
        datos.agregar(tiempo(total), 2.0);
        datos.agregar(tiempo(total + 1), 2.0);
        datos.agregar(0.0, 3.0);
        datos.agregar(0.01, 3.0);
        datos.agregar(0.02, 3.0);
        datos.procesarPendientes();
        verificar("Con puntos de las dos corridas en un mismo ciclo, solo quedan los de la nueva",
                serie.getItemCount() == 3 && serie.getMinY() == 3.0 && serie.getMaxY() == 3.0
                        && datos.getHistorial().size() == 3,
                "serie con " + serie.getItemCount() + " puntos e historial con "
                        + datos.getHistorial().size());

        // Dos muestras con el mismo tiempo no son un nuevo Iniciar
        datos.agregar(0.02, 4.0);
        boolean sinError = true;
        try {
            datos.procesarPendientes();
        } catch (RuntimeException e) {
            sinError = false;
        }
        verificar("Una muestra con el mismo tiempo que la anterior no reinicia ni da error",
                sinError && serie.getItemCount() == 4 && datos.getHistorial().size() == 4,
                sinError ? "serie con " + serie.getItemCount() + " puntos" : "lanzó una excepción");
    }

    // ===================== 10 000 muestras =====================

    private static void verificarCarga() throws InterruptedException {
        final int total = 10_000; // 100 s de muestreo a 10 ms

        // 1) Una tras otra, con un ciclo del Timer cada 5 muestras, midiendo
        //    cuánto tarda cada ciclo (en el programa, hay 50 ms por ciclo).
        SerieEnVivo datos = new SerieEnVivo(VENTANA_S);
        XYSeries serie = datos.getSerie();
        long peorCicloNanos = 0;
        long inicio = System.nanoTime();
        try {
            for (int i = 0; i < total; i++) {
                datos.agregar(tiempo(i), valor(i));
                if ((i + 1) % MUESTRAS_POR_CICLO == 0) {
                    long t0 = System.nanoTime();
                    datos.procesarPendientes();
                    peorCicloNanos = Math.max(peorCicloNanos, System.nanoTime() - t0);
                }
            }
            datos.procesarPendientes();
        } catch (RuntimeException e) {
            verificar("10 000 muestras seguidas se procesan sin errores", false, e.toString());
            return;
        }
        double totalMs = (System.nanoTime() - inicio) / 1e6;
        double promedioMs = totalMs / (total / MUESTRAS_POR_CICLO);

        verificar("10 000 muestras seguidas se procesan sin errores",
                datos.getHistorial().size() == total
                        && serie.getItemCount() == esperadosEnVentana(total)
                        && ordenada(serie),
                "historial " + datos.getHistorial().size() + ", serie " + serie.getItemCount());
        verificar(String.format("Cada ciclo tarda mucho menos que los 50 ms del Timer "
                        + "(promedio %.3f ms, el peor %.1f ms)", promedioMs, peorCicloNanos / 1e6),
                promedioMs < 1.0 && peorCicloNanos / 1e6 < 50.0,
                "demasiado lento");

        // 2) Con dos hilos a la vez, como en el programa: uno agrega (el
        //    Muestreador) mientras otro procesa y copia el historial.
        SerieEnVivo compartida = new SerieEnVivo(VENTANA_S);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread productor = new Thread(() -> {
            for (int i = 0; i < total; i++) {
                compartida.agregar(tiempo(i), valor(i));
            }
        }, "Productor de prueba");
        productor.setUncaughtExceptionHandler(guardarEImprimir(error));

        boolean copiasCorrectas = true;
        int copias = 0;
        try {
            productor.start();
            while (productor.isAlive()) {
                compartida.procesarPendientes();
                List<SerieEnVivo.Punto> copia = compartida.getHistorial();
                copias++;
                // Cada copia debe estar completa y en orden: 0, 1, 2... sin huecos
                for (int i = 0; i < copia.size(); i++) {
                    if (copia.get(i).tiempo() != tiempo(i)) {
                        copiasCorrectas = false;
                        break;
                    }
                }
            }
            productor.join();
            compartida.procesarPendientes();
        } catch (RuntimeException e) {
            error.compareAndSet(null, e);
        }
        XYSeries serieCompartida = compartida.getSerie();
        verificar("Con un hilo agregando y otro procesando a la vez, no hay errores ni puntos perdidos",
                error.get() == null
                        && compartida.getHistorial().size() == total
                        && serieCompartida.getItemCount() == esperadosEnVentana(total)
                        && ordenada(serieCompartida),
                error.get() != null ? error.get().toString()
                        : "historial " + compartida.getHistorial().size()
                        + ", serie " + serieCompartida.getItemCount());
        verificar("Las copias del historial tomadas en pleno muestreo están completas y en orden ("
                        + copias + " copias)",
                copiasCorrectas, "alguna copia salió incompleta o desordenada");
    }

    // ===================== I7LV-16: cambio de canal =====================

    private static void verificarCambioDeCanal() throws Exception {
        GraficaDePrueba g = crearGrafica(0);

        // 40 s de A0 con el muestreo corriendo, ya dibujados
        int dibujadas = 40 * MUESTRAS_POR_SEGUNDO + 1;
        enviar(g, 0, dibujadas);
        boolean dibujo = esperarDibujo(g, tiempo(dibujadas - 1));

        // Llegan 5 s más de A0 (hasta 45 s) y, antes de que el Timer los
        // dibuje, se cambia a A3: quedan en la cola, pendientes
        int hasta = 45 * MUESTRAS_POR_SEGUNDO + 1;
        List<Foto> cambio = enviarYCambiarSinDibujar(g, dibujadas, hasta,
                () -> g.grafica().cambiarCanal(3));
        Foto antes = cambio.get(0);
        Foto tras = cambio.get(1);
        verificar("Antes del cambio se grafica A0 (0,0 V), y hay 5 s recibidos que el Timer aún no dibuja",
                dibujo && antes.titulo().equals("Canal A0") && antes.historial().size() == hasta
                        && antes.finEje() == 40.0 && !antes.valoresSerie().isEmpty()
                        && soloVale(antes.valoresSerie(), valorCanal(0))
                        && soloVale(valores(antes.historial()), valorCanal(0)),
                describir(antes) + ", " + rango(antes));
        verificar("Cambiar de A0 a A3 vacía la serie dibujada y el historial",
                tras.valoresSerie().isEmpty() && tras.historial().isEmpty(), describir(tras));
        verificar("... y el título pasa a ser \"Canal A3\"",
                tras.canal() == 3 && tras.titulo().equals("Canal A3"), describir(tras));

        esperarCiclosDelTimer();
        Foto luego = enSwing(() -> foto(g));
        verificar("... y vacía la cola: los 5 s de A0 que faltaba dibujar no aparecen después",
                luego.valoresSerie().isEmpty() && luego.historial().isEmpty(),
                "150 ms después: " + describir(luego));

        // El muestreo sigue: el tiempo continúa desde 45,01 s, no vuelve a cero
        int primera = hasta;
        hasta = 60 * MUESTRAS_POR_SEGUNDO + 1;
        enviar(g, primera, hasta);
        dibujo = esperarDibujo(g, tiempo(hasta - 1));
        Foto despues = enSwing(() -> foto(g));
        verificar("Después del cambio solo se registran valores de A3 (1,5 V), desde la primera muestra nueva",
                despues.historial().size() == hasta - primera
                        && despues.historial().get(0).tiempo() == tiempo(primera)
                        && soloVale(valores(despues.historial()), valorCanal(3)),
                describir(despues));
        verificar("... y la gráfica solo dibuja valores de A3",
                dibujo && despues.valoresSerie().size() == hasta - primera
                        && soloVale(despues.valoresSerie(), valorCanal(3)),
                describir(despues));
        verificar("El eje X arranca en la primera muestra de A3, sin volver a cero y con la "
                        + "misma ventana de 30 s: va de 45,01 a 75,01 s",
                despues.inicioEje() == tiempo(primera)
                        && Math.abs(despues.finEje() - (tiempo(primera) + VENTANA_S)) < 1e-9,
                rango(despues));

        // 30 s más: la ventana se desliza como siempre
        enviar(g, hasta, 90 * MUESTRAS_POR_SEGUNDO + 1);
        dibujo = esperarDibujo(g, 90.0);
        Foto a90 = enSwing(() -> foto(g));
        verificar("... y después se desliza como siempre: a los 90 s va de 60 a 90 s",
                dibujo && a90.inicioEje() == 60.0 && a90.finEje() == 90.0, rango(a90));

        // Elegir otra vez A3: las dos fotos en la misma tarea de Swing, así
        // el Timer no puede cambiar nada entre una y otra
        List<Foto> mismo = enSwing(() -> {
            Foto previa = foto(g);
            g.grafica().cambiarCanal(3);
            return List.of(previa, foto(g));
        });
        verificar("Volver a elegir A3 no vacía nada: serie, historial y título siguen igual",
                mismo.get(0).equals(mismo.get(1)) && !mismo.get(1).historial().isEmpty(),
                "antes: " + describir(mismo.get(0)) + "; después: " + describir(mismo.get(1)));
    }

    private static void verificarCanalFueraDeRango() throws Exception {
        GraficaDePrueba g = crearGrafica(3);
        int hasta = 5 * MUESTRAS_POR_SEGUNDO + 1;
        enviar(g, 0, hasta);
        esperarDibujo(g, tiempo(hasta - 1));

        boolean[] rechazado = new boolean[2];
        List<Foto> fotos = enSwing(() -> {
            Foto previa = foto(g);
            rechazado[0] = rechaza(g.grafica(), CANALES); // 8: no existe A8
            rechazado[1] = rechaza(g.grafica(), -1);
            return List.of(previa, foto(g));
        });
        verificar("Un canal fuera de rango (8 y -1) se rechaza con IllegalArgumentException",
                rechazado[0] && rechazado[1],
                "8 " + (rechazado[0] ? "rechazado" : "aceptado")
                        + ", -1 " + (rechazado[1] ? "rechazado" : "aceptado"));
        verificar("... y se conserva el canal anterior: sigue A3, con su título y sus puntos",
                fotos.get(1).canal() == 3 && fotos.get(0).equals(fotos.get(1))
                        && fotos.get(1).historial().size() == hasta,
                describir(fotos.get(1)));

        enviar(g, hasta, hasta + MUESTRAS_POR_SEGUNDO);
        Foto luego = enSwing(() -> foto(g));
        verificar("... y las muestras siguientes se siguen registrando de A3",
                luego.historial().size() == hasta + MUESTRAS_POR_SEGUNDO
                        && soloVale(valores(luego.historial()), valorCanal(3)),
                describir(luego));
    }

    private static void verificarCambioDetenido() throws Exception {
        GraficaDePrueba g = crearGrafica(0);
        int hasta = 20 * MUESTRAS_POR_SEGUNDO + 1;
        enviar(g, 0, hasta);
        esperarDibujo(g, tiempo(hasta - 1));

        // Detener: ya no llegan muestras
        Foto tras = enSwing(() -> {
            g.grafica().cambiarCanal(5);
            return foto(g);
        });
        esperarCiclosDelTimer();
        Foto luego = enSwing(() -> foto(g));
        verificar("Con el muestreo detenido, cambiar a A5 deja la gráfica vacía con el título \"Canal A5\"",
                tras.canal() == 5 && tras.titulo().equals("Canal A5")
                        && tras.valoresSerie().isEmpty() && tras.historial().isEmpty()
                        && luego.valoresSerie().isEmpty() && luego.historial().isEmpty(),
                "justo después: " + describir(tras) + "; 150 ms después: " + describir(luego));

        // Iniciar: el tiempo del Muestreador vuelve a empezar desde cero
        hasta = 10 * MUESTRAS_POR_SEGUNDO + 1;
        enviar(g, 0, hasta);
        boolean dibujo = esperarDibujo(g, tiempo(hasta - 1));
        Foto iniciado = enSwing(() -> foto(g));
        verificar("Al Iniciar se grafica A5 (2,5 V) y el eje X vuelve a ir de 0 a 30 s",
                dibujo && iniciado.historial().size() == hasta
                        && iniciado.valoresSerie().size() == hasta
                        && soloVale(valores(iniciado.historial()), valorCanal(5))
                        && soloVale(iniciado.valoresSerie(), valorCanal(5))
                        && iniciado.inicioEje() == 0.0 && iniciado.finEje() == VENTANA_S,
                describir(iniciado) + ", " + rango(iniciado));
    }

    private static void verificarControlSeleccion() throws Exception {
        GraficaDePrueba g = crearGrafica(2);
        JComboBox<String> selector = g.panel().getComboCanal();
        int hasta = 5 * MUESTRAS_POR_SEGUNDO + 1;
        enviar(g, 0, hasta); // 5 s de A2

        String mostrado = enSwing(() -> {
            new ControlSeleccion(selector, g.grafica());
            return selector.getSelectedIndex() + " " + selector.getSelectedItem();
        });
        Foto creado = enSwing(() -> foto(g));
        int pedidosAlCrear = enSwing(() -> g.grafica().pedidosDeCambio);
        verificar("Al crearse, el control deja el selector mostrando el canal de la gráfica (A2)",
                mostrado.equals("2 A2"), "el selector muestra " + mostrado);
        verificar("... y crearlo no cambia ni vacía la gráfica",
                pedidosAlCrear == 0 && creado.canal() == 2 && creado.historial().size() == hasta,
                "cambios pedidos: " + pedidosAlCrear + "; " + describir(creado));

        Foto elegido = enSwing(() -> {
            selector.setSelectedIndex(6);
            return foto(g);
        });
        verificar("Elegir A6 en el selector cambia la gráfica: canal 6, título \"Canal A6\", "
                        + "serie e historial vacíos",
                elegido.canal() == 6 && elegido.titulo().equals("Canal A6")
                        && elegido.valoresSerie().isEmpty() && elegido.historial().isEmpty(),
                describir(elegido));

        enviar(g, hasta, hasta + 3 * MUESTRAS_POR_SEGUNDO);
        int[] avisosAction = {0};
        int[] pedidos = new int[2];
        List<Foto> mismo = enSwing(() -> {
            selector.addActionListener(e -> avisosAction[0]++);
            Foto previa = foto(g);
            pedidos[0] = g.grafica().pedidosDeCambio;
            selector.setSelectedIndex(6);
            pedidos[1] = g.grafica().pedidosDeCambio;
            return List.of(previa, foto(g));
        });
        verificar("Volver a elegir A6 en el selector no vacía nada",
                mismo.get(0).equals(mismo.get(1))
                        && soloVale(valores(mismo.get(1).historial()), valorCanal(6))
                        && mismo.get(1).historial().size() == 3 * MUESTRAS_POR_SEGUNDO,
                "antes: " + describir(mismo.get(0)) + "; después: " + describir(mismo.get(1)));
        verificar("... y el control ni siquiera se lo pide a la gráfica: el selector avisa con "
                        + "ActionEvent, pero el control solo atiende cambios reales",
                avisosAction[0] == 1 && pedidos[1] == pedidos[0],
                "ActionEvent: " + avisosAction[0] + "; cambios pedidos a la gráfica: "
                        + (pedidos[1] - pedidos[0]));

        // Una gráfica nueva en la que se elige A4 antes del primer Iniciar.
        // Como en el programa, la primera muestra llega un poco después de 0 s.
        GraficaDePrueba nueva = crearGrafica(0);
        enSwing(() -> {
            JComboBox<String> otroSelector = nueva.panel().getComboCanal();
            new ControlSeleccion(otroSelector, nueva.grafica());
            otroSelector.setSelectedIndex(4);
            return null;
        });
        double retraso = 0.0004;
        for (int i = 0; i <= 10 * MUESTRAS_POR_SEGUNDO; i++) {
            nueva.grafica().muestraRecibida(muestra(retraso + tiempo(i)));
        }
        boolean dibujo = esperarDibujo(nueva, retraso + tiempo(10 * MUESTRAS_POR_SEGUNDO));
        Foto primera = enSwing(() -> foto(nueva));
        verificar("Si se elige A4 antes del primer Iniciar, al iniciar se grafica A4 con el eje X de 0 a 30 s",
                dibujo && primera.titulo().equals("Canal A4")
                        && soloVale(primera.valoresSerie(), valorCanal(4))
                        && primera.inicioEje() == 0.0 && primera.finEje() == VENTANA_S,
                describir(primera) + ", " + rango(primera));
    }

    /**
     * Un hilo envía muestras sin parar, como un Muestreador muy rápido,
     * mientras el hilo de Swing cambia de canal 1000 veces. Se repite varias
     * rondas para que un error ocasional se note.
     *
     * En cada cambio, dentro de la misma tarea de Swing, se revisa:
     * - antes: lo que el Timer ya dibujó es todo del canal actual;
     * - después: la serie quedó vacía y el historial solo tiene puntos del
     *   canal nuevo (el otro hilo puede haber agregado algunos ya).
     * Al final de cada ronda, el historial y la serie solo tienen valores del
     * último canal elegido.
     *
     * Aquí el tiempo avanza 1 ms por muestra y no 10 ms: el otro hilo envía
     * millones de muestras por segundo, y así la ventana de 30 s (30 000
     * puntos) alcanza a abarcar varios cambios seguidos. Si un cambio dejara
     * en la cola puntos del canal anterior, el Timer los dibujaría y la
     * revisión del cambio siguiente los encontraría.
     *
     * Tope de envío: después de cada cambio, el otro hilo envía a lo sumo
     * MUESTRAS_POR_CAMBIO muestras y espera el cambio siguiente. Sin tope,
     * la prueba agotaba la memoria en algunas corridas: el Timer saca puntos
     * de la cola hasta dejarla vacía, y con un productor casi igual de rápido
     * un ciclo del Timer llegó a durar 98 s (72 millones de puntos). Mientras
     * tanto no había cambios de canal, que son los que vacían el historial,
     * y el historial creció hasta llenar los 8 GB. Con el tope, la cola y el
     * historial nunca pasan de 20 000 puntos. El envío sigue siendo sin
     * pausas, millones de muestras por segundo mientras no llega al tope, y
     * el tope es menor que la ventana visible: un punto colado sigue a la
     * vista en la revisión del cambio siguiente.
     */
    private static void verificarCambiosConcurrentes() throws Exception {
        GraficaDePrueba g = crearGrafica(0);
        long siguienteMuestra = 0; // el tiempo sigue corriendo de una ronda a otra

        for (int ronda = 1; ronda <= RONDAS; ronda++) {
            AtomicBoolean enviando = new AtomicBoolean(true);
            AtomicLong enviadas = new AtomicLong();
            AtomicReference<Throwable> errorProductor = new AtomicReference<>();
            // Cambios hechos, para que el otro hilo sepa cuándo puede seguir,
            // y veces que llegó al tope y tuvo que esperar
            AtomicInteger cambiosHechos = new AtomicInteger();
            AtomicInteger esperasEnElTope = new AtomicInteger();
            long desde = siguienteMuestra;
            Thread productor = new Thread(() -> {
                long i = desde;
                int cambiosVistos = 0;
                int desdeElCambio = 0;
                while (enviando.get()) {
                    int hechos = cambiosHechos.get();
                    if (hechos != cambiosVistos) {
                        cambiosVistos = hechos;
                        desdeElCambio = 0;
                    }
                    if (desdeElCambio < MUESTRAS_POR_CAMBIO) {
                        g.grafica().muestraRecibida(muestra(tiempoRapido(i)));
                        i++;
                        desdeElCambio++;
                        if (desdeElCambio == MUESTRAS_POR_CAMBIO) {
                            esperasEnElTope.incrementAndGet();
                        }
                    } else {
                        Thread.onSpinWait(); // en el tope: espera el cambio siguiente
                    }
                }
                enviadas.set(i - desde);
            }, "Muestreador de prueba");
            productor.setUncaughtExceptionHandler(guardarEImprimir(errorProductor));

            Random azar = new Random(ronda);
            int canal = enSwing(() -> g.grafica().getCanal());
            int puntosAjenos = 0;
            int cambiosConDibujo = 0;
            long inicio = System.nanoTime();
            productor.start();
            for (int k = 0; k < CAMBIOS_POR_RONDA; k++) {
                int actual = canal;
                // Siempre un canal distinto del actual
                int nuevo = (actual + 1 + azar.nextInt(CANALES - 1)) % CANALES;
                int[] resultado = enSwing(() -> {
                    XYSeries serie = g.serie();
                    int ajenos = ajenos(serie, valorCanal(actual));
                    int dibujados = serie.getItemCount();
                    g.grafica().cambiarCanal(nuevo);
                    cambiosHechos.incrementAndGet(); // el otro hilo puede enviar otra tanda
                    ajenos += serie.getItemCount(); // debe quedar vacía
                    ajenos += ajenos(g.grafica().getHistorial(), valorCanal(nuevo));
                    return new int[]{ajenos, dibujados > 0 ? 1 : 0};
                });
                puntosAjenos += resultado[0];
                cambiosConDibujo += resultado[1];
                canal = nuevo;
                // Pausa corta y variable: el cambio cae en momentos distintos
                // del registro de muestras, y el Timer alcanza a dibujar
                // entre algunos cambios
                pausar(azar.nextInt(400));
            }
            enviando.set(false);
            productor.join();
            double segundos = (System.nanoTime() - inicio) / 1e9;

            // Unas muestras más, ya sin cambios, para esperar a que se dibujen
            long fin = desde + enviadas.get();
            for (long i = fin; i < fin + 10; i++) {
                g.grafica().muestraRecibida(muestra(tiempoRapido(i)));
            }
            siguienteMuestra = fin + 10;
            boolean dibujo = esperarDibujo(g, tiempoRapido(fin + 9));
            Foto alFinal = enSwing(() -> foto(g));
            int ultimo = canal;
            boolean finalCorrecto = dibujo && alFinal.canal() == ultimo
                    && !alFinal.historial().isEmpty() && !alFinal.valoresSerie().isEmpty()
                    && soloVale(valores(alFinal.historial()), valorCanal(ultimo))
                    && soloVale(alFinal.valoresSerie(), valorCanal(ultimo));

            verificar(String.format("Ronda %d de %d: %d cambios de canal mientras otro hilo envía "
                            + "muestras sin parar (%,d muestras en %.1f s, a lo sumo %,d por cambio; "
                            + "llegó a ese tope %d veces; el Timer dibujó entre cambios %d veces): "
                            + "ningún punto de otro canal, y al final solo valores del último "
                            + "canal elegido (A%d)",
                            ronda, RONDAS, CAMBIOS_POR_RONDA, enviadas.get(), segundos,
                            MUESTRAS_POR_CAMBIO, esperasEnElTope.get(), cambiosConDibujo, ultimo),
                    errorProductor.get() == null && puntosAjenos == 0 && finalCorrecto,
                    errorProductor.get() != null ? errorProductor.get().toString()
                            : puntosAjenos + " puntos de otro canal durante los cambios; al final: "
                            + describir(alFinal));
        }
    }

    // ===================== Utilidades de I7LV-16 =====================

    /**
     * Una gráfica analógica real dentro de su pestaña, que nunca se muestra.
     * Los métodos leen lo que la gráfica de verdad dibuja; se llaman en el
     * hilo de Swing.
     */
    private record GraficaDePrueba(PanelSenal panel, GraficaContada grafica) {

        JFreeChart chart() {
            return ((ChartPanel) panel.getPanelGrafica().getComponent(0)).getChart();
        }

        XYSeries serie() {
            return ((XYSeriesCollection) chart().getXYPlot().getDataset()).getSeries(0);
        }
    }

    /**
     * La GraficaAnalogica del programa, que además cuenta cuántas veces le
     * piden cambiar de canal. Sirve para ver qué le pide ControlSeleccion.
     */
    private static final class GraficaContada extends GraficaAnalogica {

        /** Veces que se llamó cambiarCanal(). Solo se usa en el hilo de Swing. */
        int pedidosDeCambio = 0;

        GraficaContada(PanelSenal panel, int canal) {
            this(MUESTREADOR, panel, canal);
        }

        /** Con otro Muestreador, que la prueba sí inicia (I7LV-18 fase B). */
        GraficaContada(Muestreador muestreador, PanelSenal panel, int canal) {
            super(muestreador, panel, canal);
        }

        @Override
        public void cambiarCanal(int nuevoCanal) {
            pedidosDeCambio++;
            super.cambiarCanal(nuevoCanal);
        }
    }

    /**
     * Lo que muestra una gráfica en un instante. Dos fotos son iguales
     * (equals) si tienen el mismo canal, título, serie, historial y eje X.
     */
    private record Foto(int canal, String titulo, List<Double> valoresSerie,
                        List<SerieEnVivo.Punto> historial, double inicioEje, double finEje) {
    }

    /** Crea, en el hilo de Swing, una pestaña con los canales A0 a A7 y su gráfica. */
    private static GraficaDePrueba crearGrafica(int canal) throws Exception {
        return enSwing(() -> {
            String[] nombres = new String[CANALES];
            for (int i = 0; i < CANALES; i++) {
                nombres[i] = "A" + i;
            }
            PanelSenal panel = new PanelSenal(nombres);
            return new GraficaDePrueba(panel, new GraficaContada(panel, canal));
        });
    }

    /** Toma la foto de la gráfica. Se llama en el hilo de Swing. */
    private static Foto foto(GraficaDePrueba g) {
        XYSeries serie = g.serie();
        List<Double> valores = new ArrayList<>(serie.getItemCount());
        for (int i = 0; i < serie.getItemCount(); i++) {
            valores.add(serie.getY(i).doubleValue());
        }
        Range ejeX = g.chart().getXYPlot().getDomainAxis().getRange();
        return new Foto(g.grafica().getCanal(), g.chart().getTitle().getText(), valores,
                g.grafica().getHistorial(), ejeX.getLowerBound(), ejeX.getUpperBound());
    }

    /**
     * Ejecuta la tarea en el hilo de Swing, espera a que termine y devuelve
     * su resultado.
     */
    private static <T> T enSwing(Supplier<T> tarea) throws Exception {
        AtomicReference<T> resultado = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> resultado.set(tarea.get()));
        return resultado.get();
    }

    /**
     * Envía las muestras desde (incluida) hasta (excluida) mientras el hilo
     * de Swing está ocupado, y enseguida, sin soltarlo, ejecuta ahí la
     * acción. Así el Timer no alcanza a dibujar esas muestras: siguen en la
     * cola, pendientes, cuando llega la acción.
     *
     * @return la foto de justo antes de la acción y la de justo después
     */
    private static List<Foto> enviarYCambiarSinDibujar(GraficaDePrueba g, int desde, int hasta,
                                                       Runnable accion) throws Exception {
        CountDownLatch swingOcupado = new CountDownLatch(1);
        CountDownLatch enviadas = new CountDownLatch(1);
        AtomicReference<List<Foto>> fotos = new AtomicReference<>();
        SwingUtilities.invokeLater(() -> {
            swingOcupado.countDown();
            try {
                enviadas.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Foto previa = foto(g);
            accion.run();
            fotos.set(List.of(previa, foto(g)));
        });
        swingOcupado.await();
        enviar(g, desde, hasta);
        enviadas.countDown();
        // Swing atiende las tareas en orden: cuando termina esta, ya terminó la anterior
        enSwing(() -> null);
        return fotos.get();
    }

    /** Envía a la gráfica las muestras desde (incluida) hasta (excluida), como el Muestreador. */
    private static void enviar(GraficaDePrueba g, int desde, int hasta) {
        for (int i = desde; i < hasta; i++) {
            g.grafica().muestraRecibida(muestra(tiempo(i)));
        }
    }

    /** Muestra sintética: el canal i vale i × 0,5 V. */
    private static Muestra muestra(double tiempo) {
        return new Muestra(tiempo, ANALOGICAS, DIGITALES);
    }

    /** Tiempo de la muestra número i de la prueba con dos hilos: 1 ms entre muestras. */
    private static double tiempoRapido(long i) {
        return i / 1000.0;
    }

    private static double valorCanal(int canal) {
        return canal * 0.5;
    }

    /**
     * Espera a que el Timer de la gráfica (cada 50 ms) dibuje hasta la
     * muestra con ese tiempo. Espera como mucho 5 s.
     *
     * @return true si se dibujó
     */
    private static boolean esperarDibujo(GraficaDePrueba g, double ultimoTiempo) throws Exception {
        long limite = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < limite) {
            boolean dibujado = enSwing(() -> {
                XYSeries serie = g.serie();
                int n = serie.getItemCount();
                return n > 0 && serie.getX(n - 1).doubleValue() == ultimoTiempo;
            });
            if (dibujado) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }

    /** Deja pasar tres ciclos del Timer (150 ms) y espera a que Swing los atienda. */
    private static void esperarCiclosDelTimer() throws Exception {
        Thread.sleep(150);
        enSwing(() -> null);
    }

    /** Espera activa de unos microsegundos (Thread.sleep no baja de 1 ms). */
    private static void pausar(int microsegundos) {
        long hasta = System.nanoTime() + microsegundos * 1000L;
        while (System.nanoTime() < hasta) {
            Thread.onSpinWait();
        }
    }

    /** Intenta cambiar a un canal y dice si se rechazó con IllegalArgumentException. */
    private static boolean rechaza(CanalSeleccionable grafica, int canal) {
        try {
            grafica.cambiarCanal(canal);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static List<Double> valores(List<SerieEnVivo.Punto> puntos) {
        List<Double> valores = new ArrayList<>(puntos.size());
        for (SerieEnVivo.Punto p : puntos) {
            valores.add(p.valor());
        }
        return valores;
    }

    private static boolean soloVale(List<Double> valores, double valor) {
        for (double v : valores) {
            if (v != valor) {
                return false;
            }
        }
        return true;
    }

    /** Cuántos puntos de la serie no valen lo esperado. */
    private static int ajenos(XYSeries serie, double valor) {
        int cuenta = 0;
        for (int i = 0; i < serie.getItemCount(); i++) {
            if (serie.getY(i).doubleValue() != valor) {
                cuenta++;
            }
        }
        return cuenta;
    }

    /** Cuántos puntos del historial no valen lo esperado. */
    private static int ajenos(List<SerieEnVivo.Punto> historial, double valor) {
        int cuenta = 0;
        for (SerieEnVivo.Punto p : historial) {
            if (p.valor() != valor) {
                cuenta++;
            }
        }
        return cuenta;
    }

    private static String describir(Foto f) {
        return "canal " + f.canal() + ", título \"" + f.titulo() + "\", serie con "
                + f.valoresSerie().size() + " puntos, historial con " + f.historial().size();
    }

    private static String rango(Foto f) {
        return String.format("el eje X va de %.2f a %.2f s", f.inicioEje(), f.finEje());
    }

    /**
     * Fuente que nunca se usa: el Muestreador de las pruebas de I7LV-16 no se
     * inicia. Si algo la llamara, sería un error de la prueba.
     */
    private static final class FuenteSinUso implements FuenteDeDatos {

        @Override
        public void iniciar() {
            throw sinUso();
        }

        @Override
        public void detener() {
            throw sinUso();
        }

        @Override
        public double[] leerAnalogicas() {
            throw sinUso();
        }

        @Override
        public boolean[] leerDigitales() {
            throw sinUso();
        }

        @Override
        public void escribirSalida(int canal, boolean encendida) {
            throw sinUso();
        }

        @Override
        public void fijarTiempoMuestreo(int milisegundos) {
            throw sinUso();
        }

        private static UnsupportedOperationException sinUso() {
            return new UnsupportedOperationException("La prueba no inicia el muestreo");
        }
    }

    // ===================== I7LV-20: gráfica digital =====================

    private static void verificarRegistroDigital() throws Exception {
        DigitalDePrueba g = crearDigital(0);
        int hasta = 5 * MUESTRAS_POR_SEGUNDO + 1;
        enviarDigital(g, 0, hasta, VerificacionSprint2::patron);
        boolean dibujo = esperarDibujoDigital(g, tiempo(hasta - 1));

        // La gráfica entrega el historial de la señal seleccionada: se elige
        // cada una y se toma el suyo
        List<List<SerieEnVivo.Punto>> historiales = historialesDigitales(g);
        int malos = 0;
        for (int canal = 0; canal < CANALES_DIGITALES; canal++) {
            malos += diferencias(historiales.get(canal), canal, hasta, VerificacionSprint2::patron);
        }
        verificar("Se registran los 4 canales: los historiales de D0, D1, D2 y D3 tienen las 501 "
                        + "muestras, una por muestra, cada una con su tiempo y su bit",
                dibujo && malos == 0,
                malos + " puntos no coinciden; tamaños " + tamanos(historiales));

        boolean inmutable = false;
        try {
            historiales.get(0).add(new SerieEnVivo.Punto(99, 1));
        } catch (UnsupportedOperationException e) {
            inmutable = true;
        }
        verificar("La copia del historial no se puede modificar", inmutable, "aceptó un punto nuevo");

        // Llegan muestras que la gráfica todavía no dibuja
        enviarDigital(g, hasta, hasta + 2, VerificacionSprint2::patron);
        int nuevo = enSwing(() -> g.grafica().getHistorial().size());
        verificar("Una copia ya entregada no cambia cuando llegan más muestras, y una nueva las incluye",
                historiales.get(0).size() == hasta && nuevo == hasta + 2,
                "la entregada tiene " + historiales.get(0).size() + " y la nueva " + nuevo);
    }

    private static void verificarHexadecimal() throws Exception {
        String digitos = "0123456789ABCDEF";
        boolean todos = true;
        for (int valor = 0; valor < 16; valor++) {
            if (!RendererBus.hexadecimal(valor).equals(String.valueOf(digitos.charAt(valor)))) {
                todos = false;
            }
        }
        verificar("El texto de cada tramo del carril Valor es el valor en hexadecimal: 0 es \"0\", "
                        + "5 es \"5\", 10 es \"A\" y 15 es \"F\" (se revisan los 16)",
                todos, "algún valor no da su dígito");

        // De punta a punta: los bits se escriben como D3 D2 D1 D0 y se lee lo
        // que dibuja la gráfica, 3 muestras por caso
        String[] casos = {"0000", "0101", "1010", "1111", "0001", "1000", "0110"};
        DigitalDePrueba g = crearDigital(0);
        int i = 0;
        for (String caso : casos) {
            for (int k = 0; k < 3; k++) {
                g.grafica().muestraRecibida(muestraBits(tiempo(i), caso));
                i++;
            }
        }
        boolean dibujo = esperarDibujoDigital(g, tiempo(i - 1));
        List<List<String>> leido = enSwing(() -> {
            List<String> textos = new ArrayList<>();
            List<String> bits = new ArrayList<>();
            for (int k = 0; k < casos.length; k++) {
                double t = tiempo(3 * k + 1); // la muestra del medio de cada caso
                textos.add(RendererBus.hexadecimal((int) valorEn(g.serie(CARRIL_VALOR), t)));
                StringBuilder d3d2d1d0 = new StringBuilder();
                for (int canal = CANALES_DIGITALES - 1; canal >= 0; canal--) {
                    d3d2d1d0.append((int) valorEn(g.serie(canal), t));
                }
                bits.add(d3d2d1d0.toString());
            }
            return List.of(textos, bits);
        });
        verificar("Con D3 D2 D1 D0 = 0000, 0101, 1010, 1111, 0001, 1000 y 0110, el carril Valor "
                        + "muestra 0, 5, A, F, 1, 8 y 6: D3 es el bit más significativo",
                dibujo && leido.get(0).equals(List.of("0", "5", "A", "F", "1", "8", "6")),
                "muestra " + leido.get(0));
        verificar("... y los carriles D3, D2, D1 y D0 dibujan esos mismos bits, cada uno en su carril",
                leido.get(1).equals(List.of(casos)), "dibujan " + leido.get(1));
    }

    private static void verificarSeleccionDigital() throws Exception {
        DigitalDePrueba g = crearDigital(0);
        int hasta = 5 * MUESTRAS_POR_SEGUNDO + 1;
        enviarDigital(g, 0, hasta, VerificacionSprint2::patron);
        esperarDibujoDigital(g, tiempo(hasta - 1));

        FotoDigital inicial = enSwing(() -> fotoDigital(g));
        verificar("Al comenzar está seleccionada D0: título \"Seleccionada: D0\", D0 en rojo y con "
                        + "trazo más grueso, D1 a D3 en gris oscuro",
                inicial.canal() == 0 && inicial.titulo().equals("Seleccionada: D0")
                        && resaltada(inicial, 0),
                describir(inicial));

        // Las dos fotos en la misma tarea de Swing: el Timer no puede cambiar
        // nada entre una y otra
        List<FotoDigital> cambio = enSwing(() -> {
            FotoDigital previa = fotoDigital(g);
            g.grafica().cambiarCanal(2);
            return List.of(previa, fotoDigital(g));
        });
        FotoDigital antes = cambio.get(0);
        FotoDigital tras = cambio.get(1);
        verificar("Cambiar de D0 a D2 no borra la gráfica: las 5 series dibujadas y el eje X quedan igual",
                !antes.series().get(0).isEmpty() && tras.series().equals(antes.series())
                        && tras.inicioEje() == antes.inicioEje() && tras.finEje() == antes.finEje(),
                "antes: " + describir(antes) + "; después: " + describir(tras));
        verificar("... y el historial entregado pasa a ser el de D2, completo: 501 muestras con los bits de D2",
                diferencias(tras.historial(), 2, hasta, VerificacionSprint2::patron) == 0,
                describir(tras));
        verificar("... y solo cambian el título (\"Seleccionada: D2\") y el resaltado: D2 en rojo y "
                        + "grueso, D0 de vuelta a gris oscuro",
                tras.canal() == 2 && tras.titulo().equals("Seleccionada: D2") && resaltada(tras, 2),
                describir(tras));

        // Siguen llegando muestras con D2 seleccionada
        int mas = hasta + 2 * MUESTRAS_POR_SEGUNDO;
        enviarDigital(g, hasta, mas, VerificacionSprint2::patron);
        esperarDibujoDigital(g, tiempo(mas - 1));
        List<List<SerieEnVivo.Punto>> historiales = historialesDigitales(g);
        int malos = 0;
        for (int canal = 0; canal < CANALES_DIGITALES; canal++) {
            malos += diferencias(historiales.get(canal), canal, mas, VerificacionSprint2::patron);
        }
        verificar("Las muestras siguientes se registran en los 4 canales, no solo en el "
                        + "seleccionado: al elegir cada uno se entrega su historial completo (701)",
                malos == 0, malos + " puntos no coinciden; tamaños " + tamanos(historiales));

        List<FotoDigital> mismo = enSwing(() -> {
            FotoDigital previa = fotoDigital(g);
            g.grafica().cambiarCanal(2);
            return List.of(previa, fotoDigital(g));
        });
        verificar("Volver a elegir D2 no cambia nada",
                mismo.get(0).equals(mismo.get(1)) && mismo.get(1).canal() == 2,
                "antes: " + describir(mismo.get(0)) + "; después: " + describir(mismo.get(1)));

        boolean[] rechazado = new boolean[2];
        List<FotoDigital> fuera = enSwing(() -> {
            FotoDigital previa = fotoDigital(g);
            rechazado[0] = rechaza(g.grafica(), CANALES_DIGITALES); // 4: no existe D4
            rechazado[1] = rechaza(g.grafica(), -1);
            return List.of(previa, fotoDigital(g));
        });
        verificar("Una señal fuera de rango (4 y -1) se rechaza con IllegalArgumentException y todo "
                        + "sigue igual, con D2 seleccionada",
                rechazado[0] && rechazado[1] && fuera.get(0).equals(fuera.get(1)),
                "4 " + (rechazado[0] ? "rechazado" : "aceptado") + ", -1 "
                        + (rechazado[1] ? "rechazado" : "aceptado"));

        boolean[] creacion = enSwing(() -> new boolean[]{
                rechazaCrear(new PanelSenal(new String[]{"A0", "A1", "A2", "A3", "A4", "A5", "A6", "A7"}), 0),
                rechazaCrear(new PanelSenal(new String[]{"D0", "D1", "D2", "D3"}), 4)});
        verificar("La gráfica no se crea en una pestaña cuyo selector no tiene 4 señales, ni con "
                        + "una señal inicial que no existe",
                creacion[0] && creacion[1],
                "8 señales " + (creacion[0] ? "rechazada" : "aceptada") + ", señal 4 "
                        + (creacion[1] ? "rechazada" : "aceptada"));
    }

    private static void verificarVentanaDigital() throws Exception {
        SenalesDigitalesEnVivo datos = new SenalesDigitalesEnVivo(CANALES_DIGITALES, VENTANA_S);
        verificar("Sin datos, el eje X va de 0 a 30 s",
                datos.getInicioVentana() == 0.0 && datos.getFinVentana() == VENTANA_S,
                rango(datos));

        // D3 fija en 1; D0 a D2 con el patrón
        alimentarDigital(datos, 0, 10 * MUESTRAS_POR_SEGUNDO + 1, VerificacionSprint2::patronConD3);
        verificar("A los 10 s, el eje X sigue de 0 a 30 s",
                datos.getInicioVentana() == 0.0 && datos.getFinVentana() == VENTANA_S,
                rango(datos));

        int total = 45 * MUESTRAS_POR_SEGUNDO + 1;
        alimentarDigital(datos, 10 * MUESTRAS_POR_SEGUNDO + 1, total, VerificacionSprint2::patronConD3);
        verificar("A los 45 s, el eje X se deslizó: va de 15 a 45 s, la misma ventana de la "
                        + "gráfica analógica",
                datos.getInicioVentana() == 15.0 && datos.getFinVentana() == 45.0, rango(datos));

        int malos = 0;
        int enBorde = 15 * MUESTRAS_POR_SEGUNDO; // la muestra de los 15,00 s
        for (int carril = 0; carril <= CARRIL_VALOR; carril++) {
            XYSeries serie = serieDe(datos, carril);
            for (int k = 1; k < serie.getItemCount(); k++) {
                if (serie.getX(k).doubleValue() < 15.0) {
                    malos++; // solo el primero puede quedar antes de la ventana
                }
            }
            if (serie.getX(serie.getItemCount() - 1).doubleValue() != 45.0
                    || valorEn(serie, 15.0) != esperado(patronConD3(enBorde), carril)) {
                malos++;
            }
        }
        verificar("A los 45 s, cada carril solo tiene puntos de los últimos 30 s, más el último "
                        + "cambio anterior, que da el valor en el borde izquierdo",
                malos == 0, malos + " fallas");

        XYSeries fija = datos.getSerieCanal(3);
        verificar("La señal fija (D3 en 1 los 45 s) se dibuja en toda la ventana con solo 2 puntos: "
                        + "su último cambio (0 s) y el punto final (45 s)",
                puntos(fija).equals(List.of(new SerieEnVivo.Punto(0.0, 1.0),
                        new SerieEnVivo.Punto(45.0, 1.0))),
                "tiene " + puntos(fija));

        boolean completos = true;
        for (int canal = 0; canal < CANALES_DIGITALES; canal++) {
            if (datos.getHistorial(canal).size() != total) {
                completos = false;
            }
        }
        verificar("Los historiales conservan los 45 s completos (4501 muestras por canal)",
                completos, "tamaño de D0: " + datos.getHistorial(0).size());

        DigitalDePrueba g = crearDigital(0);
        enviarDigital(g, 0, total, VerificacionSprint2::patronConD3);
        boolean dibujo = esperarDibujoDigital(g, 45.0);
        FotoDigital a45 = enSwing(() -> fotoDigital(g));
        verificar("En la gráfica real, a los 45 s el eje X compartido va de 15 a 45 s",
                dibujo && a45.inicioEje() == 15.0 && a45.finEje() == 45.0, rango(a45));
    }

    private static void verificarReinicioDigital() throws Exception {
        SenalesDigitalesEnVivo datos = new SenalesDigitalesEnVivo(CANALES_DIGITALES, VENTANA_S);
        int total = 45 * MUESTRAS_POR_SEGUNDO + 1;
        alimentarDigital(datos, 0, total, VerificacionSprint2::patron);

        // Nuevo Iniciar: el tiempo vuelve a empezar desde cero
        datos.agregar(0.0, 0b1010);
        boolean vacios = true;
        for (int canal = 0; canal < CANALES_DIGITALES; canal++) {
            vacios &= datos.getHistorial(canal).size() == 1;
        }
        verificar("Una muestra con tiempo menor (nuevo Iniciar) vacía los historiales en el acto",
                vacios, "D0 tiene " + datos.getHistorial(0).size() + " puntos");
        datos.procesarPendientes();
        boolean series = true;
        for (int carril = 0; carril <= CARRIL_VALOR; carril++) {
            List<SerieEnVivo.Punto> p = puntos(serieDe(datos, carril));
            series &= p.equals(List.of(new SerieEnVivo.Punto(0.0, esperado(0b1010, carril))));
        }
        verificar("... y vacía los 5 carriles antes de agregarla, y el eje X vuelve a ir de 0 a 30 s",
                series && datos.getInicioVentana() == 0.0 && datos.getFinVentana() == VENTANA_S,
                "Valor: " + puntos(datos.getSerieValor()) + ", " + rango(datos));

        // Detener e Iniciar entre dos ciclos del Timer
        SenalesDigitalesEnVivo mezcla = new SenalesDigitalesEnVivo(CANALES_DIGITALES, VENTANA_S);
        alimentarDigital(mezcla, 0, total, VerificacionSprint2::patron);
        mezcla.agregar(tiempo(total), 0b1111);
        mezcla.agregar(tiempo(total + 1), 0b1111);
        mezcla.agregar(0.0, 0b0101);
        mezcla.agregar(0.01, 0b0101);
        mezcla.agregar(0.02, 0b0110);
        mezcla.procesarPendientes();
        int malos = 0;
        for (int carril = 0; carril <= CARRIL_VALOR; carril++) {
            XYSeries serie = serieDe(mezcla, carril);
            if (serie.getMinX() != 0.0 || serie.getMaxX() != 0.02
                    || valorEn(serie, 0.01) != esperado(0b0101, carril)
                    || valorEn(serie, 0.02) != esperado(0b0110, carril)) {
                malos++;
            }
        }
        verificar("Con muestras de las dos corridas en un mismo ciclo, solo quedan las de la nueva",
                malos == 0 && mezcla.getHistorial(0).size() == 3,
                malos + " carriles mal; historial de D0 con " + mezcla.getHistorial(0).size());

        // En la gráfica real, con D3 seleccionada
        DigitalDePrueba g = crearDigital(3);
        enviarDigital(g, 0, total, VerificacionSprint2::patron);
        esperarDibujoDigital(g, 45.0);
        int hasta = 5 * MUESTRAS_POR_SEGUNDO + 1;
        enviarDigital(g, 0, hasta, VerificacionSprint2::patron);
        boolean dibujo = esperarDibujoDigital(g, tiempo(hasta - 1));
        FotoDigital iniciado = enSwing(() -> fotoDigital(g));
        verificar("En la gráfica real, Detener e Iniciar reinicia: eje X de 0 a 30 s, historial "
                        + "desde 0 s, y D3 sigue seleccionada",
                dibujo && iniciado.inicioEje() == 0.0 && iniciado.finEje() == VENTANA_S
                        && iniciado.canal() == 3 && resaltada(iniciado, 3)
                        && diferencias(iniciado.historial(), 3, hasta, VerificacionSprint2::patron) == 0,
                describir(iniciado) + ", " + rango(iniciado));
    }

    /**
     * Las series dibujadas solo guardan los cambios y el último punto. Se
     * revisa que la onda en escalones que dibujan pase por el valor enviado
     * en el tiempo de CADA muestra: si se perdiera un flanco, algún tiempo
     * tendría el valor equivocado.
     */
    private static void verificarSoloCambios() {
        // Tramos de 5 s que alternan: en unos, cada bit cambia en la mitad de
        // las muestras (muchos pulsos de una sola muestra); en otros, casi nunca
        Random azar = new Random(20);
        int total = 60 * MUESTRAS_POR_SEGUNDO + 1;
        int[] bits = new int[total];
        for (int i = 1; i < total; i++) {
            boolean agitado = (i / (5 * MUESTRAS_POR_SEGUNDO)) % 2 == 0;
            int cambian = agitado ? azar.nextInt(16)
                    : (azar.nextInt(100) < 2 ? 1 << azar.nextInt(4) : 0);
            bits[i] = bits[i - 1] ^ cambian;
        }

        // Ciclos del Timer de 1 a 20 muestras, al azar
        SenalesDigitalesEnVivo datos = new SenalesDigitalesEnVivo(CANALES_DIGITALES, VENTANA_S);
        int primeros = 25 * MUESTRAS_POR_SEGUNDO + 1; // dentro de la primera ventana
        int i = alimentarAlAzar(datos, bits, 0, primeros, azar);

        int errores = 0;
        int puntosDeMas = 0;
        StringBuilder tamanos = new StringBuilder();
        for (int carril = 0; carril <= CARRIL_VALOR; carril++) {
            XYSeries serie = serieDe(datos, carril);
            int flancos = 0;
            for (int j = 0; j < primeros; j++) {
                if (valorEn(serie, tiempo(j)) != esperado(bits[j], carril)) {
                    errores++;
                }
                if (j > 0 && esperado(bits[j], carril) != esperado(bits[j - 1], carril)) {
                    flancos++;
                }
            }
            boolean finalCambio = esperado(bits[primeros - 1], carril)
                    != esperado(bits[primeros - 2], carril);
            int esperados = 1 + flancos + (finalCambio ? 0 : 1);
            puntosDeMas += Math.abs(serie.getItemCount() - esperados);
            tamanos.append(carril == CARRIL_VALOR ? "Valor " : "D" + carril + " ")
                    .append(serie.getItemCount()).append(carril < CARRIL_VALOR ? ", " : "");
        }
        verificar("Guardar solo los cambios no pierde flancos: en los 5 carriles, la onda dibujada "
                        + "pasa por el valor de cada una de las 2501 muestras, incluidos pulsos de una "
                        + "sola muestra, con ciclos del Timer de 1 a 20 muestras",
                errores == 0, errores + " muestras con otro valor");
        verificar("... y cada serie tiene exactamente un punto por flanco, más el primero y el final ("
                        + tamanos + " puntos, contra 2501 muestras del historial)",
                puntosDeMas == 0, puntosDeMas + " puntos de más o de menos");

        // Hasta 60 s: la ventana ya se desliza y se quitan puntos viejos
        alimentarAlAzar(datos, bits, i, total, azar);
        double inicio = datos.getInicioVentana();
        int erroresVentana = 0;
        int ultimaAntes = (int) Math.round(inicio * MUESTRAS_POR_SEGUNDO); // muestra del borde
        for (int carril = 0; carril <= CARRIL_VALOR; carril++) {
            XYSeries serie = serieDe(datos, carril);
            for (int j = ultimaAntes; j < total; j++) {
                if (valorEn(serie, tiempo(j)) != esperado(bits[j], carril)) {
                    erroresVentana++;
                }
            }
            if (valorEn(serie, inicio) != esperado(bits[ultimaAntes], carril)) {
                erroresVentana++;
            }
        }
        verificar("... y también a los 60 s, con la ventana deslizándose: todas las muestras de "
                        + "los últimos 30 s coinciden, también en el borde izquierdo",
                inicio == 30.0 && erroresVentana == 0,
                erroresVentana + " fallas, " + rango(datos));
    }

    private static void verificarCargaDigital() throws Exception {
        final int total = 10_000; // 100 s de muestreo a 10 ms
        Random azar = new Random(30);
        int[] alAzar = new int[total];
        int[] peorCaso = new int[total];
        int estado = 0;
        for (int i = 0; i < total; i++) {
            for (int canal = 0; canal < CANALES_DIGITALES; canal++) {
                if (azar.nextDouble() < 0.1) {
                    estado ^= 1 << canal;
                }
            }
            alAzar[i] = estado;
            peorCaso[i] = i % 2 == 0 ? 0b0000 : 0b1111;
        }
        String[] nombres = {"con bits al azar (cada bit cambia en el 10 % de las muestras)",
                "en el peor caso (los 4 bits cambian en cada muestra)"};
        int[][] casos = {alAzar, peorCaso};

        for (int c = 0; c < casos.length; c++) {
            SenalesDigitalesEnVivo datos = new SenalesDigitalesEnVivo(CANALES_DIGITALES, VENTANA_S);
            long peorCicloNanos = 0;
            long inicio = System.nanoTime();
            for (int i = 0; i < total; i++) {
                datos.agregar(tiempo(i), casos[c][i]);
                if ((i + 1) % MUESTRAS_POR_CICLO == 0) {
                    long t0 = System.nanoTime();
                    datos.procesarPendientes();
                    peorCicloNanos = Math.max(peorCicloNanos, System.nanoTime() - t0);
                }
            }
            datos.procesarPendientes();
            double promedioMs = (System.nanoTime() - inicio) / 1e6 / (total / MUESTRAS_POR_CICLO);
            verificar(String.format("10 000 muestras a 10 ms %s: cada ciclo del Timer tarda mucho "
                                    + "menos que 50 ms (promedio %.3f ms, el peor %.1f ms)",
                            nombres[c], promedioMs, peorCicloNanos / 1e6),
                    promedioMs < 1.0 && peorCicloNanos / 1e6 < 50.0
                            && datos.getHistorial(0).size() == total,
                    "demasiado lento");
        }

        // Dibujar la gráfica real, como lo hace el ChartPanel (con la
        // información de entidades), al tamaño que tiene en la ventana de
        // 1000 × 700
        for (int c = 0; c < casos.length; c++) {
            DigitalDePrueba g = crearDigital(0);
            int hasta = 40 * MUESTRAS_POR_SEGUNDO;
            int[] caso = casos[c];
            enviarDigital(g, 0, hasta, i -> caso[i]);
            esperarDibujoDigital(g, tiempo(hasta - 1));
            BufferedImage imagen = new BufferedImage(1000, 560, BufferedImage.TYPE_INT_RGB);
            double promedioMs = 0;
            for (int k = 0; k < 25; k++) {
                long t0 = System.nanoTime();
                enSwing(() -> {
                    Graphics2D g2 = imagen.createGraphics();
                    g.chart().draw(g2, new Rectangle(0, 0, 1000, 560), null, new ChartRenderingInfo());
                    g2.dispose();
                    return null;
                });
                if (k >= 5) { // los 5 primeros calientan la máquina virtual
                    promedioMs += (System.nanoTime() - t0) / 1e6 / 20;
                }
            }
            verificar(String.format("Dibujar la gráfica (1000 × 560 px) con 30 s de datos a 10 ms "
                            + "%s tarda %.1f ms en promedio, menos que el ciclo de 50 ms",
                            nombres[c], promedioMs),
                    promedioMs < 50.0, "demasiado lento");
        }

        // Un ciclo del Timer con varias muestras nuevas: un solo redibujo
        DigitalDePrueba g = crearDigital(0);
        enviarDigital(g, 0, 101, VerificacionSprint2::patron);
        esperarDibujoDigital(g, tiempo(100));
        int[] avisos = {0};
        enSwing(() -> {
            g.chart().addChangeListener(e -> avisos[0]++);
            return null;
        });
        conSwingOcupado(() -> enviarDigital(g, 101, 101 + MUESTRAS_POR_CICLO,
                VerificacionSprint2::patron));
        boolean dibujo = esperarDibujoDigital(g, tiempo(100 + MUESTRAS_POR_CICLO));
        esperarCiclosDelTimer();
        int redibujos = enSwing(() -> avisos[0]);
        verificar("Un ciclo del Timer con 5 muestras nuevas redibuja la gráfica una sola vez, aunque "
                        + "cambien los 5 carriles y el eje X",
                dibujo && redibujos == 1, "se redibujó " + redibujos + " veces");
    }

    /**
     * Los historiales con dos hilos a la vez, sin la gráfica: uno agrega
     * lecturas sin parar (el Muestreador), con un nuevo Iniciar cada 200,
     * mientras otro procesa la cola y copia los historiales de los 4 canales
     * sin parar (el Timer y el guardado). Con reinicios tan seguidos, muchas
     * copias caen justo cuando el historial se vacía: si faltara el candado,
     * alguna saldría a medias o mezclaría dos corridas.
     *
     * El que agrega hace una pausa de 1 µs por lectura: sin ella termina en
     * una fracción de segundo y el otro hilo apenas alcanza a copiar.
     */
    private static void verificarHistorialConcurrente() throws InterruptedException {
        final int porCorrida = 200;
        final long total = 500_000;
        SenalesDigitalesEnVivo datos = new SenalesDigitalesEnVivo(CANALES_DIGITALES, VENTANA_S);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread productor = new Thread(() -> {
            for (long i = 0; i < total; i++) {
                long j = i % porCorrida;
                datos.agregar(tiempoRapido(j), patronRapido(j));
                pausar(1);
            }
        }, "Productor de prueba");
        productor.setUncaughtExceptionHandler(guardarEImprimir(error));

        int copias = 0;
        int incoherentes = 0;
        try {
            productor.start();
            while (productor.isAlive()) {
                datos.procesarPendientes();
                for (int canal = 0; canal < CANALES_DIGITALES; canal++) {
                    if (incoherencias(datos.getHistorial(canal), canal) > 0) {
                        incoherentes++;
                    }
                    copias++;
                }
            }
            productor.join();
            datos.procesarPendientes();
        } catch (RuntimeException e) {
            error.compareAndSet(null, e);
        }
        verificar(String.format("Con un hilo agregando (un nuevo Iniciar cada 200 muestras) y otro "
                                + "procesando y copiando los historiales sin parar, las %,d copias son "
                                + "coherentes: de una sola corrida, sin huecos y con los bits de su canal",
                        copias),
                error.get() == null && incoherentes == 0 && copias > 0
                        && datos.getHistorial(0).size() == porCorrida,
                error.get() != null ? error.get().toString()
                        : incoherentes + " copias incoherentes de " + copias);
    }

    /**
     * Un hilo envía muestras sin parar, como un Muestreador rápido, mientras
     * el hilo de Swing cambia la señal seleccionada 1000 veces. Se repite
     * varias rondas para que un error ocasional se note.
     *
     * En cada cambio, en la misma tarea de Swing, se revisa que el cambio no
     * toque las series dibujadas, que el título y el canal sean los nuevos, y
     * que el historial entregado sea coherente: el del canal nuevo, completo
     * desde el último Iniciar (tiempos 0, 1, 2... ms sin huecos) y con los
     * bits de ese canal. Al final de cada ronda, que los 5 carriles dibujen
     * exactamente lo enviado.
     *
     * El tiempo avanza 1 ms por muestra y vuelve a 0 cada 40 000 muestras (un
     * nuevo Iniciar), así la prueba también mezcla reinicios con los cambios.
     * Los bits de cada muestra salen de su número, con muchos cambios.
     */
    private static void verificarSeleccionConcurrente() throws Exception {
        DigitalDePrueba g = crearDigital(0);
        long siguienteMuestra = 0; // la numeración sigue de una ronda a otra

        for (int ronda = 1; ronda <= RONDAS; ronda++) {
            AtomicBoolean enviando = new AtomicBoolean(true);
            AtomicLong enviadas = new AtomicLong();
            AtomicReference<Throwable> errorProductor = new AtomicReference<>();
            long desde = siguienteMuestra;
            Thread productor = new Thread(() -> {
                long i = desde;
                while (enviando.get()) {
                    enviarRapida(g, i);
                    i++;
                    pausar(2); // unas 300 000 muestras por segundo
                }
                enviadas.set(i - desde);
            }, "Muestreador de prueba");
            productor.setUncaughtExceptionHandler(guardarEImprimir(errorProductor));

            Random azar = new Random(100 + ronda);
            int canal = enSwing(() -> g.grafica().getCanal());
            int fallas = 0;
            long inicio = System.nanoTime();
            productor.start();
            for (int k = 0; k < CAMBIOS_POR_RONDA; k++) {
                // Siempre una señal distinta de la actual
                int nuevo = (canal + 1 + azar.nextInt(CANALES_DIGITALES - 1)) % CANALES_DIGITALES;
                fallas += enSwing(() -> {
                    List<Integer> antes = tamanosSeries(g);
                    g.grafica().cambiarCanal(nuevo);
                    int mal = tamanosSeries(g).equals(antes) ? 0 : 1;
                    if (g.grafica().getCanal() != nuevo
                            || !g.chart().getTitle().getText().equals("Seleccionada: D" + nuevo)) {
                        mal++;
                    }
                    return mal + incoherencias(g.grafica().getHistorial(), nuevo);
                });
                canal = nuevo;
                pausar(azar.nextInt(400));
            }
            enviando.set(false);
            productor.join();
            double segundos = (System.nanoTime() - inicio) / 1e9;

            // 3000 muestras más, ya sin cambios, y se revisa lo dibujado
            long fin = desde + enviadas.get();
            for (long i = fin; i < fin + 3000; i++) {
                enviarRapida(g, i);
            }
            siguienteMuestra = fin + 3000;
            long ultima = (fin + 2999) % MUESTRAS_POR_CORRIDA;
            boolean dibujo = esperarDibujoDigital(g, tiempoRapido(ultima));
            int ultimo = canal;
            int fallasFinales = enSwing(() -> {
                List<SerieEnVivo.Punto> historial = g.grafica().getHistorial();
                int mal = historial.size() == ultima + 1 ? 0 : 1;
                mal += incoherencias(historial, ultimo);
                mal += dibujadoDistinto(g, historial);
                return mal + (resaltada(fotoDigital(g), ultimo) ? 0 : 1);
            });

            verificar(String.format("Ronda %d de %d: %d cambios de selección mientras otro hilo envía "
                                    + "muestras sin parar (%,d muestras en %.1f s): nada se borra, el "
                                    + "historial entregado siempre es el del canal elegido y está "
                                    + "completo, y al final los 5 carriles dibujan lo enviado (D%d resaltada)",
                            ronda, RONDAS, CAMBIOS_POR_RONDA, enviadas.get(), segundos, ultimo),
                    errorProductor.get() == null && fallas == 0 && dibujo && fallasFinales == 0,
                    errorProductor.get() != null ? errorProductor.get().toString()
                            : fallas + " fallas durante los cambios, " + fallasFinales + " al final");
        }
    }

    // ===================== Utilidades de I7LV-20 =====================

    /**
     * Una gráfica digital real dentro de su pestaña, que nunca se muestra.
     * Los métodos leen lo que la gráfica de verdad dibuja; se llaman en el
     * hilo de Swing.
     */
    private record DigitalDePrueba(PanelSenal panel, GraficaDigital grafica) {

        JFreeChart chart() {
            return ((ChartPanel) panel.getPanelGrafica().getComponent(0)).getChart();
        }

        /** Carril 0 a 3: D0 a D3; carril 4: Valor. */
        XYPlot carril(int carril) {
            return ((CombinedDomainXYPlot) chart().getPlot()).getSubplots().get(carril);
        }

        XYSeries serie(int carril) {
            return ((XYSeriesCollection) carril(carril).getDataset()).getSeries(0);
        }
    }

    /**
     * Lo que muestra la gráfica digital en un instante. Dos fotos son iguales
     * (equals) si tienen la misma selección, título, series, historial,
     * colores, grosores, colores de los nombres de los carriles y eje X.
     */
    private record FotoDigital(int canal, String titulo, List<List<SerieEnVivo.Punto>> series,
                               List<SerieEnVivo.Punto> historial, List<Paint> colores,
                               List<Float> grosores, List<Paint> coloresNombres,
                               double inicioEje, double finEje) {
    }

    /** Crea, en el hilo de Swing, una pestaña con D0 a D3 y su gráfica digital. */
    private static DigitalDePrueba crearDigital(int canal) throws Exception {
        return enSwing(() -> {
            PanelSenal panel = new PanelSenal(new String[]{"D0", "D1", "D2", "D3"});
            return new DigitalDePrueba(panel, new GraficaDigital(MUESTREADOR, panel, canal));
        });
    }

    /** Toma la foto de la gráfica digital. Se llama en el hilo de Swing. */
    private static FotoDigital fotoDigital(DigitalDePrueba g) {
        List<List<SerieEnVivo.Punto>> series = new ArrayList<>();
        for (int carril = 0; carril <= CARRIL_VALOR; carril++) {
            series.add(puntos(g.serie(carril)));
        }
        List<Paint> colores = new ArrayList<>();
        List<Float> grosores = new ArrayList<>();
        for (int canal = 0; canal < CANALES_DIGITALES; canal++) {
            XYItemRenderer renderer = g.carril(canal).getRenderer();
            colores.add(renderer.getSeriesPaint(0));
            grosores.add(((BasicStroke) renderer.getSeriesStroke(0)).getLineWidth());
        }
        Range ejeX = g.chart().getXYPlot().getDomainAxis().getRange();
        return new FotoDigital(g.grafica().getCanal(), g.chart().getTitle().getText(), series,
                g.grafica().getHistorial(), colores, grosores, coloresNombres(g),
                ejeX.getLowerBound(), ejeX.getUpperBound());
    }

    /**
     * Color del nombre de cada carril (el rótulo de su eje Y), de D0 a D3 y
     * Valor. Se llama en el hilo de Swing.
     */
    private static List<Paint> coloresNombres(DigitalDePrueba g) {
        List<Paint> colores = new ArrayList<>();
        for (int carril = 0; carril <= CARRIL_VALOR; carril++) {
            colores.add(g.carril(carril).getRangeAxis().getLabelPaint());
        }
        return colores;
    }

    /** Si la señal está en rojo y más gruesa, y las demás en gris oscuro, todas igual de delgadas. */
    private static boolean resaltada(FotoDigital f, int canal) {
        float delgado = f.grosores().get(canal == 0 ? 1 : 0); // el de otra señal
        for (int otro = 0; otro < CANALES_DIGITALES; otro++) {
            if (otro != canal && (!f.colores().get(otro).equals(Tema.GRIS_OSCURO)
                    || f.grosores().get(otro) != delgado)) {
                return false;
            }
        }
        return f.colores().get(canal).equals(Tema.ROJO) && f.grosores().get(canal) > delgado;
    }

    /** El historial de cada señal: se elige cada una y se toma el suyo; al final vuelve a la que estaba. */
    private static List<List<SerieEnVivo.Punto>> historialesDigitales(DigitalDePrueba g) throws Exception {
        return enSwing(() -> {
            int elegida = g.grafica().getCanal();
            List<List<SerieEnVivo.Punto>> lista = new ArrayList<>();
            for (int canal = 0; canal < CANALES_DIGITALES; canal++) {
                g.grafica().cambiarCanal(canal);
                lista.add(g.grafica().getHistorial());
            }
            g.grafica().cambiarCanal(elegida);
            return lista;
        });
    }

    /**
     * Cuántos puntos del historial de un canal no son los esperados: debe
     * tener las muestras 0 a cantidad - 1, cada una con su tiempo y su bit.
     * Un tamaño distinto cuenta como una diferencia más.
     */
    private static int diferencias(List<SerieEnVivo.Punto> historial, int canal, int cantidad,
                                   IntUnaryOperator bits) {
        int cuenta = historial.size() == cantidad ? 0 : 1;
        for (int i = 0; i < Math.min(cantidad, historial.size()); i++) {
            SerieEnVivo.Punto p = historial.get(i);
            if (p.tiempo() != tiempo(i) || p.valor() != esperado(bits.applyAsInt(i), canal)) {
                cuenta++;
            }
        }
        return cuenta;
    }

    /**
     * Cuántos puntos del historial no son coherentes en la prueba de
     * concurrencia: el punto j debe tener el tiempo de la muestra j de la
     * corrida (j ms) y el bit del canal en esa muestra.
     */
    private static int incoherencias(List<SerieEnVivo.Punto> historial, int canal) {
        int cuenta = 0;
        for (int j = 0; j < historial.size(); j++) {
            SerieEnVivo.Punto p = historial.get(j);
            if (p.tiempo() != tiempoRapido(j) || p.valor() != esperado(patronRapido(j), canal)) {
                cuenta++;
            }
        }
        return cuenta;
    }

    /**
     * Cuántos valores dibujados no son los enviados en una prueba de
     * concurrencia: en el tiempo de cada muestra del historial que cae en la
     * ventana visible, cada uno de los 5 carriles debe valer lo que traía
     * esa muestra. Se llama en el hilo de Swing.
     */
    private static int dibujadoDistinto(DigitalDePrueba g, List<SerieEnVivo.Punto> historial) {
        int mal = 0;
        double inicioEje = g.chart().getXYPlot().getDomainAxis().getLowerBound();
        for (SerieEnVivo.Punto p : historial) {
            if (p.tiempo() >= inicioEje) {
                int bitsMuestra = patronRapido(Math.round(p.tiempo() * 1000));
                for (int carril = 0; carril <= CARRIL_VALOR; carril++) {
                    if (valorEn(g.serie(carril), p.tiempo()) != esperado(bitsMuestra, carril)) {
                        mal++;
                    }
                }
            }
        }
        return mal;
    }

    /** Envía a la gráfica digital las muestras desde (incluida) hasta (excluida), con esos bits. */
    private static void enviarDigital(DigitalDePrueba g, int desde, int hasta, IntUnaryOperator bits) {
        for (int i = desde; i < hasta; i++) {
            g.grafica().muestraRecibida(muestraDigital(tiempo(i), bits.applyAsInt(i)));
        }
    }

    /** Envía la muestra número i de la prueba de concurrencia: 1 ms entre muestras, con reinicios. */
    private static void enviarRapida(DigitalDePrueba g, long i) {
        long j = i % MUESTRAS_POR_CORRIDA;
        g.grafica().muestraRecibida(muestraDigital(tiempoRapido(j), patronRapido(j)));
    }

    /**
     * Agrega las muestras desde (incluida) hasta (excluida), con un ciclo
     * del Timer cada 5 muestras, como pasa en el programa a 10 ms.
     */
    private static void alimentarDigital(SenalesDigitalesEnVivo datos, int desde, int hasta,
                                         IntUnaryOperator bits) {
        for (int i = desde; i < hasta; i++) {
            datos.agregar(tiempo(i), bits.applyAsInt(i));
            if ((i + 1) % MUESTRAS_POR_CICLO == 0) {
                datos.procesarPendientes();
            }
        }
        datos.procesarPendientes();
    }

    /**
     * Agrega las muestras desde (incluida) hasta (excluida) con ciclos del
     * Timer de 1 a 20 muestras, al azar.
     *
     * @return hasta
     */
    private static int alimentarAlAzar(SenalesDigitalesEnVivo datos, int[] bits, int desde,
                                       int hasta, Random azar) {
        int i = desde;
        while (i < hasta) {
            int ciclo = 1 + azar.nextInt(20);
            for (int k = 0; k < ciclo && i < hasta; k++, i++) {
                datos.agregar(tiempo(i), bits[i]);
            }
            datos.procesarPendientes();
        }
        return i;
    }

    /**
     * Espera a que el Timer de la gráfica digital (cada 50 ms) dibuje hasta
     * la muestra con ese tiempo. Espera como mucho 5 s.
     *
     * @return true si se dibujó
     */
    private static boolean esperarDibujoDigital(DigitalDePrueba g, double ultimoTiempo) throws Exception {
        long limite = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < limite) {
            boolean dibujado = enSwing(() -> {
                XYSeries valor = g.serie(CARRIL_VALOR);
                int n = valor.getItemCount();
                return n > 0 && valor.getX(n - 1).doubleValue() == ultimoTiempo;
            });
            if (dibujado) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }

    /**
     * Ejecuta el envío en este hilo mientras el hilo de Swing está ocupado:
     * el Timer no alcanza a procesar nada de lo enviado hasta que termina, y
     * después lo procesa todo en un mismo ciclo.
     */
    private static void conSwingOcupado(Runnable envio) throws InterruptedException {
        CountDownLatch ocupado = new CountDownLatch(1);
        CountDownLatch listo = new CountDownLatch(1);
        SwingUtilities.invokeLater(() -> {
            ocupado.countDown();
            try {
                listo.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        ocupado.await();
        try {
            envio.run();
        } finally {
            listo.countDown();
        }
    }

    /** Intenta crear una gráfica digital y dice si se rechazó con IllegalArgumentException. */
    private static boolean rechazaCrear(PanelSenal panel, int canal) {
        try {
            new GraficaDigital(MUESTREADOR, panel, canal);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    /** Muestra sintética con esos bits en las entradas digitales: el bit i es Di. */
    private static Muestra muestraDigital(double tiempo, int bits) {
        boolean[] digitales = new boolean[CANALES_DIGITALES];
        for (int canal = 0; canal < CANALES_DIGITALES; canal++) {
            digitales[canal] = ((bits >> canal) & 1) == 1;
        }
        return new Muestra(tiempo, ANALOGICAS, digitales);
    }

    /**
     * Muestra sintética con los bits escritos como D3 D2 D1 D0, por ejemplo
     * "1010" es D3 = 1, D2 = 0, D1 = 1 y D0 = 0. No usa muestraDigital(),
     * para revisar el orden de los bits por otro camino.
     */
    private static Muestra muestraBits(double tiempo, String d3d2d1d0) {
        boolean[] digitales = new boolean[CANALES_DIGITALES];
        for (int canal = 0; canal < CANALES_DIGITALES; canal++) {
            digitales[canal] = d3d2d1d0.charAt(CANALES_DIGITALES - 1 - canal) == '1';
        }
        return new Muestra(tiempo, ANALOGICAS, digitales);
    }

    /**
     * Bits de la muestra número i en las pruebas de I7LV-20: un contador que
     * avanza cada 7 muestras. D0 cambia cada 7 muestras, D1 cada 14, D2 cada
     * 28 y D3 cada 56, así el historial de cada canal es distinto.
     */
    private static int patron(int i) {
        return (i / 7) & 0b1111;
    }

    /** Como patron(), pero con D3 siempre en 1. */
    private static int patronConD3(int i) {
        return (patron(i) & 0b0111) | 0b1000;
    }

    /** Bits de la muestra j de una corrida en la prueba de concurrencia: pseudoaleatorios, con muchos cambios. */
    private static int patronRapido(long j) {
        return (int) ((j * 0x9E3779B97F4A7C15L) >>> 60);
    }

    /** Lo que debe valer un carril con esos bits: el bit del canal (D0 a D3) o el número entero (Valor). */
    private static double esperado(int bits, int carril) {
        return carril == CARRIL_VALOR ? bits : (bits >> carril) & 1;
    }

    /** Serie de un carril de SenalesDigitalesEnVivo: 0 a 3 son D0 a D3; 4 es Valor. */
    private static XYSeries serieDe(SenalesDigitalesEnVivo datos, int carril) {
        return carril == CARRIL_VALOR ? datos.getSerieValor() : datos.getSerieCanal(carril);
    }

    /**
     * Valor de la onda en escalones de la serie en el instante t: el del
     * último punto con tiempo menor o igual que t (NaN si no hay ninguno).
     * Es lo que se ve dibujado en ese instante. Búsqueda binaria: los puntos
     * están en orden de tiempo.
     */
    private static double valorEn(XYSeries serie, double t) {
        int bajo = 0;
        int alto = serie.getItemCount() - 1;
        int encontrado = -1;
        while (bajo <= alto) {
            int medio = (bajo + alto) >>> 1;
            if (serie.getX(medio).doubleValue() <= t) {
                encontrado = medio;
                bajo = medio + 1;
            } else {
                alto = medio - 1;
            }
        }
        return encontrado < 0 ? Double.NaN : serie.getY(encontrado).doubleValue();
    }

    /** Los puntos de una serie, como pares (tiempo, valor). */
    private static List<SerieEnVivo.Punto> puntos(XYSeries serie) {
        List<SerieEnVivo.Punto> lista = new ArrayList<>(serie.getItemCount());
        for (int k = 0; k < serie.getItemCount(); k++) {
            lista.add(new SerieEnVivo.Punto(serie.getX(k).doubleValue(), serie.getY(k).doubleValue()));
        }
        return lista;
    }

    /** Cuántos puntos tiene cada una de las 5 series dibujadas. */
    private static List<Integer> tamanosSeries(DigitalDePrueba g) {
        List<Integer> tamanos = new ArrayList<>();
        for (int carril = 0; carril <= CARRIL_VALOR; carril++) {
            tamanos.add(g.serie(carril).getItemCount());
        }
        return tamanos;
    }

    private static List<Integer> tamanos(List<List<SerieEnVivo.Punto>> historiales) {
        List<Integer> tamanos = new ArrayList<>();
        for (List<SerieEnVivo.Punto> h : historiales) {
            tamanos.add(h.size());
        }
        return tamanos;
    }

    private static String describir(FotoDigital f) {
        return "seleccionada " + f.canal() + ", título \"" + f.titulo() + "\", series con "
                + f.series().stream().map(List::size).toList() + " puntos, historial con "
                + f.historial().size() + ", colores " + f.colores() + ", grosores " + f.grosores()
                + ", nombres " + f.coloresNombres();
    }

    private static String rango(FotoDigital f) {
        return String.format("el eje X va de %.2f a %.2f s", f.inicioEje(), f.finEje());
    }

    private static String rango(SenalesDigitalesEnVivo datos) {
        return String.format("va de %.2f a %.2f s", datos.getInicioVentana(), datos.getFinVentana());
    }

    // ===================== I7LV-19: selector de la señal digital =====================

    /**
     * El selector de la pestaña "Señal digital" conectado con la gráfica
     * digital por ControlSeleccion, como en Main. El selector es el
     * JComboBox<String> de la pestaña de la prueba, con D0 a D3, igual al de
     * la ventana; se elige en él como lo haría el usuario.
     */
    private static void verificarSelectorDigital() throws Exception {
        DigitalDePrueba g = crearDigital(0);
        JComboBox<String> selector = g.panel().getComboCanal();
        int hasta = 5 * MUESTRAS_POR_SEGUNDO + 1;
        enviarDigital(g, 0, hasta, VerificacionSprint2::patron); // 5 s con D0 seleccionada
        esperarDibujoDigital(g, tiempo(hasta - 1));

        // Antes de conectarlo, el selector se deja en otra señal (D3): así se
        // ve que es el control el que lo pone en la señal de la gráfica
        List<FotoDigital> creacion = enSwing(() -> {
            selector.setSelectedIndex(3);
            FotoDigital previa = fotoDigital(g);
            new ControlSeleccion(selector, g.grafica());
            return List.of(previa, fotoDigital(g));
        });
        FotoDigital creado = creacion.get(1);
        String mostrado = enSwing(() -> selector.getSelectedIndex() + " " + selector.getSelectedItem());
        verificar("Al crear el ControlSeleccion, el selector muestra D0 y la gráfica tiene seleccionado "
                        + "el canal 0",
                mostrado.equals("0 D0") && creado.canal() == 0,
                "el selector muestra " + mostrado + "; " + describir(creado));
        verificar("... y crearlo no cambia la gráfica: sigue \"Seleccionada: D0\" con D0 resaltada, y "
                        + "las mismas series e historial",
                creacion.get(0).equals(creado) && creado.titulo().equals("Seleccionada: D0")
                        && resaltada(creado, 0) && !creado.series().get(0).isEmpty(),
                "antes: " + describir(creacion.get(0)) + "; después: " + describir(creado));

        // Elegir D2 en el selector. Las dos fotos y la cuenta de redibujos en
        // la misma tarea de Swing: el Timer no puede cambiar nada entre medio
        int[] redibujos = {0};
        List<FotoDigital> eleccion = enSwing(() -> {
            FotoDigital previa = fotoDigital(g);
            ChartChangeListener contador = e -> redibujos[0]++;
            g.chart().addChangeListener(contador);
            selector.setSelectedItem("D2");
            g.chart().removeChangeListener(contador);
            return List.of(previa, fotoDigital(g));
        });
        FotoDigital antes = eleccion.get(0);
        FotoDigital tras = eleccion.get(1);
        verificar("Elegir D2 en el selector deja seleccionado el canal 2 y el título dice "
                        + "\"Seleccionada: D2\", con D2 resaltada y un solo redibujo",
                tras.canal() == 2 && tras.titulo().equals("Seleccionada: D2") && resaltada(tras, 2)
                        && redibujos[0] == 1,
                describir(tras) + "; redibujos: " + redibujos[0]);
        verificar("... y el historial entregado es el de D2: sus 501 muestras, con los bits de D2",
                diferencias(tras.historial(), 2, hasta, VerificacionSprint2::patron) == 0,
                describir(tras));
        verificar("... sin borrar la gráfica: las 5 series dibujadas y el eje X quedan igual",
                !antes.series().get(0).isEmpty() && tras.series().equals(antes.series())
                        && tras.inicioEje() == antes.inicioEje() && tras.finEje() == antes.finEje(),
                "antes: " + describir(antes) + "; después: " + describir(tras));
        verificar("El nombre del carril seleccionado está en rojo y los demás, incluido Valor, en "
                        + "negro: D0 antes de cambiar; D2 después, y D0 vuelve a negro",
                nombreResaltado(creado.coloresNombres(), 0) && nombreResaltado(tras.coloresNombres(), 2),
                "antes: " + creado.coloresNombres() + "; después: " + tras.coloresNombres());

        // Siguen llegando muestras con D2 seleccionada
        int mas = hasta + 2 * MUESTRAS_POR_SEGUNDO;
        enviarDigital(g, hasta, mas, VerificacionSprint2::patron);
        esperarDibujoDigital(g, tiempo(mas - 1));
        List<List<SerieEnVivo.Punto>> historiales = historialesPorSelector(g);
        int malos = 0;
        for (int canal = 0; canal < CANALES_DIGITALES; canal++) {
            malos += diferencias(historiales.get(canal), canal, mas, VerificacionSprint2::patron);
        }
        int alFinal = enSwing(() -> g.grafica().getCanal());
        verificar("Los historiales de los 4 canales siguen completos después de cambiar la selección: "
                        + "al elegir cada señal en el selector se entrega la suya, con las 701 muestras "
                        + "(5 s antes del cambio y 2 s después)",
                malos == 0 && alFinal == 2,
                malos + " puntos no coinciden; tamaños " + tamanos(historiales)
                        + "; seleccionada al final: " + alFinal);

        // Volver a elegir D2, por nombre y por posición
        int[] avisos = {0};
        String[] enSelector = new String[1];
        List<FotoDigital> mismo = enSwing(() -> {
            FotoDigital previa = fotoDigital(g);
            ChartChangeListener contador = e -> avisos[0]++;
            g.chart().addChangeListener(contador);
            selector.setSelectedItem("D2");
            selector.setSelectedIndex(2);
            g.chart().removeChangeListener(contador);
            enSelector[0] = String.valueOf(selector.getSelectedItem());
            return List.of(previa, fotoDigital(g));
        });
        verificar("Volver a elegir D2, la señal ya elegida, no cambia nada: ni selección, ni título, ni "
                        + "colores, ni nombres, ni series, ni historial, y la gráfica ni se redibuja",
                mismo.get(0).equals(mismo.get(1)) && mismo.get(1).canal() == 2
                        && enSelector[0].equals("D2") && avisos[0] == 0,
                "antes: " + describir(mismo.get(0)) + "; después: " + describir(mismo.get(1))
                        + "; redibujos: " + avisos[0]);
    }

    /**
     * El nombre del carril seleccionado en rojo también con la gráfica vacía
     * (antes del primer Iniciar), y al cambiar la selección. Además de los
     * colores configurados, se dibuja la gráfica en una imagen, como lo hace
     * el ChartPanel, y se revisa dónde quedan los píxeles rojos y negros: así
     * se comprueba que el nombre de verdad se ve.
     */
    private static void verificarNombresCarriles() throws Exception {
        DigitalDePrueba g = crearDigital(0);
        JComboBox<String> selector = g.panel().getComboCanal();
        FotoDigital vacia = enSwing(() -> {
            new ControlSeleccion(selector, g.grafica());
            return fotoDigital(g);
        });
        NombresDibujados dibujo = dibujarNombres(g);
        verificar("Con la gráfica vacía, el nombre de D0 (la seleccionada) está en rojo y los demás en negro",
                vacia.series().get(0).isEmpty() && nombreResaltado(vacia.coloresNombres(), 0),
                describir(vacia));
        verificar("... y así se ve al dibujarla: los únicos píxeles rojos (" + dibujo.rojosEnTotal()
                        + ") forman el nombre de D0, y los nombres de D1, D2, D3 y Valor son negros",
                nombreRojoEnDibujo(dibujo, 0, true), dibujo.toString());

        FotoDigital vaciaD3 = enSwing(() -> {
            selector.setSelectedIndex(3);
            return fotoDigital(g);
        });
        NombresDibujados dibujoD3 = dibujarNombres(g);
        verificar("Con la gráfica todavía vacía, elegir D3 en el selector pone su nombre en rojo y el de "
                        + "D0 vuelve a negro, también en el dibujo, y el título dice \"Seleccionada: D3\"",
                vaciaD3.titulo().equals("Seleccionada: D3") && nombreResaltado(vaciaD3.coloresNombres(), 3)
                        && nombreRojoEnDibujo(dibujoD3, 3, true),
                describir(vaciaD3) + "; " + dibujoD3);

        // Con datos, la línea roja de la señal también tiene píxeles rojos,
        // pero dentro de su carril: la zona de los nombres no cambia
        int hasta = 5 * MUESTRAS_POR_SEGUNDO + 1;
        enviarDigital(g, 0, hasta, VerificacionSprint2::patron);
        esperarDibujoDigital(g, tiempo(hasta - 1));
        FotoDigital conDatos = enSwing(() -> {
            selector.setSelectedIndex(1);
            return fotoDigital(g);
        });
        NombresDibujados dibujoD1 = dibujarNombres(g);
        verificar("Con datos, elegir D1 pone su nombre en rojo y los demás en negro, también en el dibujo",
                nombreResaltado(conDatos.coloresNombres(), 1) && resaltada(conDatos, 1)
                        && nombreRojoEnDibujo(dibujoD1, 1, false),
                describir(conDatos) + "; " + dibujoD1);
    }

    /**
     * Como recorrer el selector con las flechas ↓ y ↑ a 10 ms: un hilo envía
     * muestras sin parar mientras el hilo de Swing mueve el selector una
     * posición por vez (D0, D1, D2, D3, D2, D1, D0, D1...), 1000 veces. Las
     * flechas del teclado solo actúan con la ventana visible; aquí se cambia
     * la posición del selector por código, que produce los mismos avisos
     * (ItemEvent) que las flechas.
     *
     * En cada paso, en la misma tarea de Swing, se revisa que la gráfica siga
     * al selector (señal, título y nombre en rojo), que no se toque ninguna
     * serie y que el historial entregado sea el de la señal elegida,
     * completo y coherente. Al final, que los 5 carriles dibujen lo enviado.
     */
    private static void verificarRecorridoConFlechas() throws Exception {
        DigitalDePrueba g = crearDigital(0);
        JComboBox<String> selector = g.panel().getComboCanal();
        enSwing(() -> new ControlSeleccion(selector, g.grafica()));

        AtomicBoolean enviando = new AtomicBoolean(true);
        AtomicLong enviadas = new AtomicLong();
        AtomicReference<Throwable> errorProductor = new AtomicReference<>();
        Thread productor = new Thread(() -> {
            long i = 0;
            while (enviando.get()) {
                enviarRapida(g, i);
                i++;
                pausar(2);
            }
            enviadas.set(i);
        }, "Muestreador de prueba");
        productor.setUncaughtExceptionHandler(guardarEImprimir(errorProductor));

        Random azar = new Random(19);
        int canal = 0;
        int paso = 1; // 1 baja por la lista (↓), -1 sube (↑)
        int fallas = 0;
        long inicio = System.nanoTime();
        productor.start();
        for (int k = 0; k < CAMBIOS_POR_RONDA; k++) {
            if (canal + paso < 0 || canal + paso >= CANALES_DIGITALES) {
                paso = -paso; // en un extremo de la lista, la otra flecha
            }
            int nuevo = canal + paso;
            fallas += enSwing(() -> {
                List<Integer> antes = tamanosSeries(g);
                selector.setSelectedIndex(nuevo);
                int mal = tamanosSeries(g).equals(antes) ? 0 : 1;
                if (g.grafica().getCanal() != nuevo
                        || !g.chart().getTitle().getText().equals("Seleccionada: D" + nuevo)
                        || !nombreResaltado(coloresNombres(g), nuevo)) {
                    mal++;
                }
                return mal + incoherencias(g.grafica().getHistorial(), nuevo);
            });
            canal = nuevo;
            pausar(azar.nextInt(400));
        }
        enviando.set(false);
        productor.join();
        double segundos = (System.nanoTime() - inicio) / 1e9;

        // 3000 muestras más, ya sin cambios, y se revisa lo dibujado
        long fin = enviadas.get();
        for (long i = fin; i < fin + 3000; i++) {
            enviarRapida(g, i);
        }
        long ultima = (fin + 2999) % MUESTRAS_POR_CORRIDA;
        boolean dibujo = esperarDibujoDigital(g, tiempoRapido(ultima));
        int ultimo = canal;
        int fallasFinales = enSwing(() -> {
            int mal = selector.getSelectedIndex() == ultimo && g.grafica().getCanal() == ultimo ? 0 : 1;
            List<SerieEnVivo.Punto> historial = g.grafica().getHistorial();
            mal += historial.size() == ultima + 1 ? 0 : 1;
            mal += incoherencias(historial, ultimo);
            mal += dibujadoDistinto(g, historial);
            return mal + (resaltada(fotoDigital(g), ultimo) ? 0 : 1);
        });

        verificar(String.format("Recorrer el selector como con las flechas ↓ y ↑ (%d cambios de una "
                                + "posición) mientras otro hilo envía muestras sin parar (%,d muestras en "
                                + "%.1f s): en cada paso la gráfica sigue al selector (señal, título y "
                                + "nombre en rojo), nada se borra y el historial entregado es el de la "
                                + "señal elegida; al final los 5 carriles dibujan lo enviado (D%d)",
                        CAMBIOS_POR_RONDA, enviadas.get(), segundos, ultimo),
                errorProductor.get() == null && fallas == 0 && dibujo && fallasFinales == 0,
                errorProductor.get() != null ? errorProductor.get().toString()
                        : fallas + " fallas durante los cambios, " + fallasFinales + " al final");
    }

    /**
     * Las dos pestañas conectadas como en Main, cada una con su gráfica y su
     * ControlSeleccion, y las mismas muestras llegando a las dos (como hace
     * el Muestreador con sus oyentes): la analógica sigue funcionando igual
     * que en I7LV-16 y ninguna se entera de lo que se elige en la otra.
     */
    private static void verificarAnalogicaSigueIgual() throws Exception {
        GraficaDePrueba analogica = crearGrafica(0);
        DigitalDePrueba digital = crearDigital(0);
        JComboBox<String> selectorAnalogico = analogica.panel().getComboCanal();
        JComboBox<String> selectorDigital = digital.panel().getComboCanal();
        enSwing(() -> {
            new ControlSeleccion(selectorAnalogico, analogica.grafica());
            new ControlSeleccion(selectorDigital, digital.grafica());
            return null;
        });
        int hasta = 5 * MUESTRAS_POR_SEGUNDO + 1;
        enviarAAmbas(analogica, digital, 0, hasta);
        boolean dibujo = esperarDibujo(analogica, tiempo(hasta - 1))
                && esperarDibujoDigital(digital, tiempo(hasta - 1));

        List<FotoAmbas> eleccionDigital = enSwing(() -> {
            FotoAmbas previa = fotoAmbas(analogica, digital);
            selectorDigital.setSelectedItem("D2");
            return List.of(previa, fotoAmbas(analogica, digital));
        });
        FotoAmbas trasD2 = eleccionDigital.get(1);
        int pedidos = enSwing(() -> analogica.grafica().pedidosDeCambio);
        verificar("Con las dos pestañas conectadas como en Main, elegir D2 en el selector digital no toca "
                        + "la gráfica analógica: sigue en A0, con su título, su serie y su historial",
                dibujo && trasD2.digital().canal() == 2 && trasD2.analogica().canal() == 0
                        && trasD2.analogica().equals(eleccionDigital.get(0).analogica())
                        && trasD2.analogica().historial().size() == hasta && pedidos == 0,
                describir(trasD2.analogica()) + "; cambios pedidos a la analógica: " + pedidos);

        List<FotoAmbas> eleccionAnalogica = enSwing(() -> {
            FotoAmbas previa = fotoAmbas(analogica, digital);
            selectorAnalogico.setSelectedItem("A3");
            return List.of(previa, fotoAmbas(analogica, digital));
        });
        Foto a3 = eleccionAnalogica.get(1).analogica();
        verificar("Elegir A3 en el selector analógico funciona igual que en I7LV-16: canal 3, título "
                        + "\"Canal A3\", serie e historial vacíos",
                a3.canal() == 3 && a3.titulo().equals("Canal A3")
                        && a3.valoresSerie().isEmpty() && a3.historial().isEmpty(),
                describir(a3));
        verificar("... y no toca la gráfica digital: sigue D2 seleccionada, con todo lo dibujado y su historial",
                eleccionAnalogica.get(1).digital().equals(eleccionAnalogica.get(0).digital())
                        && eleccionAnalogica.get(1).digital().canal() == 2,
                "antes: " + describir(eleccionAnalogica.get(0).digital())
                        + "; después: " + describir(eleccionAnalogica.get(1).digital()));

        // El muestreo sigue: las mismas muestras llegan a las dos gráficas
        int mas = hasta + 2 * MUESTRAS_POR_SEGUNDO;
        enviarAAmbas(analogica, digital, hasta, mas);
        dibujo = esperarDibujo(analogica, tiempo(mas - 1)) && esperarDibujoDigital(digital, tiempo(mas - 1));
        FotoAmbas luego = enSwing(() -> fotoAmbas(analogica, digital));
        verificar("... y después la analógica solo registra y dibuja A3 (1,5 V), mientras la digital "
                        + "sigue registrando todo: el historial de D2 tiene sus 701 muestras",
                dibujo && luego.analogica().historial().size() == mas - hasta
                        && soloVale(valores(luego.analogica().historial()), valorCanal(3))
                        && soloVale(luego.analogica().valoresSerie(), valorCanal(3))
                        && diferencias(luego.digital().historial(), 2, mas, VerificacionSprint2::patron) == 0,
                describir(luego.analogica()) + "; " + describir(luego.digital()));
    }

    // ===================== Utilidades de I7LV-19 =====================

    /** Mínimo de píxeles para dar por dibujado un nombre: "D0" en negrita de 13 tiene unos 70. */
    private static final int PIXELES_NOMBRE = 30;

    /** Lo que muestran las dos gráficas, la analógica y la digital, en un mismo instante. */
    private record FotoAmbas(Foto analogica, FotoDigital digital) {
    }

    /**
     * Píxeles de la gráfica digital dibujada: rojos y negros en la zona del
     * nombre de cada carril (a la izquierda de su área de dibujo y a su misma
     * altura), de D0 a D3 y Valor, y rojos en toda la imagen.
     */
    private record NombresDibujados(List<Integer> rojos, List<Integer> negros, int rojosEnTotal) {

        @Override
        public String toString() {
            return "píxeles rojos por carril " + rojos + ", negros " + negros
                    + ", rojos en toda la imagen " + rojosEnTotal;
        }
    }

    /** Toma la foto de las dos gráficas. Se llama en el hilo de Swing. */
    private static FotoAmbas fotoAmbas(GraficaDePrueba analogica, DigitalDePrueba digital) {
        return new FotoAmbas(foto(analogica), fotoDigital(digital));
    }

    /**
     * Si el nombre del carril de la señal está en rojo y los de los demás
     * carriles, incluido Valor, en negro.
     *
     * @param colores color del nombre de cada carril, de D0 a D3 y Valor
     */
    private static boolean nombreResaltado(List<Paint> colores, int canal) {
        for (int carril = 0; carril <= CARRIL_VALOR; carril++) {
            if (!colores.get(carril).equals(carril == canal ? Tema.ROJO : Tema.NEGRO)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Si en el dibujo el nombre de la señal es rojo (sin nada negro) y el de
     * cada uno de los demás carriles es negro (sin nada rojo). Con la gráfica
     * vacía, además, no debe haber ningún otro píxel rojo en toda la imagen;
     * con datos está también la línea roja de la señal.
     */
    private static boolean nombreRojoEnDibujo(NombresDibujados d, int canal, boolean vacia) {
        for (int carril = 0; carril <= CARRIL_VALOR; carril++) {
            boolean bien = carril == canal
                    ? d.rojos().get(carril) >= PIXELES_NOMBRE && d.negros().get(carril) == 0
                    : d.rojos().get(carril) == 0 && d.negros().get(carril) >= PIXELES_NOMBRE;
            if (!bien) {
                return false;
            }
        }
        return !vacia || d.rojosEnTotal() == d.rojos().get(canal);
    }

    /**
     * Dibuja la gráfica digital en una imagen, como lo hace el ChartPanel, al
     * tamaño que tiene en la ventana de 1000 × 700, y cuenta sus píxeles
     * rojos y negros (ver NombresDibujados). El área de dibujo de cada carril
     * sale de la información que JFreeChart registra al dibujar.
     */
    private static NombresDibujados dibujarNombres(DigitalDePrueba g) throws Exception {
        return enSwing(() -> {
            BufferedImage imagen = new BufferedImage(1000, 560, BufferedImage.TYPE_INT_RGB);
            ChartRenderingInfo info = new ChartRenderingInfo();
            Graphics2D g2 = imagen.createGraphics();
            g.chart().draw(g2, new Rectangle(0, 0, 1000, 560), null, info);
            g2.dispose();

            int rojosEnTotal = 0;
            for (int y = 0; y < imagen.getHeight(); y++) {
                for (int x = 0; x < imagen.getWidth(); x++) {
                    if (esRojo(imagen.getRGB(x, y))) {
                        rojosEnTotal++;
                    }
                }
            }
            List<Integer> rojos = new ArrayList<>();
            List<Integer> negros = new ArrayList<>();
            for (int carril = 0; carril <= CARRIL_VALOR; carril++) {
                Rectangle2D area = info.getPlotInfo().getSubplotInfo(carril).getDataArea();
                int r = 0;
                int n = 0;
                for (int y = (int) Math.ceil(area.getMinY()); y < area.getMaxY(); y++) {
                    for (int x = 0; x < area.getMinX(); x++) {
                        int pixel = imagen.getRGB(x, y);
                        if (esRojo(pixel)) {
                            r++;
                        } else if (esNegro(pixel)) {
                            n++;
                        }
                    }
                }
                rojos.add(r);
                negros.add(n);
            }
            return new NombresDibujados(rojos, negros, rojosEnTotal);
        });
    }

    /**
     * Píxel rojo: el de Tema (204, 0, 0) o casi. En el borde de las letras el
     * suavizado mezcla el color con el blanco del fondo; esos píxeles claros
     * no cuentan ni como rojos ni como negros.
     */
    private static boolean esRojo(int rgb) {
        int rojo = (rgb >> 16) & 0xFF;
        int verde = (rgb >> 8) & 0xFF;
        int azul = rgb & 0xFF;
        return rojo >= 140 && verde <= 100 && azul <= 100;
    }

    /** Píxel negro o casi negro. */
    private static boolean esNegro(int rgb) {
        int rojo = (rgb >> 16) & 0xFF;
        int verde = (rgb >> 8) & 0xFF;
        int azul = rgb & 0xFF;
        return rojo <= 100 && verde <= 100 && azul <= 100;
    }

    /**
     * El historial de cada señal, eligiéndola en el selector de la pestaña
     * como lo haría el usuario antes de guardar; al final vuelve a elegir la
     * que estaba. Si el selector no estuviera conectado, los cuatro serían el
     * de la misma señal.
     */
    private static List<List<SerieEnVivo.Punto>> historialesPorSelector(DigitalDePrueba g) throws Exception {
        return enSwing(() -> {
            JComboBox<String> selector = g.panel().getComboCanal();
            int elegida = selector.getSelectedIndex();
            List<List<SerieEnVivo.Punto>> lista = new ArrayList<>();
            for (int canal = 0; canal < CANALES_DIGITALES; canal++) {
                selector.setSelectedIndex(canal);
                lista.add(g.grafica().getHistorial());
            }
            selector.setSelectedIndex(elegida);
            return lista;
        });
    }

    /**
     * Envía las mismas muestras a las dos gráficas, como el Muestreador a sus
     * oyentes: el canal Ai vale i × 0,5 V y los bits son los de patron().
     */
    private static void enviarAAmbas(GraficaDePrueba analogica, DigitalDePrueba digital, int desde, int hasta) {
        for (int i = desde; i < hasta; i++) {
            Muestra muestra = muestraDigital(tiempo(i), patron(i));
            analogica.grafica().muestraRecibida(muestra);
            digital.grafica().muestraRecibida(muestra);
        }
    }

    // ===================== I7LV-21 e I7LV-22: salidas digitales =====================

    private static void verificarSalidasAlCrear() throws Exception {
        FuenteDePrueba fuente = fuenteConectada();
        SalidasDePrueba s = crearSalidas(fuente);
        FotoSalidas foto = enSwing(() -> fotoSalidas(s));
        verificar("Al crearse, ControlSalidas envía a la fuente las 4 salidas apagadas: una escritura por "
                        + "salida, de la 0 a la 3, y la fuente las acepta",
                porSalida(fuente.escrituras()).equals(escrituras(false, false, false, false)),
                "escribió " + fuente.escrituras());
        verificar("... y la pantalla empieza de acuerdo con la fuente: los 4 interruptores sin pulsar y "
                        + "habilitados, y las 4 etiquetas dicen \"Apagada\" en negro",
                pantallaDice(foto, false, false, false, false), describir(foto));

        // Con el interruptor 1 ya pulsado antes de crear el control: así se ve
        // que envía lo que muestra la pantalla y no siempre "apagada"
        FuenteDePrueba otra = fuenteConectada();
        SalidasDePrueba preparada = enSwing(() -> {
            PanelSalidas panel = new PanelSalidas();
            panel.getInterruptores()[1].setSelected(true);
            return new SalidasDePrueba(otra, panel);
        });
        FotoSalidas fotoPreparada = enSwing(() -> fotoSalidas(preparada));
        verificar("Envía lo que muestran los interruptores: con el 1 ya pulsado al crear el control, escribe "
                        + "(1, encendida) y su etiqueta dice \"Encendida\" en rojo",
                porSalida(otra.escrituras()).equals(escrituras(false, true, false, false))
                        && pantallaDice(fotoPreparada, false, true, false, false),
                "escribió " + otra.escrituras() + "; " + describir(fotoPreparada));

        // Como cuando Main no puede conectar la fuente
        FuenteDePrueba sinConectar = new FuenteDePrueba();
        SalidasDePrueba desconectada = crearSalidas(sinConectar);
        FotoSalidas fotoDesconectada = enSwing(() -> fotoSalidas(desconectada));
        List<String> avisos = avisos(desconectada);
        verificar("Si la fuente no está conectada al crearlo, los 4 interruptores quedan deshabilitados y el "
                        + "control no abre un aviso propio (el de la conexión lo da Main)",
                !fotoDesconectada.habilitados().contains(true) && avisos.isEmpty(),
                describir(fotoDesconectada) + "; avisos: " + avisos);
    }

    private static void verificarPulsarInterruptor() throws Exception {
        FuenteDePrueba fuente = fuenteConectada();
        SalidasDePrueba s = crearSalidas(fuente);

        int desde = fuente.escrituras().size();
        FotoSalidas encendida = pulsar(s, 2);
        verificar("Pulsar el interruptor 2 escribe (2, encendida) en la fuente, una sola vez",
                escriturasDesde(fuente, desde).equals(List.of(new Escritura(2, true, true)))
                        && Arrays.equals(fuente.salidas(), new boolean[]{false, false, true, false}),
                "escribió " + escriturasDesde(fuente, desde));
        verificar("... y su etiqueta dice \"Encendida\" en el rojo de Tema; las demás siguen \"Apagada\" en negro",
                pantallaDice(encendida, false, false, true, false), describir(encendida));

        desde = fuente.escrituras().size();
        FotoSalidas apagada = pulsar(s, 2);
        verificar("Volver a pulsarlo escribe (2, apagada), una sola vez, y la etiqueta vuelve a decir "
                        + "\"Apagada\" en negro",
                escriturasDesde(fuente, desde).equals(List.of(new Escritura(2, false, true)))
                        && Arrays.equals(fuente.salidas(), new boolean[4])
                        && pantallaDice(apagada, false, false, false, false),
                "escribió " + escriturasDesde(fuente, desde) + "; " + describir(apagada));
        List<String> avisos = avisos(s);
        verificar("Mientras la fuente acepta las escrituras no hay avisos", avisos.isEmpty(), "avisos: " + avisos);
    }

    private static void verificarEscrituraRechazada() throws Exception {
        FuenteDePrueba fuente = fuenteConectada();
        SalidasDePrueba s = crearSalidas(fuente);
        pulsar(s, 3); // la 3 encendida, antes de que la fuente empiece a fallar
        fuente.setFallar(true);

        // Encender la 1: la fuente lo rechaza. Las dos fotos en la misma
        // tarea de Swing que el clic.
        int desde = fuente.escrituras().size();
        List<FotoSalidas> encender = enSwing(() -> {
            FotoSalidas previa = fotoSalidas(s);
            s.panel.getInterruptores()[1].doClick(0);
            return List.of(previa, fotoSalidas(s));
        });
        List<String> avisos = avisos(s);
        verificar("Con la fuente configurada para fallar, pulsar el interruptor 1 intenta escribir "
                        + "(1, encendida) una sola vez: al devolver el interruptor no hay una segunda escritura",
                escriturasDesde(fuente, desde).equals(List.of(new Escritura(1, true, false))),
                "intentó " + escriturasDesde(fuente, desde));
        verificar("... el interruptor 1 vuelve a quedar sin pulsar y ninguna etiqueta cambia: la 1 sigue "
                        + "\"Apagada\" en negro y la 3 \"Encendida\" en rojo",
                encender.get(1).equals(encender.get(0))
                        && pantallaDice(encender.get(1), false, false, false, true),
                "antes: " + describir(encender.get(0)) + "; después: " + describir(encender.get(1)));
        verificar("... y se avisa una vez: \"No se pudo encender la salida 1\", con el motivo que dio la fuente",
                avisos.size() == 1 && avisos.get(0).startsWith("No se pudo encender la salida 1")
                        && avisos.get(0).contains(FuenteDePrueba.MOTIVO),
                "avisos: " + avisos);

        // Apagar la 3: también lo rechaza
        desde = fuente.escrituras().size();
        List<FotoSalidas> apagar = enSwing(() -> {
            FotoSalidas previa = fotoSalidas(s);
            s.panel.getInterruptores()[3].doClick(0);
            return List.of(previa, fotoSalidas(s));
        });
        avisos = avisos(s);
        verificar("Lo mismo al apagar: pulsar el 3 intenta (3, apagada) una sola vez, el interruptor sigue "
                        + "pulsado, su etiqueta sigue \"Encendida\" en rojo y se avisa \"No se pudo apagar la salida 3\"",
                escriturasDesde(fuente, desde).equals(List.of(new Escritura(3, false, false)))
                        && apagar.get(1).equals(apagar.get(0))
                        && pantallaDice(apagar.get(1), false, false, false, true)
                        && avisos.size() == 2 && avisos.get(1).startsWith("No se pudo apagar la salida 3"),
                "intentó " + escriturasDesde(fuente, desde) + "; " + describir(apagar.get(1))
                        + "; avisos: " + avisos);
        verificar("La fuente conserva las salidas como estaban: solo la 3 encendida",
                Arrays.equals(fuente.salidas(), new boolean[]{false, false, false, true}),
                "salidas " + Arrays.toString(fuente.salidas()));

        // La fuente vuelve a aceptar
        fuente.setFallar(false);
        desde = fuente.escrituras().size();
        FotoSalidas recuperada = pulsar(s, 1);
        verificar("Cuando la fuente vuelve a aceptar, el mismo interruptor funciona: (1, encendida) y "
                        + "\"Encendida\" en rojo",
                escriturasDesde(fuente, desde).equals(List.of(new Escritura(1, true, true)))
                        && pantallaDice(recuperada, false, true, false, true),
                "escribió " + escriturasDesde(fuente, desde) + "; " + describir(recuperada));
    }

    /**
     * Las salidas con un Muestreador real sobre la misma fuente, a 10 ms. El
     * hilo de la prueba hace de ControlMuestreo: inicia y detiene el
     * Muestreador. Si detener() desconectara la fuente, como antes de
     * I7LV-21 e I7LV-22, la fuente de prueba rechazaría las escrituras
     * siguientes y contaría la desconexión.
     */
    private static void verificarSalidasConMuestreoDetenido() throws Exception {
        FuenteDePrueba fuente = fuenteConectada();
        Muestreador muestreador = new Muestreador(fuente, 10);
        AtomicInteger muestras = new AtomicInteger();
        muestreador.agregarOyente(muestra -> muestras.incrementAndGet());
        SalidasDePrueba s = crearSalidas(fuente);

        int desde = fuente.escrituras().size();
        FotoSalidas sinMuestrear = pulsar(s, 0);
        verificar("Con el Muestreador detenido, sin haberlo iniciado nunca, pulsar el interruptor 0 enciende "
                        + "la salida 0: (0, encendida) aceptada y \"Encendida\" en rojo",
                !muestreador.estaCorriendo() && muestras.get() == 0
                        && escriturasDesde(fuente, desde).equals(List.of(new Escritura(0, true, true)))
                        && pantallaDice(sinMuestrear, true, false, false, false),
                "escribió " + escriturasDesde(fuente, desde) + "; " + describir(sinMuestrear));

        // Muestreando, se enciende también la 3; luego se detiene
        muestreador.iniciar();
        boolean llegaron = esperarMuestras(muestras, 5);
        pulsar(s, 3);
        int escrituras = fuente.escrituras().size();
        FotoSalidas antes = enSwing(() -> fotoSalidas(s));
        muestreador.detener();
        FotoSalidas despues = enSwing(() -> fotoSalidas(s));
        verificar("Detener el Muestreador no cambia las salidas: siguen encendidas la 0 y la 3, en la fuente y "
                        + "en la pantalla, y al detener no se escribe nada",
                llegaron && !muestreador.estaCorriendo()
                        && pantallaDice(antes, true, false, false, true) && despues.equals(antes)
                        && fuente.escrituras().size() == escrituras
                        && Arrays.equals(fuente.salidas(), new boolean[]{true, false, false, true}),
                (llegaron ? "" : "no llegaron muestras; ") + "salidas " + Arrays.toString(fuente.salidas())
                        + "; " + describir(despues));
        boolean entregaDatos = fuente.entregaDatos();
        verificar("... ni desconecta la fuente: sigue conectada y entregando datos (se conectó 1 vez y no se "
                        + "desconectó)",
                fuente.conectada() && entregaDatos && fuente.conexiones() == 1 && fuente.desconexiones() == 0,
                fuente.estado());

        desde = fuente.escrituras().size();
        FotoSalidas detenido = pulsar(s, 2);
        verificar("Con el Muestreador detenido después de haber muestreado, pulsar el interruptor 2 enciende "
                        + "la salida 2",
                escriturasDesde(fuente, desde).equals(List.of(new Escritura(2, true, true)))
                        && pantallaDice(detenido, true, false, true, true),
                "escribió " + escriturasDesde(fuente, desde) + "; " + describir(detenido));
    }

    private static void verificarHabilitarSalidas() throws Exception {
        FuenteDePrueba fuente = fuenteConectada();
        SalidasDePrueba s = crearSalidas(fuente);
        pulsar(s, 0);

        int desde = fuente.escrituras().size();
        List<FotoSalidas> deshabilitar = enSwing(() -> {
            s.setHabilitado(false);
            FotoSalidas tras = fotoSalidas(s);
            for (JToggleButton interruptor : s.panel.getInterruptores()) {
                interruptor.doClick(0);
            }
            return List.of(tras, fotoSalidas(s));
        });
        verificar("setHabilitado(false) deshabilita los 4 interruptores",
                deshabilitar.get(0).habilitados().equals(List.of(false, false, false, false)),
                describir(deshabilitar.get(0)));
        verificar("... y pulsarlos así no escribe nada ni cambia la pantalla",
                escriturasDesde(fuente, desde).isEmpty() && deshabilitar.get(1).equals(deshabilitar.get(0)),
                "escribió " + escriturasDesde(fuente, desde) + "; " + describir(deshabilitar.get(1)));

        FotoSalidas habilitados = enSwing(() -> {
            s.setHabilitado(true);
            return fotoSalidas(s);
        });
        FotoSalidas pulsado = pulsar(s, 1);
        verificar("setHabilitado(true) vuelve a habilitar los 4, con la salida 0 todavía encendida, y "
                        + "pulsar el 1 funciona otra vez",
                pantallaDice(habilitados, true, false, false, false)
                        && escriturasDesde(fuente, desde).equals(List.of(new Escritura(1, true, true)))
                        && pantallaDice(pulsado, true, true, false, false),
                describir(habilitados) + "; escribió " + escriturasDesde(fuente, desde));
    }

    /**
     * ControlMuestreo con la separación entre conexión y muestreo: sus
     * botones reales, en una barra y una pestaña que nunca se muestran, se
     * pulsan como lo haría el usuario, con un ControlSalidas sobre la misma
     * fuente. Mientras la fuente funcione, ControlMuestreo no abre avisos.
     */
    private static void verificarIniciarDetenerSinReconectar() throws Exception {
        FuenteDePrueba fuente = fuenteConectada();
        Muestreador muestreador = new Muestreador(fuente, 10);
        AtomicInteger muestras = new AtomicInteger();
        muestreador.agregarOyente(muestra -> muestras.incrementAndGet());
        MuestreoDePrueba m = enSwing(() -> {
            BarraEstado barra = new BarraEstado();
            PanelMuestreo panel = new PanelMuestreo();
            return new MuestreoDePrueba(barra, panel, new ControlMuestreo(muestreador, barra, panel, null));
        });
        SalidasDePrueba s = crearSalidas(fuente);
        pulsar(s, 1);
        int escrituras = fuente.escrituras().size();

        final int corridas = 3;
        int conMuestras = 0;
        int botonesMal = 0;
        for (int k = 0; k < corridas; k++) {
            int antes = muestras.get();
            boolean inicio = enSwing(() -> {
                m.barra().getBtnIniciar().doClick(0);
                return muestreador.estaCorriendo();
            });
            if (esperarMuestras(muestras, antes + 5)) {
                conMuestras++;
            }
            boolean detuvo = enSwing(() -> {
                m.barra().getBtnDetener().doClick(0);
                return !muestreador.estaCorriendo();
            });
            botonesMal += (inicio ? 0 : 1) + (detuvo ? 0 : 1);
        }
        FotoSalidas salidas = enSwing(() -> fotoSalidas(s));
        verificar(String.format("Con los botones de la barra (ControlMuestreo real), Iniciar y Detener %d veces "
                                + "seguidas funciona sin reconectar: llegan muestras en cada corrida, la fuente "
                                + "se conectó una sola vez y sigue conectada, y la salida 1 sigue encendida",
                        corridas),
                conMuestras == corridas && botonesMal == 0 && fuente.conectada()
                        && fuente.conexiones() == 1 && fuente.desconexiones() == 0
                        && fuente.escrituras().size() == escrituras
                        && Arrays.equals(fuente.salidas(), new boolean[]{false, true, false, false})
                        && pantallaDice(salidas, false, true, false, false),
                "corridas con muestras: " + conMuestras + " de " + corridas + "; botones que no respondieron: "
                        + botonesMal + "; " + fuente.estado() + "; " + describir(salidas));

        List<BotonesMuestreo> deshabilitado = enSwing(() -> {
            m.control().setHabilitado(false);
            BotonesMuestreo tras = botones(m);
            m.barra().getBtnIniciar().doClick(0);
            return List.of(tras, botones(m));
        });
        verificar("ControlMuestreo.setHabilitado(false), para cuando no se pudo conectar la fuente, deshabilita "
                        + "Iniciar, el campo del tiempo y Aplicar (Detener ya lo estaba), la barra dice \"Sin "
                        + "conexión con la fuente de datos\" y pulsar Iniciar no hace nada",
                deshabilitado.get(0).equals(new BotonesMuestreo(false, false, false, false,
                        "Sin conexión con la fuente de datos"))
                        && deshabilitado.get(1).equals(deshabilitado.get(0)) && !muestreador.estaCorriendo(),
                deshabilitado.toString());
        BotonesMuestreo habilitado = enSwing(() -> {
            m.control().setHabilitado(true);
            return botones(m);
        });
        verificar("... y setHabilitado(true) los vuelve a habilitar: la barra dice \"Detenido · cada 10 ms\"",
                habilitado.equals(new BotonesMuestreo(true, false, true, true, "Detenido · cada 10 ms")),
                habilitado.toString());
    }

    // ===================== Utilidades de I7LV-21 e I7LV-22 =====================

    /**
     * Fuente de prueba de I7LV-21 e I7LV-22. Registra cada llamada a
     * escribirSalida(), también las que rechaza, y se puede configurar para
     * rechazarlas. Como la fuente real, exige estar conectada (iniciar())
     * para leer y escribir, y además cuenta las conexiones y desconexiones.
     * Sus métodos son synchronized porque la usan el hilo de Swing (las
     * escrituras) y el hilo del Muestreador (las lecturas).
     */
    private static final class FuenteDePrueba implements FuenteDeDatos {

        /** Motivo de las escrituras rechazadas a propósito. El aviso debe incluirlo. */
        static final String MOTIVO = "falla configurada por la prueba";

        private final List<Escritura> escrituras = new ArrayList<>();
        private final boolean[] salidas = new boolean[NUM_SALIDAS_DIGITALES];
        private boolean conectada = false;
        private boolean fallar = false;
        private int conexiones = 0;
        private int desconexiones = 0;

        @Override
        public synchronized void iniciar() {
            conectada = true;
            conexiones++;
        }

        @Override
        public synchronized void detener() {
            conectada = false;
            desconexiones++;
        }

        @Override
        public synchronized double[] leerAnalogicas() throws FuenteDeDatosException {
            exigirConexion();
            return ANALOGICAS.clone();
        }

        @Override
        public synchronized boolean[] leerDigitales() throws FuenteDeDatosException {
            exigirConexion();
            return DIGITALES.clone();
        }

        @Override
        public synchronized void escribirSalida(int canal, boolean encendida) throws FuenteDeDatosException {
            escrituras.add(new Escritura(canal, encendida, conectada && !fallar));
            exigirConexion();
            if (fallar) {
                throw new FuenteDeDatosException(MOTIVO);
            }
            salidas[canal] = encendida;
        }

        @Override
        public synchronized void fijarTiempoMuestreo(int milisegundos) {
            // El tiempo de muestreo no es lo que se prueba aquí
        }

        synchronized void setFallar(boolean fallar) {
            this.fallar = fallar;
        }

        /** Copia de todas las escrituras, en orden. */
        synchronized List<Escritura> escrituras() {
            return List.copyOf(escrituras);
        }

        /** Copia del estado de las salidas: el último que la fuente aceptó. */
        synchronized boolean[] salidas() {
            return salidas.clone();
        }

        synchronized boolean conectada() {
            return conectada;
        }

        synchronized int conexiones() {
            return conexiones;
        }

        synchronized int desconexiones() {
            return desconexiones;
        }

        /** Si una lectura funciona, como la que haría el Muestreador. */
        synchronized boolean entregaDatos() {
            try {
                leerAnalogicas();
                leerDigitales();
                return true;
            } catch (FuenteDeDatosException e) {
                return false;
            }
        }

        synchronized String estado() {
            return (conectada ? "conectada" : "desconectada") + ", " + conexiones + " conexiones y "
                    + desconexiones + " desconexiones";
        }

        private void exigirConexion() throws FuenteDeDatosException {
            if (!conectada) {
                throw new FuenteDeDatosException("La fuente de prueba no está conectada");
            }
        }
    }

    /** Una llamada a escribirSalida(): la salida, el estado pedido y si la fuente lo aceptó. */
    private record Escritura(int salida, boolean encendida, boolean aceptada) {

        @Override
        public String toString() {
            return "(" + salida + ", " + (encendida ? "encendida" : "apagada")
                    + (aceptada ? "" : ", rechazada") + ")";
        }
    }

    /**
     * El ControlSalidas del programa sobre una pestaña Salidas que nunca se
     * muestra. Guarda los avisos en una lista en vez de mostrarlos en un
     * cuadro de diálogo: así la prueba no abre ventanas.
     */
    private static final class SalidasDePrueba extends ControlSalidas {

        final PanelSalidas panel;

        /** Avisos que habría mostrado. Solo se usa en el hilo de Swing. */
        final List<String> avisos = new ArrayList<>();

        SalidasDePrueba(FuenteDeDatos fuente, PanelSalidas panel) {
            super(fuente, panel, null);
            this.panel = panel;
        }

        @Override
        protected void avisar(String mensaje) {
            avisos.add(mensaje);
        }
    }

    /**
     * Lo que muestra la pestaña Salidas en un instante, por salida: si el
     * interruptor está pulsado y habilitado, el texto y el color de su
     * etiqueta, y si su LED está encendido y qué dice su ayuda emergente
     * (I7LV-18). Como las pruebas de I7LV-21 e I7LV-22 comparan fotos
     * completas, "no cambia nada" también cubre los LEDs.
     */
    private record FotoSalidas(List<Boolean> pulsados, List<Boolean> habilitados,
                               List<String> textos, List<Color> colores,
                               List<Boolean> leds, List<String> ayudas) {
    }

    /** La barra y la pestaña Muestreo, que nunca se muestran, con su ControlMuestreo. */
    private record MuestreoDePrueba(BarraEstado barra, PanelMuestreo panel, ControlMuestreo control) {
    }

    /** Si se pueden usar Iniciar, Detener, el campo del tiempo y Aplicar, y el texto de la barra. */
    private record BotonesMuestreo(boolean iniciar, boolean detener, boolean campo, boolean aplicar,
                                   String estado) {
    }

    private static FuenteDePrueba fuenteConectada() {
        FuenteDePrueba fuente = new FuenteDePrueba();
        fuente.iniciar();
        return fuente;
    }

    /** Crea, en el hilo de Swing, una pestaña Salidas y su control. */
    private static SalidasDePrueba crearSalidas(FuenteDePrueba fuente) throws Exception {
        return enSwing(() -> new SalidasDePrueba(fuente, new PanelSalidas()));
    }

    /** Pulsa el interruptor de la salida como el usuario y devuelve cómo quedó la pestaña. */
    private static FotoSalidas pulsar(SalidasDePrueba s, int salida) throws Exception {
        return enSwing(() -> {
            s.panel.getInterruptores()[salida].doClick(0);
            return fotoSalidas(s);
        });
    }

    /** Toma la foto de la pestaña Salidas. Se llama en el hilo de Swing. */
    private static FotoSalidas fotoSalidas(SalidasDePrueba s) {
        List<Boolean> pulsados = new ArrayList<>();
        List<Boolean> habilitados = new ArrayList<>();
        for (JToggleButton interruptor : s.panel.getInterruptores()) {
            pulsados.add(interruptor.isSelected());
            habilitados.add(interruptor.isEnabled());
        }
        List<String> textos = new ArrayList<>();
        List<Color> colores = new ArrayList<>();
        for (JLabel estado : s.panel.getEstados()) {
            textos.add(estado.getText());
            colores.add(estado.getForeground());
        }
        List<Boolean> leds = new ArrayList<>();
        List<String> ayudas = new ArrayList<>();
        for (LedIndicador led : s.panel.getLeds()) {
            leds.add(led.isEncendido());
            ayudas.add(led.getToolTipText());
        }
        return new FotoSalidas(pulsados, habilitados, textos, colores, leds, ayudas);
    }

    /** Copia de los avisos que dio el control. */
    private static List<String> avisos(SalidasDePrueba s) throws Exception {
        return enSwing(() -> List.copyOf(s.avisos));
    }

    /**
     * Si la pestaña muestra esas salidas encendidas y las demás apagadas, con
     * los 4 interruptores habilitados: pulsado y "Encendida" en rojo, o sin
     * pulsar y "Apagada" en negro.
     */
    private static boolean pantallaDice(FotoSalidas f, boolean... encendidas) {
        for (int i = 0; i < encendidas.length; i++) {
            if (f.pulsados().get(i) != encendidas[i] || !f.habilitados().get(i)
                    || !f.textos().get(i).equals(encendidas[i] ? "Encendida" : "Apagada")
                    || !f.colores().get(i).equals(encendidas[i] ? Tema.ROJO : Tema.NEGRO)) {
                return false;
            }
        }
        return f.pulsados().size() == encendidas.length;
    }

    /** Las escrituras aceptadas que dejan las salidas 0 a 3 en esos estados, una por salida. */
    private static List<Escritura> escrituras(boolean... encendidas) {
        List<Escritura> lista = new ArrayList<>();
        for (int salida = 0; salida < encendidas.length; salida++) {
            lista.add(new Escritura(salida, encendidas[salida], true));
        }
        return lista;
    }

    /** Las escrituras ordenadas por número de salida (el orden en que se envían no importa). */
    private static List<Escritura> porSalida(List<Escritura> escrituras) {
        return escrituras.stream().sorted(Comparator.comparingInt(Escritura::salida)).toList();
    }

    /** Las escrituras que recibió la fuente desde la número desde (incluida). */
    private static List<Escritura> escriturasDesde(FuenteDePrueba fuente, int desde) {
        List<Escritura> todas = fuente.escrituras();
        return todas.subList(desde, todas.size());
    }

    /** Espera a que el contador llegue a esa cantidad de muestras. Espera como mucho 5 s. */
    private static boolean esperarMuestras(AtomicInteger muestras, int cantidad) throws InterruptedException {
        long limite = System.nanoTime() + 5_000_000_000L;
        while (muestras.get() < cantidad) {
            if (System.nanoTime() > limite) {
                return false;
            }
            Thread.sleep(5);
        }
        return true;
    }

    /** Lee los botones y el texto de la barra de ControlMuestreo. Se llama en el hilo de Swing. */
    private static BotonesMuestreo botones(MuestreoDePrueba m) {
        return new BotonesMuestreo(m.barra().getBtnIniciar().isEnabled(), m.barra().getBtnDetener().isEnabled(),
                m.panel().getTxtNuevoTiempo().isEnabled(), m.panel().getBtnAplicar().isEnabled(),
                m.barra().getLblEstado().getText());
    }

    private static String describir(FotoSalidas f) {
        List<String> etiquetas = new ArrayList<>();
        for (int i = 0; i < f.textos().size(); i++) {
            etiquetas.add("\"" + f.textos().get(i) + "\" " + nombreColor(f.colores().get(i)));
        }
        return "interruptores pulsados " + f.pulsados() + ", habilitados " + f.habilitados()
                + ", etiquetas " + etiquetas + ", LEDs encendidos " + f.leds() + ", ayudas " + f.ayudas();
    }

    private static String nombreColor(Color color) {
        if (color.equals(Tema.ROJO)) {
            return "en rojo";
        }
        if (color.equals(Tema.NEGRO)) {
            return "en negro";
        }
        return String.format("en RGB %d, %d, %d", color.getRed(), color.getGreen(), color.getBlue());
    }

    // ===================== I7LV-18 fase A: barra de estado y LEDs =====================

    /** Contadores con los que se prueba el texto de la barra: 1, 4 y 7 dígitos. */
    private static final long[] CONTADORES_BARRA = { 1, 1_234, 9_999_999 };

    /**
     * El texto de la barra en la ventana real, que nunca se muestra: el
     * constructor de VentanaPrincipal la arma con pack() pero no la hace
     * visible. Se prueba en su tamaño mínimo (800 × 550) y en el de arranque
     * (1000 × 700), con los textos de ControlMuestreo.textoEstado() en los
     * tres estados, a 10 ms y a 10000 ms (el tiempo más largo alarga el
     * texto). "Se ve completo" se decide con el mismo cálculo con que Swing
     * dibuja la etiqueta (SwingUtilities.layoutCompoundLabel): si el texto no
     * cabe, lo devuelve recortado con "...".
     */
    private static void verificarTextoBarra() throws Exception {
        List<String> textos = new ArrayList<>();
        for (int periodo : new int[] { Muestreador.PERIODO_MIN_MS, Muestreador.PERIODO_MAX_MS }) {
            for (long muestras : CONTADORES_BARRA) {
                textos.add(ControlMuestreo.textoEstado(true, true, periodo, muestras));
            }
            textos.add(ControlMuestreo.textoEstado(false, true, periodo, 0));
        }
        textos.add(ControlMuestreo.textoEstado(false, false, Muestreador.PERIODO_MIN_MS, 0));

        List<List<MedidaBarra>> porTamano = enSwing(() -> {
            Tema.aplicar(); // como Main, antes de crear la ventana
            VentanaPrincipal ventana = new VentanaPrincipal();
            try {
                return List.of(medirBarra(ventana, ventana.getMinimumSize(), textos),
                        medirBarra(ventana, ventana.getPreferredSize(), textos));
            } finally {
                ventana.dispose();
            }
        });

        for (List<MedidaBarra> medidas : porTamano) {
            MedidaBarra masLargo = medidas.stream()
                    .max(Comparator.comparingInt(MedidaBarra::anchoTexto)).orElseThrow();
            List<MedidaBarra> recortados = medidas.stream().filter(m -> !m.completo()).toList();
            verificar(String.format("Ventana de %d × %d: el texto de la barra se ve completo, sin \"...\" y sin "
                                    + "tocar los botones, en los 3 estados, con 1, 1.234 y 9.999.999 muestras, a 10 "
                                    + "y a 10000 ms (%d textos; el más largo mide %d px de %d disponibles)",
                            masLargo.anchoVentana(), masLargo.altoVentana(), medidas.size(),
                            masLargo.anchoTexto(), masLargo.anchoDisponible()),
                    recortados.isEmpty(), "no se ven completos: " + recortados);
        }

        List<String> anchos = new ArrayList<>();
        boolean fijos = true;
        for (List<MedidaBarra> medidas : porTamano) {
            fijos &= medidas.stream().map(MedidaBarra::etiqueta).distinct().count() == 1
                    && medidas.stream().map(MedidaBarra::botones).distinct().count() == 1
                    && medidas.stream().map(MedidaBarra::anchoBarra).distinct().count() == 1;
            anchos.add(medidas.stream().map(m -> m.etiqueta().width + " px").distinct().toList()
                    + " con la ventana de " + medidas.get(0).anchoVentana());
        }
        verificar("El ancho de la etiqueta de estado no cambia entre esos contadores ni entre los estados, y la "
                        + "barra y los botones no cambian de tamaño ni se mueven: " + String.join(", ", anchos),
                fijos, "etiqueta, botones y barra: " + porTamano.stream().map(medidas -> medidas.stream()
                        .map(m -> m.etiqueta() + " " + m.botones() + " " + m.anchoBarra()).distinct().toList())
                        .toList());
    }

    /**
     * El texto que arma ControlMuestreo.textoEstado() y el que pone el
     * ControlMuestreo real en su barra, que nunca se muestra. La fuente es
     * FuenteDePrueba y el tiempo de muestreo el máximo, 10 s: después de la
     * primera muestra no llega otra durante la prueba, y las 1233 que faltan
     * para 1234 se le envían al control directamente, como si vinieran del
     * Muestreador.
     */
    private static void verificarTextoDelControl() throws Exception {
        List<String> armados = List.of(
                ControlMuestreo.textoEstado(true, true, 10, 1),
                ControlMuestreo.textoEstado(true, true, 10, 1_234),
                ControlMuestreo.textoEstado(true, true, 10, 9_999_999),
                ControlMuestreo.textoEstado(false, true, 10, 1_234),
                ControlMuestreo.textoEstado(false, false, 10, 1_234));
        verificar("El texto dice el estado, el tiempo de muestreo y la cantidad de muestras, con los miles "
                        + "separados por punto: \"● Muestreando · cada 10 ms · 1.234 muestras\", \"... 9.999.999 "
                        + "muestras\" y \"... 1 muestra\"; \"Detenido · cada 10 ms\" y \"Sin conexión con la "
                        + "fuente de datos\" siguen igual",
                armados.equals(List.of(
                        "● Muestreando · cada 10 ms · 1 muestra",
                        "● Muestreando · cada 10 ms · 1.234 muestras",
                        "● Muestreando · cada 10 ms · 9.999.999 muestras",
                        "Detenido · cada 10 ms",
                        "Sin conexión con la fuente de datos")),
                "arma " + armados);

        FuenteDePrueba fuente = fuenteConectada();
        Muestreador muestreador = new Muestreador(fuente, Muestreador.PERIODO_MAX_MS);
        MuestreoDePrueba m = enSwing(() -> {
            BarraEstado barra = new BarraEstado();
            PanelMuestreo panel = new PanelMuestreo();
            return new MuestreoDePrueba(barra, panel, new ControlMuestreo(muestreador, barra, panel, null));
        });
        // Se registra después del control: cada muestra le llega primero a él
        AtomicInteger reales = new AtomicInteger();
        muestreador.agregarOyente(muestra -> reales.incrementAndGet());

        List<String> vistos = new ArrayList<>();
        vistos.add(enSwing(() -> m.barra().getLblEstado().getText()));
        // Justo después del clic el texto puede decir 0 o 1 muestras, según
        // si la primera ya llegó: no se revisa. Se espera a la primera.
        enSwing(() -> {
            m.barra().getBtnIniciar().doClick(0);
            return null;
        });
        boolean llego = esperarMuestras(reales, 1);
        vistos.add(enSwing(() -> m.barra().getLblEstado().getText()));
        Muestra otra = muestra(0);
        for (int i = 1; i < 1_234; i++) {
            m.control().muestraRecibida(otra);
        }
        vistos.add(enSwing(() -> m.barra().getLblEstado().getText()));
        int realesAlFinal = reales.get();
        vistos.add(enSwing(() -> {
            m.barra().getBtnDetener().doClick(0);
            return m.barra().getLblEstado().getText();
        }));
        vistos.add(enSwing(() -> {
            m.control().setHabilitado(false);
            return m.barra().getLblEstado().getText();
        }));
        verificar("La barra del ControlMuestreo real muestra esos textos: detenido, \"1 muestra\" tras la primera, "
                        + "\"1.234 muestras\" tras 1234, detenido otra vez y sin conexión",
                llego && realesAlFinal == 1 && vistos.equals(List.of(
                        "Detenido · cada 10000 ms",
                        "● Muestreando · cada 10000 ms · 1 muestra",
                        "● Muestreando · cada 10000 ms · 1.234 muestras",
                        "Detenido · cada 10000 ms",
                        "Sin conexión con la fuente de datos")),
                (llego ? "" : "no llegó la primera muestra; ") + "muestras reales: " + realesAlFinal
                        + "; textos: " + vistos);
    }

    private static void verificarLedsAlCrear() throws Exception {
        SalidasDePrueba s = crearSalidas(fuenteConectada());
        FotoSalidas foto = enSwing(() -> fotoSalidas(s));
        verificar("Al crear ControlSalidas, los 4 LEDs están apagados, como sus etiquetas, y la ayuda emergente de "
                        + "cada uno dice su estado (\"Salida 0: apagada\" a \"Salida 3: apagada\")",
                ledsDicen(foto, false, false, false, false) && ledsSiguenEtiquetas(foto), describir(foto));

        // Con el interruptor 1 ya pulsado antes de crear el control, como en I7LV-21 e I7LV-22
        FuenteDePrueba otra = fuenteConectada();
        SalidasDePrueba preparada = enSwing(() -> {
            PanelSalidas panel = new PanelSalidas();
            panel.getInterruptores()[1].setSelected(true);
            return new SalidasDePrueba(otra, panel);
        });
        FotoSalidas fotoPreparada = enSwing(() -> fotoSalidas(preparada));
        verificar("Con el interruptor 1 ya pulsado al crear el control, su LED se enciende al enviar el estado "
                        + "inicial, junto con la etiqueta (\"Salida 1: encendida\"); los otros 3 quedan apagados",
                ledsDicen(fotoPreparada, false, true, false, false) && ledsSiguenEtiquetas(fotoPreparada)
                        && pantallaDice(fotoPreparada, false, true, false, false),
                describir(fotoPreparada));

        // Como cuando Main no puede conectar la fuente
        SalidasDePrueba desconectada = crearSalidas(new FuenteDePrueba());
        FotoSalidas fotoDesconectada = enSwing(() -> fotoSalidas(desconectada));
        verificar("Si el estado inicial no se puede enviar (fuente sin conectar), los LEDs no cambian: siguen "
                        + "apagados, como las etiquetas, y cada uno tiene su ayuda emergente",
                ledsDicen(fotoDesconectada, false, false, false, false) && ledsSiguenEtiquetas(fotoDesconectada),
                describir(fotoDesconectada));
    }

    private static void verificarLedsSiguenEscrituras() throws Exception {
        SalidasDePrueba s = crearSalidas(fuenteConectada());
        FotoSalidas antes = enSwing(() -> fotoSalidas(s));
        FotoSalidas encendida = pulsar(s, 2);
        verificar("Tras una escritura aceptada que enciende la salida 2, su LED se enciende en la misma tarea de "
                        + "Swing que la etiqueta y su ayuda dice \"Salida 2: encendida\"; los LEDs de las demás no "
                        + "cambian",
                ledsDicen(encendida, false, false, true, false) && mismosLedsSalvo(antes, encendida, 2)
                        && ledsSiguenEtiquetas(encendida),
                describir(encendida));

        pulsar(s, 0);
        FotoSalidas conTres = pulsar(s, 3);
        FotoSalidas apagada = pulsar(s, 2);
        verificar("Al apagar la salida 2, su LED se apaga (\"Salida 2: apagada\") y los de la 0 y la 3 siguen "
                        + "encendidos",
                ledsDicen(conTres, true, false, true, true) && ledsDicen(apagada, true, false, false, true)
                        && mismosLedsSalvo(conTres, apagada, 2) && ledsSiguenEtiquetas(apagada),
                "antes: " + describir(conTres) + "; después: " + describir(apagada));
    }

    private static void verificarLedsConFalla() throws Exception {
        FuenteDePrueba fuente = fuenteConectada();
        SalidasDePrueba s = crearSalidas(fuente);
        pulsar(s, 3); // la 3 encendida, antes de que la fuente empiece a fallar
        fuente.setFallar(true);

        // Cada clic con la foto de antes y la de después en la misma tarea de Swing
        List<FotoSalidas> encender = enSwing(() -> {
            FotoSalidas previa = fotoSalidas(s);
            s.panel.getInterruptores()[1].doClick(0);
            return List.of(previa, fotoSalidas(s));
        });
        List<FotoSalidas> apagar = enSwing(() -> {
            FotoSalidas previa = fotoSalidas(s);
            s.panel.getInterruptores()[3].doClick(0);
            return List.of(previa, fotoSalidas(s));
        });
        List<String> avisos = avisos(s);
        verificar("Con la fuente configurada para fallar, el LED no cambia: al intentar encender la 1 sigue apagado "
                        + "y al intentar apagar la 3 sigue encendido, con la misma ayuda emergente (y hubo 2 avisos)",
                mismosLeds(encender.get(0), encender.get(1)) && mismosLeds(apagar.get(0), apagar.get(1))
                        && ledsDicen(apagar.get(1), false, false, false, true) && avisos.size() == 2,
                "encender la 1: " + describir(encender.get(1)) + "; apagar la 3: " + describir(apagar.get(1))
                        + "; avisos: " + avisos);
    }

    /**
     * LedIndicador creado con el constructor sin parámetros, como lo crea el
     * diseñador de NetBeans, y dibujado en una imagen con fondo blanco, como
     * lo dibuja Swing.
     */
    private static void verificarDibujoLed() throws Exception {
        DibujoLed d = enSwing(() -> {
            LedContado led = new LedContado();
            Dimension tamano = led.getPreferredSize();
            boolean alCrear = led.isEncendido();
            boolean opaco = led.isOpaque();
            BufferedImage apagado = dibujarLed(led);

            int antes = led.redibujos;
            led.setEncendido(true);
            int alEncender = led.redibujos - antes;
            BufferedImage encendido = dibujarLed(led);
            antes = led.redibujos;
            led.setEncendido(true);
            int sinCambio = led.redibujos - antes;
            antes = led.redibujos;
            led.setEncendido(false);
            int alApagar = led.redibujos - antes;

            return new DibujoLed(tamano, alCrear, opaco,
                    centro(encendido), centro(apagado), bordeSuperior(encendido), bordeSuperior(apagado),
                    brillo(encendido), brillo(apagado), List.of(alEncender, sinCambio, alApagar));
        });
        verificar("LedIndicador con el constructor sin parámetros (el que usa el diseñador de NetBeans): mide "
                        + "20 × 20 px, empieza apagado y es transparente alrededor del círculo",
                d.tamano().equals(new Dimension(20, 20)) && !d.encendidoAlCrear() && !d.opaco(),
                "mide " + d.tamano().width + " × " + d.tamano().height + ", encendido " + d.encendidoAlCrear()
                        + ", opaco " + d.opaco());
        verificar(String.format("LedIndicador dibujado en una imagen: el centro es el rojo de Tema encendido y gris "
                                + "claro (Tema.GRIS_BORDE) apagado, con contorno negro en los dos; encendido tiene "
                                + "brillo (%d píxeles más claros que el rojo cerca del centro) y apagado no",
                        d.brilloEncendido()),
                d.centroEncendido().equals(Tema.ROJO) && d.centroApagado().equals(Tema.GRIS_BORDE)
                        && esNegro(d.bordeEncendido().getRGB()) && esNegro(d.bordeApagado().getRGB())
                        && d.brilloEncendido() > 0 && d.brilloApagado() == 0,
                "centro encendido " + rgb(d.centroEncendido()) + ", apagado " + rgb(d.centroApagado())
                        + "; borde de arriba encendido " + rgb(d.bordeEncendido()) + ", apagado "
                        + rgb(d.bordeApagado()) + "; píxeles de brillo encendido " + d.brilloEncendido()
                        + ", apagado " + d.brilloApagado());
        verificar("setEncendido() lo redibuja solo: pide un redibujo al encenderlo y otro al apagarlo, y ninguno si "
                        + "el estado no cambia",
                d.redibujos().equals(List.of(1, 0, 1)),
                "redibujos al encender, al volver a encender y al apagar: " + d.redibujos());
    }

    // ===================== Utilidades de I7LV-18 fase A =====================

    /**
     * La barra de estado con un texto: lo que Swing dejaría ver (el texto, o
     * el texto recortado con "..."), cuánto mide y dónde quedó cada parte,
     * con la ventana de ese tamaño.
     */
    private record MedidaBarra(String texto, String visible, int anchoTexto, int anchoDisponible,
                               Rectangle etiqueta, Rectangle botones, int anchoBarra,
                               int anchoVentana, int altoVentana) {

        /** Si se ve todo el texto y la etiqueta queda a la derecha de los botones, sin tocarlos. */
        boolean completo() {
            return visible.equals(texto) && anchoTexto <= anchoDisponible
                    && etiqueta.x >= botones.x + botones.width;
        }

        @Override
        public String toString() {
            return "\"" + texto + "\" se ve \"" + visible + "\" (" + anchoTexto + " px de " + anchoDisponible + ")";
        }
    }

    /** Lo que se midió al dibujar un LedIndicador. */
    private record DibujoLed(Dimension tamano, boolean encendidoAlCrear, boolean opaco,
                             Color centroEncendido, Color centroApagado,
                             Color bordeEncendido, Color bordeApagado,
                             int brilloEncendido, int brilloApagado, List<Integer> redibujos) {
    }

    /** LedIndicador que cuenta los pedidos de redibujo. Solo se usa en el hilo de Swing. */
    private static final class LedContado extends LedIndicador {

        int redibujos = 0;

        @Override
        public void repaint(long tm, int x, int y, int width, int height) {
            redibujos++;
            super.repaint(tm, x, y, width, height);
        }
    }

    /**
     * Pone la ventana en ese tamaño, escribe cada texto en la etiqueta de la
     * barra y mide. validate() hace lo que Swing haría antes de dibujar la
     * ventana visible. Se llama en el hilo de Swing.
     */
    private static List<MedidaBarra> medirBarra(VentanaPrincipal ventana, Dimension tamano, List<String> textos) {
        ventana.setSize(tamano);
        BarraEstado barra = ventana.getBarraEstado();
        JLabel etiqueta = barra.getLblEstado();
        Container botones = barra.getBtnIniciar().getParent();
        List<MedidaBarra> medidas = new ArrayList<>();
        for (String texto : textos) {
            etiqueta.setText(texto);
            ventana.validate();
            Insets bordes = etiqueta.getInsets();
            Rectangle vista = new Rectangle(bordes.left, bordes.top,
                    etiqueta.getWidth() - bordes.left - bordes.right,
                    etiqueta.getHeight() - bordes.top - bordes.bottom);
            FontMetrics medidasFuente = etiqueta.getFontMetrics(etiqueta.getFont());
            String visible = SwingUtilities.layoutCompoundLabel(etiqueta, medidasFuente, texto, null,
                    etiqueta.getVerticalAlignment(), etiqueta.getHorizontalAlignment(),
                    etiqueta.getVerticalTextPosition(), etiqueta.getHorizontalTextPosition(),
                    vista, new Rectangle(), new Rectangle(), etiqueta.getIconTextGap());
            medidas.add(new MedidaBarra(texto, visible, medidasFuente.stringWidth(texto), vista.width,
                    etiqueta.getBounds(), botones.getBounds(), barra.getWidth(),
                    ventana.getWidth(), ventana.getHeight()));
        }
        return medidas;
    }

    /**
     * Si los LEDs muestran esas salidas encendidas y las demás apagadas, cada
     * uno con su ayuda emergente: "Salida i: encendida" o "Salida i: apagada".
     */
    private static boolean ledsDicen(FotoSalidas f, boolean... encendidos) {
        for (int i = 0; i < encendidos.length; i++) {
            String ayuda = "Salida " + i + ": " + (encendidos[i] ? "encendida" : "apagada");
            if (f.leds().get(i) != encendidos[i] || !ayuda.equals(f.ayudas().get(i))) {
                return false;
            }
        }
        return f.leds().size() == encendidos.length;
    }

    /** Si el LED de cada salida está encendido exactamente cuando su etiqueta dice "Encendida". */
    private static boolean ledsSiguenEtiquetas(FotoSalidas f) {
        for (int i = 0; i < f.leds().size(); i++) {
            if (f.leds().get(i) != f.textos().get(i).equals("Encendida")) {
                return false;
            }
        }
        return true;
    }

    /** Si los LEDs y sus ayudas son los mismos en las dos fotos. */
    private static boolean mismosLeds(FotoSalidas a, FotoSalidas b) {
        return a.leds().equals(b.leds()) && a.ayudas().equals(b.ayudas());
    }

    /** Si los LEDs y sus ayudas son los mismos en las dos fotos, salvo el de esa salida. */
    private static boolean mismosLedsSalvo(FotoSalidas a, FotoSalidas b, int salida) {
        for (int i = 0; i < a.leds().size(); i++) {
            if (i != salida && (!a.leds().get(i).equals(b.leds().get(i))
                    || !a.ayudas().get(i).equals(b.ayudas().get(i)))) {
                return false;
            }
        }
        return true;
    }

    /** Dibuja el LED en su tamaño preferido sobre un fondo blanco. Se llama en el hilo de Swing. */
    private static BufferedImage dibujarLed(LedIndicador led) {
        led.setSize(led.getPreferredSize());
        BufferedImage imagen = new BufferedImage(led.getWidth(), led.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagen.createGraphics();
        g.setColor(Tema.BLANCO);
        g.fillRect(0, 0, imagen.getWidth(), imagen.getHeight());
        led.paint(g);
        g.dispose();
        return imagen;
    }

    private static Color centro(BufferedImage imagen) {
        return new Color(imagen.getRGB(imagen.getWidth() / 2, imagen.getHeight() / 2));
    }

    /** El píxel de arriba en el medio: ahí pasa el contorno del círculo. */
    private static Color bordeSuperior(BufferedImage imagen) {
        return new Color(imagen.getRGB(imagen.getWidth() / 2, 0));
    }

    /**
     * Píxeles más claros que el rojo de Tema (rojo al menos igual, verde y
     * azul de 60 o más) a menos de 7 px del centro, lejos del contorno, cuyo
     * suavizado deja grises claros sobre el fondo blanco.
     */
    private static int brillo(BufferedImage imagen) {
        int cx = imagen.getWidth() / 2;
        int cy = imagen.getHeight() / 2;
        int cuenta = 0;
        for (int y = 0; y < imagen.getHeight(); y++) {
            for (int x = 0; x < imagen.getWidth(); x++) {
                Color c = new Color(imagen.getRGB(x, y));
                boolean cerca = (x - cx) * (x - cx) + (y - cy) * (y - cy) < 7 * 7;
                if (cerca && c.getRed() >= Tema.ROJO.getRed() && c.getGreen() >= 60 && c.getBlue() >= 60) {
                    cuenta++;
                }
            }
        }
        return cuenta;
    }

    private static String rgb(Color c) {
        return "RGB " + c.getRed() + ", " + c.getGreen() + ", " + c.getBlue();
    }

    // ===================== I7LV-18 fase B: base de tiempo y escala vertical =====================

    /** Duración de cada opción del selector "Ventana:", en el orden en que aparecen. */
    private static final double[] VENTANAS_S = { 1, 5, 10, 30, 60 };

    /** Textos del selector "Ventana:". */
    private static final List<String> TEXTOS_VENTANA = List.of("1 s", "5 s", "10 s", "30 s", "1 min");

    /** Textos del selector "Escala:". */
    private static final List<String> TEXTOS_ESCALA = List.of("0 a 5 V", "0 a 3,3 V", "Automática");

    /**
     * En la prueba de concurrencia, muestras que el otro hilo puede enviar
     * entre dos cambios de ventana (como MUESTRAS_POR_CAMBIO en I7LV-16):
     * así el historial no pasa de 300 000 puntos y copiarlo en cada cambio
     * no hace lenta la prueba.
     */
    private static final int MUESTRAS_POR_CAMBIO_VENTANA = 300;

    private static void verificarValoresPorDefecto() throws Exception {
        GraficaDePrueba g = crearGrafica(0);
        DigitalDePrueba d = crearDigital(0);
        Vista analogica = enSwing(() -> vista(g));
        Vista digital = enSwing(() -> vistaDigital(d));
        verificar("Por defecto la ventana es de 30 s en las dos gráficas, con el eje X de 0 a 30 s, y la escala "
                        + "de la analógica es \"0 a 5 V\", con el eje Y de 0 a 5 V",
                analogica.equals(new Vista(30, 0, 30, EscalaVertical.CERO_A_5_V, 0, 5))
                        && digital.equals(new Vista(30, 0, 30, null, Double.NaN, Double.NaN)),
                "analógica: " + analogica + "; digital: " + digital);

        Selectores enAnalogica = enSwing(() -> {
            controlVisualizacion(g.panel(), g.grafica());
            return selectores(g.panel());
        });
        Selectores enDigital = enSwing(() -> {
            controlVisualizacion(d.panel(), d.grafica());
            return selectores(d.panel());
        });
        Vista analogicaDespues = enSwing(() -> vista(g));
        Vista digitalDespues = enSwing(() -> vistaDigital(d));
        verificar("ControlVisualizacion pone las opciones de ventana (1 s, 5 s, 10 s, 30 s y 1 min) y de escala "
                        + "(0 a 5 V, 0 a 3,3 V y Automática), deja elegidas \"30 s\" y \"0 a 5 V\", y crearlo no "
                        + "cambia ninguna de las dos gráficas",
                enAnalogica.ventanas().equals(TEXTOS_VENTANA) && "30 s".equals(enAnalogica.ventanaElegida())
                        && enAnalogica.escalas().equals(TEXTOS_ESCALA)
                        && "0 a 5 V".equals(enAnalogica.escalaElegida())
                        && enDigital.ventanas().equals(TEXTOS_VENTANA) && "30 s".equals(enDigital.ventanaElegida())
                        && analogicaDespues.equals(analogica) && digitalDespues.equals(digital),
                "analógica: " + enAnalogica + ", " + analogicaDespues + "; digital: " + enDigital + ", "
                        + digitalDespues);
    }

    /**
     * ControlVisualizacion con una gráfica de prueba que solo cuenta lo que
     * le piden (GraficaDeVistaContada), en una pestaña que nunca se muestra.
     */
    private static void verificarControlVisualizacion() throws Exception {
        GraficaDeVistaContada contada = new GraficaDeVistaContada();
        List<String> pedidos = enSwing(() -> {
            PanelSenal panel = new PanelSenal(nombresAnalogicos());
            JComboBox<String> ventana = panel.getComboVentana();
            JComboBox<String> escala = panel.getComboEscala();
            controlVisualizacion(panel, contada);
            int[] avisosAction = {0};
            ventana.addActionListener(e -> avisosAction[0]++);
            ventana.setSelectedIndex(3); // 30 s: la que ya tiene
            escala.setSelectedIndex(0); // 0 a 5 V: la que ya tiene
            String alRepetir = contada.pedidos + " pedidos, " + avisosAction[0] + " ActionEvent";
            ventana.setSelectedIndex(0); // 1 s
            escala.setSelectedIndex(2); // automática
            ventana.setSelectedIndex(0); // otra vez 1 s
            return List.of(alRepetir, contada.pedidos.toString());
        });
        verificar("ControlVisualizacion solo atiende cambios reales: volver a elegir la ventana y la escala que ya "
                        + "tiene la gráfica no le pide nada (aunque el selector avise con ActionEvent), y elegir 1 s y "
                        + "Automática le pide exactamente esos dos cambios",
                pedidos.get(0).equals("[] pedidos, 1 ActionEvent")
                        && pedidos.get(1).equals("[ventana 1.0, escala Automática]"),
                "al repetir: " + pedidos.get(0) + "; en total: " + pedidos.get(1));

        GraficaDeVistaContada rara = new GraficaDeVistaContada();
        rara.ventana = 20;
        boolean rechazado = enSwing(() -> {
            PanelSenal panel = new PanelSenal(nombresAnalogicos());
            try {
                controlVisualizacion(panel, rara);
                return false;
            } catch (IllegalArgumentException e) {
                return true;
            }
        });
        verificar("Si la ventana de la gráfica (20 s) no es una de las opciones, ControlVisualizacion lo rechaza con "
                        + "IllegalArgumentException en vez de mostrar otra",
                rechazado, "se aceptó");
    }

    /**
     * La ventana en SerieEnVivo, sin gráfica, con muestras a 10 ms. Los
     * bordes de las ventanas caen justo en una muestra (por ejemplo, 35,00 s),
     * así la serie esperada es exactamente el tramo del historial.
     */
    private static void verificarVentanaEnDatos() {
        SerieEnVivo datos = new SerieEnVivo(VENTANA_S);
        XYSeries serie = datos.getSerie();
        int a45 = 45 * MUESTRAS_POR_SEGUNDO + 1;
        alimentar(datos, 0, a45);
        List<SerieEnVivo.Punto> historial45 = datos.getHistorial();

        datos.setVentanaVisible(10);
        boolean achicada = puntos(serie).equals(historial45.subList(35 * MUESTRAS_POR_SEGUNDO, a45))
                && datos.getInicioVentana() == 35.0 && datos.getFinVentana() == 45.0;
        boolean historialIgual = datos.getHistorial().equals(historial45);
        verificar("Achicar la ventana de 30 a 10 s recorta la serie dibujada: quedan los 1001 puntos de 35,00 a "
                        + "45,00 s y el eje X va de 35 a 45 s",
                achicada, "la serie tiene " + serie.getItemCount() + " puntos; " + rango(datos));

        datos.setVentanaVisible(60);
        boolean agrandada = puntos(serie).equals(historial45)
                && datos.getInicioVentana() == 0.0 && datos.getFinVentana() == 60.0;
        historialIgual &= datos.getHistorial().equals(historial45);
        verificar("Agrandarla a 1 min la reconstruye desde el historial: reaparecen los puntos de 0 a 35 s que ya "
                        + "habían salido (la serie vuelve a tener los 4501) y el eje X va de 0 a 60 s",
                agrandada, "la serie tiene " + serie.getItemCount() + " puntos; " + rango(datos));

        // Hasta 90 s: la ventana de 1 min ya se desliza
        int a90 = 90 * MUESTRAS_POR_SEGUNDO + 1;
        alimentar(datos, a45, a90);
        List<SerieEnVivo.Punto> historial90 = datos.getHistorial();
        StringBuilder detalle = new StringBuilder();
        boolean deslizada = true;
        double[] anchos = { 60, 1, 30 };
        for (double ancho : anchos) {
            datos.setVentanaVisible(ancho);
            int desde = (int) Math.round((90 - ancho) * MUESTRAS_POR_SEGUNDO);
            boolean bien = puntos(serie).equals(historial90.subList(desde, a90))
                    && datos.getInicioVentana() == 90.0 - ancho && datos.getFinVentana() == 90.0;
            deslizada &= bien;
            historialIgual &= datos.getHistorial().equals(historial90);
            detalle.append(ancho).append(" s: ").append(serie.getItemCount()).append(" puntos, ")
                    .append(rango(datos)).append("; ");
        }
        verificar("Con 90 s de datos pasa lo mismo: con 1 min la serie va de 30 a 90 s, con 1 s de 89 a 90 s y "
                        + "con 30 s de 60 a 90 s, siempre con los puntos del historial que caen en la ventana",
                deslizada, detalle.toString());
        verificar("El historial no cambia en ningún caso: después de cada cambio de ventana es el mismo (4501 "
                        + "puntos a los 45 s, 9001 a los 90 s)",
                historialIgual, "el historial tiene " + datos.getHistorial().size() + " puntos");

        // Puntos recibidos que el Timer todavía no dibujaba
        for (int i = a90; i < a90 + 5; i++) {
            datos.agregar(tiempo(i), valor(i));
        }
        datos.setVentanaVisible(5);
        boolean sinPendientes = !datos.hayPendientes();
        List<SerieEnVivo.Punto> tras = puntos(serie);
        datos.procesarPendientes();
        List<SerieEnVivo.Punto> historial = datos.getHistorial();
        verificar("Con 5 puntos recibidos que el Timer aún no dibujaba, cambiar la ventana los dibuja una sola vez: "
                        + "la serie es el tramo de 5 s que termina en ellos, sin repetidos, y ya no quedan pendientes",
                sinPendientes && tras.equals(tramoEsperado(historial, 5)) && puntos(serie).equals(tras)
                        && serie.getX(serie.getItemCount() - 1).doubleValue() == tiempo(a90 + 4),
                "la serie tiene " + tras.size() + " puntos, pendientes: " + !sinPendientes);

        // Un nuevo Iniciar todavía sin dibujar
        datos.agregar(0.0, 1.0);
        datos.agregar(0.01, 1.0);
        datos.agregar(0.02, 1.0);
        datos.setVentanaVisible(10);
        verificar("Con un nuevo Iniciar todavía sin dibujar, cambiar la ventana deja solo la corrida nueva (3 "
                        + "puntos) y el eje X de 0 a 10 s",
                puntos(serie).equals(List.of(new SerieEnVivo.Punto(0.0, 1.0), new SerieEnVivo.Punto(0.01, 1.0),
                        new SerieEnVivo.Punto(0.02, 1.0)))
                        && datos.getInicioVentana() == 0.0 && datos.getFinVentana() == 10.0
                        && !datos.hayPendientes(),
                "serie " + puntos(serie) + "; " + rango(datos));

        List<SerieEnVivo.Punto> antes = puntos(serie);
        boolean rechazadas = rechazaVentana(datos, 0) && rechazaVentana(datos, -5)
                && rechazaVentana(datos, Double.NaN) && rechazaVentana(datos, Double.POSITIVE_INFINITY);
        verificar("Una ventana de 0 s, negativa, NaN o infinita se rechaza con IllegalArgumentException, y la "
                        + "ventana y la serie siguen igual",
                rechazadas && datos.getVentanaVisible() == 10 && puntos(serie).equals(antes),
                "rechazadas: " + rechazadas + ", ventana " + datos.getVentanaVisible());
    }

    /**
     * El eje X de las gráficas reales, eligiendo cada ventana en su selector
     * como el usuario: sin datos, con datos y después de deslizarse.
     */
    private static void verificarEjeConVentana() throws Exception {
        GraficaDePrueba g = crearGrafica(0);
        DigitalDePrueba d = crearDigital(0);
        JComboBox<String> ventanaA = g.panel().getComboVentana();
        JComboBox<String> ventanaD = d.panel().getComboVentana();
        enSwing(() -> {
            controlVisualizacion(g.panel(), g.grafica());
            controlVisualizacion(d.panel(), d.grafica());
            return null;
        });

        StringBuilder sinDatos = new StringBuilder();
        boolean bienSinDatos = true;
        for (int i = 0; i < VENTANAS_S.length; i++) {
            int opcion = i;
            List<Vista> v = enSwing(() -> {
                ventanaA.setSelectedIndex(opcion);
                ventanaD.setSelectedIndex(opcion);
                return List.of(vista(g), vistaDigital(d));
            });
            for (Vista vista : v) {
                bienSinDatos &= vista.ventana() == VENTANAS_S[i] && vista.inicioX() == 0.0
                        && vista.finX() == VENTANAS_S[i];
            }
            sinDatos.append(v.get(0).rangoX()).append(" y ").append(v.get(1).rangoX()).append("; ");
        }
        verificar("Sin datos, al elegir cada ventana (1 s, 5 s, 10 s, 30 s y 1 min) el eje X va de 0 a esa "
                        + "duración, en las dos gráficas",
                bienSinDatos, sinDatos.toString());

        // 100 s de datos a 10 ms
        int hasta = 100 * MUESTRAS_POR_SEGUNDO + 1;
        enviar(g, 0, hasta);
        enviarDigital(d, 0, hasta, VerificacionSprint2::patron);
        boolean dibujo = esperarDibujo(g, 100.0) && esperarDibujoDigital(d, 100.0);

        StringBuilder conDatos = new StringBuilder();
        boolean bienConDatos = dibujo;
        for (int i = 0; i < VENTANAS_S.length; i++) {
            int opcion = i;
            double ancho = VENTANAS_S[i];
            double ultimo = tiempo(hasta - 1);
            List<Vista> elegida = enSwing(() -> {
                ventanaA.setSelectedIndex(opcion);
                ventanaD.setSelectedIndex(opcion);
                return List.of(vista(g), vistaDigital(d));
            });
            // Un segundo más: la ventana se desliza
            enviar(g, hasta, hasta + MUESTRAS_POR_SEGUNDO);
            enviarDigital(d, hasta, hasta + MUESTRAS_POR_SEGUNDO, VerificacionSprint2::patron);
            hasta += MUESTRAS_POR_SEGUNDO;
            double nuevoUltimo = tiempo(hasta - 1);
            bienConDatos &= esperarDibujo(g, nuevoUltimo) && esperarDibujoDigital(d, nuevoUltimo);
            List<Vista> deslizada = enSwing(() -> List.of(vista(g), vistaDigital(d)));
            for (Vista v : elegida) {
                bienConDatos &= v.inicioX() == ultimo - ancho && v.finX() == ultimo;
            }
            for (Vista v : deslizada) {
                bienConDatos &= v.inicioX() == nuevoUltimo - ancho && v.finX() == nuevoUltimo;
            }
            conDatos.append(TEXTOS_VENTANA.get(i)).append(": ").append(elegida.get(0).rangoX()).append(" → ")
                    .append(deslizada.get(0).rangoX()).append("; ");
        }
        verificar("Con datos, el eje X abarca la duración elegida y termina en la última muestra, al elegirla y "
                        + "después de deslizarse 1 s más, en las dos gráficas (analógica: " + conDatos + ")",
                bienConDatos, conDatos.toString());
    }

    /**
     * Las tres escalas en la gráfica analógica real, eligiéndolas en el
     * selector. Las muestras ponen el mismo valor en los 8 canales
     * (muestraConValor()), así se sabe qué se grafica.
     */
    private static void verificarEscalas() throws Exception {
        GraficaDePrueba g = crearGrafica(5);
        JComboBox<String> escala = g.panel().getComboEscala();
        JComboBox<String> ventana = g.panel().getComboVentana();
        enSwing(() -> {
            controlVisualizacion(g.panel(), g.grafica());
            return null;
        });

        List<Vista> fijas = enSwing(() -> {
            escala.setSelectedIndex(1);
            Vista tres = vista(g);
            escala.setSelectedIndex(0);
            return List.of(tres, vista(g));
        });
        verificar("\"0 a 3,3 V\" pone el eje Y de 0 a 3,3 V y \"0 a 5 V\" lo devuelve a 0 a 5 V",
                fijas.get(0).escala() == EscalaVertical.CERO_A_3_3_V
                        && cerca(new Range(fijas.get(0).inicioY(), fijas.get(0).finY()), 0.0, 3.3)
                        && fijas.get(1).escala() == EscalaVertical.CERO_A_5_V
                        && cerca(new Range(fijas.get(1).inicioY(), fijas.get(1).finY()), 0.0, 5.0),
                "con 0 a 3,3 V: " + fijas.get(0) + "; con 0 a 5 V: " + fijas.get(1));

        // Automática con una señal plana de 2,5 V
        enSwing(() -> {
            escala.setSelectedIndex(2);
            return null;
        });
        int hasta = 5 * MUESTRAS_POR_SEGUNDO + 1;
        enviarValores(g, 0, hasta, i -> 2.5);
        boolean dibujo = esperarDibujo(g, tiempo(hasta - 1));
        Range plana = enSwing(() -> ejeY(g));
        verificar("\"Automática\" con una señal plana de 2,5 V: aplica el rango mínimo de 0,1 V, centrado: el eje "
                        + "Y va de 2,45 a 2,55 V",
                dibujo && cerca(plana, 2.45, 2.55), "eje Y " + plana);

        // Una rampa de 1 a 3 V: 5 % de margen a cada lado
        int inicioRampa = hasta;
        hasta += 201;
        enviarValores(g, inicioRampa, hasta, i -> 1.0 + (i - inicioRampa) / 100.0);
        dibujo = esperarDibujo(g, tiempo(hasta - 1));
        Range rampa = enSwing(() -> ejeY(g));
        verificar("Con valores visibles de 1 a 3 V deja un margen del 5 % del rango a cada lado: el eje Y va de "
                        + "0,9 a 3,1 V",
                dibujo && cerca(rampa, 0.9, 3.1), "eje Y " + rampa);

        // Solo cuenta lo visible: con la ventana de 1 s, la rampa de 2 a 3 V
        Range visible = enSwing(() -> {
            ventana.setSelectedIndex(0);
            return ejeY(g);
        });
        verificar("Solo cuentan los valores visibles: con la ventana de 1 s se ven de 2 a 3 V y el eje Y pasa a "
                        + "ir de 1,95 a 3,05 V en el mismo cambio de ventana",
                Math.abs(visible.getLowerBound() - 1.95) < 0.002 && Math.abs(visible.getUpperBound() - 3.05) < 0.002,
                "eje Y " + visible);

        // Un ciclo del Timer que cambia el rango: un solo redibujo
        int[] avisos = {0};
        enSwing(() -> {
            g.chart().addChangeListener(e -> avisos[0]++);
            return null;
        });
        int desde = hasta;
        conSwingOcupado(() -> enviarValores(g, desde, desde + MUESTRAS_POR_CICLO, i -> 4.0));
        hasta += MUESTRAS_POR_CICLO;
        dibujo = esperarDibujo(g, tiempo(hasta - 1));
        esperarCiclosDelTimer();
        int redibujos = enSwing(() -> avisos[0]);
        Range subio = enSwing(() -> ejeY(g));
        verificar("Se recalcula en cada ciclo del Timer sin redibujos adicionales: 5 muestras nuevas de 4 V, "
                        + "procesadas en un mismo ciclo, llevan el eje Y hasta "
                        + String.format("%.3f", subio.getUpperBound()) + " V con un solo redibujo (serie y ejes juntos)",
                dibujo && redibujos == 1 && subio.getUpperBound() > 4.0,
                "se redibujó " + redibujos + " veces; eje Y " + subio);

        // Casi plana: de 2,00 a 2,04 V, en otra gráfica
        GraficaDePrueba casiPlana = crearGrafica(0);
        enSwing(() -> {
            controlVisualizacion(casiPlana.panel(), casiPlana.grafica());
            casiPlana.panel().getComboEscala().setSelectedIndex(2);
            return null;
        });
        int n = 2 * MUESTRAS_POR_SEGUNDO + 1;
        enviarValores(casiPlana, 0, n, i -> i % 2 == 0 ? 2.0 : 2.04);
        dibujo = esperarDibujo(casiPlana, tiempo(n - 1));
        Range minima = enSwing(() -> ejeY(casiPlana));
        verificar("Una señal casi plana (de 2,00 a 2,04 V) no se ve como un ruido enorme: el eje Y mide el mínimo "
                        + "de 0,1 V, de 1,97 a 2,07 V, y no 0,044 V",
                dibujo && cerca(minima, 1.97, 2.07), "eje Y " + minima);

        Range cinco = enSwing(() -> {
            escala.setSelectedIndex(0);
            return ejeY(g);
        });
        verificar("Volver a \"0 a 5 V\" desde la automática fija otra vez el eje Y de 0 a 5 V",
                cerca(cinco, 0.0, 5.0), "eje Y " + cinco);
    }

    /**
     * El punto anterior al borde izquierdo en la analógica, con muestras
     * cada 10 s y ventana de 5 s.
     */
    private static void verificarHuecoIzquierda() throws Exception {
        SerieEnVivo datos = new SerieEnVivo(5);
        for (int k = 0; k <= 4; k++) {
            datos.agregar(10.0 * k, k);
            datos.procesarPendientes();
        }
        List<SerieEnVivo.Punto> a40 = puntos(datos.getSerie());
        String rango40 = rango(datos);
        boolean bien40 = a40.equals(List.of(new SerieEnVivo.Punto(30, 3), new SerieEnVivo.Punto(40, 4)))
                && datos.getInicioVentana() == 35.0 && datos.getFinVentana() == 40.0;
        verificar("Con 10 s por muestra y ventana de 5 s, la serie conserva el último punto anterior al borde "
                        + "izquierdo: a los 40 s tiene los de 30 y 40 s con el eje X de 35 a 40 s, así la línea "
                        + "entra desde el borde (sin ese punto sería un solo punto, sin línea)",
                bien40, "serie " + a40 + "; " + rango40);

        datos.setVentanaVisible(15);
        List<SerieEnVivo.Punto> con15 = puntos(datos.getSerie());
        datos.setVentanaVisible(1);
        List<SerieEnVivo.Punto> con1 = puntos(datos.getSerie());
        verificar("... también al cambiar la ventana: con 15 s quedan los de 20, 30 y 40 s, y con 1 s los de 30 "
                        + "y 40 s",
                con15.equals(List.of(new SerieEnVivo.Punto(20, 2), new SerieEnVivo.Punto(30, 3),
                        new SerieEnVivo.Punto(40, 4)))
                        && con1.equals(List.of(new SerieEnVivo.Punto(30, 3), new SerieEnVivo.Punto(40, 4))),
                "con 15 s: " + con15 + "; con 1 s: " + con1);

        // En la gráfica real, con la escala automática
        GraficaDePrueba g = crearGrafica(0);
        enSwing(() -> {
            controlVisualizacion(g.panel(), g.grafica());
            g.panel().getComboVentana().setSelectedIndex(1); // 5 s
            g.panel().getComboEscala().setSelectedIndex(2); // automática
            return null;
        });
        for (int k = 0; k <= 4; k++) {
            g.grafica().muestraRecibida(muestraConValor(10.0 * k, k));
        }
        boolean dibujo = esperarDibujo(g, 40.0);
        List<Object> real = enSwing(() -> List.of(puntos(g.serie()), vista(g)));
        Vista v = (Vista) real.get(1);
        verificar("En la gráfica real (ventana de 5 s): la serie empieza en el punto de los 30 s, antes del borde "
                        + "(35 s), y la escala automática usa el valor con que entra la línea (3,5 V), no el del "
                        + "punto oculto (3 V): eje Y de 3,475 a 4,025 V",
                dibujo && real.get(0).equals(List.of(new SerieEnVivo.Punto(30, 3), new SerieEnVivo.Punto(40, 4)))
                        && v.inicioX() == 35.0 && v.finX() == 40.0
                        && cerca(new Range(v.inicioY(), v.finY()), 3.475, 4.025),
                "serie " + real.get(0) + "; " + v);
    }

    /**
     * El eje X después de un cambio de canal en la gráfica analógica: con el
     * muestreo detenido (el Muestreador de la prueba nunca corre) y con un
     * Muestreador real corriendo, rápido y lento.
     */
    private static void verificarEjeTrasCambioDeCanal() throws Exception {
        GraficaDePrueba g = crearGrafica(0);
        JComboBox<String> ventana = g.panel().getComboVentana();
        enSwing(() -> {
            controlVisualizacion(g.panel(), g.grafica());
            return null;
        });
        int hasta = 40 * MUESTRAS_POR_SEGUNDO + 1;
        enviar(g, 0, hasta);
        boolean dibujo = esperarDibujo(g, 40.0);
        List<Vista> cambio = enSwing(() -> {
            Vista previa = vista(g);
            g.grafica().cambiarCanal(3);
            return List.of(previa, vista(g));
        });
        esperarCiclosDelTimer();
        Vista luego = enSwing(() -> vista(g));
        Vista con5 = enSwing(() -> {
            ventana.setSelectedIndex(1);
            return vista(g);
        });
        verificar("Con el muestreo detenido, al cambiar de A0 a A3 el eje X pasa de 10–40 s a 0–30 s en el mismo "
                        + "cambio, sigue así sin muestras nuevas, y al elegir la ventana de 5 s va de 0 a 5 s",
                dibujo && cambio.get(0).inicioX() == 10.0 && cambio.get(0).finX() == 40.0
                        && cambio.get(1).inicioX() == 0.0 && cambio.get(1).finX() == 30.0
                        && luego.inicioX() == 0.0 && luego.finX() == 30.0
                        && con5.inicioX() == 0.0 && con5.finX() == 5.0,
                "antes " + cambio.get(0).rangoX() + ", al cambiar " + cambio.get(1).rangoX() + ", 150 ms después "
                        + luego.rangoX() + ", con 5 s " + con5.rangoX());

        int primera = hasta;
        enviar(g, primera, primera + 3 * MUESTRAS_POR_SEGUNDO);
        dibujo = esperarDibujo(g, tiempo(primera + 3 * MUESTRAS_POR_SEGUNDO - 1));
        Vista conMuestras = enSwing(() -> vista(g));
        verificar("... y cuando llegan muestras de A3 (desde 40,01 s), el eje X arranca en la primera y abarca 5 s: "
                        + "va de 40,01 a 45,01 s, como antes de esta tarea",
                dibujo && conMuestras.inicioX() == tiempo(primera)
                        && Math.abs(conMuestras.finX() - (tiempo(primera) + 5)) < 1e-9,
                conMuestras.rangoX());

        // Muestreo corriendo a 10 ms: el eje no debe pasar por 0 un instante
        FuenteDePrueba fuente = fuenteConectada();
        Muestreador rapido = new Muestreador(fuente, 10);
        AtomicInteger llegadas = new AtomicInteger();
        rapido.agregarOyente(muestra -> llegadas.incrementAndGet());
        GraficaDePrueba r = enSwing(() -> {
            PanelSenal panel = new PanelSenal(nombresAnalogicos());
            GraficaContada grafica = new GraficaContada(rapido, panel, 0);
            grafica.setVentanaVisible(1);
            return new GraficaDePrueba(panel, grafica);
        });
        List<Range> rangos = java.util.Collections.synchronizedList(new ArrayList<>());
        rapido.iniciar();
        boolean corrio = esperarMuestras(llegadas, 250); // unos 2,5 s: el eje X va de 1,5 a 2,5 s
        Range alCambiar = enSwing(() -> {
            ValueAxis eje = r.chart().getXYPlot().getDomainAxis();
            eje.addChangeListener(e -> rangos.add(eje.getRange()));
            r.grafica().cambiarCanal(3);
            return eje.getRange();
        });
        int enElCambio = llegadas.get();
        boolean llegaronMas = esperarMuestras(llegadas, enElCambio + 20)
                && esperarEnSwing(() -> r.serie().getItemCount() >= 20);
        List<Object> final10 = enSwing(() -> List.of(vista(r), puntos(r.serie())));
        rapido.detener();
        List<Range> vistos = List.copyOf(rangos);
        @SuppressWarnings("unchecked")
        List<SerieEnVivo.Punto> serie10 = (List<SerieEnVivo.Punto>) final10.get(1);
        Vista vista10 = (Vista) final10.get(0);
        boolean nuncaEnCero = vistos.stream().noneMatch(rango -> rango.getLowerBound() == 0.0);
        verificar(String.format("Con el muestreo corriendo a 10 ms, al cambiar de canal el eje X no pasa por 0 a 1 s: "
                                + "se queda en %.2f–%.2f s hasta la primera muestra del canal nuevo y arranca en ella "
                                + "(%d cambios del eje, ninguno en 0)",
                        alCambiar.getLowerBound(), alCambiar.getUpperBound(), vistos.size()),
                corrio && llegaronMas && alCambiar.getLowerBound() > 1.0 && nuncaEnCero && !serie10.isEmpty()
                        && soloVale(valores(serie10), valorCanal(3))
                        && vista10.inicioX() >= serie10.get(0).tiempo() && !vistos.isEmpty(),
                "rangos del eje: " + vistos + "; serie desde " + (serie10.isEmpty() ? "-" : serie10.get(0)));

        // Muestreo corriendo a 1000 ms: la próxima muestra tarda casi 1 s
        Muestreador lento = new Muestreador(fuenteConectada(), 1000);
        AtomicInteger llegadasLento = new AtomicInteger();
        lento.agregarOyente(muestra -> llegadasLento.incrementAndGet());
        GraficaDePrueba l = enSwing(() -> {
            PanelSenal panel = new PanelSenal(nombresAnalogicos());
            GraficaContada grafica = new GraficaContada(lento, panel, 0);
            grafica.setVentanaVisible(1);
            return new GraficaDePrueba(panel, grafica);
        });
        lento.iniciar();
        // Las muestras llegan a los 0, 1 y 2 s. Se espera a que la tercera
        // esté dibujada (el eje X va de 1 a 2 s), y enseguida se cambia de
        // canal, en la misma tarea de Swing en que se revisa.
        boolean tres = esperarMuestras(llegadasLento, 3)
                && esperarEnSwing(() -> !l.serie().isEmpty() && l.serie().getMaxX() > 1.5);
        List<Vista> lentoCambio = enSwing(() -> {
            Vista previa = vista(l);
            l.grafica().cambiarCanal(3);
            return List.of(previa, vista(l));
        });
        boolean cuatro = esperarMuestras(llegadasLento, 4) // a los 3 s
                && esperarEnSwing(() -> !l.serie().isEmpty());
        List<Object> finalLento = enSwing(() -> List.of(vista(l), puntos(l.serie())));
        lento.detener();
        Vista vistaLento = (Vista) finalLento.get(0);
        @SuppressWarnings("unchecked")
        List<SerieEnVivo.Punto> serieLento = (List<SerieEnVivo.Punto>) finalLento.get(1);
        verificar("Con el muestreo corriendo a 1000 ms, al cambiar de canal justo después de una muestra (la "
                        + "siguiente tarda casi 1 s), el eje X pasa al instante de " + lentoCambio.get(0).rangoX()
                        + " a 0–1 s (de 0 a la duración de la ventana), y con la primera muestra de A3 arranca en "
                        + "ella",
                tres && cuatro && lentoCambio.get(0).inicioX() > 0.9
                        && lentoCambio.get(1).inicioX() == 0.0 && lentoCambio.get(1).finX() == 1.0
                        && serieLento.size() == 1 && vistaLento.inicioX() == serieLento.get(0).tiempo()
                        && vistaLento.inicioX() > 2.5,
                "antes " + lentoCambio.get(0).rangoX() + ", al cambiar " + lentoCambio.get(1).rangoX()
                        + ", después " + vistaLento.rangoX() + ", serie " + serieLento);
    }

    /**
     * La ventana en la gráfica digital: en SenalesDigitalesEnVivo, con bits
     * que tienen pulsos de una sola muestra, y en la gráfica real con su
     * selector.
     */
    private static void verificarVentanaDigitalFaseB() throws Exception {
        Random azar = new Random(21);
        int total = 45 * MUESTRAS_POR_SEGUNDO + 1;
        int[] bits = new int[total];
        for (int i = 1; i < total; i++) {
            boolean agitado = (i / (5 * MUESTRAS_POR_SEGUNDO)) % 2 == 0;
            int cambian = agitado ? azar.nextInt(16) : (azar.nextInt(100) < 2 ? 1 << azar.nextInt(4) : 0);
            bits[i] = bits[i - 1] ^ cambian;
        }
        SenalesDigitalesEnVivo datos = new SenalesDigitalesEnVivo(CANALES_DIGITALES, VENTANA_S);
        alimentarAlAzar(datos, bits, 0, total, azar);
        List<List<SerieEnVivo.Punto>> historiales = new ArrayList<>();
        for (int canal = 0; canal < CANALES_DIGITALES; canal++) {
            historiales.add(datos.getHistorial(canal));
        }

        int errores = 0;
        int puntosDeMas = 0;
        boolean historialesIguales = true;
        StringBuilder detalle = new StringBuilder();
        double[] anchos = { 10, 60, 1, 5 };
        for (double ancho : anchos) {
            datos.setVentanaVisible(ancho);
            double inicio = datos.getInicioVentana();
            double ultimo = tiempo(total - 1);
            if (inicio != Math.max(0.0, ultimo - ancho) || datos.getFinVentana() != inicio + ancho) {
                errores++;
            }
            // Primera muestra dentro de la ventana y la anterior, que da el valor en el borde
            int primeraVisible = 0;
            while (tiempo(primeraVisible) < ultimo - ancho) {
                primeraVisible++;
            }
            int primero = Math.max(0, primeraVisible - 1);
            for (int carril = 0; carril <= CARRIL_VALOR; carril++) {
                XYSeries serie = serieDe(datos, carril);
                for (int j = primeraVisible; j < total; j++) {
                    if (valorEn(serie, tiempo(j)) != esperado(bits[j], carril)) {
                        errores++;
                    }
                }
                int borde = (int) Math.floor(inicio * MUESTRAS_POR_SEGUNDO + 1e-9);
                if (valorEn(serie, inicio) != esperado(bits[borde], carril)) {
                    errores++;
                }
                int flancos = 0;
                for (int j = primero + 1; j < total; j++) {
                    if (esperado(bits[j], carril) != esperado(bits[j - 1], carril)) {
                        flancos++;
                    }
                }
                boolean finalCambio = esperado(bits[total - 1], carril) != esperado(bits[total - 2], carril);
                puntosDeMas += Math.abs(serie.getItemCount() - (1 + flancos + (finalCambio ? 0 : 1)));
            }
            for (int canal = 0; canal < CANALES_DIGITALES; canal++) {
                historialesIguales &= datos.getHistorial(canal).equals(historiales.get(canal));
            }
            detalle.append(ancho).append(" s: Valor con ").append(datos.getSerieValor().getItemCount())
                    .append(" puntos, ").append(rango(datos)).append("; ");
        }
        verificar("En la digital, cambiar la ventana a 10 s, 1 min, 1 s y 5 s reconstruye los 5 carriles sin perder "
                        + "flancos: la onda pasa por el valor de cada muestra visible (incluidos pulsos de una sola "
                        + "muestra) y por el del borde izquierdo, y con 1 min reaparecen los 45 s completos",
                errores == 0, errores + " valores distintos; " + detalle);
        verificar("... conservando la optimización: cada carril tiene exactamente un punto por flanco, más el del "
                        + "borde y el final (" + detalle + ")",
                puntosDeMas == 0, puntosDeMas + " puntos de más o de menos");
        verificar("... y los historiales de los 4 canales no cambian (4501 muestras cada uno)",
                historialesIguales, "tamaño de D0: " + datos.getHistorial(0).size());

        // Lecturas pendientes al cambiar la ventana
        for (int i = total; i < total + 3; i++) {
            datos.agregar(tiempo(i), 0b0110);
        }
        datos.setVentanaVisible(10);
        boolean pendientesBien = !datos.hayPendientes() && Math.abs(datos.getFinVentana() - tiempo(total + 2)) < 1e-9;
        for (int carril = 0; carril <= CARRIL_VALOR; carril++) {
            pendientesBien &= valorEn(serieDe(datos, carril), tiempo(total + 2)) == esperado(0b0110, carril);
        }
        verificar("Con lecturas recibidas que el Timer aún no dibujaba, cambiar la ventana las dibuja y no deja "
                        + "pendientes",
                pendientesBien, rango(datos));

        // La gráfica real con su selector: la escala no aparece
        DigitalDePrueba d = crearDigital(0);
        Selectores s = enSwing(() -> {
            controlVisualizacion(d.panel(), d.grafica());
            return selectores(d.panel());
        });
        verificar("En la pestaña digital, ControlVisualizacion oculta la etiqueta \"Escala:\" y su selector; el de "
                        + "ventana queda visible con sus 5 opciones",
                !s.escalaVisible() && !s.etiquetaEscalaVisible() && s.ventanaVisible()
                        && s.ventanas().equals(TEXTOS_VENTANA),
                s.toString());

        int hasta = 45 * MUESTRAS_POR_SEGUNDO + 1;
        enviarDigital(d, 0, hasta, VerificacionSprint2::patron);
        boolean dibujo = esperarDibujoDigital(d, 45.0);
        int mal = 0;
        StringBuilder rangos = new StringBuilder();
        for (int opcion : new int[] { 1, 4, 0 }) { // 5 s, 1 min y 1 s
            List<Object> r = enSwing(() -> {
                d.panel().getComboVentana().setSelectedIndex(opcion);
                return List.of(vistaDigital(d), dibujadoDistintoPatron(d, hasta));
            });
            Vista v = (Vista) r.get(0);
            mal += (Integer) r.get(1);
            if (v.inicioX() != Math.max(0, 45.0 - VENTANAS_S[opcion]) || v.finX() != v.inicioX() + VENTANAS_S[opcion]) {
                mal++;
            }
            rangos.append(TEXTOS_VENTANA.get(opcion)).append(": ").append(v.rangoX()).append("; ");
        }
        verificar("En la gráfica digital real, elegir 5 s, 1 min y 1 s en el selector mueve el eje X y los 5 "
                        + "carriles valen lo enviado en cada muestra visible (" + rangos + ")",
                dibujo && mal == 0, mal + " fallas");
    }

    /**
     * Un hilo envía muestras (1 ms entre muestras) a una gráfica analógica y
     * a una digital reales, mientras el hilo de Swing cambia la ventana de
     * las dos 1000 veces, al azar. Como en I7LV-16, el otro hilo envía a lo
     * sumo MUESTRAS_POR_CAMBIO_VENTANA muestras entre dos cambios.
     *
     * En cada cambio, en la misma tarea de Swing: la serie analógica es un
     * tramo seguido del historial (sin repetidos ni huecos), entra desde el
     * borde izquierdo y el eje X mide la ventana elegida. Cada 20 cambios,
     * los 5 carriles digitales valen lo enviado en cada muestra visible. Al
     * final, los historiales están completos y en orden.
     */
    private static void verificarVentanaConcurrente() throws Exception {
        GraficaDePrueba g = crearGrafica(0);
        DigitalDePrueba d = crearDigital(0);
        AtomicBoolean enviando = new AtomicBoolean(true);
        AtomicInteger cambiosHechos = new AtomicInteger();
        AtomicLong enviadas = new AtomicLong();
        AtomicReference<Throwable> errorProductor = new AtomicReference<>();
        Thread productor = new Thread(() -> {
            long i = 0;
            int cambiosVistos = 0;
            int desdeElCambio = 0;
            while (enviando.get()) {
                int hechos = cambiosHechos.get();
                if (hechos != cambiosVistos) {
                    cambiosVistos = hechos;
                    desdeElCambio = 0;
                }
                if (desdeElCambio < MUESTRAS_POR_CAMBIO_VENTANA) {
                    Muestra muestra = muestraDigital(tiempoRapido(i), patronRapido(i));
                    g.grafica().muestraRecibida(muestra);
                    d.grafica().muestraRecibida(muestra);
                    i++;
                    desdeElCambio++;
                } else {
                    Thread.onSpinWait();
                }
            }
            enviadas.set(i);
        }, "Muestreador de prueba");
        productor.setUncaughtExceptionHandler(guardarEImprimir(errorProductor));

        Random azar = new Random(18);
        final int cambios = 1000;
        int incoherencias = 0;
        int digitalDistinto = 0;
        int revisionesDigital = 0;
        long inicio = System.nanoTime();
        productor.start();
        for (int k = 0; k < cambios; k++) {
            double ancho = VENTANAS_S[azar.nextInt(VENTANAS_S.length)];
            boolean revisarDigital = k % 20 == 0;
            int[] r = enSwing(() -> {
                g.grafica().setVentanaVisible(ancho);
                d.grafica().setVentanaVisible(ancho);
                cambiosHechos.incrementAndGet(); // el otro hilo puede enviar otra tanda
                int malDigital = revisarDigital ? digitalDistintoHastaLoDibujado(d) : 0;
                return new int[] { serieIncoherente(g, ancho), malDigital };
            });
            incoherencias += r[0];
            digitalDistinto += r[1];
            revisionesDigital += revisarDigital ? 1 : 0;
            pausar(azar.nextInt(400));
        }
        enviando.set(false);
        productor.join();
        double segundos = (System.nanoTime() - inicio) / 1e9;

        // Unas muestras más, ya sin cambios, para esperar a que se dibujen
        long fin = enviadas.get();
        for (long i = fin; i < fin + 10; i++) {
            Muestra muestra = muestraDigital(tiempoRapido(i), patronRapido(i));
            g.grafica().muestraRecibida(muestra);
            d.grafica().muestraRecibida(muestra);
        }
        long totalEnviadas = fin + 10;
        boolean dibujo = esperarDibujo(g, tiempoRapido(totalEnviadas - 1))
                && esperarDibujoDigital(d, tiempoRapido(totalEnviadas - 1));
        List<Object> alFinal = enSwing(() -> List.of(g.grafica().getHistorial(), d.grafica().getHistorial(),
                serieIncoherente(g, g.grafica().getVentanaVisible()),
                dibujadoDistinto(d, d.grafica().getHistorial())));
        @SuppressWarnings("unchecked")
        List<SerieEnVivo.Punto> historialA = (List<SerieEnVivo.Punto>) alFinal.get(0);
        @SuppressWarnings("unchecked")
        List<SerieEnVivo.Punto> historialD = (List<SerieEnVivo.Punto>) alFinal.get(1);
        int huecos = 0;
        for (int j = 0; j < historialA.size(); j++) {
            if (historialA.get(j).tiempo() != tiempoRapido(j)) {
                huecos++;
            }
        }
        boolean completos = historialA.size() == totalEnviadas && huecos == 0
                && historialD.size() == totalEnviadas && incoherencias(historialD, 0) == 0;

        verificar(String.format("Cambiar la ventana %d veces mientras otro hilo envía muestras (%,d en %.1f s, a lo "
                                + "sumo %d por cambio): en cada cambio la serie analógica es un tramo seguido del "
                                + "historial, sin repetidos ni huecos, que entra desde el borde, con el eje X de la "
                                + "ventana elegida; y en %d revisiones los 5 carriles digitales valen lo enviado",
                        cambios, totalEnviadas, segundos, MUESTRAS_POR_CAMBIO_VENTANA, revisionesDigital),
                errorProductor.get() == null && incoherencias == 0 && digitalDistinto == 0,
                (errorProductor.get() != null ? "error: " + errorProductor.get() + "; " : "")
                        + incoherencias + " incoherencias analógicas; " + digitalDistinto + " valores digitales mal");
        verificar("... y al final los historiales están intactos: completos, en orden y sin huecos (analógico y "
                        + "digital, " + totalEnviadas + " muestras), y lo dibujado coincide con ellos",
                dibujo && completos && (Integer) alFinal.get(2) == 0 && (Integer) alFinal.get(3) == 0,
                "historial analógico " + historialA.size() + " (" + huecos + " fuera de lugar), digital "
                        + historialD.size() + "; al final: " + alFinal.get(2) + " incoherencias analógicas, "
                        + alFinal.get(3) + " valores digitales mal");
    }

    /**
     * RendererEscalones, que reemplaza a XYStepRenderer en los carriles D0 a
     * D3, debe verse igual. Se dibuja la gráfica digital real en una imagen
     * con los dos renderers, con los mismos colores y trazos, y se comparan
     * los píxeles. Con el suavizado, los bordes de las líneas pueden variar
     * en unos tonos; lo que no puede haber es un píxel muy distinto (una
     * línea que falta o que sobra): diferencia de más de 100 en algún color.
     * - Tramos anchos (D0 cambia cada 0,5 s, con 30 s): se dibujan completos.
     * - El peor caso (los 4 bits cambian en cada muestra, con 1 min): la
     *   franja de rayas por columna debe verse como la de XYStepRenderer.
     */
    private static void verificarRendererEscalones() throws Exception {
        IntUnaryOperator lento = i -> ((i / 50) & 1) | (((i / 100) & 1) << 1) | (((i / 200) & 1) << 2) | 0b1000;
        IntUnaryOperator peor = i -> i % 2 == 0 ? 0b0000 : 0b1111;
        Object[][] casos = { { "tramos anchos, 30 s", 30.0, 45 * MUESTRAS_POR_SEGUNDO, lento },
                { "peor caso, 1 min", 60.0, 70 * MUESTRAS_POR_SEGUNDO, peor } };
        StringBuilder detalle = new StringBuilder();
        boolean iguales = true;
        for (Object[] caso : casos) {
            DigitalDePrueba d = crearDigital(1);
            double ancho = (Double) caso[1];
            int hasta = (Integer) caso[2];
            enSwing(() -> {
                d.grafica().setVentanaVisible(ancho);
                return null;
            });
            enviarDigital(d, 0, hasta, (IntUnaryOperator) caso[3]);
            esperarDibujoDigital(d, tiempo(hasta - 1));
            BufferedImage nuevo = enSwing(() -> dibujarGrafica(d.chart()));
            BufferedImage conJFreeChart = enSwing(() -> {
                for (int canal = 0; canal < CANALES_DIGITALES; canal++) {
                    XYItemRenderer nuestro = d.carril(canal).getRenderer();
                    org.jfree.chart.renderer.xy.XYStepRenderer escalones =
                            new org.jfree.chart.renderer.xy.XYStepRenderer();
                    escalones.setSeriesPaint(0, nuestro.getSeriesPaint(0));
                    escalones.setSeriesStroke(0, nuestro.getSeriesStroke(0));
                    d.carril(canal).setRenderer(escalones);
                }
                return dibujarGrafica(d.chart());
            });
            int distintos = 0;
            int muyDistintos = 0;
            for (int y = 0; y < nuevo.getHeight(); y++) {
                for (int x = 0; x < nuevo.getWidth(); x++) {
                    int a = nuevo.getRGB(x, y);
                    int b = conJFreeChart.getRGB(x, y);
                    if (a != b) {
                        distintos++;
                        int diferencia = Math.max(Math.abs(((a >> 16) & 0xFF) - ((b >> 16) & 0xFF)),
                                Math.max(Math.abs(((a >> 8) & 0xFF) - ((b >> 8) & 0xFF)),
                                        Math.abs((a & 0xFF) - (b & 0xFF))));
                        if (diferencia > 100) {
                            muyDistintos++;
                        }
                    }
                }
            }
            iguales &= muyDistintos == 0;
            detalle.append(caso[0]).append(": ").append(distintos).append(" píxeles con otro tono, ")
                    .append(muyDistintos).append(" muy distintos; ");
        }
        verificar("RendererEscalones se ve igual que XYStepRenderer (gráfica de 1000 × 560 px): ningún píxel muy "
                        + "distinto, ni con tramos anchos ni en el peor caso (" + detalle + ")",
                iguales, detalle.toString());
    }

    /** Dibuja la gráfica en una imagen de 1000 × 560 px, como el ChartPanel. Se llama en el hilo de Swing. */
    private static BufferedImage dibujarGrafica(JFreeChart grafica) {
        BufferedImage imagen = new BufferedImage(1000, 560, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = imagen.createGraphics();
        grafica.draw(g2, new Rectangle(0, 0, 1000, 560), null, new ChartRenderingInfo());
        g2.dispose();
        return imagen;
    }

    /**
     * Rendimiento con la ventana de 1 min a 10 ms (6000 puntos a la vista):
     * cada ciclo del Timer, el cambio de ventana y el dibujo de las gráficas.
     */
    private static void verificarRendimientoVentanaMinuto() throws Exception {
        final int total = 10_000; // 100 s a 10 ms
        Random azar = new Random(30);
        int[] alAzar = new int[total];
        int[] peorCaso = new int[total];
        int estado = 0;
        for (int i = 0; i < total; i++) {
            for (int canal = 0; canal < CANALES_DIGITALES; canal++) {
                if (azar.nextDouble() < 0.1) {
                    estado ^= 1 << canal;
                }
            }
            alAzar[i] = estado;
            peorCaso[i] = i % 2 == 0 ? 0b0000 : 0b1111;
        }

        // 1) Cada ciclo del Timer: un ciclo cada 5 muestras, como a 10 ms
        SerieEnVivo analogica = new SerieEnVivo(60);
        SenalesDigitalesEnVivo digitalAzar = new SenalesDigitalesEnVivo(CANALES_DIGITALES, 60);
        SenalesDigitalesEnVivo digitalPeor = new SenalesDigitalesEnVivo(CANALES_DIGITALES, 60);
        double[] promedio = new double[3];
        double[] peor = new double[3];
        for (int i = 0; i < total; i++) {
            analogica.agregar(tiempo(i), valor(i));
            digitalAzar.agregar(tiempo(i), alAzar[i]);
            digitalPeor.agregar(tiempo(i), peorCaso[i]);
            if ((i + 1) % MUESTRAS_POR_CICLO == 0) {
                Runnable[] ciclos = { analogica::procesarPendientes, digitalAzar::procesarPendientes,
                        digitalPeor::procesarPendientes };
                for (int c = 0; c < ciclos.length; c++) {
                    long t0 = System.nanoTime();
                    ciclos[c].run();
                    double ms = (System.nanoTime() - t0) / 1e6;
                    promedio[c] += ms / (total / MUESTRAS_POR_CICLO);
                    peor[c] = Math.max(peor[c], ms);
                }
            }
        }
        verificar(String.format("Ventana de 1 min, 10 000 muestras a 10 ms: cada ciclo del Timer tarda mucho menos "
                                + "que 50 ms (promedio y peor ciclo: analógica %.3f y %.1f ms, digital al azar %.3f y "
                                + "%.1f ms, digital en el peor caso %.3f y %.1f ms)",
                        promedio[0], peor[0], promedio[1], peor[1], promedio[2], peor[2]),
                promedio[0] < 1.0 && promedio[1] < 1.0 && promedio[2] < 1.0
                        && peor[0] < 50.0 && peor[1] < 50.0 && peor[2] < 50.0
                        && puntos(analogica.getSerie()).equals(tramoEsperado(analogica.getHistorial(), 60)),
                "demasiado lento, o la serie no es el último minuto: tiene " + analogica.getSerie().getItemCount()
                        + " puntos");

        // 2) Cambiar la ventana con 100 s de datos: la reconstrucción de 1 min es la más cara
        double peorCambio = 0;
        for (int k = 0; k < 20; k++) {
            double ancho = k % 2 == 0 ? 1 : 60;
            long t0 = System.nanoTime();
            analogica.setVentanaVisible(ancho);
            digitalAzar.setVentanaVisible(ancho);
            digitalPeor.setVentanaVisible(ancho);
            if (k >= 4) { // los primeros calientan la máquina virtual
                peorCambio = Math.max(peorCambio, (System.nanoTime() - t0) / 1e6);
            }
        }
        verificar(String.format("Cambiar entre 1 s y 1 min con 100 s de datos reconstruye las tres series (analógica, "
                        + "digital al azar y en el peor caso) en %.1f ms como mucho, menos que un ciclo de 50 ms",
                        peorCambio),
                peorCambio < 50.0, "demasiado lento");

        // 3) Dibujar las gráficas reales con 1 min de datos, como lo hace el ChartPanel
        GraficaDePrueba g = crearGrafica(0);
        enSwing(() -> {
            g.grafica().setVentanaVisible(60);
            return null;
        });
        int hasta = 70 * MUESTRAS_POR_SEGUNDO;
        enviarValores(g, 0, hasta, VerificacionSprint2::valor);
        esperarDibujo(g, tiempo(hasta - 1));
        double analogicaMs = tiempoDeDibujo(g.chart());
        double[] digitalMs = new double[2];
        int[][] casos = { alAzar, peorCaso };
        for (int c = 0; c < casos.length; c++) {
            DigitalDePrueba d = crearDigital(0);
            enSwing(() -> {
                d.grafica().setVentanaVisible(60);
                return null;
            });
            int[] caso = casos[c];
            enviarDigital(d, 0, hasta, i -> caso[i]);
            esperarDibujoDigital(d, tiempo(hasta - 1));
            digitalMs[c] = tiempoDeDibujo(d.chart());
        }
        verificar(String.format("Dibujar las gráficas (1000 × 560 px) con 1 min de datos a 10 ms tarda menos que el "
                                + "ciclo de 50 ms: analógica %.1f ms, digital al azar %.1f ms, digital en el peor caso "
                                + "%.1f ms (promedios)",
                        analogicaMs, digitalMs[0], digitalMs[1]),
                analogicaMs < 50.0 && digitalMs[0] < 50.0 && digitalMs[1] < 50.0, "demasiado lento");
    }

    // ===================== Utilidades de I7LV-18 fase B =====================

    /**
     * Lo que muestra una gráfica sobre su ventana y su escala: la duración
     * de la ventana, el eje X, la escala y el eje Y. En la digital, la escala
     * es null y el eje Y, NaN.
     */
    private record Vista(double ventana, double inicioX, double finX, EscalaVertical escala,
                         double inicioY, double finY) {

        String rangoX() {
            return String.format("%.2f–%.2f s", inicioX, finX);
        }
    }

    /**
     * Gráfica de prueba para ControlVisualizacion: no dibuja nada, solo
     * anota cada cambio que le piden. Solo se usa en el hilo de Swing.
     */
    private static final class GraficaDeVistaContada implements VentanaAjustable, EscalaAjustable {

        double ventana = 30;
        EscalaVertical escala = EscalaVertical.CERO_A_5_V;
        final List<String> pedidos = new ArrayList<>();

        @Override
        public double getVentanaVisible() {
            return ventana;
        }

        @Override
        public void setVentanaVisible(double segundos) {
            pedidos.add("ventana " + segundos);
            ventana = segundos;
        }

        @Override
        public EscalaVertical getEscala() {
            return escala;
        }

        @Override
        public void setEscala(EscalaVertical nueva) {
            pedidos.add("escala " + nueva);
            escala = nueva;
        }
    }

    /** Lo que muestran los selectores de ventana y escala de una pestaña. */
    private record Selectores(List<String> ventanas, Object ventanaElegida, List<String> escalas,
                              Object escalaElegida, boolean ventanaVisible, boolean escalaVisible,
                              boolean etiquetaEscalaVisible) {
    }

    /**
     * Espera a que la condición, revisada en el hilo de Swing, se cumpla.
     * Espera como mucho 5 s. Sirve cuando las muestras las envía un
     * Muestreador real: esperar un tiempo fijo a que el Timer dibuje no
     * alcanza si la máquina virtual se detiene un momento (por ejemplo, para
     * recolectar basura), y la prueba fallaría sin que la gráfica tenga nada
     * mal.
     *
     * @return true si se cumplió
     */
    private static boolean esperarEnSwing(Supplier<Boolean> condicion) throws Exception {
        long limite = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() < limite) {
            if (enSwing(condicion)) {
                return true;
            }
            Thread.sleep(10);
        }
        return false;
    }

    /** Conecta, como Main, los selectores de ventana y escala de la pestaña con su gráfica. */
    private static ControlVisualizacion controlVisualizacion(PanelSenal panel, VentanaAjustable grafica) {
        return new ControlVisualizacion(panel.getComboVentana(), panel.getLblEscala(),
                panel.getComboEscala(), grafica);
    }

    /** Se llama en el hilo de Swing. */
    private static Vista vista(GraficaDePrueba g) {
        Range x = g.chart().getXYPlot().getDomainAxis().getRange();
        Range y = ejeY(g);
        return new Vista(g.grafica().getVentanaVisible(), x.getLowerBound(), x.getUpperBound(),
                g.grafica().getEscala(), y.getLowerBound(), y.getUpperBound());
    }

    /** Se llama en el hilo de Swing. */
    private static Vista vistaDigital(DigitalDePrueba d) {
        Range x = d.chart().getXYPlot().getDomainAxis().getRange();
        return new Vista(d.grafica().getVentanaVisible(), x.getLowerBound(), x.getUpperBound(), null,
                Double.NaN, Double.NaN);
    }

    /** Rango del eje Y de la gráfica analógica. Se llama en el hilo de Swing. */
    private static Range ejeY(GraficaDePrueba g) {
        return g.chart().getXYPlot().getRangeAxis().getRange();
    }

    /** Se llama en el hilo de Swing. */
    private static Selectores selectores(PanelSenal panel) {
        return new Selectores(opciones(panel.getComboVentana()), panel.getComboVentana().getSelectedItem(),
                opciones(panel.getComboEscala()), panel.getComboEscala().getSelectedItem(),
                panel.getComboVentana().isVisible(), panel.getComboEscala().isVisible(),
                panel.getLblEscala().isVisible());
    }

    private static List<String> opciones(JComboBox<String> combo) {
        List<String> opciones = new ArrayList<>();
        for (int i = 0; i < combo.getItemCount(); i++) {
            opciones.add(combo.getItemAt(i));
        }
        return opciones;
    }

    /** Si el rango va de esos límites, salvo el redondeo de los decimales. */
    private static boolean cerca(Range rango, double inferior, double superior) {
        return Math.abs(rango.getLowerBound() - inferior) < 1e-9 && Math.abs(rango.getUpperBound() - superior) < 1e-9;
    }

    /** Muestra sintética con el mismo valor en los 8 canales analógicos. */
    private static Muestra muestraConValor(double tiempo, double valor) {
        double[] analogicas = new double[CANALES];
        Arrays.fill(analogicas, valor);
        return new Muestra(tiempo, analogicas, DIGITALES);
    }

    /** Envía las muestras desde (incluida) hasta (excluida), con ese valor en todos los canales. */
    private static void enviarValores(GraficaDePrueba g, int desde, int hasta, IntToDoubleFunction valor) {
        for (int i = desde; i < hasta; i++) {
            g.grafica().muestraRecibida(muestraConValor(tiempo(i), valor.applyAsDouble(i)));
        }
    }

    private static String[] nombresAnalogicos() {
        String[] nombres = new String[CANALES];
        for (int i = 0; i < CANALES; i++) {
            nombres[i] = "A" + i;
        }
        return nombres;
    }

    /** Intenta poner esa ventana y dice si se rechazó con IllegalArgumentException. */
    private static boolean rechazaVentana(SerieEnVivo datos, double segundos) {
        try {
            datos.setVentanaVisible(segundos);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    /**
     * La serie que debe dibujar la analógica con una ventana de esa duración,
     * según su regla: los puntos del historial desde el borde izquierdo, más
     * el anterior si ninguno cae justo en el borde.
     */
    private static List<SerieEnVivo.Punto> tramoEsperado(List<SerieEnVivo.Punto> historial, double ancho) {
        double limite = historial.get(historial.size() - 1).tiempo() - ancho;
        int primero = 0;
        while (historial.get(primero).tiempo() < limite) {
            primero++;
        }
        if (primero > 0 && historial.get(primero).tiempo() > limite) {
            primero--;
        }
        return historial.subList(primero, historial.size());
    }

    /**
     * Cuántas incoherencias hay entre la serie analógica dibujada y su
     * historial en la prueba de concurrencia: la serie debe ser un tramo
     * seguido del historial (mismos puntos, en orden, sin repetidos ni
     * huecos), debe empezar en el borde izquierdo o antes (salvo que empiece
     * en el primer punto del historial), y el eje X debe medir la ventana.
     * Se llama en el hilo de Swing.
     */
    private static int serieIncoherente(GraficaDePrueba g, double ancho) {
        List<SerieEnVivo.Punto> historial = g.grafica().getHistorial();
        List<SerieEnVivo.Punto> dibujados = puntos(g.serie());
        Range ejeX = g.chart().getXYPlot().getDomainAxis().getRange();
        int mal = Math.abs(ejeX.getLength() - ancho) < 1e-9 ? 0 : 1;
        if (dibujados.isEmpty()) {
            return mal;
        }
        // Posición del primer punto dibujado en el historial: el punto j tiene el tiempo j ms
        int inicio = (int) Math.round(dibujados.get(0).tiempo() * 1000);
        for (int k = 0; k < dibujados.size(); k++) {
            if (inicio + k >= historial.size() || !historial.get(inicio + k).equals(dibujados.get(k))) {
                mal++;
            }
        }
        if (dibujados.get(0).tiempo() > ejeX.getLowerBound() && inicio > 0) {
            mal++; // hueco a la izquierda
        }
        return mal;
    }

    /**
     * Cuántos valores dibujados en la gráfica digital real no son los de
     * patron(): en el tiempo de cada muestra de las primeras "cantidad" que
     * cae en la ventana visible, cada carril debe valer lo que traía esa
     * muestra. Se llama en el hilo de Swing.
     */
    private static int dibujadoDistintoPatron(DigitalDePrueba d, int cantidad) {
        double inicioEje = d.chart().getXYPlot().getDomainAxis().getLowerBound();
        int mal = 0;
        for (int j = 0; j < cantidad; j++) {
            if (tiempo(j) >= inicioEje) {
                for (int carril = 0; carril <= CARRIL_VALOR; carril++) {
                    if (valorEn(d.serie(carril), tiempo(j)) != esperado(patron(j), carril)) {
                        mal++;
                    }
                }
            }
        }
        return mal;
    }

    /**
     * Como dibujadoDistinto(), pero solo hasta la última lectura ya dibujada:
     * en plena prueba de concurrencia, el historial puede traer lecturas que
     * llegaron después y que el Timer todavía no dibuja. Se llama en el hilo
     * de Swing.
     */
    private static int digitalDistintoHastaLoDibujado(DigitalDePrueba d) {
        XYSeries valor = d.serie(CARRIL_VALOR);
        if (valor.isEmpty()) {
            return 0;
        }
        double ultimoDibujado = valor.getX(valor.getItemCount() - 1).doubleValue();
        List<SerieEnVivo.Punto> dibujables = new ArrayList<>();
        for (SerieEnVivo.Punto p : d.grafica().getHistorial()) {
            if (p.tiempo() <= ultimoDibujado) {
                dibujables.add(p);
            }
        }
        return dibujadoDistinto(d, dibujables);
    }

    /**
     * Tiempo promedio de dibujar la gráfica en una imagen de 1000 × 560 px,
     * como lo hace el ChartPanel (con la información de entidades), en el
     * hilo de Swing. Los 5 primeros dibujos calientan la máquina virtual y no
     * cuentan.
     */
    private static double tiempoDeDibujo(JFreeChart grafica) throws Exception {
        BufferedImage imagen = new BufferedImage(1000, 560, BufferedImage.TYPE_INT_RGB);
        double promedioMs = 0;
        for (int k = 0; k < 25; k++) {
            long t0 = System.nanoTime();
            enSwing(() -> {
                Graphics2D g2 = imagen.createGraphics();
                grafica.draw(g2, new Rectangle(0, 0, 1000, 560), null, new ChartRenderingInfo());
                g2.dispose();
                return null;
            });
            if (k >= 5) {
                promedioMs += (System.nanoTime() - t0) / 1e6 / 20;
            }
        }
        return promedioMs;
    }

    // ===================== Utilidades =====================

    /** Tiempo de la muestra número i, a 10 ms entre muestras. */
    private static double tiempo(int i) {
        return i / (double) MUESTRAS_POR_SEGUNDO;
    }

    /** Valor sintético de la muestra número i: senoidal entre 0 y 5 V. */
    private static double valor(int i) {
        return 2.5 + 2.5 * Math.sin(2 * Math.PI * 0.5 * tiempo(i));
    }

    /**
     * Agrega las muestras desde (incluida) hasta (excluida), con un ciclo
     * del Timer cada 5 muestras, como pasa en el programa a 10 ms.
     */
    private static void alimentar(SerieEnVivo datos, int desde, int hasta) {
        for (int i = desde; i < hasta; i++) {
            datos.agregar(tiempo(i), valor(i));
            if ((i + 1) % MUESTRAS_POR_CICLO == 0) {
                datos.procesarPendientes();
            }
        }
        datos.procesarPendientes();
    }

    /**
     * Cuántas de las primeras muestras caen en la ventana visible, es decir,
     * a no más de 30 s de la última. Se cuenta con la misma cuenta que usa
     * SerieEnVivo para que el redondeo de los decimales no cambie el número.
     */
    private static int esperadosEnVentana(int muestras) {
        double limite = tiempo(muestras - 1) - VENTANA_S;
        int cuenta = 0;
        for (int i = 0; i < muestras; i++) {
            if (tiempo(i) >= limite) {
                cuenta++;
            }
        }
        return cuenta;
    }

    private static boolean ordenada(XYSeries serie) {
        for (int i = 1; i < serie.getItemCount(); i++) {
            if (serie.getX(i).doubleValue() < serie.getX(i - 1).doubleValue()) {
                return false;
            }
        }
        return true;
    }

    private static String rango(SerieEnVivo datos) {
        return String.format("va de %.2f a %.2f s", datos.getInicioVentana(), datos.getFinVentana());
    }

    private static void titulo(String texto) {
        seccionActual = texto;
        System.out.println();
        System.out.println("--- " + texto + " ---");
    }

    private static void verificar(String criterio, boolean cumple, String detalle) {
        if (cumple) {
            aprobados++;
            System.out.println("[OK]    " + criterio);
        } else {
            fallidos++;
            System.out.println("[FALLA] " + criterio + "  ->  " + detalle);
        }
    }
}
