BEGIN;

CREATE TABLE IF NOT EXISTS lapidary.search (
  table_name TEXT NOT NULL,
  search_name TEXT NOT NULL,
  user_name TEXT REFERENCES lapidary.user,
  options JSONB NOT NULL,
  PRIMARY KEY (table_name, search_name, user_name)
);

COMMIT;
