package laboratoriovirtual.control;

import java.awt.Dimension;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutionException;
import javax.swing.JButton;
import javax.swing.SwingWorker;
import laboratoriovirtual.almacenamiento.EscritorArchivo;
import laboratoriovirtual.almacenamiento.HistorialSenal;
import laboratoriovirtual.control.DialogosGuardado.Aviso;

/**
 * Guarda en un archivo la señal seleccionada de una pestaña (I7LV-25, R3 y
 * R6): conecta el botón "Guardar esta señal…" con la gráfica de la pestaña
 * y con EscritorArchivo.
 *
 * Al pulsar el botón, en el hilo de Swing:
 * 1. Toma de la gráfica el canal y su historial, juntos
 *    (getHistorialParaGuardar()).
 * 2. Si no hay muestras, avisa y termina: no se crea ningún archivo.
 * 3. Pregunta dónde guardar, con el título "Guardar señal A3", el nombre
 *    sugerido y la carpeta donde se guardó la última vez (CarpetaRecordada,
 *    la misma para las dos pestañas; al comenzar, Documentos). Si el nombre
 *    elegido no tiene extensión, le agrega ".txt".
 * 4. Si el usuario cancela, termina.
 * 5. Si el archivo ya existe, pregunta si se reemplaza; con No, termina.
 * 6. Escribe el archivo FUERA del hilo de Swing, con un SwingWorker: una
 *    hora de muestras tarda unas décimas de segundo, y en una memoria USB o
 *    un disco de red puede tardar más, y la ventana no debe congelarse. El
 *    muestreo y las gráficas siguen mientras tanto. El botón queda
 *    deshabilitado y dice "Guardando…", así no se puede pedir otro
 *    guardado de la misma pestaña a la vez; al terminar vuelve a como
 *    estaba, haya salido bien o mal.
 * 7. Si salió bien, recuerda la carpeta y avisa "Se guardaron N muestras de
 *    A3 en <ruta>". Si falló, avisa el motivo que da EscritorArchivo, que no
 *    deja ningún archivo a medias (el anterior, si había, queda intacto), y
 *    la carpeta recordada no cambia.
 *
 * Qué se guarda: exactamente lo que había al pulsar el botón. La copia del
 * historial se toma en el paso 1 y es inmutable (HistorialSenal): las
 * muestras que lleguen mientras el usuario elige el archivo, o mientras se
 * escribe, no entran. Quedan en la gráfica y entrarán en el próximo
 * guardado.
 *
 * Sirve igual para las dos pestañas: solo conoce el botón, una
 * SenalGuardable (GraficaAnalogica o GraficaDigital), la CarpetaRecordada y
 * DialogosGuardado. No abre ventanas por su cuenta: todas pasan por
 * DialogosGuardado, así VerificacionSprint3 lo prueba sin ventanas.
 */
public class ControlGuardado {

    /** Texto del botón mientras se escribe el archivo. */
    private static final String TEXTO_GUARDANDO = "Guardando…";

    /** Aviso cuando la gráfica no tiene muestras. */
    private static final String SIN_DATOS = "No hay datos para guardar. Pulsa Iniciar para registrar la señal.";

    /** Pregunta cuando el archivo elegido ya existe. */
    private static final String PREGUNTA_REEMPLAZO = "¿Reemplazar el archivo existente?";

    /** Extensión que se agrega si el nombre elegido no tiene ninguna. */
    private static final String EXTENSION = ".txt";

    private final JButton boton;
    private final SenalGuardable grafica;
    private final CarpetaRecordada carpeta;
    private final DialogosGuardado dialogos;

    // Texto del botón en el formulario ("Guardar esta señal…"), para volver
    // a él al terminar de guardar
    private final String textoBoton;

    /**
     * Conecta el botón con el guardado. Se llama desde el hilo de Swing.
     *
     * @param boton    botón "Guardar esta señal…" de la pestaña
     * @param grafica  gráfica de esa misma pestaña
     * @param carpeta  última carpeta donde se guardó, compartida con la otra pestaña
     * @param dialogos ventanas para elegir el archivo, preguntar y avisar
     */
    public ControlGuardado(JButton boton, SenalGuardable grafica, CarpetaRecordada carpeta,
                           DialogosGuardado dialogos) {
        this.boton = boton;
        this.grafica = grafica;
        this.carpeta = carpeta;
        this.dialogos = dialogos;
        textoBoton = boton.getText();

        boton.addActionListener(e -> guardar());
    }

    // ===================== Acción del usuario =====================
    // Se ejecuta en el hilo de Swing (viene de un clic).

