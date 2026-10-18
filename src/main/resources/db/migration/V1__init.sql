CREATE EXTENSION IF NOT EXISTS citext;

create table if not exists episodes
(
    id         UUID PRIMARY KEY,
    code       CITEXT,
    title      TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE OR REPLACE FUNCTION set_updated_at()
    RETURNS TRIGGER
    LANGUAGE plpgsql AS
$$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

CREATE TRIGGER users_set_updated_at
    BEFORE UPDATE
    ON episodes
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();