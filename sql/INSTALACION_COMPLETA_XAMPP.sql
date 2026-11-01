-- HOTEL SAN ANTONIO: SCRIPT UNICO DE LA BASE DE DATOS PARA XAMPP/MARIADB (es el unico .sql del proyecto)
--
-- BASE NUEVA (sin tablas): ejecute el archivo completo.
-- BASE QUE YA EXISTE: lo mas simple es borrarla (phpMyAdmin > hotel_san_antonio > Operaciones > Eliminar la base de
--   datos) y ejecutar de nuevo el archivo completo. OJO: se pierden sus datos; haga antes un respaldo si le importan.
--   Si tiene datos que NO quiere perder, no la borre: ejecute SOLO el ultimo bloque del archivo, el de las
--   actualizaciones (esta al final, bajo un titulo con lineas de =====). Se puede ejecutar mas de una vez.
-- Todo cambio nuevo de la base se agrega al final de ese bloque, con ADD COLUMN IF NOT EXISTS y similares.
--
-- Una base nueva crea 24 tablas, tipos, 24 habitaciones, catalogo de tienda, dos cuentas y el usuario de MySQL
-- hotel_app (el que usa el programa, con privilegios minimos; vea el punto 8 del bloque final).
-- No contiene personas, reservas ni pagos inventados.
-- Las habitaciones se inician DISPONIBLES para evitar ocupaciones ficticias.
-- Las cuentas admin y recepcion tienen la clave de prueba 123456: deben cambiarse antes de entregar.

-- =====================================================================
-- Hotel San Antonio — Sistema de Gestion Hotelera y Tiendita
-- Taller de Programacion II — Trabajo de Primera Unidad
-- Esquema completo DDL (MySQL / MariaDB).
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
    monto_total         DECIMAL(8,2) NOT NULL,
    estado              ENUM('CONFIRMADA','CHECKIN','FINALIZADA','CANCELADA') NOT NULL DEFAULT 'CONFIRMADA',
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
-- No elimina tablas ni filas.

ALTER TABLE usuario MODIFY rol ENUM('ADMINISTRADOR','RECEPCIONISTA','LIMPIEZA') NOT NULL;

-- Limite de intentos: tras 5 contrasenas incorrectas la cuenta se bloquea 15 minutos; tras 5 codigos de
-- recuperacion incorrectos el codigo se anula.
ALTER TABLE usuario
    ADD COLUMN intentos_fallidos TINYINT NOT NULL DEFAULT 0,
    ADD COLUMN bloqueado_hasta DATETIME NULL,
    ADD COLUMN intentos_recuperacion TINYINT NOT NULL DEFAULT 0;

-- REGISTRO DE DIAS PASADOS (cortes de luz o del sistema)
-- La venta del carrito tambien guarda la fecha real del hecho, igual que el pago de una reserva.
ALTER TABLE venta_tienda
    ADD COLUMN fecha_evento DATE NULL,
    ADD COLUMN motivo_registro_tardio VARCHAR(300) NULL,
    ADD COLUMN id_usuario_regulariza INT NULL;

-- Un renglon por cada dia que el sistema estuvo en uso: se escribe al iniciar sesion y cada 30 minutos.
-- Los dias que faltan entre dos renglones son los dias sin uso.
CREATE TABLE actividad_dia (
    fecha DATE PRIMARY KEY,
    id_usuario INT NOT NULL,
    primer_registro DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ultimo_registro DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_actividad_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario)
);

-- Un corte = un tramo de dias seguidos sin uso. El plazo para ponerse al dia es de 24 horas desde el
-- primer ingreso despues del corte; el administrador puede reabrirlo hasta 2 veces, con motivo.
CREATE TABLE corte_sistema (
    id_corte INT AUTO_INCREMENT PRIMARY KEY,
    dia_desde DATE NOT NULL,
    dia_hasta DATE NOT NULL,
    detectado_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    habilita_hasta DATETIME NOT NULL,
    reaperturas TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uq_corte_desde (dia_desde)
);

-- Dias de un corte que ya se terminaron de registrar.
CREATE TABLE dia_regularizado (
    fecha DATE PRIMARY KEY,
    id_corte INT NOT NULL,
    id_usuario INT NOT NULL,
    completado_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dia_reg_corte FOREIGN KEY (id_corte) REFERENCES corte_sistema(id_corte),
    CONSTRAINT fk_dia_reg_usuario FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario)
);

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

-- Operación de almacén y caja.
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

-- CUENTAS INICIALES (clave de prueba: 123456; cambiela antes de entregar)
START TRANSACTION;
INSERT INTO usuario (nombre, apellido, usuario, contrasena_hash, rol, activo)
VALUES
    ('Administrador', 'General', 'admin', '$2a$12$ET1YhvtHTpUfKqn9a2IgK.C31dagjN48Wfislm6Cveof20tsuK0cu', 'ADMINISTRADOR', 1),
    ('Recepcionista', 'Hotel', 'recepcion', '$2a$12$6z4YEdVPJwck30EsbE5BmO0waS860fXLzK9j2QMEO5ic1WsIxhGBy', 'RECEPCIONISTA', 1);
COMMIT;


-- =====================================================================
-- ACTUALIZACIONES PARA UNA BASE QUE YA EXISTE
-- Ejecutar SOLO desde esta linea hasta el final (en phpMyAdmin: pestana SQL, pegar desde aqui hasta el final).
-- Se puede ejecutar mas de una vez: lo que ya esta hecho no se repite. Una base nueva tambien pasa por aqui
-- al ejecutar el archivo completo, sin problema. Cada cambio nuevo se agrega abajo, con su fecha.
-- =====================================================================

USE hotel_san_antonio;

-- ---------------------------------------------------------------------
-- 12/10/2026
-- ---------------------------------------------------------------------

-- 1) COMPROBANTE: cliente, subtotal e IGV, para poder reimprimirlo igual que se emitio.
ALTER TABLE comprobante
    ADD COLUMN IF NOT EXISTS cliente_tipo_doc  VARCHAR(10)  NULL,
    ADD COLUMN IF NOT EXISTS cliente_documento VARCHAR(20)  NULL,
    ADD COLUMN IF NOT EXISTS cliente_nombre    VARCHAR(150) NULL,
    ADD COLUMN IF NOT EXISTS cliente_direccion VARCHAR(200) NULL,
    ADD COLUMN IF NOT EXISTS subtotal          DECIMAL(8,2) NULL,
    ADD COLUMN IF NOT EXISTS igv               DECIMAL(8,2) NULL;

-- 2) CIERRE DE CAJA: hora en que se confirma cada cierre. Un cierre cuenta lo cobrado y pagado desde la hora
--    del cierre anterior, asi hay dos o tres turnos al dia (manana, tarde, noche) sin contar nada dos veces.
ALTER TABLE cierre_caja
    ADD COLUMN IF NOT EXISTS fecha_confirmacion DATETIME NULL;

-- 3) ANULAR SIN BORRAR: una venta de tienda o un comprobante que se anula no se elimina, queda marcado como anulado
--    con quien lo anulo, cuando y por que. Una venta anulada ya no suma en caja, cierres, reportes ni inicio
--    (y devuelve su stock); un comprobante anulado conserva su numero, que no se vuelve a usar.
ALTER TABLE venta_tienda
    ADD COLUMN IF NOT EXISTS estado ENUM('ACTIVA','ANULADA') NOT NULL DEFAULT 'ACTIVA',
    ADD COLUMN IF NOT EXISTS fecha_anulacion DATETIME NULL,
    ADD COLUMN IF NOT EXISTS id_usuario_anula INT NULL,
    ADD COLUMN IF NOT EXISTS motivo_anulacion VARCHAR(200) NULL;
ALTER TABLE venta_tienda
    ADD CONSTRAINT fk_venta_usuario_anula FOREIGN KEY IF NOT EXISTS (id_usuario_anula) REFERENCES usuario(id_usuario);
ALTER TABLE venta_tienda
    ADD CONSTRAINT IF NOT EXISTS chk_venta_anulada CHECK (estado = 'ACTIVA' OR (fecha_anulacion IS NOT NULL
        AND id_usuario_anula IS NOT NULL AND motivo_anulacion IS NOT NULL));

ALTER TABLE comprobante
    ADD COLUMN IF NOT EXISTS estado ENUM('EMITIDO','ANULADO') NOT NULL DEFAULT 'EMITIDO',
    ADD COLUMN IF NOT EXISTS fecha_anulacion DATETIME NULL,
    ADD COLUMN IF NOT EXISTS id_usuario_anula INT NULL,
    ADD COLUMN IF NOT EXISTS motivo_anulacion VARCHAR(200) NULL;
ALTER TABLE comprobante
    ADD CONSTRAINT fk_comprobante_usuario_anula FOREIGN KEY IF NOT EXISTS (id_usuario_anula) REFERENCES usuario(id_usuario);
ALTER TABLE comprobante
    ADD CONSTRAINT IF NOT EXISTS chk_comprobante_anulado CHECK (estado = 'EMITIDO' OR (fecha_anulacion IS NOT NULL
        AND id_usuario_anula IS NOT NULL AND motivo_anulacion IS NOT NULL));

