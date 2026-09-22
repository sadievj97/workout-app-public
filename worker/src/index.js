/**
 * CutCoach's proxy to the Anthropic API.
 *
 * The whole reason this exists is the API key. An Android app is a zip file
 * anyone can unzip, so a key shipped inside one is a key you have published.
 * Here it lives as a Cloudflare secret: set once from your machine, readable
 * only by this Worker at runtime, never in the repository and never in a log.
 *
 * The app authenticates with a different secret, which is the one that does
 * ship in the app. That is a deliberate trade: if it ever leaked, someone could
 * spend your Anthropic credit, and you would rotate it with one command. If the
 * API key leaked they would have your account.
 *
 * This Worker is deliberately dull. It checks the caller, checks the shape of
 * the request, forwards it, and returns the answer. It holds no state, keeps no
 * history and makes no decisions: the guardrails are enforced in the app, where
 * they can be unit tested.
 */

/** Only these models, so a typo or a tampered request cannot pick an expensive one. */
const ALLOWED_MODELS = new Set(['claude-sonnet-5', 'claude-haiku-4-5']);

/** A ceiling on one answer, so a runaway generation cannot run up a bill. */
const MAX_OUTPUT_TOKENS = 2048;

/** Anything larger is not a summary, and this proxy only forwards summaries. */
const MAX_REQUEST_BYTES = 32 * 1024;

export default {
  async fetch(request, env) {
    if (request.method !== 'POST') {
      return problem(405, 'Only POST is accepted.');
    }

    // ---- who is calling -------------------------------------------------
    const presented = request.headers.get('x-cutcoach-secret') || '';
    if (!env.SHARED_SECRET) {
      // Fail closed. An unset secret must never mean "let everyone in".
      return problem(500, 'The Worker has no SHARED_SECRET configured.');
    }
    if (!timingSafeEqual(presented, env.SHARED_SECRET)) {
      return problem(401, 'Not authorised.');
    }
    if (!env.ANTHROPIC_API_KEY) {
      return problem(500, 'The Worker has no ANTHROPIC_API_KEY configured.');
    }

    // ---- what they are asking for ---------------------------------------
    const raw = await request.text();
    if (raw.length > MAX_REQUEST_BYTES) {
      return problem(413, 'Request too large. This proxy forwards summaries, not records.');
    }

    let body;
    try {
      body = JSON.parse(raw);
    } catch {
      return problem(400, 'Body is not valid JSON.');
    }

    if (!ALLOWED_MODELS.has(body.model)) {
      return problem(400, `Model not allowed: ${String(body.model)}`);
    }
    if (!Array.isArray(body.messages) || body.messages.length === 0) {
      return problem(400, 'messages is required.');
    }

    const forwarded = {
      model: body.model,
      max_tokens: Math.min(body.max_tokens || MAX_OUTPUT_TOKENS, MAX_OUTPUT_TOKENS),
      messages: body.messages,
    };
    if (body.system) forwarded.system = body.system;
    if (body.temperature !== undefined) forwarded.temperature = body.temperature;

    // ---- forward ---------------------------------------------------------
    let upstream;
    try {
      upstream = await fetch('https://api.anthropic.com/v1/messages', {
        method: 'POST',
        headers: {
          'content-type': 'application/json',
          'anthropic-version': '2023-06-01',
          'x-api-key': env.ANTHROPIC_API_KEY,
        },
        body: JSON.stringify(forwarded),
      });
    } catch (e) {
      // The message, never the request: the request carried the key.
      return problem(502, 'Could not reach the Anthropic API.');
    }

    const text = await upstream.text();

    // Pass the answer through unchanged. Nothing is logged here: Worker logs
    // are readable in the dashboard, and a prompt or an answer in them would
    // put health summaries somewhere they were never meant to be.
    return new Response(text, {
      status: upstream.status,
      headers: { 'content-type': 'application/json' },
    });
  },
};

/** An error the app can read, in the same shape as everything else. */
function problem(status, message) {
  return new Response(JSON.stringify({ error: { message } }), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

/**
 * Compare two secrets without leaking their contents through timing.
 *
 * A plain `a === b` returns as soon as two characters differ, so the time it
 * takes reveals how much of a guess was right. Over many attempts that is
 * enough to recover a secret one character at a time. This always walks the
 * whole string.
 */
function timingSafeEqual(a, b) {
  if (typeof a !== 'string' || typeof b !== 'string') return false;
  if (a.length !== b.length) return false;
  let difference = 0;
  for (let i = 0; i < a.length; i++) {
    difference |= a.charCodeAt(i) ^ b.charCodeAt(i);
  }
  return difference === 0;
}
