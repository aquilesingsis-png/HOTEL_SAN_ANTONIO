# Informe final — Hotel San Antonio

## Actualización de botones y validaciones — 03/10/2026

Se corrigió el estado marrón de las opciones elegidas en el menú y en el carrito. Los botones comunes muestran marrón al presionarse aunque tengan color inline en FXML. Los tres botones **Ver todas** del dashboard abren las listas correspondientes. Se agregaron avisos para acciones sin selección en carrito, limpieza, categorías y reservas programadas; el aumento de cantidad consulta stock y precio actuales. La venta verifica de nuevo precio, stock, duplicados, cantidades y usuario dentro de la transacción. La impresión del comprobante abre el diálogo de impresora después de generarlo. Se corrigió la lectura del nombre del producto, que antes se sustituía por el nombre de su categoría.

Las maquetas originales sin controlador de almacén, reportes y parte de caja/usuarios se conservan en el ZIP, pero la navegación muestra **Módulo en construcción** para que no aparezcan botones de guardado sin efecto. Por ello **no se certifica que todos los módulos del proyecto sean operativos**. Tampoco se ha verificado una impresión física ni las llamadas con credenciales reales a servicios externos. No se modificó el esquema SQL en esta actualización; los scripts completos ya incluidos siguen siendo los de esta entrega.

Verificaciones de esta actualización: Maven `BUILD SUCCESS` con 87 fuentes Java, carga de 48/48 FXML, interfaz JavaFX con cuenta de administrador (selección marrón, rutas del dashboard y carrito), pulsación marrón de botón con estilo inline, lectura real de catálogo y rechazo de precio desactualizado y stock insuficiente sin cambios en `venta_tienda`. Estas pruebas se hicieron con el MySQL local de XAMPP encendido. La aplicación requiere que MySQL permanezca iniciado durante su uso.

Fecha: 02/10/2026. Proyecto de origen: `HOTEL_SAN_ANTONIO_RESERVAS_CORREGIDO.zip` (SHA-256 `57915FC6743DE4163BA2A19E42DFA48A17361229C73048C69337C3932316199C`). Se trabajó sobre una copia del proyecto existente; el ZIP original no se modificó.

### Reentrega: corrección del error de la captura

El ZIP anterior no contenía `config.properties`, y `ConexionBD` exigía `db.url` y `db.user`. Esto causaba el diálogo genérico de conexión aunque XAMPP estuviera disponible. Esta entrega incluye `config.properties` con los valores **probados en el XAMPP local** (`localhost:3306`, `hotel_san_antonio`, `root`, contraseña MySQL vacía), más un recurso de respaldo para cuando el directorio de ejecución no contiene el archivo. El login distingue ahora errores de conexión, credenciales MySQL y tablas ausentes; el texto del diálogo se ajusta a varias líneas. No se incluyen tokens privados de ApiPeru, SUNAT ni Decolecta.

El SQL `sql/INSTALACION_COMPLETA_XAMPP.sql` crea 17 tablas, 6 tipos, 24 habitaciones disponibles, 5 categorías, 5 productos y dos cuentas con PBKDF2. **Solo debe ejecutarse en una base nueva sin tablas**. Los accesos temporales están en `ACCESOS_INICIALES.txt` y deben cambiarse. Las dos imágenes originales del login se conservaron byte a byte (SHA-256 idéntico al ZIP de origen).

**Implementado y verificado:** compilación, inicio, carga de todos los FXML y operaciones MySQL probadas en un clon (reservas, huéspedes, cambio, gastos, limpieza, recuperación y captura retroactiva). **Implementado pero requiere credencial o servicio externo:** consulta real de ApiPeru y las integraciones originales de SUNAT/Decolecta. **No implementado por ausencia de infraestructura:** envío automático de recuperación por correo; se entregó el flujo seguro alternativo con token administrado. La cola local sin conexión se descartó porque el proyecto usa una única base MySQL central.

