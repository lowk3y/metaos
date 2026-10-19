-- Google Docs in Ogrodje's Drive folder that are about a recording session, e.g. its recording day plan
-- (DocumentsMCP). Like episode_documents: linked by Drive file ID, titles and URLs are read from Drive, a link is never
-- updated.
CREATE TABLE session_documents
(
    id         UUID PRIMARY KEY,
    session_id UUID        NOT NULL REFERENCES sessions (id) ON DELETE CASCADE,
    file_id    TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT session_documents_session_file_key UNIQUE (session_id, file_id)
);

CREATE INDEX session_documents_file_id_idx ON session_documents (file_id);

CREATE TRIGGER session_documents_audit
    AFTER INSERT OR UPDATE OR DELETE
    ON session_documents
    FOR EACH ROW
EXECUTE FUNCTION audit_row_change();
