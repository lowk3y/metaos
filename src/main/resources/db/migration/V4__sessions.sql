CREATE TABLE sessions
(
    id         UUID PRIMARY KEY,
    date       DATE        NOT NULL,
    location   TEXT,
    address    TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

CREATE TRIGGER sessions_set_updated_at
    BEFORE UPDATE
    ON sessions
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

-- A session has many episodes; an episode belongs to at most one session.
ALTER TABLE episodes
    ADD COLUMN session_id UUID REFERENCES sessions (id) ON DELETE SET NULL;

CREATE INDEX episodes_session_id_idx ON episodes (session_id);