## 1. Arquitectura encontrada

Aplicación de escritorio Maven con Java 21, JavaFX 21, FXML, CSS, JDBC y MySQL/MariaDB. El flujo real es `App → LoginController → PrincipalController/Navegacion → FXML + Controller → Service (cuando existe) → DAO → JDBC → MySQL`. Se mantuvieron paquetes, tecnologías, POM y estructura de NetBeans. El proyecto inicial tenía 67 archivos Java, 39 FXML y 2 CSS; el entregable tiene 87 Java, 48 FXML y 3 CSS.

## 2. Problemas críticos encontrados

- La reserva guardaba solo el titular, aunque `num_huespedes` podía ser mayor que uno.
- La conexión usaba credenciales MySQL codificadas en Java y el login usaba SHA-256 simple.
- Faltaban cambio de habitación, gastos persistentes, rol Limpieza, recuperación y registro retroactivo.
- La consulta de identidad existente (ApiPeru) no tenía timeout ni validación suficiente; la búsqueda local por DNI no reconciliaba identidad verificada.
- Las rutas se autorizaban principalmente mediante la interfaz; había pantallas `nuevos/` que eran maquetas sin controlador.
- El esquema inicial incluía cuentas de demostración con contraseña conocida.

## 3–5. Duplicidad, eliminación y justificación

La auditoría no encontró archivos idénticos byte a byte, DAO duplicados ni handlers FXML declarados sin método. `Nueva_reservaController` y `ReservaFormController` se parecen porque sirven a dos flujos existentes; ambos se conservaron y comparten ahora reglas en `ReservaService` y componentes de acompañantes. Se eliminaron únicamente cinco controladores vacíos sin referencias funcionales: `AlmacenController`, `CajaController`, `CheckInOutController`, `ReportesController` y `UsuariosController`. Las maquetas FXML sin controlador se conservaron para no retirar rutas existentes.

## 6–7. Archivos modificados y creados

**Modificados:** `App`, `LoginController`, `PrincipalController`, `Nueva_reservaController`, `ReservasProgramadasController`, `ReservaFormController`; `CuentaDAO`, `HabitacionDAO`, `HuespedDAO`, `PagoDAO`, `ReservaDAO`, `UsuarioDAO`; `TipoHabitacion`, `ReniecService`, `ReservaService`; `Alertas`, `ConexionBD`, `Navegacion`, `PasswordUtil`, `SesionActual`; FXML de nueva reserva, reservas programadas, comprobante, dashboard, reserva desde habitaciones, login, asignar rol, crear usuario y principal administrador; `reserva.css`, `config.properties.example` y `sql/hotel_san_antonio.sql`.

**Creados:** `README.md`, `sql/migrations/001_operacion_segura.sql`; controladores `AsignarRol`, `CategoriasHabitacion`, `CrearUsuario`, `EmisionRecuperacion`, `Gastos`, `Limpieza`, `Recuperacion`, `RegistroHistorico`, `CambiarHabitacion`, `HuespedAdicional`; DAO `Auditoria`, `CambioHabitacion`, `Gasto`, `Recuperacion`, `ReservaHuesped`; modelo `Gasto`; servicios `CategoriaHabitacion`, `Gasto`, `Limpieza`, `Recuperacion`, `Usuario`; utilidades `BootstrapAdmin`, `Permisos`; nueve FXML nuevos y `app.css`. La lista y las rutas completas están dentro del ZIP.

## 8–9. Base de datos y scripts

