# Informe de la versión corregida — 03/10/2026

## Entrega

El proyecto se modificó directamente en `C:\Users\HP\Documents\NetBeansProjects\HOTEL_SAN_ANTONIO`. No se creó otro proyecto ni un ZIP. Se conservaron las imágenes `fondo_login.png` y `logo_san_antonio.png`.

El menú mantiene en marrón la opción seleccionada y los botones comunes cambian a marrón al presionarse. Las 16 vistas FXML de almacén, caja, reportes y usuarios que antes eran maquetas ahora cargan consultas y acciones mediante `ModulosNuevosController` y `ModulosDAO`. Los módulos previos de gastos, creación de usuarios, roles y recuperación conservan sus controladores.

El formulario de nueva reserva presenta un plano por piso, con pasillo, capacidad, tarifa y disponibilidad. Tiene una muestra estática visible en SceneBuilder y la aplicación la reemplaza por datos de MySQL. El botón de comprobantes guarda un PDF A4 con los datos del comprobante emitido.

## Base de datos

- Base local XAMPP: `hotel_san_antonio`, MySQL `root`, contraseña vacía, `localhost:3306`.
- Instalación nueva: importar `sql/INSTALACION_COMPLETA_XAMPP.sql` solo en una base vacía. Incluye 21 tablas y dos cuentas iniciales.
- Cuentas de la aplicación: `admin` y `recepcion`. Las claves vigentes están en `ACCESOS_INICIALES.txt` y se verificaron contra los hashes almacenados.
- Copia de seguridad previa: `C:\Users\HP\Documents\Codex\2026-10-02\a\work\hotel_san_antonio_backup_2026-10-03.sql`.

La base local pasó de 17 a 21 tablas y conservó 2 usuarios, 3 reservas, 5 productos y 24 habitaciones. Las pruebas que escriben datos se ejecutaron en una base aislada.

## Verificación

- `mvn -o -q package`: compilación correcta con Java 21.
- Carga FXML: 48 archivos, 0 errores.
- Las 16 pantallas administrativas cargaron sus tablas; todos sus botones con `fx:id` quedaron conectados.
- Los 20 accesos del menú de almacén, caja, reportes y usuarios abrieron correctamente; se probaron 82 opciones de filtro.
- El reporte de ocupación incluyó seis tipos de habitación con cero ocupación en un día sin reservas y permite agrupar el período por día, semana o mes.
- Pruebas en base aislada: categoría, producto, precio, entrada de stock, movimiento de caja, arqueo, cierre, pago parcial y final, edición de usuario y las cinco agrupaciones de reporte personalizado.
- Validaciones probadas: stock negativo, sobrepago y eliminación de categoría con productos se rechazaron.
- Plano: 24 habitaciones cargadas de la base local; 23 disponibles en las fechas iniciales de la prueba.
- `Nueva_reserva.fxml` abrió en Gluon SceneBuilder y mostró la muestra del plano con habitaciones; los 48 FXML también cargaron con JavaFX tras este ajuste.
- PDF: se comprobó estructura válida, una página y tamaño A4 con `pdfinfo`.
- Las claves del archivo de accesos coincidieron con las cuentas locales.

## Comprobaciones pendientes fuera del entorno automatizado

No se probaron llamadas reales a ApiPeru, SUNAT o Decolecta sin credenciales de esos servicios. Tampoco se hizo una impresión física ni una prueba manual de todos los flujos con ratón en NetBeans.

Para iniciar y para elegir el SQL correcto, siga `README.md`.

