package laboratoriovirtual;

import java.awt.Component;
import java.awt.Container;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.IntToDoubleFunction;
import java.util.function.IntUnaryOperator;
import java.util.function.LongConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileSystemView;
import laboratoriovirtual.almacenamiento.EscritorArchivo;
import laboratoriovirtual.almacenamiento.HistorialSenal;
import laboratoriovirtual.control.CanalSeleccionable;
import laboratoriovirtual.control.CarpetaRecordada;
import laboratoriovirtual.control.ControlGuardado;
import laboratoriovirtual.control.ControlSeleccion;
import laboratoriovirtual.control.DialogosGuardado;
import laboratoriovirtual.control.DialogosGuardado.Aviso;
import laboratoriovirtual.control.DialogosGuardadoSwing;
import laboratoriovirtual.control.GraficaAnalogica;
import laboratoriovirtual.control.GraficaDigital;
import laboratoriovirtual.control.SenalGuardable;
import laboratoriovirtual.control.SerieEnVivo;
import laboratoriovirtual.datos.FuenteDeDatos;
import laboratoriovirtual.gui.PanelSenal;
import laboratoriovirtual.muestreo.Muestra;
import laboratoriovirtual.muestreo.Muestreador;

/**
 * Verifica las tareas del Sprint 3 sin abrir ninguna ventana. Se ejecuta con
 * Shift+F6 y muestra una línea [OK] o [FALLA] por criterio, igual que
 * VerificacionSprint1 y VerificacionSprint2. Una excepción no capturada, en
 * cualquier hilo, se imprime completa y cuenta como FALLA; el programa nunca
 * se queda colgado por ella (ver main()).
 *
 * Escritura del archivo de la señal seleccionada (R3 y R6; la clave de la
 * tarea no venía en su enunciado): escribe con EscritorArchivo solo dentro
 * de una carpeta temporal del sistema, que se borra al terminar, y lee los
 * archivos de vuelta para revisar el formato fila por fila (con expresiones
 * regulares), el redondeo, los saltos de línea, la codificación, el archivo
 * vacío, el tiempo con una hora de datos, las fallas (sin temporales y con el
 * archivo anterior intacto), el reemplazo y el nombre sugerido. Todo con el
 * formato regional de Colombia (es-CO), que usa coma decimal, como
 * predeterminado. Con las gráficas reales (GraficaAnalogica y
 * GraficaDigital, dentro de un PanelSenal que nunca se muestra, como en
 * VerificacionSprint2) revisa que getHistorialParaGuardar() entregue juntos
 * el nombre del canal y su historial, también justo después de un cambio y
 * mientras otro hilo envía muestras y el hilo de Swing cambia de canal.
 *
 * I7LV-25 (guardar la señal con el botón de la pestaña): usa el
 * ControlGuardado real con las gráficas reales y el botón "Guardar esta
 * señal…" de un PanelSenal que nunca se muestra, pulsado con doClick() como
 * lo haría el usuario. Las ventanas (elegir el archivo, la pregunta de
 * reemplazo y los avisos) las responde DialogosDePrueba, que no abre nada:
 * contesta lo que la prueba le programa y anota lo que se le pidió. Revisa el
 * aviso sin datos, el título y el nombre propuestos, la extensión, cancelar,
 * el reemplazo, que se guarde lo que había al pulsar aunque lleguen muestras,
 * el botón mientras se escribe, las fallas y la carpeta recordada. Del
 * DialogosGuardadoSwing real revisa, sin mostrarlo, cómo arma el selector de
 * archivos.
 *
 * Al final escribe un archivo de ejemplo con 20 filas analógicas en la
 * carpeta temporal del sistema (esa no se borra) y muestra su ruta, para
 * abrirlo con el Bloc de notas o con Excel.
 */
public class VerificacionSprint3 {

    /** Formato regional de Colombia: coma decimal. Es el predeterminado durante la verificación. */
    private static final Locale ES_CO = Locale.forLanguageTag("es-CO");

    /** Fila analógica: tiempo y voltaje con 3 decimales y punto, separados por una tabulación. */
    private static final Pattern FILA_ANALOGICA = Pattern.compile("\\d+\\.\\d{3}\\t-?\\d+\\.\\d{3}");

    /** Fila digital: tiempo con 3 decimales y punto, tabulación, 0 o 1. */
    private static final Pattern FILA_DIGITAL = Pattern.compile("\\d+\\.\\d{3}\\t[01]");

    /** Nombre sugerido con un canal del programa: canal_aaaa-mm-dd_hh-mm.txt. */
    private static final Pattern NOMBRE_SUGERIDO =
            Pattern.compile("[A-Za-z0-9]+_\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}\\.txt");

    /** Caracteres que Windows no acepta en un nombre de archivo, además de los de control. */
    private static final String INVALIDOS_WINDOWS = "<>:\"/\\|?*";

    /** Bytes que puede tener un archivo: dígitos, punto, signo menos, tabulación y salto de línea. */
    private static final String BYTES_PERMITIDOS = "0123456789.-\t\r\n";

    /** Una hora de muestreo a 10 ms. */
    private static final int FILAS_UNA_HORA = 360_000;

    /** Lo más que puede tardar en escribirse una hora de muestreo, en segundos. */
    private static final double LIMITE_UNA_HORA_S = 2.0;

    /** Fecha y hora de los nombres sugeridos de las pruebas: 4 de octubre de 2026, 15:30. */
    private static final LocalDateTime FECHA = LocalDateTime.of(2026, 10, 4, 15, 30);

    /** Canales de la pestaña "Señal analógica": A0 a A7. */
    private static final int CANALES = FuenteDeDatos.NUM_ANALOGICAS;

    /** Señales de la pestaña "Señal digital": D0 a D3. */
    private static final int CANALES_DIGITALES = FuenteDeDatos.NUM_DIGITALES_ENTRADA;

    /** Lectura de las entradas analógicas en las pruebas con gráficas: el canal i vale i × 0,5 V. */
    private static final double[] ANALOGICAS = new double[CANALES];
    private static final boolean[] SIN_DIGITALES = new boolean[CANALES_DIGITALES];

    static {
        for (int canal = 0; canal < CANALES; canal++) {
            ANALOGICAS[canal] = valorCanal(canal);
        }
    }

    /**
     * Muestreador de las gráficas de prueba. Nunca se inicia: solo está para
     * que las gráficas se registren como oyentes. Las muestras las envía la
     * prueba.
     */
    private static final Muestreador MUESTREADOR = new Muestreador(new FuenteSinUso(), 500);

    /** Cambios de canal en las pruebas de concurrencia. */
    private static final int CAMBIOS = 1000;

    /**
     * Muestras entre dos "Iniciar" en las pruebas de concurrencia (20 s a
     * 1 ms): un tiempo menor vacía el historial, así nunca pasa de 20 000
     * puntos y cada copia es rápida.
     */
    private static final int MUESTRAS_POR_CORRIDA = 20_000;

    /** Si el programa corre en Windows: solo ahí se puede simular un archivo abierto en otro programa. */
    private static final boolean EN_WINDOWS = System.getProperty("os.name", "").startsWith("Windows");

    /**
     * Excepciones no capturadas en otros hilos. Cada una se imprime completa
     * en cuanto ocurre y queda aquí hasta que la revisión al final de su
     * sección la cuenta como FALLA (revisarErroresEnOtrosHilos()).
     */
    private static final Queue<Throwable> ERRORES_EN_OTROS_HILOS = new ConcurrentLinkedQueue<>();

    /** Sección que se está verificando, para decir dónde ocurrió una excepción. */
    private static volatile String seccionActual = "(antes de la primera sección)";

    /** Carpeta temporal de las pruebas; se borra al terminar. */
    private static Path carpetaPruebas;

    /** El archivo de ejemplo que queda para el usuario (null si no se llegó a escribir). */
    private static Path archivoEjemplo;

    private static int aprobados = 0;
    private static int fallidos = 0;

