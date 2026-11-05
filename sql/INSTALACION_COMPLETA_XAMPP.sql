-- HOTEL SAN ANTONIO: SCRIPT UNICO DE LA BASE DE DATOS PARA XAMPP/MARIADB (es el unico .sql del proyecto)
--
-- BASE NUEVA (sin tablas): ejecute el archivo completo.
-- BASE QUE YA EXISTE: lo mas simple es borrarla (phpMyAdmin > hotel_san_antonio > Operaciones > Eliminar la base de
--   datos) y ejecutar de nuevo el archivo completo. OJO: se pierden sus datos; haga antes un respaldo si le importan.
--   Si tiene datos que NO quiere perder, no la borre: ejecute SOLO el ultimo bloque del archivo, el de las
--   actualizaciones (esta al final, bajo un titulo con lineas de =====). Se puede ejecutar mas de una vez.
-- Todo cambio nuevo de la base se agrega al final de ese bloque, con ADD COLUMN IF NOT EXISTS y similares.
--
-- Una base nueva crea 23 tablas, tipos, 24 habitaciones, catalogo de tienda, dos cuentas y el usuario de MySQL
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
    ('Recepcionista', 'Hotel', 'recepcion', '$2a$12$6z4YEdVPJwck30EsbE5BmO0waS860fXLzK9j2QMEO5ic1WsIxhGBy', 'RECEPCIONISTA', 1),
    ('Personal', 'Limpieza', 'limpieza', '$2a$12$6z4YEdVPJwck30EsbE5BmO0waS860fXLzK9j2QMEO5ic1WsIxhGBy', 'LIMPIEZA', 1);
COMMIT;

-- DATOS DE PRUEBA DEL EQUIPO (los mismos en todas las computadoras): un huesped con una reserva ya finalizada y
-- pagada, y su habitacion 102 en limpieza. Se cargan aqui, antes de los triggers, para que no llenen la auditoria.
START TRANSACTION;
INSERT INTO huesped (tipo_documento, num_documento, nombres, apellidos, pais_procedencia, fecha_registro)
VALUES ('DNI', '60217536', 'JORBIN BRAYAN', 'VENTURA VALER', _utf8mb4 0x506572C3BA, '2026-10-03 12:42:17');
INSERT INTO reserva (id_huesped, id_habitacion, id_usuario, fecha_reserva, fecha_checkin, fecha_checkout,
                     num_huespedes, monto_total, estado, canal)
VALUES (1, 2, 2, '2026-10-03 12:42:17', '2026-10-03', '2026-10-04', 1, 100.00, 'FINALIZADA', 'PRESENCIAL');
INSERT INTO reserva_huesped (id_reserva, id_huesped, principal, fecha_asociacion)
VALUES (1, 1, 1, '2026-10-03 12:42:17');
INSERT INTO pago (id_reserva, id_usuario, monto, metodo_pago, tipo_pago, fecha_pago)
VALUES (1, 2, 100.00, 'EFECTIVO', 'COMPLETO', '2026-10-03 12:42:17');
UPDATE habitacion SET estado = 'LIMPIEZA' WHERE id_habitacion = 2;
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
--    registro tecnico: movimiento_stock, cambio_habitacion, actividad_dia, corte_sistema,
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

-- ---------------------------------------------------------------------
-- 7b) DATOS DE PRUEBA EN VOLUMEN (enero a noviembre de 2026)
--     Se generan con formulas fijas (sin numeros al azar), asi que todas las computadoras quedan con los mismos datos.
--     Los huespedes tienen DNI que empiezan con 9 (no son personas reales). Va antes de los triggers de auditoria
--     (seccion 9) para que la carga no llene la tabla auditoria. Si la base ya tiene reservas, no se repite.
-- ---------------------------------------------------------------------
SET @cargar = (SELECT IF(COUNT(*) < 100, 1, 0) FROM reserva);
SET @h0 = (SELECT COALESCE(MAX(id_huesped), 0) FROM huesped);
SET @r0 = (SELECT COALESCE(MAX(id_reserva), 0) FROM reserva);
SET @e0 = (SELECT COALESCE(MAX(id_empresa), 0) FROM empresa);
SET @hoy = DATE('2026-11-04');
SET @repo = CONCAT('Reposici', CHAR(0xC3B3 USING utf8mb4), 'n de tienda');
SET @peru = CONCAT('Per', CHAR(0xC3BA USING utf8mb4));
SET @desistio = CONCAT('El cliente desisti', CHAR(0xC3B3 USING utf8mb4));

-- listas de apoyo (se borran al final)
DROP TABLE IF EXISTS tmp_n;
CREATE TABLE tmp_n (grp CHAR(2) NOT NULL, i INT NOT NULL, v VARCHAR(60) NULL, PRIMARY KEY (grp, i));
INSERT INTO tmp_n SELECT 'N', seq, ELT(seq, 'JUAN','JOSE','LUIS','CARLOS','MIGUEL','JORGE','PEDRO','MANUEL','VICTOR','ALBERTO','ANDRES','DIEGO','FERNANDO','GUSTAVO','HUGO','IVAN','JAVIER','KEVIN','MARCOS','NESTOR','OSCAR','PABLO','RAUL','SERGIO','TEODORO','WILMER','ELMER','EDGAR','CESAR','DANIEL','ROBERTO','WALTER','MARIA','ROSA','ANA','CARMEN','LUZ','JULIA','SOFIA','ELENA','PATRICIA','GLADYS','FLOR','MILAGROS','KAREN','LUCIA','DIANA','YESENIA') FROM seq_1_to_60;
INSERT INTO tmp_n SELECT 'S', seq, ELT(seq, 'ANTONIO','ALEXANDER','ANDREA','BRAYAN','CRISTIAN','DAVID','EDUARDO','ENRIQUE','FRANCISCO','GABRIEL','GIOVANNI','HENRY','JESUS','JHON','JOEL','LEONARDO','MARTIN','NICOLAS','OMAR','PAOLA','ROSARIO','SANDRA','TERESA','VANESSA','YOLANDA','MERCEDES','ISABEL','LILIANA','MONICA','NORMA') FROM seq_1_to_60;
INSERT INTO tmp_n SELECT 'A', seq, ELT(seq, 'QUISPE','HUAMAN','FLORES','RODRIGUEZ','GARCIA','MENDOZA','CHAVEZ','ROJAS','SANCHEZ','VASQUEZ','DIAZ','CASTILLO','TORRES','RAMIREZ','GOMEZ','CRUZ','PEREZ','RAMOS','VARGAS','SILVA','CARRASCO','CONDORI','MAMANI','ALVAREZ','FERNANDEZ','GUTIERREZ','MORALES','HERNANDEZ','MEDINA','ESPINOZA','PAREDES','ORTIZ','CASTRO','REYES','SALAZAR','VELASQUEZ','DELGADO','VEGA','TELLO','MONTENEGRO','CABRERA','CORDOVA','LOPEZ','GONZALES','SOTO','NUNEZ','ARAUJO','BECERRA','CHAMBI','CUBAS','HUAMANI','IDROGO','JIMENEZ','LLAMOCCA','MEZA','NAVARRO','OCAMPO','PAJARES','SANTOS','TAPIA') FROM seq_1_to_70;
INSERT INTO tmp_n SELECT 'R', seq, ELT(seq, 'CONSTRUCTORA','TRANSPORTES','COMERCIAL','INVERSIONES','SERVICIOS GENERALES','AGROINDUSTRIAS','DISTRIBUIDORA','MINERA','INGENIERIA','CONSULTORES','FERRETERIA','EXPORTADORA') FROM seq_1_to_20;
INSERT INTO tmp_n SELECT 'P', seq, ELT(seq, 'AMAZONAS','BAGUA','LOS ANDES','EL MARANON','UTCUBAMBA','ORIENTE','NORTE','CONDORCANQUI','SELVA ALTA','HUAYNA','SAN MARTIN','CHICLAYO','INCA','PACIFICO','CENEPA') FROM seq_1_to_20;
INSERT INTO tmp_n SELECT 'C', seq, ELT(seq, 'BOLIVAR','GRAU','SAN MARTIN','AMAZONAS','LIMA','COMERCIO','PROGRESO','JUNIN','CUZCO','AYACUCHO') FROM seq_1_to_20;
INSERT INTO tmp_n SELECT 'K', seq, ELT(seq, 'Ecuador','Colombia','Chile','Argentina','Brasil','Venezuela','Bolivia','Estados Unidos') FROM seq_1_to_20;
DELETE FROM tmp_n WHERE v IS NULL;
SET @nN = (SELECT COUNT(*) FROM tmp_n WHERE grp = 'N');
SET @nS = (SELECT COUNT(*) FROM tmp_n WHERE grp = 'S');
SET @nA = (SELECT COUNT(*) FROM tmp_n WHERE grp = 'A');
SET @nR = (SELECT COUNT(*) FROM tmp_n WHERE grp = 'R');
SET @nP = (SELECT COUNT(*) FROM tmp_n WHERE grp = 'P');
SET @nC = (SELECT COUNT(*) FROM tmp_n WHERE grp = 'C');
SET @nK = (SELECT COUNT(*) FROM tmp_n WHERE grp = 'K');

