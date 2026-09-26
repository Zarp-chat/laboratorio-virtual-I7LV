package laboratoriovirtual;

import java.util.ArrayList;
import java.util.List;
import laboratoriovirtual.datos.FuenteAleatoria;
import laboratoriovirtual.datos.FuenteDeDatos;
import laboratoriovirtual.datos.FuenteDeDatosException;
import laboratoriovirtual.muestreo.Muestra;
import laboratoriovirtual.muestreo.Muestreador;

/**
 * Verifica que las tareas I7LV-11, I7LV-12 e I7LV-13 cumplen lo que piden.
 *
 * Se ejecuta con Shift+F6 y muestra una línea [OK] o [FALLA] por criterio.
 * Solo usa la interfaz FuenteDeDatos y el Muestreador, igual que el resto
 * del programa, así que también sirve para probar la fuente serial en el
 * Laboratorio 2 cambiando una sola línea.
 */
public class VerificacionSprint1 {

    private static int aprobados = 0;
    private static int fallidos = 0;

    public static void main(String[] args) throws Exception {
        verificarSenalesAnalogicas();
        verificarSenalesDigitales();
        verificarTiempoDeMuestreo();

        System.out.println();
        System.out.println("===================================");
        System.out.println("Resultado: " + aprobados + " aprobados, " + fallidos + " fallidos");
        System.out.println(fallidos == 0
                ? "I7LV-11, I7LV-12 e I7LV-13 cumplen sus criterios."
                : "Hay criterios sin cumplir: revisa las líneas [FALLA].");
    }

    /** Crea la fuente a probar. En el Laboratorio 2 se cambia solo esta línea. */
    private static FuenteDeDatos crearFuente() {
        return new FuenteAleatoria();
    }

    // ===================== I7LV-11 =====================

    private static void verificarSenalesAnalogicas() throws FuenteDeDatosException {
        titulo("I7LV-11  Las 8 señales analógicas");

        FuenteDeDatos fuente = crearFuente();
        fuente.iniciar();

        final int lecturas = 500;
        double[] anterior = fuente.leerAnalogicas();
        verificar("Entrega exactamente 8 valores por lectura",
                anterior.length == 8, "entregó " + anterior.length);

        double minimo = Double.MAX_VALUE;
        double maximo = -Double.MAX_VALUE;
        double saltoMaximo = 0;
        boolean[] canalVario = new boolean[anterior.length];

        for (int n = 0; n < lecturas; n++) {
            double[] actual = fuente.leerAnalogicas();
            for (int c = 0; c < actual.length; c++) {
                minimo = Math.min(minimo, actual[c]);
                maximo = Math.max(maximo, actual[c]);
                saltoMaximo = Math.max(saltoMaximo, Math.abs(actual[c] - anterior[c]));
                if (actual[c] != anterior[c]) {
                    canalVario[c] = true;
                }
            }
            anterior = actual;
        }

        verificar("Todos los valores quedan entre 0 y 5 V",
                minimo >= 0.0 && maximo <= 5.0,
                String.format("mínimo %.3f V, máximo %.3f V", minimo, maximo));

        verificar("Los 8 canales cambian con el tiempo",
                todos(canalVario), "algún canal quedó fijo");

        verificar("Varían de forma suave (sin saltos mayores a 1,5 V entre muestras)",
                saltoMaximo < 1.5, String.format("salto máximo %.3f V", saltoMaximo));

        double[] una = fuente.leerAnalogicas();
        boolean distintos = false;
        for (int c = 1; c < una.length; c++) {
            if (una[c] != una[0]) {
                distintos = true;
            }
        }
        verificar("Los canales son señales distintas entre sí", distintos,
                "los 8 canales tienen el mismo valor");

        fuente.detener();
    }

    // ===================== I7LV-12 =====================

    private static void verificarSenalesDigitales() throws FuenteDeDatosException {
        titulo("I7LV-12  Las 4 señales digitales");

        FuenteDeDatos fuente = crearFuente();
        fuente.iniciar();

        final int lecturas = 500;
        boolean[] anterior = fuente.leerDigitales();
        verificar("Entrega exactamente 4 valores por lectura",
                anterior.length == 4, "entregó " + anterior.length);

        boolean[] vioUno = new boolean[anterior.length];
        boolean[] vioCero = new boolean[anterior.length];
        int cambios = 0;

        for (int n = 0; n < lecturas; n++) {
            boolean[] actual = fuente.leerDigitales();
            for (int c = 0; c < actual.length; c++) {
                if (actual[c]) {
                    vioUno[c] = true;
                } else {
                    vioCero[c] = true;
                }
                if (actual[c] != anterior[c]) {
                    cambios++;
                }
            }
            anterior = actual;
        }

        verificar("Cada canal pasa por 0 y por 1",
                todos(vioUno) && todos(vioCero), "algún canal quedó fijo en un valor");

        double proporcion = (double) cambios / (lecturas * anterior.length);
        verificar("Forman pulsos: cambian en menos de la mitad de las muestras",
                proporcion > 0.0 && proporcion < 0.5,
                String.format("cambian en el %.0f %% de las muestras", proporcion * 100));

        fuente.escribirSalida(0, true);
        fuente.escribirSalida(3, false);
        verificar("Acepta escribir las salidas 0 a 3", true, "");

        boolean rechazaCanalInvalido = false;
        try {
            fuente.escribirSalida(4, true);
        } catch (IllegalArgumentException e) {
            rechazaCanalInvalido = true;
        }
        verificar("Rechaza una salida inexistente (la número 4)",
                rechazaCanalInvalido, "la aceptó sin error");

        fuente.detener();
    }

