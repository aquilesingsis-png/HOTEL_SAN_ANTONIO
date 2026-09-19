package untrm.hotel_san_antonio.modelo;

import java.math.BigDecimal;

public class TipoHabitacion {
    private int idTipo;
    private String nombre;
    private int capacidad;
    private BigDecimal precioBase;

    public TipoHabitacion() {}

    public TipoHabitacion(int idTipo, String nombre, int capacidad, BigDecimal precioBase) {
        this.idTipo = idTipo;
        this.nombre = nombre;
        this.capacidad = capacidad;
        this.precioBase = precioBase;
    }

    public int getIdTipo() { return idTipo; }
    public void setIdTipo(int idTipo) { this.idTipo = idTipo; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public int getCapacidad() { return capacidad; }
    public void setCapacidad(int capacidad) { this.capacidad = capacidad; }

    public BigDecimal getPrecioBase() { return precioBase; }
    public void setPrecioBase(BigDecimal precioBase) { this.precioBase = precioBase; }

    @Override
    public String toString() { return nombre; }
}