    private void guardar() {
        // 1. Lo que se guarda es lo que hay ahora, en el momento del clic
        HistorialSenal copia = grafica.getHistorialParaGuardar();
        String titulo = "Guardar señal " + copia.getCanal();

        // 2. Sin muestras no se abre la ventana de guardar
        if (copia.getCantidad() == 0) {
            dialogos.avisar(Aviso.ADVERTENCIA, titulo, SIN_DATOS);
            return;
        }

        // 3. y 4. Dónde guardar; null si canceló
        Path elegido = dialogos.elegirArchivo(titulo, carpeta.getCarpeta(),
                EscritorArchivo.nombreSugerido(copia.getCanal(), LocalDateTime.now()));
        if (elegido == null) {
            return;
        }
        Path destino = conExtension(elegido.toAbsolutePath());

        // 5. Después de agregar la extensión: si escribió "A3" y ya existe
        // "A3.txt", también se pregunta
        if (Files.isRegularFile(destino)
                && !dialogos.confirmar(titulo, "Ya existe el archivo \"" + destino.getFileName()
                        + "\".\n" + PREGUNTA_REEMPLAZO)) {
            return;
        }

        // 6. y 7.
        escribirAparte(copia, destino, titulo);
    }

    // ===================== Escritura =====================

    /**
     * Escribe el archivo con un SwingWorker: doInBackground() corre en otro
     * hilo y done() vuelve al de Swing. Mientras tanto, el botón queda
     * deshabilitado y dice "Guardando…".
     */
    private void escribirAparte(HistorialSenal copia, Path destino, String titulo) {
        // El botón conserva su tamaño mientras dice "Guardando…", que es más
        // corto: así el pie de la pestaña no se reacomoda dos veces
        Dimension tamanoFijado = boton.isPreferredSizeSet() ? boton.getPreferredSize() : null;
        boton.setPreferredSize(boton.getPreferredSize());
        boton.setText(TEXTO_GUARDANDO);
        boton.setEnabled(false);

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws IOException {
                EscritorArchivo.escribir(copia, destino);
                return null;
            }

            @Override
            protected void done() {
                // Primero el botón vuelve a como estaba, haya salido bien o
                // mal; después el aviso, que espera a que el usuario lo cierre
                boton.setText(textoBoton);
                boton.setPreferredSize(tamanoFijado);
                boton.setEnabled(true);
                try {
                    get();
                    // Solo una carpeta donde de verdad se guardó: si falló
                    // (por ejemplo, en C:\Windows), la próxima vez la ventana
                    // no vuelve a abrir ahí
                    carpeta.recordar(destino.getParent());
                    dialogos.avisar(Aviso.INFORMACION, titulo, mensajeGuardado(copia, destino));
                } catch (ExecutionException e) {
                    dialogos.avisar(Aviso.ERROR, titulo, motivo(e.getCause(), destino));
                } catch (InterruptedException e) {
                    // No pasa: done() llega cuando doInBackground() ya terminó
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    // ===================== Utilidades =====================

    /**
     * El archivo con ".txt" al final si su nombre no tiene extensión. Tiene
     * extensión si después del último punto hay al menos un carácter y
     * ningún espacio: "datos" y "Prueba 2.5 V" pasan a "datos.txt" y
     * "Prueba 2.5 V.txt"; "datos.txt" y "datos.csv" quedan igual. Antes se
     * quitan los puntos y espacios del final, que Windows no admite: "datos."
     * pasa a "datos.txt".
     */
    private static Path conExtension(Path archivo) {
        String nombre = archivo.getFileName().toString().replaceAll("[. ]+$", "");
        int punto = nombre.lastIndexOf('.');
        boolean tieneExtension = punto > 0 && punto < nombre.length() - 1
                && nombre.indexOf(' ', punto) < 0;
        return archivo.resolveSibling(tieneExtension ? nombre : nombre + EXTENSION);
    }

    /**
     * "Se guardaron 1.234 muestras de A3 en C:\…\A3_2026-10-04_15-30.txt".
     * La cantidad lleva el separador de miles de la barra de estado.
     */
    private static String mensajeGuardado(HistorialSenal copia, Path destino) {
        int cantidad = copia.getCantidad();
        return (cantidad == 1 ? "Se guardó 1 muestra" : "Se guardaron "
                + ControlMuestreo.conSeparadorDeMiles(cantidad) + " muestras")
                + " de " + copia.getCanal() + " en " + destino;
    }

    /**
     * El motivo de la falla. EscritorArchivo solo lanza IOException, con un
     * mensaje en español listo para mostrar; cualquier otra cosa sería un
     * error del programa, y se muestra igual para no callarlo.
     */
    private static String motivo(Throwable causa, Path destino) {
        if (causa instanceof IOException) {
            return causa.getMessage();
        }
        return "No se pudo guardar el archivo \"" + destino.getFileName()
                + "\" por un error inesperado: " + causa;
    }
}
