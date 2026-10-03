-- HOTEL SAN ANTONIO: INSTALACION COMPLETA PARA XAMPP/MARIADB
-- SOLO para una base nueva sin tablas. NO ejecutar sobre la base existente.
-- Crea 21 tablas, tipos, 24 habitaciones, catalogo de tienda y dos cuentas.
-- No contiene personas, reservas ni pagos inventados.
-- Las habitaciones se inician DISPONIBLES para evitar ocupaciones ficticias.
-- Las claves temporales figuran en ACCESOS_INICIALES.txt y deben cambiarse.

-- =====================================================================
-- Hotel San Antonio — Sistema de Gestion Hotelera y Tiendita
-- Taller de Programacion II — Trabajo de Primera Unidad
-- Esquema completo DDL (MySQL / MariaDB); las migraciones ya están incluidas.
--
-- Ejecutar sobre una BD nueva: crea la base, tablas, índices y datos iniciales.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS hotel_san_antonio
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE hotel_san_antonio;

-- =====================================================================
-- DDL — CREACION DE TABLAS
-- =====================================================================

-- ---------------------------------------------------------------------
-- TIPO_HABITACION
-- ---------------------------------------------------------------------
CREATE TABLE tipo_habitacion (
    id_tipo      INT AUTO_INCREMENT PRIMARY KEY,
    nombre       VARCHAR(30)   NOT NULL UNIQUE,
    capacidad    TINYINT       NOT NULL,
    precio_base  DECIMAL(8,2)  NOT NULL
);

-- ---------------------------------------------------------------------
-- HABITACION
-- ---------------------------------------------------------------------
CREATE TABLE habitacion (
    id_habitacion        INT AUTO_INCREMENT PRIMARY KEY,
    numero               VARCHAR(10) NOT NULL UNIQUE,
    id_tipo              INT NOT NULL,
    piso                 TINYINT NOT NULL,
    estado               ENUM('DISPONIBLE','OCUPADA','LIMPIEZA','MANTENIMIENTO') NOT NULL DEFAULT 'DISPONIBLE',
    motivo_mantenimiento VARCHAR(200) NULL,
    CONSTRAINT fk_habitacion_tipo FOREIGN KEY (id_tipo) REFERENCES tipo_habitacion(id_tipo)
);

-- ---------------------------------------------------------------------
-- HUESPED
-- ---------------------------------------------------------------------
CREATE TABLE huesped (
    id_huesped        INT AUTO_INCREMENT PRIMARY KEY,
    tipo_documento    ENUM('DNI','PASAPORTE') NOT NULL,
    num_documento     VARCHAR(20) NOT NULL,
    nombres           VARCHAR(80) NOT NULL,
    apellidos         VARCHAR(80) NOT NULL,
    pais_procedencia  VARCHAR(60) NOT NULL DEFAULT 'Peru',
    telefono          VARCHAR(20),
    email             VARCHAR(100),
    fecha_registro    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_huesped_doc UNIQUE (tipo_documento, num_documento)
);

