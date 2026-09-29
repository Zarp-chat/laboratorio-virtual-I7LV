package laboratoriovirtual.control;

import java.nio.file.Path;

/**
 * Las ventanas que abre el guardado de una señal (I7LV-25): elegir el
 * archivo, preguntar si se reemplaza y los avisos. ControlGuardado solo
 * habla con esta interfaz y nunca abre una ventana por su cuenta:
 * - en el programa, DialogosGuardadoSwing las muestra con JFileChooser y
 *   JOptionPane;
 * - VerificacionSprint3 la reemplaza por una que responde sola y anota lo
 *   que se le pidió, para probar el guardado sin abrir ventanas.
 *
 * Todos los métodos se llaman en el hilo de Swing y no vuelven hasta que el
 * usuario responde (las ventanas son modales).
 */
public interface DialogosGuardado {

    /** Tipo de aviso: decide el ícono de la ventana. */
    enum Aviso {
        /** Salió bien, por ejemplo "Se guardaron 40 muestras…". */
        INFORMACION,
        /** No se hizo nada, pero no es un error: por ejemplo, no hay datos. */
        ADVERTENCIA,
        /** No se pudo guardar. */
        ERROR
    }

    /**
     * Pide al usuario dónde guardar.
     *
     * @param titulo          título de la ventana, por ejemplo "Guardar señal A3"
     * @param carpetaInicial  carpeta en la que se abre
     * @param nombrePropuesto nombre que aparece ya escrito, por ejemplo
     *                        "A3_2026-10-04_15-30.txt"
     * @return el archivo elegido, tal como lo escribió el usuario (quien
     *         llama le agrega la extensión si hace falta), o null si canceló
     */
    Path elegirArchivo(String titulo, Path carpetaInicial, String nombrePropuesto);

    /**
     * Hace una pregunta de sí o no.
     *
     * @param titulo   título de la ventana
     * @param pregunta el texto de la pregunta
     * @return true solo si el usuario respondió Sí; cerrar la ventana cuenta
     *         como No
     */
    boolean confirmar(String titulo, String pregunta);

    /**
     * Muestra un aviso y espera a que el usuario lo cierre.
     *
     * @param tipo    información, advertencia o error
     * @param titulo  título de la ventana
     * @param mensaje el texto del aviso
     */
    void avisar(Aviso tipo, String titulo, String mensaje);
}
