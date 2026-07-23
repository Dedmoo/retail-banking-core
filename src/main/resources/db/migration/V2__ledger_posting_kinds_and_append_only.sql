-- Opening balances post as ledger CREDITS without a transfer row.
-- Transfer posts keep transfer_id set. Append-only: no UPDATE/DELETE on ledger.

ALTER TABLE ledger_entries
    ALTER COLUMN transfer_id DROP NOT NULL;

ALTER TABLE ledger_entries
    ADD COLUMN posting_kind VARCHAR(20) NOT NULL DEFAULT 'TRANSFER';

UPDATE ledger_entries
SET posting_kind = 'TRANSFER'
WHERE posting_kind IS NULL OR posting_kind = 'TRANSFER';

ALTER TABLE ledger_entries
    ALTER COLUMN posting_kind DROP DEFAULT;

ALTER TABLE ledger_entries
    ADD CONSTRAINT chk_ledger_posting_kind CHECK (posting_kind IN ('TRANSFER', 'OPENING'));

ALTER TABLE ledger_entries
    ADD CONSTRAINT chk_ledger_transfer_ref CHECK (
        (posting_kind = 'TRANSFER' AND transfer_id IS NOT NULL)
        OR (posting_kind = 'OPENING' AND transfer_id IS NULL)
    );

CREATE OR REPLACE FUNCTION forbid_ledger_mutation()
RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'ledger_entries is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_entries_no_update
    BEFORE UPDATE ON ledger_entries
    FOR EACH ROW
    EXECUTE PROCEDURE forbid_ledger_mutation();

CREATE TRIGGER trg_ledger_entries_no_delete
    BEFORE DELETE ON ledger_entries
    FOR EACH ROW
    EXECUTE PROCEDURE forbid_ledger_mutation();

ALTER TABLE accounts
    ADD CONSTRAINT chk_accounts_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED'));
