BEGIN;

CREATE TABLE IF NOT EXISTS lapidary.table_options (
  table_schema TEXT NOT NULL,
  table_name TEXT NOT NULL,
  options JSONB NULL,
  PRIMARY KEY (table_schema, table_name)
);

COMMIT;
