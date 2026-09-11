interface Env {
  DB: D1Database;
  SYNC_TOKEN: string;
}

type TrackPoint = {
  timeMs: number;
  lat: number;
  lon: number;
  accuracy: number;
};

type RunPayload = {
  id: string;
  startedAt: number;
  finishedAt: number;
  distanceM: number;
  durationMs: number;
  points: TrackPoint[];
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8" },
  });
}

function authorized(request: Request, env: Env): boolean {
  if (!env.SYNC_TOKEN) return false;
  return request.headers.get("authorization") === `Bearer ${env.SYNC_TOKEN}`;
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    if (request.method === "GET" && url.pathname === "/health") {
      return json({ ok: true, service: "runtracker-sync" });
    }

    if (request.method !== "POST" || url.pathname !== "/api/runs") {
      return json({ error: "not_found" }, 404);
    }

    if (!authorized(request, env)) {
      return json({ error: "unauthorized" }, 401);
    }

    let body: RunPayload;
    try {
      body = await request.json<RunPayload>();
    } catch {
      return json({ error: "invalid_json" }, 400);
    }

    if (!body?.id || !Number.isFinite(body.startedAt) || !Number.isFinite(body.finishedAt) ||
        !Number.isFinite(body.distanceM) || !Number.isFinite(body.durationMs) || !Array.isArray(body.points)) {
      return json({ error: "invalid_payload" }, 400);
    }

    if (body.points.length > 100000) {
      return json({ error: "too_many_points" }, 413);
    }

    const statements: D1PreparedStatement[] = [
      env.DB.prepare(`
        INSERT INTO runs(id, started_at, finished_at, distance_m, duration_ms, updated_at)
        VALUES (?, ?, ?, ?, ?, ?)
        ON CONFLICT(id) DO UPDATE SET
          started_at=excluded.started_at,
          finished_at=excluded.finished_at,
          distance_m=excluded.distance_m,
          duration_ms=excluded.duration_ms,
          updated_at=excluded.updated_at
      `).bind(body.id, body.startedAt, body.finishedAt, body.distanceM, body.durationMs, Date.now()),
    ];

    for (const p of body.points) {
      if (!Number.isFinite(p.timeMs) || !Number.isFinite(p.lat) || !Number.isFinite(p.lon) || !Number.isFinite(p.accuracy)) {
        return json({ error: "invalid_point" }, 400);
      }
      statements.push(
        env.DB.prepare(`
          INSERT INTO track_points(run_id, time_ms, lat, lon, accuracy)
          VALUES (?, ?, ?, ?, ?)
          ON CONFLICT(run_id, time_ms) DO UPDATE SET
            lat=excluded.lat,
            lon=excluded.lon,
            accuracy=excluded.accuracy
        `).bind(body.id, p.timeMs, p.lat, p.lon, p.accuracy)
      );
    }

    await env.DB.batch(statements);
    return json({ ok: true, runId: body.id, points: body.points.length });
  },
};
