# Hotel San Antonio

Aplicación de escritorio Java 21 + JavaFX 21, FXML, Maven, JDBC y MySQL/MariaDB. El proyecto conserva el POM y los paquetes originales y se puede abrir como proyecto Maven en NetBeans. Las pantallas nuevas y las existentes siguen siendo archivos FXML editables.

## Puesta en marcha

1. En XAMPP, encienda **MySQL**. La configuración local incluida apunta a `localhost:3306`, base `hotel_san_antonio`, usuario MySQL `root` y contraseña vacía. Estos valores fueron probados en el XAMPP local. Si su instalación usa otra clave, puerto o usuario, edite `config.properties` antes de iniciar. También puede usar `HOTEL_DB_URL`, `HOTEL_DB_USER` y `HOTEL_DB_PASSWORD`; tienen prioridad sobre el archivo.
2. Para una base **nueva y vacía**, importe **una sola vez** `sql/INSTALACION_COMPLETA_XAMPP.sql` desde phpMyAdmin. Crea las 21 tablas, los tipos, las 24 habitaciones, el catálogo de tienda y las cuentas de administrador y recepcionista. Las claves temporales están en `ACCESOS_INICIALES.txt`; sus hashes PBKDF2 son los únicos valores guardados en MySQL. No importe este archivo en una base con tablas o datos.
3. Para una base **existente** de una entrega anterior, respáldela, bórrela e importe de nuevo `sql/INSTALACION_COMPLETA_XAMPP.sql`. El proyecto ya no trae migraciones ni scripts sueltos: ese archivo es el único SQL. Si solo falta la columna `venta_tienda.metodo_pago` (medio de pago de las ventas del carrito), basta con `ALTER TABLE venta_tienda ADD COLUMN metodo_pago ENUM('EFECTIVO','YAPE','TRANSFERENCIA','TARJETA') NULL AFTER cliente_externo;`.
4. Abra `pom.xml` en NetBeans o ejecute `mvn clean javafx:run` con Maven y Java 21 o superior. El POM compila con `--release 21`. Las imágenes originales `fondo_login.png` y `logo_san_antonio.png` siguen incluidas.

## Reservas e identidad

- Cada reserva nueva registra exactamente todas las personas declaradas, hasta la capacidad de `tipo_habitacion`. La tabla `reserva_huesped` relaciona titular y acompañantes; el DNI se busca antes de crear una persona.
- La API de identidad existente es ApiPeru (`https://api.apiperu.pe/dni`). Configure `reniec.token` en `config.properties`. La consulta tiene timeout y se ejecuta fuera del hilo JavaFX. Si falla, se conservan los datos locales o se permite captura manual. Una respuesta verificada solo actualiza nombres y apellidos: no borra teléfono, correo ni país.
- Las reservas anteriores a la migración conservan al titular en `reserva_huesped`. Si una reserva antigua indicaba varios huéspedes pero solo guardaba un DNI, faltan los datos de los acompañantes; el sistema no los inventa.
- Cambiar habitación está en *Reservas programadas*. Usa una transacción, revalida disponibilidad y capacidad, recalcula la diferencia de tarifa para noches pendientes, conserva pagos y registra historial. Un cambio entre tipos exige niveles comerciales configurados por el administrador en *Categorías de habitación*. El mismo tipo funciona sin niveles. Un número mayor representa categoría superior.

## Administración

- *Gastos* permite crear, editar, listar, filtrar y desactivar. Una fecha pasada requiere motivo; `fecha_registro` conserva la captura real.
- *Registro de días pasados* permite a un administrador ingresar una estadía ya concluida y pagada por completo, con fechas reales de estadía y pago, motivo y auditoría. Los ingresos retroactivos se atribuyen a la fecha real del pago, sin inflar la caja del día de captura.
- *Crear usuario* y *Asignar rol* usan los roles definidos en MySQL. El rol `LIMPIEZA` tiene panel propio; únicamente puede consultar habitaciones y cambiar entre `DISPONIBLE` y `LIMPIEZA` cuando no hay check-in activo. El servicio y la navegación comprueban permisos.
- *¿Olvidaste tu contraseña?* usa un token temporal emitido por un administrador tras verificar la identidad del empleado. Vence en 15 minutos, se guarda como hash y funciona una vez. No hay SMTP configurado; no se envía correo ni se muestra la contraseña anterior. Las contraseñas nuevas usan PBKDF2. Una cuenta con SHA-256 heredado se actualiza al iniciar sesión correctamente.

