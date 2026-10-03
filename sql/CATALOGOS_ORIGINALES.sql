-- Reponer los catalogos originales SOLO si estas cuatro tablas estan vacias.
-- Conserva usuarios, huespedes, reservas y pagos existentes.
-- Las habitaciones se inician disponibles porque no se recrean reservas ficticias.
USE hotel_san_antonio;
START TRANSACTION;
INSERT INTO tipo_habitacion (nombre, capacidad, precio_base) VALUES
('Simple',      1, 100.00),
('Ejecutiva',   1, 120.00),
('Doble',       2, 150.00),
('Matrimonial', 2, 130.00),
('Suite',       2, 150.00),
('King',        2, 180.00);

INSERT INTO habitacion (numero, id_tipo, piso, estado) VALUES
('101',1,1,'DISPONIBLE'), ('102',1,1,'DISPONIBLE'), ('103',1,1,'DISPONIBLE'),      ('104',1,1,'DISPONIBLE'),
('105',2,1,'DISPONIBLE'), ('106',2,1,'DISPONIBLE'),   ('107',2,1,'DISPONIBLE'),  ('108',2,1,'DISPONIBLE'),
('201',3,2,'DISPONIBLE'),    ('202',3,2,'DISPONIBLE'), ('203',3,2,'DISPONIBLE'), ('204',3,2,'DISPONIBLE'),
('205',4,2,'DISPONIBLE'), ('206',4,2,'DISPONIBLE'), ('207',4,2,'DISPONIBLE'),    ('208',4,2,'DISPONIBLE'),
('301',5,3,'DISPONIBLE'), ('302',5,3,'DISPONIBLE'), ('303',5,3,'DISPONIBLE'),   ('304',5,3,'DISPONIBLE'),
('305',6,3,'DISPONIBLE'), ('306',6,3,'DISPONIBLE'),    ('307',6,3,'DISPONIBLE'), ('308',6,3,'DISPONIBLE');

INSERT INTO categoria (nombre) VALUES
('Bebidas'), ('Snacks'), ('Golosinas'), ('Higiene personal'), ('Otros');

INSERT INTO producto (codigo_barra, nombre, marca, id_categoria, precio, stock) VALUES
('7750243009116', 'Galleta Soda',         'Field',    2, 1.50, 40),
('7751271015008', 'Inca Kola 500ml',      'Inca Kola',1, 3.50, 30),
('7750243002322', 'Agua sin gas 625ml',   'San Luis', 1, 2.00, 50),
('7751148000208', 'Papitas Lays',         'Lays',     2, 2.50, 25),
('7750070032001', 'Chocolate Sublime',    'Nestle',   3, 2.00, 35);
COMMIT;
