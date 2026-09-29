package laboratoriovirtual.control;

import java.awt.Component;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;

/**
 * Las ventanas del guardado de una señal (DialogosGuardado) hechas con
 * Swing: un JFileChooser para elegir el archivo y JOptionPane para la
 * pregunta y los avisos, centrados en la ventana del programa. Es la que usa
 * Main (I7LV-25).
 *
 * Los textos largos se parten en renglones de hasta 70 caracteres, por los
 * espacios: JOptionPane no los parte solo, y un aviso con una ruta larga o
 * con el motivo de una falla quedaría más ancho que la pantalla. Una ruta
 * sin espacios queda entera en su renglón.
 *
 * Textos en español: Java (JDK 22) solo trae en inglés los textos de sus
 * ventanas estándar, aunque el equipo esté en español. Por eso los botones
 * de la pregunta y de los avisos se dan aquí ("Sí", "No", "Aceptar"), y los
 * del selector de archivos ("Guardar en:", "Nombre de archivo:", "Guardar",
 * "Cancelar"…) se ponen con ponerTextosDelSelector() antes de crearlo.
 */
public class DialogosGuardadoSwing implements DialogosGuardado {

    /** Descripción del único filtro del selector de archivos. */
    private static final String DESCRIPCION_FILTRO = "Archivos de texto (*.txt)";

    /** Largo máximo de un renglón en la pregunta y en los avisos. */
    private static final int CARACTERES_POR_RENGLON = 70;

    private final Component ventana;

    /**
     * @param ventana componente sobre el que se centran las ventanas (la
     *                ventana principal)
     */
    public DialogosGuardadoSwing(Component ventana) {
        this.ventana = ventana;
    }

    /**
     * Muestra el selector de archivos de crearSelector(). Al nombre escrito
     * se le quitan los espacios de los extremos (Windows no acepta un nombre
     * que termine en espacio). Si aun así no sirve para un archivo (queda
     * vacío o tiene, por ejemplo, "<"), lo avisa y vuelve a mostrar el
     * selector con ese mismo nombre, para corregirlo.
     */
    @Override
    public Path elegirArchivo(String titulo, Path carpetaInicial, String nombrePropuesto) {
        JFileChooser selector = crearSelector(titulo, carpetaInicial, nombrePropuesto);
        while (selector.showSaveDialog(ventana) == JFileChooser.APPROVE_OPTION) {
            File escrito = selector.getSelectedFile();
            String nombre = escrito.getName().strip();
            try {
                if (!nombre.isEmpty()) {
                    return new File(escrito.getParentFile(), nombre).toPath();
                }
            } catch (InvalidPathException e) {
                // Se avisa abajo
            }
            avisar(Aviso.ADVERTENCIA, titulo, "El nombre \"" + escrito.getName()
                    + "\" no sirve para un archivo: no puede estar vacío ni tener ninguno de "
                    + "estos caracteres: < > : \" / \\ | ? *");
        }
        return null;
    }

    /** "No" viene elegido: un Enter por descuido no reemplaza el archivo. */
    @Override
    public boolean confirmar(String titulo, String pregunta) {
        Object[] opciones = {"Sí", "No"};
        return opciones[0].equals(mostrar(pregunta, titulo, JOptionPane.QUESTION_MESSAGE, opciones, opciones[1]));
    }

    @Override
    public void avisar(Aviso tipo, String titulo, String mensaje) {
        int icono = switch (tipo) {
            case INFORMACION -> JOptionPane.INFORMATION_MESSAGE;
            case ADVERTENCIA -> JOptionPane.WARNING_MESSAGE;
            case ERROR -> JOptionPane.ERROR_MESSAGE;
        };
        Object[] opciones = {"Aceptar"};
        mostrar(mensaje, titulo, icono, opciones, opciones[0]);
    }

    /**
     * Crea el selector de archivos de elegirArchivo(), sin mostrarlo: ventana
     * de guardar con ese título y sus textos en español, un solo filtro,
     * "Archivos de texto (*.txt)", abierta en la carpeta inicial y con el
     * nombre propuesto ya escrito.
     *
     * Si la carpeta inicial ya no existe (por ejemplo, se borró después de
     * guardar en ella), abre en la más cercana que sí existe, subiendo por
     * sus carpetas padre. Si no queda ninguna (se retiró la memoria USB),
     * abre en Documentos, lo normal de JFileChooser.
     *
     * Es público para que VerificacionSprint3 revise esta configuración sin
     * abrir ninguna ventana.
     *
     * @param titulo          título de la ventana
     * @param carpetaInicial  carpeta en la que se abre
     * @param nombrePropuesto nombre ya escrito en el campo del nombre
     * @return el selector, listo para showSaveDialog()
     */
    public static JFileChooser crearSelector(String titulo, Path carpetaInicial, String nombrePropuesto) {
        ponerTextosDelSelector(); // antes de crearlo: los lee al crearse

        Path carpeta = carpetaInicial;
        while (carpeta != null && !Files.isDirectory(carpeta)) {
            carpeta = carpeta.getParent();
        }
        File abrirEn = carpeta == null ? null : carpeta.toFile(); // null: Documentos

        JFileChooser selector = new JFileChooser(abrirEn);
        selector.setDialogType(JFileChooser.SAVE_DIALOG);
        selector.setDialogTitle(titulo);
        selector.setAcceptAllFileFilterUsed(false);
        selector.setFileFilter(new FileNameExtensionFilter(DESCRIPCION_FILTRO, "txt"));
        selector.setSelectedFile(new File(selector.getCurrentDirectory(), nombrePropuesto));
        return selector;
    }

