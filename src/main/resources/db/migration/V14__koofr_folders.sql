-- Koofr folders that are about an episode or a recording session, e.g. its recordings (KoofrFoldersMCP). Koofr has no
-- file IDs, so a link is a place (the ID of a Koofr mount shared with the bot) and a path in it. Moves made through
-- move_koofr_file update the links; a folder moved anywhere else leaves its links pointing at nothing.
CREATE TABLE episode_koofr_folders
(
    id         UUID PRIMARY KEY,
    episode_id UUID        NOT NULL REFERENCES episodes (id) ON DELETE CASCADE,
    place      TEXT        NOT NULL,
    path       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT episode_koofr_folders_episode_place_path_key UNIQUE (episode_id, place, path)
);

CREATE INDEX episode_koofr_folders_place_path_idx ON episode_koofr_folders (place, path);

CREATE TRIGGER episode_koofr_folders_audit
    AFTER INSERT OR UPDATE OR DELETE
    ON episode_koofr_folders
    FOR EACH ROW
EXECUTE FUNCTION audit_row_change();

CREATE TABLE session_koofr_folders
(
    id         UUID PRIMARY KEY,
    session_id UUID        NOT NULL REFERENCES sessions (id) ON DELETE CASCADE,
    place      TEXT        NOT NULL,
    path       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT session_koofr_folders_session_place_path_key UNIQUE (session_id, place, path)
);

CREATE INDEX session_koofr_folders_place_path_idx ON session_koofr_folders (place, path);

CREATE TRIGGER session_koofr_folders_audit
    AFTER INSERT OR UPDATE OR DELETE
    ON session_koofr_folders
    FOR EACH ROW
EXECUTE FUNCTION audit_row_change();