La migración **aditiva** `sql/migrations/001_operacion_segura.sql` agrega el rol `LIMPIEZA`, relación `reserva_huesped`, nivel comercial configurable del tipo de habitación, historial `cambio_habitacion`, `gasto`, `auditoria`, fechas y motivos retroactivos en reserva/pago, y tokens de recuperación. No contiene `DROP`, `TRUNCATE` ni borrado masivo. Se probó sobre copias antes de aplicarse una vez a la base local `hotel_san_antonio`, tras respaldo lógico. En aquel momento conservó 9 reservas, 7 huéspedes y 2 usuarios. **La base local fue vaciada después de esa verificación; no atribuyo esa acción a la migración.** Al preparar esta reentrega se encontraron 17 tablas, 2 usuarios y cero catálogos o registros operativos. Tras un nuevo respaldo lógico se repusieron solo los catálogos originales: ahora hay 6 tipos, 24 habitaciones, 5 categorías, 5 productos, 2 usuarios y ninguna reserva o huésped. No se inventaron personas ni estadías. Para instalaciones nuevas use el SQL completo; para una base antigua con datos use solo la migración.

Las reservas antiguas que indicaban más de un huésped y solo guardaban el titular siguen necesitando identificación manual de acompañantes: no se inventaron personas.

## 10–14. Reservas, DNI, ApiPeru y huéspedes

Las dos entradas de reserva buscan por tipo y número de documento antes de crear persona. Un DNI existente reutiliza el registro; una respuesta verificada de ApiPeru permite comparar y actualizar solo nombres y apellidos, preservando datos manuales como teléfono y correo. La consulta HTTP usa el endpoint ya presente en el proyecto, token externo, timeout y una tarea fuera del hilo JavaFX. El formulario permite continuar con datos locales o captura manual si el proveedor falla.

`reserva_huesped` registra titular y todos los acompañantes. El servicio exige que la cantidad de personas identificadas coincida con `num_huespedes`, evita documentos repetidos y valida la capacidad de `tipo_habitacion`. La reserva, sus huéspedes, pagos y estados relacionados se persisten con transacción JDBC y revisión de disponibilidad antes de confirmar.

**Límite externo:** no se realizó una llamada real a ApiPeru en esta entrega. El token y la conectividad pertenecen al entorno del usuario; se verificó que la tarea rechaza DNI inválido y la ausencia de token, además de cargar las pantallas. La respuesta en vivo y una caída real del proveedor siguen sin verificar.

## 15–16. Cambio de habitación y tarifa

La acción está en *Reservas programadas*. Bloquea las habitaciones involucradas, comprueba de nuevo estado, cruces de fechas y capacidad, y permite el mismo tipo o uno con nivel comercial superior. La base original no definía un orden de categorías: `nivel_categoria` comienza en `NULL` y el administrador lo configura en *Categorías de habitación*. Hasta entonces solo se permiten cambios dentro del mismo tipo. El servicio calcula la diferencia según la tarifa guardada para las noches pendientes, conserva pagos y actualiza total y saldo. Registra `cambio_habitacion` y `auditoria` en la misma transacción. En una estadía con check-in, la habitación anterior pasa a limpieza y la nueva a ocupada.

## 17. Recuperación de contraseña

*¿Olvidaste tu contraseña?* usa emisión de token por administrador después de verificar la identidad del empleado. El token aleatorio vence a los 15 minutos, se almacena solo su hash y se consume una vez. La contraseña nueva usa PBKDF2; cuentas heredadas SHA-256 se migran al iniciar sesión correctamente. No existe SMTP en el proyecto, por lo que no se simula envío de correo.

## 18. Gastos

Módulo MySQL real, exclusivo de administrador, para crear, listar, filtrar, editar y desactivar gastos. Valida monto positivo, conserva `fecha_registro` real y exige motivo cuando la fecha del gasto es pasada. La tabla se actualiza después de las operaciones. Las modificaciones se auditan.

## 19–20. Limpieza y registro retroactivo

`LIMPIEZA` tiene un marco FXML propio y acceso limitado a consultar habitaciones y alternar los estados permitidos cuando no hay ocupación activa. `Permisos` valida rutas y `LimpiezaService` valida cada operación. No puede abrir finanzas ni cambiar reservas mediante el servicio.

