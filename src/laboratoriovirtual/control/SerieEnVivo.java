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
 * - procesarPendientes(), vaciarRecibidos(), vaciarSerie(), getSerie(),
 *   getInicioVentana() y getFinVentana() se usan solo desde el hilo de Swing
 *   (en el programa, desde el Timer de GraficaSenal y desde el cambio de
 *   canal). JFreeChart lee la serie al dibujar, también en ese hilo, así que
 *   nunca la usan dos hilos a la vez.
 * - hayPendientes() y getHistorial() se pueden llamar desde cualquier hilo.
 *
 * Candado: el propio objeto. agregar(), vaciarRecibidos() y getHistorial()
 * son synchronized, así que nunca se mezclan entre sí. Quien necesite que
 * otro paso ocurra junto con agregar(), sin que nadie se meta en medio,
 * puede tomar este mismo candado con synchronized (serie) { ... }:
 * GraficaSenal lo hace para leer el canal y registrar el punto en un solo
 * paso (I7LV-16). Los candados de Java se pueden volver a tomar desde el
 * mismo hilo, así que llamar a agregar() dentro de ese bloque no se traba.
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
    // deja que el hilo de Swing saque sin tomar el candado mientras el
    // Muestreador agrega. Se agrega y se vacía solo con el candado.
    private final Queue<Punto> pendientes = new ConcurrentLinkedQueue<>();

    // Todos los puntos desde el último Iniciar o el último cambio de canal.
    // Lo escribe el hilo del Muestreador y lo puede copiar cualquier otro,
    // así que la lista y ultimoTiempoRecibido solo se usan con el candado.
    private final List<Punto> historial = new ArrayList<>();
    private double ultimoTiempoRecibido = Double.NEGATIVE_INFINITY;

    // Lo que se dibuja: solo los puntos de la ventana visible.
    // Solo se usan desde el hilo de Swing.
    private final XYSeries serie;
    private double ultimoTiempoDibujado = Double.NEGATIVE_INFINITY;

    // Dónde arranca el eje X mientras la serie no llena la ventana: 0 al
    // comenzar y con cada nuevo Iniciar (el Muestreador cuenta el tiempo
    // desde cero). Después de vaciarSerie() lo decide el primer punto que se
    // dibuje, porque ahí el tiempo no vuelve a cero. Solo hilo de Swing.
    private double inicioEje = 0.0;
    private boolean esperandoPrimerPunto = false;

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
    public synchronized void agregar(double tiempo, double valor) {
        Punto punto = new Punto(tiempo, valor);
        if (tiempo < ultimoTiempoRecibido) {
            historial.clear();
        }
        historial.add(punto);
        ultimoTiempoRecibido = tiempo;
        // También con el candado: el punto entra al historial y a la cola en
        // un solo paso, y vaciarRecibidos() no puede quedar en medio,
        // borrándolo de uno y dejándolo en el otro.
        pendientes.add(punto);
    }

    // ===================== Cualquier hilo =====================

    /** Indica si hay puntos recibidos que la serie todavía no tiene. */
    public boolean hayPendientes() {
        return !pendientes.isEmpty();
    }

    /**
     * Devuelve una copia de todos los puntos recibidos desde el último
     * Iniciar (o desde el último vaciarRecibidos(), si fue después), en
     * orden de tiempo, incluso los que ya salieron de la ventana visible o
     * que la gráfica todavía no dibuja. Pensado para guardar la señal en un
     * archivo (Sprint 3).
     *
     * Seguro entre hilos: se puede llamar desde cualquier hilo, también con
     * el muestreo corriendo. La copia se hace con el mismo candado con el que
     * agregar() escribe, así que nunca sale a medio actualizar. La lista
     * devuelta es inmutable y no cambia cuando llegan más muestras: se puede
     * recorrer con calma, por ejemplo mientras se escribe el archivo.
     *
     * @return lista inmutable de puntos (tiempo, valor)
     */
    public synchronized List<Punto> getHistorial() {
        return List.copyOf(historial);
    }

    // ===================== Hilo de Swing =====================

    /**
     * Descarta todos los puntos recibidos: vacía el historial y la cola de
     * pendientes en un solo paso, con el candado, así que un agregar() queda
     * del todo antes (y su punto se borra) o del todo después. No toca la
     * serie dibujada: para eso está vaciarSerie().
     *
     * Se llama desde el hilo de Swing, el mismo que saca puntos de la cola:
     * así nadie está a mitad de sacar uno mientras se vacía.
     *
     * No olvida el tiempo del último punto recibido: si el siguiente llega
     * con un tiempo menor, se sigue reconociendo como un nuevo Iniciar.
     */
    public synchronized void vaciarRecibidos() {
        historial.clear();
        pendientes.clear();
    }

    /**
     * Vacía la serie dibujada. El eje X arrancará en el tiempo del primer
     * punto que se dibuje después (ver procesarPendientes()), con la misma
     * ventana de 30 s.
     *
     * Si todavía no se ha dibujado nada, la gráfica sigue como al comenzar
     * (eje X de 0 a 30 s): el primer punto que llegue será el del primer
     * Iniciar, cuyo tiempo empieza en cero.
     */
    public void vaciarSerie() {
        serie.clear();
        esperandoPrimerPunto = ultimoTiempoDibujado != Double.NEGATIVE_INFINITY;
    }

    /**
     * Pasa a la serie todos los puntos de la cola y quita de ella los que
     * salieron de la ventana visible. Mientras tanto la serie no avisa a la
     * gráfica; avisa una sola vez al final, así la gráfica se redibuja una
     * vez por llamada y no una vez por punto.
     *
     * Si un punto tiene un tiempo menor que el anterior, es un nuevo Iniciar:
     * la serie se vacía antes de agregarlo y el eje X vuelve a arrancar en 0.
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
                    // Nuevo Iniciar: el tiempo volvió a empezar desde cero
                    serie.clear();
                    inicioEje = 0.0;
                    esperandoPrimerPunto = false;
                } else if (esperandoPrimerPunto) {
                    // Primer punto después de vaciarSerie() (cambio de
                    // canal): el tiempo siguió corriendo, así que el eje X
                    // arranca aquí y no en 0
                    inicioEje = punto.tiempo();
                    esperandoPrimerPunto = false;
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
     * Inicio de la ventana visible, en segundos: donde arranca el eje X
     * mientras la serie no llena los 30 s, y después, 30 s antes del último
     * punto. Así, al comenzar, el eje X va de 0 a 30 s; después de un cambio
     * de canal, desde el tiempo del primer punto del canal nuevo; y después
     * sigue al último punto.
     */
    public double getInicioVentana() {
        return Math.max(inicioEje, ultimoTiempoDibujado - anchoVentana);
    }

    /** Fin de la ventana visible, en segundos: siempre 30 s después del inicio. */
    public double getFinVentana() {
        return getInicioVentana() + anchoVentana;
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
