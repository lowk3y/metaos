ALTER TRIGGER users_set_updated_at ON episodes RENAME TO episodes_set_updated_at;

ALTER TABLE episodes
    ADD COLUMN deleted_at TIMESTAMPTZ;

-- Codes are unique among live episodes only, so a soft-deleted code can be reused.
CREATE UNIQUE INDEX episodes_code_key ON episodes (code) WHERE deleted_at IS NULL;