-- 4) RESERVA: ya no existe el estado PENDIENTE (toda reserva nace CONFIRMADA) ni la columna adelanto (el adelanto
--    y los saldos se ven en la tabla pago).
UPDATE reserva SET estado = 'CONFIRMADA' WHERE estado = 'PENDIENTE';
ALTER TABLE reserva
    MODIFY COLUMN estado ENUM('CONFIRMADA','CHECKIN','FINALIZADA','CANCELADA') NOT NULL DEFAULT 'CONFIRMADA',
    DROP COLUMN IF EXISTS adelanto;

-- 5) La base tambien rechaza datos que no tienen sentido (montos, cantidades y stock negativos o en cero).
ALTER TABLE pago           ADD CONSTRAINT IF NOT EXISTS chk_pago_monto        CHECK (monto > 0);
ALTER TABLE venta_tienda   ADD CONSTRAINT IF NOT EXISTS chk_venta_total       CHECK (total > 0);
ALTER TABLE detalle_venta  ADD CONSTRAINT IF NOT EXISTS chk_detalle_cantidad  CHECK (cantidad > 0);
ALTER TABLE producto       ADD CONSTRAINT IF NOT EXISTS chk_producto_precio   CHECK (precio >= 0);
ALTER TABLE producto       ADD CONSTRAINT IF NOT EXISTS chk_producto_stock    CHECK (stock >= 0);
ALTER TABLE reserva        ADD CONSTRAINT IF NOT EXISTS chk_reserva_monto     CHECK (monto_total > 0);
ALTER TABLE tipo_habitacion ADD CONSTRAINT IF NOT EXISTS chk_tipo_capacidad   CHECK (capacidad > 0);
ALTER TABLE tipo_habitacion ADD CONSTRAINT IF NOT EXISTS chk_tipo_precio      CHECK (precio_base > 0);

-- 6) Indices por fecha para los reportes y el libro de caja.
CREATE INDEX IF NOT EXISTS idx_pago_fecha        ON pago (fecha_pago);
CREATE INDEX IF NOT EXISTS idx_venta_fecha       ON venta_tienda (fecha_venta);
CREATE INDEX IF NOT EXISTS idx_movcaja_fecha     ON movimiento_caja (fecha);

-- 7) ALMACEN: una entrada por compra guarda el costo por unidad y, si se anota como gasto "Reposicion de tienda",
--    el numero de ese gasto.
ALTER TABLE movimiento_stock
    ADD COLUMN IF NOT EXISTS costo_unitario DECIMAL(8,2) NULL,
    ADD COLUMN IF NOT EXISTS id_gasto INT NULL;
ALTER TABLE movimiento_stock
    ADD CONSTRAINT fk_ms_gasto FOREIGN KEY IF NOT EXISTS (id_gasto) REFERENCES gasto(id_gasto);
ALTER TABLE movimiento_stock
    ADD CONSTRAINT IF NOT EXISTS chk_ms_costo CHECK (costo_unitario IS NULL OR costo_unitario > 0);

-- 8) USUARIO DE MYSQL DE LA APLICACION, con privilegios minimos. El programa entra con el (ya no con root).
--    Solo puede consultar, insertar, actualizar y borrar filas de hotel_san_antonio: no puede crear ni borrar tablas,
--    ni tocar otras bases, ni crear usuarios. Hay que ejecutarlo con un usuario que pueda crear usuarios (root).
--    Su clave es la misma en todas las laptops y esta en config.properties (db.user y db.password).
CREATE USER IF NOT EXISTS 'hotel_app'@'localhost' IDENTIFIED BY 'z8ZaZMRXTD5rPgHclwK8';
CREATE USER IF NOT EXISTS 'hotel_app'@'127.0.0.1' IDENTIFIED BY 'z8ZaZMRXTD5rPgHclwK8';
GRANT SELECT, INSERT, UPDATE, DELETE ON hotel_san_antonio.* TO 'hotel_app'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE ON hotel_san_antonio.* TO 'hotel_app'@'127.0.0.1';

-- 9) AUDITORIA DE CAMBIOS CON TRIGGERS: cada cambio de datos queda en la tabla auditoria con el valor anterior y el nuevo (JSON).
--    La columna origen dice de donde vino el cambio: SISTEMA (el programa marca cada conexion con el usuario que inicio
--    sesion, variable @auditoria_usuario_id) o BASE_DE_DATOS (alguien cambio datos directo desde phpMyAdmin o MySQL;
--    el detalle indica con que usuario de MySQL). Las filas con tipo_operacion vacio son las que escribe el programa,
--    que guardan que accion fue y por que. Vigilan las 15 tablas importantes (datos de personas, usuarios, reservas
--    y dinero; insertar, modificar y borrar = 45 triggers). No se vigilan las tablas que ya son un historial o un
--    registro tecnico: movimiento_stock, cambio_habitacion, arqueo_caja, actividad_dia, corte_sistema,
--    dia_regularizado y reserva_huesped.
--    No guardan la clave cifrada de los usuarios. Para crear triggers hay que ejecutar el archivo con root.
ALTER TABLE auditoria MODIFY COLUMN id_usuario INT NULL, MODIFY COLUMN id_entidad INT NULL;
ALTER TABLE auditoria
    ADD COLUMN IF NOT EXISTS tipo_operacion ENUM('INSERT','UPDATE','DELETE') NULL,
    ADD COLUMN IF NOT EXISTS datos_anteriores JSON NULL,
    ADD COLUMN IF NOT EXISTS datos_nuevos JSON NULL,
    ADD COLUMN IF NOT EXISTS origen VARCHAR(30) NULL;
CREATE INDEX IF NOT EXISTS idx_auditoria_fecha ON auditoria (fecha);
CREATE INDEX IF NOT EXISTS idx_auditoria_usuario_fecha ON auditoria (id_usuario, fecha);

