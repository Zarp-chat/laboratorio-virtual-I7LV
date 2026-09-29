package laboratoriovirtual.almacenamiento;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Escribe el historial de una señal en un archivo de texto (R3 y R6).
 *
 * Formato (lo exige el Laboratorio 2):
 * - una fila por muestra: tiempo, tabulación, valor; sin fila de encabezado;
 * - tiempo en segundos con 3 decimales;
 * - valor analógico en voltios con 3 decimales; valor digital 0 o 1, sin
 *   decimales;
 * - punto decimal siempre, aunque el equipo use la coma (por ejemplo, con la
 *   configuración regional de Colombia): nada aquí depende del Locale;
 * - cada fila termina con el salto de línea de Windows (\r\n), también la
 *   última;
 * - UTF-8 sin BOM. Como solo hay dígitos, punto, signo menos, tabulación y
 *   saltos de línea, el archivo es también ASCII.
 * Ejemplo de fila: 0.500\t3.720
 *
 * Redondeo a 3 decimales: al más cercano, y en un empate hacia arriba (lejos
 * del cero). Se redondea el número tal como Java lo escribe
 * (Double.toString), así que 1.0005 da 1.001: lo mismo que
 * String.format("%.3f") con punto decimal. Única diferencia: un valor que
 * redondea a cero se escribe 0.000, nunca -0.000.
 *
 * Guardado seguro: primero se escribe un archivo temporal en la misma
 * carpeta ("~" + nombre + un número + ".tmp") y, solo si todo salió bien,
 * se le da el nombre final, reemplazando el archivo anterior si existía. Ese
 * cambio de nombre es un solo paso: quien abra el archivo ve el anterior
 * completo o el nuevo completo, nunca uno a medias. Si algo falla, se borra
 * el temporal, el archivo anterior queda intacto y se lanza una IOException
 * con un mensaje en español que se puede mostrar tal cual al usuario. Si
 * Windows niega el reemplazo, se reintenta durante medio segundo antes de
 * darlo por fallido (ver reemplazar()).
 *
 * Historial vacío: se escribe un archivo vacío (0 bytes). Qué decirle al
 * usuario en ese caso lo decide quien llama (I7LV-25).
 *
 * Hilos: no toca Swing ni guarda nada entre llamadas (sus métodos son
 * estáticos y HistorialSenal es inmutable), así que se puede llamar desde
 * cualquier hilo, también varias veces a la vez. I7LV-25 lo llamará FUERA
 * del hilo de Swing (por ejemplo, en doInBackground() de un SwingWorker):
 * una hora a 10 ms son 360 000 filas y, aunque se escriben en unas décimas
 * de segundo, en un disco lento o una memoria USB puede tardar más, y la
 * ventana no debe congelarse mientras tanto. El historial, en cambio, se
 * toma antes, en el hilo de Swing, al pulsar el botón (ver
 * getHistorialParaGuardar() en las gráficas). Si dos guardados apuntan al
 * mismo archivo a la vez, cada uno usa su propio temporal y el archivo queda
 * con la versión completa del último que termine.
 */
public final class EscritorArchivo {

    /** Decimales del tiempo y del valor analógico. */
    private static final int DECIMALES = 3;

    /** Separador entre el tiempo y el valor. */
    private static final char SEPARADOR = '\t';

    /** Fin de cada fila: el salto de línea de Windows. */
    private static final String FIN_DE_FILA = "\r\n";

    /**
     * Tamaño del búfer de escritura, en caracteres. Una fila tiene unos 13,
     * así que se envían al disco unas 5000 filas de una vez en vez de una
     * por una.
     */
    private static final int TAMANO_BUFER = 64 * 1024;

    /** Extensión del nombre sugerido. */
    private static final String EXTENSION = ".txt";

    /** Caracteres que Windows no acepta en un nombre de archivo (además de los de control). */
    private static final String CARACTERES_INVALIDOS = "<>:\"/\\|?*";

    /** Nombre sugerido si el del canal queda vacío. */
    private static final String NOMBRE_SIN_CANAL = "Senal";

    /**
     * Fecha y hora del nombre sugerido: 2026-10-04_15-30. Locale.ROOT y el
     * estilo de dígitos por defecto: siempre dígitos 0 a 9 y el calendario
     * de siempre, sin importar la configuración regional del equipo.
     */
    private static final DateTimeFormatter FORMATO_FECHA =
            DateTimeFormatter.ofPattern("uuuu-MM-dd_HH-mm", Locale.ROOT);

    /** Intentos para crear el temporal si su nombre, al azar, ya existe. */
    private static final int INTENTOS_TEMPORAL = 10;

