package untrm.hotel_san_antonio.modelo;

import java.math.BigDecimal;

/** Una fila del inventario del almacén: el producto con el nombre de su categoría. */
public record ItemInventario(int id, String codigoBarra, String nombre, String marca, int idCategoria,
                             String categoria, BigDecimal precio, int stock, boolean activo) {
    /** Código corto que se ve en pantalla (P001). */
    public String codigo() {
        return String.format("P%03d", id);
    }
}
