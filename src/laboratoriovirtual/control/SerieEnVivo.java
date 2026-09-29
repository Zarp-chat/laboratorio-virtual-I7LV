package laboratoriovirtual.control;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.jfree.data.Range;
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
 * - procesarPendientes(), setVentanaVisible(), vaciarRecibidos(),
 *   vaciarSerie(), getSerie(), getVentanaVisible(), getInicioVentana(),
 *   getFinVentana() y getRangoVisible() se usan solo desde el hilo de Swing
 *   (en el programa, desde el Timer de GraficaSenal, el cambio de canal y el
 *   cambio de ventana). JFreeChart lee la serie al dibujar, también en ese
 *   hilo, así que nunca la usan dos hilos a la vez.
 * - hayPendientes() y getHistorial() se pueden llamar desde cualquier hilo.
 *
 * Candado: el propio objeto. agregar(), vaciarRecibidos() y getHistorial()
 * son synchronized, así que nunca se mezclan entre sí; setVentanaVisible()
 * toma el mismo candado para sacar lo pendiente y copiar lo que necesita del
 * historial en un solo paso. Quien necesite que otro paso ocurra junto con
 * agregar(), sin que nadie se meta en medio, puede tomar este mismo candado
 * con synchronized (serie) { ... }: GraficaSenal lo hace para leer el canal y
 * registrar el punto en un solo paso (I7LV-16). Los candados de Java se
 * pueden volver a tomar desde el mismo hilo, así que llamar a agregar()
 * dentro de ese bloque no se traba.
 *
 * Ventana visible (I7LV-18): su duración empieza en la que recibe el
 * constructor y se cambia con setVentanaVisible(). La serie dibujada tiene
 * los puntos de la ventana y, si el primero de ellos no cae justo en el borde
 * izquierdo, también el último punto anterior al borde: así la línea entra
 * desde el borde aunque las muestras estén muy separadas (por ejemplo, 10 s
 * por muestra con una ventana de 5 s). JFreeChart recorta el dibujo al área
 * de la gráfica, así que ese punto no se ve; solo se ve la línea que sale de
 * él.
 *
 * No sabe de canales ni de Muestra: recibe pares (tiempo, valor). La usa la
 * gráfica analógica. La digital (I7LV-20) registra los cuatro canales a la
 * vez y tiene su propia clase de datos, SenalesDigitalesEnVivo, pero entrega
 * su historial con este mismo tipo Punto.
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

    // Duración de la ventana visible, en segundos. Solo hilo de Swing.
    private double anchoVentana;

    // Puntos recibidos que la serie todavía no tiene. ConcurrentLinkedQueue
    // deja que el hilo de Swing saque sin tomar el candado mientras el
    // Muestreador agrega. Se agrega y se vacía solo con el candado.
    private final Queue<Punto> pendientes = new ConcurrentLinkedQueue<>();

    // Todos los puntos desde el último Iniciar o el último cambio de canal,
    // en orden de tiempo. Lo escribe el hilo del Muestreador y lo puede
    // copiar cualquier otro, así que la lista y ultimoTiempoRecibido solo se
    // usan con el candado.
    private final List<Punto> historial = new ArrayList<>();
    private double ultimoTiempoRecibido = Double.NEGATIVE_INFINITY;

    // Lo que se dibuja: los puntos de la ventana visible (y el anterior al
    // borde, si hace falta). Solo se usan desde el hilo de Swing.
    private final XYSeries serie;
    private double ultimoTiempoDibujado = Double.NEGATIVE_INFINITY;

    // Dónde arranca el eje X mientras la serie no llena la ventana: 0 al
    // comenzar y con cada nuevo Iniciar (el Muestreador cuenta el tiempo
    // desde cero). Después de vaciarSerie() lo decide el primer punto que se
    // dibuje, porque ahí el tiempo no vuelve a cero. Solo hilo de Swing.
    private double inicioEje = 0.0;
    private boolean esperandoPrimerPunto = false;

    /**
     * @param anchoVentanaS duración inicial de la ventana visible, en segundos
     * @throws IllegalArgumentException si no es un número mayor que cero
     */
    public SerieEnVivo(double anchoVentanaS) {
        validarAncho(anchoVentanaS);
        this.anchoVentana = anchoVentanaS;
        // El primer "false" dice que no hace falta ordenar los puntos: llegan
        // en orden de tiempo, y un tiempo menor se trata como un nuevo
        // Iniciar. El "true" acepta dos puntos con el mismo tiempo; con
        // "false", JFreeChart recorrería toda la serie en cada add() buscando
        // un tiempo repetido, trabajo inútil con 6000 puntos en la ventana.
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
     * Vacía la serie dibujada (cambio de canal). Mientras no se dibuje un
     * punto nuevo, la ventana visible va de 0 a su duración (I7LV-18). Con
     * el primer punto que se dibuje después, el eje X arranca en el tiempo de
     * ese punto (ver procesarPendientes()), porque el tiempo no vuelve a cero.
     *
     * Si todavía no se ha dibujado nada, la gráfica sigue como al comenzar:
     * el primer punto que llegue será el del primer Iniciar, cuyo tiempo
     * empieza en cero.
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
                dibujar(punto);
            }
            quitarPuntosViejos();
        } finally {
            // Al volver a encenderlo, la serie avisa un solo cambio con todo
            serie.setNotify(true);
        }
    }

    /**
     * Cambia la duración de la ventana visible (I7LV-18) y reconstruye la
     * serie dibujada desde el historial: al agrandarla reaparecen los puntos
     * que ya habían salido; al achicarla, se recorta. El historial no cambia.
     * La serie avisa una sola vez. Pedir la duración que ya tiene no hace
     * nada.
     *
     * Con el candado, en un solo paso, se sacan de la cola los puntos que la
     * serie todavía no tiene y se copia del historial solo el tramo que se
     * ve con la ventana nueva. Como agregar() guarda cada punto en el
     * historial y en la cola con ese mismo candado, cada punto recibido queda
     * en la copia y fuera de la cola, o fuera de la copia y en la cola (para
     * el ciclo siguiente del Timer): nunca se dibuja dos veces ni se pierde.
     * Los puntos sacados de la cola se procesan como en procesarPendientes(),
     * para que un nuevo Iniciar o el primer punto después de un cambio de
     * canal muevan el eje X como siempre.
     *
     * @param segundos nueva duración, mayor que cero
     * @throws IllegalArgumentException si no es un número mayor que cero
     */
    public void setVentanaVisible(double segundos) {
        validarAncho(segundos);
        if (segundos == anchoVentana) {
            return;
        }
        List<Punto> nuevos = new ArrayList<>();
        List<Punto> tramo;
        synchronized (this) {
            Punto punto;
            while ((punto = pendientes.poll()) != null) {
                nuevos.add(punto);
            }
            tramo = tramoVisible(segundos);
        }
        anchoVentana = segundos;
        serie.setNotify(false);
        try {
            for (Punto punto : nuevos) {
                dibujar(punto);
            }
            serie.clear();
            for (Punto punto : tramo) {
                serie.add(punto.tiempo(), punto.valor(), false);
            }
        } finally {
            serie.setNotify(true);
        }
    }

    /** La serie que dibuja la gráfica: los puntos de la ventana visible. */
    public XYSeries getSerie() {
        return serie;
    }

    /** Duración de la ventana visible, en segundos. */
    public double getVentanaVisible() {
        return anchoVentana;
    }

    /**
     * Inicio de la ventana visible, en segundos: donde arranca el eje X
     * mientras la serie no llena la ventana, y después, la duración de la
     * ventana antes del último punto. Así, al comenzar, el eje X va de 0 a la
     * duración de la ventana; después de un cambio de canal, de 0 mientras
     * no llegue un punto y después desde el tiempo del primer punto del canal
     * nuevo; y a partir de ahí sigue al último punto.
     */
    public double getInicioVentana() {
        if (esperandoPrimerPunto) {
            return 0.0;
        }
        return Math.max(inicioEje, ultimoTiempoDibujado - anchoVentana);
    }

    /** Fin de la ventana visible, en segundos: siempre su duración después del inicio. */
    public double getFinVentana() {
        return getInicioVentana() + anchoVentana;
    }

    /**
     * Valores mínimo y máximo que se ven de la señal (I7LV-18, escala
     * automática): los de los puntos dentro de la ventana visible y, si la
     * línea entra desde el borde izquierdo, el valor con que entra
     * (interpolado entre el punto anterior al borde y el siguiente). El punto
     * anterior al borde no cuenta: no se ve.
     *
     * @return el rango de los valores visibles, o null si no hay puntos
     */
    public Range getRangoVisible() {
        double inicio = getInicioVentana();
        double minimo = Double.POSITIVE_INFINITY;
        double maximo = Double.NEGATIVE_INFINITY;
        // Desde el final: los puntos visibles son los últimos
        for (int i = serie.getItemCount() - 1; i >= 0; i--) {
            double x = serie.getX(i).doubleValue();
            double y = serie.getY(i).doubleValue();
            if (x < inicio) {
                if (i + 1 < serie.getItemCount()) {
                    double xSiguiente = serie.getX(i + 1).doubleValue();
                    double ySiguiente = serie.getY(i + 1).doubleValue();
                    double enBorde = y + (ySiguiente - y) * (inicio - x) / (xSiguiente - x);
                    minimo = Math.min(minimo, enBorde);
                    maximo = Math.max(maximo, enBorde);
                }
                break;
            }
            minimo = Math.min(minimo, y);
            maximo = Math.max(maximo, y);
        }
        return minimo <= maximo ? new Range(minimo, maximo) : null;
    }

    // ===================== Utilidades =====================

    /**
     * Agrega un punto a la serie, con lo que eso implica para el eje X. Lo
     * usan procesarPendientes() y setVentanaVisible().
     */
    private void dibujar(Punto punto) {
        if (punto.tiempo() < ultimoTiempoDibujado) {
            // Nuevo Iniciar: el tiempo volvió a empezar desde cero
            serie.clear();
            inicioEje = 0.0;
            esperandoPrimerPunto = false;
        } else if (esperandoPrimerPunto) {
            // Primer punto después de vaciarSerie() (cambio de canal): el
            // tiempo siguió corriendo, así que el eje X arranca aquí y no en 0
            inicioEje = punto.tiempo();
            esperandoPrimerPunto = false;
        }
        serie.add(punto.tiempo(), punto.valor(), false);
        ultimoTiempoDibujado = punto.tiempo();
    }

    /**
     * Quita de la serie los puntos anteriores al borde izquierdo de la
     * ventana, menos el último de ellos si el primer punto que queda no cae
     * justo en el borde: ese punto hace que la línea entre desde el borde.
     */
    private void quitarPuntosViejos() {
        double limite = ultimoTiempoDibujado - anchoVentana;
        int viejos = 0;
        while (viejos < serie.getItemCount()
                && serie.getX(viejos).doubleValue() < limite) {
            viejos++;
        }
        // El último punto de la serie nunca es viejo, así que después de los
        // viejos siempre queda al menos uno
        if (viejos > 0 && serie.getX(viejos).doubleValue() > limite) {
            viejos--; // se conserva el anterior al borde
        }
        if (viejos > 0) {
            // Todos de una vez: la serie recalcula sus mínimos y máximos una
            // sola vez, en vez de una vez por punto quitado.
            serie.delete(0, viejos - 1);
        }
    }

    /**
     * Copia del tramo del historial que se ve con una ventana de esa
     * duración: los puntos desde el borde izquierdo hasta el último, más el
     * anterior al borde con la misma regla que quitarPuntosViejos(). Se
     * llama con el candado. Solo copia ese tramo, no todo el historial: con
     * una hora de muestreo son 360 000 puntos, y el Muestreador espera
     * mientras se copia.
     */
    private List<Punto> tramoVisible(double ancho) {
        int n = historial.size();
        if (n == 0) {
            return List.of();
        }
        double limite = historial.get(n - 1).tiempo() - ancho;
        // Búsqueda binaria del primer punto con tiempo >= limite: el
        // historial está en orden de tiempo (un tiempo menor lo vacía)
        int bajo = 0;
        int alto = n - 1;
        while (bajo < alto) {
            int medio = (bajo + alto) >>> 1;
            if (historial.get(medio).tiempo() < limite) {
                bajo = medio + 1;
            } else {
                alto = medio;
            }
        }
        int primero = bajo;
        if (primero > 0 && historial.get(primero).tiempo() > limite) {
            primero--; // el anterior al borde
        }
        return new ArrayList<>(historial.subList(primero, n));
    }

    private static void validarAncho(double segundos) {
        if (!(segundos > 0) || Double.isInfinite(segundos)) {
            throw new IllegalArgumentException("La ventana visible debe durar más de 0 s: " + segundos);
        }
    }
}