    /**
     * Pone en español los textos del selector de archivos (Look and Feel
     * Metal, el que usa Tema). JFileChooser los toma de UIManager al crearse,
     * así que valen para todos los selectores de archivos del programa; hoy
     * solo existe este. No cambia los textos de ninguna otra ventana.
     */
    private static void ponerTextosDelSelector() {
        String[][] textos = {
            {"FileChooser.saveInLabelText", "Guardar en:"},
            {"FileChooser.lookInLabelText", "Buscar en:"},
            {"FileChooser.fileNameLabelText", "Nombre de archivo:"},
            {"FileChooser.folderNameLabelText", "Nombre de carpeta:"},
            {"FileChooser.filesOfTypeLabelText", "Tipo de archivo:"},
            {"FileChooser.saveButtonText", "Guardar"},
            {"FileChooser.saveButtonToolTipText", "Guardar en el archivo elegido"},
            {"FileChooser.openButtonText", "Abrir"},
            {"FileChooser.openButtonToolTipText", "Abrir el archivo elegido"},
            {"FileChooser.directoryOpenButtonText", "Abrir"},
            {"FileChooser.directoryOpenButtonToolTipText", "Abrir la carpeta elegida"},
            {"FileChooser.cancelButtonText", "Cancelar"},
            {"FileChooser.cancelButtonToolTipText", "Cerrar sin guardar"},
            {"FileChooser.upFolderToolTipText", "Subir un nivel"},
            {"FileChooser.upFolderAccessibleName", "Subir"},
            {"FileChooser.homeFolderToolTipText", "Inicio"},
            {"FileChooser.homeFolderAccessibleName", "Inicio"},
            {"FileChooser.newFolderToolTipText", "Crear una carpeta nueva"},
            {"FileChooser.newFolderAccessibleName", "Carpeta nueva"},
            {"FileChooser.listViewButtonToolTipText", "Lista"},
            {"FileChooser.listViewButtonAccessibleName", "Lista"},
            {"FileChooser.detailsViewButtonToolTipText", "Detalles"},
            {"FileChooser.detailsViewButtonAccessibleName", "Detalles"},
            {"FileChooser.fileNameHeaderText", "Nombre"},
            {"FileChooser.fileSizeHeaderText", "Tamaño"},
            {"FileChooser.fileTypeHeaderText", "Tipo"},
            {"FileChooser.fileDateHeaderText", "Modificado"},
            {"FileChooser.fileAttrHeaderText", "Atributos"},
            {"FileChooser.viewMenuLabelText", "Ver"},
            {"FileChooser.refreshActionLabelText", "Actualizar"},
            {"FileChooser.newFolderActionLabelText", "Carpeta nueva"},
            {"FileChooser.listViewActionLabelText", "Lista"},
            {"FileChooser.detailsViewActionLabelText", "Detalles"},
            {"FileChooser.filesListAccessibleName", "Lista de archivos"},
            {"FileChooser.filesDetailsAccessibleName", "Detalles de los archivos"},
            {"FileChooser.fileDescriptionText", "Archivo"},
            {"FileChooser.directoryDescriptionText", "Carpeta"},
            {"FileChooser.win32.newFolder", "Nueva carpeta"},
            {"FileChooser.win32.newFolder.subsequent", "Nueva carpeta ({0})"},
            {"FileChooser.other.newFolder", "NuevaCarpeta"},
            {"FileChooser.other.newFolder.subsequent", "NuevaCarpeta.{0}"},
            {"FileChooser.newFolderErrorText", "No se pudo crear la carpeta nueva"},
            {"FileChooser.newFolderParentDoesntExistTitleText", "No se pudo crear la carpeta"},
            {"FileChooser.newFolderParentDoesntExistText",
                "No se pudo crear la carpeta.\n\nLa ruta indicada no existe."},
            {"FileChooser.renameErrorTitleText", "No se pudo cambiar el nombre"},
            {"FileChooser.renameErrorText", "No se pudo cambiar el nombre de {0}"},
            {"FileChooser.renameErrorFileExistsText",
                "No se pudo cambiar el nombre de {0}: ya existe un archivo con ese nombre."},
        };
        for (String[] texto : textos) {
            UIManager.put(texto[0], texto[1]);
        }
    }

    /**
     * Muestra el mensaje en una ventana modal y devuelve la opción elegida
     * (null si se cerró la ventana).
     */
    private Object mostrar(String mensaje, String titulo, int tipo, Object[] opciones, Object inicial) {
        JOptionPane panel = new PanelConRenglones(mensaje, tipo, opciones, inicial);
        JDialog dialogo = panel.createDialog(ventana, titulo);
        dialogo.setVisible(true); // modal: vuelve cuando el usuario responde
        dialogo.dispose();
        return panel.getValue();
    }

    /**
     * JOptionPane que parte los textos largos en renglones, por los espacios
     * (JOptionPane lo hace solo si se le dice un largo máximo).
     */
    private static final class PanelConRenglones extends JOptionPane {

        private static final long serialVersionUID = 1L;

        PanelConRenglones(String mensaje, int tipo, Object[] opciones, Object inicial) {
            super(mensaje, tipo, JOptionPane.DEFAULT_OPTION, null, opciones, inicial);
        }

        @Override
        public int getMaxCharactersPerLineCount() {
            return CARACTERES_POR_RENGLON;
        }
    }
}
