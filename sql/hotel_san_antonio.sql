-- =====================================================================
-- Hotel San Antonio — Sistema de Gestion Hotelera y Tiendita
-- Taller de Programacion II — Trabajo de Primera Unidad
-- Script DDL + DML unico (MySQL / MariaDB — probado para phpMyAdmin)
--
-- Ejecutar este archivo completo sobre una BD nueva: crea la base de
-- datos, las 12 tablas, los indices y carga los datos de prueba.
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
-- DML — DATOS DE PRUEBA
-- =====================================================================

-- Tipos de habitacion (segun entrevista al hotel)
INSERT INTO tipo_habitacion (nombre, capacidad, precio_base) VALUES
('Simple',      1, 100.00),
('Ejecutiva',   1, 120.00),
('Doble',       2, 150.00),
('Matrimonial', 2, 130.00),
('Suite',       2, 150.00),
('King',        2, 180.00);

-- 24 habitaciones: 4 por cada tipo, numeradas por piso
INSERT INTO habitacion (numero, id_tipo, piso, estado) VALUES
('101',1,1,'DISPONIBLE'), ('102',1,1,'DISPONIBLE'), ('103',1,1,'OCUPADA'),      ('104',1,1,'DISPONIBLE'),
('105',2,1,'DISPONIBLE'), ('106',2,1,'LIMPIEZA'),   ('107',2,1,'DISPONIBLE'),  ('108',2,1,'DISPONIBLE'),
('201',3,2,'OCUPADA'),    ('202',3,2,'DISPONIBLE'), ('203',3,2,'DISPONIBLE'), ('204',3,2,'MANTENIMIENTO'),
('205',4,2,'DISPONIBLE'), ('206',4,2,'DISPONIBLE'), ('207',4,2,'OCUPADA'),    ('208',4,2,'DISPONIBLE'),
('301',5,3,'DISPONIBLE'), ('302',5,3,'DISPONIBLE'), ('303',5,3,'LIMPIEZA'),   ('304',5,3,'DISPONIBLE'),
('305',6,3,'DISPONIBLE'), ('306',6,3,'OCUPADA'),    ('307',6,3,'DISPONIBLE'), ('308',6,3,'DISPONIBLE');

-- Usuarios del sistema (contrasena de prueba: "123456", cifrada con SHA2 como placeholder;
-- en la app Java se debe usar BCrypt real, esto es solo para poder probar el login ya)
INSERT INTO usuario (nombre, apellido, usuario, contrasena_hash, rol, activo) VALUES
('Ana',   'Torres',  'admin',        SHA2('123456', 256), 'ADMINISTRADOR', TRUE),
('Luis',  'Vasquez', 'recepcion1',   SHA2('123456', 256), 'RECEPCIONISTA', TRUE);

-- Huespedes de prueba
INSERT INTO huesped (tipo_documento, num_documento, nombres, apellidos, pais_procedencia, telefono, email) VALUES
('DNI',       '47852136', 'Carlos',  'Mendoza Ruiz',    'Peru',    '987654321', 'carlos.mendoza@mail.com'),
('DNI',       '71234589', 'Maria',   'Fernandez Diaz',  'Peru',    '956123478', NULL),
('PASAPORTE', 'AB123456', 'John',    'Smith',           'Estados Unidos', '999888777', 'john.smith@mail.com');

-- Empresa de prueba (datos devueltos por la API de RUC para el RUC de ejemplo)
INSERT INTO empresa (ruc, razon_social, direccion_fiscal) VALUES
('20131312955', 'SUPERINTENDENCIA NACIONAL DE ADUANAS Y DE ADMINISTRACION TRIBUTARIA - SUNAT', 'AV. GARCILASO DE LA VEGA NRO. 1472');

-- Reservas de prueba
INSERT INTO reserva (id_huesped, id_habitacion, id_usuario, fecha_checkin, fecha_checkout, adelanto, monto_total, estado, canal) VALUES
(1, 9,  2, '2026-09-20', '2026-09-23', 75.00,  150.00, 'CONFIRMADA', 'WHATSAPP'),
(2, 15, 2, '2026-09-19', '2026-09-21', 65.00,  130.00, 'CHECKIN',    'TELEFONO'),
(3, 22, 1, '2026-09-25', '2026-09-28', 90.00,  180.00, 'PENDIENTE',  'BOOKING');