-- ---------------------------------------------------------------------
-- USUARIO
-- ---------------------------------------------------------------------
CREATE TABLE usuario (
    id_usuario       INT AUTO_INCREMENT PRIMARY KEY,
    nombre           VARCHAR(60) NOT NULL,
    apellido         VARCHAR(60) NOT NULL,
    usuario          VARCHAR(30) NOT NULL UNIQUE,
    contrasena_hash  VARCHAR(255) NOT NULL,
    rol              ENUM('ADMINISTRADOR','RECEPCIONISTA') NOT NULL,
    activo           BOOLEAN NOT NULL DEFAULT TRUE,
    fecha_creacion   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ---------------------------------------------------------------------
-- EMPRESA (cliente al que se factura; sus datos fiscales dependen del RUC,
-- por eso van en su propia tabla y no dentro de RESERVA — 3FN)
-- ---------------------------------------------------------------------
CREATE TABLE empresa (
    id_empresa        INT AUTO_INCREMENT PRIMARY KEY,
    ruc               VARCHAR(11)  NOT NULL UNIQUE,
    razon_social      VARCHAR(150) NOT NULL,
    direccion_fiscal  VARCHAR(200) NULL
);

-- ---------------------------------------------------------------------
-- RESERVA
-- ---------------------------------------------------------------------
CREATE TABLE reserva (
    id_reserva          INT AUTO_INCREMENT PRIMARY KEY,
    id_huesped          INT NOT NULL,
    id_habitacion       INT NOT NULL,
    id_usuario          INT NOT NULL,
    fecha_reserva       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_checkin       DATE NOT NULL,
    fecha_checkout      DATE NOT NULL,
    num_huespedes       INT NOT NULL DEFAULT 1,
    hora_checkin        TIME NULL,
    adelanto            DECIMAL(8,2) NOT NULL DEFAULT 0,
    monto_total         DECIMAL(8,2) NOT NULL,
    estado              ENUM('PENDIENTE','CONFIRMADA','CHECKIN','FINALIZADA','CANCELADA') NOT NULL DEFAULT 'PENDIENTE',
    canal               ENUM('TELEFONO','WHATSAPP','BOOKING','PRESENCIAL') NOT NULL,
    id_empresa          INT NULL,
    motivo_cancelacion  VARCHAR(60)  NULL,
    detalle_cancelacion VARCHAR(500) NULL,
    CONSTRAINT fk_reserva_huesped    FOREIGN KEY (id_huesped)    REFERENCES huesped(id_huesped),
    CONSTRAINT fk_reserva_habitacion FOREIGN KEY (id_habitacion) REFERENCES habitacion(id_habitacion),
    CONSTRAINT fk_reserva_usuario    FOREIGN KEY (id_usuario)    REFERENCES usuario(id_usuario),
    CONSTRAINT fk_reserva_empresa    FOREIGN KEY (id_empresa)    REFERENCES empresa(id_empresa),
    CONSTRAINT chk_reserva_fechas    CHECK (fecha_checkout > fecha_checkin)
);

-- ---------------------------------------------------------------------
-- PAGO
-- ---------------------------------------------------------------------
CREATE TABLE pago (
    id_pago      INT AUTO_INCREMENT PRIMARY KEY,
    id_reserva   INT NOT NULL,
    id_usuario   INT NOT NULL,
    monto        DECIMAL(8,2) NOT NULL,
    metodo_pago  ENUM('YAPE','TRANSFERENCIA','EFECTIVO','TARJETA') NOT NULL,
    tipo_pago    ENUM('ADELANTO','SALDO','COMPLETO') NOT NULL,
    fecha_pago   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pago_reserva FOREIGN KEY (id_reserva) REFERENCES reserva(id_reserva),
    CONSTRAINT fk_pago_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario)
);

-- ---------------------------------------------------------------------
-- CATEGORIA (grupo de productos de la tiendita, conjunto abierto:
-- el Administrador puede crear categorias nuevas, por eso es tabla y no ENUM)
-- ---------------------------------------------------------------------
CREATE TABLE categoria (
    id_categoria INT AUTO_INCREMENT PRIMARY KEY,
    nombre       VARCHAR(40) NOT NULL UNIQUE
);

-- ---------------------------------------------------------------------
-- PRODUCTO (catalogo de la tiendita)
-- ---------------------------------------------------------------------
CREATE TABLE producto (
    id_producto    INT AUTO_INCREMENT PRIMARY KEY,
    codigo_barra   VARCHAR(20) NOT NULL UNIQUE,
    nombre         VARCHAR(120) NOT NULL,
    marca          VARCHAR(60),
    id_categoria   INT NOT NULL,
    precio         DECIMAL(6,2) NOT NULL,
    stock          INT NOT NULL DEFAULT 0,
    activo         BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_producto_categoria FOREIGN KEY (id_categoria) REFERENCES categoria(id_categoria)
);