-- categorias y productos (los codigos de barras son unicos: si ya estan, no se repiten)
INSERT INTO categoria (nombre) SELECT 'Licores' FROM DUAL WHERE @cargar = 1 AND NOT EXISTS (SELECT 1 FROM categoria WHERE nombre = 'Licores');
INSERT INTO categoria (nombre) SELECT 'Abarrotes' FROM DUAL WHERE @cargar = 1 AND NOT EXISTS (SELECT 1 FROM categoria WHERE nombre = 'Abarrotes');
INSERT IGNORE INTO producto (codigo_barra, nombre, marca, id_categoria, precio, stock)
SELECT p.cod, p.nom, p.marca, c.id_categoria, p.precio, 0
FROM (
 SELECT '7750243000011' cod, 'Coca Cola 500ml' nom, 'Coca Cola' marca, 'Bebidas' cat, 3.50 precio UNION ALL
 SELECT '7750243000028', 'Coca Cola 1.5L', 'Coca Cola', 'Bebidas', 7.00 UNION ALL
 SELECT '7750243000035', 'Inca Kola 1.5L', 'Inca Kola', 'Bebidas', 7.00 UNION ALL
 SELECT '7750243000042', 'Sprite 500ml', 'Coca Cola', 'Bebidas', 3.50 UNION ALL
 SELECT '7750243000059', 'Fanta Naranja 500ml', 'Coca Cola', 'Bebidas', 3.50 UNION ALL
 SELECT '7750243000066', 'Agua con gas 625ml', 'San Luis', 'Bebidas', 2.50 UNION ALL
 SELECT '7750243000073', 'Agua sin gas 2.5L', 'San Luis', 'Bebidas', 5.00 UNION ALL
 SELECT '7750243000080', 'Gatorade Naranja 500ml', 'Gatorade', 'Bebidas', 5.00 UNION ALL
 SELECT '7750243000097', 'Red Bull 250ml', 'Red Bull', 'Bebidas', 9.00 UNION ALL
 SELECT '7750243000103', 'Jugo Frugos Durazno 1L', 'Gloria', 'Bebidas', 4.50 UNION ALL
 SELECT '7750243000110', 'Cafe instantaneo Nescafe', 'Nestle', 'Bebidas', 1.50 UNION ALL
 SELECT '7750243000127', 'Cerveza Pilsen 620ml', 'Backus', 'Licores', 8.00 UNION ALL
 SELECT '7750243000134', 'Cerveza Cristal 620ml', 'Backus', 'Licores', 8.00 UNION ALL
 SELECT '7750243000141', 'Cerveza Cusquena 620ml', 'Backus', 'Licores', 9.00 UNION ALL
 SELECT '7750243000158', 'Vino Tacama Tinto 750ml', 'Tacama', 'Licores', 35.00 UNION ALL
 SELECT '7750243000165', 'Pisco Quebranta 500ml', 'Tabernero', 'Licores', 28.00 UNION ALL
 SELECT '7750243000172', 'Ron Cartavio 750ml', 'Cartavio', 'Licores', 24.00 UNION ALL
 SELECT '7750243000189', 'Papas Lays Clasicas', 'Lays', 'Snacks', 2.50 UNION ALL
 SELECT '7750243000196', 'Doritos Queso', 'Doritos', 'Snacks', 3.00 UNION ALL
 SELECT '7750243000202', 'Cheetos', 'Cheetos', 'Snacks', 2.50 UNION ALL
 SELECT '7750243000219', 'Piqueo Snax', 'Snax', 'Snacks', 2.00 UNION ALL
 SELECT '7750243000226', 'Canchita salada', 'Karinto', 'Snacks', 2.50 UNION ALL
 SELECT '7750243000233', 'Galleta Casino Fresa', 'Field', 'Snacks', 1.50 UNION ALL
 SELECT '7750243000240', 'Galleta Oreo', 'Oreo', 'Snacks', 2.00 UNION ALL
 SELECT '7750243000257', 'Galleta Ritz', 'Ritz', 'Snacks', 2.00 UNION ALL
 SELECT '7750243000264', 'Chocolate Donofrio', 'Donofrio', 'Golosinas', 2.00 UNION ALL
 SELECT '7750243000271', 'Chocolate Princesa', 'Nestle', 'Golosinas', 1.50 UNION ALL
 SELECT '7750243000288', 'Caramelos Halls', 'Halls', 'Golosinas', 1.00 UNION ALL
 SELECT '7750243000295', 'Chicle Trident', 'Trident', 'Golosinas', 1.50 UNION ALL
 SELECT '7750243000301', 'Barra Snickers', 'Snickers', 'Golosinas', 3.50 UNION ALL
 SELECT '7750243000318', 'Mani confitado', 'Karinto', 'Golosinas', 2.00 UNION ALL
 SELECT '7750243000325', 'Jabon de tocador Protex', 'Protex', 'Higiene personal', 3.50 UNION ALL
 SELECT '7750243000332', 'Shampoo sachet Sedal', 'Sedal', 'Higiene personal', 1.00 UNION ALL
 SELECT '7750243000349', 'Pasta dental Colgate 75ml', 'Colgate', 'Higiene personal', 6.50 UNION ALL
 SELECT '7750243000356', 'Cepillo dental Colgate', 'Colgate', 'Higiene personal', 4.50 UNION ALL
 SELECT '7750243000363', 'Desodorante Rexona', 'Rexona', 'Higiene personal', 12.00 UNION ALL
 SELECT '7750243000370', 'Maquina de afeitar Gillette', 'Gillette', 'Higiene personal', 5.00 UNION ALL
 SELECT '7750243000387', 'Papel higienico Suave x2', 'Suave', 'Higiene personal', 3.00 UNION ALL
 SELECT '7750243000394', 'Toallitas humedas', 'Huggies', 'Higiene personal', 8.50 UNION ALL
 SELECT '7750243000400', 'Pilas AA x2', 'Duracell', 'Otros', 9.00 UNION ALL
 SELECT '7750243000417', 'Encendedor', 'Bic', 'Otros', 2.50 UNION ALL
 SELECT '7750243000424', 'Paraguas pequeno', 'Genérico', 'Otros', 18.00 UNION ALL
 SELECT '7750243000431', 'Arroz Costeno 1kg', 'Costeno', 'Abarrotes', 5.50 UNION ALL
 SELECT '7750243000448', 'Fideos Don Vittorio 500g', 'Don Vittorio', 'Abarrotes', 3.50 UNION ALL
 SELECT '7750243000455', 'Atun Florida lata', 'Florida', 'Abarrotes', 6.50 UNION ALL
 SELECT '7750243000462', 'Leche Gloria lata', 'Gloria', 'Abarrotes', 4.00 UNION ALL
 SELECT '7750243000479', 'Pan de molde Bimbo', 'Bimbo', 'Abarrotes', 8.00
) p JOIN categoria c ON c.nombre = p.cat
WHERE @cargar = 1;
SET @nprod = (SELECT COUNT(*) FROM producto);

