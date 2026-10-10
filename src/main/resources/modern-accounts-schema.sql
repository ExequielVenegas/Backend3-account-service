CREATE TABLE IF NOT EXISTS modern_accounts (
 account_id VARCHAR(36) PRIMARY KEY,
 customer_id VARCHAR(36) NOT NULL,
 creation_hash VARCHAR(64) NOT NULL,
 created_by VARCHAR(100) NOT NULL,
 tipo VARCHAR(20) NOT NULL,
 alias VARCHAR(80) NOT NULL,
 moneda VARCHAR(3) NOT NULL DEFAULT 'CLP',
 saldo DECIMAL(19,2) NOT NULL DEFAULT 0,
 estado VARCHAR(20) NOT NULL DEFAULT 'ACTIVA',
 version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT chk_modern_balance CHECK (saldo >= 0),
 CONSTRAINT chk_modern_state CHECK (estado IN ('ACTIVA','BLOQUEADA','CERRADA')),
 CONSTRAINT chk_modern_type CHECK (tipo IN ('AHORRO','CORRIENTE')),
 CONSTRAINT chk_modern_closed CHECK (estado <> 'CERRADA' OR saldo = 0)
);
CREATE TABLE IF NOT EXISTS modern_account_audit (
 audit_id VARCHAR(36) PRIMARY KEY,
 account_id VARCHAR(36) NOT NULL,
 actor VARCHAR(100) NOT NULL,
 accion VARCHAR(30) NOT NULL,
 version BIGINT NOT NULL,
 occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_modern_audit_account FOREIGN KEY (account_id) REFERENCES modern_accounts(account_id)
);
