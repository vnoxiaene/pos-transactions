CREATE TABLE IF NOT EXISTS transactions (
    id              UUID            NOT NULL,
    transaction_id  VARCHAR(64)     NOT NULL,
    nsu             VARCHAR(50)     NOT NULL,
    terminal_id     VARCHAR(50)     NOT NULL,
    amount          NUMERIC(19, 2)  NOT NULL,
    status          VARCHAR(20)     NOT NULL CHECK (status IN ('AUTHORIZED', 'CONFIRMED', 'VOIDED')),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version         BIGINT          NOT NULL DEFAULT 0,

    CONSTRAINT pk_transactions PRIMARY KEY (id),
    CONSTRAINT uq_transaction_id UNIQUE (transaction_id),
    CONSTRAINT uq_terminal_nsu UNIQUE (terminal_id, nsu)
);

CREATE INDEX IF NOT EXISTS idx_transactions_transaction_id ON transactions (transaction_id);
CREATE INDEX IF NOT EXISTS idx_transactions_terminal_nsu ON transactions (terminal_id, nsu);
CREATE INDEX IF NOT EXISTS idx_transactions_status ON transactions (status);
