# Pedidos360 · Pagos de prueba

Inicia PostgreSQL con `docker compose up -d` en este repositorio y Kafka con
`docker compose up -d` en `pedidos360-ms-admin/docker/kafka-cluster`. Inicia
ms-orders en `8084` y este servicio con `mvn spring-boot:run`.

| Método | Ruta | Comportamiento |
|---|---|---|
| `POST` | `/pagos` | JWT + `{ "ordenId": "<UUID>", "tarjetaDemo": "APROBACION" }`. Se consulta la orden del usuario y se usa su total. |
| `GET` | `/pagos/{ordenId}` | JWT del dueño: devuelve el pago aprobado, el último intento, o 404. |

Usa `APROBACION` o `RECHAZO`. No admite números de tarjeta, CVV, monto del
cliente ni cobros reales. El cuarto intento procesado contra la misma tarjeta
de prueba en una ventana móvil de diez segundos se bloquea y registra una
alerta. Los bloqueos no prolongan la ventana. Si la orden ya está pagada, la
respuesta devuelve la primera aprobación sin volver a procesarla. Estos pagos
no cambian el flujo anterior de registro de orden, stock y correo. No conectes
una pasarela de cobro real sin recalcular el precio desde el catálogo en checkout;
los datos de las líneas del carrito anterior no son una fuente confiable de precio.

Kafka recibe cada intento persistido en `p360.pagos.v1`; el publicador espera
el ACK y reintenta filas pendientes. Si se pierde el ACK tras la escritura,
Analytics deduplica por `eventoId`.

Configura `DATASOURCE_URL`, `DATASOURCE_USER`, `DATASOURCE_PASSWORD`,
`KAFKA_BOOTSTRAP_SERVERS`, `ORDENES_URL` y `CORS_ALLOWED_ORIGINS` por entorno.
Para pruebas con PostgreSQL, apunta `P360_TEST_POSTGRES_URL` a una **base
desechable distinta** y ejecuta `mvn test`. La prueba limpia la tabla de pagos
de esa base. Nunca utilices la de desarrollo o producción.
