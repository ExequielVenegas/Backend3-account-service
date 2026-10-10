CREATE TABLE IF NOT EXISTS account_payment_operations (
 payment_id VARCHAR(36) PRIMARY KEY,
 command_hash VARCHAR(64) NOT NULL,
 result_payload TEXT,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);
CREATE TABLE IF NOT EXISTS account_payment_ledger (
 payment_id VARCHAR(36) NOT NULL,
 account_id VARCHAR(36) NOT NULL,
 monto DECIMAL(19,2) NOT NULL,
 saldo_posterior DECIMAL(19,2) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 PRIMARY KEY(payment_id,account_id),
 CONSTRAINT fk_ledger_operation FOREIGN KEY(payment_id) REFERENCES account_payment_operations(payment_id),
 CONSTRAINT fk_ledger_account FOREIGN KEY(account_id) REFERENCES modern_accounts(account_id)
);
CREATE TABLE IF NOT EXISTS account_payment_outbox (
 event_id VARCHAR(36) PRIMARY KEY,
 payload TEXT NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 published_at TIMESTAMP(6),
 CONSTRAINT fk_account_outbox FOREIGN KEY(event_id) REFERENCES account_payment_operations(payment_id)
);
