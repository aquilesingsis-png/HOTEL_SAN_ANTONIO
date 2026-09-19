-- =====================================================================
-- Hotel San Antonio — Indices de rendimiento
-- Ejecutar en la pestaña SQL de phpMyAdmin, DESPUES de 03_alter_comprobante.sql
-- (Las PK, UNIQUE y FK ya tienen indice automatico en InnoDB; estos son
-- adicionales, para las consultas mas frecuentes del sistema)
-- =====================================================================
USE hotel_san_antonio;

CREATE INDEX idx_habitacion_estado ON habitacion (estado);
CREATE INDEX idx_huesped_apellidos ON huesped (apellidos);
CREATE INDEX idx_reserva_fechas    ON reserva (fecha_checkin, fecha_checkout);
CREATE INDEX idx_reserva_estado    ON reserva (estado);
