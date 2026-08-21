import type { MiddlewareHandler } from "hono";
import type { Env, JwtPayload } from "../types";

export type RateLimitOptions = {
  limit: number;
  windowSec: number;
  keyPrefix?: string;
  keyGenerator?: (c: any) => string;
};

export const rateLimiter = (
  options: RateLimitOptions,
): MiddlewareHandler<{
  Bindings: Env;
  Variables: { jwtPayload?: JwtPayload };
}> => {
  const {
    limit,
    windowSec,
    keyPrefix = "rl",
    keyGenerator = (c) => c.req.header("CF-Connecting-IP") ?? "unknown_ip",
  } = options;

  return async (c, next) => {
    if (!c.env.SESSION_KV) return next();

    const identifier = keyGenerator(c);
    const windowIndex = Math.floor(Date.now() / 1000 / windowSec);
    const key = `${keyPrefix}:${identifier}:${windowIndex}`;

    const raw = await c.env.SESSION_KV.get(key);
    const count = raw ? parseInt(raw, 10) : 0;

    if (count >= limit) {
      c.header("Retry-After", windowSec.toString());
      return c.json(
        { success: false, error: "Too many requests, calm down!", codee: 429 },
        429,
      );
    }

    await c.env.SESSION_KV.put(key, (count + 1).toString(), {
      expirationTtl: Math.max(60, windowSec * 2),
    });

    c.header("X-RateLimit-Limit", limit.toString());
    c.header(
      "X-RateLimit-Remaining",
      Math.max(0, limit - (count + 1)).toString(),
    );

    return next();
  };
};