DROP TRIGGER IF EXISTS trg_aud_categoria_insert;
CREATE TRIGGER trg_aud_categoria_insert AFTER INSERT ON categoria FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'INSERT', 'categoria', NEW.`id_categoria`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'INSERT', NULL, JSON_OBJECT('id_categoria', NEW.`id_categoria`, 'nombre', NEW.`nombre`), IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL;
DROP TRIGGER IF EXISTS trg_aud_categoria_update;
CREATE TRIGGER trg_aud_categoria_update AFTER UPDATE ON categoria FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'UPDATE', 'categoria', NEW.`id_categoria`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'UPDATE', JSON_OBJECT('id_categoria', OLD.`id_categoria`, 'nombre', OLD.`nombre`), JSON_OBJECT('id_categoria', NEW.`id_categoria`, 'nombre', NEW.`nombre`), IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL WHERE NOT (JSON_OBJECT('id_categoria', OLD.`id_categoria`, 'nombre', OLD.`nombre`) <=> JSON_OBJECT('id_categoria', NEW.`id_categoria`, 'nombre', NEW.`nombre`));
DROP TRIGGER IF EXISTS trg_aud_categoria_delete;
CREATE TRIGGER trg_aud_categoria_delete AFTER DELETE ON categoria FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'DELETE', 'categoria', OLD.`id_categoria`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'DELETE', JSON_OBJECT('id_categoria', OLD.`id_categoria`, 'nombre', OLD.`nombre`), NULL, IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL;
DROP TRIGGER IF EXISTS trg_aud_cierre_caja_insert;
CREATE TRIGGER trg_aud_cierre_caja_insert AFTER INSERT ON cierre_caja FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'INSERT', 'cierre_caja', NEW.`id_cierre`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'INSERT', NULL, JSON_OBJECT('id_cierre', NEW.`id_cierre`, 'id_usuario', NEW.`id_usuario`, 'fecha', NEW.`fecha`, 'turno', NEW.`turno`, 'fondo_inicial', NEW.`fondo_inicial`, 'cobros_efectivo', NEW.`cobros_efectivo`, 'otros_ingresos', NEW.`otros_ingresos`, 'salidas_efectivo', NEW.`salidas_efectivo`, 'efectivo_esperado', NEW.`efectivo_esperado`, 'efectivo_contado', NEW.`efectivo_contado`, 'diferencia', NEW.`diferencia`, 'efectivo_entregado', NEW.`efectivo_entregado`, 'fondo_siguiente', NEW.`fondo_siguiente`, 'observacion', NEW.`observacion`, 'estado', NEW.`estado`, 'fecha_registro', NEW.`fecha_registro`, 'fecha_confirmacion', NEW.`fecha_confirmacion`), IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL;
DROP TRIGGER IF EXISTS trg_aud_cierre_caja_update;
CREATE TRIGGER trg_aud_cierre_caja_update AFTER UPDATE ON cierre_caja FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'UPDATE', 'cierre_caja', NEW.`id_cierre`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'UPDATE', JSON_OBJECT('id_cierre', OLD.`id_cierre`, 'id_usuario', OLD.`id_usuario`, 'fecha', OLD.`fecha`, 'turno', OLD.`turno`, 'fondo_inicial', OLD.`fondo_inicial`, 'cobros_efectivo', OLD.`cobros_efectivo`, 'otros_ingresos', OLD.`otros_ingresos`, 'salidas_efectivo', OLD.`salidas_efectivo`, 'efectivo_esperado', OLD.`efectivo_esperado`, 'efectivo_contado', OLD.`efectivo_contado`, 'diferencia', OLD.`diferencia`, 'efectivo_entregado', OLD.`efectivo_entregado`, 'fondo_siguiente', OLD.`fondo_siguiente`, 'observacion', OLD.`observacion`, 'estado', OLD.`estado`, 'fecha_registro', OLD.`fecha_registro`, 'fecha_confirmacion', OLD.`fecha_confirmacion`), JSON_OBJECT('id_cierre', NEW.`id_cierre`, 'id_usuario', NEW.`id_usuario`, 'fecha', NEW.`fecha`, 'turno', NEW.`turno`, 'fondo_inicial', NEW.`fondo_inicial`, 'cobros_efectivo', NEW.`cobros_efectivo`, 'otros_ingresos', NEW.`otros_ingresos`, 'salidas_efectivo', NEW.`salidas_efectivo`, 'efectivo_esperado', NEW.`efectivo_esperado`, 'efectivo_contado', NEW.`efectivo_contado`, 'diferencia', NEW.`diferencia`, 'efectivo_entregado', NEW.`efectivo_entregado`, 'fondo_siguiente', NEW.`fondo_siguiente`, 'observacion', NEW.`observacion`, 'estado', NEW.`estado`, 'fecha_registro', NEW.`fecha_registro`, 'fecha_confirmacion', NEW.`fecha_confirmacion`), IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL WHERE NOT (JSON_OBJECT('id_cierre', OLD.`id_cierre`, 'id_usuario', OLD.`id_usuario`, 'fecha', OLD.`fecha`, 'turno', OLD.`turno`, 'fondo_inicial', OLD.`fondo_inicial`, 'cobros_efectivo', OLD.`cobros_efectivo`, 'otros_ingresos', OLD.`otros_ingresos`, 'salidas_efectivo', OLD.`salidas_efectivo`, 'efectivo_esperado', OLD.`efectivo_esperado`, 'efectivo_contado', OLD.`efectivo_contado`, 'diferencia', OLD.`diferencia`, 'efectivo_entregado', OLD.`efectivo_entregado`, 'fondo_siguiente', OLD.`fondo_siguiente`, 'observacion', OLD.`observacion`, 'estado', OLD.`estado`, 'fecha_registro', OLD.`fecha_registro`, 'fecha_confirmacion', OLD.`fecha_confirmacion`) <=> JSON_OBJECT('id_cierre', NEW.`id_cierre`, 'id_usuario', NEW.`id_usuario`, 'fecha', NEW.`fecha`, 'turno', NEW.`turno`, 'fondo_inicial', NEW.`fondo_inicial`, 'cobros_efectivo', NEW.`cobros_efectivo`, 'otros_ingresos', NEW.`otros_ingresos`, 'salidas_efectivo', NEW.`salidas_efectivo`, 'efectivo_esperado', NEW.`efectivo_esperado`, 'efectivo_contado', NEW.`efectivo_contado`, 'diferencia', NEW.`diferencia`, 'efectivo_entregado', NEW.`efectivo_entregado`, 'fondo_siguiente', NEW.`fondo_siguiente`, 'observacion', NEW.`observacion`, 'estado', NEW.`estado`, 'fecha_registro', NEW.`fecha_registro`, 'fecha_confirmacion', NEW.`fecha_confirmacion`));
DROP TRIGGER IF EXISTS trg_aud_cierre_caja_delete;
CREATE TRIGGER trg_aud_cierre_caja_delete AFTER DELETE ON cierre_caja FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'DELETE', 'cierre_caja', OLD.`id_cierre`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'DELETE', JSON_OBJECT('id_cierre', OLD.`id_cierre`, 'id_usuario', OLD.`id_usuario`, 'fecha', OLD.`fecha`, 'turno', OLD.`turno`, 'fondo_inicial', OLD.`fondo_inicial`, 'cobros_efectivo', OLD.`cobros_efectivo`, 'otros_ingresos', OLD.`otros_ingresos`, 'salidas_efectivo', OLD.`salidas_efectivo`, 'efectivo_esperado', OLD.`efectivo_esperado`, 'efectivo_contado', OLD.`efectivo_contado`, 'diferencia', OLD.`diferencia`, 'efectivo_entregado', OLD.`efectivo_entregado`, 'fondo_siguiente', OLD.`fondo_siguiente`, 'observacion', OLD.`observacion`, 'estado', OLD.`estado`, 'fecha_registro', OLD.`fecha_registro`, 'fecha_confirmacion', OLD.`fecha_confirmacion`), NULL, IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL;
DROP TRIGGER IF EXISTS trg_aud_comprobante_insert;
CREATE TRIGGER trg_aud_comprobante_insert AFTER INSERT ON comprobante FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'INSERT', 'comprobante', NEW.`id_comprobante`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'INSERT', NULL, JSON_OBJECT('id_comprobante', NEW.`id_comprobante`, 'id_reserva', NEW.`id_reserva`, 'id_usuario', NEW.`id_usuario`, 'tipo', NEW.`tipo`, 'numero', NEW.`numero`, 'fecha_emision', NEW.`fecha_emision`, 'monto_total', NEW.`monto_total`, 'cliente_tipo_doc', NEW.`cliente_tipo_doc`, 'cliente_documento', NEW.`cliente_documento`, 'cliente_nombre', NEW.`cliente_nombre`, 'cliente_direccion', NEW.`cliente_direccion`, 'subtotal', NEW.`subtotal`, 'igv', NEW.`igv`, 'estado', NEW.`estado`, 'fecha_anulacion', NEW.`fecha_anulacion`, 'id_usuario_anula', NEW.`id_usuario_anula`, 'motivo_anulacion', NEW.`motivo_anulacion`), IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL;
DROP TRIGGER IF EXISTS trg_aud_comprobante_update;
CREATE TRIGGER trg_aud_comprobante_update AFTER UPDATE ON comprobante FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'UPDATE', 'comprobante', NEW.`id_comprobante`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'UPDATE', JSON_OBJECT('id_comprobante', OLD.`id_comprobante`, 'id_reserva', OLD.`id_reserva`, 'id_usuario', OLD.`id_usuario`, 'tipo', OLD.`tipo`, 'numero', OLD.`numero`, 'fecha_emision', OLD.`fecha_emision`, 'monto_total', OLD.`monto_total`, 'cliente_tipo_doc', OLD.`cliente_tipo_doc`, 'cliente_documento', OLD.`cliente_documento`, 'cliente_nombre', OLD.`cliente_nombre`, 'cliente_direccion', OLD.`cliente_direccion`, 'subtotal', OLD.`subtotal`, 'igv', OLD.`igv`, 'estado', OLD.`estado`, 'fecha_anulacion', OLD.`fecha_anulacion`, 'id_usuario_anula', OLD.`id_usuario_anula`, 'motivo_anulacion', OLD.`motivo_anulacion`), JSON_OBJECT('id_comprobante', NEW.`id_comprobante`, 'id_reserva', NEW.`id_reserva`, 'id_usuario', NEW.`id_usuario`, 'tipo', NEW.`tipo`, 'numero', NEW.`numero`, 'fecha_emision', NEW.`fecha_emision`, 'monto_total', NEW.`monto_total`, 'cliente_tipo_doc', NEW.`cliente_tipo_doc`, 'cliente_documento', NEW.`cliente_documento`, 'cliente_nombre', NEW.`cliente_nombre`, 'cliente_direccion', NEW.`cliente_direccion`, 'subtotal', NEW.`subtotal`, 'igv', NEW.`igv`, 'estado', NEW.`estado`, 'fecha_anulacion', NEW.`fecha_anulacion`, 'id_usuario_anula', NEW.`id_usuario_anula`, 'motivo_anulacion', NEW.`motivo_anulacion`), IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL WHERE NOT (JSON_OBJECT('id_comprobante', OLD.`id_comprobante`, 'id_reserva', OLD.`id_reserva`, 'id_usuario', OLD.`id_usuario`, 'tipo', OLD.`tipo`, 'numero', OLD.`numero`, 'fecha_emision', OLD.`fecha_emision`, 'monto_total', OLD.`monto_total`, 'cliente_tipo_doc', OLD.`cliente_tipo_doc`, 'cliente_documento', OLD.`cliente_documento`, 'cliente_nombre', OLD.`cliente_nombre`, 'cliente_direccion', OLD.`cliente_direccion`, 'subtotal', OLD.`subtotal`, 'igv', OLD.`igv`, 'estado', OLD.`estado`, 'fecha_anulacion', OLD.`fecha_anulacion`, 'id_usuario_anula', OLD.`id_usuario_anula`, 'motivo_anulacion', OLD.`motivo_anulacion`) <=> JSON_OBJECT('id_comprobante', NEW.`id_comprobante`, 'id_reserva', NEW.`id_reserva`, 'id_usuario', NEW.`id_usuario`, 'tipo', NEW.`tipo`, 'numero', NEW.`numero`, 'fecha_emision', NEW.`fecha_emision`, 'monto_total', NEW.`monto_total`, 'cliente_tipo_doc', NEW.`cliente_tipo_doc`, 'cliente_documento', NEW.`cliente_documento`, 'cliente_nombre', NEW.`cliente_nombre`, 'cliente_direccion', NEW.`cliente_direccion`, 'subtotal', NEW.`subtotal`, 'igv', NEW.`igv`, 'estado', NEW.`estado`, 'fecha_anulacion', NEW.`fecha_anulacion`, 'id_usuario_anula', NEW.`id_usuario_anula`, 'motivo_anulacion', NEW.`motivo_anulacion`));
DROP TRIGGER IF EXISTS trg_aud_comprobante_delete;
CREATE TRIGGER trg_aud_comprobante_delete AFTER DELETE ON comprobante FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'DELETE', 'comprobante', OLD.`id_comprobante`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'DELETE', JSON_OBJECT('id_comprobante', OLD.`id_comprobante`, 'id_reserva', OLD.`id_reserva`, 'id_usuario', OLD.`id_usuario`, 'tipo', OLD.`tipo`, 'numero', OLD.`numero`, 'fecha_emision', OLD.`fecha_emision`, 'monto_total', OLD.`monto_total`, 'cliente_tipo_doc', OLD.`cliente_tipo_doc`, 'cliente_documento', OLD.`cliente_documento`, 'cliente_nombre', OLD.`cliente_nombre`, 'cliente_direccion', OLD.`cliente_direccion`, 'subtotal', OLD.`subtotal`, 'igv', OLD.`igv`, 'estado', OLD.`estado`, 'fecha_anulacion', OLD.`fecha_anulacion`, 'id_usuario_anula', OLD.`id_usuario_anula`, 'motivo_anulacion', OLD.`motivo_anulacion`), NULL, IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL;
DROP TRIGGER IF EXISTS trg_aud_detalle_venta_insert;
CREATE TRIGGER trg_aud_detalle_venta_insert AFTER INSERT ON detalle_venta FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'INSERT', 'detalle_venta', NEW.`id_detalle`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'INSERT', NULL, JSON_OBJECT('id_detalle', NEW.`id_detalle`, 'id_venta', NEW.`id_venta`, 'id_producto', NEW.`id_producto`, 'cantidad', NEW.`cantidad`, 'precio_unitario', NEW.`precio_unitario`, 'subtotal', NEW.`subtotal`), IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL;
DROP TRIGGER IF EXISTS trg_aud_detalle_venta_update;
CREATE TRIGGER trg_aud_detalle_venta_update AFTER UPDATE ON detalle_venta FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'UPDATE', 'detalle_venta', NEW.`id_detalle`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'UPDATE', JSON_OBJECT('id_detalle', OLD.`id_detalle`, 'id_venta', OLD.`id_venta`, 'id_producto', OLD.`id_producto`, 'cantidad', OLD.`cantidad`, 'precio_unitario', OLD.`precio_unitario`, 'subtotal', OLD.`subtotal`), JSON_OBJECT('id_detalle', NEW.`id_detalle`, 'id_venta', NEW.`id_venta`, 'id_producto', NEW.`id_producto`, 'cantidad', NEW.`cantidad`, 'precio_unitario', NEW.`precio_unitario`, 'subtotal', NEW.`subtotal`), IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL WHERE NOT (JSON_OBJECT('id_detalle', OLD.`id_detalle`, 'id_venta', OLD.`id_venta`, 'id_producto', OLD.`id_producto`, 'cantidad', OLD.`cantidad`, 'precio_unitario', OLD.`precio_unitario`, 'subtotal', OLD.`subtotal`) <=> JSON_OBJECT('id_detalle', NEW.`id_detalle`, 'id_venta', NEW.`id_venta`, 'id_producto', NEW.`id_producto`, 'cantidad', NEW.`cantidad`, 'precio_unitario', NEW.`precio_unitario`, 'subtotal', NEW.`subtotal`));
DROP TRIGGER IF EXISTS trg_aud_detalle_venta_delete;
CREATE TRIGGER trg_aud_detalle_venta_delete AFTER DELETE ON detalle_venta FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'DELETE', 'detalle_venta', OLD.`id_detalle`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'DELETE', JSON_OBJECT('id_detalle', OLD.`id_detalle`, 'id_venta', OLD.`id_venta`, 'id_producto', OLD.`id_producto`, 'cantidad', OLD.`cantidad`, 'precio_unitario', OLD.`precio_unitario`, 'subtotal', OLD.`subtotal`), NULL, IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL;
DROP TRIGGER IF EXISTS trg_aud_empresa_insert;
CREATE TRIGGER trg_aud_empresa_insert AFTER INSERT ON empresa FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'INSERT', 'empresa', NEW.`id_empresa`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'INSERT', NULL, JSON_OBJECT('id_empresa', NEW.`id_empresa`, 'ruc', NEW.`ruc`, 'razon_social', NEW.`razon_social`, 'direccion_fiscal', NEW.`direccion_fiscal`), IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL;
DROP TRIGGER IF EXISTS trg_aud_empresa_update;
CREATE TRIGGER trg_aud_empresa_update AFTER UPDATE ON empresa FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'UPDATE', 'empresa', NEW.`id_empresa`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'UPDATE', JSON_OBJECT('id_empresa', OLD.`id_empresa`, 'ruc', OLD.`ruc`, 'razon_social', OLD.`razon_social`, 'direccion_fiscal', OLD.`direccion_fiscal`), JSON_OBJECT('id_empresa', NEW.`id_empresa`, 'ruc', NEW.`ruc`, 'razon_social', NEW.`razon_social`, 'direccion_fiscal', NEW.`direccion_fiscal`), IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL WHERE NOT (JSON_OBJECT('id_empresa', OLD.`id_empresa`, 'ruc', OLD.`ruc`, 'razon_social', OLD.`razon_social`, 'direccion_fiscal', OLD.`direccion_fiscal`) <=> JSON_OBJECT('id_empresa', NEW.`id_empresa`, 'ruc', NEW.`ruc`, 'razon_social', NEW.`razon_social`, 'direccion_fiscal', NEW.`direccion_fiscal`));
DROP TRIGGER IF EXISTS trg_aud_empresa_delete;
CREATE TRIGGER trg_aud_empresa_delete AFTER DELETE ON empresa FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'DELETE', 'empresa', OLD.`id_empresa`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'DELETE', JSON_OBJECT('id_empresa', OLD.`id_empresa`, 'ruc', OLD.`ruc`, 'razon_social', OLD.`razon_social`, 'direccion_fiscal', OLD.`direccion_fiscal`), NULL, IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL;
DROP TRIGGER IF EXISTS trg_aud_gasto_insert;
CREATE TRIGGER trg_aud_gasto_insert AFTER INSERT ON gasto FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'INSERT', 'gasto', NEW.`id_gasto`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'INSERT', NULL, JSON_OBJECT('id_gasto', NEW.`id_gasto`, 'fecha', NEW.`fecha`, 'concepto', NEW.`concepto`, 'categoria', NEW.`categoria`, 'monto', NEW.`monto`, 'metodo_pago', NEW.`metodo_pago`, 'observacion', NEW.`observacion`, 'motivo_registro_tardio', NEW.`motivo_registro_tardio`, 'activo', NEW.`activo`, 'id_usuario', NEW.`id_usuario`, 'fecha_registro', NEW.`fecha_registro`), IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL;
DROP TRIGGER IF EXISTS trg_aud_gasto_update;
CREATE TRIGGER trg_aud_gasto_update AFTER UPDATE ON gasto FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'UPDATE', 'gasto', NEW.`id_gasto`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'UPDATE', JSON_OBJECT('id_gasto', OLD.`id_gasto`, 'fecha', OLD.`fecha`, 'concepto', OLD.`concepto`, 'categoria', OLD.`categoria`, 'monto', OLD.`monto`, 'metodo_pago', OLD.`metodo_pago`, 'observacion', OLD.`observacion`, 'motivo_registro_tardio', OLD.`motivo_registro_tardio`, 'activo', OLD.`activo`, 'id_usuario', OLD.`id_usuario`, 'fecha_registro', OLD.`fecha_registro`), JSON_OBJECT('id_gasto', NEW.`id_gasto`, 'fecha', NEW.`fecha`, 'concepto', NEW.`concepto`, 'categoria', NEW.`categoria`, 'monto', NEW.`monto`, 'metodo_pago', NEW.`metodo_pago`, 'observacion', NEW.`observacion`, 'motivo_registro_tardio', NEW.`motivo_registro_tardio`, 'activo', NEW.`activo`, 'id_usuario', NEW.`id_usuario`, 'fecha_registro', NEW.`fecha_registro`), IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL WHERE NOT (JSON_OBJECT('id_gasto', OLD.`id_gasto`, 'fecha', OLD.`fecha`, 'concepto', OLD.`concepto`, 'categoria', OLD.`categoria`, 'monto', OLD.`monto`, 'metodo_pago', OLD.`metodo_pago`, 'observacion', OLD.`observacion`, 'motivo_registro_tardio', OLD.`motivo_registro_tardio`, 'activo', OLD.`activo`, 'id_usuario', OLD.`id_usuario`, 'fecha_registro', OLD.`fecha_registro`) <=> JSON_OBJECT('id_gasto', NEW.`id_gasto`, 'fecha', NEW.`fecha`, 'concepto', NEW.`concepto`, 'categoria', NEW.`categoria`, 'monto', NEW.`monto`, 'metodo_pago', NEW.`metodo_pago`, 'observacion', NEW.`observacion`, 'motivo_registro_tardio', NEW.`motivo_registro_tardio`, 'activo', NEW.`activo`, 'id_usuario', NEW.`id_usuario`, 'fecha_registro', NEW.`fecha_registro`));
DROP TRIGGER IF EXISTS trg_aud_gasto_delete;
CREATE TRIGGER trg_aud_gasto_delete AFTER DELETE ON gasto FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'DELETE', 'gasto', OLD.`id_gasto`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'DELETE', JSON_OBJECT('id_gasto', OLD.`id_gasto`, 'fecha', OLD.`fecha`, 'concepto', OLD.`concepto`, 'categoria', OLD.`categoria`, 'monto', OLD.`monto`, 'metodo_pago', OLD.`metodo_pago`, 'observacion', OLD.`observacion`, 'motivo_registro_tardio', OLD.`motivo_registro_tardio`, 'activo', OLD.`activo`, 'id_usuario', OLD.`id_usuario`, 'fecha_registro', OLD.`fecha_registro`), NULL, IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL;
DROP TRIGGER IF EXISTS trg_aud_habitacion_insert;
CREATE TRIGGER trg_aud_habitacion_insert AFTER INSERT ON habitacion FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'INSERT', 'habitacion', NEW.`id_habitacion`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'INSERT', NULL, JSON_OBJECT('id_habitacion', NEW.`id_habitacion`, 'numero', NEW.`numero`, 'id_tipo', NEW.`id_tipo`, 'piso', NEW.`piso`, 'estado', NEW.`estado`, 'motivo_mantenimiento', NEW.`motivo_mantenimiento`), IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL;
DROP TRIGGER IF EXISTS trg_aud_habitacion_update;
CREATE TRIGGER trg_aud_habitacion_update AFTER UPDATE ON habitacion FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'UPDATE', 'habitacion', NEW.`id_habitacion`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'UPDATE', JSON_OBJECT('id_habitacion', OLD.`id_habitacion`, 'numero', OLD.`numero`, 'id_tipo', OLD.`id_tipo`, 'piso', OLD.`piso`, 'estado', OLD.`estado`, 'motivo_mantenimiento', OLD.`motivo_mantenimiento`), JSON_OBJECT('id_habitacion', NEW.`id_habitacion`, 'numero', NEW.`numero`, 'id_tipo', NEW.`id_tipo`, 'piso', NEW.`piso`, 'estado', NEW.`estado`, 'motivo_mantenimiento', NEW.`motivo_mantenimiento`), IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL WHERE NOT (JSON_OBJECT('id_habitacion', OLD.`id_habitacion`, 'numero', OLD.`numero`, 'id_tipo', OLD.`id_tipo`, 'piso', OLD.`piso`, 'estado', OLD.`estado`, 'motivo_mantenimiento', OLD.`motivo_mantenimiento`) <=> JSON_OBJECT('id_habitacion', NEW.`id_habitacion`, 'numero', NEW.`numero`, 'id_tipo', NEW.`id_tipo`, 'piso', NEW.`piso`, 'estado', NEW.`estado`, 'motivo_mantenimiento', NEW.`motivo_mantenimiento`));
DROP TRIGGER IF EXISTS trg_aud_habitacion_delete;
CREATE TRIGGER trg_aud_habitacion_delete AFTER DELETE ON habitacion FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'DELETE', 'habitacion', OLD.`id_habitacion`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'DELETE', JSON_OBJECT('id_habitacion', OLD.`id_habitacion`, 'numero', OLD.`numero`, 'id_tipo', OLD.`id_tipo`, 'piso', OLD.`piso`, 'estado', OLD.`estado`, 'motivo_mantenimiento', OLD.`motivo_mantenimiento`), NULL, IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL;
DROP TRIGGER IF EXISTS trg_aud_huesped_insert;
CREATE TRIGGER trg_aud_huesped_insert AFTER INSERT ON huesped FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'INSERT', 'huesped', NEW.`id_huesped`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'INSERT', NULL, JSON_OBJECT('id_huesped', NEW.`id_huesped`, 'tipo_documento', NEW.`tipo_documento`, 'num_documento', NEW.`num_documento`, 'nombres', NEW.`nombres`, 'apellidos', NEW.`apellidos`, 'pais_procedencia', NEW.`pais_procedencia`, 'telefono', NEW.`telefono`, 'email', NEW.`email`, 'fecha_registro', NEW.`fecha_registro`), IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL;
DROP TRIGGER IF EXISTS trg_aud_huesped_update;
CREATE TRIGGER trg_aud_huesped_update AFTER UPDATE ON huesped FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'UPDATE', 'huesped', NEW.`id_huesped`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'UPDATE', JSON_OBJECT('id_huesped', OLD.`id_huesped`, 'tipo_documento', OLD.`tipo_documento`, 'num_documento', OLD.`num_documento`, 'nombres', OLD.`nombres`, 'apellidos', OLD.`apellidos`, 'pais_procedencia', OLD.`pais_procedencia`, 'telefono', OLD.`telefono`, 'email', OLD.`email`, 'fecha_registro', OLD.`fecha_registro`), JSON_OBJECT('id_huesped', NEW.`id_huesped`, 'tipo_documento', NEW.`tipo_documento`, 'num_documento', NEW.`num_documento`, 'nombres', NEW.`nombres`, 'apellidos', NEW.`apellidos`, 'pais_procedencia', NEW.`pais_procedencia`, 'telefono', NEW.`telefono`, 'email', NEW.`email`, 'fecha_registro', NEW.`fecha_registro`), IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL WHERE NOT (JSON_OBJECT('id_huesped', OLD.`id_huesped`, 'tipo_documento', OLD.`tipo_documento`, 'num_documento', OLD.`num_documento`, 'nombres', OLD.`nombres`, 'apellidos', OLD.`apellidos`, 'pais_procedencia', OLD.`pais_procedencia`, 'telefono', OLD.`telefono`, 'email', OLD.`email`, 'fecha_registro', OLD.`fecha_registro`) <=> JSON_OBJECT('id_huesped', NEW.`id_huesped`, 'tipo_documento', NEW.`tipo_documento`, 'num_documento', NEW.`num_documento`, 'nombres', NEW.`nombres`, 'apellidos', NEW.`apellidos`, 'pais_procedencia', NEW.`pais_procedencia`, 'telefono', NEW.`telefono`, 'email', NEW.`email`, 'fecha_registro', NEW.`fecha_registro`));
DROP TRIGGER IF EXISTS trg_aud_huesped_delete;
CREATE TRIGGER trg_aud_huesped_delete AFTER DELETE ON huesped FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'DELETE', 'huesped', OLD.`id_huesped`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'DELETE', JSON_OBJECT('id_huesped', OLD.`id_huesped`, 'tipo_documento', OLD.`tipo_documento`, 'num_documento', OLD.`num_documento`, 'nombres', OLD.`nombres`, 'apellidos', OLD.`apellidos`, 'pais_procedencia', OLD.`pais_procedencia`, 'telefono', OLD.`telefono`, 'email', OLD.`email`, 'fecha_registro', OLD.`fecha_registro`), NULL, IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL;
DROP TRIGGER IF EXISTS trg_aud_movimiento_caja_insert;
CREATE TRIGGER trg_aud_movimiento_caja_insert AFTER INSERT ON movimiento_caja FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'INSERT', 'movimiento_caja', NEW.`id_movimiento`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'INSERT', NULL, JSON_OBJECT('id_movimiento', NEW.`id_movimiento`, 'id_usuario', NEW.`id_usuario`, 'tipo', NEW.`tipo`, 'concepto', NEW.`concepto`, 'metodo_pago', NEW.`metodo_pago`, 'monto', NEW.`monto`, 'referencia', NEW.`referencia`, 'observacion', NEW.`observacion`, 'fecha', NEW.`fecha`), IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL;
DROP TRIGGER IF EXISTS trg_aud_movimiento_caja_update;
CREATE TRIGGER trg_aud_movimiento_caja_update AFTER UPDATE ON movimiento_caja FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'UPDATE', 'movimiento_caja', NEW.`id_movimiento`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'UPDATE', JSON_OBJECT('id_movimiento', OLD.`id_movimiento`, 'id_usuario', OLD.`id_usuario`, 'tipo', OLD.`tipo`, 'concepto', OLD.`concepto`, 'metodo_pago', OLD.`metodo_pago`, 'monto', OLD.`monto`, 'referencia', OLD.`referencia`, 'observacion', OLD.`observacion`, 'fecha', OLD.`fecha`), JSON_OBJECT('id_movimiento', NEW.`id_movimiento`, 'id_usuario', NEW.`id_usuario`, 'tipo', NEW.`tipo`, 'concepto', NEW.`concepto`, 'metodo_pago', NEW.`metodo_pago`, 'monto', NEW.`monto`, 'referencia', NEW.`referencia`, 'observacion', NEW.`observacion`, 'fecha', NEW.`fecha`), IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL WHERE NOT (JSON_OBJECT('id_movimiento', OLD.`id_movimiento`, 'id_usuario', OLD.`id_usuario`, 'tipo', OLD.`tipo`, 'concepto', OLD.`concepto`, 'metodo_pago', OLD.`metodo_pago`, 'monto', OLD.`monto`, 'referencia', OLD.`referencia`, 'observacion', OLD.`observacion`, 'fecha', OLD.`fecha`) <=> JSON_OBJECT('id_movimiento', NEW.`id_movimiento`, 'id_usuario', NEW.`id_usuario`, 'tipo', NEW.`tipo`, 'concepto', NEW.`concepto`, 'metodo_pago', NEW.`metodo_pago`, 'monto', NEW.`monto`, 'referencia', NEW.`referencia`, 'observacion', NEW.`observacion`, 'fecha', NEW.`fecha`));
DROP TRIGGER IF EXISTS trg_aud_movimiento_caja_delete;
CREATE TRIGGER trg_aud_movimiento_caja_delete AFTER DELETE ON movimiento_caja FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'DELETE', 'movimiento_caja', OLD.`id_movimiento`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'DELETE', JSON_OBJECT('id_movimiento', OLD.`id_movimiento`, 'id_usuario', OLD.`id_usuario`, 'tipo', OLD.`tipo`, 'concepto', OLD.`concepto`, 'metodo_pago', OLD.`metodo_pago`, 'monto', OLD.`monto`, 'referencia', OLD.`referencia`, 'observacion', OLD.`observacion`, 'fecha', OLD.`fecha`), NULL, IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL;
DROP TRIGGER IF EXISTS trg_aud_pago_insert;
CREATE TRIGGER trg_aud_pago_insert AFTER INSERT ON pago FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'INSERT', 'pago', NEW.`id_pago`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'INSERT', NULL, JSON_OBJECT('id_pago', NEW.`id_pago`, 'id_reserva', NEW.`id_reserva`, 'id_usuario', NEW.`id_usuario`, 'monto', NEW.`monto`, 'metodo_pago', NEW.`metodo_pago`, 'tipo_pago', NEW.`tipo_pago`, 'fecha_pago', NEW.`fecha_pago`, 'fecha_evento', NEW.`fecha_evento`, 'motivo_registro_tardio', NEW.`motivo_registro_tardio`, 'id_usuario_regulariza', NEW.`id_usuario_regulariza`), IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL;
DROP TRIGGER IF EXISTS trg_aud_pago_update;
CREATE TRIGGER trg_aud_pago_update AFTER UPDATE ON pago FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'UPDATE', 'pago', NEW.`id_pago`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'UPDATE', JSON_OBJECT('id_pago', OLD.`id_pago`, 'id_reserva', OLD.`id_reserva`, 'id_usuario', OLD.`id_usuario`, 'monto', OLD.`monto`, 'metodo_pago', OLD.`metodo_pago`, 'tipo_pago', OLD.`tipo_pago`, 'fecha_pago', OLD.`fecha_pago`, 'fecha_evento', OLD.`fecha_evento`, 'motivo_registro_tardio', OLD.`motivo_registro_tardio`, 'id_usuario_regulariza', OLD.`id_usuario_regulariza`), JSON_OBJECT('id_pago', NEW.`id_pago`, 'id_reserva', NEW.`id_reserva`, 'id_usuario', NEW.`id_usuario`, 'monto', NEW.`monto`, 'metodo_pago', NEW.`metodo_pago`, 'tipo_pago', NEW.`tipo_pago`, 'fecha_pago', NEW.`fecha_pago`, 'fecha_evento', NEW.`fecha_evento`, 'motivo_registro_tardio', NEW.`motivo_registro_tardio`, 'id_usuario_regulariza', NEW.`id_usuario_regulariza`), IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL WHERE NOT (JSON_OBJECT('id_pago', OLD.`id_pago`, 'id_reserva', OLD.`id_reserva`, 'id_usuario', OLD.`id_usuario`, 'monto', OLD.`monto`, 'metodo_pago', OLD.`metodo_pago`, 'tipo_pago', OLD.`tipo_pago`, 'fecha_pago', OLD.`fecha_pago`, 'fecha_evento', OLD.`fecha_evento`, 'motivo_registro_tardio', OLD.`motivo_registro_tardio`, 'id_usuario_regulariza', OLD.`id_usuario_regulariza`) <=> JSON_OBJECT('id_pago', NEW.`id_pago`, 'id_reserva', NEW.`id_reserva`, 'id_usuario', NEW.`id_usuario`, 'monto', NEW.`monto`, 'metodo_pago', NEW.`metodo_pago`, 'tipo_pago', NEW.`tipo_pago`, 'fecha_pago', NEW.`fecha_pago`, 'fecha_evento', NEW.`fecha_evento`, 'motivo_registro_tardio', NEW.`motivo_registro_tardio`, 'id_usuario_regulariza', NEW.`id_usuario_regulariza`));
DROP TRIGGER IF EXISTS trg_aud_pago_delete;
CREATE TRIGGER trg_aud_pago_delete AFTER DELETE ON pago FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'DELETE', 'pago', OLD.`id_pago`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'DELETE', JSON_OBJECT('id_pago', OLD.`id_pago`, 'id_reserva', OLD.`id_reserva`, 'id_usuario', OLD.`id_usuario`, 'monto', OLD.`monto`, 'metodo_pago', OLD.`metodo_pago`, 'tipo_pago', OLD.`tipo_pago`, 'fecha_pago', OLD.`fecha_pago`, 'fecha_evento', OLD.`fecha_evento`, 'motivo_registro_tardio', OLD.`motivo_registro_tardio`, 'id_usuario_regulariza', OLD.`id_usuario_regulariza`), NULL, IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL;
DROP TRIGGER IF EXISTS trg_aud_producto_insert;
CREATE TRIGGER trg_aud_producto_insert AFTER INSERT ON producto FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'INSERT', 'producto', NEW.`id_producto`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'INSERT', NULL, JSON_OBJECT('id_producto', NEW.`id_producto`, 'codigo_barra', NEW.`codigo_barra`, 'nombre', NEW.`nombre`, 'marca', NEW.`marca`, 'id_categoria', NEW.`id_categoria`, 'precio', NEW.`precio`, 'stock', NEW.`stock`, 'activo', NEW.`activo`), IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL;
DROP TRIGGER IF EXISTS trg_aud_producto_update;
CREATE TRIGGER trg_aud_producto_update AFTER UPDATE ON producto FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'UPDATE', 'producto', NEW.`id_producto`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'UPDATE', JSON_OBJECT('id_producto', OLD.`id_producto`, 'codigo_barra', OLD.`codigo_barra`, 'nombre', OLD.`nombre`, 'marca', OLD.`marca`, 'id_categoria', OLD.`id_categoria`, 'precio', OLD.`precio`, 'stock', OLD.`stock`, 'activo', OLD.`activo`), JSON_OBJECT('id_producto', NEW.`id_producto`, 'codigo_barra', NEW.`codigo_barra`, 'nombre', NEW.`nombre`, 'marca', NEW.`marca`, 'id_categoria', NEW.`id_categoria`, 'precio', NEW.`precio`, 'stock', NEW.`stock`, 'activo', NEW.`activo`), IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL WHERE NOT (JSON_OBJECT('id_producto', OLD.`id_producto`, 'codigo_barra', OLD.`codigo_barra`, 'nombre', OLD.`nombre`, 'marca', OLD.`marca`, 'id_categoria', OLD.`id_categoria`, 'precio', OLD.`precio`, 'stock', OLD.`stock`, 'activo', OLD.`activo`) <=> JSON_OBJECT('id_producto', NEW.`id_producto`, 'codigo_barra', NEW.`codigo_barra`, 'nombre', NEW.`nombre`, 'marca', NEW.`marca`, 'id_categoria', NEW.`id_categoria`, 'precio', NEW.`precio`, 'stock', NEW.`stock`, 'activo', NEW.`activo`));
DROP TRIGGER IF EXISTS trg_aud_producto_delete;
CREATE TRIGGER trg_aud_producto_delete AFTER DELETE ON producto FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'DELETE', 'producto', OLD.`id_producto`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'DELETE', JSON_OBJECT('id_producto', OLD.`id_producto`, 'codigo_barra', OLD.`codigo_barra`, 'nombre', OLD.`nombre`, 'marca', OLD.`marca`, 'id_categoria', OLD.`id_categoria`, 'precio', OLD.`precio`, 'stock', OLD.`stock`, 'activo', OLD.`activo`), NULL, IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL;
DROP TRIGGER IF EXISTS trg_aud_reserva_insert;
CREATE TRIGGER trg_aud_reserva_insert AFTER INSERT ON reserva FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'INSERT', 'reserva', NEW.`id_reserva`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'INSERT', NULL, JSON_OBJECT('id_reserva', NEW.`id_reserva`, 'id_huesped', NEW.`id_huesped`, 'id_habitacion', NEW.`id_habitacion`, 'id_usuario', NEW.`id_usuario`, 'fecha_reserva', NEW.`fecha_reserva`, 'fecha_checkin', NEW.`fecha_checkin`, 'fecha_checkout', NEW.`fecha_checkout`, 'num_huespedes', NEW.`num_huespedes`, 'hora_checkin', NEW.`hora_checkin`, 'monto_total', NEW.`monto_total`, 'estado', NEW.`estado`, 'canal', NEW.`canal`, 'id_empresa', NEW.`id_empresa`, 'motivo_cancelacion', NEW.`motivo_cancelacion`, 'detalle_cancelacion', NEW.`detalle_cancelacion`, 'fecha_evento', NEW.`fecha_evento`, 'motivo_registro_tardio', NEW.`motivo_registro_tardio`, 'id_usuario_regulariza', NEW.`id_usuario_regulariza`), IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL;
DROP TRIGGER IF EXISTS trg_aud_reserva_update;
CREATE TRIGGER trg_aud_reserva_update AFTER UPDATE ON reserva FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'UPDATE', 'reserva', NEW.`id_reserva`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'UPDATE', JSON_OBJECT('id_reserva', OLD.`id_reserva`, 'id_huesped', OLD.`id_huesped`, 'id_habitacion', OLD.`id_habitacion`, 'id_usuario', OLD.`id_usuario`, 'fecha_reserva', OLD.`fecha_reserva`, 'fecha_checkin', OLD.`fecha_checkin`, 'fecha_checkout', OLD.`fecha_checkout`, 'num_huespedes', OLD.`num_huespedes`, 'hora_checkin', OLD.`hora_checkin`, 'monto_total', OLD.`monto_total`, 'estado', OLD.`estado`, 'canal', OLD.`canal`, 'id_empresa', OLD.`id_empresa`, 'motivo_cancelacion', OLD.`motivo_cancelacion`, 'detalle_cancelacion', OLD.`detalle_cancelacion`, 'fecha_evento', OLD.`fecha_evento`, 'motivo_registro_tardio', OLD.`motivo_registro_tardio`, 'id_usuario_regulariza', OLD.`id_usuario_regulariza`), JSON_OBJECT('id_reserva', NEW.`id_reserva`, 'id_huesped', NEW.`id_huesped`, 'id_habitacion', NEW.`id_habitacion`, 'id_usuario', NEW.`id_usuario`, 'fecha_reserva', NEW.`fecha_reserva`, 'fecha_checkin', NEW.`fecha_checkin`, 'fecha_checkout', NEW.`fecha_checkout`, 'num_huespedes', NEW.`num_huespedes`, 'hora_checkin', NEW.`hora_checkin`, 'monto_total', NEW.`monto_total`, 'estado', NEW.`estado`, 'canal', NEW.`canal`, 'id_empresa', NEW.`id_empresa`, 'motivo_cancelacion', NEW.`motivo_cancelacion`, 'detalle_cancelacion', NEW.`detalle_cancelacion`, 'fecha_evento', NEW.`fecha_evento`, 'motivo_registro_tardio', NEW.`motivo_registro_tardio`, 'id_usuario_regulariza', NEW.`id_usuario_regulariza`), IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL WHERE NOT (JSON_OBJECT('id_reserva', OLD.`id_reserva`, 'id_huesped', OLD.`id_huesped`, 'id_habitacion', OLD.`id_habitacion`, 'id_usuario', OLD.`id_usuario`, 'fecha_reserva', OLD.`fecha_reserva`, 'fecha_checkin', OLD.`fecha_checkin`, 'fecha_checkout', OLD.`fecha_checkout`, 'num_huespedes', OLD.`num_huespedes`, 'hora_checkin', OLD.`hora_checkin`, 'monto_total', OLD.`monto_total`, 'estado', OLD.`estado`, 'canal', OLD.`canal`, 'id_empresa', OLD.`id_empresa`, 'motivo_cancelacion', OLD.`motivo_cancelacion`, 'detalle_cancelacion', OLD.`detalle_cancelacion`, 'fecha_evento', OLD.`fecha_evento`, 'motivo_registro_tardio', OLD.`motivo_registro_tardio`, 'id_usuario_regulariza', OLD.`id_usuario_regulariza`) <=> JSON_OBJECT('id_reserva', NEW.`id_reserva`, 'id_huesped', NEW.`id_huesped`, 'id_habitacion', NEW.`id_habitacion`, 'id_usuario', NEW.`id_usuario`, 'fecha_reserva', NEW.`fecha_reserva`, 'fecha_checkin', NEW.`fecha_checkin`, 'fecha_checkout', NEW.`fecha_checkout`, 'num_huespedes', NEW.`num_huespedes`, 'hora_checkin', NEW.`hora_checkin`, 'monto_total', NEW.`monto_total`, 'estado', NEW.`estado`, 'canal', NEW.`canal`, 'id_empresa', NEW.`id_empresa`, 'motivo_cancelacion', NEW.`motivo_cancelacion`, 'detalle_cancelacion', NEW.`detalle_cancelacion`, 'fecha_evento', NEW.`fecha_evento`, 'motivo_registro_tardio', NEW.`motivo_registro_tardio`, 'id_usuario_regulariza', NEW.`id_usuario_regulariza`));
DROP TRIGGER IF EXISTS trg_aud_reserva_delete;
CREATE TRIGGER trg_aud_reserva_delete AFTER DELETE ON reserva FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'DELETE', 'reserva', OLD.`id_reserva`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'DELETE', JSON_OBJECT('id_reserva', OLD.`id_reserva`, 'id_huesped', OLD.`id_huesped`, 'id_habitacion', OLD.`id_habitacion`, 'id_usuario', OLD.`id_usuario`, 'fecha_reserva', OLD.`fecha_reserva`, 'fecha_checkin', OLD.`fecha_checkin`, 'fecha_checkout', OLD.`fecha_checkout`, 'num_huespedes', OLD.`num_huespedes`, 'hora_checkin', OLD.`hora_checkin`, 'monto_total', OLD.`monto_total`, 'estado', OLD.`estado`, 'canal', OLD.`canal`, 'id_empresa', OLD.`id_empresa`, 'motivo_cancelacion', OLD.`motivo_cancelacion`, 'detalle_cancelacion', OLD.`detalle_cancelacion`, 'fecha_evento', OLD.`fecha_evento`, 'motivo_registro_tardio', OLD.`motivo_registro_tardio`, 'id_usuario_regulariza', OLD.`id_usuario_regulariza`), NULL, IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL;
DROP TRIGGER IF EXISTS trg_aud_tipo_habitacion_insert;
CREATE TRIGGER trg_aud_tipo_habitacion_insert AFTER INSERT ON tipo_habitacion FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'INSERT', 'tipo_habitacion', NEW.`id_tipo`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'INSERT', NULL, JSON_OBJECT('id_tipo', NEW.`id_tipo`, 'nombre', NEW.`nombre`, 'capacidad', NEW.`capacidad`, 'precio_base', NEW.`precio_base`, 'nivel_categoria', NEW.`nivel_categoria`), IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL;
DROP TRIGGER IF EXISTS trg_aud_tipo_habitacion_update;
CREATE TRIGGER trg_aud_tipo_habitacion_update AFTER UPDATE ON tipo_habitacion FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'UPDATE', 'tipo_habitacion', NEW.`id_tipo`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'UPDATE', JSON_OBJECT('id_tipo', OLD.`id_tipo`, 'nombre', OLD.`nombre`, 'capacidad', OLD.`capacidad`, 'precio_base', OLD.`precio_base`, 'nivel_categoria', OLD.`nivel_categoria`), JSON_OBJECT('id_tipo', NEW.`id_tipo`, 'nombre', NEW.`nombre`, 'capacidad', NEW.`capacidad`, 'precio_base', NEW.`precio_base`, 'nivel_categoria', NEW.`nivel_categoria`), IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL WHERE NOT (JSON_OBJECT('id_tipo', OLD.`id_tipo`, 'nombre', OLD.`nombre`, 'capacidad', OLD.`capacidad`, 'precio_base', OLD.`precio_base`, 'nivel_categoria', OLD.`nivel_categoria`) <=> JSON_OBJECT('id_tipo', NEW.`id_tipo`, 'nombre', NEW.`nombre`, 'capacidad', NEW.`capacidad`, 'precio_base', NEW.`precio_base`, 'nivel_categoria', NEW.`nivel_categoria`));
DROP TRIGGER IF EXISTS trg_aud_tipo_habitacion_delete;
CREATE TRIGGER trg_aud_tipo_habitacion_delete AFTER DELETE ON tipo_habitacion FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'DELETE', 'tipo_habitacion', OLD.`id_tipo`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'DELETE', JSON_OBJECT('id_tipo', OLD.`id_tipo`, 'nombre', OLD.`nombre`, 'capacidad', OLD.`capacidad`, 'precio_base', OLD.`precio_base`, 'nivel_categoria', OLD.`nivel_categoria`), NULL, IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL;
DROP TRIGGER IF EXISTS trg_aud_usuario_insert;
CREATE TRIGGER trg_aud_usuario_insert AFTER INSERT ON usuario FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'INSERT', 'usuario', NEW.`id_usuario`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'INSERT', NULL, JSON_OBJECT('id_usuario', NEW.`id_usuario`, 'nombre', NEW.`nombre`, 'apellido', NEW.`apellido`, 'usuario', NEW.`usuario`, 'rol', NEW.`rol`, 'activo', NEW.`activo`, 'fecha_creacion', NEW.`fecha_creacion`, 'intentos_fallidos', NEW.`intentos_fallidos`, 'bloqueado_hasta', NEW.`bloqueado_hasta`, 'intentos_recuperacion', NEW.`intentos_recuperacion`), IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL;
DROP TRIGGER IF EXISTS trg_aud_usuario_update;
CREATE TRIGGER trg_aud_usuario_update AFTER UPDATE ON usuario FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'UPDATE', 'usuario', NEW.`id_usuario`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'UPDATE', JSON_OBJECT('id_usuario', OLD.`id_usuario`, 'nombre', OLD.`nombre`, 'apellido', OLD.`apellido`, 'usuario', OLD.`usuario`, 'rol', OLD.`rol`, 'activo', OLD.`activo`, 'fecha_creacion', OLD.`fecha_creacion`, 'intentos_fallidos', OLD.`intentos_fallidos`, 'bloqueado_hasta', OLD.`bloqueado_hasta`, 'intentos_recuperacion', OLD.`intentos_recuperacion`), JSON_OBJECT('id_usuario', NEW.`id_usuario`, 'nombre', NEW.`nombre`, 'apellido', NEW.`apellido`, 'usuario', NEW.`usuario`, 'rol', NEW.`rol`, 'activo', NEW.`activo`, 'fecha_creacion', NEW.`fecha_creacion`, 'intentos_fallidos', NEW.`intentos_fallidos`, 'bloqueado_hasta', NEW.`bloqueado_hasta`, 'intentos_recuperacion', NEW.`intentos_recuperacion`), IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL WHERE NOT (JSON_OBJECT('id_usuario', OLD.`id_usuario`, 'nombre', OLD.`nombre`, 'apellido', OLD.`apellido`, 'usuario', OLD.`usuario`, 'rol', OLD.`rol`, 'activo', OLD.`activo`, 'fecha_creacion', OLD.`fecha_creacion`, 'intentos_fallidos', OLD.`intentos_fallidos`, 'bloqueado_hasta', OLD.`bloqueado_hasta`, 'intentos_recuperacion', OLD.`intentos_recuperacion`) <=> JSON_OBJECT('id_usuario', NEW.`id_usuario`, 'nombre', NEW.`nombre`, 'apellido', NEW.`apellido`, 'usuario', NEW.`usuario`, 'rol', NEW.`rol`, 'activo', NEW.`activo`, 'fecha_creacion', NEW.`fecha_creacion`, 'intentos_fallidos', NEW.`intentos_fallidos`, 'bloqueado_hasta', NEW.`bloqueado_hasta`, 'intentos_recuperacion', NEW.`intentos_recuperacion`));
DROP TRIGGER IF EXISTS trg_aud_usuario_delete;
CREATE TRIGGER trg_aud_usuario_delete AFTER DELETE ON usuario FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'DELETE', 'usuario', OLD.`id_usuario`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'DELETE', JSON_OBJECT('id_usuario', OLD.`id_usuario`, 'nombre', OLD.`nombre`, 'apellido', OLD.`apellido`, 'usuario', OLD.`usuario`, 'rol', OLD.`rol`, 'activo', OLD.`activo`, 'fecha_creacion', OLD.`fecha_creacion`, 'intentos_fallidos', OLD.`intentos_fallidos`, 'bloqueado_hasta', OLD.`bloqueado_hasta`, 'intentos_recuperacion', OLD.`intentos_recuperacion`), NULL, IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL;
DROP TRIGGER IF EXISTS trg_aud_venta_tienda_insert;
CREATE TRIGGER trg_aud_venta_tienda_insert AFTER INSERT ON venta_tienda FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'INSERT', 'venta_tienda', NEW.`id_venta`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'INSERT', NULL, JSON_OBJECT('id_venta', NEW.`id_venta`, 'id_huesped', NEW.`id_huesped`, 'id_habitacion', NEW.`id_habitacion`, 'id_usuario', NEW.`id_usuario`, 'id_comprobante', NEW.`id_comprobante`, 'cliente_externo', NEW.`cliente_externo`, 'metodo_pago', NEW.`metodo_pago`, 'fecha_venta', NEW.`fecha_venta`, 'total', NEW.`total`, 'fecha_evento', NEW.`fecha_evento`, 'motivo_registro_tardio', NEW.`motivo_registro_tardio`, 'id_usuario_regulariza', NEW.`id_usuario_regulariza`, 'estado', NEW.`estado`, 'fecha_anulacion', NEW.`fecha_anulacion`, 'id_usuario_anula', NEW.`id_usuario_anula`, 'motivo_anulacion', NEW.`motivo_anulacion`), IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL;
DROP TRIGGER IF EXISTS trg_aud_venta_tienda_update;
CREATE TRIGGER trg_aud_venta_tienda_update AFTER UPDATE ON venta_tienda FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'UPDATE', 'venta_tienda', NEW.`id_venta`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'UPDATE', JSON_OBJECT('id_venta', OLD.`id_venta`, 'id_huesped', OLD.`id_huesped`, 'id_habitacion', OLD.`id_habitacion`, 'id_usuario', OLD.`id_usuario`, 'id_comprobante', OLD.`id_comprobante`, 'cliente_externo', OLD.`cliente_externo`, 'metodo_pago', OLD.`metodo_pago`, 'fecha_venta', OLD.`fecha_venta`, 'total', OLD.`total`, 'fecha_evento', OLD.`fecha_evento`, 'motivo_registro_tardio', OLD.`motivo_registro_tardio`, 'id_usuario_regulariza', OLD.`id_usuario_regulariza`, 'estado', OLD.`estado`, 'fecha_anulacion', OLD.`fecha_anulacion`, 'id_usuario_anula', OLD.`id_usuario_anula`, 'motivo_anulacion', OLD.`motivo_anulacion`), JSON_OBJECT('id_venta', NEW.`id_venta`, 'id_huesped', NEW.`id_huesped`, 'id_habitacion', NEW.`id_habitacion`, 'id_usuario', NEW.`id_usuario`, 'id_comprobante', NEW.`id_comprobante`, 'cliente_externo', NEW.`cliente_externo`, 'metodo_pago', NEW.`metodo_pago`, 'fecha_venta', NEW.`fecha_venta`, 'total', NEW.`total`, 'fecha_evento', NEW.`fecha_evento`, 'motivo_registro_tardio', NEW.`motivo_registro_tardio`, 'id_usuario_regulariza', NEW.`id_usuario_regulariza`, 'estado', NEW.`estado`, 'fecha_anulacion', NEW.`fecha_anulacion`, 'id_usuario_anula', NEW.`id_usuario_anula`, 'motivo_anulacion', NEW.`motivo_anulacion`), IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL WHERE NOT (JSON_OBJECT('id_venta', OLD.`id_venta`, 'id_huesped', OLD.`id_huesped`, 'id_habitacion', OLD.`id_habitacion`, 'id_usuario', OLD.`id_usuario`, 'id_comprobante', OLD.`id_comprobante`, 'cliente_externo', OLD.`cliente_externo`, 'metodo_pago', OLD.`metodo_pago`, 'fecha_venta', OLD.`fecha_venta`, 'total', OLD.`total`, 'fecha_evento', OLD.`fecha_evento`, 'motivo_registro_tardio', OLD.`motivo_registro_tardio`, 'id_usuario_regulariza', OLD.`id_usuario_regulariza`, 'estado', OLD.`estado`, 'fecha_anulacion', OLD.`fecha_anulacion`, 'id_usuario_anula', OLD.`id_usuario_anula`, 'motivo_anulacion', OLD.`motivo_anulacion`) <=> JSON_OBJECT('id_venta', NEW.`id_venta`, 'id_huesped', NEW.`id_huesped`, 'id_habitacion', NEW.`id_habitacion`, 'id_usuario', NEW.`id_usuario`, 'id_comprobante', NEW.`id_comprobante`, 'cliente_externo', NEW.`cliente_externo`, 'metodo_pago', NEW.`metodo_pago`, 'fecha_venta', NEW.`fecha_venta`, 'total', NEW.`total`, 'fecha_evento', NEW.`fecha_evento`, 'motivo_registro_tardio', NEW.`motivo_registro_tardio`, 'id_usuario_regulariza', NEW.`id_usuario_regulariza`, 'estado', NEW.`estado`, 'fecha_anulacion', NEW.`fecha_anulacion`, 'id_usuario_anula', NEW.`id_usuario_anula`, 'motivo_anulacion', NEW.`motivo_anulacion`));
DROP TRIGGER IF EXISTS trg_aud_venta_tienda_delete;
CREATE TRIGGER trg_aud_venta_tienda_delete AFTER DELETE ON venta_tienda FOR EACH ROW
    INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, tipo_operacion, datos_anteriores, datos_nuevos, origen)
    SELECT NULLIF(@auditoria_usuario_id, 0), 'DELETE', 'venta_tienda', OLD.`id_venta`,
           IF(@auditoria_usuario_id IS NULL, CONCAT('Cambio directo en la base de datos por ', USER()), 'Cambio hecho desde el sistema'),
           'DELETE', JSON_OBJECT('id_venta', OLD.`id_venta`, 'id_huesped', OLD.`id_huesped`, 'id_habitacion', OLD.`id_habitacion`, 'id_usuario', OLD.`id_usuario`, 'id_comprobante', OLD.`id_comprobante`, 'cliente_externo', OLD.`cliente_externo`, 'metodo_pago', OLD.`metodo_pago`, 'fecha_venta', OLD.`fecha_venta`, 'total', OLD.`total`, 'fecha_evento', OLD.`fecha_evento`, 'motivo_registro_tardio', OLD.`motivo_registro_tardio`, 'id_usuario_regulariza', OLD.`id_usuario_regulariza`, 'estado', OLD.`estado`, 'fecha_anulacion', OLD.`fecha_anulacion`, 'id_usuario_anula', OLD.`id_usuario_anula`, 'motivo_anulacion', OLD.`motivo_anulacion`), NULL, IF(@auditoria_usuario_id IS NULL, 'BASE_DE_DATOS', 'SISTEMA')
    FROM DUAL;
