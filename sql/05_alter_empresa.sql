-- =====================================================================
-- Hotel San Antonio — Migracion: facturacion a empresas (tabla EMPRESA)
-- Ejecutar DESPUES de 04_indices.sql
--
-- Los datos fiscales (RUC, razon social, direccion) dependen del RUC y no
-- de la reserva, asi que van en su propia tabla (3FN). RESERVA solo guarda
-- la llave foranea id_empresa (opcional).
-- =====================================================================
USE hotel_san_antonio;

CREATE TABLE empresa (
    id_empresa        INT AUTO_INCREMENT PRIMARY KEY,
    ruc               VARCHAR(11)  NOT NULL UNIQUE,
    razon_social      VARCHAR(150) NOT NULL,
    direccion_fiscal  VARCHAR(200) NULL
);

ALTER TABLE reserva
  ADD COLUMN id_empresa INT NULL AFTER canal,
  ADD CONSTRAINT fk_reserva_empresa FOREIGN KEY (id_empresa) REFERENCES empresa(id_empresa);

-- Como ahora se captura el RUC del cliente, se permite el tipo FACTURA
ALTER TABLE comprobante
  MODIFY tipo ENUM('BOLETA','NOTA_VENTA','FACTURA') NOT NULL;

-- Empresa de prueba (datos devueltos por la API de RUC para el RUC de ejemplo)
INSERT INTO empresa (ruc, razon_social, direccion_fiscal) VALUES
('20131312955', 'SUPERINTENDENCIA NACIONAL DE ADUANAS Y DE ADMINISTRACION TRIBUTARIA - SUNAT', 'AV. GARCILASO DE LA VEGA NRO. 1472');
