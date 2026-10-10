CREATE TABLE IF NOT EXISTS legacy_account_associations (
 legacy_id INT PRIMARY KEY,
 account_id VARCHAR(36) NOT NULL UNIQUE,
 customer_id VARCHAR(36) NOT NULL,
 source_hash VARCHAR(64) NOT NULL,
 source_line INT NOT NULL,
 source_period VARCHAR(7) NOT NULL,
 source_balance DECIMAL(19,2) NOT NULL,
 decision_hash VARCHAR(64) NOT NULL,
 decision_reason VARCHAR(500) NOT NULL,
 actor VARCHAR(100) NOT NULL,
 created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
 CONSTRAINT fk_legacy_association FOREIGN KEY (account_id) REFERENCES modern_accounts(account_id),
 CONSTRAINT chk_legacy_reference CHECK (legacy_id > 0 AND source_line >= 2 AND source_balance >= 0)
);
