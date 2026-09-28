package laboratoriovirtual;

import java.util.Locale;
import laboratoriovirtual.datos.FuenteAleatoria;
import laboratoriovirtual.datos.FuenteDeDatos;
import laboratoriovirtual.muestreo.Muestreador;

/**
 * Prueba temporal del Sprint 1: verifica que los datos se producen
 * por dentro antes de tener la ventana. Se puede borrar después.
 */
public class PruebaConsola {

    public static void main(String[] args) throws Exception {
        // ÚNICO lugar donde se elige la implementación.
        // En el Laboratorio 2 esta línea pasa a ser: new FuenteSerial(...)
        FuenteDeDatos fuente = new FuenteAleatoria();

        Muestreador muestreador = new Muestreador(fuente, 500);

        muestreador.agregarOyente(m -> System.out.printf(Locale.US,
                "t=%6.3f s | A0=%.2f V  A7=%.2f V | D0=%d D3=%d%n",
                m.getTiempo(), m.getAnalogica(0), m.getAnalogica(7),
                m.getDigital(0) ? 1 : 0, m.getDigital(3) ? 1 : 0));

        // El Muestreador no conecta la fuente: se conecta antes de iniciarlo
        fuente.iniciar();
        muestreador.iniciar();
        System.out.println("--- Periodo: " + muestreador.getPeriodoMs() + " ms");
        Thread.sleep(2600);

        muestreador.setPeriodoMs(200);
        System.out.println("--- Periodo: " + muestreador.getPeriodoMs() + " ms");
        fuente.escribirSalida(0, true);
        Thread.sleep(1100);

        muestreador.detener();
        fuente.detener();
        System.out.println("--- Muestreo detenido");
    }
}
