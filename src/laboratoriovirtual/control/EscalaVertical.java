package laboratoriovirtual.control;

/**
 * Escalas del eje Y de la gráfica analógica (I7LV-18). El texto de cada una
 * es el que muestra el selector "Escala:" de la pestaña.
 */
public enum EscalaVertical {

    /** De 0 a 5 V: el rango completo de las entradas analógicas. Es la escala al comenzar. */
    CERO_A_5_V("0 a 5 V", 0.0, 5.0),

    /**
     * De 0 a 3,3 V, para señales de 3,3 V (por ejemplo, las de un
     * microcontrolador que trabaja a 3,3 V). Los valores por encima de
     * 3,3 V quedan fuera del área visible: la línea se corta en el borde
     * superior. Es lo esperado: quien elige esta escala quiere ver en detalle
     * ese rango.
     */
    CERO_A_3_3_V("0 a 3,3 V", 0.0, 3.3),

    /**
     * Se ajusta a los valores visibles, con un pequeño margen y un rango
     * mínimo, para que una señal casi plana no parezca un ruido enorme (ver
     * GraficaAnalogica). No tiene límites fijos.
     */
    AUTOMATICA("Automática", Double.NaN, Double.NaN);

    private final String texto;
    private final double minimo;
    private final double maximo;

    EscalaVertical(String texto, double minimo, double maximo) {
        this.texto = texto;
        this.minimo = minimo;
        this.maximo = maximo;
    }

    /** Si el eje se ajusta a los datos en vez de tener límites fijos. */
    public boolean esAutomatica() {
        return this == AUTOMATICA;
    }

    /** Límite inferior del eje, en voltios. NaN en la automática. */
    public double getMinimo() {
        return minimo;
    }

    /** Límite superior del eje, en voltios. NaN en la automática. */
    public double getMaximo() {
        return maximo;
    }

    /** El texto que muestra el selector, por ejemplo "0 a 3,3 V". */
    @Override
    public String toString() {
        return texto;
    }
}
