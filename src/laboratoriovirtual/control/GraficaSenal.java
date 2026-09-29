package laboratoriovirtual.control;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.Timer;
import laboratoriovirtual.almacenamiento.HistorialSenal;
import laboratoriovirtual.gui.PanelSenal;
import laboratoriovirtual.gui.Tema;
import laboratoriovirtual.muestreo.Muestra;
import laboratoriovirtual.muestreo.Muestreador;
import laboratoriovirtual.muestreo.OyenteMuestras;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.data.Range;
import org.jfree.data.xy.XYSeriesCollection;

/**
 * Base de las gráficas en vivo de UNA señal contra el tiempo, dentro de una
 * pestaña PanelSenal. La usa GraficaAnalogica (I7LV-17).
 *
 * Aquí queda todo lo de una gráfica de un solo canal: el reparto entre
 * hilos, la ventana deslizante (30 s al comenzar, ajustable desde I7LV-18),
 * el historial y el cambio de canal (que vacía la gráfica). La subclase
 * entrega el valor que toma de cada Muestra, el eje Y y la forma de la
 * línea, y puede ajustar el eje Y en cada ciclo (ajustarEjeValor()). El
 * estilo de Tema y el ChartPanel sin zoom ni menú están en EstiloGrafica.
 *
 * La gráfica digital (I7LV-20) no hereda de esta clase: registra los cuatro
 * canales a la vez y elegir un canal solo cambia cuál se resalta, sin vaciar
 * nada. Ver GraficaDigital.
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
 * El mismo candado sirve para guardar la señal (Sprint 3):
 * historialParaGuardar() toma el nombre del canal y la copia de su historial
 * dentro de otro synchronized (datos), así que los dos son del mismo canal.
 *
 * Eje X después de un cambio de canal (I7LV-18): mientras no llegue una
 * muestra del canal nuevo, va de 0 a la duración de la ventana; con la
 * primera, arranca en su tiempo. Con el muestreo corriendo, pasar a 0 y un
 * instante después al tiempo de la primera muestra se vería como un
 * parpadeo. Por eso, si la próxima muestra debe llegar en menos de
 * ESPERA_SIN_PARPADEO_MS, el eje se queda como estaba hasta que llegue (ver
 * cambiarCanal()).
 */
public abstract class GraficaSenal implements OyenteMuestras, CanalSeleccionable, VentanaAjustable {

    /**
     * Duración de la ventana visible al comenzar, en segundos. También la
     * usa GraficaDigital. Se cambia con setVentanaVisible() (I7LV-18).
     */
    public static final double VENTANA_POR_DEFECTO_S = 30.0;

    /**
     * Cada cuánto se pasan los puntos a la gráfica y se redibuja, en
     * milisegundos. También lo usa GraficaDigital.
     */
    static final int PERIODO_REFRESCO_MS = 50;

    /**
     * Después de un cambio de canal con el muestreo corriendo: si la próxima
     * muestra debe llegar dentro de este tiempo, en milisegundos, el eje X no
     * pasa a 0 mientras tanto (ver cambiarCanal()). Con muestreos más lentos,
     * el eje va de 0 a la duración de la ventana al menos este tiempo antes
     * de la primera muestra: medio segundo se ve como un cambio y no como un
     * parpadeo.
     */
    static final long ESPERA_SIN_PARPADEO_MS = 500;

    // Nombres de los canales ("A0", "D2"...), copiados del selector de la
    // pestaña: así la gráfica y el selector siempre dicen lo mismo. Su
    // cantidad es el rango de canales válidos.
    private final List<String> nombresCanales;

    // Canal que se grafica. Lo cambia el hilo de Swing y lo lee el del
    // Muestreador, así que, pasado el constructor (que lo fija antes de
    // registrarse como oyente), solo se usa dentro de synchronized (datos).
    private int canal;

    private final SerieEnVivo datos = new SerieEnVivo(VENTANA_POR_DEFECTO_S);
    private final NumberAxis ejeTiempo;
    private final JFreeChart grafica;

    // Para saber, al cambiar de canal, si el muestreo corre y cuándo llega
    // la próxima muestra (solo se consulta; nunca se inicia ni se detiene)
    private final Muestreador muestreador;

    // Cuándo llegó la última muestra (System.nanoTime()). La escribe el hilo
    // del Muestreador y la lee el de Swing.
    private volatile long llegadaUltimaMuestra = 0;

    // Si, después de un cambio de canal, el eje X espera la primera muestra
    // del canal nuevo sin pasar a 0, y hasta cuándo. Solo hilo de Swing.
    private boolean ejeEnEspera = false;
    private long finEsperaEje = 0;

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
        this.muestreador = muestreador;

        ejeTiempo = new NumberAxis("Tiempo (s)");
        // Rango fijo que refrescar() mueve a mano. Al comenzar va de 0 a 30 s.
        ejeTiempo.setRange(0.0, VENTANA_POR_DEFECTO_S);