## Limitaciones y configuración externa

- El orden comercial de tipos no estaba en la base original. `nivel_categoria` permanece sin valor hasta que el administrador defina ese orden; no se infiere de la tarifa. Así se evita autorizar un ascenso equivocado.
- Las llamadas reales a ApiPeru, SUNAT y Decolecta requieren tokens e Internet. `config.properties` incluye solo marcadores, nunca claves privadas.
- Las vistas de almacén, caja, reportes y usuarios en `nuevos/` siguen siendo FXML editables. Al abrirlas en la aplicación se conectan con `ModulosNuevosController` y `ModulosDAO`; las altas y movimientos se validan y guardan en MySQL. Gastos, Crear usuario, Asignar rol y Recuperación mantienen sus controladores específicos.
- El SQL de instalación completa incluye dos cuentas con contraseñas temporales para poder entrar inmediatamente. Cámbielas tras el primer acceso.

## Botones y validaciones (actualización 03/10/2026)

- Los botones del menú y las opciones de boleta, factura y pago mantienen el color marrón cuando quedan seleccionados. Los botones comunes se vuelven marrones mientras se presionan, incluidos los que tienen color definido directamente en FXML.
- El carrito avisa cuando no hay producto seleccionado, verifica el stock y precio actuales al aumentar cantidad y vuelve a validarlos dentro de la transacción antes de descontar stock. Una venta rechazada no se guarda parcialmente.
- Los tres botones **Ver todas** del dashboard abren sus listas. **Guardar PDF** en comprobantes exporta el comprobante generado a un PDF A4 elegido por el usuario. Los informes administrativos permiten guardar PDF o CSV.
- La reserva nueva muestra un plano por piso con pasillo central, capacidad, precio y disponibilidad. Una habitación no disponible o de capacidad insuficiente queda deshabilitada. Su FXML incluye una muestra visible en SceneBuilder; la aplicación la reemplaza con las habitaciones reales.
- Para registrar un movimiento de stock, seleccione un producto y pulse **Ver detalle**; se abre el formulario con el código y stock actuales.

## Verificación de esta entrega

- `mvn -o -q package` compiló el proyecto con Java 21.
- Las 16 vistas administrativas antes sin controlador cargaron con una base de prueba y sus botones con `fx:id` quedaron conectados.
- Los 20 accesos de almacén, caja, reportes y usuarios se abrieron desde la navegación; se revisaron 82 opciones de filtro.
- Ocupación muestra también las noches sin reservas del período; la consulta inicial abarca los últimos 30 días y puede agruparse por día, semana o mes.
- Se probaron altas de categoría y producto, cambio de precio, stock, caja, arqueo, cierre y usuario en una base aislada. Se probaron también los rechazos de stock negativo y eliminación de una categoría con productos.
- El plano mostró 24 habitaciones de la base local y 23 disponibles para las fechas iniciales.
- `Nueva_reserva.fxml` se abrió en Gluon SceneBuilder con el plano visible y editable. Tras ese ajuste, se cargaron los 48 FXML con JavaFX sin errores.
- La base local pasó de 17 a 21 tablas y conservó sus 2 usuarios, 3 reservas, 5 productos y 24 habitaciones. La copia de seguridad previa está fuera del proyecto en `C:\Users\HP\Documents\Codex\2026-10-02\a\work\hotel_san_antonio_backup_2026-10-03.sql`.
