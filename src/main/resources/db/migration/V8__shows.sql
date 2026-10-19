-- Series an episode is part of, as in the CMS (Hygraph), e.g. Zagon, META, Borderless, Bytes.
CREATE TABLE shows
(
    id         UUID PRIMARY KEY,
    name       CITEXT      NOT NULL,
    color      TEXT CHECK (color ~ '^#[0-9a-fA-F]{6}$'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

-- Names are unique among live shows only, so a soft-deleted show's name can be reused.
CREATE UNIQUE INDEX shows_name_key ON shows (name) WHERE deleted_at IS NULL;

CREATE TRIGGER shows_set_updated_at
    BEFORE UPDATE
    ON shows
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER shows_audit
    AFTER INSERT OR UPDATE OR DELETE
    ON shows
    FOR EACH ROW
EXECUTE FUNCTION audit_row_change();

-- A show has many episodes; an episode belongs to at most one show.
ALTER TABLE episodes
    ADD COLUMN show_id UUID REFERENCES shows (id) ON DELETE SET NULL;

CREATE INDEX episodes_show_id_idx ON episodes (show_id);
