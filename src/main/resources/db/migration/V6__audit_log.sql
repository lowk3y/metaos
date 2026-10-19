-- Every insert, update and delete on the domain tables, and who made it.
-- The app tags each read-write transaction with the signed-in user, their OAuth client and the MCP tool they called
-- (see AuditingTransactionManager); changes made any other way are recorded without them.
CREATE TABLE audit_log
(
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    table_name TEXT        NOT NULL,
    row_id     UUID        NOT NULL,
    operation  TEXT        NOT NULL CHECK (operation IN ('INSERT', 'UPDATE', 'DELETE')),
    old_row    JSONB,
    new_row    JSONB,
    actor_id   TEXT, -- Keycloak user ID (sub)
    actor      TEXT, -- username at the time of the change
    client     TEXT, -- OAuth client (azp), i.e. the MCP client that made the change
    tool       TEXT, -- MCP tool
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX audit_log_row_id_idx ON audit_log (row_id);

CREATE OR REPLACE FUNCTION audit_row_change()
    RETURNS TRIGGER
    LANGUAGE plpgsql AS
$$
DECLARE
    old_data JSONB := CASE WHEN TG_OP <> 'INSERT' THEN to_jsonb(OLD) END;
    new_data JSONB := CASE WHEN TG_OP <> 'DELETE' THEN to_jsonb(NEW) END;
BEGIN
    -- set_updated_at bumps updated_at even when nothing else changed
    IF TG_OP = 'UPDATE' AND old_data - 'updated_at' = new_data - 'updated_at' THEN
        RETURN NULL;
    END IF;

    -- The settings are transaction-local, but read as '' instead of NULL once a connection has had them set
    INSERT INTO audit_log (table_name, row_id, operation, old_row, new_row, actor_id, actor, client, tool)
    VALUES (TG_TABLE_NAME, (coalesce(new_data, old_data) ->> 'id')::uuid, TG_OP, old_data, new_data,
            nullif(current_setting('metaos.actor_id', true), ''),
            nullif(current_setting('metaos.actor', true), ''),
            nullif(current_setting('metaos.client', true), ''),
            nullif(current_setting('metaos.tool', true), ''));
    RETURN NULL;
END;
$$;

CREATE TRIGGER episodes_audit
    AFTER INSERT OR UPDATE OR DELETE
    ON episodes
    FOR EACH ROW
EXECUTE FUNCTION audit_row_change();

CREATE TRIGGER people_audit
    AFTER INSERT OR UPDATE OR DELETE
    ON people
    FOR EACH ROW
EXECUTE FUNCTION audit_row_change();

CREATE TRIGGER episode_people_audit
    AFTER INSERT OR UPDATE OR DELETE
    ON episode_people
    FOR EACH ROW
EXECUTE FUNCTION audit_row_change();

CREATE TRIGGER sessions_audit
    AFTER INSERT OR UPDATE OR DELETE
    ON sessions
    FOR EACH ROW
EXECUTE FUNCTION audit_row_change();