-- empresas con RUC valido (digito verificador calculado)
INSERT IGNORE INTO empresa (ruc, razon_social, direccion_fiscal)
SELECT CONCAT(b.r10, MOD(11 - MOD(b.s, 11), 10)),
       CONCAT(rr.v, ' ', pp.v, ELT(1 + MOD(b.n, 3), ' S.A.C.', ' E.I.R.L.', ' S.R.L.')),
       CONCAT(ELT(1 + MOD(b.n, 4), 'JR. ', 'AV. ', 'CALLE ', 'PSJE. '), cc.v, ' NRO. ', 100 + MOD(b.n * 17, 900), ', BAGUA')
FROM (
  SELECT q.n, q.r10,
         SUBSTRING(q.r10,1,1)*5 + SUBSTRING(q.r10,2,1)*4 + SUBSTRING(q.r10,3,1)*3 + SUBSTRING(q.r10,4,1)*2 + SUBSTRING(q.r10,5,1)*7
       + SUBSTRING(q.r10,6,1)*6 + SUBSTRING(q.r10,7,1)*5 + SUBSTRING(q.r10,8,1)*4 + SUBSTRING(q.r10,9,1)*3 + SUBSTRING(q.r10,10,1)*2 AS s
  FROM (SELECT seq AS n, CONCAT('20', LPAD(10000000 + seq * 7331, 8, '0')) AS r10 FROM seq_1_to_60) q
) b
JOIN tmp_n rr ON rr.grp = 'R' AND rr.i = 1 + MOD(b.n, @nR)
JOIN tmp_n pp ON pp.grp = 'P' AND pp.i = 1 + MOD(b.n * 5, @nP)
JOIN tmp_n cc ON cc.grp = 'C' AND cc.i = 1 + MOD(b.n * 3, @nC)
WHERE @cargar = 1;

-- 1500 huespedes (DNI 9xxxxxxx; uno de cada 25 con pasaporte)
INSERT IGNORE INTO huesped (tipo_documento, num_documento, nombres, apellidos, pais_procedencia, telefono, email, fecha_registro)
SELECT IF(MOD(s.seq, 25) = 0, 'PASAPORTE', 'DNI'),
       IF(MOD(s.seq, 25) = 0, CONCAT('P', LPAD(s.seq, 7, '0')), CONCAT('9', LPAD(s.seq, 7, '0'))),
       CONCAT(n1.v, IF(MOD(s.seq, 3) = 0, CONCAT(' ', n2.v), '')),
       CONCAT(a1.v, ' ', a2.v),
       IF(MOD(s.seq, 25) = 0, kk.v, @peru),
       CONCAT('9', LPAD(10000000 + MOD(s.seq * 7919, 90000000), 8, '0')),
       IF(MOD(s.seq, 4) = 0, CONCAT('huesped', s.seq, '@example.com'), NULL),
       TIMESTAMP('2026-01-02')
FROM seq_1_to_1500 s
JOIN tmp_n n1 ON n1.grp = 'N' AND n1.i = 1 + MOD(s.seq * 7, @nN)
JOIN tmp_n n2 ON n2.grp = 'S' AND n2.i = 1 + MOD(s.seq * 13, @nS)
JOIN tmp_n a1 ON a1.grp = 'A' AND a1.i = 1 + MOD(s.seq * 11, @nA)
JOIN tmp_n a2 ON a2.grp = 'A' AND a2.i = 1 + MOD(s.seq * 17 + 7, @nA)
JOIN tmp_n kk ON kk.grp = 'K' AND kk.i = 1 + MOD(s.seq DIV 25, @nK)
WHERE @cargar = 1;

-- reservas: cada habitacion tiene turnos de 4 dias con estadias de 1 a 3 noches (sin cruces)
INSERT INTO reserva (id_huesped, id_habitacion, id_usuario, fecha_reserva, fecha_checkin, fecha_checkout, num_huespedes,
                     hora_checkin, monto_total, estado, canal, id_empresa, motivo_cancelacion)
SELECT @h0 + 1 + MOD(z.k * 1103, 1500),
       z.id_habitacion,
       IF(MOD(z.hsh DIV 5, 7) = 0, 1, 2),
       IF(z.ci > @hoy,
          TIMESTAMP(DATE_SUB(@hoy, INTERVAL MOD(z.hsh, 20) DAY), '00:00:00') + INTERVAL (540 + MOD(z.hsh * 3, 300)) MINUTE,
          IF(z.canal = 'PRESENCIAL',
             TIMESTAMP(z.ci, '00:00:00') + INTERVAL (660 + MOD(z.hsh, 240)) MINUTE,
             TIMESTAMP(DATE_SUB(z.ci, INTERVAL (1 + MOD(z.hsh DIV 11, 14)) DAY), '00:00:00') + INTERVAL (540 + MOD(z.hsh * 3, 600)) MINUTE)),
       z.ci, z.co,
       IF(z.capacidad = 1, 1, 1 + MOD(z.hsh DIV 9, 2)),
       IF(z.estado IN ('FINALIZADA', 'CHECKIN'), SEC_TO_TIME((720 + MOD(z.hsh DIV 4, 360)) * 60), NULL),
       z.precio_base * z.d,
       z.estado, z.canal,
       IF(MOD(z.hsh DIV 13, 8) = 0, @e0 + 1 + MOD(z.k * 7, 60), NULL),
       IF(z.estado = 'CANCELADA',
          ELT(1 + MOD(z.hsh DIV 3, 6), @desistio, 'Cambio de fecha', 'Error en la reserva', 'Falta de pago', 'Duplicidad de reserva', 'Otro'),
          NULL)
