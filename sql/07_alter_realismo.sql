-- =====================================================================
-- Hotel San Antonio — Migracion: datos que un hotel real sí registraría
-- Ejecutar DESPUES de 06_alter_reserva_cancelacion.sql
--
-- Tres cosas que la interfaz ya preguntaba (o necesitaba mostrar) pero
-- la base de datos no tenia donde guardar:
--   - cuantos huespedes se hospedan (el formulario de Nueva reserva ya
--     trae un contador, pero se descartaba)
--   - a que hora se espera la llegada (el formulario ya trae un
--     selector de hora, tambien descartado)
--   - por que se puso una habitacion en mantenimiento (hoy se pone en
--     mantenimiento sin dejar ningun registro del motivo)
-- =====================================================================
USE hotel_san_antonio;

ALTER TABLE reserva
  ADD COLUMN num_huespedes INT NOT NULL DEFAULT 1,
  ADD COLUMN hora_checkin  TIME NULL;

ALTER TABLE habitacion
  ADD COLUMN motivo_mantenimiento VARCHAR(200) NULL;