-- ---------------------------------------------------------------------
-- COMPROBANTE (se crea primero: VENTA_TIENDA lo referencia)
-- ---------------------------------------------------------------------
CREATE TABLE comprobante (
    id_comprobante  INT AUTO_INCREMENT PRIMARY KEY,
    id_reserva      INT NULL,
    id_usuario      INT NOT NULL,
    tipo            ENUM('BOLETA','NOTA_VENTA','FACTURA') NOT NULL,
    numero          VARCHAR(20) NOT NULL UNIQUE,
    fecha_emision   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    monto_total     DECIMAL(8,2) NOT NULL,
    CONSTRAINT fk_comprobante_reserva FOREIGN KEY (id_reserva) REFERENCES reserva(id_reserva),
    CONSTRAINT fk_comprobante_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario)
);

-- ---------------------------------------------------------------------
-- VENTA_TIENDA (cabecera del carrito)
-- id_comprobante queda NULL mientras el consumo esta "pendiente" en la
-- cuenta de la habitacion; se completa recien cuando se emite el recibo
-- final (check-out), que puede agrupar varias ventas de una sola vez.
-- ---------------------------------------------------------------------
CREATE TABLE venta_tienda (
    id_venta         INT AUTO_INCREMENT PRIMARY KEY,
    id_huesped       INT NULL,
    id_habitacion    INT NULL,
    id_usuario       INT NOT NULL,
    id_comprobante   INT NULL,
    cliente_externo  VARCHAR(100) NULL,
    metodo_pago      ENUM('EFECTIVO','YAPE','TRANSFERENCIA','TARJETA') NULL,
    fecha_venta      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total            DECIMAL(8,2) NOT NULL,
    CONSTRAINT fk_venta_huesped     FOREIGN KEY (id_huesped)     REFERENCES huesped(id_huesped),
    CONSTRAINT fk_venta_habitacion  FOREIGN KEY (id_habitacion)  REFERENCES habitacion(id_habitacion),
    CONSTRAINT fk_venta_usuario     FOREIGN KEY (id_usuario)     REFERENCES usuario(id_usuario),
    CONSTRAINT fk_venta_comprobante FOREIGN KEY (id_comprobante) REFERENCES comprobante(id_comprobante)
);

-- ---------------------------------------------------------------------
-- DETALLE_VENTA (productos dentro de cada venta)
-- ---------------------------------------------------------------------
CREATE TABLE detalle_venta (
    id_detalle       INT AUTO_INCREMENT PRIMARY KEY,
    id_venta         INT NOT NULL,
    id_producto      INT NOT NULL,
    cantidad         INT NOT NULL,
    precio_unitario  DECIMAL(6,2) NOT NULL,
    subtotal         DECIMAL(8,2) NOT NULL,
    CONSTRAINT fk_detalle_venta    FOREIGN KEY (id_venta)    REFERENCES venta_tienda(id_venta),
    CONSTRAINT fk_detalle_producto FOREIGN KEY (id_producto) REFERENCES producto(id_producto)
);


-- =====================================================================
-- INDICES adicionales (las PK, UNIQUE y FK ya se indexan automaticamente)
-- =====================================================================
CREATE INDEX idx_habitacion_estado ON habitacion (estado);
CREATE INDEX idx_huesped_apellidos ON huesped (apellidos);
CREATE INDEX idx_reserva_fechas    ON reserva (fecha_checkin, fecha_checkout);
CREATE INDEX idx_reserva_estado    ON reserva (estado);


-- AMPLIACIONES DE OPERACION SEGURA
-- Ejecutar una sola vez sobre hotel_san_antonio, despues de una copia de seguridad.
-- No elimina tablas ni filas. La instalacion nueva requiere primero hotel_san_antonio.sql.

