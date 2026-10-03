package untrm.hotel_san_antonio.dao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import untrm.hotel_san_antonio.util.ConexionBD;
import untrm.hotel_san_antonio.util.Permisos;
import untrm.hotel_san_antonio.util.SesionActual;
import untrm.hotel_san_antonio.util.Validador;

/** Consultas y cambios de las pantallas administrativas antiguas. */
public class ModulosDAO {
    public record Registro(int id, List<String> celdas, LocalDate fecha) { }
    public record CierreDatos(LocalDate fecha, String turno, BigDecimal fondo, BigDecimal cobros,
                              BigDecimal otros, BigDecimal salidas, BigDecimal esperado,
                              BigDecimal contado, BigDecimal entregado, BigDecimal siguiente,
                              String observacion) { }
    private final AuditoriaDAO auditoria = new AuditoriaDAO();

    private int usuario() {
        return SesionActual.getUsuario().getIdUsuario();
    }

    private void admin() {
        Permisos.requerir("ADMINISTRADOR");
    }

    private void validarImporte(BigDecimal valor, String campo) {
        if (valor == null || valor.signum() < 0 || valor.scale() > 2
                || valor.compareTo(new BigDecimal("99999999.99")) > 0) {
            throw new IllegalArgumentException(campo + " debe ser un importe no negativo con hasta dos decimales.");
        }
    }

    public List<Registro> consultar(String modulo, int umbral) throws SQLException {
        return consultar(modulo, umbral, LocalDate.now());
    }

