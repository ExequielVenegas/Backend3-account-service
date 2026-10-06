# Account Service


Servicio de consultas y acceso a MySQL. Centraliza los datos que utilizan los
BFF; no implementa apertura/cierre de cuentas, pagos ni gestión de clientes,
servicios excluidos por indicación del profesor.

## Uso dentro del flujo

Batch carga MySQL → Account consulta los datos → el BFF adapta la respuesta.

| Endpoint GET interno | Uso |
|---|---|
| /internal/accounts/{accountId} | Detalle de cuenta para Web |
| /internal/accounts/{accountId}/summary | Resumen para Mobile |
| /internal/accounts/{accountId}/balance | Saldo para ATM |
| /internal/transactions/{transactionId} | Detalle de transacción |

Consulta las tablas calculo_intereses, estado_cuenta_anual y transacciones_diarias.
No expone una API de escritura en esta etapa.