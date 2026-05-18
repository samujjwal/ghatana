import { createHmac, timingSafeEqual } from "node:crypto";

// ── JWT Types ──────────────────────────────────────────────────────────────────
export interface JwtPayload {
  sub?: string;
  exp?: number;
  iat?: number;
  [key: string]: unknown;
}

function parseExpiresIn(expiresIn: string | number): number {
  if (typeof expiresIn === "number") {
    return expiresIn;
  }

  const match = expiresIn.trim().match(/^(\d+)([smhd])$/u);
  if (!match) {
    throw new Error(`Unsupported JWT expiration format: ${expiresIn}`);
  }

  const amount = Number(match[1]);
  const unit = match[2] as "s" | "m" | "h" | "d";
  const unitSeconds = {
    s: 1,
    m: 60,
    h: 60 * 60,
    d: 24 * 60 * 60,
  }[unit];
  return amount * unitSeconds;
}

export function signJwt(
  payload: Record<string, unknown>,
  secret: string,
  expiresIn: string | number = "1h",
): string {
  const header = Buffer.from(
    JSON.stringify({ alg: "HS256", typ: "JWT" }),
  ).toString("base64url");
  const now = Math.floor(Date.now() / 1000);
  const bodyPayload: JwtPayload = {
    ...payload,
    iat: now,
    exp: now + parseExpiresIn(expiresIn),
  };
  const body = Buffer.from(JSON.stringify(bodyPayload)).toString("base64url");
  const signature = createHmac("sha256", secret)
    .update(`${header}.${body}`)
    .digest("base64url");
  return `${header}.${body}.${signature}`;
}

// ── JWT helper (node:crypto — no external JWT library) ────────────────────────
export function verifyJwt(token: string, secret: string): JwtPayload {
  const parts = token.split(".");
  if (parts.length !== 3) {
    throw new Error("Invalid JWT structure");
  }
  const [headerB64, payloadB64, signatureB64] = parts as [
    string,
    string,
    string,
  ];

  let header: { alg?: string };
  try {
    header = JSON.parse(
      Buffer.from(headerB64, "base64url").toString("utf8"),
    ) as { alg?: string };
  } catch {
    throw new Error("Invalid JWT header");
  }
  if (header.alg !== "HS256") {
    throw new Error(`Unsupported JWT algorithm: ${header.alg ?? "none"}`);
  }

  const expectedSig = createHmac("sha256", secret)
    .update(`${headerB64}.${payloadB64}`)
    .digest("base64url");
  const expectedBuf = Buffer.from(expectedSig, "base64url");
  const actualBuf = Buffer.from(signatureB64, "base64url");
  if (
    expectedBuf.length !== actualBuf.length ||
    !timingSafeEqual(expectedBuf, actualBuf)
  ) {
    throw new Error("Invalid JWT signature");
  }

  let payload: JwtPayload;
  try {
    payload = JSON.parse(
      Buffer.from(payloadB64, "base64url").toString("utf8"),
    ) as JwtPayload;
  } catch {
    throw new Error("Invalid JWT payload");
  }
  if (
    typeof payload.exp === "number" &&
    payload.exp < Math.floor(Date.now() / 1000)
  ) {
    throw new Error("JWT has expired");
  }
  return payload;
}

export function extractBearerToken(
  authHeader: string | undefined,
): string | null {
  if (!authHeader?.startsWith("Bearer ")) return null;
  const token = authHeader.slice(7).trim();
  return token.length > 0 ? token : null;
}
