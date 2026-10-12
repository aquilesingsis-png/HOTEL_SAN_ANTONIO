# Estado del proyecto — Hotel San Antonio

Este resumen describe cómo está el proyecto hoy. Las instrucciones para iniciarlo están en `README.md`.

## Contenido

- 44 pantallas FXML, organizadas en una carpeta por módulo (`Reserva`, `habitaciones`, `carrito`, `comprobante`, `dashboard`, `login`, `principal` y `nuevos` con almacén, caja, días pasados, reportes, respaldo y usuarios). Los estilos van dentro de cada FXML y de `EstiloGlobal`; no hay archivos `.css`.
- Código Java en cinco capas: `controlador`, `servicio` (reglas de negocio y permisos), `dao` (consultas JDBC), `modelo` y `util`.
- Base de datos `hotel_san_antonio` con 24 tablas, creada con un único archivo: `sql/INSTALACION_COMPLETA_XAMPP.sql` (instalación nueva y, en su bloque final, actualización de una base existente).

## Reglas de negocio principales

- Una reserva nueva se hace desde mañana, con un adelanto de al menos 50 %; desde Habitaciones solo se recibe a quien llega hoy, con pago completo. El total sale de la tarifa de la habitación por las noches.
- Venta de tienda, comprobante y check-in se pueden corregir sin borrar nada: quedan anulados con quién, cuándo y por qué. Una venta ya contada en un cierre de caja confirmado no se anula.
- El cierre de caja se hace por turnos (mañana, tarde, noche): el sistema calcula lo cobrado y pagado desde el cierre anterior, y la caja chica pasa de un turno al siguiente.
- Un pago equivocado se corrige con un movimiento manual del administrador, no se anula.

## Seguridad

- Contraseñas con PBKDF2, bloqueo temporal por intentos fallidos y recuperación con código de un solo uso.
- Los tokens de las APIs y la clave de MySQL van en `config.properties`, que no se sube a Git.
- Las claves de las cuentas iniciales son de prueba y deben cambiarse antes de entregar.

## Lo que no está en el proyecto

- No hay pruebas automáticas (`src/test`).
- Las consultas a RENIEC, SUNAT y Decolecta dependen de Internet y de tokens vigentes.
