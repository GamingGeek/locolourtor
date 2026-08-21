import type { MiddlewareHandler } from "hono";
import { Hono } from "hono";
import { jwt } from "hono/jwt";
import {
  getDefaultColour,
  isValidUuid,
  normaliseHex,
  parseUuidList,
} from "../lib/colour";
import { rateLimiter } from "../middleware/rateLimit";
import type { Env, JwtPayload } from "../types";

type ColourVariables = {
  jwtPayload: JwtPayload;
};

export const colourRouter = new Hono<{
  Bindings: Env;
  Variables: ColourVariables;
}>();

async function resolveColours(
  env: Env,
  uuids: string[],
): Promise<Record<string, string>> {
  const result: Record<string, string> = {};
  const kvPromises = uuids.map((uuid) => env.COLOURS_KV.get(uuid));
  const kvResults = await Promise.all(kvPromises);

  for (let i = 0; i < uuids.length; i++) {
    const uuid = uuids[i];
    const customColour = kvResults[i];
    if (customColour) result[uuid] = customColour;
    else result[uuid] = getDefaultColour(uuid);
  }

  return result;
}

colourRouter.get(
  "/colours",
  rateLimiter({ limit: 60, windowSec: 60, keyPrefix: "rl:colour_retrieve" }),
  async (c) => {
    const rawUuids = c.req.query("uuids");
    if (!rawUuids)
      return c.json(
        { success: false, error: "Missing uuids query", code: 400 },
        400,
      );

    const uuids = parseUuidList(rawUuids);
    if (uuids.length === 0)
      return c.json(
        { success: false, error: "No valid UUIDs provided", code: 400 },
        400,
      );
    else if (uuids.length > 50)
      return c.json(
        { success: false, error: "Maximum of 50 UUIDs per request", code: 400 },
        400,
      );

    const result = await resolveColours(c.env, uuids);
    c.header("Cache-Control", "public, max-age=30, stale-while-revalidate=60");
    return c.json(result);
  },
);

colourRouter.post(
  "/colours",
  rateLimiter({ limit: 60, windowSec: 60, keyPrefix: "rl:colour_retrieve" }),
  async (c) => {
    let body: { uuids?: unknown };
    try {
      body = await c.req.json();
    } catch {
      return c.json(
        { success: false, error: "Invalid JSON body", code: 400 },
        400,
      );
    }

    if (!Array.isArray(body?.uuids))
      return c.json(
        { success: false, error: "Missing or invalid uuids", code: 400 },
        400,
      );

    const uuids: string[] = [];
    for (const item of body.uuids)
      if (typeof item === "string" && isValidUuid(item))
        uuids.push(item.toLowerCase());

    if (uuids.length === 0)
      return c.json(
        { success: false, error: "No valid UUIDs provided", code: 400 },
        400,
      );
    else if (uuids.length > 50)
      return c.json(
        { success: false, error: "Maximum of 50 UUIDs per request", code: 400 },
        400,
      );

    const result = await resolveColours(c.env, uuids);
    c.header("Cache-Control", "public, max-age=30, stale-while-revalidate=60");
    return c.json(result);
  },
);

const jwtAuth: MiddlewareHandler<{
  Bindings: Env;
  Variables: ColourVariables;
}> = (c, next) => {
  const secret = c.env.JWT_SECRET;
  return jwt({ secret, alg: "HS256" })(c, next);
};

const modRateLimit = rateLimiter({
  limit: 10,
  windowSec: 60,
  keyPrefix: "rl:locolourtor",
  keyGenerator: (c) =>
    c.get("jwtPayload")?.sub ?? c.req.header("CF-Connecting-IP") ?? "unknown",
});

colourRouter.put("/colour", jwtAuth, modRateLimit, async (c) => {
  const uuid = c.get("jwtPayload").sub;

  let body: { colour?: unknown };
  try {
    body = await c.req.json();
  } catch {
    return c.json(
      { success: false, error: "Invalid JSON body", code: 400 },
      400,
    );
  }

  if (typeof body?.colour !== "string")
    return c.json(
      { success: false, error: "Missing or invalid colour", code: 400 },
      400,
    );

  let hex: string;
  try {
    hex = normaliseHex(body.colour);
  } catch (e: any) {
    return c.json(
      {
        success: false,
        error: "Failed to normalise provided colour",
        code: 400,
      },
      400,
    );
  }

  await c.env.COLOURS_KV.put(uuid, hex);

  try {
    const id = c.env.BROADCASTER.idFromName("global");
    const broadcaster = c.env.BROADCASTER.get(id);
    await broadcaster.fetch("http://internal/broadcast", {
      method: "POST",
      body: JSON.stringify({ uuid, colour: hex }),
      headers: { "Content-Type": "application/json" },
    });
  } catch (e) {
    console.error("Failed to broadcast colour update:", e);
  }

  return c.json({ ok: true, colour: hex });
});

colourRouter.delete("/colour", jwtAuth, modRateLimit, async (c) => {
  const uuid = c.get("jwtPayload").sub;
  await c.env.COLOURS_KV.delete(uuid);

  try {
    const id = c.env.BROADCASTER.idFromName("global");
    const broadcaster = c.env.BROADCASTER.get(id);
    await broadcaster.fetch("http://internal/broadcast", {
      method: "POST",
      body: JSON.stringify({ uuid, colour: null }),
      headers: { "Content-Type": "application/json" },
    });
  } catch (e) {
    console.error("Failed to broadcast colour reset:", e);
  }

  return c.json({ ok: true, message: "Colour reset to default" });
});