    // ===================== I7LV-13 =====================

    private static void verificarTiempoDeMuestreo() throws Exception {
        titulo("I7LV-13  El tiempo de muestreo");

        FuenteDeDatos fuente = crearFuente();

        boolean exigeIniciar = false;
        try {
            fuente.leerAnalogicas();
        } catch (FuenteDeDatosException e) {
            exigeIniciar = true;
        }
        verificar("La fuente no entrega datos antes de iniciarla",
                exigeIniciar, "entregó datos sin iniciar");

        Muestreador muestreador = new Muestreador(fuente, 100);
        List<Muestra> recibidas = new ArrayList<>();
        muestreador.agregarOyente(m -> {
            synchronized (recibidas) {
                recibidas.add(m);
            }
        });

        verificar("Arranca con el tiempo pedido (100 ms)",
                muestreador.getPeriodoMs() == 100, "tiene " + muestreador.getPeriodoMs() + " ms");

        // Periodo de 100 ms
        muestreador.iniciar();
        Thread.sleep(2050);
        double promedio100 = intervaloPromedio(recibidas);
        verificar("Con 100 ms, las muestras llegan cada 100 ms (±15 %)",
                cerca(promedio100, 100, 0.15), String.format("promedio %.1f ms", promedio100));

        // Cambio a 50 ms con el muestreo corriendo
        muestreador.setPeriodoMs(50);
        synchronized (recibidas) {
            recibidas.clear();
        }
        Thread.sleep(2025);
        double promedio50 = intervaloPromedio(recibidas);
        verificar("Tras cambiar a 50 ms, llegan cada 50 ms (±15 %)",
                cerca(promedio50, 50, 0.15), String.format("promedio %.1f ms", promedio50));

        boolean tiemposCrecientes = true;
        synchronized (recibidas) {
            for (int i = 1; i < recibidas.size(); i++) {
                if (recibidas.get(i).getTiempo() <= recibidas.get(i - 1).getTiempo()) {
                    tiemposCrecientes = false;
                }
            }
        }
        verificar("El tiempo de cada muestra siempre aumenta", tiemposCrecientes,
                "hay muestras con tiempo repetido o hacia atrás");

        verificar("Rechaza 5 ms (menos del mínimo)", rechazaPeriodo(muestreador, 5), "lo aceptó");
        verificar("Rechaza 20 000 ms (más del máximo)", rechazaPeriodo(muestreador, 20000), "lo aceptó");
        verificar("Tras rechazar, conserva el tiempo anterior (50 ms)",
                muestreador.getPeriodoMs() == 50, "tiene " + muestreador.getPeriodoMs() + " ms");

        // Detener
        muestreador.detener();
        int alDetener;
        synchronized (recibidas) {
            alDetener = recibidas.size();
        }
        Thread.sleep(300);
        int despues;
        synchronized (recibidas) {
            despues = recibidas.size();
        }
        verificar("Al detener, dejan de llegar muestras",
                !muestreador.estaCorriendo() && despues == alDetener,
                "llegaron " + (despues - alDetener) + " muestras después de detener");
    }

    // ===================== Utilidades =====================

    private static double intervaloPromedio(List<Muestra> muestras) {
        synchronized (muestras) {
            if (muestras.size() < 2) {
                return Double.NaN;
            }
            double total = muestras.get(muestras.size() - 1).getTiempo() - muestras.get(0).getTiempo();
            return total / (muestras.size() - 1) * 1000.0; // en milisegundos
        }
    }

    private static boolean rechazaPeriodo(Muestreador muestreador, int ms) throws FuenteDeDatosException {
        try {
            muestreador.setPeriodoMs(ms);
            return false;
        } catch (IllegalArgumentException e) {
            return true;
        }
    }

    private static boolean cerca(double medido, double esperado, double tolerancia) {
        return !Double.isNaN(medido) && Math.abs(medido - esperado) <= esperado * tolerancia;
    }

    private static boolean todos(boolean[] valores) {
        for (boolean v : valores) {
            if (!v) {
                return false;
            }
        }
        return true;
    }

    private static void titulo(String texto) {
        System.out.println();
        System.out.println("--- " + texto + " ---");
    }

    private static void verificar(String criterio, boolean cumple, String detalle) {
        if (cumple) {
            aprobados++;
            System.out.println("[OK]    " + criterio);
        } else {
            fallidos++;
            System.out.println("[FALLA] " + criterio + "  ->  " + detalle);
        }
    }
}