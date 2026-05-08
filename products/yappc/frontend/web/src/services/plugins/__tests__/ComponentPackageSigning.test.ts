/**
 * Component Package Signing Tests
 *
 * @doc.type test
 * @doc.purpose Validate marketplace component package signature verification
 * @doc.layer product
 */

import { describe, expect, it } from 'vitest';
import {
  COMPONENT_PACKAGE_SIGNATURE_ALGORITHM,
  buildComponentPackageSigningPayload,
  computeComponentPackageIntegrityDigest,
  validateComponentPackageSignature,
  type ComponentPackageSignature,
  type ComponentPackageSigningInput,
} from '../ComponentPackageSigning';

describe('ComponentPackageSigning', () => {
  it('builds a stable canonical payload independent of renderer and allowlist order', () => {
    const first = buildComponentPackageSigningPayload({
      packageName: '@marketplace/cards',
      version: '1.0.0',
      minBuilderVersion: '1.0.0',
      rendererContracts: ['HeroCard', 'ArticleCard'],
      securityPolicy: {
        allowedDomains: ['api.ghatana.com', 'assets.ghatana.com'],
        allowedTelemetryEvents: ['card.loaded', 'card.clicked'],
      },
      marketplacePackageId: 'pkg_cards',
    });
    const second = buildComponentPackageSigningPayload({
      packageName: '@marketplace/cards',
      version: '1.0.0',
      minBuilderVersion: '1.0.0',
      rendererContracts: ['ArticleCard', 'HeroCard'],
      securityPolicy: {
        allowedDomains: ['assets.ghatana.com', 'api.ghatana.com'],
        allowedTelemetryEvents: ['card.clicked', 'card.loaded'],
      },
      marketplacePackageId: 'pkg_cards',
    });

    expect(first).toEqual(second);
    expect(first.rendererContracts).toEqual(['ArticleCard', 'HeroCard']);
  });

  it('accepts a current signature with matching subject and digest', () => {
    const input = createSigningInput();
    const signature = createSignature(input);

    const result = validateComponentPackageSignature(
      {
        ...input,
        signature,
      },
      new Date('2026-05-07T12:00:00.000Z'),
    );

    expect(result.valid).toBe(true);
    expect(result.errors).toHaveLength(0);
    expect(result.digest).toBe(signature.digest);
  });

  it('fails closed when the signature envelope is missing', () => {
    const result = validateComponentPackageSignature(createSigningInput());

    expect(result.valid).toBe(false);
    expect(result.errors).toContain('Marketplace package signature is required');
  });

  it('rejects tampered package payloads with a digest mismatch', () => {
    const original = createSigningInput();
    const signature = createSignature(original);
    const tampered = {
      ...original,
      rendererContracts: ['HeroCard', 'SecretExporter'],
      signature,
    };

    const result = validateComponentPackageSignature(tampered, new Date('2026-05-07T12:00:00.000Z'));

    expect(result.valid).toBe(false);
    expect(result.errors).toContain('Package signature digest does not match manifest payload');
  });

  it('rejects expired signatures', () => {
    const input = createSigningInput();
    const signature = createSignature(input, {
      expiresAt: '2025-01-01T00:00:00.000Z',
    });

    const result = validateComponentPackageSignature(
      {
        ...input,
        signature,
      },
      new Date('2026-05-07T12:00:00.000Z'),
    );

    expect(result.valid).toBe(false);
    expect(result.errors).toContain('Package signature has expired');
  });
});

function createSigningInput(): ComponentPackageSigningInput {
  return {
    packageName: '@marketplace/cards',
    version: '1.0.0',
    minBuilderVersion: '1.0.0',
    rendererContracts: ['HeroCard'],
    securityPolicy: {
      allowedDomains: ['api.ghatana.com'],
    },
    marketplacePackageId: 'pkg_cards',
  };
}

function createSignature(
  input: ComponentPackageSigningInput,
  override: Partial<ComponentPackageSignature> = {},
): ComponentPackageSignature {
  const digest = computeComponentPackageIntegrityDigest(input);
  return {
    algorithm: COMPONENT_PACKAGE_SIGNATURE_ALGORITHM,
    keyId: 'yappc-marketplace-root-2026',
    issuedAt: '2026-01-01T00:00:00.000Z',
    expiresAt: '2099-01-01T00:00:00.000Z',
    subject: {
      packageName: input.packageName,
      version: input.version,
      marketplacePackageId: input.marketplacePackageId,
    },
    digest,
    signature: `yappc-sig-v1:${digest}`,
    ...override,
  };
}
