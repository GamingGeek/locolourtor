import { DurableObject } from "cloudflare:workers";
import type { Env } from "../types";

export class ColourBroadcaster extends DurableObject<Env> {
  constructor(ctx: DurableObjectState, env: Env) {
    super(ctx, env);
  }

  async fetch(request: Request): Promise<Response> {
    const url = new URL(request.url);

    if (request.headers.get("Upgrade") === "websocket") {
      const pair = new WebSocketPair();
      const [client, server] = Object.values(pair);

      this.ctx.acceptWebSocket(server);

      return new Response(null, {
        status: 101,
        webSocket: client,
      });
    }

    if (url.pathname === "/broadcast" && request.method === "POST") {
      const body = (await request.json()) as {
        uuid: string;
        colour: string | null;
      };
      const payload = JSON.stringify({
        type: "colour_update",
        uuid: body.uuid,
        colour: body.colour,
      });

      for (const ws of this.ctx.getWebSockets()) {
        try {
          ws.send(payload);
        } catch {
          // ignore, possibly dead connection
        }
      }

      return new Response(
        JSON.stringify({
          ok: true,
          recipients: this.ctx.getWebSockets().length,
        }),
        {
          headers: { "Content-Type": "application/json" },
        },
      );
    }

    return new Response("Not found", { status: 404 });
  }

  async webSocketMessage(
    ws: WebSocket,
    message: string | ArrayBuffer,
  ): Promise<void> {
    if (message === "ping") ws.send("pong");
  }

  async webSocketClose(
    ws: WebSocket,
    code: number,
    _reason: string,
    _wasClean: boolean,
  ): Promise<void> {
    try {
      ws.close(code, "Closed");
    } catch {}
  }
}
