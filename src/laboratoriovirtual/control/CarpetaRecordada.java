package laboratoriovirtual.control;

import java.nio.file.Path;
import java.util.Objects;
import javax.swing.filechooser.FileSystemView;

/**
 * La última carpeta en la que se guardó una señal durante esta sesión
 * (I7LV-25). La ventana para elegir dónde guardar abre en ella. Cancelar,
 * no reemplazar un archivo o una falla al guardar no la cambian.
 *
 * La comparten las dos pestañas de señal: Main crea una sola y se la pasa a
 * los dos ControlGuardado. No es una variable estática: cada objeto es
 * independiente (las pruebas crean los suyos). No se guarda al cerrar el
 * programa: la sesión siguiente empieza otra vez en Documentos.
 *
 * Solo se usa en el hilo de Swing.
 */
public final class CarpetaRecordada {

    private Path carpeta;

    /**
     * Empieza en la carpeta Documentos del usuario: la misma en que abre por
     * defecto un JFileChooser (FileSystemView.getDefaultDirectory()). En
     * Windows es la carpeta Documentos de verdad, aunque esté en OneDrive o
     * se llame "Documents".
     */
    public CarpetaRecordada() {
        carpeta = FileSystemView.getFileSystemView().getDefaultDirectory().toPath();
    }

    /** La carpeta en la que debe abrir la próxima ventana de guardar. */
    public Path getCarpeta() {
        return carpeta;
    }

    /**
     * Recuerda la carpeta en la que se acaba de guardar.
     *
     * @param nueva la carpeta del archivo guardado
     * @throws NullPointerException si es null
     */
    public void recordar(Path nueva) {
        carpeta = Objects.requireNonNull(nueva, "La carpeta no puede ser null");
    }
}