FROM (
  SELECT y2.*, ROW_NUMBER() OVER (ORDER BY y2.ci, y2.id_habitacion) AS k,
         CASE WHEN y2.co <= @hoy THEN IF(MOD(y2.hsh, 10) = 0, 'CANCELADA', 'FINALIZADA')
              WHEN y2.ci <= @hoy THEN 'CHECKIN'
              ELSE IF(MOD(y2.hsh, 13) = 0, 'CANCELADA', 'CONFIRMADA') END AS estado,
         ELT(1 + MOD(y2.hsh DIV 7, 20), 'PRESENCIAL','PRESENCIAL','PRESENCIAL','PRESENCIAL','PRESENCIAL','PRESENCIAL','PRESENCIAL','PRESENCIAL',
             'WHATSAPP','WHATSAPP','WHATSAPP','WHATSAPP','WHATSAPP','TELEFONO','TELEFONO','TELEFONO','TELEFONO','BOOKING','BOOKING','BOOKING') AS canal
  FROM (
    SELECT y.*
    FROM (
      SELECT x.*,
             DATE_ADD('2026-01-01', INTERVAL (x.slot * 4 + x.o) DAY) AS ci,
             DATE_ADD('2026-01-01', INTERVAL (x.slot * 4 + x.o + x.d) DAY) AS co
      FROM (
        SELECT w.*, MOD(w.hsh DIV 3, 5 - w.d) AS o
        FROM (
          SELECT h.id_habitacion, h.numero, t.capacidad, t.precio_base, s.seq AS slot,
                 MOD(h.id_habitacion * 7919 + s.seq * 104729, 1000) AS hsh,
                 1 + MOD(MOD(h.id_habitacion * 7919 + s.seq * 104729, 1000), 3) AS d
          FROM habitacion h
          JOIN tipo_habitacion t ON t.id_tipo = h.id_tipo
          JOIN seq_0_to_90 s
        ) w
      ) x
    ) y
    WHERE @cargar = 1
      AND MOD(y.hsh DIV 17, 10) < IF(y.ci <= @hoy, IF(y.ci >= DATE_SUB(@hoy, INTERVAL 3 DAY), 10, 8), 3)
      AND y.ci <= '2026-12-20'
      AND NOT (y.id_habitacion = 2 AND y.ci < '2026-10-04' AND y.co > '2026-10-03')
      AND NOT (y.numero = '308' AND y.co > @hoy)
  ) y2
) z
ORDER BY z.ci, z.id_habitacion;

-- ocupantes: el titular y, cuando son dos personas, un acompanante
INSERT INTO reserva_huesped (id_reserva, id_huesped, principal, fecha_asociacion)
SELECT r.id_reserva, r.id_huesped, 1, r.fecha_reserva FROM reserva r WHERE r.id_reserva > @r0;
INSERT INTO reserva_huesped (id_reserva, id_huesped, principal, fecha_asociacion)
SELECT r.id_reserva, @h0 + 1 + MOD(r.id_huesped - @h0 - 1 + 311, 1500), 0, r.fecha_reserva
FROM reserva r WHERE r.id_reserva > @r0 AND r.num_huespedes = 2;

-- la fecha de registro del huesped es la de su primera reserva
UPDATE huesped h SET h.fecha_registro = COALESCE(
    (SELECT MIN(r.fecha_reserva) FROM reserva_huesped rh JOIN reserva r ON r.id_reserva = rh.id_reserva WHERE rh.id_huesped = h.id_huesped),
    TIMESTAMP('2026-01-02') + INTERVAL MOD(h.id_huesped * 37, 300) DAY)
WHERE h.id_huesped > @h0;

-- ventas de tienda: consumos cargados a la habitacion (en estadias) y ventas al paso
DROP TABLE IF EXISTS tmp_v;
CREATE TABLE tmp_v (m INT NOT NULL PRIMARY KEY, id_reserva INT NULL, id_huesped INT NULL, id_habitacion INT NULL, id_usuario INT NOT NULL,
                    fecha_venta DATETIME NOT NULL, metodo VARCHAR(20) NULL, cliente VARCHAR(100) NULL, anulada TINYINT NOT NULL, hsh INT NOT NULL, id_venta INT NULL);
INSERT INTO tmp_v (m, id_reserva, id_huesped, id_habitacion, id_usuario, fecha_venta, metodo, cliente, anulada, hsh)
SELECT ROW_NUMBER() OVER (ORDER BY q.fecha_venta, q.src, q.ref), q.id_reserva, q.id_huesped, q.id_habitacion, q.id_usuario, q.fecha_venta, q.metodo, q.cliente, q.anulada, q.hsh
FROM (
  SELECT 0 AS src, r.id_reserva AS ref, r.id_reserva, r.id_huesped, r.id_habitacion, r.id_usuario,
         TIMESTAMP(DATE_ADD(r.fecha_checkin, INTERVAL LEAST(MOD(r.id_reserva, DATEDIFF(r.fecha_checkout, r.fecha_checkin)), DATEDIFF(@hoy, r.fecha_checkin)) DAY), '00:00:00')
           + INTERVAL (780 + MOD(r.id_reserva * 37, 480)) MINUTE AS fecha_venta,
         NULL AS metodo, NULL AS cliente, 0 AS anulada, MOD(r.id_reserva * 31, 1000) AS hsh
  FROM reserva r WHERE r.id_reserva > @r0 AND r.estado IN ('FINALIZADA', 'CHECKIN') AND MOD(r.id_reserva, 20) < 9
  UNION ALL
  SELECT 1, r.id_reserva, r.id_reserva, r.id_huesped, r.id_habitacion, r.id_usuario,
         TIMESTAMP(DATE_ADD(r.fecha_checkin, INTERVAL LEAST(MOD(r.id_reserva + 1, DATEDIFF(r.fecha_checkout, r.fecha_checkin)), DATEDIFF(@hoy, r.fecha_checkin)) DAY), '00:00:00')
           + INTERVAL (1100 + MOD(r.id_reserva * 53, 200)) MINUTE,
         NULL, NULL, 0, MOD(r.id_reserva * 47, 1000)
  FROM reserva r WHERE r.id_reserva > @r0 AND r.estado IN ('FINALIZADA', 'CHECKIN') AND MOD(r.id_reserva, 20) < 3
  UNION ALL
  SELECT 2, s.seq, NULL, NULL, NULL, IF(MOD(s.seq, 5) = 0, 1, 2),
         TIMESTAMP(DATE_ADD('2026-01-02', INTERVAL ((s.seq - 1) * 306 DIV 650) DAY), '00:00:00') + INTERVAL (480 + MOD(s.seq * 37, 840)) MINUTE,
         ELT(1 + MOD(s.seq DIV 2, 20), 'EFECTIVO','EFECTIVO','EFECTIVO','EFECTIVO','EFECTIVO','EFECTIVO','EFECTIVO','EFECTIVO','EFECTIVO','EFECTIVO','EFECTIVO',
             'YAPE','YAPE','YAPE','YAPE','YAPE','TARJETA','TARJETA','TRANSFERENCIA','TRANSFERENCIA'),
         IF(MOD(s.seq, 3) = 0, CONCAT(n1.v, ' ', a1.v), NULL),
         IF(MOD(s.seq, 33) = 0, 1, 0), MOD(s.seq * 29, 1000)
  FROM seq_1_to_650 s
  JOIN tmp_n n1 ON n1.grp = 'N' AND n1.i = 1 + MOD(s.seq * 5, @nN)
  JOIN tmp_n a1 ON a1.grp = 'A' AND a1.i = 1 + MOD(s.seq * 9, @nA)
  WHERE @cargar = 1
) q;
SET @v0 = (SELECT COALESCE(MAX(id_venta), 0) FROM venta_tienda);
INSERT INTO venta_tienda (id_huesped, id_habitacion, id_usuario, cliente_externo, metodo_pago, fecha_venta, total, estado,
                          fecha_anulacion, id_usuario_anula, motivo_anulacion)
