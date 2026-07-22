CREATE TABLE app_users (
    id              UUID PRIMARY KEY,
    username        VARCHAR(50) NOT NULL UNIQUE,
    email           VARCHAR(255) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE accounts (
    id              UUID PRIMARY KEY,
    owner_id        UUID NOT NULL REFERENCES app_users (id),
    account_number  VARCHAR(20) NOT NULL UNIQUE,
    currency        VARCHAR(3) NOT NULL,
    balance         NUMERIC(19, 4) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_accounts_balance_non_negative CHECK (balance >= 0)
);

CREATE INDEX idx_accounts_owner_id ON accounts (owner_id);

CREATE TABLE transfers (
    id                  UUID PRIMARY KEY,
    idempotency_key     VARCHAR(100) NOT NULL,
    initiated_by        UUID NOT NULL REFERENCES app_users (id),
    from_account_id     UUID NOT NULL REFERENCES accounts (id),
    to_account_id       UUID NOT NULL REFERENCES accounts (id),
    amount              NUMERIC(19, 4) NOT NULL,
    currency            VARCHAR(3) NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_transfers_idempotency UNIQUE (idempotency_key, initiated_by),
    CONSTRAINT chk_transfers_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_transfers_distinct_accounts CHECK (from_account_id <> to_account_id)
);

CREATE INDEX idx_transfers_from_account ON transfers (from_account_id);
CREATE INDEX idx_transfers_to_account ON transfers (to_account_id);

CREATE TABLE ledger_entries (
    id              UUID PRIMARY KEY,
    transfer_id     UUID NOT NULL REFERENCES transfers (id),
    account_id      UUID NOT NULL REFERENCES accounts (id),
    entry_type      VARCHAR(10) NOT NULL,
    amount          NUMERIC(19, 4) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_ledger_entry_type CHECK (entry_type IN ('DEBIT', 'CREDIT')),
    CONSTRAINT chk_ledger_amount_positive CHECK (amount > 0)
);

CREATE INDEX idx_ledger_entries_transfer_id ON ledger_entries (transfer_id);
CREATE INDEX idx_ledger_entries_account_id ON ledger_entries (account_id);