ALTER TABLE usuario MODIFY rol ENUM('ADMINISTRADOR','RECEPCIONISTA','LIMPIEZA') NOT NULL;

CREATE TABLE reserva_huesped (
    id_reserva INT NOT NULL,
    id_huesped INT NOT NULL,
    principal BOOLEAN NOT NULL DEFAULT FALSE,
    fecha_asociacion DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_reserva, id_huesped),
    CONSTRAINT fk_rh_reserva FOREIGN KEY (id_reserva) REFERENCES reserva(id_reserva),
    CONSTRAINT fk_rh_huesped FOREIGN KEY (id_huesped) REFERENCES huesped(id_huesped)
);
INSERT INTO reserva_huesped (id_reserva, id_huesped, principal)
SELECT id_reserva, id_huesped, TRUE FROM reserva;

-- El precio no define necesariamente la categoria. El administrador configura
-- este orden de ascenso antes de ofrecer cambios entre tipos diferentes.
ALTER TABLE tipo_habitacion ADD COLUMN nivel_categoria SMALLINT NULL;

CREATE TABLE cambio_habitacion (
    id_cambio INT AUTO_INCREMENT PRIMARY KEY,
    id_reserva INT NOT NULL,
    id_habitacion_anterior INT NOT NULL,
    id_habitacion_nueva INT NOT NULL,
    id_usuario INT NOT NULL,
    fecha_cambio DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    precio_anterior DECIMAL(8,2) NOT NULL,
    precio_nuevo DECIMAL(8,2) NOT NULL,
    motivo VARCHAR(300) NULL,
    CONSTRAINT fk_ch_reserva FOREIGN KEY (id_reserva) REFERENCES reserva(id_reserva),
    CONSTRAINT fk_ch_anterior FOREIGN KEY (id_habitacion_anterior) REFERENCES habitacion(id_habitacion),
    CONSTRAINT fk_ch_nueva FOREIGN KEY (id_habitacion_nueva) REFERENCES habitacion(id_habitacion),
    CONSTRAINT fk_ch_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario)
);

CREATE TABLE gasto (
    id_gasto INT AUTO_INCREMENT PRIMARY KEY,
    fecha DATE NOT NULL,
    concepto VARCHAR(150) NOT NULL,
    categoria VARCHAR(60) NOT NULL,
    monto DECIMAL(10,2) NOT NULL,
    metodo_pago VARCHAR(30) NOT NULL,
    observacion VARCHAR(500) NULL,
    motivo_registro_tardio VARCHAR(300) NULL,
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    id_usuario INT NOT NULL,
    fecha_registro DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_gasto_monto CHECK (monto > 0),
    CONSTRAINT fk_gasto_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario),
    INDEX idx_gasto_fecha (fecha, activo)
);

CREATE TABLE auditoria (
    id_auditoria BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_usuario INT NOT NULL,
    accion VARCHAR(60) NOT NULL,
    entidad VARCHAR(60) NOT NULL,
    id_entidad INT NOT NULL,
    detalle VARCHAR(500) NULL,
    fecha DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_auditoria_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario),
    INDEX idx_auditoria_entidad (entidad, id_entidad)
);

-- Fecha del hecho separada de la fecha real de captura del sistema.
ALTER TABLE reserva ADD COLUMN fecha_evento DATE NULL,
    ADD COLUMN motivo_registro_tardio VARCHAR(300) NULL,
    ADD COLUMN id_usuario_regulariza INT NULL,
    ADD CONSTRAINT fk_reserva_regulariza FOREIGN KEY (id_usuario_regulariza) REFERENCES usuario(id_usuario);

ALTER TABLE pago ADD COLUMN fecha_evento DATE NULL,
    ADD COLUMN motivo_registro_tardio VARCHAR(300) NULL,
    ADD COLUMN id_usuario_regulariza INT NULL,
    ADD CONSTRAINT fk_pago_regulariza FOREIGN KEY (id_usuario_regulariza) REFERENCES usuario(id_usuario);

