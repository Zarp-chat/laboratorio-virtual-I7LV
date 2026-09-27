package laboratoriovirtual.control;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.jfree.data.xy.XYSeries;

/**
 * Datos de las señales digitales que se grafican en vivo (I7LV-20): la cola
 * entre hilos, la ventana visible de tiempo, el historial completo y la serie
 * de cada carril del diagrama de tiempos.
 *
 * Es la parte de GraficaDigital que no dibuja nada. Está aparte por la misma
 * razón que SerieEnVivo: así se puede probar sin ventana
 * (VerificacionSprint2).
 *
 * Cada lectura trae todos los canales a la vez, combinados en un número: el
 * canal i es el bit i (D0 el bit menos significativo). Es lo mismo que
 * mandará el microcontrolador en el Laboratorio 2: los 4 interruptores en
 * 1 byte de cada trama.
 *
 * Historial: se registran SIEMPRE todos los canales, no solo el
 * seleccionado. Se guarda una lectura (tiempo y bits) por muestra, y de ahí
 * sale el historial completo de cada canal (getHistorial(canal)): una fila
 * por muestra, con 0 o 1. Guardar los bits juntos, en vez de una lista por
 * canal, ocupa la cuarta parte de memoria y deja los cuatro historiales
 * siempre con los mismos tiempos.
 *
 * Series dibujadas: una por canal (0 y 1) y la del carril "Valor" (el número
 * combinado, de 0 a 15). Solo guardan los puntos donde el valor cambia, más
 * el último: la onda en escalones y los tramos del bus se ven igual y la
 * serie queda mucho más corta (una señal fija durante 30 s a 10 ms son 2
 * puntos, no 3000).
 *
 * Reparto de hilos: el mismo de SerieEnVivo.
 * - agregar() lo llama el hilo del Muestreador. Guarda la lectura en el
 *   historial y en la cola; no toca las series.
 * - procesarPendientes(), getSerieCanal(), getSerieValor(),
 *   getInicioVentana() y getFinVentana() se usan solo desde el hilo de Swing
 *   (en el programa, desde el Timer de GraficaDigital). JFreeChart lee las
 *   series al dibujar, también en ese hilo.
 * - hayPendientes() y getHistorial() se pueden llamar desde cualquier hilo.
 *
 * Candado: el propio objeto. agregar() es synchronized y getHistorial() copia
 * el historial con ese mismo candado.
 */
public class SenalesDigitalesEnVivo {

    /**
     * Una lectura de todas las entradas digitales. Es inmutable, así que la
     * misma lectura puede estar en la cola y en el historial, y pasar de un
     * hilo a otro.
     *
     * @param tiempo segundos desde el último Iniciar
     * @param bits   estado de cada canal: el bit i es el canal i
     */
    private record Lectura(double tiempo, int bits) {
    }

    /** Cantidad de canales: los bits que se usan de cada lectura. */
    private final int canales;

    /** Ancho de la ventana visible, en segundos. */
    private final double anchoVentana;

    // Lecturas recibidas que las series todavía no tienen. Como en
    // SerieEnVivo: el hilo de Swing saca sin tomar el candado mientras el
    // Muestreador agrega.
    private final Queue<Lectura> pendientes = new ConcurrentLinkedQueue<>();

    // Todas las lecturas desde el último Iniciar. Lo escribe el hilo del
    // Muestreador y lo puede copiar cualquier otro, así que la lista y
    // ultimoTiempoRecibido solo se usan con el candado.
    private final List<Lectura> historial = new ArrayList<>();
    private double ultimoTiempoRecibido = Double.NEGATIVE_INFINITY;

    // Lo que se dibuja: un carril por canal y el del valor combinado.
    // Solo se usan desde el hilo de Swing.
    private final Carril[] carrilesCanal;
    private final Carril carrilValor;
    private final List<Carril> todos = new ArrayList<>();
    private double ultimoTiempoDibujado = Double.NEGATIVE_INFINITY;

    /**
     * @param canales       cantidad de canales digitales (de 1 a 30)
     * @param anchoVentanaS ancho de la ventana visible, en segundos
     */
    public SenalesDigitalesEnVivo(int canales, double anchoVentanaS) {
        if (canales < 1 || canales > 30) {
            throw new IllegalArgumentException("Cantidad de canales no válida: " + canales);
        }
        this.canales = canales;
        this.anchoVentana = anchoVentanaS;
        carrilesCanal = new Carril[canales];
        for (int i = 0; i < canales; i++) {
            carrilesCanal[i] = new Carril("Canal " + i);
            todos.add(carrilesCanal[i]);
        }
        carrilValor = new Carril("Valor");
        todos.add(carrilValor);
    }