La estrategia de contingencia elegida fue captura retroactiva, compatible con MySQL central. Un administrador puede registrar una estadía antigua concluida y pagada, con fechas reales del evento/pago, fecha de captura automática, motivo y auditoría. Los pagos históricos se suman en la fecha real del pago, sin inflar la caja del día de captura. Los gastos pasados también guardan motivo y fecha de registro.

## 21–22. FXML y CSS

Las pantallas añadidas son FXML y usan CSS común (`app.css`) con la paleta marrón/dorado existente. Se mantuvieron las vistas existentes y la carga central de estilos de escenas. Dos FXML existentes se ajustaron al namespace JavaFX 21. Se conservaron controles dinámicos pequeños del diseño original; no se creó una interfaz principal en Java. La prueba con `FXMLLoader` cargó los **48/48 FXML** sin error. **Scene Builder no estaba instalado**, de modo que su apertura directa no se verificó; los archivos permanecen FXML editables y compatibles con `FXMLLoader` de JavaFX 21.

## 23–24. DAO y seguridad

La persistencia nueva se ubicó en DAO y se usa `PreparedStatement`; una búsqueda estática no encontró SQL en controladores. `ConexionBD` lee `HOTEL_DB_URL`, `HOTEL_DB_USER`, `HOTEL_DB_PASSWORD`, un archivo local `config.properties` y valores locales incluidos para XAMPP, en ese orden de prioridad. El ZIP incluye solo la configuración MySQL local y marcadores de tokens; no contiene las claves API privadas del proyecto original. `sql/hotel_san_antonio.sql` sigue siendo un esquema sin cuentas; el nuevo SQL completo incluye dos hashes PBKDF2 y las claves temporales se entregan por separado. Operaciones de varias tablas usan transacciones con rollback ante error; las rutas y servicios sensibles comprueban rol. `Alertas` registra el error técnico y muestra un mensaje legible.

## 25–27. Pruebas, aprobaciones y límites

- **Compilación:** `mvn -o clean package` con el Maven incluido en NetBeans: `BUILD SUCCESS`, 87 fuentes Java y 54 recursos. El POM compila con `--release 21`; el equipo de prueba tenía Java 25. No había suite JUnit en el proyecto.
- **Login real de JavaFX:** administrador y recepcionista iniciaron sesión y abrieron su marco principal; una contraseña incorrecta fue rechazada. El administrador se probó sin variables de entorno, usando la nueva configuración local. Los hashes se verificaron tanto en la base actual como en una instalación de prueba.
- **SQL completo:** importado en una base de prueba separada: 17 tablas, 6 tipos, 24 habitaciones, 5 productos y 2 usuarios. No se ejecutó sobre la base local existente.
- **Inicio y FXML:** `App.start(new Stage())` abrió login; `FXMLLoader` cargó 48/48 vistas, sin handlers rotos. Aviso no fatal de JavaFX por classpath de la prueba.
- **Integración sobre clon MySQL:** 36 comprobaciones aprobadas: hash correcto/incorrecto; dos huéspedes y rechazo de cantidad incompleta; orden de categoría mediante servicio; ascenso, recálculo, pagos retenidos y descenso rechazado; CRUD/filtro/desactivación de gastos; motivo retroactivo; fechas reales y caja del día; alta y rol de limpieza; denegación de ruta/finanzas/cambio de reserva; transición de limpieza; restablecimiento y token de un solo uso. Una segunda pasada comprobó corrección de identidad por DNI, conservación de contacto, auditoría solo ante cambio, reutilización sin duplicar, rechazo de cruce de fechas, capacidad de habitación individual y rollback. Dos comprobaciones adicionales de la tarea ApiPeru aprobaron la validación de DNI y el error claro cuando falta token (38 en total).
- **Base local actual:** 17 tablas, 6 tipos, 24 habitaciones, 5 categorías, 5 productos y 2 usuarios. Cero reservas y cero huéspedes al cierre. Se respaldó antes de reponer catálogos. Las pruebas que crean huéspedes, reservas y gastos se ejecutaron en un clon, no en la base local.
- **Pendiente de verificación externa/manual:** respuesta real y caída de ApiPeru, SUNAT/Decolecta, apertura en Scene Builder, recorrido visual manual de todos los módulos, pruebas concurrentes con dos clientes y regresión funcional completa de tienda/comprobantes/reportes. No se afirma que esas pruebas hayan pasado. Las maquetas originales de almacén, reportes y parte de caja/usuarios siguen sin lógica operativa.

