-- Says on channel metaos_changes which table changed, for the front-end's pages to show it right away (Changes.kt).
-- Every change the audit trail records, so a table with an audit trigger is in it too, and changes made any other way
-- (psql, migrations) as well. Postgres sends notifications when the transaction commits, to every connection listening,
-- i.e. to every pod, and only once for the same table in one transaction.
CREATE OR REPLACE FUNCTION notify_change()
    RETURNS TRIGGER
    LANGUAGE plpgsql AS
$$
BEGIN
    PERFORM pg_notify('metaos_changes', NEW.table_name);
    RETURN NULL;
END;
$$;

CREATE TRIGGER audit_log_notify
    AFTER INSERT
    ON audit_log
    FOR EACH ROW
EXECUTE FUNCTION notify_change();