    public List<Registro> consultar(String modulo, int umbral, LocalDate dia) throws SQLException {
        return switch (modulo) {
            case "almacen/alertas_stock" -> filas(
                "SELECT p.id_producto, CONCAT('P',LPAD(p.id_producto,3,'0')), p.nombre, c.nombre, p.stock, ?, "
                + "GREATEST(?-p.stock,0), CASE WHEN p.stock=0 THEN 'CRÍTICA' WHEN p.stock<=?/2 THEN 'ALTA' ELSE 'MEDIA' END "
                + "FROM producto p JOIN categoria c ON c.id_categoria=p.id_categoria WHERE p.activo=1 AND p.stock<=? ORDER BY p.stock,p.nombre",
                umbral, umbral, umbral, umbral);
            case "almacen/categorias" -> filas(
                "SELECT c.id_categoria, CONCAT('C',LPAD(c.id_categoria,3,'0')), c.nombre, COUNT(p.id_producto) "
                + "FROM categoria c LEFT JOIN producto p ON p.id_categoria=c.id_categoria GROUP BY c.id_categoria,c.nombre ORDER BY c.nombre");
            case "almacen/precios" -> filas(
                "SELECT p.id_producto, CONCAT('P',LPAD(p.id_producto,3,'0')), p.nombre, COALESCE(p.marca,''), "
                + "c.nombre, p.precio, IF(p.activo=1,'Activo','Inactivo') "
                + "FROM producto p JOIN categoria c ON c.id_categoria=p.id_categoria ORDER BY p.nombre");
            case "almacen/productos" -> filas(
                "SELECT p.id_producto, CONCAT('P',LPAD(p.id_producto,3,'0')), p.codigo_barra, p.nombre, "
                + "COALESCE(p.marca,''), c.nombre, p.precio, p.stock, IF(p.activo=1,'Activo','Inactivo') "
                + "FROM producto p JOIN categoria c ON c.id_categoria=p.id_categoria ORDER BY p.nombre");
            case "almacen/stock" -> filas(
                "SELECT p.id_producto, CONCAT('P',LPAD(p.id_producto,3,'0')), p.nombre, c.nombre, p.stock, "
                + "IF(p.activo=1,'Activo','Inactivo') FROM producto p JOIN categoria c "
                + "ON c.id_categoria=p.id_categoria ORDER BY p.nombre");
            case "caja/arqueo" -> filas(
                "SELECT a.id_arqueo,a.id_arqueo,a.fecha,a.turno,CONCAT(u.nombre,' ',u.apellido),"
                + "a.efectivo_esperado,a.efectivo_contado,a.diferencia FROM arqueo_caja a "
                + "JOIN usuario u ON u.id_usuario=a.id_usuario ORDER BY a.fecha DESC");
            case "caja/cierre_caja" -> filas(
                "SELECT c.id_cierre,c.id_cierre,c.fecha,c.turno,CONCAT(u.nombre,' ',u.apellido),"
                + "c.efectivo_esperado,c.efectivo_contado,c.diferencia,c.estado FROM cierre_caja c "
                + "JOIN usuario u ON u.id_usuario=c.id_usuario ORDER BY c.fecha DESC,c.id_cierre DESC");
            case "caja/movimientos" -> filas(
                "SELECT m.id_movimiento,m.fecha,m.id_movimiento,m.tipo,m.concepto,COALESCE(m.referencia,''),"
                + "m.metodo_pago,IF(m.tipo='INGRESO',m.monto,0),IF(m.tipo='EGRESO',m.monto,0),"
                + "CONCAT(u.nombre,' ',u.apellido) FROM movimiento_caja m JOIN usuario u "
                + "ON u.id_usuario=m.id_usuario ORDER BY m.fecha DESC");
            case "caja/registrar_pagos" -> filas(
                "SELECT p.id_pago,p.id_pago,p.fecha_pago,CONCAT('R-',LPAD(r.id_reserva,4,'0'))," 
                + "CONCAT(h.nombres,' ',h.apellidos),hab.numero,p.tipo_pago,p.metodo_pago,p.monto,"
                + "CONCAT(u.nombre,' ',u.apellido) FROM pago p JOIN reserva r ON r.id_reserva=p.id_reserva "
                + "JOIN huesped h ON h.id_huesped=r.id_huesped JOIN habitacion hab ON hab.id_habitacion=r.id_habitacion "
                + "JOIN usuario u ON u.id_usuario=p.id_usuario ORDER BY p.fecha_pago DESC");
            case "caja/totales_dia" -> filas(
                "SELECT 0,p.metodo_pago,SUM(p.monto),0,SUM(p.monto),COUNT(*) FROM pago p "
                + "WHERE DATE(p.fecha_pago)=? GROUP BY p.metodo_pago UNION ALL "
                + "SELECT 0,'NO REGISTRADO',0,COALESCE(SUM(v.total),0),COALESCE(SUM(v.total),0),COUNT(*) "
                + "FROM venta_tienda v WHERE DATE(v.fecha_venta)=?", Date.valueOf(dia), Date.valueOf(dia));
            case "reportes/ingresos" -> filas(
                "SELECT 0,DATE(p.fecha_pago),'Alojamiento',p.metodo_pago,COUNT(*),SUM(p.monto) "
                + "FROM pago p GROUP BY DATE(p.fecha_pago),p.metodo_pago UNION ALL "
                + "SELECT 0,DATE(v.fecha_venta),'Tienda','NO REGISTRADO',COUNT(*),SUM(v.total) "
                + "FROM venta_tienda v GROUP BY DATE(v.fecha_venta) ORDER BY 2 DESC");
            case "reportes/ocupacion" -> ocupacionDiaria();
            case "reportes/reportes_personalizados" -> filas(
                "SELECT 0,DATE(p.fecha_pago),'Alojamiento','Pago de reserva',COUNT(*),SUM(p.monto) "
                + "FROM pago p GROUP BY DATE(p.fecha_pago) UNION ALL "
                + "SELECT 0,DATE(v.fecha_venta),'Tienda','Venta de productos',COUNT(*),SUM(v.total) "
                + "FROM venta_tienda v GROUP BY DATE(v.fecha_venta) ORDER BY 2 DESC");
            case "reportes/ventas" -> filas(
                "SELECT 0,CONCAT('P',LPAD(p.id_producto,3,'0')),p.nombre,c.nombre,SUM(d.cantidad),"
                + "ROUND(SUM(d.subtotal)/SUM(d.cantidad),2),SUM(d.subtotal) FROM detalle_venta d "
                + "JOIN producto p ON p.id_producto=d.id_producto JOIN categoria c ON c.id_categoria=p.id_categoria "
                + "GROUP BY p.id_producto,p.nombre,c.nombre ORDER BY SUM(d.subtotal) DESC");
            case "usuarios/editar_usuario" -> filas(
                "SELECT u.id_usuario,CONCAT('U',LPAD(u.id_usuario,3,'0')),u.nombre,u.apellido,u.usuario,"
                + "u.rol,IF(u.activo=1,'Activo','Inactivo'),u.fecha_creacion FROM usuario u ORDER BY u.nombre,u.apellido");
            case "usuarios/historial_usuarios" -> filas(
                "SELECT a.id_auditoria,a.fecha,COALESCE(afectado.usuario,CONCAT('#',a.id_entidad)),a.accion,"
                + "COALESCE(SUBSTRING_INDEX(a.detalle,' → ',1),''),"
                + "COALESCE(SUBSTRING_INDEX(a.detalle,' → ',-1),''),actor.usuario,COALESCE(a.detalle,'') "
                + "FROM auditoria a JOIN usuario actor ON actor.id_usuario=a.id_usuario "
                + "LEFT JOIN usuario afectado ON afectado.id_usuario=a.id_entidad "
                + "WHERE a.entidad='usuario' ORDER BY a.fecha DESC");
            default -> throw new IllegalArgumentException("Módulo desconocido: " + modulo);
        };
    }