SELECT t.id_huesped, t.id_habitacion, t.id_usuario, t.cliente, t.metodo, t.fecha_venta, 1,
       IF(t.anulada = 1, 'ANULADA', 'ACTIVA'),
       IF(t.anulada = 1, t.fecha_venta + INTERVAL 20 MINUTE, NULL),
       IF(t.anulada = 1, 1, NULL),
       IF(t.anulada = 1, ELT(1 + MOD(t.hsh, 3), 'Error de digitacion', 'Producto devuelto', 'Cobro duplicado'), NULL)
FROM tmp_v t ORDER BY t.m;
UPDATE tmp_v SET id_venta = @v0 + m;

-- detalle de cada venta: de 1 a 4 productos distintos
INSERT INTO detalle_venta (id_venta, id_producto, cantidad, precio_unitario, subtotal)
SELECT t.id_venta, p.id_producto, 1 + MOD(t.id_venta + s.seq * 3, 3), p.precio, (1 + MOD(t.id_venta + s.seq * 3, 3)) * p.precio
FROM tmp_v t
JOIN seq_1_to_4 s ON s.seq <= 1 + MOD(t.id_venta * 7, 4)
JOIN producto p ON p.id_producto = 1 + MOD(t.id_venta * 13 + s.seq * 7, @nprod)
WHERE t.id_venta > @v0
ORDER BY t.id_venta, s.seq;
UPDATE venta_tienda v SET v.total = (SELECT SUM(d.subtotal) FROM detalle_venta d WHERE d.id_venta = v.id_venta) WHERE v.id_venta > @v0;

-- pagos de las reservas (el consumo cargado a la habitacion se cobra con la estadia)
SET @p0 = (SELECT COALESCE(MAX(id_pago), 0) FROM pago);
DROP TABLE IF EXISTS tmp_c;
CREATE TABLE tmp_c AS
SELECT r.id_reserva, r.id_usuario, r.estado, r.canal, r.monto_total, r.fecha_reserva, r.fecha_checkin, r.fecha_checkout, r.hora_checkin,
       r.monto_total + COALESCE((SELECT SUM(v.total) FROM venta_tienda v JOIN tmp_v t ON t.id_venta = v.id_venta
                                 WHERE t.id_reserva = r.id_reserva AND v.estado = 'ACTIVA'), 0) AS debe,
       MOD(r.id_reserva, 10) AS m10,
       IF(r.canal = 'BOOKING', ELT(1 + MOD(r.id_reserva, 2), 'TARJETA', 'TRANSFERENCIA'),
          ELT(1 + MOD(r.id_reserva DIV 3, 20), 'EFECTIVO','EFECTIVO','EFECTIVO','EFECTIVO','EFECTIVO','EFECTIVO','EFECTIVO','EFECTIVO','EFECTIVO',
              'YAPE','YAPE','YAPE','YAPE','YAPE','TARJETA','TARJETA','TARJETA','TRANSFERENCIA','TRANSFERENCIA','TRANSFERENCIA')) AS metodo
FROM reserva r WHERE r.id_reserva > @r0;
-- 1) pago unico completo, o adelanto
INSERT INTO pago (id_reserva, id_usuario, monto, metodo_pago, tipo_pago, fecha_pago)
SELECT c.id_reserva, c.id_usuario,
       CASE WHEN c.estado = 'FINALIZADA' AND c.m10 <= 6 THEN c.debe
            WHEN c.estado = 'CHECKIN' AND c.m10 <= 2 THEN c.monto_total
            ELSE ROUND(c.monto_total * 0.4) END,
       c.metodo,
       CASE WHEN c.estado = 'FINALIZADA' AND c.m10 <= 6 THEN 'COMPLETO'
            WHEN c.estado = 'CHECKIN' AND c.m10 <= 2 THEN 'COMPLETO'
            ELSE 'ADELANTO' END,
       CASE WHEN c.estado = 'FINALIZADA' AND c.m10 <= 6 THEN TIMESTAMP(c.fecha_checkout, '00:00:00') + INTERVAL (600 + MOD(c.id_reserva * 7, 120)) MINUTE
            WHEN c.estado = 'CHECKIN' AND c.m10 <= 2 THEN TIMESTAMP(c.fecha_checkin, c.hora_checkin)
            ELSE c.fecha_reserva + INTERVAL 30 MINUTE END
FROM tmp_c c
WHERE c.estado IN ('FINALIZADA', 'CHECKIN')
   OR (c.estado = 'CONFIRMADA' AND c.m10 <= 5)
   OR (c.estado = 'CANCELADA' AND c.m10 <= 2);
-- 2) saldo al salir, en las estadias finalizadas que dejaron adelanto
INSERT INTO pago (id_reserva, id_usuario, monto, metodo_pago, tipo_pago, fecha_pago)
SELECT c.id_reserva, c.id_usuario, c.debe - ROUND(c.monto_total * 0.4),
       ELT(1 + MOD(c.id_reserva DIV 5, 4), 'EFECTIVO', 'EFECTIVO', 'YAPE', 'TARJETA'), 'SALDO',
       TIMESTAMP(c.fecha_checkout, '00:00:00') + INTERVAL (600 + MOD(c.id_reserva * 7, 120)) MINUTE
FROM tmp_c c
WHERE c.estado = 'FINALIZADA' AND c.m10 > 6;

-- comprobantes: boleta, factura o nota de venta por estadia finalizada y por algunas ventas al paso
DROP TABLE IF EXISTS tmp_k;
CREATE TABLE tmp_k (
    orden INT NOT NULL AUTO_INCREMENT PRIMARY KEY, origen CHAR(1) NOT NULL, ref INT NOT NULL, tipo VARCHAR(12) NOT NULL,
    id_usuario INT NOT NULL, emision DATETIME NOT NULL, monto DECIMAL(8,2) NOT NULL,
    doc_tipo VARCHAR(10) NULL, doc VARCHAR(20) NULL, nombre VARCHAR(150) NULL, direccion VARCHAR(200) NULL,
    anulado TINYINT NOT NULL, numero VARCHAR(20) NULL, id_comprobante INT NULL);
