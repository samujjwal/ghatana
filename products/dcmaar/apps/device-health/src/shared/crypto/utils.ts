import crypto from 'crypto';

export function base64ToArrayBuffer(base64: string): ArrayBuffer {
  const buf = Buffer.from(base64, 'base64');
  return buf.buffer.slice(buf.byteOffset, buf.byteOffset + buf.byteLength);
}

export function pemToArrayBuffer(pem: string): ArrayBuffer {
  const lines = pem.split(/\r?\n/).filter((l) => !l.includes('-----'));
  const b64 = lines.join('');
  return base64ToArrayBuffer(b64);
}

function _trimLeadingZeros(buf: Buffer): Buffer {
  let i = 0;
  while (i < buf.length - 1 && buf[i] === 0) i++;
  return buf.slice(i);
}

function _rawToDer(raw: Buffer): Buffer {
  const half = Math.floor(raw.length / 2);
  const r = _trimLeadingZeros(raw.slice(0, half));
  const s = _trimLeadingZeros(raw.slice(half));
  const rFirstByte = r[0] ?? 0;
  const sFirstByte = s[0] ?? 0;
  const rEnc = Buffer.concat([
    Buffer.from([0x02, r.length]),
    rFirstByte & 0x80 ? Buffer.concat([Buffer.from([0x00]), r]) : r
  ]);
  const sEnc = Buffer.concat([
    Buffer.from([0x02, s.length]),
    sFirstByte & 0x80 ? Buffer.concat([Buffer.from([0x00]), s]) : s
  ]);
  const seq = Buffer.concat([rEnc, sEnc]);
  return Buffer.concat([Buffer.from([0x30, seq.length]), seq]);
}

export function derToRawSignature(derInput: ArrayBuffer | Uint8Array | Buffer): Uint8Array {
  const buf = Buffer.isBuffer(derInput) ? derInput : Buffer.from(derInput as ArrayBuffer);
  let offset = 0;
  
  if (buf[offset++] !== 0x30) throw new Error('Invalid DER');
  const _len = buf[offset++];
  
  // Read r
  if (buf[offset++] !== 0x02) throw new Error('Invalid DER int');
  const rLen = buf[offset++] ?? 0;
  const r = buf.slice(offset, offset + rLen);
  offset += rLen;
  
  // Read s
  if (buf[offset++] !== 0x02) throw new Error('Invalid DER int s');
  const sLen = buf[offset++] ?? 0;
  const s = buf.slice(offset, offset + sLen);
  
  // Pad to 32 bytes each
  const rPadded = Buffer.concat([Buffer.alloc(Math.max(0, 32 - r.length)), r]).slice(-32);
  const sPadded = Buffer.concat([Buffer.alloc(Math.max(0, 32 - s.length)), s]).slice(-32);
  
  return Uint8Array.from(Buffer.concat([rPadded, sPadded]));
}

export function verifySignatureFromSpki(
  spkiPem: string,
  data: ArrayBuffer | Uint8Array | Buffer,
  signature: string | ArrayBuffer | Uint8Array | Buffer
): boolean {
  try {
    const pubKey = crypto.createPublicKey({ key: spkiPem, format: 'pem', type: 'spki' });

    const dataBuf = Buffer.isBuffer(data) ? data : Buffer.from(data as ArrayBuffer);

    let sigBuf: Buffer;
    if (typeof signature === 'string') {
      // base64-encoded DER
      sigBuf = Buffer.from(signature, 'base64');
    } else {
      sigBuf = Buffer.isBuffer(signature) ? signature : Buffer.from(signature as ArrayBuffer);
      // If caller provided a raw 64-byte (r||s) signature, convert to DER
      if (sigBuf.length === 64) {
        sigBuf = _rawToDer(sigBuf);
      }
    }

    // Try verifying with the provided DER first. Some tests craft slightly
    // non-canonical DER (extra leading zeros, long-form lengths). If the
    // initial verify fails, attempt to normalize the DER and verify again.
    try {
      if (crypto.verify(undefined, dataBuf, pubKey, sigBuf)) return true;
    } catch (err) {
      // ignore - we'll try normalization below
    }

    const normalized = _normalizeDer(sigBuf);
    if (normalized && normalized !== sigBuf) {
      try {
        if (crypto.verify(undefined, dataBuf, pubKey, normalized)) return true;
      } catch (err) {
        // fallthrough
      }
    }

    return false;
  } catch (err) {
    return false;
  }
}

function _readLen(buf: Buffer, offset: number): { len: number; headerBytes: number } {
  const first = buf[offset];
  if (first === undefined) {
    throw new Error('Invalid buffer offset');
  }
  
  if ((first & 0x80) === 0) {
    return { len: first, headerBytes: 1 };
  }
  
  const n = first & 0x7f;
  let len = 0;
  
  for (let i = 0; i < n; i++) {
    const byte = buf[offset + 1 + i];
    if (byte === undefined) {
      throw new Error('Invalid length encoding');
    }
    len = (len << 8) | byte;
  }
  
  return { len, headerBytes: 1 + n };
}

function _encodeLen(len: number): Buffer {
  if (len < 0x80) return Buffer.from([len]);
  // minimal long form
  const parts: number[] = [];
  let v = len;
  while (v > 0) {
    parts.unshift(v & 0xff);
    v = v >> 8;
  }
  return Buffer.from([0x80 | parts.length, ...parts]);
}

function _normalizeDer(buf: Buffer): Buffer | null {
  try {
    if (!buf || buf.length < 2 || buf[0] !== 0x30) return null;
    
    // read seq len
    let offset = 1;
    const seqLenInfo = _readLen(buf, offset);
    const seqHeaderBytes = seqLenInfo.headerBytes;
    const seqLen = seqLenInfo.len;
    offset += seqHeaderBytes;

    const end = offset + seqLen;
    const ints: Buffer[] = [];
    
    while (offset < end) {
      if (buf[offset++] !== 0x02) return null;
      
      const intLenInfo = _readLen(buf, offset);
      const intHeader = intLenInfo.headerBytes;
      const intLen = intLenInfo.len;
      offset += intHeader;
      
      const intVal = buf.slice(offset, offset + intLen);
      offset += intLen;

      // strip redundant leading zeros: keep removing while more than one byte
      // and first byte is 0x00 and next byte's high bit is 0
      let v = intVal;
      while (v.length > 1 && v[0] === 0x00 && v[1] !== undefined && (v[1] & 0x80) === 0) {
        v = v.slice(1);
      }
      ints.push(v);
    }

    // Re-encode
    const encInts: Buffer[] = ints.map((v) => Buffer.concat([Buffer.from([0x02]), _encodeLen(v.length), v]));
    const seqPayload = Buffer.concat(encInts);
    const seqHdr = _encodeLen(seqPayload.length);
    return Buffer.concat([Buffer.from([0x30]), seqHdr, seqPayload]);
  } catch (err) {
    return null;
  }
}

export { _normalizeDer };
export const __test__ = { _normalizeDer };

export default { base64ToArrayBuffer, pemToArrayBuffer, derToRawSignature, verifySignatureFromSpki };
