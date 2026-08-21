import { Hono } from "hono";
import { HTTPException } from "hono/http-exception";
import { ColourBroadcaster } from "./durable/ColourBroadcaster";
import { authRouter } from "./routes/auth";
import { colourRouter } from "./routes/colours";
import type { Env } from "./types";

export { ColourBroadcaster };

const app = new Hono<{ Bindings: Env }>();

app.get("/", (c) => c.json({ success: true, service: "locolourtor" }));

app.get("/ws", (c) => {
  const id = c.env.BROADCASTER.idFromName("global");
  const broadcaster = c.env.BROADCASTER.get(id);
  return broadcaster.fetch(c.req.raw);
});

app.route("/auth", authRouter);
app.route("/", colourRouter);

app.notFound((c) =>
  c.json({ success: false, error: "Not found", code: 404 }, 404),
);

app.onError((err, c) => {
  if (err instanceof HTTPException) {
    return c.json(
      { success: false, error: err.message, code: err.status },
      err.status,
    );
  }
  console.error("Unhandled error:", err);
  return c.json(
    { success: false, error: "Internal server error", code: 500 },
    500,
  );
});

export default app;
