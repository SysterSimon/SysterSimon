export interface Env {
  DB: D1Database;
  APP_TOKEN: string;
}

type RunPayload = {
  id: string;
  startedAt: number;
  endedAt: number | null;
  distanceM: number;
  durationMs: number;
  status: string;
};

type PointPayload = {
  id: string;
  runId: string;
  recordedAt: number;
  latitude: number;
  longitude: number;
  accuracyM?: number | null;
  altitudeM?: number | null;
  speedMps?: number | null;
};

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json; charset=utf-8" },
  });
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (url.pathname === "/health") return json({ ok: true });

    const auth = request.headers.get("authorization");
    if (!env.APP_TOKEN || auth !== `Bearer ${env.APP_TOKEN}`) {
      return json({ error: "unauthorized" }, 401);
    }

    if (request.method !== "POST" || url.pathname !== "/v1/sync") {
      return json({ error: "not_found" }, 404);
    }

    let payload: { run?: RunPayload; points?: PointPayload[] };
    try {
      payload = await request.json();
    } catch {
      return json({ error: "invalid_json" }, 400);
    }

    const run = payload.run;
    const points = Array.isArray(payload.points) ? payload.points : [];
    if (!run?.id || !Number.isFinite(run.startedAt)) return json({ error: "invalid_run" }, 400);
    if (points.some((p) => !p.id || p.runId !== run.id || !Number.isFinite(p.latitude) || !Number.isFinite(p.longitude))) {
      return json({ error: "invalid_points" }, 400);
    }

    const now = Date.now();
    const statements: D1PreparedStatement[] = [
      env.DB.prepare(`
        INSERT INTO runs (id, started_at, ended_at, distance_m, duration_ms, status, updated_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(id) DO UPDATE SET
          ended_at = excluded.ended_at,
          distance_m = excluded.distance_m,
          duration_ms = excluded.duration_ms,
          status = excluded.status,
          updated_at = excluded.updated_at
      `).bind(run.id, run.startedAt, run.endedAt, run.distanceM, run.durationMs, run.status, now),
    ];

    for (const p of points) {
      statements.push(
        env.DB.prepare(`
          INSERT OR IGNORE INTO track_points
          (id, run_id, recorded_at, latitude, longitude, accuracy_m, altitude_m, speed_mps)
          VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        `).bind(
          p.id,
          p.runId,
          p.recordedAt,
          p.latitude,
          p.longitude,
          p.accuracyM ?? null,
          p.altitudeM ?? null,
          p.speedMps ?? null,
        )
      );
    }

    await env.DB.batch(statements);
    return json({ ok: true, acceptedPoints: points.length });
  },
};
