package laboratoriovirtual;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import laboratoriovirtual.control.ControlSeleccion;
import laboratoriovirtual.control.GraficaAnalogica;
import laboratoriovirtual.control.GraficaSenal;
import laboratoriovirtual.control.SerieEnVivo;
import laboratoriovirtual.datos.FuenteDeDatos;
import laboratoriovirtual.gui.PanelSenal;
import laboratoriovirtual.muestreo.Muestra;
import laboratoriovirtual.muestreo.Muestreador;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.data.Range;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
 * Verifica las tareas I7LV-17 e I7LV-16 sin abrir ninguna ventana. Se
 * ejecuta con Shift+F6 y muestra una línea [OK] o [FALLA] por criterio,
 * igual que VerificacionSprint1.
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
 */
public class VerificacionSprint2 {

    /** Ancho de la ventana visible, el mismo de las gráficas del programa. */
    private static final double VENTANA_S = GraficaSenal.VENTANA_VISIBLE_S;

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

    private static int aprobados = 0;
    private static int fallidos = 0;

    public static void main(String[] args) throws Exception {
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

        System.out.println();
        System.out.println("===================================");
        System.out.println("Resultado: " + aprobados + " aprobados, " + fallidos + " fallidos");
        System.out.println(fallidos == 0
                ? "I7LV-17 e I7LV-16 cumplen sus criterios."
                : "Hay criterios sin cumplir: revisa las líneas [FALLA].");

        // Las gráficas de I7LV-16 dejan corriendo su Timer de refresco, que
        // mantiene vivo el hilo de Swing: sin esto el programa no terminaría.
        System.exit(fallidos == 0 ? 0 : 1);
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
        productor.setUncaughtExceptionHandler((hilo, e) -> error.set(e));

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
     */
    private static void verificarCambiosConcurrentes() throws Exception {
        GraficaDePrueba g = crearGrafica(0);
        long siguienteMuestra = 0; // el tiempo sigue corriendo de una ronda a otra

        for (int ronda = 1; ronda <= RONDAS; ronda++) {
            AtomicBoolean enviando = new AtomicBoolean(true);
            AtomicLong enviadas = new AtomicLong();
            AtomicReference<Throwable> errorProductor = new AtomicReference<>();
            long desde = siguienteMuestra;
            Thread productor = new Thread(() -> {
                long i = desde;
                while (enviando.get()) {
                    g.grafica().muestraRecibida(muestra(tiempoRapido(i)));
                    i++;
                }
                enviadas.set(i - desde);
            }, "Muestreador de prueba");
            productor.setUncaughtExceptionHandler((hilo, e) -> errorProductor.set(e));

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
                            + "muestras sin parar (%,d muestras en %.1f s; el Timer dibujó entre "
                            + "cambios %d veces): ningún punto de otro canal, y al final solo "
                            + "valores del último canal elegido (A%d)",
                            ronda, RONDAS, CAMBIOS_POR_RONDA, enviadas.get(), segundos,
                            cambiosConDibujo, ultimo),
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
            super(MUESTREADOR, panel, canal);
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
    private static boolean rechaza(GraficaSenal grafica, int canal) {
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
