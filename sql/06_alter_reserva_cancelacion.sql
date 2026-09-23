-- =====================================================================
-- Hotel San Antonio — Migracion: motivo de cancelacion de una reserva
-- Ejecutar DESPUES de 05_alter_empresa.sql
--
-- La pantalla "Cancelar reserva" pide un motivo (de una lista) y un detalle
-- libre; se guardan en la propia reserva porque son datos de ESA reserva,
-- no ameritan una tabla aparte (no se repiten ni se consultan por su cuenta).
-- =====================================================================
USE hotel_san_antonio;

ALTER TABLE reserva
  ADD COLUMN motivo_cancelacion  VARCHAR(60)  NULL,
  ADD COLUMN detalle_cancelacion VARCHAR(500) NULL;
