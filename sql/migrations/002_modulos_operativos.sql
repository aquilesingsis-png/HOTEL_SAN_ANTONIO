-- Ejecutar una sola vez en una base existente hotel_san_antonio.
-- Migración aditiva: conserva todas las reservas, ventas, usuarios y productos.
USE hotel_san_antonio;

CREATE TABLE IF NOT EXISTS movimiento_stock (
    id_movimiento INT AUTO_INCREMENT PRIMARY KEY,
    id_producto INT NOT NULL,
    id_usuario INT NOT NULL,
    tipo ENUM('ENTRADA','SALIDA','AJUSTE') NOT NULL,
    cantidad INT NOT NULL,
    stock_anterior INT NOT NULL,
    stock_resultante INT NOT NULL,
    motivo VARCHAR(100) NOT NULL,
    referencia VARCHAR(100) NULL,
    observacion VARCHAR(500) NULL,
    fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ms_producto FOREIGN KEY (id_producto) REFERENCES producto(id_producto),
    CONSTRAINT fk_ms_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario),
    CONSTRAINT chk_ms_cantidad CHECK (cantidad > 0),
    CONSTRAINT chk_ms_stock CHECK (stock_resultante >= 0)
);

CREATE TABLE IF NOT EXISTS movimiento_caja (
    id_movimiento INT AUTO_INCREMENT PRIMARY KEY,
    id_usuario INT NOT NULL,
    tipo ENUM('INGRESO','EGRESO') NOT NULL,
    concepto VARCHAR(150) NOT NULL,
    metodo_pago ENUM('EFECTIVO','TARJETA','TRANSFERENCIA','YAPE') NOT NULL,
    monto DECIMAL(10,2) NOT NULL,
    referencia VARCHAR(100) NULL,
    observacion VARCHAR(500) NULL,
    fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_mc_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario),
    CONSTRAINT chk_mc_monto CHECK (monto > 0)
);

CREATE TABLE IF NOT EXISTS arqueo_caja (
    id_arqueo INT AUTO_INCREMENT PRIMARY KEY,
    id_usuario INT NOT NULL,
    turno VARCHAR(50) NOT NULL,
    efectivo_esperado DECIMAL(10,2) NOT NULL,
    efectivo_contado DECIMAL(10,2) NOT NULL,
    diferencia DECIMAL(10,2) NOT NULL,
    observacion VARCHAR(500) NULL,
    fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_ac_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario)
);

CREATE TABLE IF NOT EXISTS cierre_caja (
    id_cierre INT AUTO_INCREMENT PRIMARY KEY,
    id_usuario INT NOT NULL,
    fecha DATE NOT NULL,
    turno VARCHAR(50) NOT NULL,
    fondo_inicial DECIMAL(10,2) NOT NULL DEFAULT 0,
    cobros_efectivo DECIMAL(10,2) NOT NULL DEFAULT 0,
    otros_ingresos DECIMAL(10,2) NOT NULL DEFAULT 0,
    salidas_efectivo DECIMAL(10,2) NOT NULL DEFAULT 0,
    efectivo_esperado DECIMAL(10,2) NOT NULL DEFAULT 0,
    efectivo_contado DECIMAL(10,2) NOT NULL DEFAULT 0,
    diferencia DECIMAL(10,2) NOT NULL DEFAULT 0,
    efectivo_entregado DECIMAL(10,2) NOT NULL DEFAULT 0,
    fondo_siguiente DECIMAL(10,2) NOT NULL DEFAULT 0,
    observacion VARCHAR(500) NULL,
    estado ENUM('BORRADOR','CONFIRMADO') NOT NULL DEFAULT 'BORRADOR',
    fecha_registro DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cc_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario),
    CONSTRAINT uq_cc_fecha_turno UNIQUE (fecha, turno)
);
