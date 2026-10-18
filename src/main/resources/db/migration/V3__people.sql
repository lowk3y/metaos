CREATE TABLE people
(
    id         UUID PRIMARY KEY,
    name       TEXT        NOT NULL,
    email      CITEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at TIMESTAMPTZ
);

-- Emails are optional, but unique among live people.
CREATE UNIQUE INDEX people_email_key ON people (email) WHERE deleted_at IS NULL;

CREATE TRIGGER people_set_updated_at
    BEFORE UPDATE
    ON people
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

-- A person has exactly one role per episode.
CREATE TABLE episode_people
(
    id         UUID PRIMARY KEY,
    episode_id UUID        NOT NULL REFERENCES episodes (id) ON DELETE CASCADE,
    person_id  UUID        NOT NULL REFERENCES people (id) ON DELETE CASCADE,
    role       TEXT        NOT NULL CHECK (role IN ('HOST', 'CO_HOST', 'GUEST')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT episode_people_episode_person_key UNIQUE (episode_id, person_id)
);

CREATE INDEX episode_people_person_id_idx ON episode_people (person_id);

CREATE TRIGGER episode_people_set_updated_at
    BEFORE UPDATE
    ON episode_people
    FOR EACH ROW
EXECUTE FUNCTION set_updated_at();
