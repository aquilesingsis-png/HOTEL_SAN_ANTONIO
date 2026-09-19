-- =====================================================================
-- Hotel San Antonio — Migracion: agregar CATEGORIA de producto
-- Ejecutar en la pestaña SQL de phpMyAdmin sobre la BD hotel_san_antonio
-- (no borra nada de lo que ya cargaste)
-- =====================================================================
USE hotel_san_antonio;

CREATE TABLE categoria (
    id_categoria INT AUTO_INCREMENT PRIMARY KEY,
    nombre       VARCHAR(40) NOT NULL UNIQUE
);

INSERT INTO categoria (nombre) VALUES
('Bebidas'), ('Snacks'), ('Golosinas'), ('Higiene personal'), ('Otros');

ALTER TABLE producto ADD COLUMN id_categoria INT NULL AFTER marca;

-- Asignar categoria a los 5 productos de prueba ya insertados
UPDATE producto SET id_categoria = (SELECT id_categoria FROM categoria WHERE nombre = 'Snacks')
  WHERE codigo_barra IN ('7750243009116', '7751148000208'); -- Galleta Soda, Papitas Lays

UPDATE producto SET id_categoria = (SELECT id_categoria FROM categoria WHERE nombre = 'Bebidas')
  WHERE codigo_barra IN ('7751271015008', '7750243002322'); -- Inca Kola, Agua

UPDATE producto SET id_categoria = (SELECT id_categoria FROM categoria WHERE nombre = 'Golosinas')
  WHERE codigo_barra = '7750070032001'; -- Chocolate Sublime

-- Cualquier producto que quede sin categoria (por si agregaste otros) cae en "Otros"
UPDATE producto SET id_categoria = (SELECT id_categoria FROM categoria WHERE nombre = 'Otros')
  WHERE id_categoria IS NULL;

ALTER TABLE producto MODIFY id_categoria INT NOT NULL;
ALTER TABLE producto ADD CONSTRAINT fk_producto_categoria
  FOREIGN KEY (id_categoria) REFERENCES categoria(id_categoria);
