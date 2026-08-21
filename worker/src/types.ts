import type { ColourBroadcaster } from "./durable/ColourBroadcaster";

export type Env = {
  COLOURS_KV: KVNamespace;
  SESSION_KV: KVNamespace;
  BROADCASTER: DurableObjectNamespace<ColourBroadcaster>;
  JWT_SECRET: string;
};

export type JwtPayload = {
  sub: string; // dashed UUID
  name: string; // IGN
  iat: number;
  exp: number;
};
