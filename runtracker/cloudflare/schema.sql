CREATE TABLE IF NOT EXISTS runs (
  id TEXT PRIMARY KEY,
  started_at INTEGER NOT NULL,
  finished_at INTEGER NOT NULL,
  distance_m REAL NOT NULL,
  duration_ms INTEGER NOT NULL,
  updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS track_points (
  run_id TEXT NOT NULL,
  time_ms INTEGER NOT NULL,
  lat REAL NOT NULL,
  lon REAL NOT NULL,
  accuracy REAL NOT NULL,
  PRIMARY KEY (run_id, time_ms),
  FOREIGN KEY (run_id) REFERENCES runs(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_track_points_run_time
ON track_points(run_id, time_ms);
