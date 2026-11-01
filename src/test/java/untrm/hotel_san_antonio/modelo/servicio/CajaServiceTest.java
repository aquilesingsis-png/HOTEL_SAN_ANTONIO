package untrm.hotel_san_antonio.modelo.servicio;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import untrm.hotel_san_antonio.modelo.EntradaCierre;
import untrm.hotel_san_antonio.modelo.ResumenCierre;
import untrm.hotel_san_antonio.modelo.Usuario;
import untrm.hotel_san_antonio.modelo.seguridad.SesionActual;

/** Reglas de la caja: lo inválido se rechaza antes de llegar a la base de datos (no necesita MySQL). */
class CajaServiceTest {

    private final CajaService caja = new CajaService();

    @BeforeEach
    void entrarComoAdministrador() {
        SesionActual.iniciar(new Usuario(1, "Admin", "General", "admin", "ADMINISTRADOR", true));
    }

    @AfterEach
    void cerrarSesion() {
        SesionActual.cerrar();
    }

    private void rechaza(String esperado, org.junit.jupiter.api.function.Executable accion) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, accion);
        assertEquals(esperado, error.getMessage());
    }

    private EntradaCierre cierre(String turno, String contado, String fondoSiguiente) {
        return new EntradaCierre(turno, new BigDecimal("200"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal(contado), new BigDecimal(fondoSiguiente), null);
    }

    @Test
    void unMovimientoManualSoloLoHaceElAdministrador() {
        SesionActual.iniciar(new Usuario(2, "Recep", "Hotel", "recepcion", "RECEPCIONISTA", true));
        assertThrows(SecurityException.class, () -> caja.guardarMovimientoCaja("INGRESO", "Concepto", "EFECTIVO",
                new BigDecimal("10"), null, null, "Mañana"));
    }

    @Test
    void elMovimientoManualSeValida() {
        BigDecimal diez = new BigDecimal("10");
        rechaza("Seleccione ingreso o egreso.", () -> caja.guardarMovimientoCaja("OTRO", "Concepto", "EFECTIVO", diez, null, null, "Mañana"));
        rechaza("Indique un concepto de hasta 150 caracteres.", () -> caja.guardarMovimientoCaja("INGRESO", " ", "EFECTIVO", diez, null, null, "Mañana"));
        rechaza("Seleccione un método de pago válido.", () -> caja.guardarMovimientoCaja("INGRESO", "Concepto", "CHEQUE", diez, null, null, "Mañana"));
        String monto = "El monto debe ser mayor que cero, con hasta dos decimales.";
        rechaza(monto, () -> caja.guardarMovimientoCaja("INGRESO", "Concepto", "EFECTIVO", BigDecimal.ZERO, null, null, "Mañana"));
        rechaza(monto, () -> caja.guardarMovimientoCaja("INGRESO", "Concepto", "EFECTIVO", new BigDecimal("1.234"), null, null, "Mañana"));
        rechaza("Indique el turno o caja (hasta 50 caracteres).", () -> caja.guardarMovimientoCaja("INGRESO", "Concepto", "EFECTIVO", diez, null, null, " "));
    }

    @Test
    void elCierreExigeElTurno() {
        rechaza("Seleccione el turno que se cierra.", () -> caja.guardarCierre(cierre(" ", "100", "50"), false));
    }

    @Test
    void elEfectivoContadoNoPuedeSerNegativo() {
        rechaza("El efectivo contado debe ser un importe no negativo con hasta dos decimales.",
                () -> caja.guardarCierre(cierre("Mañana", "-1", "0"), false));
    }

    @Test
    void laCajaChicaQueQuedaNoPuedeSuperarLoContado() {
        rechaza("La caja chica que queda no puede superar el efectivo contado.",
                () -> caja.guardarCierre(cierre("Mañana", "100", "150"), false));
    }

    @Test
    void elEsperadoEsFondoMasCobrosMasOtrosMenosSalidas() {
        ResumenCierre resumen = new ResumenCierre(new BigDecimal("200"), new BigDecimal("150"), new BigDecimal("20"),
                new BigDecimal("70"), null, null, true);
        assertEquals(new BigDecimal("300"), resumen.esperado());
    }

    @Test
    void listarCierresSinSesionSeRechaza() {
        SesionActual.cerrar();
        assertThrows(SecurityException.class, () -> caja.listarCierres());
    }
}