    /**
     * Ejecuta todas las secciones y termina el programa con el resumen. Las
     * excepciones no capturadas se tratan como en VerificacionSprint2: las de
     * otros hilos se imprimen y cuentan como FALLA en la revisión de su
     * sección; una en este hilo detiene la verificación con un mensaje claro.
     * Siempre termina con System.exit: el Timer de las gráficas de prueba
     * mantiene vivo el hilo de Swing.
     */
    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler(VerificacionSprint3::excepcionEnOtroHilo);
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
                ? "La escritura del archivo de la señal seleccionada e I7LV-25 cumplen sus criterios."
                : "Hay criterios sin cumplir: revisa las líneas [FALLA].");
        if (archivoEjemplo != null) {
            System.out.println();
            System.out.println("Archivo de ejemplo (20 filas analógicas del canal A3), para abrir con el "
                    + "Bloc de notas o con Excel:");
            System.out.println(archivoEjemplo);
        }
        System.exit(fallidos == 0 ? 0 : 1);
    }

    private static void verificarSecciones() throws Exception {
        Locale original = Locale.getDefault();
        carpetaPruebas = Files.createTempDirectory("VerificacionSprint3_");
        try {
            // Formato regional de Colombia, que usa coma decimal: el archivo
            // debe llevar punto igual
            Locale.setDefault(ES_CO);

            titulo("Escritura del archivo de la señal seleccionada, R3 y R6 (clave por confirmar)");

            // Primero, con EscritorArchivo todavía sin usar, como en el
            // primer guardado del programa
            verificarRendimiento();
            verificarFormato();
            verificarPuntoDecimal();
            verificarRedondeo();
            verificarLecturaDeVuelta();
            verificarHistorialVacio();
            verificarTemporal();
            verificarReemplazo();
            verificarReemplazoConLecturas();
            verificarFallas();
            verificarNombreSugerido();
            verificarHistorialSenal();
            verificarGraficaAnalogica();
            verificarGraficaDigital();
            verificarConcurrenciaAnalogica();
            verificarConcurrenciaDigital();
            verificarArchivoDesdeGraficas();

            revisarErroresEnOtrosHilos("la escritura del archivo");

            titulo("I7LV-25  Guardar la señal con el botón de la pestaña (control y gráficas reales, "
                    + "diálogos de prueba, sin ventanas)");

            verificarGuardadoSinDatos();
            verificarTituloYNombre();
            verificarCancelar();
            verificarGuardadoCompleto();
            verificarExtension();
            verificarReemplazoConPregunta();
            verificarMuestrasDuranteElGuardado();
            verificarFallaAlGuardar();
            verificarCarpetaCompartida();
            verificarSelectorDeArchivos();

            revisarErroresEnOtrosHilos("I7LV-25");
        } finally {
            Locale.setDefault(original);
            borrarCarpetaPruebas();
        }

        titulo("Archivo de ejemplo");

        escribirEjemplo();
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
     * revisión anterior (ya se imprimieron completas cuando ocurrieron).
     */
    private static void revisarErroresEnOtrosHilos(String seccion) {
        List<Throwable> errores = new ArrayList<>();
        Throwable error;
        while ((error = ERRORES_EN_OTROS_HILOS.poll()) != null) {
            errores.add(error);
        }
        verificar("Ningún error sin atrapar en el Timer (hilo de Swing) ni en otro hilo durante " + seccion,
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

    // ===================== Rendimiento =====================

    private static void verificarRendimiento() throws IOException {
        Path carpeta = carpetaNueva("rendimiento");

        HistorialSenal analogica = analogicaAlAzar("A0", FILAS_UNA_HORA, 11);
        Path archivoA = carpeta.resolve("A0.txt");
        double segundosA = medirEscritura(analogica, archivoA);
        int filasA = contarFinesDeFila(Files.readAllBytes(archivoA));
        verificar(String.format("Una hora a 10 ms (360 000 filas analógicas) se escribe en menos de %.0f s: "
                                + "%.2f s la primera vez que el programa usa EscritorArchivo (%,d bytes)",
                        LIMITE_UNA_HORA_S, segundosA, Files.size(archivoA)),
                segundosA < LIMITE_UNA_HORA_S && filasA == FILAS_UNA_HORA,
                String.format("%.2f s y %,d filas", segundosA, filasA));

        HistorialSenal digital = digitalAlAzar("D0", FILAS_UNA_HORA, 12);
        Path archivoD = carpeta.resolve("D0.txt");
        double segundosD = medirEscritura(digital, archivoD);
        int filasD = contarFinesDeFila(Files.readAllBytes(archivoD));
        verificar(String.format("... y 360 000 filas digitales, en %.2f s (%,d bytes)",
                        segundosD, Files.size(archivoD)),
                segundosD < LIMITE_UNA_HORA_S && filasD == FILAS_UNA_HORA,
                String.format("%.2f s y %,d filas", segundosD, filasD));
    }

    // ===================== Formato =====================

    private static void verificarFormato() throws IOException {
        Path carpeta = carpetaNueva("formato");
        HistorialSenal analogica = analogicaAlAzar("A3", 2000, 1);
        HistorialSenal digital = digitalAlAzar("D2", 2000, 2);
        Path archivoA = carpeta.resolve("A3.txt");
        Path archivoD = carpeta.resolve("D2.txt");
        EscritorArchivo.escribir(analogica, archivoA);
        EscritorArchivo.escribir(digital, archivoD);
        Leido a = leer(archivoA);
        Leido d = leer(archivoD);

        int malasA = noCumplen(a.filas(), FILA_ANALOGICA);
        verificar("Analógica: las 2000 filas son tiempo, tabulación y voltaje, los dos con 3 decimales "
                        + "y punto (cumplen " + FILA_ANALOGICA + ")",
                a.filas().size() == 2000 && malasA == 0,
                a.filas().size() + " filas, " + malasA + " no cumplen; la primera: "
                        + primeraQueNoCumple(a.filas(), FILA_ANALOGICA));
        verificar("... sin fila de encabezado: la primera fila ya es la primera muestra (\""
                        + visible(primera(a)) + "\")",
                primera(a).equals(filaEsperada(analogica, 0)),
                "se esperaba \"" + visible(filaEsperada(analogica, 0)) + "\"");

        int malasD = noCumplen(d.filas(), FILA_DIGITAL);
        verificar("Digital: las 2000 filas son tiempo con 3 decimales, tabulación y 0 o 1 sin decimales "
                        + "(cumplen " + FILA_DIGITAL + ")",
                d.filas().size() == 2000 && malasD == 0,
                d.filas().size() + " filas, " + malasD + " no cumplen; la primera: "
                        + primeraQueNoCumple(d.filas(), FILA_DIGITAL));
        verificar("... sin fila de encabezado: la primera fila ya es la primera muestra (\""
                        + visible(primera(d)) + "\")",
                primera(d).equals(filaEsperada(digital, 0)),
                "se esperaba \"" + visible(filaEsperada(digital, 0)) + "\"");

        verificar("Saltos de línea de Windows: cada fila termina en \\r\\n, también la última, y no hay "
                        + "\\r ni \\n sueltos (analógica y digital)",
                saltosDeWindows(a) && saltosDeWindows(d),
                "analógica " + (saltosDeWindows(a) ? "bien" : "mal") + ", digital "
                        + (saltosDeWindows(d) ? "bien" : "mal") + "; texto después del último \\r\\n: \""
                        + visible(a.resto()) + "\" y \"" + visible(d.resto()) + "\"");
        verificar("UTF-8 sin BOM: solo bytes de dígitos, punto, signo menos, tabulación y salto de línea "
                        + "(ASCII, que es UTF-8 válido)",
                soloBytesPermitidos(a.bytes()) && soloBytesPermitidos(d.bytes()),
                "primeros bytes: " + primerosBytes(a.bytes()) + " y " + primerosBytes(d.bytes()));
    }

    private static void verificarPuntoDecimal() throws IOException {
        Path archivo = carpetaNueva("punto-decimal").resolve("A1.txt");
        EscritorArchivo.escribir(new HistorialSenal("A1", HistorialSenal.Tipo.ANALOGICA,
                new double[]{0.5, 1.0}, new double[]{3.72, 0.1}), archivo);
        Leido leido = leer(archivo);
        String conFormatoRegional = String.format("%.3f", 3.72);
        verificar("Con el formato regional de Colombia (es-CO) como predeterminado, que usa coma "
                        + "(String.format(\"%.3f\", 3.72) da \"" + conFormatoRegional + "\"), el archivo "
                        + "usa punto: \"0.500\\t3.720\" y \"1.000\\t0.100\", sin ninguna coma",
                Locale.getDefault().equals(ES_CO) && conFormatoRegional.equals("3,720")
                        && leido.filas().equals(List.of("0.500\t3.720", "1.000\t0.100"))
                        && !leido.texto().contains(","),
                "Locale " + Locale.getDefault().toLanguageTag() + "; filas: " + visible(leido.filas().toString()));
    }

    private static void verificarRedondeo() throws IOException {
        // Cada número va como tiempo y como valor de la misma fila
        double[] numeros = {0.0, 3.72, 3.7204, 3.7206, 1.0005, 0.1235, 2.0015, 0.0005,
                            4.9995, 5.0, -0.0004, -1.2345, 3599.9995, 12345.6784};
        String[] esperados = {"0.000", "3.720", "3.720", "3.721", "1.001", "0.124", "2.002", "0.001",
                              "5.000", "5.000", "0.000", "-1.235", "3600.000", "12345.678"};
        Path archivo = carpetaNueva("redondeo").resolve("A0.txt");
        EscritorArchivo.escribir(new HistorialSenal("A0", HistorialSenal.Tipo.ANALOGICA,
                numeros, numeros), archivo);
        List<String> filas = leer(archivo).filas();
        List<String> distintas = new ArrayList<>();
        for (int i = 0; i < numeros.length; i++) {
            String esperada = esperados[i] + "\t" + esperados[i];
            if (i >= filas.size() || !filas.get(i).equals(esperada)) {
                distintas.add(numeros[i] + " dio \"" + (i < filas.size() ? visible(filas.get(i)) : "(sin fila)")
                        + "\" y no \"" + visible(esperada) + "\"");
            }
        }
        verificar("Redondeo a 3 decimales, igual en el tiempo y en el valor: al más cercano "
                        + "(3.7206 → 3.721), los empates hacia arriba (1.0005 → 1.001, 4.9995 → 5.000) "
                        + "y nunca \"-0.000\" (-0.0004 → 0.000): " + numeros.length + " casos",
                filas.size() == numeros.length && distintas.isEmpty(),
                filas.size() + " filas; " + distintas);
    }

    private static void verificarLecturaDeVuelta() throws IOException {
        Path carpeta = carpetaNueva("lectura");
        Random azar = new Random(3);
        int cantidad = 5000;
        double[] tiempos = new double[cantidad];
        double[] voltajes = new double[cantidad];
        double[] bits = new double[cantidad];
        for (int i = 0; i < cantidad; i++) {
            tiempos[i] = azar.nextDouble() * 3600.0; // hasta una hora
            voltajes[i] = azar.nextDouble() * 5.0;
            bits[i] = azar.nextInt(2);
        }
        HistorialSenal analogica = new HistorialSenal("A4", HistorialSenal.Tipo.ANALOGICA, tiempos, voltajes);
        HistorialSenal digital = new HistorialSenal("D3", HistorialSenal.Tipo.DIGITAL, tiempos, bits);
        Path archivoA = carpeta.resolve("A4.txt");
        Path archivoD = carpeta.resolve("D3.txt");
        EscritorArchivo.escribir(analogica, archivoA);
        EscritorArchivo.escribir(digital, archivoD);

        Leido a = leer(archivoA);
        int distintasA = distintasAlLeer(a, cantidad, i -> tiempos[i], i -> voltajes[i], false);
        verificar("Leer de vuelta el archivo analógico da la misma cantidad de filas (5000) y los mismos "
                        + "tiempos (hasta 1 h) y voltajes, redondeados a 3 decimales (comparados con "
                        + "String.format(Locale.ROOT, \"%.3f\"))",
                a.filas().size() == cantidad && distintasA == 0,
                a.filas().size() + " filas; " + distintasA + " distintas");

        Leido d = leer(archivoD);
        int distintasD = distintasAlLeer(d, cantidad, i -> tiempos[i], i -> bits[i], true);
        verificar("Leer de vuelta el archivo digital da la misma cantidad de filas (5000), los mismos "
                        + "tiempos redondeados y exactamente los mismos 0 y 1",
                d.filas().size() == cantidad && distintasD == 0,
                d.filas().size() + " filas; " + distintasD + " distintas");
    }

    private static void verificarHistorialVacio() throws IOException {
        Path carpeta = carpetaNueva("vacio");
        Path archivoA = carpeta.resolve("A0.txt");
        Path archivoD = carpeta.resolve("D0.txt");
        EscritorArchivo.escribir(vacio("A0", HistorialSenal.Tipo.ANALOGICA), archivoA);
        EscritorArchivo.escribir(vacio("D0", HistorialSenal.Tipo.DIGITAL), archivoD);
        verificar("Historial vacío: se crea un archivo vacío (0 bytes), analógico y digital",
                Files.isRegularFile(archivoA) && Files.size(archivoA) == 0
                        && Files.isRegularFile(archivoD) && Files.size(archivoD) == 0,
                "existen: " + Files.exists(archivoA) + " y " + Files.exists(archivoD));

        Path conDatos = carpeta.resolve("A1.txt");
        EscritorArchivo.escribir(analogicaAlAzar("A1", 100, 4), conDatos);
        long antes = Files.size(conDatos);
        EscritorArchivo.escribir(vacio("A1", HistorialSenal.Tipo.ANALOGICA), conDatos);
        verificar("... y si ya había un archivo con datos (" + antes + " bytes), lo reemplaza por uno vacío",
                antes > 0 && Files.size(conDatos) == 0
                        && contenido(carpeta).equals(Set.of("A0.txt", "D0.txt", "A1.txt")),
                Files.size(conDatos) + " bytes; en la carpeta: " + contenido(carpeta));
    }

    // ===================== Temporal, reemplazo y fallas =====================

    /**
     * Mientras se escribe una hora de datos sobre un archivo que ya existe,
     * otro hilo mira la carpeta sin parar: debe ver el temporal en la misma
     * carpeta y el archivo final siempre completo (el viejo o el nuevo).
     * Solo lee nombres y tamaños, sin abrir los archivos: en Windows, tener
     * abierto el destino impediría reemplazarlo. Leer el tamaño justo en el
     * instante del reemplazo también puede hacer que Windows lo niegue; el
     * escritor reintenta (ver verificarReemplazoConLecturas()).
     */
    private static void verificarTemporal() throws Exception {
        Path carpeta = carpetaNueva("temporal");
        Path archivo = carpeta.resolve("A5.txt");
        EscritorArchivo.escribir(analogicaAlAzar("A5", 1000, 5), archivo);
        long tamanoViejo = Files.size(archivo);

        Set<String> nombresVistos = ConcurrentHashMap.newKeySet();
        Set<Long> tamanosVistos = ConcurrentHashMap.newKeySet();
        AtomicInteger sinTamano = new AtomicInteger();
        AtomicInteger vueltas = new AtomicInteger();
        AtomicBoolean escribiendo = new AtomicBoolean(true);
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread observador = new Thread(() -> {
            while (escribiendo.get()) {
                try (Stream<Path> archivos = Files.list(carpeta)) {
                    archivos.forEach(p -> nombresVistos.add(p.getFileName().toString()));
                } catch (IOException e) {
                    // La carpeta cambió mientras se listaba: se vuelve a mirar
                }
                try {
                    tamanosVistos.add(Files.size(archivo));
                } catch (IOException e) {
                    sinTamano.incrementAndGet();
                }
                vueltas.incrementAndGet();
            }
        }, "Observador de la carpeta");
        observador.setUncaughtExceptionHandler(guardarEImprimir(error));
        observador.start();
        try {
            EscritorArchivo.escribir(analogicaAlAzar("A5", FILAS_UNA_HORA, 6), archivo);
        } finally {
            escribiendo.set(false);
            observador.join();
        }
        long tamanoNuevo = Files.size(archivo);

        List<String> temporales = new ArrayList<>();
        for (String nombre : nombresVistos) {
            if (nombre.startsWith("~A5.txt.") && nombre.endsWith(".tmp")) {
                temporales.add(nombre);
            }
        }
        verificar("Mientras escribe, los datos van a un temporal en la misma carpeta (se vio \""
                        + (temporales.isEmpty() ? "ninguno" : temporales.get(0))
                        + "\"), que ya no está al terminar",
                error.get() == null && !temporales.isEmpty() && contenido(carpeta).equals(Set.of("A5.txt")),
                "nombres vistos: " + nombresVistos + "; al terminar: " + contenido(carpeta));

        Set<Long> tamanosRaros = new TreeSet<>(tamanosVistos);
        tamanosRaros.remove(tamanoViejo);
        tamanosRaros.remove(tamanoNuevo);
        verificar(String.format("... y el archivo anterior se vio siempre completo: en %,d vistazos, solo con "
                                + "su tamaño viejo (%,d bytes) o con el nuevo (%,d), nunca a medias%s",
                        vueltas.get(), tamanoViejo, tamanoNuevo,
                        sinTamano.get() == 0 ? "" : " (" + sinTamano.get() + " vistazos sin poder leer el tamaño)"),
                error.get() == null && tamanosVistos.contains(tamanoViejo) && tamanosRaros.isEmpty(),
                "tamaños vistos: " + tamanosVistos);
    }

    private static void verificarReemplazo() throws IOException {
        Path carpeta = carpetaNueva("reemplazo");
        Path archivo = carpeta.resolve("D1.txt");
        EscritorArchivo.escribir(analogicaAlAzar("A1", 1000, 7), archivo);
        long antes = Files.size(archivo);
        EscritorArchivo.escribir(new HistorialSenal("D1", HistorialSenal.Tipo.DIGITAL,
                new double[]{0.0, 0.01, 0.02}, new double[]{1, 0, 1}), archivo);
        String texto = Files.readString(archivo, StandardCharsets.UTF_8);
        verificar("Reemplazar un archivo existente (1000 filas, " + antes + " bytes) deja solo el contenido "
                        + "nuevo: exactamente \"0.000\\t1\\r\\n0.010\\t0\\r\\n0.020\\t1\\r\\n\", y ningún temporal",
                texto.equals("0.000\t1\r\n0.010\t0\r\n0.020\t1\r\n")
                        && contenido(carpeta).equals(Set.of("D1.txt")),
                "quedó \"" + visible(texto.length() > 60 ? texto.substring(0, 60) + "…" : texto)
                        + "\"; en la carpeta: " + contenido(carpeta));
    }

    /**
     * Otro hilo lee el tamaño del archivo sin parar mientras se reemplaza 500
     * veces seguidas. En Windows, una lectura justo en el instante del
     * reemplazo hace que se niegue: sin reintentar fallaban de 26 a 41 de los
     * 500 (medido en I7LV-25). Lo mismo puede hacer el Explorador de Windows
     * con la carpeta abierta. EscritorArchivo reintenta, así que no debe
     * fallar ninguno.
     */
    private static void verificarReemplazoConLecturas() throws Exception {
        Path carpeta = carpetaNueva("reemplazo-con-lecturas");
        Path archivo = carpeta.resolve("A2.txt");
        HistorialSenal historial = analogicaAlAzar("A2", 1000, 31);
        EscritorArchivo.escribir(historial, archivo);

        AtomicBoolean seguir = new AtomicBoolean(true);
        AtomicLong lecturas = new AtomicLong();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread lector = new Thread(() -> {
            while (seguir.get()) {
                try {
                    Files.size(archivo);
                    lecturas.incrementAndGet();
                } catch (IOException e) {
                    // Justo durante un reemplazo: se vuelve a leer
                }
            }
        }, "Lector del tamaño");
        lector.setUncaughtExceptionHandler(guardarEImprimir(error));
        int fallas = 0;
        String primera = "";
        lector.start();
        try {
            for (int k = 0; k < 500; k++) {
                IOException falla = falla(() -> EscritorArchivo.escribir(historial, archivo));
                if (falla != null && fallas++ == 0) {
                    primera = mensaje(falla);
                }
            }
        } finally {
            seguir.set(false);
            lector.join();
        }
        verificar(String.format("Reemplazar 500 veces seguidas mientras otro hilo lee el tamaño del archivo sin parar "
                                + "(%,d lecturas), como el Explorador de Windows con la carpeta abierta: ninguno falla "
                                + "(el escritor reintenta) y no queda ningún temporal", lecturas.get()),
                error.get() == null && fallas == 0 && contenido(carpeta).equals(Set.of("A2.txt")),
                fallas + " fallas; la primera: " + primera + "; en la carpeta: " + contenido(carpeta));
    }

    private static void verificarFallas() throws IOException {
        HistorialSenal nuevo = analogicaAlAzar("A3", 50, 8);

        // 1) Carpeta de destino inexistente
        Path carpeta = carpetaNueva("falla-sin-carpeta");
        Path enCarpetaInexistente = carpeta.resolve("no-existe").resolve("A3.txt");
        IOException falla = falla(() -> EscritorArchivo.escribir(nuevo, enCarpetaInexistente));
        verificar("Carpeta de destino inexistente: lanza IOException con un mensaje en español que lo dice "
                        + "(\"" + mensaje(falla) + "\")",
                falla != null && mensaje(falla).contains("la carpeta no existe")
                        && mensaje(falla).contains("A3.txt"),
                falla == null ? "no lanzó ninguna excepción" : mensaje(falla));
        verificar("... y no crea la carpeta ni deja ningún archivo, tampoco un temporal",
                !Files.exists(enCarpetaInexistente.getParent()) && contenido(carpeta).isEmpty(),
                "en la carpeta: " + contenido(carpeta));

        // 2) El destino es una carpeta
        carpeta = carpetaNueva("falla-es-carpeta");
        Path esCarpeta = Files.createDirectory(carpeta.resolve("A3.txt"));
        Path adentro = Files.writeString(esCarpeta.resolve("adentro.txt"), "no tocar");
        falla = falla(() -> EscritorArchivo.escribir(nuevo, esCarpeta));
        verificar("Si el destino es una carpeta: IOException que lo dice (\"" + mensaje(falla) + "\"), la carpeta "
                        + "y lo que tiene quedan intactos y no queda ningún temporal",
                falla != null && mensaje(falla).contains("ya hay una carpeta con ese nombre")
                        && Files.isDirectory(esCarpeta)
                        && Files.readString(adentro).equals("no tocar")
                        && contenido(carpeta).equals(Set.of("A3.txt")),
                (falla == null ? "no lanzó ninguna excepción" : mensaje(falla)) + "; en la carpeta: "
                        + contenido(carpeta));

        // 3) y 4) Archivo abierto en otro programa, y archivo de solo lectura
        if (!EN_WINDOWS) {
            System.out.println("(Se omiten las pruebas del archivo abierto en otro programa y del archivo de "
                    + "solo lectura: esos bloqueos solo se pueden simular en Windows.)");
            return;
        }
        carpeta = carpetaNueva("falla-abierto");
        Path abierto = carpeta.resolve("A3.txt");
        EscritorArchivo.escribir(analogicaAlAzar("A3", 1000, 9), abierto);
        byte[] antes = Files.readAllBytes(abierto);
        // Como Excel: otro programa tiene el archivo abierto. Un
        // FileInputStream no deja que Windows lo reemplace mientras tanto.
        FileInputStream otroPrograma = new FileInputStream(abierto.toFile());
        try {
            falla = falla(() -> EscritorArchivo.escribir(nuevo, abierto));
        } finally {
            otroPrograma.close();
        }
        verificar("Archivo de destino abierto en otro programa (simulado con un FileInputStream abierto, "
                        + "que en Windows impide reemplazarlo, como Excel): lanza IOException que lo explica "
                        + "(\"" + mensaje(falla) + "\")",
                falla != null && mensaje(falla).contains("abierto en otro programa"),
                falla == null ? "no lanzó ninguna excepción" : mensaje(falla));
        verificar("... el archivo anterior queda intacto (los mismos " + antes.length + " bytes) y no queda "
                        + "ningún temporal",
                Arrays.equals(antes, Files.readAllBytes(abierto))
                        && contenido(carpeta).equals(Set.of("A3.txt")),
                Files.size(abierto) + " bytes; en la carpeta: " + contenido(carpeta));

        carpeta = carpetaNueva("falla-solo-lectura");
        Path soloLectura = carpeta.resolve("A3.txt");
        EscritorArchivo.escribir(analogicaAlAzar("A3", 1000, 10), soloLectura);
        byte[] previo = Files.readAllBytes(soloLectura);
        boolean marcado = soloLectura.toFile().setReadOnly();
        try {
            falla = falla(() -> EscritorArchivo.escribir(nuevo, soloLectura));
        } finally {
            soloLectura.toFile().setWritable(true); // para poder borrarlo al terminar
        }
        verificar("Archivo de destino de solo lectura: IOException, el archivo queda intacto y no queda "
                        + "ningún temporal",
                marcado && falla != null && Arrays.equals(previo, Files.readAllBytes(soloLectura))
                        && contenido(carpeta).equals(Set.of("A3.txt")),
                (falla == null ? "no lanzó ninguna excepción" : mensaje(falla)) + "; en la carpeta: "
                        + contenido(carpeta));
    }

    // ===================== Nombre sugerido =====================

    private static void verificarNombreSugerido() throws IOException {
        String a3 = EscritorArchivo.nombreSugerido("A3", FECHA);
        verificar("Nombre sugerido: \"A3\" el 4 de octubre de 2026 a las 15:30 da \"A3_2026-10-04_15-30.txt\"",
                a3.equals("A3_2026-10-04_15-30.txt"), "dio \"" + a3 + "\"");
        String d2 = EscritorArchivo.nombreSugerido("D2", LocalDateTime.of(2026, 1, 5, 9, 7, 59));
        verificar("... con ceros a la izquierda y sin segundos: \"D2\" el 5 de enero de 2026 a las 9:07:59 da "
                        + "\"D2_2026-01-05_09-07.txt\"",
                d2.equals("D2_2026-01-05_09-07.txt"), "dio \"" + d2 + "\"");

        List<String> noCumplen = new ArrayList<>();
        for (String canal : nombresCanalesDelPrograma()) {
            String nombre = EscritorArchivo.nombreSugerido(canal, FECHA);
            if (!nombre.startsWith(canal + "_") || !NOMBRE_SUGERIDO.matcher(nombre).matches()) {
                noCumplen.add(nombre);
            }
        }
        verificar("... y con todos los canales del programa (A0 a A7, D0 a D3) cumple canal_aaaa-mm-dd_hh-mm.txt ("
                        + NOMBRE_SUGERIDO + "), con el nombre del canal tal cual",
                noCumplen.isEmpty(), "no cumplen: " + noCumplen);

        List<String> conOtraRegion = new ArrayList<>();
        String[] regiones = {"th-TH-u-nu-thai", "ar-SA-u-nu-arab", "hi-IN-u-nu-deva", "ja-JP-u-ca-japanese"};
        try {
            for (String region : regiones) {
                Locale.setDefault(Locale.forLanguageTag(region));
                conOtraRegion.add(EscritorArchivo.nombreSugerido("A3", FECHA));
            }
        } finally {
            Locale.setDefault(ES_CO);
        }
        verificar("... igual con cualquier configuración regional (tailandesa, árabe y hindi con sus propios "
                        + "dígitos, japonesa con su calendario): siempre dígitos 0 a 9 y el calendario de siempre",
                conOtraRegion.stream().allMatch(a3::equals), "dio " + conOtraRegion);

        String raro = EscritorArchivo.nombreSugerido(" A<3>:\"/\\|?*\u0007 ", FECHA);
        boolean sinInvalidos = raro.chars().noneMatch(c -> c < ' ' || INVALIDOS_WINDOWS.indexOf(c) >= 0);
        Path carpeta = carpetaNueva("nombres");
        boolean creado;
        try {
            creado = Files.isRegularFile(Files.createFile(carpeta.resolve(raro)));
        } catch (RuntimeException | IOException e) {
            creado = false;
        }
        verificar("Un canal con caracteres que Windows no acepta en un nombre (< > : \" / \\ | ? *, un "
                        + "carácter de control y espacios en los extremos) da \"" + raro + "\": sin esos "
                        + "caracteres, y con ese nombre se puede crear el archivo",
                sinInvalidos && raro.equals("A_3" + "_".repeat(9) + "_2026-10-04_15-30.txt") && creado
                        && contenido(carpeta).equals(Set.of(raro)),
                "sin inválidos: " + sinInvalidos + "; creado: " + creado);
        String sinNombre = EscritorArchivo.nombreSugerido("  ", FECHA);
        verificar("... y un nombre de canal vacío da \"Senal_2026-10-04_15-30.txt\"",
                sinNombre.equals("Senal_2026-10-04_15-30.txt"), "dio \"" + sinNombre + "\"");
    }

    // ===================== HistorialSenal =====================

    private static void verificarHistorialSenal() {
        HistorialSenal.Tipo analogica = HistorialSenal.Tipo.ANALOGICA;
        boolean distintoLargo = rechaza(() -> new HistorialSenal("A0", analogica, new double[2], new double[3]));
        boolean tiempoNaN = rechaza(() -> new HistorialSenal("A0", analogica,
                new double[]{0.0, Double.NaN}, new double[]{1.0, 1.0}));
        boolean valorInfinito = rechaza(() -> new HistorialSenal("A0", analogica,
                new double[]{0.0, 0.01}, new double[]{1.0, Double.POSITIVE_INFINITY}));
        boolean digitalRaro = rechaza(() -> new HistorialSenal("D0", HistorialSenal.Tipo.DIGITAL,
                new double[]{0.0, 0.01}, new double[]{1.0, 0.5}));
        verificar("HistorialSenal rechaza con IllegalArgumentException lo que no se podría escribir bien: "
                        + "tiempos y valores de distinta cantidad, un tiempo NaN, un valor infinito y un valor "
                        + "digital que no es 0 ni 1",
                distintoLargo && tiempoNaN && valorInfinito && digitalRaro,
                "distinta cantidad " + distintoLargo + ", NaN " + tiempoNaN + ", infinito " + valorInfinito
                        + ", digital 0,5 " + digitalRaro);

        double[] tiempos = {0.1, 0.2};
        double[] valores = {1.0, 2.0};
        HistorialSenal historial = new HistorialSenal("A0", analogica, tiempos, valores);
        tiempos[0] = 99.0;
        valores[0] = 99.0;
        verificar("HistorialSenal copia los arreglos: cambiarlos después no cambia lo que se va a escribir",
                historial.getTiempo(0) == 0.1 && historial.getValor(0) == 1.0,
                "quedó (" + historial.getTiempo(0) + ", " + historial.getValor(0) + ")");
    }

    // ===================== Las gráficas =====================

    private static void verificarGraficaAnalogica() throws Exception {
        AnalogicaDePrueba g = crearAnalogica(2);
        enviarAnalogica(g, 0, 500); // 5 s de muestras: A2 vale 1,0 V

        HistorialSenal a2 = enSwing(() -> g.grafica().getHistorialParaGuardar());
        List<SerieEnVivo.Punto> historial = enSwing(() -> g.grafica().getHistorial());
        verificar("Analógica: getHistorialParaGuardar() entrega el canal elegido (\"A2\"), tipo analógico, y "
                        + "las 500 muestras de su historial con sus tiempos y voltajes (1,0 V)",
                a2.getCanal().equals("A2") && a2.getTipo() == HistorialSenal.Tipo.ANALOGICA
                        && a2.getCantidad() == 500 && mismosPuntos(a2, historial)
                        && soloVale(a2, valorCanal(2)),
                describir(a2) + "; el historial de la gráfica tiene " + historial.size());

        HistorialSenal a5 = enSwing(() -> {
            g.grafica().cambiarCanal(5);
            return g.grafica().getHistorialParaGuardar();
        });
        verificar("Justo después de cambiar a A5, en la misma tarea de Swing: \"A5\" con el historial vacío "
                        + "(nada de A2)",
                a5.getCanal().equals("A5") && a5.getCantidad() == 0, describir(a5));

        enviarAnalogica(g, 500, 800);
        HistorialSenal a5Luego = enSwing(() -> g.grafica().getHistorialParaGuardar());
        verificar("... y con las muestras que llegan después: \"A5\" con solo 2,5 V (300 muestras)",
                a5Luego.getCanal().equals("A5") && a5Luego.getCantidad() == 300
                        && soloVale(a5Luego, valorCanal(5)),
                describir(a5Luego));

        // Con el selector de la pestaña, conectado como en Main
        HistorialSenal a6 = enSwing(() -> {
            JComboBox<String> selector = g.panel().getComboCanal();
            new ControlSeleccion(selector, g.grafica());
            selector.setSelectedIndex(6);
            return g.grafica().getHistorialParaGuardar();
        });
        verificar("Al elegir A6 en el selector de la pestaña, la copia tomada enseguida ya es de \"A6\" (vacía)",
                a6.getCanal().equals("A6") && a6.getCantidad() == 0, describir(a6));

        enviarAnalogica(g, 800, 900);
        HistorialSenal primera = enSwing(() -> g.grafica().getHistorialParaGuardar());
        enviarAnalogica(g, 900, 1000);
        HistorialSenal segunda = enSwing(() -> g.grafica().getHistorialParaGuardar());
        verificar("Una copia ya entregada no cambia cuando llegan más muestras (sigue con 100; una nueva "
                        + "tiene 200, todas de A6)",
                primera.getCantidad() == 100 && segunda.getCantidad() == 200
                        && soloVale(segunda, valorCanal(6)),
                describir(primera) + " / " + describir(segunda));
    }

    private static void verificarGraficaDigital() throws Exception {
        DigitalDePrueba g = crearDigital(0);
        enviarDigital(g, 0, 700, VerificacionSprint3::patron);

        HistorialSenal d0 = enSwing(() -> g.grafica().getHistorialParaGuardar());
        verificar("Digital: getHistorialParaGuardar() entrega la señal seleccionada (\"D0\"), tipo digital, con "
                        + "las 700 muestras de su historial (sus tiempos y los 0 y 1 de D0)",
                d0.getCanal().equals("D0") && d0.getTipo() == HistorialSenal.Tipo.DIGITAL
                        && d0.getCantidad() == 700 && bitsDistintos(d0, 0, VerificacionSprint3::patron) == 0,
                describir(d0) + "; " + bitsDistintos(d0, 0, VerificacionSprint3::patron) + " puntos distintos");

        HistorialSenal d2 = enSwing(() -> {
            g.grafica().cambiarCanal(2);
            return g.grafica().getHistorialParaGuardar();
        });
        verificar("Justo después de seleccionar D2, en la misma tarea de Swing: \"D2\" con las mismas 700 "
                        + "muestras, ahora con los bits de D2 (cambiar la selección no borra nada)",
                d2.getCanal().equals("D2") && d2.getCantidad() == 700
                        && bitsDistintos(d2, 2, VerificacionSprint3::patron) == 0,
                describir(d2) + "; " + bitsDistintos(d2, 2, VerificacionSprint3::patron) + " puntos distintos");

        HistorialSenal d3 = enSwing(() -> {
            JComboBox<String> selector = g.panel().getComboCanal();
            new ControlSeleccion(selector, g.grafica());
            selector.setSelectedIndex(3);
            return g.grafica().getHistorialParaGuardar();
        });
        verificar("Al elegir D3 en el selector de la pestaña, la copia tomada enseguida es de \"D3\" con sus bits",
                d3.getCanal().equals("D3") && d3.getCantidad() == 700
                        && bitsDistintos(d3, 3, VerificacionSprint3::patron) == 0,
                describir(d3) + "; " + bitsDistintos(d3, 3, VerificacionSprint3::patron) + " puntos distintos");
    }

    /**
     * Un hilo envía muestras sin parar (el canal i siempre vale i × 0,5 V)
     * mientras el hilo de Swing cambia de canal 1000 veces y otro hilo toma
     * copias para guardar sin parar. Cada copia debe ser coherente: todos
     * sus voltajes, del canal que dice su nombre. Es lo que garantiza el
     * candado de GraficaSenal.historialParaGuardar(); desde otro hilo es el
     * caso más exigente (en el programa se llamará desde el de Swing).
     */
    private static void verificarConcurrenciaAnalogica() throws Exception {
        AnalogicaDePrueba g = crearAnalogica(0);
        Concurrencia c = concurrencia(g.grafica(), "A", CANALES,
                i -> g.grafica().muestraRecibida(muestraAnalogica(tiempoRapido(i))),
                VerificacionSprint3::analogicaCoherente, 21);
        verificar(String.format("Analógica: %,d copias tomadas desde otro hilo mientras el hilo de Swing cambia "
                                + "de canal %d veces y otro hilo envía %,d muestras: todas coherentes, con el "
                                + "nombre y el historial del mismo canal (%,d con datos, de %d canales)",
                        c.copias(), CAMBIOS, c.muestras(), c.copiasConDatos(), c.canalesVistos()),
                c.error() == null && c.incoherentes() == 0 && c.copiasConDatos() > 0 && c.canalesVistos() > 1,
                c.error() != null ? c.error().toString()
                        : c.incoherentes() + " copias incoherentes; la primera: " + c.primeraIncoherente());
        verificar("... y justo después de cada cambio, en la misma tarea de Swing, la copia ya es del canal "
                        + "nuevo y coherente: " + c.bienTrasCambio() + " de " + CAMBIOS,
                c.bienTrasCambio() == CAMBIOS, (CAMBIOS - c.bienTrasCambio()) + " mal");
    }

    /** Lo mismo con la gráfica digital: bits pseudoaleatorios que se conocen por el tiempo de la muestra. */
    private static void verificarConcurrenciaDigital() throws Exception {
        DigitalDePrueba g = crearDigital(0);
        Concurrencia c = concurrencia(g.grafica(), "D", CANALES_DIGITALES,
                i -> g.grafica().muestraRecibida(muestraDigital(tiempoRapido(i), patronRapido(i))),
                VerificacionSprint3::digitalCoherente, 22);
        verificar(String.format("Digital: %,d copias tomadas desde otro hilo mientras el hilo de Swing cambia "
                                + "la selección %d veces y otro hilo envía %,d muestras: todas coherentes, con "
                                + "los 0 y 1 de la señal que dice su nombre (%,d con datos, de %d señales)",
                        c.copias(), CAMBIOS, c.muestras(), c.copiasConDatos(), c.canalesVistos()),
                c.error() == null && c.incoherentes() == 0 && c.copiasConDatos() > 0 && c.canalesVistos() > 1,
                c.error() != null ? c.error().toString()
                        : c.incoherentes() + " copias incoherentes; la primera: " + c.primeraIncoherente());
        verificar("... y justo después de cada cambio, en la misma tarea de Swing, la copia ya es de la señal "
                        + "nueva y coherente: " + c.bienTrasCambio() + " de " + CAMBIOS,
                c.bienTrasCambio() == CAMBIOS, (CAMBIOS - c.bienTrasCambio()) + " mal");
    }

    /**
     * De la gráfica al archivo, como lo hará I7LV-25: la copia se toma en el
     * hilo de Swing y se escribe desde este hilo, fuera del de Swing, con el
     * nombre sugerido.
     */
    private static void verificarArchivoDesdeGraficas() throws Exception {
        Path carpeta = carpetaNueva("desde-graficas");

        AnalogicaDePrueba a = crearAnalogica(5);
        for (int i = 0; i < 300; i++) {
            a.grafica().muestraRecibida(muestraVariable(tiempo(i)));
        }
        HistorialSenal copiaA = enSwing(() -> a.grafica().getHistorialParaGuardar());
        List<SerieEnVivo.Punto> puntosA = enSwing(() -> a.grafica().getHistorial());
        Path archivoA = carpeta.resolve(EscritorArchivo.nombreSugerido(copiaA.getCanal(), FECHA));
        EscritorArchivo.escribir(copiaA, archivoA);
        Leido leidoA = leer(archivoA);
        int distintasA = distintasAlLeer(leidoA, puntosA.size(), i -> puntosA.get(i).tiempo(),
                i -> puntosA.get(i).valor(), false);
        verificar("De la gráfica al archivo, analógica en A5: \"" + archivoA.getFileName() + "\" con una fila por "
                        + "punto del historial (300), cada una con su tiempo y su voltaje redondeados a 3 decimales",
                archivoA.getFileName().toString().equals("A5_2026-10-04_15-30.txt")
                        && puntosA.size() == 300 && leidoA.filas().size() == 300 && distintasA == 0
                        && noCumplen(leidoA.filas(), FILA_ANALOGICA) == 0,
                leidoA.filas().size() + " filas, " + distintasA + " distintas");

        DigitalDePrueba d = crearDigital(2);
        enviarDigital(d, 0, 300, VerificacionSprint3::patron);
        HistorialSenal copiaD = enSwing(() -> d.grafica().getHistorialParaGuardar());
        List<SerieEnVivo.Punto> puntosD = enSwing(() -> d.grafica().getHistorial());
        Path archivoD = carpeta.resolve(EscritorArchivo.nombreSugerido(copiaD.getCanal(), FECHA));
        EscritorArchivo.escribir(copiaD, archivoD);
        Leido leidoD = leer(archivoD);
        int distintasD = distintasAlLeer(leidoD, puntosD.size(), i -> puntosD.get(i).tiempo(),
                i -> puntosD.get(i).valor(), true);
        verificar("De la gráfica al archivo, digital con D2 seleccionada: \"" + archivoD.getFileName()
                        + "\" con una fila por punto del historial de D2 (300), con sus 0 y 1",
                archivoD.getFileName().toString().equals("D2_2026-10-04_15-30.txt")
                        && puntosD.size() == 300 && leidoD.filas().size() == 300 && distintasD == 0
                        && noCumplen(leidoD.filas(), FILA_DIGITAL) == 0,
                leidoD.filas().size() + " filas, " + distintasD + " distintas");
    }

    // ===================== I7LV-25: guardar con el botón =====================

    private static void verificarGuardadoSinDatos() throws Exception {
        Path destinos = carpetaNueva("sin-datos");
        DialogosDePrueba dialogos = new DialogosDePrueba(destinos::resolve);
        AnalogicaDePrueba a = crearAnalogica(3);
        DigitalDePrueba d = crearDigital(0);
        GuardadoDePrueba ga = conGuardado(a.panel(), a.grafica(), new CarpetaRecordada(), dialogos);
        GuardadoDePrueba gd = conGuardado(d.panel(), d.grafica(), new CarpetaRecordada(), dialogos);

        Clic clicA = pulsarGuardar(ga);
        Clic clicD = pulsarGuardar(gd);
        List<AvisoDado> avisos = avisos(dialogos);
        verificar("Historial vacío, en las dos pestañas: aviso \"" + SIN_DATOS + "\" (\"Guardar señal A3\" y "
                        + "\"Guardar señal D0\") y nada más: no se pregunta dónde guardar ni se crea ningún archivo",
                avisos.equals(List.of(new AvisoDado(Aviso.ADVERTENCIA, "Guardar señal A3", SIN_DATOS),
                        new AvisoDado(Aviso.ADVERTENCIA, "Guardar señal D0", SIN_DATOS)))
                        && pedidos(dialogos).isEmpty() && contenido(destinos).isEmpty()
                        && clicA.sinEscribir() && clicD.sinEscribir(),
                "avisos: " + avisos + "; pedidos: " + pedidos(dialogos) + "; en la carpeta: " + contenido(destinos));

        // Con muestras de A3, y enseguida otro canal: el cambio vacía el historial
        enviarAnalogica(a, 0, 100);
        enSwing(() -> {
            a.grafica().cambiarCanal(5);
            return null;
        });
        Clic clicA5 = pulsarGuardar(ga);
        avisos = avisos(dialogos);
        verificar("... también justo después de cambiar de canal en la analógica (de A3 con 100 muestras a A5): "
                        + "el mismo aviso, en \"Guardar señal A5\"",
                avisos.size() == 3
                        && avisos.get(2).equals(new AvisoDado(Aviso.ADVERTENCIA, "Guardar señal A5", SIN_DATOS))
                        && pedidos(dialogos).isEmpty() && contenido(destinos).isEmpty() && clicA5.sinEscribir(),
                "avisos: " + avisos);
    }

    private static void verificarTituloYNombre() throws Exception {
        DialogosDePrueba dialogos = new DialogosDePrueba(nombre -> null); // cancela
        AnalogicaDePrueba a = crearAnalogica(0);
        DigitalDePrueba d = crearDigital(0);
        enviarAnalogica(a, 0, 50);
        enviarDigital(d, 0, 50, VerificacionSprint3::patron);
        // A3 y D2 elegidas en los selectores de las pestañas, como el usuario
        enSwing(() -> {
            new ControlSeleccion(a.panel().getComboCanal(), a.grafica());
            new ControlSeleccion(d.panel().getComboCanal(), d.grafica());
            a.panel().getComboCanal().setSelectedIndex(3);
            d.panel().getComboCanal().setSelectedIndex(2);
            return null;
        });
        enviarAnalogica(a, 50, 100); // el cambio a A3 vació lo de A0
        GuardadoDePrueba ga = conGuardado(a.panel(), a.grafica(), new CarpetaRecordada(), dialogos);
        GuardadoDePrueba gd = conGuardado(d.panel(), d.grafica(), new CarpetaRecordada(), dialogos);

        LocalDateTime antes = LocalDateTime.now();
        pulsarGuardar(ga);
        pulsarGuardar(gd);
        LocalDateTime despues = LocalDateTime.now();
        List<Pedido> pedidos = pedidos(dialogos);
        verificar("La ventana de guardar recibe el título con el canal real y el nombre sugerido: \"Guardar señal A3\" "
                        + "y \"" + (pedidos.isEmpty() ? "" : pedidos.get(0).nombre()) + "\" en la analógica, \"Guardar "
                        + "señal D2\" y \"" + (pedidos.size() < 2 ? "" : pedidos.get(1).nombre()) + "\" en la digital "
                        + "(canales elegidos en los selectores)",
                pedidos.size() == 2
                        && pedidos.get(0).titulo().equals("Guardar señal A3")
                        && nombreSugeridoEntre(pedidos.get(0).nombre(), "A3", antes, despues)
                        && pedidos.get(1).titulo().equals("Guardar señal D2")
                        && nombreSugeridoEntre(pedidos.get(1).nombre(), "D2", antes, despues),
                "pedidos: " + pedidos);
    }

    private static void verificarCancelar() throws Exception {
        CarpetaRecordada carpeta = new CarpetaRecordada();
        Path inicial = enSwing(carpeta::getCarpeta);
        DialogosDePrueba dialogos = new DialogosDePrueba(nombre -> null);
        AnalogicaDePrueba a = crearAnalogica(1);
        enviarAnalogica(a, 0, 200);
        GuardadoDePrueba g = conGuardado(a.panel(), a.grafica(), carpeta, dialogos);

        Clic clic = pulsarGuardar(g);
        List<Pedido> pedidos = pedidos(dialogos);
        String propuesto = pedidos.isEmpty() ? "" : pedidos.get(0).nombre();
        verificar("Cancelar la ventana de guardar no hace nada: ninguna pregunta ni aviso, el botón nunca cambia, la "
                        + "carpeta recordada sigue igual y no se escribe el nombre propuesto en la carpeta inicial",
                pedidos.size() == 1 && preguntas(dialogos).isEmpty() && avisos(dialogos).isEmpty()
                        && clic.sinEscribir() && enSwing(carpeta::getCarpeta).equals(inicial)
                        && !Files.exists(inicial.resolve(propuesto)),
                clic + "; preguntas " + preguntas(dialogos) + "; avisos " + avisos(dialogos));
    }

    private static void verificarGuardadoCompleto() throws Exception {
        Path destinos = carpetaNueva("guardado");
        CarpetaRecordada carpeta = new CarpetaRecordada();
        DialogosDePrueba dialogos = new DialogosDePrueba(destinos::resolve); // acepta el nombre propuesto
        AnalogicaDePrueba a = crearAnalogica(3);
        for (int i = 0; i < 1234; i++) {
            a.grafica().muestraRecibida(muestraVariable(tiempo(i)));
        }
        GuardadoDePrueba g = conGuardado(a.panel(), a.grafica(), carpeta, dialogos);

        List<SerieEnVivo.Punto> alClic = enSwing(() -> a.grafica().getHistorial());
        Clic clic = pulsarGuardar(g, 2); // el segundo clic llega con el botón deshabilitado
        List<Pedido> pedidos = pedidos(dialogos);
        Path archivo = destinos.resolve(pedidos.get(0).nombre());
        Leido leido = leer(archivo);
        int distintas = distintasAlLeer(leido, alClic.size(), i -> alClic.get(i).tiempo(),
                i -> alClic.get(i).valor(), false);
        verificar("Guardar A3 con 1234 muestras: el archivo \"" + archivo.getFileName() + "\" tiene una fila por "
                        + "muestra del historial, con sus tiempos y voltajes, y no se pregunta nada porque no existía",
                alClic.size() == 1234 && leido.filas().size() == 1234 && distintas == 0
                        && noCumplen(leido.filas(), FILA_ANALOGICA) == 0 && preguntas(dialogos).isEmpty(),
                leido.filas().size() + " filas, " + distintas + " distintas; preguntas " + preguntas(dialogos));

        String exito = "Se guardaron 1.234 muestras de A3 en " + archivo;
        verificar("... y avisa \"Se guardaron 1.234 muestras de A3 en <ruta completa>\", con los miles como en la "
                        + "barra de estado",
                avisos(dialogos).equals(List.of(new AvisoDado(Aviso.INFORMACION, "Guardar señal A3", exito))),
                "avisos: " + avisos(dialogos));
        verificar("Mientras escribe, el botón queda deshabilitado y dice \"Guardando…\", sin cambiar de ancho; al "
                        + "terminar vuelve a \"" + TEXTO_BOTON + "\", habilitado",
                clic.escribio(), clic.toString());
        verificar("... un segundo clic mientras escribe no hace nada (se pidió dónde guardar una sola vez), y todas "
                        + "las ventanas se piden en el hilo de Swing",
                pedidos.size() == 1 && enSwing(() -> dialogos.fueraDelHiloDeSwing) == 0,
                pedidos.size() + " pedidos; " + enSwing(() -> dialogos.fueraDelHiloDeSwing) + " llamadas fuera del hilo de Swing");
        verificar("... en la carpeta queda solo el archivo guardado (ningún temporal), y esa carpeta queda recordada",
                contenido(destinos).equals(Set.of(archivo.getFileName().toString()))
                        && enSwing(carpeta::getCarpeta).equals(destinos),
                "en la carpeta: " + contenido(destinos) + "; recordada: " + enSwing(carpeta::getCarpeta));
    }

    private static void verificarExtension() throws Exception {
        Path destinos = carpetaNueva("extension");
        String[] escritos = {"datos", "Prueba 2.5 V", "datos2.", "tabla.csv", "MAYUSCULAS.TXT"};
        String[] guardados = {"datos.txt", "Prueba 2.5 V.txt", "datos2.txt", "tabla.csv", "MAYUSCULAS.TXT"};
        AnalogicaDePrueba a = crearAnalogica(0);
        enviarAnalogica(a, 0, 1); // una sola muestra
        DialogosDePrueba dialogos = new DialogosDePrueba(null);
        GuardadoDePrueba g = conGuardado(a.panel(), a.grafica(), new CarpetaRecordada(), dialogos);
        for (String escrito : escritos) {
            enSwing(() -> {
                dialogos.eleccion = nombre -> destinos.resolve(escrito);
                return null;
            });
            pulsarGuardar(g);
        }

        Set<String> quedaron = contenido(destinos);
        verificar("Si el nombre escrito no tiene extensión se agrega \".txt\": \"datos\" → \"datos.txt\", \"Prueba "
                        + "2.5 V\" → \"Prueba 2.5 V.txt\" (\"5 V\" no es una extensión) y \"datos2.\" → \"datos2.txt\"",
                quedaron.containsAll(List.of(guardados[0], guardados[1], guardados[2]))
                        && !quedaron.contains("datos") && !quedaron.contains("Prueba 2.5 V"),
                "en la carpeta: " + quedaron);
        verificar("... y si ya tiene una, queda igual: \"tabla.csv\" y \"MAYUSCULAS.TXT\"",
                quedaron.equals(Set.of(guardados)), "en la carpeta: " + quedaron);

        List<AvisoDado> esperados = new ArrayList<>();
        for (String guardado : guardados) {
            esperados.add(new AvisoDado(Aviso.INFORMACION, "Guardar señal A0",
                    "Se guardó 1 muestra de A0 en " + destinos.resolve(guardado)));
        }
        verificar("Con una sola muestra el aviso va en singular (\"Se guardó 1 muestra de A0 en …\"), con la ruta "
                        + "que de verdad se usó",
                avisos(dialogos).equals(esperados), "avisos: " + avisos(dialogos));
    }

    private static void verificarReemplazoConPregunta() throws Exception {
        Path destinos = carpetaNueva("reemplazo-con-pregunta");
        Path archivo = destinos.resolve("A3.txt");
        byte[] anterior = "contenido anterior\r\n".getBytes(StandardCharsets.UTF_8);
        Files.write(archivo, anterior);
        DialogosDePrueba dialogos = new DialogosDePrueba(nombre -> archivo);
        AnalogicaDePrueba a = crearAnalogica(3);
        enviarAnalogica(a, 0, 300);
        CarpetaRecordada carpeta = new CarpetaRecordada();
        Path carpetaInicial = enSwing(carpeta::getCarpeta);
        GuardadoDePrueba g = conGuardado(a.panel(), a.grafica(), carpeta, dialogos);

        Clic conNo = pulsarGuardar(g); // contesta No
        List<Pregunta> preguntas = preguntas(dialogos);
        verificar("Si el archivo ya existe se pregunta \"" + PREGUNTA_REEMPLAZO + "\" (con el nombre del archivo, en "
                        + "\"Guardar señal A3\")",
                preguntas.size() == 1 && preguntas.get(0).titulo().equals("Guardar señal A3")
                        && preguntas.get(0).texto().contains(PREGUNTA_REEMPLAZO)
                        && preguntas.get(0).texto().contains("A3.txt"),
                "preguntas: " + preguntas);
        verificar("... con No, el archivo queda intacto: ningún aviso, el botón no pasa por \"Guardando…\", ningún "
                        + "temporal, y la carpeta recordada no cambia",
                Arrays.equals(anterior, Files.readAllBytes(archivo)) && avisos(dialogos).isEmpty()
                        && conNo.sinEscribir() && contenido(destinos).equals(Set.of("A3.txt"))
                        && enSwing(carpeta::getCarpeta).equals(carpetaInicial),
                conNo + "; avisos " + avisos(dialogos) + "; en la carpeta: " + contenido(destinos)
                        + "; recordada: " + enSwing(carpeta::getCarpeta));

        enSwing(() -> {
            dialogos.reemplazar = true;
            return null;
        });
        List<SerieEnVivo.Punto> alClic = enSwing(() -> a.grafica().getHistorial());
        Clic conSi = pulsarGuardar(g);
        Leido leido = leer(archivo);
        int distintas = distintasAlLeer(leido, alClic.size(), i -> alClic.get(i).tiempo(),
                i -> alClic.get(i).valor(), false);
        List<AvisoDado> avisos = avisos(dialogos);
        verificar("... con Sí, se reemplaza: queda solo lo nuevo (las 300 muestras de A3), con el aviso de éxito y "
                        + "ningún temporal",
                preguntas(dialogos).size() == 2 && conSi.escribio() && leido.filas().size() == 300 && distintas == 0
                        && avisos.size() == 1 && avisos.get(0).tipo() == Aviso.INFORMACION
                        && contenido(destinos).equals(Set.of("A3.txt")),
                leido.filas().size() + " filas, " + distintas + " distintas; avisos " + avisos);

        // Escrito sin extensión: el archivo que se usaría es "A3.txt", que ya existe
        byte[] reemplazado = Files.readAllBytes(archivo);
        enSwing(() -> {
            dialogos.reemplazar = false;
            dialogos.eleccion = nombre -> destinos.resolve("A3");
            return null;
        });
        pulsarGuardar(g);
        preguntas = preguntas(dialogos);
        verificar("... y si se escribe \"A3\", sin extensión, también se pregunta, porque ya existe \"A3.txt\" (con "
                        + "No, sigue igual)",
                preguntas.size() == 3 && preguntas.get(2).texto().contains("A3.txt")
                        && Arrays.equals(reemplazado, Files.readAllBytes(archivo))
                        && contenido(destinos).equals(Set.of("A3.txt")),
                "preguntas: " + preguntas + "; en la carpeta: " + contenido(destinos));
    }

    /**
     * Mientras se elige el archivo y se escribe, siguen llegando muestras,
     * como con el muestreo corriendo. Lo guardado debe ser exactamente lo que
     * había al pulsar. El historial es grande (150 000 muestras) para que la
     * escritura dure unas décimas de segundo y lleguen muestras durante ella.
     */
    private static void verificarMuestrasDuranteElGuardado() throws Exception {
        Path destinos = carpetaNueva("muestras-durante");
        int alPulsar = 150_000;
        AnalogicaDePrueba a = crearAnalogica(1);
        enviarAnalogica(a, 0, alPulsar);

        AtomicBoolean parar = new AtomicBoolean();
        AtomicInteger llegadas = new AtomicInteger();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread muestreo = new Thread(() -> {
            // Con pausa y con tope, para que el Timer de la gráfica no se
            // quede atrás (ver MUESTRAS_POR_CAMBIO en VerificacionSprint2)
            for (int i = alPulsar; !parar.get() && i < alPulsar + 50_000; i++) {
                a.grafica().muestraRecibida(muestraAnalogica(tiempo(i)));
                llegadas.incrementAndGet();
                pausar(5);
            }
        }, "Muestreador de prueba");
        muestreo.setUncaughtExceptionHandler(guardarEImprimir(error));
        DialogosDePrueba dialogos = new DialogosDePrueba(destinos::resolve);
        // Las muestras empiezan a llegar con la ventana de guardar "abierta":
        // después del clic, que ya tomó la copia, y antes de escribir
        dialogos.mientrasElige = muestreo::start;
        GuardadoDePrueba g = conGuardado(a.panel(), a.grafica(), new CarpetaRecordada(), dialogos);

        List<SerieEnVivo.Punto> alClic = enSwing(() -> a.grafica().getHistorial());
        Clic clic = pulsarGuardar(g);
        int llegaronMientras = llegadas.get();
        parar.set(true);
        muestreo.join();
        int alFinal = enSwing(() -> a.grafica().getHistorial()).size();

        Path archivo = destinos.resolve(pedidos(dialogos).get(0).nombre());
        Leido leido = leer(archivo);
        int distintas = distintasAlLeer(leido, alClic.size(), i -> alClic.get(i).tiempo(),
                i -> alClic.get(i).valor(), false);
        verificar(String.format("Se guarda lo que había al pulsar: %,d filas, iguales al historial en el momento del "
                                + "clic, aunque mientras se elegía el archivo y se escribía llegaron %,d muestras más "
                                + "(la gráfica ya tiene %,d)", leido.filas().size(), llegaronMientras, alFinal),
                error.get() == null && clic.escribio() && alClic.size() == alPulsar
                        && leido.filas().size() == alPulsar && distintas == 0 && llegaronMientras > 0
                        && alFinal > alPulsar,
                error.get() != null ? error.get().toString()
                        : clic + "; " + distintas + " distintas; " + llegaronMientras + " llegadas");
    }

    private static void verificarFallaAlGuardar() throws Exception {
        Path destinos = carpetaNueva("falla-al-guardar");
        Path sinCarpeta = destinos.resolve("no-existe").resolve("A3.txt");
        AnalogicaDePrueba a = crearAnalogica(3);
        enviarAnalogica(a, 0, 500);
        DialogosDePrueba dialogos = new DialogosDePrueba(nombre -> sinCarpeta);
        CarpetaRecordada carpeta = new CarpetaRecordada();
        Path carpetaInicial = enSwing(carpeta::getCarpeta);
        GuardadoDePrueba g = conGuardado(a.panel(), a.grafica(), carpeta, dialogos);

        Clic clic = pulsarGuardar(g);
        // El motivo que da el escritor para ese mismo destino, pedido directamente
        IOException delEscritor = falla(() -> EscritorArchivo.escribir(
                vacio("A3", HistorialSenal.Tipo.ANALOGICA), sinCarpeta));
        List<AvisoDado> avisos = avisos(dialogos);
        verificar("Falla al guardar (la carpeta elegida ya no existe): aviso de error en \"Guardar señal A3\" con el "
                        + "motivo que da el escritor (\"" + mensaje(delEscritor) + "\")",
                delEscritor != null && avisos.equals(List.of(
                        new AvisoDado(Aviso.ERROR, "Guardar señal A3", mensaje(delEscritor)))),
                "avisos: " + avisos);
        verificar("... no queda ningún archivo, el botón vuelve a la normalidad después de \"Guardando…\", y la "
                        + "carpeta recordada no cambia: la próxima vez la ventana no abre donde falló",
                clic.escribio() && contenido(destinos).isEmpty()
                        && enSwing(carpeta::getCarpeta).equals(carpetaInicial),
                clic + "; en la carpeta: " + contenido(destinos) + "; recordada: " + enSwing(carpeta::getCarpeta));

        if (!EN_WINDOWS) {
            System.out.println("(Se omite la falla con el archivo abierto en otro programa: solo se puede simular "
                    + "en Windows.)");
            return;
        }
        Path abierto = destinos.resolve("A3.txt");
        EscritorArchivo.escribir(analogicaAlAzar("A3", 1000, 30), abierto);
        byte[] antes = Files.readAllBytes(abierto);
        enSwing(() -> {
            dialogos.eleccion = nombre -> abierto;
            dialogos.reemplazar = true;
            return null;
        });
        // Como Excel: otro programa tiene el archivo abierto (ver verificarFallas())
        FileInputStream otroPrograma = new FileInputStream(abierto.toFile());
        Clic clicAbierto;
        try {
            clicAbierto = pulsarGuardar(g);
        } finally {
            otroPrograma.close();
        }
        avisos = avisos(dialogos);
        AvisoDado ultimo = avisos.get(avisos.size() - 1);
        verificar("Falla con el archivo abierto en otro programa (como Excel): aviso de error con el motivo del "
                        + "escritor, el archivo anterior queda intacto, ningún temporal, y el botón vuelve a la "
                        + "normalidad",
                avisos.size() == 2 && ultimo.tipo() == Aviso.ERROR
                        && ultimo.mensaje().contains("abierto en otro programa")
                        && Arrays.equals(antes, Files.readAllBytes(abierto))
                        && contenido(destinos).equals(Set.of("A3.txt")) && clicAbierto.escribio(),
                clicAbierto + "; último aviso: " + ultimo + "; en la carpeta: " + contenido(destinos));
    }

    /**
     * Las dos pestañas con una sola CarpetaRecordada y unos mismos diálogos,
     * como en Main.
     */
    private static void verificarCarpetaCompartida() throws Exception {
        Path x = carpetaNueva("carpeta-x");
        Path y = carpetaNueva("carpeta-y");
        Path documentos = FileSystemView.getFileSystemView().getDefaultDirectory().toPath();
        CarpetaRecordada carpeta = new CarpetaRecordada();
        DialogosDePrueba dialogos = new DialogosDePrueba(x::resolve);
        AnalogicaDePrueba a = crearAnalogica(3);
        DigitalDePrueba d = crearDigital(2);
        enviarAnalogica(a, 0, 100);
        enviarDigital(d, 0, 100, VerificacionSprint3::patron);
        GuardadoDePrueba ga = conGuardado(a.panel(), a.grafica(), carpeta, dialogos);
        GuardadoDePrueba gd = conGuardado(d.panel(), d.grafica(), carpeta, dialogos);

        pulsarGuardar(ga); // A3 se guarda en X
        enSwing(() -> {
            dialogos.eleccion = y::resolve;
            return null;
        });
        pulsarGuardar(gd); // D2 abre en X y se guarda en Y
        enSwing(() -> {
            dialogos.eleccion = nombre -> null;
            return null;
        });
        pulsarGuardar(ga); // A3 abre en Y y cancela
        pulsarGuardar(gd); // D2 sigue abriendo en Y

        List<Path> carpetas = new ArrayList<>();
        for (Pedido pedido : pedidos(dialogos)) {
            carpetas.add(pedido.carpeta());
        }
        verificar("La primera vez, la ventana de guardar abre en la carpeta Documentos del usuario (" + documentos + ")",
                !carpetas.isEmpty() && carpetas.get(0).equals(documentos) && Files.isDirectory(documentos),
                "abrió en " + carpetas);
        verificar("La carpeta usada se recuerda y se comparte entre las dos pestañas: después de guardar A3 en una "
                        + "carpeta, D2 abre en ella; después de guardar D2 en otra, A3 abre en esa; cancelar no la cambia",
                carpetas.equals(List.of(documentos, x, y, y))
                        && contenido(x).size() == 1 && contenido(y).size() == 1,
                "abrió en " + carpetas + "; en X: " + contenido(x) + "; en Y: " + contenido(y));
    }

    /** El JFileChooser que arma DialogosGuardadoSwing, revisado sin mostrarlo. */
    private static void verificarSelectorDeArchivos() throws Exception {
        Path carpeta = carpetaNueva("selector");
        String nombre = "A3_2026-10-04_15-30.txt";
        Selector s = enSwing(() -> Selector.de(DialogosGuardadoSwing.crearSelector("Guardar señal A3", carpeta,
                nombre), carpeta));
        verificar("El selector de archivos real (DialogosGuardadoSwing, sin mostrarlo) es una ventana de guardar con "
                        + "el título \"Guardar señal A3\"",
                s.tipo() == JFileChooser.SAVE_DIALOG && s.titulo().equals("Guardar señal A3"), s.toString());
        verificar("... con un solo filtro, \"Archivos de texto (*.txt)\", que muestra los .txt y no los .csv, sin "
                        + "\"Todos los archivos\"",
                s.filtros().equals(List.of("Archivos de texto (*.txt)")) && s.muestraTxt() && !s.muestraCsv()
                        && !s.todosLosArchivos(),
                s.toString());
        verificar("... abre en la carpeta pedida, con el nombre propuesto ya escrito en el campo del nombre",
                s.carpeta().equals(carpeta) && s.archivo().equals(nombre) && s.textos().contains(nombre),
                s.toString());
        verificar("... y en español aunque Java solo traiga sus textos en inglés: \"Guardar en:\", \"Nombre de "
                        + "archivo:\", \"Tipo de archivo:\", y los botones \"Guardar\" y \"Cancelar\"",
                s.etiquetas().containsAll(List.of("Guardar en:", "Nombre de archivo:", "Tipo de archivo:"))
                        && s.botones().containsAll(List.of("Guardar", "Cancelar"))
                        && !s.botones().contains("Save") && !s.botones().contains("Cancel"),
                "etiquetas " + s.etiquetas() + "; botones " + s.botones());

        Selector sinCarpeta = enSwing(() -> Selector.de(DialogosGuardadoSwing.crearSelector("Guardar señal A3",
                carpeta.resolve("ya-no-existe"), nombre), carpeta));
        verificar("... y si la carpeta recordada ya no existe, abre en la más cercana que sí existe",
                sinCarpeta.carpeta().equals(carpeta) && sinCarpeta.textos().contains(nombre),
                sinCarpeta.toString());
    }

    // ===================== Utilidades de I7LV-25 =====================

    /** Texto del botón en el formulario (PanelSenal.form). */
    private static final String TEXTO_BOTON = "Guardar esta señal…";

    /** Aviso cuando no hay datos, tal como lo pide la tarea. */
    private static final String SIN_DATOS = "No hay datos para guardar. Pulsa Iniciar para registrar la señal.";

    /** Pregunta cuando el archivo existe, tal como la pide la tarea. */
    private static final String PREGUNTA_REEMPLAZO = "¿Reemplazar el archivo existente?";

    /** Un pedido de elegir archivo. */
    private record Pedido(String titulo, Path carpeta, String nombre) {
    }

    /** Una pregunta de sí o no. */
    private record Pregunta(String titulo, String texto) {
    }

    /** Un aviso mostrado. */
    private record AvisoDado(Aviso tipo, String titulo, String mensaje) {
    }

    /**
     * DialogosGuardado de prueba: no abre ninguna ventana. Contesta lo que la
     * prueba le programa y anota lo que ControlGuardado le pidió. Solo se usa
     * en el hilo de Swing, donde lo llama ControlGuardado y donde la prueba
     * lo programa y lo lee (con enSwing()); cuenta además las veces que lo
     * llamaron desde otro hilo, que deben ser cero.
     */
    private static final class DialogosDePrueba implements DialogosGuardado {

        /** Lo que "elige" el usuario, a partir del nombre propuesto; null es Cancelar. */
        Function<String, Path> eleccion;

        /** Lo que contesta a "¿Reemplazar el archivo existente?". */
        boolean reemplazar = false;

        /** Qué hacer mientras la ventana de guardar está "abierta" (la copia ya se tomó). */
        Runnable mientrasElige = () -> {
        };

        final List<Pedido> pedidos = new ArrayList<>();
        final List<Pregunta> preguntas = new ArrayList<>();
        final List<AvisoDado> avisos = new ArrayList<>();
        int fueraDelHiloDeSwing = 0;

        DialogosDePrueba(Function<String, Path> eleccion) {
            this.eleccion = eleccion;
        }

        @Override
        public Path elegirArchivo(String titulo, Path carpetaInicial, String nombrePropuesto) {
            anotarHilo();
            pedidos.add(new Pedido(titulo, carpetaInicial, nombrePropuesto));
            mientrasElige.run();
            return eleccion.apply(nombrePropuesto);
        }

        @Override
        public boolean confirmar(String titulo, String pregunta) {
            anotarHilo();
            preguntas.add(new Pregunta(titulo, pregunta));
            return reemplazar;
        }

        @Override
        public void avisar(Aviso tipo, String titulo, String mensaje) {
            anotarHilo();
            avisos.add(new AvisoDado(tipo, titulo, mensaje));
        }

        private void anotarHilo() {
            if (!SwingUtilities.isEventDispatchThread()) {
                fueraDelHiloDeSwing++;
            }
        }
    }

    /** Una pestaña que nunca se muestra, con su ControlGuardado. */
    private record GuardadoDePrueba(PanelSenal panel, DialogosDePrueba dialogos) {

        JButton boton() {
            return panel.getBtnGuardar();
        }
    }

    /** Conecta, como Main, el botón de la pestaña con su gráfica, la carpeta y los diálogos. */
    private static GuardadoDePrueba conGuardado(PanelSenal panel, SenalGuardable grafica, CarpetaRecordada carpeta,
                                                DialogosDePrueba dialogos) throws Exception {
        enSwing(() -> new ControlGuardado(panel.getBtnGuardar(), grafica, carpeta, dialogos));
        return new GuardadoDePrueba(panel, dialogos);
    }

    /** Cómo está el botón en un instante. */
    private record EstadoBoton(boolean habilitado, String texto, int ancho, boolean tamanoFijado) {

        static EstadoBoton de(JButton boton) {
            return new EstadoBoton(boton.isEnabled(), boton.getText(), boton.getPreferredSize().width,
                    boton.isPreferredSizeSet());
        }

        boolean normal() {
            return habilitado && texto.equals(TEXTO_BOTON) && !tamanoFijado;
        }

        boolean guardando() {
            return !habilitado && texto.equals("Guardando…");
        }
    }

    /**
     * El botón antes del clic, en la misma tarea de Swing justo después del
     * clic (el SwingWorker no puede haber terminado: su done() también corre
     * en el hilo de Swing) y al final, cuando ya terminó el guardado.
     */
    private record Clic(EstadoBoton antes, EstadoBoton alPulsar, boolean termino, EstadoBoton alFinal) {

        /** No se escribió: el botón no cambió en ningún momento. */
        boolean sinEscribir() {
            return antes.normal() && alPulsar.normal() && termino && alFinal.normal();
        }

        /** Se escribió: "Guardando…" al pulsar, del mismo ancho, y normal al final. */
        boolean escribio() {
            return antes.normal() && alPulsar.guardando() && alPulsar.ancho() == antes.ancho()
                    && termino && alFinal.normal();
        }
    }

    private static Clic pulsarGuardar(GuardadoDePrueba g) throws Exception {
        return pulsarGuardar(g, 1);
    }

    /**
     * Pulsa el botón, las veces pedidas seguidas en la misma tarea de Swing,
     * y espera a que el guardado termine: el botón vuelve a estar habilitado
     * (como mucho 5 s). done() del SwingWorker habilita el botón y avisa en
     * una misma tarea, así que al volver el aviso ya está anotado.
     */
    private static Clic pulsarGuardar(GuardadoDePrueba g, int clics) throws Exception {
        EstadoBoton[] alPulsar = enSwing(() -> {
            JButton boton = g.boton();
            EstadoBoton antes = EstadoBoton.de(boton);
            boton.doClick(0);
            EstadoBoton despues = EstadoBoton.de(boton);
            for (int k = 1; k < clics; k++) {
                boton.doClick(0);
            }
            return new EstadoBoton[]{antes, despues};
        });
        boolean termino = esperarEnSwing(() -> g.boton().isEnabled());
        return new Clic(alPulsar[0], alPulsar[1], termino, enSwing(() -> EstadoBoton.de(g.boton())));
    }

    private static List<Pedido> pedidos(DialogosDePrueba d) throws Exception {
        return enSwing(() -> List.copyOf(d.pedidos));
    }

    private static List<Pregunta> preguntas(DialogosDePrueba d) throws Exception {
        return enSwing(() -> List.copyOf(d.preguntas));
    }

    private static List<AvisoDado> avisos(DialogosDePrueba d) throws Exception {
        return enSwing(() -> List.copyOf(d.avisos));
    }

    /** Si el nombre es el sugerido para ese canal en algún minuto entre antes y después. */
    private static boolean nombreSugeridoEntre(String nombre, String canal, LocalDateTime antes,
                                               LocalDateTime despues) {
        return nombre.equals(EscritorArchivo.nombreSugerido(canal, antes))
                || nombre.equals(EscritorArchivo.nombreSugerido(canal, despues));
    }

    /** Lo que se configuró en un JFileChooser, leído en el hilo de Swing. */
    private record Selector(int tipo, String titulo, List<String> filtros, boolean muestraTxt, boolean muestraCsv,
                            boolean todosLosArchivos, Path carpeta, String archivo, List<String> textos,
                            List<String> etiquetas, List<String> botones) {

        static Selector de(JFileChooser s, Path carpetaDePrueba) {
            List<String> filtros = new ArrayList<>();
            for (FileFilter filtro : s.getChoosableFileFilters()) {
                filtros.add(filtro.getDescription());
            }
            File txt = carpetaDePrueba.resolve("x.txt").toFile();
            File csv = carpetaDePrueba.resolve("x.csv").toFile();
            return new Selector(s.getDialogType(), s.getDialogTitle(), filtros, s.getFileFilter().accept(txt),
                    s.getFileFilter().accept(csv), s.isAcceptAllFileFilterUsed(),
                    s.getCurrentDirectory().toPath(), s.getSelectedFile().getName(),
                    textosDe(s, JTextField.class, JTextField::getText),
                    textosDe(s, JLabel.class, JLabel::getText),
                    textosDe(s, JButton.class, JButton::getText));
        }
    }

    /**
     * Los textos de todos los componentes de ese tipo dentro del contenedor
     * (en el JFileChooser: el campo del nombre, las etiquetas y los botones).
     */
    private static <T extends Component> List<String> textosDe(Container contenedor, Class<T> tipo,
                                                              Function<T, String> texto) {
        List<String> textos = new ArrayList<>();
        for (Component c : contenedor.getComponents()) {
            if (tipo.isInstance(c) && texto.apply(tipo.cast(c)) != null) {
                textos.add(texto.apply(tipo.cast(c)));
            }
            if (c instanceof Container hijo) {
                textos.addAll(textosDe(hijo, tipo, texto));
            }
        }
        return textos;
    }

    /**
     * Espera a que la condición, revisada en el hilo de Swing, se cumpla.
     * Espera como mucho 5 s.
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

    // ===================== Archivo de ejemplo =====================

    /**
     * Escribe en la carpeta temporal del sistema (que no se borra) un archivo
     * con 20 filas analógicas, como las que daría el canal A3 muestreado cada
     * 500 ms, para abrirlo con el Bloc de notas o con Excel. Su ruta se
     * muestra al final del resultado.
     */
    private static void escribirEjemplo() throws IOException {
        int filas = 20;
        double[] tiempos = new double[filas];
        double[] voltajes = new double[filas];
        Random azar = new Random(20);
        for (int i = 0; i < filas; i++) {
            // Cada 500 ms, con el pequeño retraso del Muestreador
            tiempos[i] = i * 0.5 + 0.0001 + azar.nextDouble() * 0.0004;
            voltajes[i] = 2.5 + 1.8 * Math.sin(2 * Math.PI * tiempos[i] / 8.0) + azar.nextDouble() * 0.05;
        }
        Path carpeta = Path.of(System.getProperty("java.io.tmpdir"));
        Path archivo = carpeta.resolve(EscritorArchivo.nombreSugerido("A3", LocalDateTime.now()));
        EscritorArchivo.escribir(new HistorialSenal("A3", HistorialSenal.Tipo.ANALOGICA, tiempos, voltajes),
                archivo);
        Leido leido = leer(archivo);
        verificar("Se escribió el archivo de ejemplo: 20 filas analógicas del canal A3 con el formato de siempre "
                        + "(la ruta está al final)",
                leido.filas().size() == filas && noCumplen(leido.filas(), FILA_ANALOGICA) == 0
                        && saltosDeWindows(leido),
                leido.filas().size() + " filas");
        archivoEjemplo = archivo.toAbsolutePath();
    }

    // ===================== Utilidades de las gráficas =====================

    /** Una gráfica analógica real dentro de su pestaña, que nunca se muestra. */
    private record AnalogicaDePrueba(PanelSenal panel, GraficaAnalogica grafica) {
    }

    /** Una gráfica digital real dentro de su pestaña, que nunca se muestra. */
    private record DigitalDePrueba(PanelSenal panel, GraficaDigital grafica) {
    }

    /** Crea, en el hilo de Swing, una pestaña con los canales A0 a A7 y su gráfica. */
    private static AnalogicaDePrueba crearAnalogica(int canal) throws Exception {
        return enSwing(() -> {
            PanelSenal panel = new PanelSenal(nombres("A", CANALES));
            return new AnalogicaDePrueba(panel, new GraficaAnalogica(MUESTREADOR, panel, canal));
        });
    }

    /** Crea, en el hilo de Swing, una pestaña con D0 a D3 y su gráfica digital. */
    private static DigitalDePrueba crearDigital(int canal) throws Exception {
        return enSwing(() -> {
            PanelSenal panel = new PanelSenal(nombres("D", CANALES_DIGITALES));
            return new DigitalDePrueba(panel, new GraficaDigital(MUESTREADOR, panel, canal));
        });
    }

    /** Envía las muestras desde (incluida) hasta (excluida), a 10 ms, como el Muestreador. */
    private static void enviarAnalogica(AnalogicaDePrueba g, int desde, int hasta) {
        for (int i = desde; i < hasta; i++) {
            g.grafica().muestraRecibida(muestraAnalogica(tiempo(i)));
        }
    }

    /** Envía las muestras desde (incluida) hasta (excluida), a 10 ms, con esos bits. */
    private static void enviarDigital(DigitalDePrueba g, int desde, int hasta, IntUnaryOperator bits) {
        for (int i = desde; i < hasta; i++) {
            g.grafica().muestraRecibida(muestraDigital(tiempo(i), bits.applyAsInt(i)));
        }
    }

    /** Lo que se midió en una prueba de concurrencia. */
    private record Concurrencia(long copias, long copiasConDatos, int incoherentes, String primeraIncoherente,
                                int canalesVistos, int bienTrasCambio, long muestras, Throwable error) {
    }

    /**
     * Prueba de concurrencia de getHistorialParaGuardar(), igual para las dos
     * gráficas. Tres hilos a la vez:
     * - uno envía muestras (1 ms de tiempo por muestra, con un nuevo Iniciar
     *   cada MUESTRAS_POR_CORRIDA, así el historial no crece sin límite), con
     *   una pausa de 2 µs por muestra para que el Timer de la gráfica no se
     *   quede atrás (ver MUESTRAS_POR_CAMBIO en VerificacionSprint2);
     * - otro toma copias para guardar sin parar y revisa cada una;
     * - el hilo de Swing cambia de canal 1000 veces, con pausas cortas al
     *   azar, y en la misma tarea toma una copia y revisa que sea del canal
     *   nuevo.
     *
     * @param grafica  una de las dos gráficas del programa
     * @param letra    letra de sus canales: "A" o "D"
     * @param canales  cantidad de canales
     * @param envio    envía a la gráfica la muestra número i de una corrida
     * @param revision si una copia es coherente, sabiendo solo lo que trae
     * @param semilla  de los canales y las pausas al azar
     */
    private static <G extends SenalGuardable & CanalSeleccionable> Concurrencia concurrencia(
            G grafica, String letra, int canales, LongConsumer envio, Predicate<HistorialSenal> revision,
            long semilla) throws Exception {
        AtomicBoolean corriendo = new AtomicBoolean(true);
        AtomicLong enviadas = new AtomicLong();
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread productor = new Thread(() -> {
            long i = 0;
            while (corriendo.get()) {
                envio.accept(i % MUESTRAS_POR_CORRIDA);
                i++;
                pausar(2);
            }
            enviadas.set(i);
        }, "Muestreador de prueba");
        productor.setUncaughtExceptionHandler(guardarEImprimir(error));

        AtomicLong copias = new AtomicLong();
        AtomicLong conDatos = new AtomicLong();
        AtomicInteger incoherentes = new AtomicInteger();
        AtomicReference<String> primeraIncoherente = new AtomicReference<>("");
        Set<String> canalesVistos = ConcurrentHashMap.newKeySet();
        Thread lector = new Thread(() -> {
            while (corriendo.get()) {
                HistorialSenal copia = grafica.getHistorialParaGuardar();
                copias.incrementAndGet();
                if (copia.getCantidad() > 0) {
                    conDatos.incrementAndGet();
                    canalesVistos.add(copia.getCanal());
                }
                if (!revision.test(copia)) {
                    incoherentes.incrementAndGet();
                    primeraIncoherente.compareAndSet("", describir(copia));
                }
            }
        }, "Lector de copias");
        lector.setUncaughtExceptionHandler(guardarEImprimir(error));

        Random azar = new Random(semilla);
        int canal = enSwing(grafica::getCanal);
        int bienTrasCambio = 0;
        productor.start();
        lector.start();
        try {
            for (int k = 0; k < CAMBIOS; k++) {
                // Siempre un canal distinto del actual
                int nuevo = (canal + 1 + azar.nextInt(canales - 1)) % canales;
                boolean bien = enSwing(() -> {
                    grafica.cambiarCanal(nuevo);
                    HistorialSenal copia = grafica.getHistorialParaGuardar();
                    return copia.getCanal().equals(letra + nuevo) && revision.test(copia);
                });
                if (bien) {
                    bienTrasCambio++;
                }
                canal = nuevo;
                pausar(azar.nextInt(1000));
            }
        } finally {
            corriendo.set(false);
            productor.join();
            lector.join();
        }
        return new Concurrencia(copias.get(), conDatos.get(), incoherentes.get(), primeraIncoherente.get(),
                canalesVistos.size(), bienTrasCambio, enviadas.get(), error.get());
    }

    /**
     * Copia analógica coherente: todos sus voltajes son los del canal que
     * dice su nombre (el canal i vale i × 0,5 V) y los tiempos avanzan.
     */
    private static boolean analogicaCoherente(HistorialSenal copia) {
        int canal = Integer.parseInt(copia.getCanal().substring(1));
        for (int i = 0; i < copia.getCantidad(); i++) {
            if (copia.getValor(i) != valorCanal(canal)
                    || (i > 0 && copia.getTiempo(i) <= copia.getTiempo(i - 1))) {
                return false;
            }
        }
        return copia.getTipo() == HistorialSenal.Tipo.ANALOGICA;
    }

    /**
     * Copia digital coherente: cada valor es el bit, en la muestra de ese
     * tiempo, de la señal que dice su nombre, y los tiempos avanzan.
     */
    private static boolean digitalCoherente(HistorialSenal copia) {
        int canal = Integer.parseInt(copia.getCanal().substring(1));
        for (int i = 0; i < copia.getCantidad(); i++) {
            long j = Math.round(copia.getTiempo(i) * 1000);
            if (copia.getValor(i) != ((patronRapido(j) >> canal) & 1)
                    || (i > 0 && copia.getTiempo(i) <= copia.getTiempo(i - 1))) {
                return false;
            }
        }
        return copia.getTipo() == HistorialSenal.Tipo.DIGITAL;
    }

    /** Si la copia tiene exactamente los mismos puntos que el historial de la gráfica. */
    private static boolean mismosPuntos(HistorialSenal copia, List<SerieEnVivo.Punto> historial) {
        if (copia.getCantidad() != historial.size()) {
            return false;
        }
        for (int i = 0; i < historial.size(); i++) {
            if (copia.getTiempo(i) != historial.get(i).tiempo() || copia.getValor(i) != historial.get(i).valor()) {
                return false;
            }
        }
        return true;
    }

    private static boolean soloVale(HistorialSenal copia, double valor) {
        for (int i = 0; i < copia.getCantidad(); i++) {
            if (copia.getValor(i) != valor) {
                return false;
            }
        }
        return true;
    }

    /**
     * Cuántos puntos de la copia no son los esperados: el punto i debe ser la
     * muestra i (tiempo a 10 ms) con el bit del canal.
     */
    private static int bitsDistintos(HistorialSenal copia, int canal, IntUnaryOperator bits) {
        int distintos = 0;
        for (int i = 0; i < copia.getCantidad(); i++) {
            if (copia.getTiempo(i) != tiempo(i) || copia.getValor(i) != ((bits.applyAsInt(i) >> canal) & 1)) {
                distintos++;
            }
        }
        return distintos;
    }

    /** Muestra sintética: el canal analógico i vale i × 0,5 V. */
    private static Muestra muestraAnalogica(double tiempo) {
        return new Muestra(tiempo, ANALOGICAS, SIN_DIGITALES);
    }

    /** Muestra sintética con voltajes que cambian con el tiempo: una senoidal distinta por canal. */
    private static Muestra muestraVariable(double tiempo) {
        double[] voltajes = new double[CANALES];
        for (int canal = 0; canal < CANALES; canal++) {
            voltajes[canal] = 2.5 + 2.4 * Math.sin(2 * Math.PI * 0.5 * tiempo + canal);
        }
        return new Muestra(tiempo, voltajes, SIN_DIGITALES);
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
     * Bits de la muestra número i: un contador que avanza cada 7 muestras.
     * D0 cambia cada 7 muestras, D1 cada 14, D2 cada 28 y D3 cada 56, así el
     * historial de cada señal es distinto.
     */
    private static int patron(int i) {
        return (i / 7) & 0b1111;
    }

    /** Bits de la muestra j en la prueba de concurrencia: pseudoaleatorios, con muchos cambios. */
    private static int patronRapido(long j) {
        return (int) ((j * 0x9E3779B97F4A7C15L) >>> 60);
    }

    /** Tiempo de la muestra número i, a 10 ms entre muestras. */
    private static double tiempo(int i) {
        return i / 100.0;
    }

    /** Tiempo de la muestra número i de las pruebas de concurrencia: 1 ms entre muestras. */
    private static double tiempoRapido(long i) {
        return i / 1000.0;
    }

    private static double valorCanal(int canal) {
        return canal * 0.5;
    }

    private static String[] nombres(String letra, int cantidad) {
        String[] nombres = new String[cantidad];
        for (int i = 0; i < cantidad; i++) {
            nombres[i] = letra + i;
        }
        return nombres;
    }

    /** Los nombres de todos los canales del programa: A0 a A7 y D0 a D3. */
    private static List<String> nombresCanalesDelPrograma() {
        List<String> nombres = new ArrayList<>(Arrays.asList(nombres("A", CANALES)));
        nombres.addAll(Arrays.asList(nombres("D", CANALES_DIGITALES)));
        return nombres;
    }

    private static String describir(HistorialSenal h) {
        return "canal \"" + h.getCanal() + "\", " + h.getTipo() + ", " + h.getCantidad() + " muestras";
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

    /** Espera activa de unos microsegundos (Thread.sleep no baja de 1 ms). */
    private static void pausar(int microsegundos) {
        long hasta = System.nanoTime() + microsegundos * 1000L;
        while (System.nanoTime() < hasta) {
            Thread.onSpinWait();
        }
    }

    /**
     * Fuente que nunca se usa: el Muestreador de las pruebas no se inicia. Si
     * algo la llamara, sería un error de la prueba.
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

    // ===================== Utilidades de los archivos =====================

    /**
     * Lo que se leyó de un archivo: sus bytes, el texto en UTF-8, las filas
     * (lo que hay antes de cada \r\n) y lo que queda después del último
     * \r\n, que debe ser nada.
     */
    private record Leido(byte[] bytes, String texto, List<String> filas, String resto) {
    }

    private static Leido leer(Path archivo) throws IOException {
        byte[] bytes = Files.readAllBytes(archivo);
        String texto = new String(bytes, StandardCharsets.UTF_8);
        List<String> filas = new ArrayList<>();
        int desde = 0;
        int fin;
        while ((fin = texto.indexOf("\r\n", desde)) >= 0) {
            filas.add(texto.substring(desde, fin));
            desde = fin + 2;
        }
        return new Leido(bytes, texto, filas, texto.substring(desde));
    }

    /** Si todas las filas terminan en \r\n, también la última, y no hay \r ni \n sueltos. */
    private static boolean saltosDeWindows(Leido leido) {
        if (!leido.resto().isEmpty() || !leido.texto().endsWith("\r\n")) {
            return false;
        }
        for (String fila : leido.filas()) {
            if (fila.indexOf('\r') >= 0 || fila.indexOf('\n') >= 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean soloBytesPermitidos(byte[] bytes) {
        for (byte b : bytes) {
            if (b < 0 || BYTES_PERMITIDOS.indexOf(b) < 0) {
                return false;
            }
        }
        return true;
    }

    private static String primerosBytes(byte[] bytes) {
        StringBuilder texto = new StringBuilder();
        for (int i = 0; i < Math.min(4, bytes.length); i++) {
            texto.append(String.format("%02X ", bytes[i]));
        }
        return texto.toString().strip();
    }

    /** Cuántos \r\n tiene el archivo: sus filas. */
    private static int contarFinesDeFila(byte[] bytes) {
        int cuenta = 0;
        for (int i = 1; i < bytes.length; i++) {
            if (bytes[i] == '\n' && bytes[i - 1] == '\r') {
                cuenta++;
            }
        }
        return cuenta;
    }

    private static int noCumplen(List<String> filas, Pattern formato) {
        int cuenta = 0;
        for (String fila : filas) {
            if (!formato.matcher(fila).matches()) {
                cuenta++;
            }
        }
        return cuenta;
    }

    private static String primeraQueNoCumple(List<String> filas, Pattern formato) {
        for (String fila : filas) {
            if (!formato.matcher(fila).matches()) {
                return "\"" + visible(fila) + "\"";
            }
        }
        return "ninguna";
    }

    private static String primera(Leido leido) {
        return leido.filas().isEmpty() ? "(sin filas)" : leido.filas().get(0);
    }

    /**
     * La fila que debería tener el archivo para la muestra i, armada aquí
     * por otro camino: String.format con Locale.ROOT (punto decimal).
     */
    private static String filaEsperada(HistorialSenal historial, int i) {
        String valor = historial.getTipo() == HistorialSenal.Tipo.DIGITAL
                ? (historial.getValor(i) == 1.0 ? "1" : "0")
                : tresDecimales(historial.getValor(i));
        return tresDecimales(historial.getTiempo(i)) + "\t" + valor;
    }

    /**
     * El número con 3 decimales según String.format con Locale.ROOT, la
     * referencia de las pruebas. Solo se corrige "-0.000", que el escritor
     * escribe 0.000.
     */
    private static String tresDecimales(double numero) {
        String texto = String.format(Locale.ROOT, "%.3f", numero);
        return texto.equals("-0.000") ? "0.000" : texto;
    }

    /**
     * Lee de vuelta cada fila del archivo y cuenta las que no dan el tiempo y
     * el valor originales, redondeados a 3 decimales (o 0 y 1 en la
     * digital). Además de comparar con la referencia, revisa que el número
     * leído no se aleje del original más de media milésima. Una cantidad de
     * filas distinta cuenta como una diferencia más.
     */
    private static int distintasAlLeer(Leido leido, int cantidad, IntToDoubleFunction tiempo,
                                       IntToDoubleFunction valor, boolean digital) {
        int distintas = leido.filas().size() == cantidad ? 0 : 1;
        for (int i = 0; i < Math.min(cantidad, leido.filas().size()); i++) {
            String[] campos = leido.filas().get(i).split("\t", -1);
            if (campos.length != 2 || !mismoNumero(campos[0], tiempo.applyAsDouble(i))) {
                distintas++;
            } else if (digital) {
                if (!campos[1].equals(valor.applyAsDouble(i) == 1.0 ? "1" : "0")) {
                    distintas++;
                }
            } else if (!mismoNumero(campos[1], valor.applyAsDouble(i))) {
                distintas++;
            }
        }
        return distintas;
    }

    /** Si el texto leído es el número original redondeado a 3 decimales. */
    private static boolean mismoNumero(String leido, double original) {
        try {
            BigDecimal numero = new BigDecimal(leido);
            return numero.compareTo(new BigDecimal(tresDecimales(original))) == 0
                    && Math.abs(numero.doubleValue() - original) <= 0.0005 + 1e-9;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** El texto con las tabulaciones y los saltos de línea a la vista. */
    private static String visible(String texto) {
        return texto.replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n");
    }

    /** Historial analógico al azar: cada 10 ms con un pequeño retraso, como el Muestreador, entre 0 y 5 V. */
    private static HistorialSenal analogicaAlAzar(String canal, int cantidad, long semilla) {
        Random azar = new Random(semilla);
        double[] tiempos = new double[cantidad];
        double[] voltajes = new double[cantidad];
        for (int i = 0; i < cantidad; i++) {
            tiempos[i] = i * 0.01 + azar.nextDouble() * 0.001;
            voltajes[i] = azar.nextDouble() * 5.0;
        }
        return new HistorialSenal(canal, HistorialSenal.Tipo.ANALOGICA, tiempos, voltajes);
    }

    /** Historial digital al azar: cada 10 ms, con pulsos (cambia en una de cada 4 muestras). */
    private static HistorialSenal digitalAlAzar(String canal, int cantidad, long semilla) {
        Random azar = new Random(semilla);
        double[] tiempos = new double[cantidad];
        double[] bits = new double[cantidad];
        double actual = 0;
        for (int i = 0; i < cantidad; i++) {
            tiempos[i] = i * 0.01 + azar.nextDouble() * 0.001;
            if (azar.nextInt(4) == 0) {
                actual = 1 - actual;
            }
            bits[i] = actual;
        }
        return new HistorialSenal(canal, HistorialSenal.Tipo.DIGITAL, tiempos, bits);
    }

    private static HistorialSenal vacio(String canal, HistorialSenal.Tipo tipo) {
        return new HistorialSenal(canal, tipo, new double[0], new double[0]);
    }

    /** Escribe y devuelve cuánto tardó, en segundos. */
    private static double medirEscritura(HistorialSenal historial, Path archivo) throws IOException {
        long inicio = System.nanoTime();
        EscritorArchivo.escribir(historial, archivo);
        return (System.nanoTime() - inicio) / 1e9;
    }

    /** Una escritura que puede fallar. */
    @FunctionalInterface
    private interface Escritura {
        void escribir() throws IOException;
    }

    /** Ejecuta la escritura y devuelve la IOException que lanzó, o null si no falló. */
    private static IOException falla(Escritura escritura) {
        try {
            escritura.escribir();
            return null;
        } catch (IOException e) {
            return e;
        }
    }

    private static String mensaje(IOException e) {
        return e == null ? "" : String.valueOf(e.getMessage());
    }

    /** Si crear el historial se rechaza con IllegalArgumentException. */
    private static boolean rechaza(Runnable crear) {
        try {
            crear.run();
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    /** Crea una carpeta nueva y vacía dentro de la carpeta temporal de las pruebas. */
    private static Path carpetaNueva(String nombre) throws IOException {
        return Files.createDirectory(carpetaPruebas.resolve(nombre));
    }

    /** Los nombres de lo que hay en la carpeta. */
    private static Set<String> contenido(Path carpeta) throws IOException {
        Set<String> nombres = new TreeSet<>();
        try (Stream<Path> archivos = Files.list(carpeta)) {
            archivos.forEach(p -> nombres.add(p.getFileName().toString()));
        }
        return nombres;
    }

    /**
     * Borra la carpeta temporal de las pruebas con todo lo que tiene. Si algo
     * no se puede borrar, lo dice y sigue: no debe tapar el resultado.
     */
    private static void borrarCarpetaPruebas() {
        if (carpetaPruebas == null) {
            return;
        }
        List<Path> todo = new ArrayList<>();
        try (Stream<Path> archivos = Files.walk(carpetaPruebas)) {
            archivos.sorted(Comparator.reverseOrder()).forEach(todo::add);
        } catch (IOException e) {
            System.out.println("(No se pudo recorrer la carpeta temporal de las pruebas " + carpetaPruebas
                    + ": " + e + ")");
            return;
        }
        for (Path p : todo) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException e) {
                System.out.println("(No se pudo borrar " + p + ": " + e + ")");
            }
        }
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
