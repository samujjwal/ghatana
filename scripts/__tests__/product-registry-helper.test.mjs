import test from 'node:test';
import assert from 'node:assert/strict';

import {
  getRuntimeDependencyProducts,
} from '../lib/product-registry-helper.mjs';

test('runtime dependency proof scope includes Data Cloud and release-enabled business products only', () => {
  const productIds = getRuntimeDependencyProducts().map(({ productId }) => productId);

  assert.ok(productIds.includes('data-cloud'));
  assert.ok(productIds.includes('digital-marketing'));
  assert.ok(productIds.includes('phr'));
  assert.equal(productIds.includes('finance'), false);
  assert.equal(productIds.includes('yappc'), false);
  assert.equal(productIds.includes('audio-video'), false);
});
