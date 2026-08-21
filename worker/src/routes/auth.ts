import { Hono } from "hono";
import { sign } from "hono/jwt";
import { isValidUuid } from "../lib/colour";
import { verifyMojangCertificate, verifyPlayerAction } from "../lib/crypto";
import { rateLimiter } from "../middleware/rateLimit";
import type { Env, JwtPayload } from "../types";

export const authRouter = new Hono<{ Bindings: Env }>();

authRouter.post(
  "/verify",
  rateLimiter({ limit: 10, windowSec: 60, keyPrefix: "rl:auth" }),
  async (c) => {
    let body: {
      uuid?: unknown;
      username?: unknown;
      timestamp?: unknown;
      publicKey?: unknown;
      expiresAt?: unknown;
      keySignature?: unknown;
      actionSignature?: unknown;
    };

    try {
      body = await c.req.json();
    } catch {
      return c.json(
        { success: false, error: "Invalid JSON body", code: 400 },
        400,
      );
    }

    const {
      uuid,
      username,
      timestamp,
      publicKey,
      expiresAt,
      keySignature,
      actionSignature,
    } = body;

    if (typeof uuid !== "string" || !isValidUuid(uuid))
      return c.json(
        { success: false, error: "Missing or invalid uuid", code: 400 },
        400,
      );
    else if (typeof username !== "string" || username.trim().length === 0)
      return c.json(
        { success: false, error: "Missing or invalid username", code: 400 },
        400,
      );
    else if (typeof timestamp !== "number" || isNaN(timestamp))
      return c.json(
        { success: false, error: "Missing or invalid timestamp", code: 400 },
        400,
      );
    else if (typeof publicKey !== "string" || publicKey.length === 0)
      return c.json(
        { success: false, error: "Missing or invalid publicKey", code: 400 },
        400,
      );
    else if (typeof expiresAt !== "number" || isNaN(expiresAt))
      return c.json(
        { success: false, error: "Missing or invalid expiresAt", code: 400 },
        400,
      );
    else if (typeof keySignature !== "string" || keySignature.length === 0)
      return c.json(
        {
          success: false,
          error: "Missing or invalid keySignature",
          code: 400,
        },
        400,
      );
    else if (
      typeof actionSignature !== "string" ||
      actionSignature.length === 0
    )
      return c.json(
        {
          success: false,
          error: "Missing or invalid actionSignature",
          code: 400,
        },
        400,
      );

    const now = Date.now();
    if (Math.abs(now - timestamp) > 300_000)
      return c.json(
        {
          error: "Invalid timestamp, check your system clock",
        },
        401,
      );

    if (expiresAt < now)
      return c.json(
        {
          error: "Your public key has expired, try restarting your game",
        },
        401,
      );

    const isMojangKeyValid = await verifyMojangCertificate(
      uuid,
      expiresAt,
      publicKey,
      keySignature,
    );

    if (!isMojangKeyValid)
      return c.json(
        {
          success: false,
          error: "Invalid Mojang public key certificate signature",
          code: 401,
        },
        401,
      );

    const challenge = `locolourtor-auth:${uuid}:${timestamp}`;
    const isActionValid = await verifyPlayerAction(
      challenge,
      publicKey,
      actionSignature,
    );

    if (!isActionValid)
      return c.json(
        {
          success: false,
          error: "Invalid action challenge signature",
          code: 401,
        },
        401,
      );

    const issuedAt = Math.floor(now / 1000);
    const expiresAtSec = issuedAt + 24 * 60 * 60;

    const payload: JwtPayload = {
      sub: uuid,
      name: username.trim(),
      iat: issuedAt,
      exp: expiresAtSec,
    };

    const token = await sign(payload, c.env.JWT_SECRET, "HS256");

    await c.env.SESSION_KV.put(`session:${uuid}`, "1", {
      expirationTtl: 24 * 60 * 60,
    });

    return c.json({ token, uuid });
  },
);