        grafica = crearGrafica(titulo(canal), ejeValor, linea);
        panel.getPanelGrafica().add(EstiloGrafica.crearPanelGrafica(grafica),
                BorderLayout.CENTER);

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
     * Ajusta el eje Y a lo que se ve, si la subclase lo necesita (la escala
     * automática de GraficaAnalogica, I7LV-18). Se llama en el hilo de Swing
     * después de actualizar la serie y el eje X: en cada ciclo del Timer con
     * puntos nuevos y al cambiar la ventana. Se llama con los avisos de la
     * gráfica apagados, así que un cambio del eje Y no agrega redibujos: todo
     * llega al ChartPanel como uno solo. Por defecto no hace nada.
     */
    protected void ajustarEjeValor() {
        // El eje Y de la subclase queda como lo dejó ella
    }

    /**
     * Valores mínimo y máximo que se ven de la señal, para ajustarEjeValor().
     * Ver SerieEnVivo.getRangoVisible(). Se llama en el hilo de Swing.
     *
     * @return el rango de los valores visibles, o null si no se ve ningún punto
     */
    protected Range rangoVisible() {
        return datos.getRangoVisible();
    }

    /**
     * Copia inmutable de todos los puntos (tiempo, valor) recibidos desde el
     * último Iniciar o el último cambio de canal, aunque ya no se dibujen.
     * Seguro entre hilos: ver {@link SerieEnVivo#getHistorial()}. Para
     * guardar la señal en un archivo, la subclase ofrece
     * getHistorialParaGuardar(), que entrega además el nombre del canal,
     * tomado junto con la copia (ver historialParaGuardar()).
     */
    public List<SerieEnVivo.Punto> getHistorial() {
        return datos.getHistorial();
    }

    /**
     * El nombre del canal que se grafica y la copia de su historial, tomados
     * juntos, listos para EscritorArchivo. Es la base de
     * getHistorialParaGuardar() de la subclase (ver SenalGuardable, donde
     * está la regla de hilos).
     *
     * El nombre y la copia se toman dentro de un solo synchronized (datos),
     * el candado de muestraRecibida() y cambiarCanal(): un cambio de canal
     * queda del todo antes (y la copia ya es del canal nuevo, el que dice el
     * nombre) o del todo después. Sin el candado se podría leer el nombre del
     * canal anterior y copiar el historial ya vaciado o con puntos del
     * nuevo. Armar los arreglos, que recorre todos los puntos, se hace
     * después, sin frenar al Muestreador.
     *
     * @param tipo cómo se escribirán los valores (lo sabe la subclase)
     * @return el canal y su historial
     */
    protected HistorialSenal historialParaGuardar(HistorialSenal.Tipo tipo) {
        String nombre;
        List<SerieEnVivo.Punto> puntos;
        synchronized (datos) {
            nombre = nombresCanales.get(canal);
            puntos = datos.getHistorial();
        }
        return aHistorialSenal(nombre, tipo, puntos);
    }

    /**
     * Convierte los puntos de un historial al tipo que recibe EscritorArchivo
     * (paquete almacenamiento, que no conoce SerieEnVivo.Punto). También la
     * usa GraficaDigital.
     */
    static HistorialSenal aHistorialSenal(String canal, HistorialSenal.Tipo tipo,
                                          List<SerieEnVivo.Punto> puntos) {
        double[] tiempos = new double[puntos.size()];
        double[] valores = new double[puntos.size()];
        for (int i = 0; i < tiempos.length; i++) {
            SerieEnVivo.Punto punto = puntos.get(i);
            tiempos[i] = punto.tiempo();
            valores[i] = punto.valor();
        }
        return new HistorialSenal(canal, tipo, tiempos, valores);
    }

    /** Canal que se grafica: su posición en el selector de la pestaña. */
    @Override
    public int getCanal() {
        synchronized (datos) {
            return canal;
        }
    }

    /** Duración de la ventana visible, en segundos. Se llama en el hilo de Swing. */
    @Override
    public double getVentanaVisible() {
        return datos.getVentanaVisible();
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
        llegadaUltimaMuestra = System.nanoTime();
    }

    // errorEnFuente() no se sobrescribe: si la fuente falla, la gráfica
    // conserva lo que ya dibujó. ControlMuestreo es quien avisa al usuario.

    // ===================== Hilo de Swing =====================

