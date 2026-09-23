package laboratoriovirtual.muestreo;

import laboratoriovirtual.datos.FuenteDeDatosException;

/**
 * La implementa quien quiera recibir las muestras: la gráfica,
 * el guardado en archivo, etc.
 *
 * IMPORTANTE: estos métodos se llaman desde el hilo de muestreo,
 * no desde el hilo de Swing. Si el oyente toca la interfaz gráfica,
 * debe hacerlo dentro de SwingUtilities.invokeLater.
 */
public interface OyenteMuestras {

    /** Llega una muestra nueva. */
    void muestraRecibida(Muestra muestra);

    /**
     * La fuente falló y el muestreo se detuvo.
     * Por defecto no hace nada; la ventana lo sobrescribirá para avisar al usuario.
     */
    default void errorEnFuente(FuenteDeDatosException error) {
    }
}