-- Pagos de prueba
INSERT INTO pago (id_reserva, id_usuario, monto, metodo_pago, tipo_pago) VALUES
(1, 2, 75.00, 'YAPE',          'ADELANTO'),
(2, 2, 65.00, 'EFECTIVO',      'ADELANTO'),
(3, 1, 90.00, 'TRANSFERENCIA', 'ADELANTO');

-- Categorias de producto
INSERT INTO categoria (nombre) VALUES
('Bebidas'), ('Snacks'), ('Golosinas'), ('Higiene personal'), ('Otros');

-- Productos de la tiendita (codigos de barra reales o plausibles, para probar con Open Food Facts)
INSERT INTO producto (codigo_barra, nombre, marca, id_categoria, precio, stock) VALUES
('7750243009116', 'Galleta Soda',         'Field',    2, 1.50, 40),
('7751271015008', 'Inca Kola 500ml',      'Inca Kola',1, 3.50, 30),
('7750243002322', 'Agua sin gas 625ml',   'San Luis', 1, 2.00, 50),
('7751148000208', 'Papitas Lays',         'Lays',     2, 2.50, 25),
('7750070032001', 'Chocolate Sublime',    'Nestle',   3, 2.00, 35);

-- Caso 1: huesped hace CHECK-OUT (reserva 1). Se emite UN comprobante que
-- consolida el total de la habitacion (150.00) + su consumo de tiendita (5.50).
INSERT INTO comprobante (id_reserva, id_usuario, tipo, numero, monto_total) VALUES
(1, 2, 'BOLETA', 'B001-000001', 155.50);

INSERT INTO venta_tienda (id_huesped, id_habitacion, id_usuario, id_comprobante, cliente_externo, total) VALUES
(1, 9, 2, 1, NULL, 5.50);

INSERT INTO detalle_venta (id_venta, id_producto, cantidad, precio_unitario, subtotal) VALUES
(1, 1, 1, 1.50, 1.50),
(1, 3, 2, 2.00, 4.00);

-- Caso 2: cliente externo que solo compra en la tiendita (no se hospeda).
-- Aqui el comprobante se emite al toque, no espera a ningun check-out.
INSERT INTO comprobante (id_reserva, id_usuario, tipo, numero, monto_total) VALUES
(NULL, 2, 'NOTA_VENTA', 'NV001-000001', 3.50);

INSERT INTO venta_tienda (id_huesped, id_habitacion, id_usuario, id_comprobante, cliente_externo, total) VALUES
(NULL, NULL, 2, 2, 'Cliente varios', 3.50);

INSERT INTO detalle_venta (id_venta, id_producto, cantidad, precio_unitario, subtotal) VALUES
(2, 2, 1, 3.50, 3.50);

-- Caso 3: huesped en estadia activa (reserva 2, estado CHECKIN) compra en la
-- tiendita AHORA; queda "pendiente" en su cuenta (id_comprobante = NULL) hasta
-- que haga check-out y se emita el comprobante final.
INSERT INTO venta_tienda (id_huesped, id_habitacion, id_usuario, id_comprobante, cliente_externo, total) VALUES
(2, 15, 2, NULL, NULL, 2.00);

INSERT INTO detalle_venta (id_venta, id_producto, cantidad, precio_unitario, subtotal) VALUES
(3, 5, 1, 2.00, 2.00);


-- =====================================================================
-- INDICES adicionales (las PK, UNIQUE y FK ya se indexan automaticamente)
-- =====================================================================
CREATE INDEX idx_habitacion_estado ON habitacion (estado);
CREATE INDEX idx_huesped_apellidos ON huesped (apellidos);
CREATE INDEX idx_reserva_fechas    ON reserva (fecha_checkin, fecha_checkout);
CREATE INDEX idx_reserva_estado    ON reserva (estado);
