package laboratoriovirtual.gui;

import java.awt.Color;
import java.awt.Font;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.plaf.ColorUIResource;

/**
 * Identidad visual de la Universidad del Valle: colores y fuentes.
 *
 * Los formularios (.form) NO leen estas constantes. El editor visual de
 * NetBeans guarda cada color y cada fuente como un valor concreto (RGB,
 * nombre, estilo y tamaño) para poder dibujarlos en la vista Design.
 * Por eso los .form repiten exactamente los mismos valores RGB y las mismas
 * fuentes que están aquí. Si se cambia un valor en esta clase, hay que
 * cambiarlo también, a mano, en cada .form que lo use.
 *
 * El rojo es PROVISIONAL: falta verificarlo con el manual de identidad
 * visual de Univalle.
 */
public final class Tema {

    // ---------------------------------------------------------------
    // Colores

    /** Rojo institucional, RGB 204, 0, 0. PROVISIONAL: pendiente de verificar con el manual de identidad de Univalle. */
    public static final Color ROJO = new Color(204, 0, 0);

    /** Blanco, RGB 255, 255, 255. Textos sobre rojo y fondo de la barra de estado, la gráfica y los formularios. */
    public static final Color BLANCO = new Color(255, 255, 255);

    /** Negro, RGB 0, 0, 0. Textos y bordes. */
    public static final Color NEGRO = new Color(0, 0, 0);

    /** Gris de bordes, RGB 180, 180, 180. Borde de la gráfica, de los formularios, línea sobre el botón Guardar y relleno del LED apagado (LedIndicador). */
    public static final Color GRIS_BORDE = new Color(180, 180, 180);

    /** Gris de fondo secundario, RGB 245, 245, 245. Fondo de las pestañas y de su contenido. */
    public static final Color GRIS_FONDO = new Color(245, 245, 245);

    /** Gris oscuro, RGB 90, 90, 90. Señales digitales no seleccionadas en la gráfica digital. */
    public static final Color GRIS_OSCURO = new Color(90, 90, 90);

    // ---------------------------------------------------------------
    // Fuentes

    /** Título del encabezado: SansSerif, negrita, 26. */
    public static final Font FUENTE_TITULO = new Font("SansSerif", Font.BOLD, 26);

    /** Subtítulo del encabezado: SansSerif, normal, 13. */
    public static final Font FUENTE_SUBTITULO = new Font("SansSerif", Font.PLAIN, 13);

    /** Nombres de los creadores en el encabezado: SansSerif, negrita, 12. */
    public static final Font FUENTE_CREADORES = new Font("SansSerif", Font.BOLD, 12);

    /** Textos, etiquetas, selector de canal y campo de texto: SansSerif, normal, 13. */
    public static final Font FUENTE_TEXTO = new Font("SansSerif", Font.PLAIN, 13);

    /** Títulos de las pestañas y valores destacados: SansSerif, negrita, 13. */
    public static final Font FUENTE_TEXTO_NEGRITA = new Font("SansSerif", Font.BOLD, 13);

    /** Botones e interruptores: SansSerif, negrita, 12. */
    public static final Font FUENTE_BOTON = new Font("SansSerif", Font.BOLD, 12);

    /** Notas de ayuda, como el rango permitido: SansSerif, normal, 12. */
    public static final Font FUENTE_NOTA = new Font("SansSerif", Font.PLAIN, 12);

    private Tema() {
        // Solo constantes y métodos estáticos
    }

    /**
     * Activa el look and feel multiplataforma (Metal) y ajusta los colores
     * que dependen de él. Se llama una sola vez, en el hilo de Swing, antes
     * de crear cualquier ventana: los componentes toman estos valores al crearse.
     */
    public static void aplicar() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (ReflectiveOperationException | UnsupportedLookAndFeelException e) {
            // Metal viene incluido en todo JDK, así que no debería fallar.
            // Si falla, la aplicación sigue con el look and feel por defecto.
            System.err.println("No se pudo activar el look and feel Metal: " + e);
        }

        // Pestaña seleccionada del JTabbedPane: fondo rojo y texto blanco.
        // Se usan ColorUIResource porque Swing solo aplica el color de texto
        // de la pestaña seleccionada cuando el color normal viene del look and feel.
        UIManager.put("TabbedPane.selected", new ColorUIResource(ROJO));
        UIManager.put("TabbedPane.selectedForeground", new ColorUIResource(BLANCO));

        // Textos en negro. Metal usa gris oscuro (RGB 51, 51, 51) por defecto.
        // No se define en los .form porque en el diseñador de NetBeans el negro
        // ya es el valor por defecto y NetBeans descartaría esa propiedad al guardar.
        ColorUIResource negro = new ColorUIResource(NEGRO);
        UIManager.put("Label.foreground", negro);
        UIManager.put("Button.foreground", negro);
        UIManager.put("ToggleButton.foreground", negro);
        UIManager.put("ComboBox.foreground", negro);
        UIManager.put("TextField.foreground", negro);
        UIManager.put("TabbedPane.foreground", negro);
    }
}
