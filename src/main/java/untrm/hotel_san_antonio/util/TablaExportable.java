package untrm.hotel_san_antonio.util;

import java.util.List;
import java.util.Set;

/**
 * Una tabla lista para guardarse como PDF o como Excel.
 *
 * @param titulo      titulo del informe
 * @param descripcion lineas bajo el titulo (filtros usados, fecha de emision...)
 * @param encabezados nombres de las columnas
 * @param filas       los datos, todo como texto
 * @param numericas   indices de las columnas con importes: van a la derecha y en Excel son numeros
 * @param pesos       ancho relativo de cada columna
 * @param resumen     pares etiqueta / valor que se muestran al final (totales)
 */
public record TablaExportable(String titulo, List<String> descripcion, List<String> encabezados,
                              List<List<String>> filas, Set<Integer> numericas, double[] pesos,
                              List<String[]> resumen) {
}
