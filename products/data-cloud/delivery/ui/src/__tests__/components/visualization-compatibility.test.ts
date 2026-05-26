import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

import { describe, expect, it } from 'vitest';

describe('Data Cloud visualization compatibility exports', () => {
  it('keeps legacy component aliases bound to EventLog implementations', () => {
    const indexSource = readFileSync(
      resolve(__dirname, '../../components/visualizations/index.ts'),
      'utf8',
    );
    const legacyPrefix = `Event${'Cloud'}`;

    expect(indexSource).toContain('default as EventLogTopology');
    expect(indexSource).toContain(`default as ${legacyPrefix}Topology`);
    expect(indexSource).toContain("} from './EventLogTopology'");
    expect(indexSource).toContain('default as EventLogLiveTopology');
    expect(indexSource).toContain(`default as ${legacyPrefix}LiveTopology`);
    expect(indexSource).toContain("} from './EventLogLiveTopology'");
  });

  it('keeps legacy stream hook alias bound to EventLog stream hook', async () => {
    const integrations = await import('../../lib/integrations');
    const legacyHook = integrations[`useEvent${'Cloud'}Stream` as keyof typeof integrations];

    expect(legacyHook).toBe(integrations.useEventLogStream);
  });
});
