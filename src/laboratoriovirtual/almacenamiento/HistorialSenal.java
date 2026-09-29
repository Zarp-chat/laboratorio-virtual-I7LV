package laboratoriovirtual.almacenamiento;

import java.util.Objects;

/**
 * El historial de una señal, listo para escribirlo en un archivo con
 * EscritorArchivo: el nombre del canal ("A3", "D2"), el tipo de señal y sus
 * muestras (tiempo y valor), en orden.
 *
 * Es el tipo propio del paquete almacenamiento: no depende de Swing ni de
 * las gráficas. Las gráficas lo arman a partir de su historial
 * (getHistorialParaGuardar(), en el paquete control).
 *
 * Es inmutable: el constructor copia los arreglos y nada los cambia después.
 * Así se puede armar en el hilo de Swing y escribir en otro hilo sin
 * candados, aunque el muestreo siga.
 */
public final class HistorialSenal {

    /** Tipo de señal: decide cómo se escribe el valor en el archivo. */
    public enum Tipo {
        /** En voltios: el valor se escribe con 3 decimales, por ejemplo 3.720. */
        ANALOGICA,
        /** 0 o 1: el valor se escribe sin decimales. */
        DIGITAL
    }

    private final String canal;
    private final Tipo tipo;
    private final double[] tiempos;
    private final double[] valores;

    /**
     * @param canal   nombre del canal, por ejemplo "A3" o "D2"
     * @param tipo    tipo de señal
     * @param tiempos tiempo de cada muestra, en segundos
     * @param valores valor de cada muestra: voltios (analógica), o 0 y 1
     *                (digital)
     * @throws NullPointerException     si algún parámetro es null
     * @throws IllegalArgumentException si los arreglos no tienen el mismo
     *                                  largo, si un tiempo o un valor no es
     *                                  un número finito, o si un valor
     *                                  digital no es 0 ni 1
     */
    public HistorialSenal(String canal, Tipo tipo, double[] tiempos, double[] valores) {
        this.canal = Objects.requireNonNull(canal, "El nombre del canal no puede ser null");
        this.tipo = Objects.requireNonNull(tipo, "El tipo de señal no puede ser null");
        if (tiempos.length != valores.length) {
            throw new IllegalArgumentException("Hay " + tiempos.length + " tiempos y "
                    + valores.length + " valores: debe haber uno de cada por muestra");
        }
        this.tiempos = tiempos.clone();
        this.valores = valores.clone();
        // Se revisan las copias: nadie las puede cambiar entre la revisión y
        // la escritura. NaN o infinito se escribirían como "NaN" o
        // "Infinity", que no son números para quien lea el archivo (Excel,
        // el Laboratorio 2).
        for (int i = 0; i < this.tiempos.length; i++) {
            if (!Double.isFinite(this.tiempos[i])) {
                throw new IllegalArgumentException("El tiempo de la muestra " + i
                        + " no es un número finito: " + this.tiempos[i]);
            }
            if (!Double.isFinite(this.valores[i])) {
                throw new IllegalArgumentException("El valor de la muestra " + i
                        + " no es un número finito: " + this.valores[i]);
            }
            if (tipo == Tipo.DIGITAL && this.valores[i] != 0.0 && this.valores[i] != 1.0) {
                throw new IllegalArgumentException("El valor digital de la muestra " + i
                        + " debe ser 0 o 1 y es " + this.valores[i]);
            }
        }
    }

    /** Nombre del canal, por ejemplo "A3" o "D2". */
    public String getCanal() {
        return canal;
    }

    /** Tipo de señal: decide cómo se escribe el valor. */
    public Tipo getTipo() {
        return tipo;
    }

    /** Cantidad de muestras: las filas que tendrá el archivo. */
    public int getCantidad() {
        return tiempos.length;
    }

    /** Tiempo de la muestra número i (desde 0), en segundos. */
    public double getTiempo(int i) {
        return tiempos[i];
    }

    /** Valor de la muestra número i (desde 0): voltios, o 0 y 1. */
    public double getValor(int i) {
        return valores[i];
    }
}
