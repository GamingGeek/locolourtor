import type { MiddlewareHandler } from "hono";
import { jwt } from "hono/jwt";
import type { Env } from "../types";

export const createAuthMiddleware = (): MiddlewareHandler<{
  Bindings: Env;
}> => {
  return async (c, next) => {
    const secret = c.env.JWT_SECRET;
    return jwt({ secret, alg: "HS256" })(c, next);
  };
};
