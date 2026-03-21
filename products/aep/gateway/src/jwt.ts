import { createHmac, timingSafeEqual } from 'node:crypto';

// ── JWT Types ──────────────────────────────────────────────────────────────────
export interface JwtPayload {
  sub?: string;
  exp?: number;
  iat?: number;
  [key: string]: unknown;
}

// ── JWT helper (node:crypto — no external JWT library) ────────────────────────
export function verifyJwt(token: string, secret: string): JwtPayload {
  const parts = token.split('.');
  if (parts.length !== 3) {
    throw new Error('Invalid JWT structure');
  }
  const [headerB64, payloadB64, signatureB64] = parts as [string, string, string];

  let header: { alg?: string };
  try {
    header = JSON.parse(Buffer.from(headerB64, 'base64url').toString('utf8')) as { alg?: string };
  } catch {
    throw new Error('Invalid JWT header');
  }
  if (header.alg !== 'HS256') {
    throw new Error(`Unsupported JWT algorithm: ${header.alg ?? 'none'}`);
  }

  const expectedSig = createHmac('sha256', secret)
    .update(`${headerB64}.${payloadB64}`)
    .digest('base64url');
  const expectedBuf = Buffer.from(expectedSig, 'base64url');
  const actualBuf   = Buffer.from(signatureB64, 'base64url');
  if (expectedBuf.length !== actualBuf.length || !timingSafeEqual(expectedBuf, actualBuf)) {
    throw new Error('Invalid JWT signature');
  }

  let payload: JwtPayload;
  try {
    payload = JSON.parse(Buffer.from(payloadB64, 'base64url').toString('utf8')) as JwtPayload;
  } catch {
    throw new Error('Invalid JWT payload');
  }
  if (typeof payload.exp === 'number' && payload.exp < Math.floor(Date.now() / 1000)) {
    throw new Error('JWT has expired');
  }
  return payload;
}

export function extractBearerToken(authHeader: string | undefined): string | null {
  if (!authHeader?.startsWith('Bearer ')) return null;
  const token = authHeader.slice(7).trim();
  return token.length > 0 ? token : null;
}
