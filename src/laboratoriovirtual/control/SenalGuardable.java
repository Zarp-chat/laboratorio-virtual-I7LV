package laboratoriovirtual.control;

import laboratoriovirtual.almacenamiento.HistorialSenal;

/**
 * Una gráfica cuya señal seleccionada se puede guardar en un archivo (R3 y
 * R6). La implementan GraficaAnalogica y GraficaDigital. Existe por la misma
 * razón que CanalSeleccionable: para que el guardado (I7LV-25) sirva igual
 * para las dos pestañas, aunque la gráfica digital no herede de
 * GraficaSenal.
 */
public interface SenalGuardable {

    /**
     * El nombre del canal seleccionado ("A3", "D2") y la copia de su
     * historial completo (todas las muestras desde el último Iniciar, aunque
     * ya no se dibujen), tomados juntos: el historial siempre es el del canal
     * que dice el nombre, aunque en ese mismo momento se cambie de canal o
     * lleguen muestras. Así el archivo corresponde siempre a un solo canal.
     * Se entrega listo para EscritorArchivo.
     *
     * Hilo, con la regla de I7LV-16: se llama desde el hilo de Swing, en la
     * tarea que atiende el botón "Guardar esta señal…". El canal solo cambia
     * en ese hilo, así que se guarda el que el usuario ve elegido al pulsar.
     * La copia es inmutable y no cambia cuando llegan más muestras: después
     * se puede escribir en otro hilo (EscritorArchivo, fuera del hilo de
     * Swing) mientras el muestreo sigue. Desde otro hilo también sale
     * coherente (nombre e historial del mismo canal), pero podría ser de un
     * canal distinto del que el usuario vio al pulsar.
     *
     * Si no hay muestras (antes del primer Iniciar o, en la analógica, justo
     * después de un cambio de canal), el historial viene vacío.
     *
     * @return el canal y su historial
     */
    HistorialSenal getHistorialParaGuardar();
}
