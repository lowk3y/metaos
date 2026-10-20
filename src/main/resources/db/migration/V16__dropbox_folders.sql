-- Dropbox folders that are about an episode or a recording session, e.g. its artwork (DropboxFoldersMCP). A link is a
-- place (the ID of a folder shared with the bot), the folder's Dropbox ID, which stays with it when it's moved or
-- renamed, also in the Dropbox app, and its path when it was linked.
CREATE TABLE episode_dropbox_folders
(
    id         UUID PRIMARY KEY,
    episode_id UUID        NOT NULL REFERENCES episodes (id) ON DELETE CASCADE,
    place      TEXT        NOT NULL,
    folder_id  TEXT        NOT NULL,
    path       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT episode_dropbox_folders_episode_folder_key UNIQUE (episode_id, folder_id)
);

CREATE TRIGGER episode_dropbox_folders_audit
    AFTER INSERT OR UPDATE OR DELETE
    ON episode_dropbox_folders
    FOR EACH ROW
EXECUTE FUNCTION audit_row_change();

CREATE TABLE session_dropbox_folders
(
    id         UUID PRIMARY KEY,
    session_id UUID        NOT NULL REFERENCES sessions (id) ON DELETE CASCADE,
    place      TEXT        NOT NULL,
    folder_id  TEXT        NOT NULL,
    path       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT session_dropbox_folders_session_folder_key UNIQUE (session_id, folder_id)
);

CREATE TRIGGER session_dropbox_folders_audit
    AFTER INSERT OR UPDATE OR DELETE
    ON session_dropbox_folders
    FOR EACH ROW
EXECUTE FUNCTION audit_row_change();
