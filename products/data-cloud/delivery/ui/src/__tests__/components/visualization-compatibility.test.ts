import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { describe, expect, it } from 'vitest';

import {
  useEventCloudStream,
  useEventLogStream,
} from '../../lib/integrations';

describe('Data Cloud visualization compatibility exports', () => {
  it('keeps EventCloud component aliases bound to EventLog implementations', () => {
    const indexSource = readFileSync(
      resolve(__dirname, '../../components/visualizations/index.ts'),
      'utf8',
    );

    expect(indexSource).toContain('default as EventLogTopology');
    expect(indexSource).toContain('default as EventCloudTopology');
    expect(indexSource).toContain("} from './EventLogTopology'");
    expect(indexSource).toContain('default as EventLogLiveTopology');
    expect(indexSource).toContain('default as EventCloudLiveTopology');
    expect(indexSource).toContain("} from './EventLogLiveTopology'");
  });

  it('keeps EventCloud stream hook alias bound to EventLog stream hook', () => {
    expect(useEventCloudStream).toBe(useEventLogStream);
  });
});
