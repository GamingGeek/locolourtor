// Mojang blocks Cloudflare Workers so we have to hardcode these
// as we can't fetch /publickeys, which would be the more ideal option
export const MOJANG_ROOT_PUBLIC_KEYS_B64 = [
  "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAylB4B6m5lz7jwrcFz6Fd/fnfUhcvlxsTSn5kIK/2aGG1C3kMy4VjhwlxF6BFUSnfxhNswPjh3ZitkBxEAFY25uzkJFRwHwVA9mdwjashXILtR6OqdLXXFVyUPIURLOSWqGNBtb08EN5fMnG8iFLgEJIBMxs9BvF3s3/FhuHyPKiVTZmXY0WY4ZyYqvoKR+XjaTRPPvBsDa4WI2u1zxXMeHlodT3lnCzVvyOYBLXL6CJgByuOxccJ8hnXfF9yY4F0aeL080Jz/3+EBNG8RO4ByhtBf4Ny8NQ6stWsjfeUIvH7bU/4zCYcYOq4WrInXHqS8qruDmIl7P5XXGcabuzQstPf/h2CRAUpP/PlHXcMlvewjmGU6MfDK+lifScNYwjPxRo4nKTGFZf/0aqHCh/EAsQyLKrOIYRE0lDG3bzBh8ogIMLAugsAfBb6M3mqCqKaTMAf/VAjh5FFJnjS+7bE+bZEV0qwax1CEoPPJL1fIQjOS8zj086gjpGRCtSy9+bTPTfTR/SJ+VUB5G2IeCItkNHpJX2ygojFZ9n5Fnj7R9ZnOM+L8nyIjPu3aePvtcrXlyLhH/hvOfIOjPxOlqW+O5QwSFP4OEcyLAUgDdUgyW36Z5mB285uKW/ighzZsOTevVUG2QwDItObIV6i8RCxFbN2oDHyPaO5j1tTaBNyVt8CAwEAAQ==",
  "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAt4t9NPuu7cktclnaH7eZj0omkLcJHeLz5MKsyJEntHZ0INtuBjSSul3Pp3pBeJN8k3ADdcdBLUN90bcAi7WsQqTx3Ft363q3W7TbM8j2iTEdp/0uVspoRt/DP1tkaWFs/w2WwUv9jbVoBUzfUc4pSTIxRwdjmqjZQfvjwKNDbOx3IhP2H0WXodbISejPi1wBZqNW4m1rnZAXp/EpUguxA8mobCa4vUCBkyFDyXdl69/wUSJHyCPmgcMJ364OlAhIqtwVPShBZObvrK/f0BYk6ShJD3N7TFDatSYsIIdcTKRknaIm91s+EsMrdB9U4Yw+ZJ/pyCB4S3vk8zfDCnb0DWIxYH3/EMzaxl77djmTmMzi/JDITup5z3jfWtRZmrAhU2/+W5IO5hEpo3/bCS9PXIY5xb41Lmp2ZO8dXKtyD66Chchy0W129n8vPl2GIruOdrxsjZAHnneyAb9jm0uaGaphwnEnuecX/qgHY6ZMtayvLLsPst8PO6R1vufMy8WqjK+j7LnC1krL7CPDg0NEhyQTmw5l+NCNjSlvB1juM9V4PARg0bYCOkGXm7ydRCjSSH8CJXZpwnd5cBB5WKAX3KPzutRgMi/LFwNSMZzFuUyXaYOZPpD259yqph1LmGqegEdDriACVU+dVEONFMm8eIuBofe7ljmsAFKW9BINwK0CAwEAAQ==",
];

export const base64ToArrayBuffer = (b64: string): ArrayBuffer => {
  const binary = atob(b64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i++) {
    bytes[i] = binary.charCodeAt(i);
  }
  return bytes.buffer;
};

export const buildMojangSignedPayload = (
  uuidStr: string,
  expiresAtEpochMs: number,
  publicKeyDer: Uint8Array,
): Uint8Array => {
  const cleanUuid = uuidStr.replace(/-/g, "");
  if (cleanUuid.length !== 32) throw new Error("Invalid UUID format");

  const msb = BigInt("0x" + cleanUuid.slice(0, 16));
  const lsb = BigInt("0x" + cleanUuid.slice(16, 32));

  const buffer = new ArrayBuffer(24 + publicKeyDer.length);
  const view = new DataView(buffer);

  view.setBigUint64(0, msb, false);
  view.setBigUint64(8, lsb, false);
  view.setBigUint64(16, BigInt(expiresAtEpochMs), false);

  const out = new Uint8Array(buffer);
  out.set(publicKeyDer, 24);
  return out;
};

export async function verifyMojangCertificate(
  uuidStr: string,
  expiresAtEpochMs: number,
  publicKeyB64: string,
  keySignatureB64: string,
): Promise<boolean> {
  const publicKeyDer = new Uint8Array(base64ToArrayBuffer(publicKeyB64));
  const keySignature = base64ToArrayBuffer(keySignatureB64);
  const payload = buildMojangSignedPayload(
    uuidStr,
    expiresAtEpochMs,
    publicKeyDer,
  );

  const hashAlgorithms = ["SHA-1", "SHA-256"];

  for (const rootKeyB64 of MOJANG_ROOT_PUBLIC_KEYS_B64) {
    const rootKeyDer = base64ToArrayBuffer(rootKeyB64);
    for (const hash of hashAlgorithms) {
      try {
        const cryptoKey = await crypto.subtle.importKey(
          "spki",
          rootKeyDer,
          { name: "RSASSA-PKCS1-v1_5", hash },
          false,
          ["verify"],
        );

        const valid = await crypto.subtle.verify(
          "RSASSA-PKCS1-v1_5",
          cryptoKey,
          keySignature,
          payload,
        );

        if (valid) return true;
      } catch {
        // ignore, we'll continue with another alg
      }
    }
  }

  return false;
}

export async function verifyPlayerAction(
  challenge: string,
  publicKeyB64: string,
  actionSignatureB64: string,
): Promise<boolean> {
  const publicKeyDer = base64ToArrayBuffer(publicKeyB64);
  const actionSignature = base64ToArrayBuffer(actionSignatureB64);
  const encoder = new TextEncoder();
  const data = encoder.encode(challenge);

  for (const hash of ["SHA-256", "SHA-1"]) {
    try {
      const cryptoKey = await crypto.subtle.importKey(
        "spki",
        publicKeyDer,
        { name: "RSASSA-PKCS1-v1_5", hash },
        false,
        ["verify"],
      );

      const valid = await crypto.subtle.verify(
        "RSASSA-PKCS1-v1_5",
        cryptoKey,
        actionSignature,
        data,
      );

      if (valid) return true;
    } catch {
      // ignore, we'll try with another hash
      // (and by another, I mean SHA-1)
    }
  }

  return false;
}
