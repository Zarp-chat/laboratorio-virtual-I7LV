package laboratoriovirtual.datos;

/**
 * Error al comunicarse con la fuente de datos.
 *
 * En el Laboratorio 1 nunca se lanza (los datos aleatorios no fallan),
 * pero en el Laboratorio 2 representa fallos del puerto serie:
 * puerto desconectado, trama incompleta, tiempo de espera agotado, etc.
 */
public class FuenteDeDatosException extends Exception {

    private static final long serialVersionUID = 1L;

    public FuenteDeDatosException(String mensaje) {
        super(mensaje);
    }

    public FuenteDeDatosException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