    // ===================== Hilo del Muestreador =====================

    /**
     * Recibe una lectura nueva. La guarda en el historial y la deja en la
     * cola para que procesarPendientes() la pase a las series. No toca las
     * series ni Swing, así que se puede llamar desde el hilo del Muestreador.
     *
     * Si el tiempo es menor que el de la lectura anterior, es un nuevo
     * Iniciar: el historial se vacía antes de guardarla.
     *
     * @param tiempo segundos desde el último Iniciar
     * @param bits   estado de cada canal: el bit i es el canal i
     */
    public synchronized void agregar(double tiempo, int bits) {
        Lectura lectura = new Lectura(tiempo, bits);
        if (tiempo < ultimoTiempoRecibido) {
            historial.clear();
        }
        historial.add(lectura);
        ultimoTiempoRecibido = tiempo;
        pendientes.add(lectura);
    }

    // ===================== Cualquier hilo =====================

    /** Indica si hay lecturas recibidas que las series todavía no tienen. */
    public boolean hayPendientes() {
        return !pendientes.isEmpty();
    }

    /**
     * Devuelve el historial completo de un canal: un punto (tiempo, 0 o 1)
     * por cada lectura recibida desde el último Iniciar, en orden de tiempo,
     * incluso las que ya salieron de la ventana visible o que la gráfica
     * todavía no dibuja. Pensado para guardar la señal en un archivo
     * (Sprint 3).
     *
     * Seguro entre hilos, como SerieEnVivo.getHistorial(): se copia con el
     * mismo candado con el que agregar() escribe, así que nunca sale a medio
     * actualizar. La lista devuelta es inmutable y no cambia cuando llegan
     * más muestras.
     *
     * @param canal número del canal, desde 0
     * @return lista inmutable de puntos (tiempo, valor)
     * @throws IllegalArgumentException si el canal no existe
     */
    public List<SerieEnVivo.Punto> getHistorial(int canal) {
        if (canal < 0 || canal >= canales) {
            throw new IllegalArgumentException("No existe el canal digital " + canal);
        }
        // Con el candado solo la copia, que es rápida: así el Muestreador no
        // espera mientras se arman los puntos
        List<Lectura> copia;
        synchronized (this) {
            copia = List.copyOf(historial);
        }
        List<SerieEnVivo.Punto> puntos = new ArrayList<>(copia.size());
        for (Lectura lectura : copia) {
            puntos.add(new SerieEnVivo.Punto(lectura.tiempo(), estado(lectura.bits(), canal)));
        }
        return Collections.unmodifiableList(puntos);
    }

    // ===================== Hilo de Swing =====================

    /**
     * Pasa a las series todas las lecturas de la cola y quita de ellas lo que
     * salió de la ventana visible. Mientras tanto las series no avisan a la
     * gráfica; avisan al final, todas en el mismo momento.
     *
     * Si una lectura tiene un tiempo menor que la anterior, es un nuevo
     * Iniciar: las series se vacían antes de agregarla y el eje X vuelve a
     * arrancar en 0.
     */
    public void procesarPendientes() {
        if (pendientes.isEmpty()) {
            return;
        }
        for (Carril carril : todos) {
            carril.empezarCiclo();
        }
        try {
            Lectura lectura;
            while ((lectura = pendientes.poll()) != null) {
                if (lectura.tiempo() < ultimoTiempoDibujado) {
                    // Nuevo Iniciar: el tiempo volvió a empezar desde cero
                    for (Carril carril : todos) {
                        carril.vaciar();
                    }
                }
                for (int i = 0; i < canales; i++) {
                    carrilesCanal[i].agregar(lectura.tiempo(), estado(lectura.bits(), i));
                }
                carrilValor.agregar(lectura.tiempo(), lectura.bits());
                ultimoTiempoDibujado = lectura.tiempo();
            }
            double inicioVentana = ultimoTiempoDibujado - anchoVentana;
            for (Carril carril : todos) {
                carril.terminarCiclo(inicioVentana);
            }
        } finally {
            // Al volver a encenderlos, cada serie avisa un solo cambio con todo
            for (Carril carril : todos) {
                carril.serie.setNotify(true);
            }
        }
    }

