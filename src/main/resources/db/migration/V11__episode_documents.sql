-- Google Docs in Ogrodje's Drive folder that are about an episode, e.g. its preparation doc (DocumentsMCP). Linked by
-- Drive file ID, which survives renames and moves; titles and URLs are read from Drive. A link is never updated.
CREATE TABLE episode_documents
(
    id         UUID PRIMARY KEY,
    episode_id UUID        NOT NULL REFERENCES episodes (id) ON DELETE CASCADE,
    file_id    TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT episode_documents_episode_file_key UNIQUE (episode_id, file_id)
);

CREATE INDEX episode_documents_file_id_idx ON episode_documents (file_id);

CREATE TRIGGER episode_documents_audit
    AFTER INSERT OR UPDATE OR DELETE
    ON episode_documents
    FOR EACH ROW
EXECUTE FUNCTION audit_row_change();
