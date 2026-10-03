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