    /** Serie que dibuja el carril de un canal: 0 y 1, solo los cambios y el último punto. */
    public XYSeries getSerieCanal(int canal) {
        return carrilesCanal[canal].serie;
    }

    /** Serie que dibuja el carril "Valor": los bits combinados, solo los cambios y el último punto. */
    public XYSeries getSerieValor() {
        return carrilValor.serie;
    }

    /**
     * Inicio de la ventana visible, en segundos: 0 mientras no se llenan los
     * 30 s (al comenzar y con cada nuevo Iniciar), y después, 30 s antes de
     * la última lectura dibujada. Es la misma ventana de la gráfica
     * analógica.
     */
    public double getInicioVentana() {
        return Math.max(0.0, ultimoTiempoDibujado - anchoVentana);
    }

    /** Fin de la ventana visible, en segundos: siempre 30 s después del inicio. */
    public double getFinVentana() {
        return getInicioVentana() + anchoVentana;
    }

    // ===================== Utilidades =====================

    /** Estado de un canal dentro de los bits de una lectura: 1.0 o 0.0. */
    private static double estado(int bits, int canal) {
        return (bits >> canal) & 1;
    }

    /**
     * La serie de un carril, con solo los puntos donde el valor cambia más un
     * punto final. Solo se usa desde el hilo de Swing.
     *
     * El punto final prolonga el último valor hasta la última lectura; sin
     * él, una señal que no cambia no llegaría hasta el borde derecho. Como
     * cada lectura nueva lo movería, se quita al empezar cada ciclo del Timer
     * y se vuelve a poner al terminarlo, más adelante: una vez por ciclo y
     * no una vez por lectura. Si la última lectura fue un cambio, ella misma
     * es el último punto y no hace falta.
     */
    private static final class Carril {

        final XYSeries serie;

        // Valor que tiene la señal ahora: el del último punto de cambio
        private double valorActual;

        // Tiempo de la última lectura agregada, haya sido un cambio o no
        private double tiempoActual;

        // Si el último punto de la serie es el punto final
        private boolean hayPuntoFinal = false;

        Carril(String nombre) {
            // Como en SerieEnVivo: los puntos llegan en orden de tiempo (sin
            // ordenar) y se acepta repetir un tiempo (sin buscar repetidos).
            serie = new XYSeries(nombre, false, true);
        }

        /** Apaga los avisos de la serie y quita el punto final. */
        void empezarCiclo() {
            serie.setNotify(false);
            if (hayPuntoFinal) {
                serie.remove(serie.getItemCount() - 1);
                hayPuntoFinal = false;
            }
        }

        /** Agrega un punto solo si el valor cambió (o si la serie está vacía). */
        void agregar(double tiempo, double valor) {
            if (serie.isEmpty() || valor != valorActual) {
                serie.add(tiempo, valor, false);
                valorActual = valor;
            }
            tiempoActual = tiempo;
        }

        /** Pone el punto final, si hace falta, y quita los puntos viejos. */
        void terminarCiclo(double inicioVentana) {
            int n = serie.getItemCount();
            if (n > 0 && serie.getX(n - 1).doubleValue() < tiempoActual) {
                serie.add(tiempoActual, valorActual, false);
                hayPuntoFinal = true;
            }
            quitarPuntosViejos(inicioVentana);
        }

        /** Vacía la serie (nuevo Iniciar). */
        void vaciar() {
            serie.clear();
            hayPuntoFinal = false;
        }

        /**
         * Quita los puntos anteriores al inicio de la ventana, menos el
         * último de ellos: ese dice qué valor tiene la señal en el borde
         * izquierdo. Sin él, una señal que no cambia desde hace más de 30 s
         * se quedaría sin línea. El punto que queda fuera de la ventana no se
         * ve: JFreeChart recorta el dibujo al área de la gráfica.
         */
        private void quitarPuntosViejos(double inicioVentana) {
            int viejos = 0;
            while (viejos < serie.getItemCount()
                    && serie.getX(viejos).doubleValue() < inicioVentana) {
                viejos++;
            }
            if (viejos > 1) {
                // Todos de una vez, como en SerieEnVivo
                serie.delete(0, viejos - 2);
            }
        }
    }
}
