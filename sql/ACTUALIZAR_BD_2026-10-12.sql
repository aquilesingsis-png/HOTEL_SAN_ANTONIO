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
