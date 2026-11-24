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
no cambian el flujo de registro de orden, stock y correo. Usa órdenes nuevas
para la demo: Carrito EP5 toma los precios vigentes del catálogo al registrar
la orden; no recalcula las órdenes históricas. El simulador no está habilitado
para cobros reales.

Kafka recibe cada intento persistido en `p360.pagos.v1`; el publicador espera
el ACK y reintenta filas pendientes. Si se pierde el ACK tras la escritura,
Analytics deduplica por `eventoId`.

Configura `DATASOURCE_URL`, `DATASOURCE_USER`, `DATASOURCE_PASSWORD`,
`KAFKA_BOOTSTRAP_SERVERS`, `ORDENES_URL` y `CORS_ALLOWED_ORIGINS` por entorno.
Para pruebas con PostgreSQL, apunta `P360_TEST_POSTGRES_URL` a una **base
desechable distinta** y ejecuta `mvn test`. La prueba limpia la tabla de pagos
de esa base. Nunca utilices la de desarrollo o producción.

## WebPay Plus (modo integración)

| Método | Ruta | Comportamiento |
|---|---|---|
| `POST` | `/pagos/webpay/iniciar` | JWT + `{ "ordenId": "<UUID>" }`. Valida la orden contra ms-orders, aplica el antifraude y devuelve `{ "url", "token" }`. El monto lo calcula el backend. |
| `POST` | `/pagos/webpay/retorno` | Recibe el POST de Transbank con `token_ws` (o `TBK_TOKEN`), hace `commit`, actualiza el pago y redirige 302 a `FRONTEND_ORIGIN/pago/resultado`. Sin JWT a propósito. |
| `GET` | `/pagos/{ordenId}` | Sin cambios: devuelve el resultado auténtico del pago. |

Variables: `WEBPAY_COMMERCE_CODE` (default `597055555532`), `WEBPAY_API_KEY` (vacía =
`IntegrationApiKeys.WEBPAY`), `WEBPAY_RETURN_URL`, `FRONTEND_ORIGIN`, además de las del
simulador. Transbank exige `buyOrder` de máximo 26 caracteres: se genera como `TX` + 22
caracteres del UUID sin guiones. El `commit` comprueba que `buyOrder`, `sessionId` y
`amount` coincidan con el pago guardado antes de aprobarlo.

Tarjetas de prueba (RUT `11.111.111-1`, clave `123`, cualquier fecha futura):

| Tipo | Número | CVV | Resultado |
|---|---|---|---|
| VISA | 4051 8856 0044 6623 | 123 | Aprobada |
| Mastercard | 5186 0595 5959 0568 | 123 | Rechazada |
| AMEX | 3700 0000 0002 032 | 1234 | Aprobada |

El pago se guarda como `PENDIENTE` y el outbox no lo publica a Kafka hasta que el retorno
define `APROBADO`/`RECHAZADO`. Si Transbank no completa el commit, la fila queda pendiente
y el retorno redirige con `resultado=ERROR` (nunca se aprueba un pago no confirmado).