CREATE TABLE recuperacion_contrasena (
    id_recuperacion BIGINT AUTO_INCREMENT PRIMARY KEY,
    id_usuario INT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expira DATETIME NOT NULL,
    usado DATETIME NULL,
    creado DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_recuperacion_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario),
    INDEX idx_recuperacion_usuario (id_usuario, expira)
);

-- Operación de almacén y caja. En una base existente use la migración 002.
CREATE TABLE movimiento_stock (
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
CREATE TABLE movimiento_caja (
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
CREATE TABLE arqueo_caja (
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
CREATE TABLE cierre_caja (
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


-- CATALOGOS ORIGINALES DEL PROYECTO
INSERT INTO tipo_habitacion (nombre, capacidad, precio_base) VALUES
('Simple',      1, 100.00),
('Ejecutiva',   1, 120.00),
('Doble',       2, 150.00),
('Matrimonial', 2, 130.00),
('Suite',       2, 150.00),
('King',        2, 180.00);

INSERT INTO habitacion (numero, id_tipo, piso, estado) VALUES
('101',1,1,'DISPONIBLE'), ('102',1,1,'DISPONIBLE'), ('103',1,1,'DISPONIBLE'),      ('104',1,1,'DISPONIBLE'),
('105',2,1,'DISPONIBLE'), ('106',2,1,'DISPONIBLE'),   ('107',2,1,'DISPONIBLE'),  ('108',2,1,'DISPONIBLE'),
('201',3,2,'DISPONIBLE'),    ('202',3,2,'DISPONIBLE'), ('203',3,2,'DISPONIBLE'), ('204',3,2,'DISPONIBLE'),
('205',4,2,'DISPONIBLE'), ('206',4,2,'DISPONIBLE'), ('207',4,2,'DISPONIBLE'),    ('208',4,2,'DISPONIBLE'),
('301',5,3,'DISPONIBLE'), ('302',5,3,'DISPONIBLE'), ('303',5,3,'DISPONIBLE'),   ('304',5,3,'DISPONIBLE'),
('305',6,3,'DISPONIBLE'), ('306',6,3,'DISPONIBLE'),    ('307',6,3,'DISPONIBLE'), ('308',6,3,'DISPONIBLE');

INSERT INTO categoria (nombre) VALUES
('Bebidas'), ('Snacks'), ('Golosinas'), ('Higiene personal'), ('Otros');

INSERT INTO producto (codigo_barra, nombre, marca, id_categoria, precio, stock) VALUES
('7750243009116', 'Galleta Soda',         'Field',    2, 1.50, 40),
('7751271015008', 'Inca Kola 500ml',      'Inca Kola',1, 3.50, 30),
('7750243002322', 'Agua sin gas 625ml',   'San Luis', 1, 2.00, 50),
('7751148000208', 'Papitas Lays',         'Lays',     2, 2.50, 25),
('7750070032001', 'Chocolate Sublime',    'Nestle',   3, 2.00, 35);

-- CUENTAS INICIALES CON PBKDF2
START TRANSACTION;
INSERT INTO usuario (nombre, apellido, usuario, contrasena_hash, rol, activo)
VALUES
    ('Administrador', 'General', 'admin', 'pbkdf2$210000$FVo8kZDdc3y2STloxFeYyw==$xHoFg4lRwcaVryP+TNi/oX+u3u7qIccvPhDECFrK3nU=', 'ADMINISTRADOR', 1),
    ('Recepcionista', 'Hotel', 'recepcion', 'pbkdf2$210000$5sb/E5bxvBZDFTsjcJZOsQ==$yGorwxiscuwz/WW0hDG2c0nr8h0tVufaluYnIm9Fhyg=', 'RECEPCIONISTA', 1);
COMMIT;
