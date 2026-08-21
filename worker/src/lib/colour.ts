export const getUuidHashCode = (uuidStr: string): number => {
  const hex = uuidStr.replace(/-/g, "");
  if (hex.length !== 32) throw new Error(`Invalid UUID: ${uuidStr}`);

  const mostSigBits = BigInt("0x" + hex.slice(0, 16));
  const leastSigBits = BigInt("0x" + hex.slice(16, 32));

  const hilo = mostSigBits ^ leastSigBits;
  const hi = Number((hilo >> 32n) & 0xffffffffn);
  const lo = Number(hilo & 0xffffffffn);

  return (hi ^ lo) | 0;
};

export const withBrightness = (
  color: number,
  brightness: number = 0.9,
): number => {
  const r = (color >> 16) & 0xff;
  const g = (color >> 8) & 0xff;
  const b = color & 0xff;
  const a = (color >> 24) & 0xff;

  const max = Math.max(r, Math.max(g, b));
  const min = Math.min(r, Math.min(g, b));
  const delta = max - min;

  const s = max === 0 ? 0 : delta / max;
  let h = 0;

  if (s !== 0) {
    const dr = (max - r) / delta;
    const dg = (max - g) / delta;
    const db = (max - b) / delta;

    if (r === max) h = db - dg;
    else if (g === max) h = 2.0 + dr - db;
    else h = 4.0 + dg - dr;

    h = h / 6.0;
    if (h < 0.0) h += 1.0;
  }

  const hi = Math.floor(h * 6.0);
  const f = h * 6.0 - hi;
  const p = brightness * (1.0 - s);
  const q = brightness * (1.0 - f * s);
  const t = brightness * (1.0 - (1.0 - f) * s);

  let outR = 0;
  let outG = 0;
  let outB = 0;

  switch (hi) {
    case 0: {
      outR = Math.round(brightness * 255.0);
      outG = Math.round(t * 255.0);
      outB = Math.round(p * 255.0);
      break;
    }
    case 1: {
      outR = Math.round(q * 255.0);
      outG = Math.round(brightness * 255.0);
      outB = Math.round(p * 255.0);
      break;
    }
    case 2: {
      outR = Math.round(p * 255.0);
      outG = Math.round(brightness * 255.0);
      outB = Math.round(t * 255.0);
      break;
    }
    case 3: {
      outR = Math.round(p * 255.0);
      outG = Math.round(q * 255.0);
      outB = Math.round(brightness * 255.0);
      break;
    }
    case 4: {
      outR = Math.round(t * 255.0);
      outG = Math.round(p * 255.0);
      outB = Math.round(brightness * 255.0);
      break;
    }
    case 5: {
      outR = Math.round(brightness * 255.0);
      outG = Math.round(p * 255.0);
      outB = Math.round(q * 255.0);
      break;
    }
  }

  return (
    ((a & 0xff) << 24) |
    ((outR & 0xff) << 16) |
    ((outG & 0xff) << 8) |
    (outB & 0xff)
  );
};

// We want to make it less obvious which players have and haven't used the mod
// so we'll calculate and return the default color for unknown players rather than
// omitting them or setting it to null. If someone *did* want to figure out, they
// could obviously calculate the default themselves but that's more effort and less
// likely to occur. It's also entirely possible that someone *sets* their default color
export const getDefaultColour = (uuidStr: string): string => {
  const hash = getUuidHashCode(uuidStr);
  const color = (0xff << 24) | (hash & 0x00ffffff);
  const brightened = withBrightness(color, 0.9);
  const rgbHex = (brightened & 0x00ffffff).toString(16).padStart(6, "0");
  return `#${rgbHex}`;
};

export const normaliseHex = (input: string): string => {
  const stripped = input.startsWith("#") ? input.slice(1) : input;
  if (!/^[0-9a-fA-F]{6}$/.test(stripped))
    throw new Error(
      `Invalid hex colour: "${input}". Expected 6 hex digits (e.g. "ff6600" or "#ff6600").`,
    );
  return `#${stripped.toLowerCase()}`;
};

export const formatUuid = (raw: string): string => {
  if (raw.length !== 32)
    throw new Error(`Invalid raw UUID length: ${raw.length}`);
  return `${raw.slice(0, 8)}-${raw.slice(8, 12)}-${raw.slice(12, 16)}-${raw.slice(16, 20)}-${raw.slice(20)}`;
};

export const isValidUuid = (input: string): boolean => {
  return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(
    input,
  );
};

export const parseUuidList = (raw: string): string[] => {
  return raw
    .split(",")
    .map((s) => s.trim().toLowerCase())
    .filter(isValidUuid);
};
