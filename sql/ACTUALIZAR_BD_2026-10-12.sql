-- ACTUALIZACION DE LA BASE DE DATOS (12/10/2026)
--
-- Para una base que YA existe (la de XAMPP con tus datos). Se puede ejecutar mas de una vez: lo que ya esta
-- hecho no se repite. En una base nueva ya viene incluido en INSTALACION_COMPLETA_XAMPP.sql.
-- Antes de ejecutarlo conviene hacer un respaldo (menu Respaldo del sistema).
--
-- Como ejecutarlo: phpMyAdmin > base hotel_san_antonio > pestana SQL > pegar todo > Continuar,
-- o desde la carpeta del proyecto:  mysql -u root -P 3320 -h 127.0.0.1 hotel_san_antonio < sql/ACTUALIZAR_BD_2026-10-12.sql

USE hotel_san_antonio;

-- 1) COMPROBANTE: quien es el cliente y cuanto fue el subtotal y el IGV. Asi un comprobante (boleta, factura o
--    nota de venta) se puede volver a imprimir igual que se emitio.
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