    private List<Registro> filas(String sql, Object... valores) throws SQLException {
        List<Registro> resultado = new ArrayList<>();
        try (Connection con = ConexionBD.conectar(); PreparedStatement ps = con.prepareStatement(sql)) {
            for (int i = 0; i < valores.length; i++) ps.setObject(i + 1, valores[i]);
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData meta = rs.getMetaData();
                while (rs.next()) {
                    List<String> celdas = new ArrayList<>();
                    LocalDate fecha = null;
                    for (int i = 2; i <= meta.getColumnCount(); i++) {
                        Object valor = rs.getObject(i);
                        celdas.add(valor == null ? "" : valor.toString());
                        if (fecha == null && valor instanceof Date d) fecha = d.toLocalDate();
                        if (fecha == null && valor instanceof Timestamp t) fecha = t.toLocalDateTime().toLocalDate();
                    }
                    resultado.add(new Registro(rs.getInt(1), celdas, fecha));
                }
            }
        }
        return resultado;
    }

    private static final class OcupacionDia {
        int disponibles;
        final Set<Integer> habitaciones = new HashSet<>();
        int reservas;
        int huespedes;
    }

    /** Cuenta cada noche entre check-in y checkout; checkout no ocupa la noche siguiente. */
    private List<Registro> ocupacionDiaria() throws SQLException {
        String sql = "SELECT r.fecha_checkin,r.fecha_checkout,r.num_huespedes,h.id_habitacion,"
                + "t.nombre,(SELECT COUNT(*) FROM habitacion x WHERE x.id_tipo=t.id_tipo) "
                + "FROM reserva r JOIN habitacion h ON h.id_habitacion=r.id_habitacion "
                + "JOIN tipo_habitacion t ON t.id_tipo=h.id_tipo "
                + "WHERE r.estado NOT IN ('CANCELADA','PENDIENTE')";
        TreeMap<LocalDate, Map<String, OcupacionDia>> porFecha = new TreeMap<>();
        try (Connection con = ConexionBD.conectar(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                LocalDate ingreso = rs.getDate(1).toLocalDate();
                LocalDate salida = rs.getDate(2).toLocalDate();
                int noches = (int) java.time.temporal.ChronoUnit.DAYS.between(ingreso, salida);
                if (noches <= 0 || noches > 3660) continue;
                for (int i = 0; i < noches; i++) {
                    LocalDate fecha = ingreso.plusDays(i);
                    OcupacionDia dia = porFecha.computeIfAbsent(fecha, x -> new TreeMap<>())
                            .computeIfAbsent(rs.getString(5), x -> new OcupacionDia());
                    dia.disponibles = rs.getInt(6);
                    dia.habitaciones.add(rs.getInt(4));
                    dia.reservas++;
                    dia.huespedes += rs.getInt(3);
                }
            }
        }
        List<Registro> resultado = new ArrayList<>();
        for (var fecha : porFecha.descendingMap().entrySet()) {
            for (var tipo : fecha.getValue().entrySet()) {
                OcupacionDia dia = tipo.getValue();
                int ocupadas = dia.habitaciones.size();
                String porcentaje = dia.disponibles == 0 ? "0.0" : BigDecimal.valueOf(ocupadas * 100L)
                        .divide(BigDecimal.valueOf(dia.disponibles), 1, RoundingMode.HALF_UP).toPlainString();
                resultado.add(new Registro(0, List.of(fecha.getKey().toString(), tipo.getKey(),
                        String.valueOf(dia.disponibles), String.valueOf(ocupadas), porcentaje,
                        String.valueOf(dia.reservas), String.valueOf(dia.huespedes)), fecha.getKey()));
            }
        }
        return resultado;
    }

    private static final class ResumenReporte {
        String periodo;
        String origen;
        String concepto;
        LocalDate fecha;
        BigDecimal cantidad = BigDecimal.ZERO;
        BigDecimal importe = BigDecimal.ZERO;
    }

    /** Agrupa movimientos reales de alojamiento y detalle de tienda. */
    public List<Registro> reportePersonalizado(String fuente, String agrupacion,
                                               LocalDate desde, LocalDate hasta) throws SQLException {
        if (!List.of("Todos", "Alojamiento", "Tienda").contains(fuente))
            throw new IllegalArgumentException("Seleccione una fuente válida.");
        if (!List.of("Día", "Mes", "Habitación", "Producto", "Método de pago").contains(agrupacion))
            throw new IllegalArgumentException("Seleccione una agrupación válida.");
        if (agrupacion.equals("Habitación") && fuente.equals("Tienda"))
            throw new IllegalArgumentException("Para agrupar por habitación seleccione Alojamiento o Todos.");
        if (agrupacion.equals("Producto") && fuente.equals("Alojamiento"))
            throw new IllegalArgumentException("Para agrupar por producto seleccione Tienda o Todos.");
        String sql = "SELECT 0,DATE(p.fecha_pago),'Alojamiento',CONCAT('Hab. ',h.numero),"
                + "p.metodo_pago,1,p.monto FROM pago p JOIN reserva r ON r.id_reserva=p.id_reserva "
                + "JOIN habitacion h ON h.id_habitacion=r.id_habitacion UNION ALL "
                + "SELECT 0,DATE(v.fecha_venta),'Tienda',pr.nombre,'NO REGISTRADO',d.cantidad,d.subtotal "
                + "FROM venta_tienda v JOIN detalle_venta d ON d.id_venta=v.id_venta "
                + "JOIN producto pr ON pr.id_producto=d.id_producto";
        Map<String, ResumenReporte> grupos = new LinkedHashMap<>();
        for (Registro fila : filas(sql)) {
            if (fila.fecha() == null || desde != null && fila.fecha().isBefore(desde)
                    || hasta != null && fila.fecha().isAfter(hasta)) continue;
            String origen = fila.celdas().get(1);
            if (!fuente.equals("Todos") && !fuente.equals(origen)) continue;
            if (agrupacion.equals("Habitación") && !origen.equals("Alojamiento")) continue;
            if (agrupacion.equals("Producto") && !origen.equals("Tienda")) continue;
            String periodo = agrupacion.equals("Mes") ? fila.fecha().toString().substring(0, 7)
                    : agrupacion.equals("Día") ? fila.fecha().toString()
                    : desde == null && hasta == null ? "Histórico"
                    : (desde == null ? "Inicio" : desde) + " a " + (hasta == null ? "Hoy" : hasta);
            String concepto = switch (agrupacion) {
                case "Habitación", "Producto" -> fila.celdas().get(2);
                case "Método de pago" -> fila.celdas().get(3);
                default -> origen.equals("Tienda") ? "Ventas de tienda" : "Pagos de reserva";
            };
            String clave = periodo + "|" + origen + "|" + concepto;
            ResumenReporte grupo = grupos.computeIfAbsent(clave, x -> new ResumenReporte());
            grupo.periodo = periodo; grupo.origen = origen; grupo.concepto = concepto;
            grupo.fecha = fila.fecha();
            grupo.cantidad = grupo.cantidad.add(new BigDecimal(fila.celdas().get(4)));
            grupo.importe = grupo.importe.add(new BigDecimal(fila.celdas().get(5)));
        }
        List<Registro> resultado = new ArrayList<>();
        for (ResumenReporte grupo : grupos.values()) resultado.add(new Registro(0,
                List.of(grupo.periodo, grupo.origen, grupo.concepto,
                        grupo.cantidad.toPlainString(), grupo.importe.toPlainString()), grupo.fecha));
        return resultado;
    }

    public List<String> categorias() throws SQLException {
        List<String> lista = new ArrayList<>();
        try (Connection con = ConexionBD.conectar(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT nombre FROM categoria ORDER BY nombre")) {
            while (rs.next()) lista.add(rs.getString(1));
        }
        return lista;
    }

    public List<String> tiposHabitacion() throws SQLException {
        List<String> tipos = new ArrayList<>();
        try (Connection con = ConexionBD.conectar(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery("SELECT nombre FROM tipo_habitacion ORDER BY nombre")) {
            while (rs.next()) tipos.add(rs.getString(1));
        }
        return tipos;
    }

    public int idProductoPorCodigo(String codigo) throws SQLException {
        if (codigo == null || codigo.isBlank()) throw new IllegalArgumentException("Indique el código del producto.");
        String normalizado = codigo.trim().replaceFirst("^[Pp]", "");
        int id = normalizado.matches("\\d+") ? Integer.parseInt(normalizado) : -1;
        try (Connection con = ConexionBD.conectar(); PreparedStatement ps = con.prepareStatement(
                "SELECT id_producto FROM producto WHERE (id_producto=? OR codigo_barra=?) AND activo=1")) {
            ps.setInt(1, id); ps.setString(2, codigo.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("No se encontró un producto activo con ese código.");
                return rs.getInt(1);
            }
        }
    }

    public BigDecimal efectivoHoy() throws SQLException {
        String sql = "SELECT COALESCE((SELECT SUM(monto) FROM pago WHERE metodo_pago='EFECTIVO' "
                + "AND DATE(fecha_pago)=CURDATE()),0) + COALESCE((SELECT SUM(monto) FROM movimiento_caja "
                + "WHERE tipo='INGRESO' AND metodo_pago='EFECTIVO' AND DATE(fecha)=CURDATE()),0) "
                + "- COALESCE((SELECT SUM(monto) FROM gasto WHERE activo=1 AND metodo_pago='EFECTIVO' "
                + "AND fecha=CURDATE()),0) - COALESCE((SELECT SUM(monto) FROM movimiento_caja "
                + "WHERE tipo='EGRESO' AND metodo_pago='EFECTIVO' AND DATE(fecha)=CURDATE()),0)";
        try (Connection con = ConexionBD.conectar(); Statement st = con.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            rs.next(); return rs.getBigDecimal(1);
        }
    }

    /** Componentes de efectivo para el cierre de una fecha y turno. */
    public BigDecimal[] resumenEfectivo(LocalDate fecha, String turno) throws SQLException {
        if (fecha == null) throw new IllegalArgumentException("Seleccione la fecha del cierre.");
        String sql = "SELECT "
                + "COALESCE((SELECT fondo_siguiente FROM cierre_caja c WHERE c.estado='CONFIRMADO' "
                + "AND (c.fecha<? OR (c.fecha=? AND c.turno<>?)) ORDER BY c.fecha DESC,c.id_cierre DESC LIMIT 1),0),"
                + "COALESCE((SELECT SUM(monto) FROM pago WHERE metodo_pago='EFECTIVO' AND DATE(fecha_pago)=?),0),"
                + "COALESCE((SELECT SUM(monto) FROM movimiento_caja WHERE tipo='INGRESO' "
                + "AND metodo_pago='EFECTIVO' AND DATE(fecha)=?),0),"
                + "COALESCE((SELECT SUM(monto) FROM movimiento_caja WHERE tipo='EGRESO' "
                + "AND metodo_pago='EFECTIVO' AND DATE(fecha)=?),0) + "
                + "COALESCE((SELECT SUM(monto) FROM gasto WHERE activo=1 AND metodo_pago='EFECTIVO' AND fecha=?),0)";
        try (Connection con = ConexionBD.conectar(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setDate(1, Date.valueOf(fecha)); ps.setDate(2, Date.valueOf(fecha));
            ps.setString(3, turno == null ? "" : turno.trim());
            for (int i = 4; i <= 7; i++) ps.setDate(i, Date.valueOf(fecha));
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return new BigDecimal[] {rs.getBigDecimal(1), rs.getBigDecimal(2),
                        rs.getBigDecimal(3), rs.getBigDecimal(4)};
            }
        }
    }

    public void guardarCategoria(Integer id, String nombre) throws SQLException {
        admin();
        if (nombre == null || nombre.isBlank() || nombre.trim().length() > 40)
            throw new IllegalArgumentException("La categoría debe tener entre 1 y 40 caracteres.");
        String sql = id == null ? "INSERT INTO categoria(nombre) VALUES (?)"
                : "UPDATE categoria SET nombre=? WHERE id_categoria=?";
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, nombre.trim());
                if (id != null) ps.setInt(2, id);
                if (ps.executeUpdate() != 1) throw new SQLException("No se guardó la categoría.");
                int afectado = id == null ? claveGenerada(ps) : id;
                auditoria.registrar(con, usuario(), id == null ? "CATEGORIA_CREAR" : "CATEGORIA_EDITAR",
                        "categoria", afectado, nombre.trim());
                con.commit();
            } catch (SQLException | RuntimeException ex) { con.rollback(); throw ex; }
        }
    }

    public void eliminarCategoria(int id) throws SQLException {
        admin();
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT COUNT(*) FROM producto WHERE id_categoria=?")) {
                    ps.setInt(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        if (rs.getInt(1) > 0) throw new IllegalStateException(
                                "La categoría tiene productos asociados y no se puede eliminar.");
                    }
                }
                try (PreparedStatement ps = con.prepareStatement("DELETE FROM categoria WHERE id_categoria=?")) {
                    ps.setInt(1, id);
                    if (ps.executeUpdate() != 1) throw new SQLException("La categoría ya no existe.");
                }
                auditoria.registrar(con, usuario(), "CATEGORIA_ELIMINAR", "categoria", id, "Sin productos asociados");
                con.commit();
            } catch (SQLException | RuntimeException ex) { con.rollback(); throw ex; }
        }
    }

    public void guardarProducto(Integer id, String codigo, String nombre, String marca,
                                String categoria, BigDecimal precio, int stock, boolean activo) throws SQLException {
        admin();
        if (codigo == null || !codigo.trim().matches("[A-Za-z0-9-]{3,20}"))
            throw new IllegalArgumentException("El código de barras debe tener 3 a 20 caracteres alfanuméricos.");
        if (nombre == null || nombre.isBlank() || nombre.trim().length() > 120)
            throw new IllegalArgumentException("Ingrese un nombre de producto de hasta 120 caracteres.");
        if (marca != null && marca.trim().length() > 60)
            throw new IllegalArgumentException("La marca supera 60 caracteres.");
        validarImporte(precio, "El precio");
        if (precio.signum() == 0 || precio.compareTo(new BigDecimal("9999.99")) > 0)
            throw new IllegalArgumentException("El precio debe estar entre S/ 0.01 y S/ 9999.99.");
        if (stock < 0) throw new IllegalArgumentException("El stock no puede ser negativo.");
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                int idCategoria = categoriaId(con, categoria);
                String sql = id == null
                        ? "INSERT INTO producto(codigo_barra,nombre,marca,id_categoria,precio,stock,activo) VALUES (?,?,?,?,?,?,?)"
                        : "UPDATE producto SET codigo_barra=?,nombre=?,marca=?,id_categoria=?,precio=?,activo=? WHERE id_producto=?";
                try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, codigo.trim()); ps.setString(2, nombre.trim());
                    ps.setString(3, marca == null ? null : marca.trim()); ps.setInt(4, idCategoria);
                    ps.setBigDecimal(5, precio);
                    if (id == null) { ps.setInt(6, stock); ps.setBoolean(7, activo); }
                    else { ps.setBoolean(6, activo); ps.setInt(7, id); }
                    if (ps.executeUpdate() != 1) throw new SQLException("No se guardó el producto.");
                    int afectado = id == null ? claveGenerada(ps) : id;
                    auditoria.registrar(con, usuario(), id == null ? "PRODUCTO_CREAR" : "PRODUCTO_EDITAR",
                            "producto", afectado, nombre.trim());
                }
                con.commit();
            } catch (SQLException | RuntimeException ex) { con.rollback(); throw ex; }
        }
    }

    private int categoriaId(Connection con, String nombre) throws SQLException {
        if (nombre == null || nombre.isBlank()) throw new IllegalArgumentException("Seleccione una categoría.");
        try (PreparedStatement ps = con.prepareStatement("SELECT id_categoria FROM categoria WHERE nombre=?")) {
            ps.setString(1, nombre);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new IllegalArgumentException("La categoría no existe.");
                return rs.getInt(1);
            }
        }
    }

    private int claveGenerada(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.getGeneratedKeys()) {
            if (!rs.next()) throw new SQLException("No se obtuvo el identificador generado.");
            return rs.getInt(1);
        }
    }

    public void cambiarPrecio(int idProducto, BigDecimal precio) throws SQLException {
        admin(); validarImporte(precio, "El precio");
        if (precio.signum() == 0 || precio.compareTo(new BigDecimal("9999.99")) > 0)
            throw new IllegalArgumentException("El precio debe estar entre S/ 0.01 y S/ 9999.99.");
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                BigDecimal anterior;
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT precio FROM producto WHERE id_producto=? AND activo=1 FOR UPDATE")) {
                    ps.setInt(1, idProducto);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new IllegalStateException("El producto no existe o está inactivo.");
                        anterior = rs.getBigDecimal(1);
                    }
                }
                if (anterior.compareTo(precio) == 0) throw new IllegalArgumentException("El precio no cambió.");
                try (PreparedStatement ps = con.prepareStatement("UPDATE producto SET precio=? WHERE id_producto=?")) {
                    ps.setBigDecimal(1, precio); ps.setInt(2, idProducto); ps.executeUpdate();
                }
                auditoria.registrar(con, usuario(), "PRODUCTO_PRECIO", "producto", idProducto,
                        anterior + " → " + precio);
                con.commit();
            } catch (SQLException | RuntimeException ex) { con.rollback(); throw ex; }
        }
    }

    public void moverStock(int idProducto, String tipo, int cantidad, String motivo,
                           String referencia, String observacion) throws SQLException {
        admin();
        if (!List.of("ENTRADA", "SALIDA", "AJUSTE").contains(tipo))
            throw new IllegalArgumentException("Seleccione el tipo de movimiento.");
        if (cantidad <= 0) throw new IllegalArgumentException("La cantidad debe ser mayor que cero.");
        if (motivo == null || motivo.isBlank() || motivo.length() > 100)
            throw new IllegalArgumentException("Indique un motivo válido.");
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                int anterior;
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT stock FROM producto WHERE id_producto=? AND activo=1 FOR UPDATE")) {
                    ps.setInt(1, idProducto);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new IllegalStateException("El producto no existe o está inactivo.");
                        anterior = rs.getInt(1);
                    }
                }
                int nuevo = "AJUSTE".equals(tipo) ? cantidad : "ENTRADA".equals(tipo)
                        ? Math.addExact(anterior, cantidad) : anterior - cantidad;
                if (nuevo < 0) throw new IllegalArgumentException("No hay stock suficiente para la salida.");
                try (PreparedStatement ps = con.prepareStatement("UPDATE producto SET stock=? WHERE id_producto=?")) {
                    ps.setInt(1, nuevo); ps.setInt(2, idProducto); ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO movimiento_stock(id_producto,id_usuario,tipo,cantidad,stock_anterior,"
                        + "stock_resultante,motivo,referencia,observacion) VALUES (?,?,?,?,?,?,?,?,?)")) {
                    ps.setInt(1, idProducto); ps.setInt(2, usuario()); ps.setString(3, tipo);
                    ps.setInt(4, cantidad); ps.setInt(5, anterior); ps.setInt(6, nuevo);
                    ps.setString(7, motivo.trim()); ps.setString(8, referencia);
                    ps.setString(9, observacion); ps.executeUpdate();
                }
                auditoria.registrar(con, usuario(), "PRODUCTO_STOCK", "producto", idProducto,
                        anterior + " → " + nuevo + " (" + tipo + ")");
                con.commit();
            } catch (SQLException | RuntimeException ex) { con.rollback(); throw ex; }
        }
    }

    public void guardarMovimientoCaja(String tipo, String concepto, String metodo,
                                      BigDecimal monto, String referencia, String observacion,
                                      String turno) throws SQLException {
        admin();
        if (!List.of("INGRESO", "EGRESO").contains(tipo))
            throw new IllegalArgumentException("Seleccione ingreso o egreso.");
        if (concepto == null || concepto.isBlank() || concepto.length() > 150)
            throw new IllegalArgumentException("Indique un concepto de hasta 150 caracteres.");
        if (!List.of("EFECTIVO", "TARJETA", "TRANSFERENCIA", "YAPE").contains(metodo))
            throw new IllegalArgumentException("Seleccione un método de pago válido.");
        validarImporte(monto, "El monto");
        if (monto.signum() == 0) throw new IllegalArgumentException("El monto debe ser mayor que cero.");
        if (turno == null || turno.isBlank() || turno.length() > 50)
            throw new IllegalArgumentException("Indique el turno o caja (hasta 50 caracteres).");
        String nota = "Turno/caja: " + turno.trim()
                + (observacion == null || observacion.isBlank() ? "" : "\n" + observacion.trim());
        if (nota.length() > 500) throw new IllegalArgumentException("La observación supera 500 caracteres.");
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO movimiento_caja(id_usuario,tipo,concepto,metodo_pago,monto,referencia,observacion) "
                    + "VALUES (?,?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, usuario()); ps.setString(2, tipo); ps.setString(3, concepto.trim());
                ps.setString(4, metodo); ps.setBigDecimal(5, monto);
                ps.setString(6, referencia); ps.setString(7, nota);
                ps.executeUpdate();
                auditoria.registrar(con, usuario(), "CAJA_MOVIMIENTO", "movimiento_caja",
                        claveGenerada(ps), tipo + " " + monto + " " + concepto.trim());
                con.commit();
            } catch (SQLException | RuntimeException ex) { con.rollback(); throw ex; }
        }
    }

    public void guardarArqueo(String turno, BigDecimal esperado, BigDecimal contado,
                              String observacion) throws SQLException {
        admin();
        if (turno == null || turno.isBlank() || turno.length() > 50)
            throw new IllegalArgumentException("Indique un turno de hasta 50 caracteres.");
        validarImporte(esperado, "El efectivo esperado");
        validarImporte(contado, "El efectivo contado");
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO arqueo_caja(id_usuario,turno,efectivo_esperado,efectivo_contado,diferencia,observacion) "
                    + "VALUES (?,?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS)) {
                ps.setInt(1, usuario()); ps.setString(2, turno.trim()); ps.setBigDecimal(3, esperado);
                ps.setBigDecimal(4, contado); ps.setBigDecimal(5, contado.subtract(esperado));
                ps.setString(6, observacion); ps.executeUpdate();
                auditoria.registrar(con, usuario(), "CAJA_ARQUEO", "arqueo_caja", claveGenerada(ps),
                        "Diferencia: " + contado.subtract(esperado));
                con.commit();
            } catch (SQLException | RuntimeException ex) { con.rollback(); throw ex; }
        }
    }

    public void guardarCierre(Integer id, CierreDatos datos, boolean confirmar) throws SQLException {
        admin();
        if (datos.fecha() == null || datos.fecha().isAfter(LocalDate.now()))
            throw new IllegalArgumentException("La fecha del cierre no puede ser futura.");
        if (datos.turno() == null || datos.turno().isBlank() || datos.turno().length() > 50)
            throw new IllegalArgumentException("Indique un turno de hasta 50 caracteres.");
        for (BigDecimal valor : List.of(datos.fondo(), datos.cobros(), datos.otros(), datos.salidas(),
                datos.esperado(), datos.contado(), datos.entregado(), datos.siguiente())) {
            validarImporte(valor, "Los importes del cierre");
        }
        BigDecimal calculado = datos.fondo().add(datos.cobros()).add(datos.otros()).subtract(datos.salidas());
        if (calculado.compareTo(datos.esperado()) != 0)
            throw new IllegalArgumentException("El efectivo esperado no coincide con fondo + cobros + ingresos - salidas.");
        if (datos.entregado().add(datos.siguiente()).compareTo(datos.contado()) != 0)
            throw new IllegalArgumentException("Efectivo entregado + fondo siguiente debe igualar el contado.");
        if (datos.observacion() != null && datos.observacion().length() > 500)
            throw new IllegalArgumentException("La observación del cierre supera 500 caracteres.");
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                if (id == null) {
                    try (PreparedStatement ps = con.prepareStatement(
                            "SELECT id_cierre,estado FROM cierre_caja WHERE fecha=? AND turno=? FOR UPDATE")) {
                        ps.setDate(1, Date.valueOf(datos.fecha()));
                        ps.setString(2, datos.turno().trim());
                        try (ResultSet rs = ps.executeQuery()) {
                            if (rs.next()) {
                                if ("CONFIRMADO".equals(rs.getString(2)))
                                    throw new IllegalStateException("El cierre de ese día y turno ya está confirmado.");
                                id = rs.getInt(1);
                            }
                        }
                    }
                }
                if (id != null) {
                    try (PreparedStatement ps = con.prepareStatement(
                            "SELECT estado FROM cierre_caja WHERE id_cierre=? FOR UPDATE")) {
                        ps.setInt(1, id);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) throw new IllegalStateException("El cierre ya no existe.");
                            if ("CONFIRMADO".equals(rs.getString(1)))
                                throw new IllegalStateException("Un cierre confirmado no se puede modificar.");
                        }
                    }
                }
                String sql = id == null
                        ? "INSERT INTO cierre_caja(id_usuario,fecha,turno,fondo_inicial,cobros_efectivo,otros_ingresos,"
                          + "salidas_efectivo,efectivo_esperado,efectivo_contado,diferencia,efectivo_entregado,"
                          + "fondo_siguiente,observacion,estado) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
                        : "UPDATE cierre_caja SET id_usuario=?,fecha=?,turno=?,fondo_inicial=?,cobros_efectivo=?,"
                          + "otros_ingresos=?,salidas_efectivo=?,efectivo_esperado=?,efectivo_contado=?,diferencia=?,"
                          + "efectivo_entregado=?,fondo_siguiente=?,observacion=?,estado=? WHERE id_cierre=?";
                try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, usuario()); ps.setDate(2, Date.valueOf(datos.fecha()));
                    ps.setString(3, datos.turno().trim()); ps.setBigDecimal(4, datos.fondo());
                    ps.setBigDecimal(5, datos.cobros()); ps.setBigDecimal(6, datos.otros());
                    ps.setBigDecimal(7, datos.salidas()); ps.setBigDecimal(8, datos.esperado());
                    ps.setBigDecimal(9, datos.contado());
                    ps.setBigDecimal(10, datos.contado().subtract(datos.esperado()));
                    ps.setBigDecimal(11, datos.entregado()); ps.setBigDecimal(12, datos.siguiente());
                    ps.setString(13, datos.observacion());
                    ps.setString(14, confirmar ? "CONFIRMADO" : "BORRADOR");
                    if (id != null) ps.setInt(15, id);
                    if (ps.executeUpdate() != 1) throw new SQLException("No se guardó el cierre.");
                    int afectado = id == null ? claveGenerada(ps) : id;
                    auditoria.registrar(con, usuario(), confirmar ? "CAJA_CIERRE" : "CAJA_BORRADOR",
                            "cierre_caja", afectado, datos.fecha() + " / " + datos.turno());
                }
                con.commit();
            } catch (SQLException | RuntimeException ex) { con.rollback(); throw ex; }
        }
    }

    public String[] reservaParaPago(int idReserva) throws SQLException {
        String sql = "SELECT CONCAT(h.nombres,' ',h.apellidos),h.num_documento,hab.numero,r.monto_total,"
                + "r.monto_total-COALESCE((SELECT SUM(p.monto) FROM pago p WHERE p.id_reserva=r.id_reserva),0),"
                + "r.estado FROM reserva r JOIN huesped h ON h.id_huesped=r.id_huesped "
                + "JOIN habitacion hab ON hab.id_habitacion=r.id_habitacion WHERE r.id_reserva=?";
        try (Connection con = ConexionBD.conectar(); PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idReserva);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                String[] datos = new String[6];
                for (int i = 0; i < datos.length; i++) datos[i] = rs.getString(i + 1);
                return datos;
            }
        }
    }

    public void registrarPago(int idReserva, String metodo, BigDecimal monto) throws SQLException {
        Permisos.requerir("ADMINISTRADOR", "RECEPCIONISTA");
        if (!List.of("EFECTIVO", "TARJETA", "TRANSFERENCIA", "YAPE").contains(metodo))
            throw new IllegalArgumentException("Seleccione un método de pago válido.");
        validarImporte(monto, "El pago");
        if (monto.signum() == 0) throw new IllegalArgumentException("El pago debe ser mayor que cero.");
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                BigDecimal total;
                String estado;
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT monto_total,estado FROM reserva WHERE id_reserva=? FOR UPDATE")) {
                    ps.setInt(1, idReserva);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("No existe la reserva indicada.");
                        total = rs.getBigDecimal(1); estado = rs.getString(2);
                    }
                }
                if (List.of("CANCELADA", "FINALIZADA").contains(estado))
                    throw new IllegalStateException("No se puede cobrar una reserva cancelada o finalizada.");
                BigDecimal abonado = new PagoDAO().sumarPorReserva(con, idReserva);
                if (monto.compareTo(total.subtract(abonado)) > 0)
                    throw new IllegalArgumentException("El pago supera el saldo de la reserva.");
                String tipo = abonado.signum() == 0 && monto.compareTo(total) == 0 ? "COMPLETO"
                        : abonado.signum() == 0 ? "ADELANTO" : "SALDO";
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO pago(id_reserva,id_usuario,monto,metodo_pago,tipo_pago) VALUES (?,?,?,?,?)")) {
                    ps.setInt(1, idReserva); ps.setInt(2, usuario()); ps.setBigDecimal(3, monto);
                    ps.setString(4, metodo); ps.setString(5, tipo); ps.executeUpdate();
                }
                auditoria.registrar(con, usuario(), "RESERVA_PAGO", "reserva", idReserva,
                        tipo + " " + monto + " " + metodo);
                con.commit();
            } catch (SQLException | RuntimeException ex) { con.rollback(); throw ex; }
        }
    }

    public void editarUsuario(int id, String nombre, String apellido, boolean activo) throws SQLException {
        admin();
        if (!Validador.esNombreValido(nombre) || !Validador.esNombreValido(apellido))
            throw new IllegalArgumentException("Revise nombres y apellidos.");
        if (!activo && id == usuario())
            throw new IllegalArgumentException("No puede desactivar su propia cuenta.");
        try (Connection con = ConexionBD.conectar()) {
            con.setAutoCommit(false);
            try {
                String rol;
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT rol FROM usuario WHERE id_usuario=? FOR UPDATE")) {
                    ps.setInt(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("El usuario ya no existe.");
                        rol = rs.getString(1);
                    }
                }
                if (!activo && "ADMINISTRADOR".equals(rol)
                        && new UsuarioDAO().contarAdministradoresActivos(con) <= 1)
                    throw new IllegalStateException("Debe permanecer al menos un administrador activo.");
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE usuario SET nombre=?,apellido=?,activo=? WHERE id_usuario=?")) {
                    ps.setString(1, nombre.trim()); ps.setString(2, apellido.trim());
                    ps.setBoolean(3, activo); ps.setInt(4, id); ps.executeUpdate();
                }
                auditoria.registrar(con, usuario(), "USUARIO_EDITAR", "usuario", id,
                        nombre.trim() + " " + apellido.trim() + " / " + (activo ? "Activo" : "Inactivo"));
                con.commit();
            } catch (SQLException | RuntimeException ex) { con.rollback(); throw ex; }
        }
    }
}
