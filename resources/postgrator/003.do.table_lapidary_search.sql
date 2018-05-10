BEGIN;

CREATE TABLE IF NOT EXISTS lapidary.search (
  table_schema TEXT NOT NULL,
  table_name TEXT NOT NULL,
  search_name TEXT NOT NULL,
  user_name TEXT REFERENCES lapidary.user,
  options JSONB NOT NULL,
  PRIMARY KEY (table_schema, table_name, search_name),
  FOREIGN KEY (table_schema, table_name) REFERENCES pg_catalog.pg_class (relnamespace, relname)
);

COMMIT;
