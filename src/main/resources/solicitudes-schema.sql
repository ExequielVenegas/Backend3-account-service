CREATE TABLE IF NOT EXISTS solicitudes_estado_cuenta (
    solicitud_id VARCHAR(36) NOT NULL PRIMARY KEY,
    cliente VARCHAR(100) NOT NULL,
    cuenta_id INT NOT NULL,
    anio INT NOT NULL,
    estado VARCHAR(20) NOT NULL,
    recibida_en TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
