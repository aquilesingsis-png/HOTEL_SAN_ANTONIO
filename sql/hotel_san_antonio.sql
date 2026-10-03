-- =====================================================================
-- Hotel San Antonio — Sistema de Gestion Hotelera y Tiendita
-- Taller de Programacion II — Trabajo de Primera Unidad
-- Esquema base DDL (MySQL / MariaDB); aplicar despues las migraciones
--
-- Ejecutar sobre una BD nueva: crea la base, 12 tablas e indices.
-- No incluye usuarios ni datos de prueba.
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
-- INDICES adicionales (las PK, UNIQUE y FK ya se indexan automaticamente)
-- =====================================================================
CREATE INDEX idx_habitacion_estado ON habitacion (estado);
CREATE INDEX idx_huesped_apellidos ON huesped (apellidos);
CREATE INDEX idx_reserva_fechas    ON reserva (fecha_checkin, fecha_checkout);
CREATE INDEX idx_reserva_estado    ON reserva (estado);
