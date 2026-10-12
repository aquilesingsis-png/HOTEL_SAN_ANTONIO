-- USUARIO PROPIO DE LA APLICACION (la app ya no necesita entrar como root)
--
-- hotel_app solo puede leer y escribir los datos de hotel_san_antonio. No puede borrar tablas ni la
-- base, no ve otras bases, no puede dar permisos a nadie y solo se conecta desde esta misma PC.
--
-- Como usarlo:
--   1. Cambie CAMBIAR_ESTA_CLAVE (las dos veces) por una clave suya.
--   2. Ejecutelo con root: phpMyAdmin > SQL, o  mysql -u root < sql/CREAR_USUARIO_APP.sql
--   3. En config.properties escriba:  db.user=hotel_app  y  db.password=<la clave elegida>
--
-- Los respaldos automaticos (mysqldump con flush-logs) necesitan mas permisos que la app.

CREATE USER IF NOT EXISTS 'hotel_app'@'localhost' IDENTIFIED BY 'CAMBIAR_ESTA_CLAVE';
CREATE USER IF NOT EXISTS 'hotel_app'@'127.0.0.1' IDENTIFIED BY 'CAMBIAR_ESTA_CLAVE';

GRANT SELECT, INSERT, UPDATE, DELETE ON hotel_san_antonio.* TO 'hotel_app'@'localhost';
GRANT SELECT, INSERT, UPDATE, DELETE ON hotel_san_antonio.* TO 'hotel_app'@'127.0.0.1';

FLUSH PRIVILEGES;
