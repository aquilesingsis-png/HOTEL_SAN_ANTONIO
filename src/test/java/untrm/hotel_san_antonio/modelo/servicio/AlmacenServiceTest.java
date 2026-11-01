package untrm.hotel_san_antonio.modelo.servicio;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import untrm.hotel_san_antonio.modelo.Usuario;
import untrm.hotel_san_antonio.modelo.seguridad.SesionActual;

/**
 * Reglas del almacén: lo que no cumple se rechaza antes de llegar a la base de datos, así que estas pruebas
 * no necesitan MySQL ni cambian ningún dato.
 */
class AlmacenServiceTest {

    private final AlmacenService almacen = new AlmacenService();
    private static final BigDecimal PRECIO = new BigDecimal("3.50");

    @BeforeEach
    void entrarComoAdministrador() {
        SesionActual.iniciar(new Usuario(1, "Admin", "General", "admin", "ADMINISTRADOR", true));
    }

    @AfterEach
    void cerrarSesion() {
        SesionActual.cerrar();
    }

    private void mensaje(String esperado, org.junit.jupiter.api.function.Executable accion) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, accion);
        assertEquals(esperado, error.getMessage());
    }

    @Test
    void soloElAdministradorUsaElAlmacen() {
        SesionActual.iniciar(new Usuario(2, "Recep", "Hotel", "recepcion", "RECEPCIONISTA", true));
        assertThrows(SecurityException.class, () -> almacen.guardarProducto(null, "AB-001", "Producto", null, 1, PRECIO, 1, true));
        assertThrows(SecurityException.class, () -> almacen.moverStock(1, "ENTRADA", 1, "Reposicion", null, null, null, null));
        assertThrows(SecurityException.class, () -> almacen.eliminarProducto(1));
        assertThrows(SecurityException.class, () -> almacen.guardarCategoria(null, "Licores"));
    }

    @Test
    void elCodigoDeBarrasDebeTener3A20CaracteresValidos() {
        String error = "El código de barras debe tener 3 a 20 caracteres (letras, números o guion).";
        mensaje(error, () -> almacen.guardarProducto(null, "ab", "Producto", null, 1, PRECIO, 1, true));
        mensaje(error, () -> almacen.guardarProducto(null, "con espacio", "Producto", null, 1, PRECIO, 1, true));
        mensaje(error, () -> almacen.guardarProducto(null, null, "Producto", null, 1, PRECIO, 1, true));
    }

    @Test
    void elNombreEsObligatorioYTieneLargoMaximo() {
        String error = "Ingrese un nombre de producto de hasta 120 caracteres.";
        mensaje(error, () -> almacen.guardarProducto(null, "AB-001", "  ", null, 1, PRECIO, 1, true));
        mensaje(error, () -> almacen.guardarProducto(null, "AB-001", "x".repeat(121), null, 1, PRECIO, 1, true));
    }

    @Test
    void laMarcaNoPuedePasarDe60Caracteres() {
        mensaje("La marca supera 60 caracteres.",
                () -> almacen.guardarProducto(null, "AB-001", "Producto", "m".repeat(61), 1, PRECIO, 1, true));
    }

    @Test
    void elPrecioDebeEstarEntre001Y999999ConDosDecimales() {
        String error = "El precio debe estar entre S/ 0.01 y S/ 9999.99, con hasta dos decimales.";
        mensaje(error, () -> almacen.guardarProducto(null, "AB-001", "Producto", null, 1, BigDecimal.ZERO, 1, true));
        mensaje(error, () -> almacen.guardarProducto(null, "AB-001", "Producto", null, 1, new BigDecimal("-1"), 1, true));
        mensaje(error, () -> almacen.guardarProducto(null, "AB-001", "Producto", null, 1, new BigDecimal("1.234"), 1, true));
        mensaje(error, () -> almacen.guardarProducto(null, "AB-001", "Producto", null, 1, new BigDecimal("10000"), 1, true));
        mensaje(error, () -> almacen.guardarProducto(null, "AB-001", "Producto", null, 1, null, 1, true));
    }

    @Test
    void elStockInicialNoPuedeSerNegativo() {
        mensaje("El stock no puede ser negativo.",
                () -> almacen.guardarProducto(null, "AB-001", "Producto", null, 1, PRECIO, -1, true));
    }

    @Test
    void laCategoriaTieneEntre1Y40Caracteres() {
        String error = "La categoría debe tener entre 1 y 40 caracteres.";
        mensaje(error, () -> almacen.guardarCategoria(null, "   "));
        mensaje(error, () -> almacen.guardarCategoria(null, null));
        mensaje(error, () -> almacen.guardarCategoria(null, "x".repeat(41)));
    }

    @Test
    void elMovimientoDeStockSeValida() {
        mensaje("Seleccione el tipo de movimiento.", () -> almacen.moverStock(1, "OTRO", 1, "Motivo", null, null, null, null));
        mensaje("La cantidad debe ser mayor que cero.", () -> almacen.moverStock(1, "ENTRADA", 0, "Motivo", null, null, null, null));
        mensaje("Indique un motivo válido.", () -> almacen.moverStock(1, "ENTRADA", 1, " ", null, null, null, null));
    }

    @Test
    void unaCompraExigeElCostoPorUnidadYUnMetodoDePagoValido() {
        String costo = "Indique el costo por unidad de la compra (S/ 0.01 a S/ 9999.99, con hasta dos decimales).";
        mensaje(costo, () -> almacen.moverStock(1, "ENTRADA", 5, AlmacenService.MOTIVO_COMPRA, null, null, null, null));
        mensaje(costo, () -> almacen.moverStock(1, "ENTRADA", 5, AlmacenService.MOTIVO_COMPRA, null, null, BigDecimal.ZERO, null));
        mensaje("Seleccione con qué se pagó la compra.",
                () -> almacen.moverStock(1, "ENTRADA", 5, AlmacenService.MOTIVO_COMPRA, null, null, new BigDecimal("1.20"), "CHEQUE"));
    }
}
