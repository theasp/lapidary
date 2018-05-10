BEGIN;

CREATE TABLE IF NOT EXISTS lapidary.table_settings (
  table_schema TEXT NOT NULL,
  table_name TEXT NOT NULL,
  settings JSONB NOT NULL,
  PRIMARY KEY (table_schema, table_name),
  FOREIGN KEY (table_schema, table_name) REFERENCES pg_catalog.pg_class (relnamespace, relname) ON DELETE CASCADE
);

COMMIT;