INSERT INTO tmp_k (origen, ref, tipo, id_usuario, emision, monto, doc_tipo, doc, nombre, direccion, anulado)
SELECT 'R', r.id_reserva,
       IF(r.id_empresa IS NOT NULL, 'FACTURA', IF(MOD(r.id_reserva, 5) = 0, 'NOTA_VENTA', 'BOLETA')),
       r.id_usuario,
       TIMESTAMP(r.fecha_checkout, '00:00:00') + INTERVAL (720 + MOD(r.id_reserva * 11, 180)) MINUTE,
       c.debe,
       IF(r.id_empresa IS NOT NULL, 'RUC', IF(h.tipo_documento = 'DNI', 'DNI', 'PASAPORTE')),
       IF(r.id_empresa IS NOT NULL, e.ruc, h.num_documento),
       IF(r.id_empresa IS NOT NULL, e.razon_social, CONCAT(h.nombres, ' ', h.apellidos)),
       IF(r.id_empresa IS NOT NULL, e.direccion_fiscal, NULL),
       IF(MOD(r.id_reserva, 40) = 7, 1, 0)
FROM reserva r
JOIN tmp_c c ON c.id_reserva = r.id_reserva
JOIN huesped h ON h.id_huesped = r.id_huesped
LEFT JOIN empresa e ON e.id_empresa = r.id_empresa
WHERE r.id_reserva > @r0 AND r.estado = 'FINALIZADA' AND MOD(r.id_reserva, 20) < 17
ORDER BY r.fecha_checkout, r.id_reserva;
INSERT INTO tmp_k (origen, ref, tipo, id_usuario, emision, monto, doc_tipo, doc, nombre, direccion, anulado)
SELECT 'V', v.id_venta, IF(MOD(v.id_venta, 4) = 0, 'NOTA_VENTA', 'BOLETA'), v.id_usuario, v.fecha_venta + INTERVAL 2 MINUTE, v.total,
       NULL, NULL, COALESCE(v.cliente_externo, 'CLIENTE VARIOS'), NULL, 0
FROM venta_tienda v
WHERE v.id_venta > @v0 AND v.id_habitacion IS NULL AND v.estado = 'ACTIVA' AND MOD(v.id_venta, 3) = 0
ORDER BY v.fecha_venta, v.id_venta;
DROP TABLE IF EXISTS tmp_k2;
CREATE TABLE tmp_k2 AS SELECT orden, ROW_NUMBER() OVER (PARTITION BY tipo ORDER BY emision, orden) AS n FROM tmp_k;
UPDATE tmp_k k
JOIN tmp_k2 q ON q.orden = k.orden
SET k.numero = CONCAT(ELT(FIND_IN_SET(k.tipo, 'BOLETA,FACTURA,NOTA_VENTA'), 'B001', 'F001', 'NV001'), '-', LPAD(q.n, 6, '0'));
DROP TABLE tmp_k2;
SET @c0 = (SELECT COALESCE(MAX(id_comprobante), 0) FROM comprobante);
INSERT INTO comprobante (id_reserva, id_usuario, tipo, numero, fecha_emision, monto_total, cliente_tipo_doc, cliente_documento,
                         cliente_nombre, cliente_direccion, subtotal, igv, estado, fecha_anulacion, id_usuario_anula, motivo_anulacion)
SELECT IF(k.origen = 'R', k.ref, NULL), k.id_usuario, k.tipo, k.numero, k.emision, k.monto, k.doc_tipo, k.doc, k.nombre, k.direccion,
       IF(k.tipo = 'NOTA_VENTA', NULL, ROUND(k.monto / 1.18, 2)),
       IF(k.tipo = 'NOTA_VENTA', NULL, k.monto - ROUND(k.monto / 1.18, 2)),
       IF(k.anulado = 1, 'ANULADO', 'EMITIDO'),
       IF(k.anulado = 1, k.emision + INTERVAL 1 DAY, NULL), IF(k.anulado = 1, 1, NULL),
       IF(k.anulado = 1, 'Error en los datos del cliente', NULL)
FROM tmp_k k ORDER BY k.orden;
UPDATE tmp_k k JOIN comprobante c ON c.numero = k.numero SET k.id_comprobante = c.id_comprobante;
UPDATE venta_tienda v JOIN tmp_k k ON k.origen = 'V' AND k.ref = v.id_venta SET v.id_comprobante = k.id_comprobante;
UPDATE venta_tienda v
JOIN tmp_v t ON t.id_venta = v.id_venta AND t.id_reserva IS NOT NULL
JOIN tmp_k k ON k.origen = 'R' AND k.ref = t.id_reserva
SET v.id_comprobante = k.id_comprobante;

-- compras al almacen: una por producto y mes, cubren lo vendido; cada compra deja su gasto de reposicion
DROP TABLE IF EXISTS tmp_vd;
CREATE TABLE tmp_vd (id_producto INT NOT NULL, fecha DATETIME NOT NULL, mes INT NOT NULL, cant INT NOT NULL, KEY (id_producto, fecha));
INSERT INTO tmp_vd
SELECT d.id_producto, v.fecha_venta, MONTH(v.fecha_venta), d.cantidad
FROM detalle_venta d JOIN venta_tienda v ON v.id_venta = d.id_venta
WHERE v.estado = 'ACTIVA' AND v.id_venta > @v0;
DROP TABLE IF EXISTS tmp_m;
CREATE TABLE tmp_m (n INT NOT NULL AUTO_INCREMENT PRIMARY KEY, id_producto INT NOT NULL, fecha DATETIME NOT NULL, cant INT NOT NULL,
                    costo DECIMAL(8,2) NOT NULL, ant INT NULL, id_gasto INT NULL);
INSERT INTO tmp_m (id_producto, fecha, cant, costo)
SELECT q.id_producto, q.fecha, q.cant, q.costo
FROM (
  SELECT p.id_producto, TIMESTAMP(CONCAT('2026-', LPAD(ms.seq, 2, '0'), '-01'), '06:00:00') + INTERVAL MOD(p.id_producto * 7, 120) MINUTE AS fecha,
         COALESCE((SELECT SUM(vd.cant) FROM tmp_vd vd WHERE vd.id_producto = p.id_producto AND vd.mes = ms.seq), 0)
           + IF(ms.seq = 1, IF(MOD(p.id_producto, 25) = 0, 0, IF(MOD(p.id_producto, 7) = 0, 2 + MOD(p.id_producto DIV 7, 8), 40 + MOD(p.id_producto * 7, 60))),
             IF(MOD(p.id_producto, 7) = 0 OR MOD(p.id_producto, 25) = 0, 0, MOD(p.id_producto + ms.seq, 4) * 5)) AS cant,
         ROUND(p.precio * 0.6, 2) AS costo
  FROM producto p JOIN seq_1_to_11 ms
  WHERE @cargar = 1
) q
WHERE q.cant > 0
ORDER BY q.fecha, q.id_producto;
SET @g0 = (SELECT COALESCE(MAX(id_gasto), 0) FROM gasto);
INSERT INTO gasto (fecha, concepto, categoria, monto, metodo_pago, observacion, activo, id_usuario, fecha_registro)
SELECT DATE(m.fecha), CONCAT('Compra de ', p.nombre), @repo, ROUND(m.cant * m.costo, 2),
       ELT(1 + MOD(m.n, 10), 'TRANSFERENCIA','TRANSFERENCIA','TRANSFERENCIA','TRANSFERENCIA','TRANSFERENCIA','YAPE','YAPE','YAPE','TRANSFERENCIA','TARJETA'),
       NULL, 1, 1, m.fecha