    /** Cuánto se sigue intentando el reemplazo si Windows lo niega, en milisegundos (ver reemplazar()). */
    private static final long TIEMPO_REINTENTOS_MS = 500;

    /** Pausa entre dos intentos de reemplazo, en milisegundos. */
    private static final long PAUSA_REINTENTO_MS = 20;

    private EscritorArchivo() {
        // Solo métodos estáticos
    }

    /**
     * Escribe el historial en el archivo de destino con el formato de la
     * clase, reemplazándolo si ya existe. Si el historial está vacío, el
     * archivo queda vacío.
     *
     * Se puede llamar desde cualquier hilo; I7LV-25 la llamará fuera del
     * hilo de Swing (ver el comentario de la clase).
     *
     * @param historial lo que se escribe
     * @param destino   ruta del archivo; su carpeta debe existir
     * @throws IOException          si no se pudo guardar. El mensaje, en
     *                              español, dice qué archivo y por qué. En
     *                              ese caso no queda ningún temporal y el
     *                              archivo anterior, si había, sigue intacto.
     * @throws NullPointerException si algún parámetro es null
     */
    public static void escribir(HistorialSenal historial, Path destino) throws IOException {
        Objects.requireNonNull(historial, "El historial no puede ser null");
        Path archivo = Objects.requireNonNull(destino, "La ruta de destino no puede ser null")
                .toAbsolutePath();
        Path carpeta = archivo.getParent();
        Path nombre = archivo.getFileName();
        if (carpeta == null || nombre == null) {
            throw new IOException("No se pudo guardar la señal: \"" + archivo
                    + "\" no es la ruta de un archivo.");
        }
        String motivo = "No se pudo guardar el archivo \"" + nombre + "\" en la carpeta \""
                + carpeta + "\": ";
        if (!Files.isDirectory(carpeta)) {
            throw new IOException(motivo + "la carpeta no existe.");
        }
        if (Files.isDirectory(archivo)) {
            throw new IOException(motivo + "ya hay una carpeta con ese nombre.");
        }

        Path temporal = crearTemporal(carpeta, nombre.toString(), motivo);
        try {
            escribirTemporal(historial, temporal, motivo);
            reemplazar(temporal, archivo, motivo);
        } catch (IOException | RuntimeException | Error e) {
            // El archivo final no se tocó: solo falta borrar el temporal
            try {
                Files.deleteIfExists(temporal);
            } catch (IOException alBorrar) {
                e.addSuppressed(alBorrar);
            }
            throw e;
        }
    }

    /**
     * Nombre sugerido para el archivo de una señal: el canal, la fecha y la
     * hora (sin segundos), por ejemplo "A3_2026-10-04_15-30.txt".
     *
     * Solo tiene caracteres válidos en nombres de archivo de Windows: en el
     * nombre del canal, los caracteres < > : " / \ | ? * y los de control
     * se cambian por "_", y se le quitan los espacios de los
     * extremos; si queda vacío se usa "Senal". Los nombres de los canales del
     * programa ("A0" a "A7", "D0" a "D3") no cambian.
     *
     * @param canal     nombre del canal, por ejemplo "A3"
     * @param fechaHora cuándo se guarda
     * @return el nombre, sin carpeta
     * @throws NullPointerException si algún parámetro es null
     */
    public static String nombreSugerido(String canal, LocalDateTime fechaHora) {
        Objects.requireNonNull(fechaHora, "La fecha y hora no pueden ser null");
        StringBuilder nombre = new StringBuilder();
        for (char c : canal.strip().toCharArray()) {
            boolean invalido = c < ' ' || CARACTERES_INVALIDOS.indexOf(c) >= 0;
            nombre.append(invalido ? '_' : c);
        }
        if (nombre.isEmpty()) {
            nombre.append(NOMBRE_SIN_CANAL);
        }
        return nombre + "_" + FORMATO_FECHA.format(fechaHora) + EXTENSION;
    }

    // ===================== Pasos del guardado =====================

    /**
     * Crea el temporal, vacío, en la misma carpeta del destino: así el cambio
     * de nombre final es un solo paso (entre carpetas de discos distintos
     * sería una copia).
     */
    private static Path crearTemporal(Path carpeta, String nombre, String motivo) throws IOException {
        for (int intento = 1; ; intento++) {
            long numero = ThreadLocalRandom.current().nextLong() & Long.MAX_VALUE;
            Path temporal = carpeta.resolve("~" + nombre + "." + Long.toString(numero, 36) + ".tmp");
            try {
                return Files.createFile(temporal);
            } catch (FileAlreadyExistsException e) {
                // Otro guardado usa ese nombre: se prueba con otro número
                if (intento == INTENTOS_TEMPORAL) {
                    throw new IOException(motivo + "no se pudo crear el archivo temporal.", e);
                }
            } catch (AccessDeniedException e) {
                throw new IOException(motivo + "no hay permiso para escribir en esa carpeta.", e);
            } catch (IOException e) {
                throw new IOException(motivo + "no se pudo crear el archivo temporal ("
                        + e.getMessage() + ").", e);
            }
        }
    }

