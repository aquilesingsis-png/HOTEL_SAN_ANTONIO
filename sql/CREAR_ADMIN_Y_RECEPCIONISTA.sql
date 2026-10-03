-- Crear un administrador y un recepcionista en una base HOTEL SAN ANTONIO ya instalada.
-- Contraseñas temporales aleatorias entregadas por separado; este archivo guarda SOLO hashes PBKDF2.
-- Ejecutar UNA SOLA VEZ en phpMyAdmin. No sustituye la instalacion del esquema.
-- Si ya existe el usuario admin o recepcion, el INSERT fallara sin modificar esas cuentas.

USE hotel_san_antonio;

START TRANSACTION;
INSERT INTO usuario (nombre, apellido, usuario, contrasena_hash, rol, activo)
VALUES
    ('Administrador', 'General', 'admin', 'pbkdf2$210000$FVo8kZDdc3y2STloxFeYyw==$xHoFg4lRwcaVryP+TNi/oX+u3u7qIccvPhDECFrK3nU=', 'ADMINISTRADOR', 1),
    ('Recepcionista', 'Hotel', 'recepcion', 'pbkdf2$210000$5sb/E5bxvBZDFTsjcJZOsQ==$yGorwxiscuwz/WW0hDG2c0nr8h0tVufaluYnIm9Fhyg=', 'RECEPCIONISTA', 1);
COMMIT;

SELECT id_usuario, usuario, rol, activo FROM usuario
WHERE usuario IN ('admin','recepcion') ORDER BY id_usuario;