FROM tmp_m m JOIN producto p ON p.id_producto = m.id_producto ORDER BY m.n;
UPDATE tmp_m SET id_gasto = @g0 + n;
UPDATE tmp_m m SET m.ant =
    COALESCE((SELECT SUM(m2.cant) FROM tmp_m m2 WHERE m2.id_producto = m.id_producto AND m2.fecha < m.fecha), 0)
  - COALESCE((SELECT SUM(vd.cant) FROM tmp_vd vd WHERE vd.id_producto = m.id_producto AND vd.fecha < m.fecha), 0);
INSERT INTO movimiento_stock (id_producto, id_usuario, tipo, cantidad, stock_anterior, stock_resultante, motivo, referencia, fecha, costo_unitario, id_gasto)
SELECT m.id_producto, 1, 'ENTRADA', m.cant, m.ant, m.ant + m.cant, 'Compra', CONCAT('Compra ', DATE_FORMAT(m.fecha, '%m/%Y')), m.fecha, m.costo, m.id_gasto
FROM tmp_m m ORDER BY m.n;
UPDATE producto p SET p.stock =
    COALESCE((SELECT SUM(m.cant) FROM tmp_m m WHERE m.id_producto = p.id_producto), 0)
  - COALESCE((SELECT SUM(vd.cant) FROM tmp_vd vd WHERE vd.id_producto = p.id_producto), 0)
WHERE @cargar = 1;

-- otros gastos del hotel
SET @g1 = (SELECT COALESCE(MAX(id_gasto), 0) FROM gasto);
INSERT INTO gasto (fecha, concepto, categoria, monto, metodo_pago, observacion, activo, id_usuario, fecha_registro)
SELECT d.f, d.concepto, d.cat, d.monto, d.metodo, NULL, 1, 1, TIMESTAMP(d.f, '10:00:00') + INTERVAL MOD(d.k, 300) MINUTE
FROM (
  SELECT DATE_ADD('2026-01-06', INTERVAL (ms.seq - 1) MONTH) + INTERVAL c.i DAY AS f, c.concepto, 'Servicios' AS cat,
         ROUND(c.base + MOD(ms.seq * 37 + c.i * 11, 90), 2) AS monto, 'TRANSFERENCIA' AS metodo, ms.seq * 10 + c.i AS k
  FROM seq_1_to_10 ms
  JOIN (SELECT 0 AS i, 'Recibo de luz' AS concepto, 420 AS base UNION ALL SELECT 2, 'Recibo de agua', 130 UNION ALL SELECT 3, 'Internet y cable', 160) c
  UNION ALL
  SELECT DATE_ADD('2026-01-28', INTERVAL (ms.seq - 1) MONTH) + INTERVAL c.i DAY, c.concepto, 'Sueldos', c.base, 'TRANSFERENCIA', ms.seq * 10 + c.i + 5
  FROM seq_1_to_10 ms
  JOIN (SELECT 0 AS i, 'Sueldo de recepcion' AS concepto, 1130 AS base UNION ALL SELECT 0, 'Sueldo de limpieza', 1130 UNION ALL SELECT 0, 'Sueldo de administracion', 1800) c
  UNION ALL
  SELECT DATE_ADD('2026-01-04', INTERVAL (s.seq - 1) * 14 DAY), 'Compra de detergente, lejia y bolsas', 'Insumos de limpieza',
         ROUND(80 + MOD(s.seq * 41, 140), 2), 'EFECTIVO', s.seq + 500
  FROM seq_1_to_22 s
  UNION ALL
  SELECT DATE_ADD('2026-01-09', INTERVAL (s.seq - 1) * 7 + MOD(s.seq, 5) DAY),
         ELT(1 + MOD(s.seq, 6), 'Reparacion de aire acondicionado', 'Servicio de plomeria', 'Cambio de focos y cableado', 'Pintura de pasillos', 'Mantenimiento de ascensor', 'Reparacion de cerradura'),
         'Mantenimiento', ROUND(60 + MOD(s.seq * 53, 300), 2), ELT(1 + MOD(s.seq, 3), 'EFECTIVO', 'EFECTIVO', 'YAPE'), s.seq + 700
  FROM seq_1_to_40 s
  UNION ALL
  SELECT DATE_ADD('2026-01-12', INTERVAL (s.seq - 1) * 8 DAY),
         ELT(1 + MOD(s.seq, 5), 'Utiles de oficina', 'Publicidad en redes', 'Pasajes y movilidad', 'Fumigacion', 'Lavanderia de sabanas'),
         'Otros', ROUND(30 + MOD(s.seq * 29, 200), 2), ELT(1 + MOD(s.seq, 4), 'EFECTIVO', 'EFECTIVO', 'YAPE', 'TRANSFERENCIA'), s.seq + 900
  FROM seq_1_to_38 s
) d
WHERE @cargar = 1 AND d.f <= '2026-11-03';
-- en efectivo solo los gastos chicos; los grandes se pagan por Yape o transferencia
UPDATE gasto SET metodo_pago = 'YAPE' WHERE id_gasto > @g1 AND metodo_pago = 'EFECTIVO' AND monto > 60;

-- movimientos manuales de caja (los registra el administrador)
SET @m0 = (SELECT COALESCE(MAX(id_movimiento), 0) FROM movimiento_caja);
INSERT INTO movimiento_caja (id_usuario, tipo, concepto, metodo_pago, monto, referencia, fecha)
SELECT 1, IF(MOD(s.seq, 5) < 2, 'INGRESO', 'EGRESO'),
       IF(MOD(s.seq, 5) < 2,
          ELT(1 + MOD(s.seq, 5), 'Aporte a caja chica', 'Ingreso por servicio adicional'),
          ELT(1 + MOD(s.seq, 6), 'Compra de utiles de aseo', 'Pago de taxi o delivery', 'Compra de gas', 'Pago a tecnico', 'Gastos menores', 'Compra de bolsas')),
       IF(MOD(s.seq, 7) = 0, ELT(1 + MOD(s.seq DIV 7, 2), 'YAPE', 'TRANSFERENCIA'), 'EFECTIVO'),
       IF(MOD(s.seq, 5) < 2, ROUND(40 + MOD(s.seq * 17, 160), 2), ROUND(8 + MOD(s.seq * 13, 41), 2)),
       IF(MOD(s.seq, 4) = 0, CONCAT('MC-', LPAD(s.seq, 5, '0')), NULL),
       TIMESTAMP(DATE_ADD('2026-01-02', INTERVAL ((s.seq - 1) * 306 DIV 1100) DAY), '00:00:00') + INTERVAL (480 + MOD(s.seq * 53, 840)) MINUTE
FROM seq_1_to_1100 s WHERE @cargar = 1;
-- cada manana el administrador repone la caja chica
INSERT INTO movimiento_caja (id_usuario, tipo, concepto, metodo_pago, monto, referencia, fecha)
SELECT 1, 'INGRESO', 'Reposicion de caja chica', 'EFECTIVO', 150.00, 'REP-CAJA',
       TIMESTAMP(DATE_ADD('2026-01-02', INTERVAL (s.seq - 1) DAY), '07:30:00')
FROM seq_1_to_306 s WHERE @cargar = 1;