    private static void escribirTemporal(HistorialSenal historial, Path temporal, String motivo)
            throws IOException {
        try (FileChannel abierto = FileChannel.open(temporal, StandardOpenOption.WRITE);
             Writer salida = new BufferedWriter(new OutputStreamWriter(
                     Channels.newOutputStream(abierto), StandardCharsets.UTF_8), TAMANO_BUFER)) {
            escribirFilas(historial, salida);
            salida.flush();
            // Que los datos lleguen al disco antes de darles el nombre final:
            // si se va la luz justo después, el archivo no queda vacío ni a medias
            abierto.force(true);
        } catch (IOException e) {
            throw new IOException(motivo + "falló la escritura (" + e.getMessage() + ").", e);
        }
    }

    private static void escribirFilas(HistorialSenal historial, Writer salida) throws IOException {
        boolean digital = historial.getTipo() == HistorialSenal.Tipo.DIGITAL;
        for (int i = 0; i < historial.getCantidad(); i++) {
            salida.write(conTresDecimales(historial.getTiempo(i)));
            salida.write(SEPARADOR);
            if (digital) {
                // HistorialSenal garantiza que vale 0 o 1
                salida.write(historial.getValor(i) == 1.0 ? '1' : '0');
            } else {
                salida.write(conTresDecimales(historial.getValor(i)));
            }
            salida.write(FIN_DE_FILA);
        }
    }

    /**
     * Reemplaza el archivo final por el temporal, en un solo paso.
     *
     * Si Windows lo niega (AccessDeniedException), se reintenta cada 20 ms
     * durante medio segundo. Basta con que otro programa lea los datos del
     * archivo (su tamaño, su fecha) justo en ese instante para que falle, y
     * un momento después funciona: puede pasar con el Explorador de Windows
     * abierto en esa carpeta, el antivirus o el indexador. Medido en I7LV-25:
     * con otro hilo leyendo el tamaño del archivo sin parar, fallaban de 26 a
     * 41 de cada 500 reemplazos sin reintentar. Si el archivo de verdad está
     * abierto en otro programa (Excel) o es de solo lectura, sigue fallando y
     * se lanza la excepción después de ese medio segundo.
     */
    private static void reemplazar(Path temporal, Path archivo, String motivo) throws IOException {
        long limite = System.nanoTime() + TIEMPO_REINTENTOS_MS * 1_000_000L;
        try {
            while (true) {
                try {
                    mover(temporal, archivo);
                    return;
                } catch (AccessDeniedException e) {
                    if (System.nanoTime() - limite >= 0) {
                        throw e;
                    }
                    Thread.sleep(PAUSA_REINTENTO_MS);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException(motivo + "se interrumpió antes de darle el nombre final.", e);
        } catch (IOException e) {
            if (Files.exists(archivo)) {
                // En Windows pasa si el archivo está abierto en otro programa
                // o es de solo lectura
                throw new IOException(motivo + "no se pudo reemplazar el archivo que ya existe. "
                        + "Puede estar abierto en otro programa (por ejemplo, Excel) o ser de solo "
                        + "lectura: ciérrelo o elija otro nombre, e intente de nuevo.", e);
            }
            throw new IOException(motivo + "no se pudo darle el nombre final ("
                    + e.getMessage() + ").", e);
        }
    }

    /** Un intento de reemplazo. */
    private static void mover(Path temporal, Path archivo) throws IOException {
        try {
            Files.move(temporal, archivo, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            // Algunos sistemas de archivos (por ejemplo, ciertas unidades de
            // red) no lo permiten: se reemplaza de la forma común
            Files.move(temporal, archivo, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    // ===================== Formato de los números =====================

    /**
     * El número con 3 decimales y punto decimal (ver el redondeo en el
     * comentario de la clase). BigDecimal no tiene "-0", así que un valor
     * como -0.0004 queda 0.000.
     */
    private static String conTresDecimales(double numero) {
        return BigDecimal.valueOf(numero).setScale(DECIMALES, RoundingMode.HALF_UP).toPlainString();
    }
}
