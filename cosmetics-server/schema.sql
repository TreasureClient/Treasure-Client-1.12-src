-- Treasure Client cosmetics: one row per player name (lower case).
CREATE TABLE IF NOT EXISTS looks (
  name TEXT PRIMARY KEY,
  look TEXT NOT NULL,
  token_hash TEXT NOT NULL,
  updated INTEGER NOT NULL
);
