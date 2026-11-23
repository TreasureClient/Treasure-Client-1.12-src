/**
 * Treasure Client cosmetics server (a Cloudflare Worker with a D1 database bound as DB).
 *
 * POST /v1/looks  {"names": ["Steve", ...]}             -> {"looks": {"steve": "c=frost;a=halo;..."}}
 * POST /v1/set    {"name": "Steve", "look": "...", "token": "<key or null>"}
 *                 -> {"ok": true, "token": "<new key>"} the first time a name is shared (the client keeps the key),
 *                    {"ok": true} after that, or 409 {"error": "taken"} if another browser holds that name's key.
 *                    A name nobody has updated for 30 days can be taken over.
 *
 * Only a name and a short look line are stored (no IPs, no accounts). Bodies are sent as plain text,
 * so browsers don't need a CORS preflight.
 */
const NAME = /^[A-Za-z0-9_]{1,16}$/;
const LOOK = /^[a-z0-9=;.]{1,300}$/;
const TAKEOVER_MS = 30 * 24 * 3600 * 1000;
const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
  'Access-Control-Allow-Headers': 'content-type',
  'Access-Control-Max-Age': '86400',
};

// a simple per-address speed limit (per server instance; enough to stop runaway clients)
const hits = new Map();
function limited(ip, kind, max) {
  const now = Date.now(), key = kind + ip;
  let h = hits.get(key);
  if (!h || now - h.start > 60000) { h = { start: now, n: 0 }; hits.set(key, h); }
  if (hits.size > 5000) hits.clear();
  return ++h.n > max;
}

function json(obj, status = 200) {
  return new Response(JSON.stringify(obj), { status, headers: { ...CORS, 'content-type': 'application/json' } });
}

async function sha256(s) {
  const d = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(s));
  return [...new Uint8Array(d)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

function newToken() {
  const b = new Uint8Array(32);
  crypto.getRandomValues(b);
  return [...b].map((x) => x.toString(16).padStart(2, '0')).join('');
}

export default {
  async fetch(req, env) {
    if (req.method === 'OPTIONS') return new Response(null, { headers: CORS });
    const url = new URL(req.url);
    if (req.method === 'GET' && url.pathname === '/') return json({ ok: true, service: 'Treasure Client cosmetics' });
    if (req.method !== 'POST') return json({ error: 'not found' }, 404);
    const ip = req.headers.get('cf-connecting-ip') || '?';
    const text = await req.text();
    if (text.length > 4000) return json({ error: 'too big' }, 413);
    let body;
    try { body = JSON.parse(text); } catch (e) { return json({ error: 'bad request' }, 400); }
    if (!body || typeof body !== 'object') return json({ error: 'bad request' }, 400);

    if (url.pathname === '/v1/looks') {
      if (limited(ip, 'g', 120)) return json({ error: 'slow down' }, 429);
      const list = Array.isArray(body.names) ? body.names : [];
      const names = [...new Set(list.filter((n) => typeof n === 'string' && NAME.test(n)).map((n) => n.toLowerCase()))].slice(0, 50);
      if (!names.length) return json({ looks: {} });
      const q = 'SELECT name, look FROM looks WHERE name IN (' + names.map(() => '?').join(',') + ')';
      const { results } = await env.DB.prepare(q).bind(...names).all();
      const looks = {};
      for (const r of results) looks[r.name] = r.look;
      return json({ looks });
    }

    if (url.pathname === '/v1/set') {
      if (limited(ip, 's', 20)) return json({ error: 'slow down' }, 429);
      const { name, look, token } = body;
      if (typeof name !== 'string' || !NAME.test(name) || typeof look !== 'string' || !LOOK.test(look)) {
        return json({ error: 'bad request' }, 400);
      }
      const key = name.toLowerCase(), now = Date.now();
      const row = await env.DB.prepare('SELECT token_hash, updated FROM looks WHERE name = ?').bind(key).first();
      const given = typeof token === 'string' && /^[a-f0-9]{64}$/.test(token) ? await sha256(token) : null;
      const owner = row && given !== null && row.token_hash === given;
      if (row && !owner && now - row.updated < TAKEOVER_MS) return json({ error: 'taken' }, 409);
      let fresh = null, hash = given;
      if (!owner) { fresh = newToken(); hash = await sha256(fresh); }
      await env.DB.prepare(
        'INSERT INTO looks (name, look, token_hash, updated) VALUES (?, ?, ?, ?) ' +
        'ON CONFLICT(name) DO UPDATE SET look = excluded.look, token_hash = excluded.token_hash, updated = excluded.updated'
      ).bind(key, look, hash, now).run();
      return json(fresh ? { ok: true, token: fresh } : { ok: true });
    }

    return json({ error: 'not found' }, 404);
  },
};
