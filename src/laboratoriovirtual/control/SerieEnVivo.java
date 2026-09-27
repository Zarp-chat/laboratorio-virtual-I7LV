package laboratoriovirtual.control;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.jfree.data.xy.XYSeries;

/**
 * Datos de una señal que se grafica en vivo (I7LV-17): la cola entre hilos,
 * la ventana visible de tiempo y el historial completo.
 *
 * Es la parte de la gráfica que no dibuja nada. Se separó de GraficaSenal
 * para poder probarla sin ventana (VerificacionSprint2): solo usa la XYSeries
 * de JFreeChart, que es un objeto de datos y no necesita pantalla.
 *
 * Reparto de hilos:
 * - agregar() lo llama el hilo del Muestreador. Guarda el punto en la cola y
 *   en el historial; no toca la serie.
 * - procesarPendientes(), getSerie(), getInicioVentana() y getFinVentana()
 *   se usan solo desde el hilo de Swing (en el programa, desde el Timer de
 *   GraficaSenal). JFreeChart lee la serie al dibujar, también en ese hilo,
 *   así que nunca la usan dos hilos a la vez.
 * - hayPendientes() y getHistorial() se pueden llamar desde cualquier hilo.
 *
 * No sabe de canales ni de Muestra: recibe pares (tiempo, valor), así que
 * sirve igual para la gráfica analógica y para la digital (valores 0 y 1).
 */
public class SerieEnVivo {

    /**
     * Un punto de la señal. Es inmutable, así que el mismo objeto puede
     * estar en la cola y en el historial, y pasar de un hilo a otro.
     *
     * @param tiempo segundos desde el último Iniciar
     * @param valor  valor de la señal en ese instante
     */
    public record Punto(double tiempo, double valor) {
    }

    /** Ancho de la ventana visible, en segundos. */
    private final double anchoVentana;

    // Puntos recibidos que la serie todavía no tiene. ConcurrentLinkedQueue
    // deja que un hilo agregue mientras otro saca, sin bloquear a ninguno.
    private final Queue<Punto> pendientes = new ConcurrentLinkedQueue<>();

    // Todos los puntos desde el último Iniciar. Lo escribe el hilo del
    // Muestreador y lo puede copiar cualquier otro, así que la lista y
    // ultimoTiempoRecibido solo se usan dentro de synchronized (historial).
    private final List<Punto> historial = new ArrayList<>();
    private double ultimoTiempoRecibido = Double.NEGATIVE_INFINITY;

    // Lo que se dibuja: solo los puntos de la ventana visible.
    // Solo se usan desde el hilo de Swing.
    private final XYSeries serie;
    private double ultimoTiempoDibujado = Double.NEGATIVE_INFINITY;

    /**
     * @param anchoVentanaS ancho de la ventana visible, en segundos
     */
    public SerieEnVivo(double anchoVentanaS) {
        this.anchoVentana = anchoVentanaS;
        // El primer "false" dice que no hace falta ordenar los puntos: llegan
        // en orden de tiempo, y un tiempo menor se trata como un nuevo
        // Iniciar. El "true" acepta dos puntos con el mismo tiempo; con
        // "false", JFreeChart recorrería toda la serie en cada add() buscando
        // un tiempo repetido, trabajo inútil con 3000 puntos en la ventana.
        this.serie = new XYSeries("Señal", false, true);
    }

    // ===================== Hilo del Muestreador =====================

    /**
     * Recibe un punto nuevo. Lo guarda en el historial y lo deja en la cola
     * para que procesarPendientes() lo pase a la serie. No toca la serie ni
     * Swing, así que se puede llamar desde el hilo del Muestreador.
     *
     * Si el tiempo es menor que el del punto anterior, es un nuevo Iniciar:
     * el historial se vacía antes de guardar el punto.
     */
    public void agregar(double tiempo, double valor) {
        Punto punto = new Punto(tiempo, valor);
        synchronized (historial) {
            if (tiempo < ultimoTiempoRecibido) {
                historial.clear();
            }
            historial.add(punto);
            ultimoTiempoRecibido = tiempo;
        }
        pendientes.add(punto);
    }

    // ===================== Cualquier hilo =====================

    /** Indica si hay puntos recibidos que la serie todavía no tiene. */
    public boolean hayPendientes() {
        return !pendientes.isEmpty();
    }

    /**
     * Devuelve una copia de todos los puntos recibidos desde el último
     * Iniciar, en orden de tiempo, incluso los que ya salieron de la ventana
     * visible o que la gráfica todavía no dibuja. Pensado para guardar la
     * señal en un archivo (Sprint 3).
     *
     * Seguro entre hilos: se puede llamar desde cualquier hilo, también con
     * el muestreo corriendo. La copia se hace con el mismo candado con el que
     * agregar() escribe, así que nunca sale a medio actualizar. La lista
     * devuelta es inmutable y no cambia cuando llegan más muestras: se puede
     * recorrer con calma, por ejemplo mientras se escribe el archivo.
     *
     * @return lista inmutable de puntos (tiempo, valor)
     */
    public List<Punto> getHistorial() {
        synchronized (historial) {
            return List.copyOf(historial);
        }
    }

    // ===================== Hilo de Swing =====================

    /**
     * Pasa a la serie todos los puntos de la cola y quita de ella los que
     * salieron de la ventana visible. Mientras tanto la serie no avisa a la
     * gráfica; avisa una sola vez al final, así la gráfica se redibuja una
     * vez por llamada y no una vez por punto.
     *
     * Si un punto tiene un tiempo menor que el anterior, es un nuevo Iniciar:
     * la serie se vacía antes de agregarlo.
     */
    public void procesarPendientes() {
        if (pendientes.isEmpty()) {
            return;
        }
        serie.setNotify(false);
        try {
            Punto punto;
            while ((punto = pendientes.poll()) != null) {
                if (punto.tiempo() < ultimoTiempoDibujado) {
                    serie.clear();
                }
                serie.add(punto.tiempo(), punto.valor(), false);
                ultimoTiempoDibujado = punto.tiempo();
            }
            quitarPuntosViejos();
        } finally {
            // Al volver a encenderlo, la serie avisa un solo cambio con todo
            serie.setNotify(true);
        }
    }

    /** La serie que dibuja la gráfica: solo los puntos de la ventana visible. */
    public XYSeries getSerie() {
        return serie;
    }

    /**
     * Fin de la ventana visible, en segundos: el tiempo del último punto, o
     * el ancho de la ventana mientras no se llegue a él. Así, al comenzar,
     * el eje X va de 0 a 30 s, y después sigue al último punto.
     */
    public double getFinVentana() {
        return Math.max(anchoVentana, ultimoTiempoDibujado);
    }

    /** Inicio de la ventana visible, en segundos. */
    public double getInicioVentana() {
        return getFinVentana() - anchoVentana;
    }

    /** Quita de la serie los puntos anteriores al inicio de la ventana. */
    private void quitarPuntosViejos() {
        double limite = ultimoTiempoDibujado - anchoVentana;
        int viejos = 0;
        while (viejos < serie.getItemCount()
                && serie.getX(viejos).doubleValue() < limite) {
            viejos++;
        }
        if (viejos > 0) {
            // Todos de una vez: la serie recalcula sus mínimos y máximos una
            // sola vez, en vez de una vez por punto quitado.
            serie.delete(0, viejos - 1);
        }
    }
}
