# Hotel San Antonio

Aplicación de escritorio para la gestión de un hotel y su tiendita: habitaciones, reservas, cuentas de huéspedes, ventas, comprobantes, caja, almacén y reportes. Java 21 + JavaFX 21, pantallas en FXML, Maven, JDBC y MySQL/MariaDB (XAMPP). Se abre como proyecto Maven en NetBeans.

## Puesta en marcha

1. En XAMPP, encienda **MySQL**.
2. Copie `config.properties.example` como `config.properties` (junto a `pom.xml`; ese archivo **no se sube a Git**) y complete:
   - `db.url`, `db.user` y `db.password`: la conexión a su MySQL. Si usa otro puerto que 3306, cámbielo en `db.url`. Si no existe `config.properties`, el programa usa `root` sin clave en `localhost:3306` y lo anota en el registro. También sirven las variables `HOTEL_DB_URL`, `HOTEL_DB_USER` y `HOTEL_DB_PASSWORD`, que tienen prioridad.
   - `reniec.token`, `sunat.ruc.token` y `decolecta.token`: los tokens de las consultas de DNI, RUC y tipo de cambio. Pídalos al equipo; no los suba a Git.
3. La base de datos se arma con **un solo archivo**: `sql/INSTALACION_COMPLETA_XAMPP.sql`.
   - **Base nueva y vacía:** importe el archivo completo en phpMyAdmin. Crea las 24 tablas, los tipos y 24 habitaciones, el catálogo de la tienda y las cuentas `admin` y `recepcion` (claves de prueba: cámbielas antes de entregar).
   - **Base que ya existe, con datos:** no importe el archivo completo. Haga un respaldo y ejecute solo el bloque final «ACTUALIZACIONES PARA UNA BASE QUE YA EXISTE» (desde su título hasta el final del archivo). Se puede ejecutar más de una vez.
   - Todo cambio nuevo de la base se agrega al final de ese bloque; no se crean otros archivos `.sql`.
4. Abra `pom.xml` en NetBeans o ejecute `mvn clean javafx:run` con Java 21 o superior.

## Qué hace

- **Roles:** administrador, recepcionista y limpieza. Cada uno ve su propio menú y los servicios vuelven a comprobar los permisos.
- **Habitaciones y reservas:** plano por piso, check-in directo, reservas con adelanto de al menos 50 %, calendario de ocupación, cambio de habitación, cancelación (el adelanto no se devuelve) y paso automático a «No llegó». Se puede deshacer un check-in hecho por error el mismo día.
- **Huéspedes:** DNI verificado con RENIEC, pasaporte para extranjeros, acompañantes y empresa con RUC consultado en la SUNAT.
- **Tienda:** carrito con control de stock, venta al contado o cargada a la habitación, y boleta o factura solo si el cliente la pide.
- **Anulaciones:** una venta o un comprobante no se borra, se anula con motivo y queda en la auditoría. La venta anulada devuelve su stock y deja de sumar en caja, cierres y reportes. El comprobante anulado conserva su número.
- **Caja:** libro de caja con filtros y exportación a Excel y PDF, gastos con categorías, cierre de caja por turnos con caja chica, y registro de días en que el sistema no se usó.
- **Almacén:** productos, categorías y movimientos de stock; una entrada por compra pide el costo y puede anotarse como gasto de «Reposición de tienda».
- **Administración:** usuarios, recuperación de clave con código temporal emitido por un administrador, reportes, respaldo de la base desde el sistema y conversor de dólares.
- **Registro de errores:** los errores quedan en la carpeta `HotelSanAntonio/registros` del usuario de Windows.

## Más información

- La base guarda cada acción importante en la tabla `auditoria`.
- Las llamadas reales a RENIEC, SUNAT y Decolecta necesitan Internet y tokens válidos.
- `recursos_originales/` conserva las imágenes originales del proyecto.
