package untrm.hotel_san_antonio.modelo;

/**
 * Empresa a la que se factura (tabla empresa). Tambien es el resultado de
 * consultar la API de RUC (SUNAT): estado y condicion vienen de la API y
 * no se guardan en la BD.
 */
public class Empresa {
    private int idEmpresa;
    private String ruc;
    private String razonSocial;
    private String direccion;   // columna direccion_fiscal
    private String estado;      // solo API
    private String condicion;   // solo API

    public Empresa() {}

    public int getIdEmpresa() { return idEmpresa; }
    public void setIdEmpresa(int idEmpresa) { this.idEmpresa = idEmpresa; }

    public String getRuc() { return ruc; }
    public void setRuc(String ruc) { this.ruc = ruc; }

    public String getRazonSocial() { return razonSocial; }
    public void setRazonSocial(String razonSocial) { this.razonSocial = razonSocial; }

    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getCondicion() { return condicion; }
    public void setCondicion(String condicion) { this.condicion = condicion; }
}
