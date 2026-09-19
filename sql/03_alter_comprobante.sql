-- =====================================================================
-- Hotel San Antonio — Migracion: COMPROBANTE consolida reserva + N ventas
-- Ejecutar en la pestaña SQL de phpMyAdmin, DESPUES de 02_alter_categoria.sql
-- =====================================================================
USE hotel_san_antonio;

-- 1) COMPROBANTE ya no apunta a una sola venta; ahora es VENTA_TIENDA
--    quien apunta (opcionalmente) al comprobante que la cerro.
ALTER TABLE comprobante DROP FOREIGN KEY fk_comprobante_venta;
ALTER TABLE comprobante DROP COLUMN id_venta;

ALTER TABLE venta_tienda ADD COLUMN id_comprobante INT NULL AFTER id_usuario;
ALTER TABLE venta_tienda ADD CONSTRAINT fk_venta_comprobante
  FOREIGN KEY (id_comprobante) REFERENCES comprobante(id_comprobante);

-- 2) Migrar los datos de prueba que ya tenias al nuevo esquema
UPDATE venta_tienda SET id_comprobante = 1 WHERE id_venta = 1; -- consumo cargado a la reserva 1
UPDATE venta_tienda SET id_comprobante = 2 WHERE id_venta = 2; -- venta de cliente externo

-- El comprobante de la reserva 1 ahora debe incluir habitacion (150) + tiendita (5.50)
UPDATE comprobante SET monto_total = 155.50 WHERE id_comprobante = 1;

-- 3) Ejemplo de venta "pendiente": huesped en estadia activa (reserva 2) que
--    compra en la tiendita y todavia no hace check-out (sin comprobante aun)
INSERT INTO venta_tienda (id_huesped, id_habitacion, id_usuario, id_comprobante, cliente_externo, total) VALUES
(2, 15, 2, NULL, NULL, 2.00);

INSERT INTO detalle_venta (id_venta, id_producto, cantidad, precio_unitario, subtotal) VALUES
(3, 5, 1, 2.00, 2.00);
