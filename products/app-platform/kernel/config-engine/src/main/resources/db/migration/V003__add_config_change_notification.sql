-- V003: PostgreSQL LISTEN/NOTIFY trigger for config hot-reload
-- Creates a trigger on config_entries that PG-notifies the 'config_changes' channel
-- whenever a config entry is inserted or updated. The notification payload is a JSON
-- object containing the changed entry's namespace and key so consumers can selectively
-- reload only what changed.

CREATE OR REPLACE FUNCTION notify_config_change()
    RETURNS trigger
    LANGUAGE plpgsql
AS $$
DECLARE
    payload TEXT;
BEGIN
    payload := json_build_object(
        'namespace', NEW.namespace,
        'key',       NEW.key,
        'level',     NEW.level,
        'level_id',  NEW.level_id
    )::text;

    PERFORM pg_notify('config_changes', payload);
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS config_change_notify ON config_entries;

CREATE TRIGGER config_change_notify
    AFTER INSERT OR UPDATE ON config_entries
    FOR EACH ROW
EXECUTE FUNCTION notify_config_change();

COMMENT ON FUNCTION notify_config_change() IS
    'Fires pg_notify(''config_changes'', json_payload) on every config_entries INSERT/UPDATE';

COMMENT ON TRIGGER config_change_notify ON config_entries IS
    'Notifies config_changes channel for hot-reload consumers on every config change';