## 28–30. NetBeans, conservación y estado por módulo

El proyecto conserva `pom.xml`, `nbactions.xml`, paquetes y FXML y compiló usando el Maven instalado con NetBeans; es abrible como proyecto Maven. No se ejecutó un recorrido de toda la interfaz dentro de NetBeans. Se preservaron las rutas y archivos existentes, salvo los cinco controladores vacíos indicados. La carga de todos los FXML y las pruebas de integración reducen el riesgo de regresión, pero no equivalen a certificar cada flujo antiguo.

| Módulo | Estado |
|---|---|
| Login y roles | Implementado; hash, rol y navegación cargados; credenciales verificadas mediante DAO y `PasswordUtil`. |
| Reservas, personas y pagos | Implementado; asociación múltiple, capacidad, pagos y registro histórico probados en MySQL clon. |
| DNI / ApiPeru | Integración configurable implementada; llamada real sin verificar. |
| Cambio de habitación | Implementado y probado; orden de categorías requiere configuración administrativa en base local. |
| Gastos | Implementado y probado en MySQL clon. |
| Limpieza | Implementado y probado en MySQL clon. |
| Recuperación | Token administrado y un solo uso probados; sin correo SMTP. |
| Habitaciones, tienda, comprobantes, dashboard | Código y vistas conservados; carga FXML aprobada; recorrido completo manual pendiente. |
| Almacén, reportes y algunas vistas `nuevos/` | Maquetas originales conservadas; siguen sin operación real. |

## Checklist de entrega

| Verificación solicitada | Resultado |
|---|---|
| Compila e inicia | Sí: Maven y `App.start`. |
| Login, roles y Limpieza | Hash y restricciones probadas; flujo completo con clics pendiente. |
| Reservas, huésped existente y doble con dos personas | Persistencia y asociación verificadas en clon; reconciliación ApiPeru en vivo pendiente. |
| Capacidad, disponibilidad y cambio válido/inválido | Reglas en servicio; casos de cantidad, ascenso y descenso probados; concurrencia simultánea pendiente. |
| Precio y pagos | Recálculo y pagos retenidos probados. |
| Gastos y recuperación | CRUD, filtros, token y uso único probados. |
| Registro retroactivo y auditoría | Fechas, motivo y atribución de caja probados; historial/auditoría persistidos por transacción. |
| FXML, CSS y Scene Builder | 48/48 cargan; CSS común; Scene Builder directo no disponible. |
| SQL en DAO, `PreparedStatement`, transacciones, credenciales | Revisión estática aprobada; XAMPP local configurado; claves API privadas excluidas del ZIP. |
| Datos y funciones existentes | Recuentos originales conservados; regresión manual total pendiente. |

## Entrega y puesta en marcha

El ZIP actualizado contiene el proyecto Maven, las imágenes originales, `config.properties` local sin tokens privados, `ACCESOS_INICIALES.txt`, SQL completo, migración y `README.md`. No contiene respaldos de la base, `target/` ni datos personales. En la base local `hotel_san_antonio` de esta sesión ya existen las 17 tablas y las cuentas iniciales; **no ejecute allí la instalación completa ni la migración otra vez**. Para otro XAMPP, siga `README.md` y ajuste `db.password` si su usuario MySQL tiene clave.
