BEGIN;

CREATE TABLE IF NOT EXISTS lapidary.user (user_name TEXT NOT NULL PRIMARY KEY, password_hash TEXT NOT NULL, options JSONB);

COMMIT;