    /**
     * Pasa a graficar otro canal (I7LV-16). Vacía la serie dibujada, la cola
     * de puntos pendientes y el historial, y cambia el título ("Canal A3").
     * Desde ese momento solo se registra el canal nuevo.
     *
     * Eje X (I7LV-18): mientras no llegue una muestra del canal nuevo, va de
     * 0 a la duración de la ventana; con la primera, arranca en su tiempo y
     * con la misma ventana (el tiempo no vuelve a cero, porque lo lleva el
     * Muestreador). Excepción, para que no parpadee: si el muestreo corre y
     * la próxima muestra debe llegar dentro de ESPERA_SIN_PARPADEO_MS, el eje
     * se queda como estaba hasta que llegue, y pasa directamente al tiempo
     * de esa muestra. Si no llega a tiempo (por ejemplo, porque se detuvo el
     * muestreo), refrescar() lo pasa a 0.
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
    @Override
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

        // Paso 2, ya sin el candado: la serie, el título y el eje X solo se
        // tocan en este hilo, y así el Muestreador no espera mientras
        // JFreeChart avisa los cambios. Con los avisos de la gráfica apagados
        // mientras tanto, todo llega al ChartPanel como un solo redibujo.
        grafica.setNotify(false);
        try {
            datos.vaciarSerie();
            grafica.getTitle().setText(titulo(nuevoCanal));
            long ahora = System.nanoTime();
            long falta = faltaParaLaProximaMuestra(ahora);
            ejeEnEspera = falta >= 0 && falta <= ESPERA_SIN_PARPADEO_MS * 1_000_000L;
            if (ejeEnEspera) {
                // Se da un par de ciclos del Timer de margen, por si la
                // muestra se atrasa un poco
                finEsperaEje = ahora + falta + 2L * PERIODO_REFRESCO_MS * 1_000_000L;
            } else {
                ejeTiempo.setRange(datos.getInicioVentana(), datos.getFinVentana()); // de 0 a la ventana
            }
        } finally {
            grafica.setNotify(true);
        }
    }

    /**
     * Cambia la duración de la ventana visible (I7LV-18): la serie se
     * reconstruye desde el historial (ver SerieEnVivo.setVentanaVisible()),
     * y el eje X y, si hace falta, el eje Y se ajustan, todo en un solo
     * redibujo. Pedir la duración que ya tiene no hace nada. Se llama desde
     * el hilo de Swing.
     */
    @Override
    public void setVentanaVisible(double segundos) {
        if (segundos == datos.getVentanaVisible()) {
            return;
        }
        grafica.setNotify(false);
        try {
            datos.setVentanaVisible(segundos); // valida antes de tocar nada
            ejeEnEspera = false;
            ejeTiempo.setRange(datos.getInicioVentana(), datos.getFinVentana());
            ajustarEjeValor();
        } finally {
            grafica.setNotify(true);
        }
    }

    /** Lo llama el Timer cada 50 ms, en el hilo de Swing. */
    private void refrescar() {
        boolean hayPuntos = datos.hayPendientes();
        // Después de un cambio de canal, si la primera muestra no llegó a
        // tiempo o se detuvo el muestreo, el eje X pasa a 0 (ver cambiarCanal())
        boolean venceEspera = ejeEnEspera
                && (System.nanoTime() - finEsperaEje >= 0 || !muestreador.estaCorriendo());
        if (!hayPuntos && !venceEspera) {
            return; // No llegó nada nuevo, por ejemplo con el muestreo detenido
        }
        // La serie avisa una sola vez, pero los ejes también cambian y
        // avisarían por su lado. Con los avisos de la gráfica apagados
        // mientras tanto, todo llega al ChartPanel como uno solo: un redibujo.
        grafica.setNotify(false);
        try {
            datos.procesarPendientes();
            ejeEnEspera = false;
            ejeTiempo.setRange(datos.getInicioVentana(), datos.getFinVentana());
            ajustarEjeValor();
        } finally {
            grafica.setNotify(true);
        }
    }

    /**
     * Cuánto falta, en nanosegundos, para que llegue la próxima muestra, o
     * -1 si el muestreo no está corriendo. El Muestreador toma una muestra
     * cada periodo, a ritmo fijo, así que llega un periodo después de la
     * anterior. Si ya debería haber llegado, devuelve 0.
     */
    private long faltaParaLaProximaMuestra(long ahora) {
        if (!muestreador.estaCorriendo()) {
            return -1;
        }
        long periodo = muestreador.getPeriodoMs() * 1_000_000L;
        return Math.max(0, periodo - (ahora - llegadaUltimaMuestra));
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
        EstiloGrafica.aplicarAEje(ejeTiempo);
        EstiloGrafica.aplicarAEje(ejeValor);

        // La señal (serie 0): línea roja de 2 px
        linea.setSeriesPaint(0, Tema.ROJO);
        linea.setSeriesStroke(0, new BasicStroke(2.0f));

        XYSeriesCollection dataset = new XYSeriesCollection(datos.getSerie());
        XYPlot areaDibujo = new XYPlot(dataset, ejeTiempo, ejeValor, linea);
        // Fondo blanco, borde gris y cuadrícula gris en las dos direcciones
        EstiloGrafica.aplicarAAreaDibujo(areaDibujo);

        // Título pequeño con el nombre del canal y sin leyenda (una sola señal)
        return EstiloGrafica.crearGrafica(titulo, areaDibujo);
    }
}
