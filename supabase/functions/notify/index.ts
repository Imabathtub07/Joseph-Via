// ============================================================================
// DISCOURSE — Notify Edge Function (email nudges)
//
// Deploy with: supabase functions deploy notify
// Secrets:     supabase secrets set RESEND_API_KEY=re_...
//              supabase secrets set NOTIFY_FROM="Discourse <notify@yourdomain.com>"
//              supabase secrets set APP_URL=https://josephvia.com
//
// IMPORTANT: Resend's shared sender (onboarding@resend.dev) can only deliver to
// the email address that owns the Resend account. To email anyone else (e.g. an
// opponent) you must verify a domain in Resend and set NOTIFY_FROM to it.
// Without that, every nudge to another person is rejected (HTTP 403).
//
// Changes vs v1: HTML-escapes user-controlled text (topic / name), reports
// whether a failure is worth retrying, and logs every failure.
// ============================================================================

import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

const RESEND_API_KEY = Deno.env.get("RESEND_API_KEY");
const NOTIFY_FROM = Deno.env.get("NOTIFY_FROM") || "Discourse <onboarding@resend.dev>";
const APP_URL = Deno.env.get("APP_URL") || "";
const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_ROLE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { ...CORS, "Content-Type": "application/json" },
  });
}

const esc = (s: string) =>
  String(s ?? "").replace(/[&<>"']/g, (c) =>
    ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]!)
  );

type Kind = "your_turn" | "debate_started" | "debate_finished";

function buildEmail(kind: Kind, rawTopic: string, rawFrom: string, link: string) {
  const topic = esc(rawTopic);
  const fromName = esc(rawFrom);
  const btn = link
    ? `<p style="margin:24px 0;"><a href="${esc(link)}" style="background:#ff2e88;color:#ffffff;text-decoration:none;padding:12px 24px;font-weight:bold;border-radius:4px;display:inline-block;">Open the debate →</a></p>`
    : "";
  const foot = `<p style="color:#888;font-size:12px;">DISCOURSE // mediated debate · You're getting this because you're a participant in this debate.</p>`;
  switch (kind) {
    case "your_turn":
      return {
        subject: `🎯 Your move: "${rawTopic}"`,
        html: `<p><strong>${fromName}</strong> just made their point in <em>"${topic}"</em>.</p><p>The floor is yours — the clock is running.</p>${btn}${foot}`,
      };
    case "debate_started":
      return {
        subject: `⚔ It's on: "${rawTopic}"`,
        html: `<p><strong>${fromName}</strong> accepted your challenge and made their opening argument on <em>"${topic}"</em>.</p><p>The debate is live, and it's your turn.</p>${btn}${foot}`,
      };
    case "debate_finished":
      return {
        subject: `🏁 Debate finished: "${rawTopic}"`,
        html: `<p>Your debate with <strong>${fromName}</strong> on <em>"${topic}"</em> has ended.</p><p>The summary card is ready.</p>${btn}${foot}`,
      };
  }
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: CORS });

  try {
    if (!RESEND_API_KEY) {
      console.warn("notify: RESEND_API_KEY is not set — no emails can be sent");
      return jsonResponse({ sent: false, reason: "email not configured", retryable: false });
    }

    const authHeader = req.headers.get("Authorization") || "";
    const jwt = authHeader.replace(/^Bearer\s+/i, "");
    const admin = createClient(SUPABASE_URL, SERVICE_ROLE_KEY);
    const { data: userData, error: userErr } = await admin.auth.getUser(jwt);
    if (userErr || !userData?.user?.email) return jsonResponse({ error: "unauthorized" }, 401);
    const callerEmail = userData.user.email.toLowerCase();

    const { debateId, kind, fromName } = await req.json();
    if (!debateId || !["your_turn", "debate_started", "debate_finished"].includes(kind)) {
      return jsonResponse({ error: "missing or invalid fields (debateId, kind)" }, 400);
    }

    const { data: debate, error: dbErr } = await admin
      .from("debates")
      .select("id, topic, creator_email, opponent_email")
      .eq("id", debateId)
      .maybeSingle();
    if (dbErr || !debate) return jsonResponse({ error: "debate not found" }, 404);

    const creator = (debate.creator_email || "").toLowerCase();
    const opponent = (debate.opponent_email || "").toLowerCase();
    if (callerEmail !== creator && callerEmail !== opponent) {
      return jsonResponse({ error: "not a participant" }, 403);
    }
    const recipient = callerEmail === creator ? opponent : creator;

    const link = APP_URL ? `${APP_URL}?debate=${debate.id}` : "";
    const senderName = (typeof fromName === "string" && fromName.trim())
      ? fromName.trim().slice(0, 60)
      : callerEmail.split("@")[0];
    const email = buildEmail(kind as Kind, debate.topic, senderName, link);

    const resp = await fetch("https://api.resend.com/emails", {
      method: "POST",
      headers: { "Content-Type": "application/json", "Authorization": `Bearer ${RESEND_API_KEY}` },
      body: JSON.stringify({ from: NOTIFY_FROM, to: [recipient], subject: email.subject, html: email.html }),
    });

    if (!resp.ok) {
      const errText = await resp.text();
      console.error("notify: Resend rejected email", resp.status, errText);
      // 429/5xx are transient; 4xx (e.g. unverified domain) won't fix themselves.
      return jsonResponse({
        sent: false,
        reason: `resend ${resp.status}`,
        retryable: resp.status === 429 || resp.status >= 500,
      });
    }
    return jsonResponse({ sent: true });
  } catch (e) {
    console.error("notify: unexpected error", e);
    return jsonResponse({ error: e instanceof Error ? e.message : "unknown error" }, 500);
  }
});