-- cambios de habitacion dentro de la misma categoria
INSERT INTO cambio_habitacion (id_reserva, id_habitacion_anterior, id_habitacion_nueva, id_usuario, fecha_cambio, precio_anterior, precio_nuevo, motivo)
SELECT r.id_reserva,
       (SELECT MIN(h2.id_habitacion) FROM habitacion h2 WHERE h2.id_tipo = h.id_tipo AND h2.id_habitacion <> r.id_habitacion),
       r.id_habitacion, r.id_usuario,
       TIMESTAMP(DATE_ADD(r.fecha_checkin, INTERVAL 1 DAY), '15:00:00') + INTERVAL MOD(r.id_reserva, 90) MINUTE,
       t.precio_base, t.precio_base,
       ELT(1 + MOD(r.id_reserva, 5), 'Aire acondicionado con falla', 'Ruido en la habitacion', 'Solicitud del huesped', 'Mantenimiento de la habitacion', 'Cambio a una habitacion mas amplia')
FROM reserva r JOIN habitacion h ON h.id_habitacion = r.id_habitacion JOIN tipo_habitacion t ON t.id_tipo = h.id_tipo
WHERE r.id_reserva > @r0 AND r.estado = 'FINALIZADA' AND DATEDIFF(r.fecha_checkout, r.fecha_checkin) >= 2 AND MOD(r.id_reserva, 17) = 0;

-- cierre de caja de cada dia (turno Noche), con los importes que sale de sumar cobros, ingresos y salidas en efectivo del dia
INSERT INTO cierre_caja (id_usuario, fecha, turno, fondo_inicial, cobros_efectivo, otros_ingresos, salidas_efectivo, efectivo_esperado,
                         efectivo_contado, diferencia, efectivo_entregado, fondo_siguiente, observacion, estado, fecha_registro, fecha_confirmacion)
SELECT IF(MOD(q.n, 6) = 0, 1, 2), q.dia, 'Noche', 100, q.cobros, q.otros, q.salidas, 100 + q.cobros + q.otros - q.salidas,
       100 + q.cobros + q.otros - q.salidas + q.dif, q.dif, 100 + q.cobros + q.otros - q.salidas + q.dif - 100, 100,
       IF(q.dif <> 0, 'Diferencia de caja registrada al contar el efectivo', NULL),
       'CONFIRMADO', TIMESTAMP(q.dia, '22:30:00'), TIMESTAMP(q.dia, '23:00:00')
FROM (
  SELECT d.n, d.dia,
         COALESCE((SELECT SUM(p.monto) FROM pago p WHERE p.metodo_pago = 'EFECTIVO' AND DATE(p.fecha_pago) = d.dia AND p.id_pago > @p0), 0)
       + COALESCE((SELECT SUM(v.total) FROM venta_tienda v WHERE v.id_habitacion IS NULL AND v.metodo_pago = 'EFECTIVO' AND v.estado = 'ACTIVA' AND DATE(v.fecha_venta) = d.dia AND v.id_venta > @v0), 0) AS cobros,
         COALESCE((SELECT SUM(m.monto) FROM movimiento_caja m WHERE m.tipo = 'INGRESO' AND m.metodo_pago = 'EFECTIVO' AND DATE(m.fecha) = d.dia AND m.id_movimiento > @m0), 0) AS otros,
         COALESCE((SELECT SUM(m.monto) FROM movimiento_caja m WHERE m.tipo = 'EGRESO' AND m.metodo_pago = 'EFECTIVO' AND DATE(m.fecha) = d.dia AND m.id_movimiento > @m0), 0)
       + COALESCE((SELECT SUM(g.monto) FROM gasto g WHERE g.activo = 1 AND g.metodo_pago = 'EFECTIVO' AND DATE(g.fecha_registro) = d.dia AND g.id_gasto > @g0), 0) AS salidas,
         CASE MOD(d.n, 9) WHEN 0 THEN -2.00 WHEN 1 THEN 1.00 WHEN 2 THEN -0.50 ELSE 0 END AS dif
  FROM (SELECT seq AS n, DATE_ADD('2026-01-02', INTERVAL (seq - 1) DAY) AS dia FROM seq_1_to_306) d
) q
WHERE @cargar = 1;

-- auditoria: movimientos de la aplicacion (sesiones y reservas creadas)
INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, fecha, origen)
SELECT u.id, 'INICIO_SESION', 'usuario', u.id, 'Inicio de sesion correcto',
       TIMESTAMP(DATE_ADD('2026-01-02', INTERVAL (d.seq - 1) DAY), '00:00:00') + INTERVAL (420 + u.k * 190 + MOD(d.seq * 7, 40)) MINUTE, 'SISTEMA'
FROM seq_1_to_306 d JOIN (SELECT 1 AS id, 0 AS k UNION ALL SELECT 2, 1 UNION ALL SELECT 2, 3) u
WHERE @cargar = 1;
INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, fecha, origen)
SELECT u.id, 'CIERRE_SESION', 'usuario', u.id, 'Cierre de sesion',
       TIMESTAMP(DATE_ADD('2026-01-02', INTERVAL (d.seq - 1) DAY), '00:00:00') + INTERVAL (420 + u.k * 190 + 150 + MOD(d.seq * 7, 40)) MINUTE, 'SISTEMA'
FROM seq_1_to_306 d JOIN (SELECT 1 AS id, 0 AS k UNION ALL SELECT 2, 1 UNION ALL SELECT 2, 3) u
WHERE @cargar = 1;
INSERT INTO auditoria (id_usuario, accion, entidad, id_entidad, detalle, fecha, origen)
SELECT r.id_usuario, 'RESERVA_CREAR', 'reserva', r.id_reserva, CONCAT('Reserva registrada. Canal: ', r.canal), r.fecha_reserva, 'SISTEMA'
FROM reserva r WHERE r.id_reserva > @r0;

-- estado actual de las habitaciones segun las estadias de hoy
UPDATE habitacion SET estado = 'DISPONIBLE', motivo_mantenimiento = NULL WHERE @cargar = 1;
UPDATE habitacion h SET h.estado = 'LIMPIEZA'
WHERE @cargar = 1 AND EXISTS (SELECT 1 FROM reserva r WHERE r.id_habitacion = h.id_habitacion AND r.estado = 'FINALIZADA' AND r.fecha_checkout = @hoy);
UPDATE habitacion h SET h.estado = 'OCUPADA'
WHERE @cargar = 1 AND EXISTS (SELECT 1 FROM reserva r WHERE r.id_habitacion = h.id_habitacion AND r.estado = 'CHECKIN');
UPDATE habitacion SET estado = 'MANTENIMIENTO', motivo_mantenimiento = 'Pintado de paredes y cambio de cortinas' WHERE @cargar = 1 AND numero = '308';

DROP TABLE IF EXISTS tmp_n, tmp_v, tmp_c, tmp_k, tmp_vd, tmp_m;

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

-- 10) LIMPIEZA: se quita la tabla arqueo_caja (ningun modulo la usa; el conteo de efectivo esta en cierre_caja)
--     y se agrega la clave foranea que faltaba en venta_tienda.id_usuario_regulariza.
DROP TABLE IF EXISTS arqueo_caja;
ALTER TABLE venta_tienda
    ADD CONSTRAINT fk_venta_regulariza FOREIGN KEY IF NOT EXISTS (id_usuario_regulariza) REFERENCES usuario(id_usuario);
