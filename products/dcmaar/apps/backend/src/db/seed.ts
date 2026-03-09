/**
 * Database seeding utility for test and development data.
 *
 * <p><b>Purpose</b><br>
 * Populates database with baseline test data for development and testing.
 * Creates sample parent user, child profile, policies, and usage data
 * to enable rapid feature development without manual data entry.
 *
 * <p><b>Seed Data Generated</b><br>
 * - Parent user (email, password_hash, profile)
 * - Child profile (name, age, settings)
 * - Default policies (content filtering, time limits)
 * - Sample usage sessions (last 7 days)
 * - Sample block events
 * - Device registration
 *
 * <p><b>Options</b><br>
 * - parentEmail: Email for seed parent (default: parent@example.com)
 * - childName: Name for seed child (default: Sample Child)
 *
 * <p><b>Safety</b><br>
 * Uses ON CONFLICT DO UPDATE to ensure idempotency.
 * Safe to run multiple times (re-creates existing seed data).
 * Uses seeded password hash for consistency across environments.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { seedDatabase } from './db/seed';
 * 
 * // Seed with defaults
 * await seedDatabase();
 * 
 * // Seed with custom values
 * await seedDatabase({
 *   parentEmail: 'testparent@example.com',
 *   childName: 'Test Child'
 * });
 * }</pre>
 *
 * <p><b>Testing</b><br>
 * Seed is automatically run during test setup to provide consistent
 * baseline data. Tests can rely on seedDatabase() having been called.
 *
 * @doc.type utility
 * @doc.purpose Database seeding utility for test and development data
 * @doc.layer backend
 * @doc.pattern Database Seed
 */
import { transaction } from './index';
import { logger } from '../utils/logger';
import { hashPassword } from '../services/auth.service';

export interface SeedOptions {
  parentEmail?: string;
  childName?: string;
  /** Create comprehensive test data for E2E testing */
  comprehensive?: boolean;
}

/**
 * Seed result containing IDs of created entities for test reference.
 */
export interface SeedResult {
  parentId: string;
  children: Array<{ id: string; name: string }>;
  devices: Array<{ id: string; type: string; childId: string }>;
  policies: Array<{ id: string; name: string }>;
}

export async function seedDatabase(options: SeedOptions = {}): Promise<SeedResult> {
  const parentEmail = options.parentEmail ?? 'parent@example.com';
  const childName = options.childName ?? 'Sample Child';
  const comprehensive = options.comprehensive ?? false;

  return await transaction(async client => {
    logger.info('Seeding database with baseline data', { comprehensive });

    // Create parent user
    const seededPassword = 'password123';
    const seededPasswordHash = await hashPassword(seededPassword);

    const parentResult = await client.query(
      `INSERT INTO users (email, password_hash, display_name, email_verified)
       VALUES ($1, $2, $3, true)
       ON CONFLICT (email) DO UPDATE SET password_hash = EXCLUDED.password_hash, display_name = EXCLUDED.display_name
       RETURNING id`,
      [parentEmail.toLowerCase(), seededPasswordHash, 'Seed Parent']
    );
    const parentId = parentResult.rows[0].id;

    // Create demo parent user for development convenience
    const demoEmail = 'demo@example.com';
    const demoPassword = 'password123';
    const demoPasswordHash = await hashPassword(demoPassword);

    await client.query(
      `INSERT INTO users (email, password_hash, display_name, email_verified)
       VALUES ($1, $2, $3, true)
       ON CONFLICT (email) DO UPDATE SET password_hash = EXCLUDED.password_hash, display_name = EXCLUDED.display_name`,
      [demoEmail.toLowerCase(), demoPasswordHash, 'Demo Parent User']
    );

    const children: SeedResult['children'] = [];
    const devices: SeedResult['devices'] = [];
    const policies: SeedResult['policies'] = [];

    // Create first child
    const childResult = await client.query(
      `INSERT INTO children (user_id, name, birth_date)
       VALUES ($1, $2, $3)
       ON CONFLICT DO NOTHING
       RETURNING id`,
      [parentId, childName, '2012-05-15']
    );
    const childId = childResult.rows[0]?.id;
    if (childId) {
      children.push({ id: childId, name: childName });
    }

    // Create mobile device for first child
    const deviceResult = await client.query(
      `INSERT INTO devices (user_id, child_id, device_type, device_name, is_paired, status, is_active)
       VALUES ($1, $2, 'mobile', 'Seeded Mobile Device', true, 'active', true)
       RETURNING id`,
      [parentId, childId]
    );
    const mobileDeviceId = deviceResult.rows[0].id;
    devices.push({ id: mobileDeviceId, type: 'mobile', childId });

    // Create browser extension device for first child
    const extensionResult = await client.query(
      `INSERT INTO devices (user_id, child_id, device_type, device_name, is_paired, status, is_active)
       VALUES ($1, $2, 'extension', 'Chrome Browser Extension', true, 'active', true)
       RETURNING id`,
      [parentId, childId]
    );
    const extensionDeviceId = extensionResult.rows[0].id;
    devices.push({ id: extensionDeviceId, type: 'extension', childId });

    // Create default website blocking policy
    const policyResult = await client.query(
      `INSERT INTO policies (user_id, child_id, name, policy_type, enabled, priority, config)
       VALUES ($1, $2, 'Social Media Block', 'website', true, 10, $3)
       RETURNING id`,
      [
        parentId,
        childId,
        JSON.stringify({
          domains: ['facebook.com', 'tiktok.com', 'instagram.com'],
          action: 'block',
        }),
      ]
    );
    policies.push({ id: policyResult.rows[0].id, name: 'Social Media Block' });

    // Create sample block event
    await client.query(
      `INSERT INTO block_events (device_id, policy_id, event_type, blocked_item, category, reason)
       VALUES ($1, $2, 'website', 'tiktok.com', 'social', 'Blocked by Social Media Block policy')`,
      [extensionDeviceId, policyResult.rows[0].id]
    );

    // Create sample usage session
    await client.query(
      `INSERT INTO usage_sessions (device_id, session_type, item_name, category, start_time, end_time, duration_seconds)
       VALUES ($1, 'website', 'youtube.com', 'entertainment', NOW() - INTERVAL '1 hour', NOW(), 3600)`,
      [extensionDeviceId]
    );

    // Comprehensive mode: add more test data
    if (comprehensive) {
      // Second child
      const child2Result = await client.query(
        `INSERT INTO children (user_id, name, birth_date)
         VALUES ($1, $2, $3)
         ON CONFLICT DO NOTHING
         RETURNING id`,
        [parentId, 'Teen Child', '2008-09-20']
      );
      const child2Id = child2Result.rows[0]?.id;
      if (child2Id) {
        children.push({ id: child2Id, name: 'Teen Child' });

        // Desktop device for second child
        const desktopResult = await client.query(
          `INSERT INTO devices (user_id, child_id, device_type, device_name, is_paired, status, is_active)
           VALUES ($1, $2, 'desktop', 'Windows Desktop', true, 'active', true)
           RETURNING id`,
          [parentId, child2Id]
        );
        devices.push({ id: desktopResult.rows[0].id, type: 'desktop', childId: child2Id });
      }

      // Third child (younger)
      const child3Result = await client.query(
        `INSERT INTO children (user_id, name, birth_date)
         VALUES ($1, $2, $3)
         ON CONFLICT DO NOTHING
         RETURNING id`,
        [parentId, 'Young Child', '2017-03-10']
      );
      const child3Id = child3Result.rows[0]?.id;
      if (child3Id) {
        children.push({ id: child3Id, name: 'Young Child' });
      }

      // Category blocking policy (global for all children)
      const categoryPolicyResult = await client.query(
        `INSERT INTO policies (user_id, name, policy_type, enabled, priority, config)
         VALUES ($1, 'Adult Content Block', 'category', true, 100, $2)
         RETURNING id`,
        [
          parentId,
          JSON.stringify({
            categories: ['adult', 'gambling', 'violence'],
            action: 'block',
          }),
        ]
      );
      policies.push({ id: categoryPolicyResult.rows[0].id, name: 'Adult Content Block' });

      // Schedule policy for first child
      const schedulePolicyResult = await client.query(
        `INSERT INTO policies (user_id, child_id, name, policy_type, enabled, priority, config)
         VALUES ($1, $2, 'Bedtime Schedule', 'schedule', true, 50, $3)
         RETURNING id`,
        [
          parentId,
          childId,
          JSON.stringify({
            schedule: {
              weekdays: { start: '21:00', end: '07:00' },
              weekends: { start: '22:00', end: '08:00' },
            },
            action: 'block_all',
          }),
        ]
      );
      policies.push({ id: schedulePolicyResult.rows[0].id, name: 'Bedtime Schedule' });

      // Sample usage data for last 7 days
      for (let i = 0; i < 7; i++) {
        await client.query(
          `INSERT INTO usage_sessions (device_id, session_type, item_name, category, start_time, end_time, duration_seconds)
           VALUES ($1, 'website', $2, $3, NOW() - INTERVAL '${i} days' - INTERVAL '2 hours', NOW() - INTERVAL '${i} days', $4)`,
          [
            extensionDeviceId,
            ['youtube.com', 'google.com', 'wikipedia.org'][i % 3],
            ['entertainment', 'search', 'education'][i % 3],
            1800 + (i * 300),
          ]
        );
      }

      // Sample block events
      const blockedSites = ['tiktok.com', 'facebook.com', 'instagram.com'];
      for (let i = 0; i < 5; i++) {
        await client.query(
          `INSERT INTO block_events (device_id, policy_id, event_type, blocked_item, category, reason, timestamp)
           VALUES ($1, $2, 'website', $3, 'social', 'Blocked by policy', NOW() - INTERVAL '${i} hours')`,
          [extensionDeviceId, policyResult.rows[0].id, blockedSites[i % 3]]
        );
      }

      logger.info('Comprehensive seed data inserted', {
        children: children.length,
        devices: devices.length,
        policies: policies.length,
      });
    }

    logger.info('Seed data inserted successfully');

    return {
      parentId,
      children,
      devices,
      policies,
    };
  });
}

if (process.argv[1]?.includes('seed.ts')) {
  const comprehensive = process.argv.includes('--comprehensive') || process.argv.includes('-c');
  seedDatabase({ comprehensive })
    .then((result) => {
      logger.info('Database seed complete', {
        parentId: result.parentId,
        childrenCount: result.children.length,
        devicesCount: result.devices.length,
        policiesCount: result.policies.length,
      });
      console.log('\n=== Seed Result ===');
      console.log(`Parent ID: ${result.parentId}`);
      console.log(`Children: ${result.children.map(c => `${c.name} (${c.id})`).join(', ')}`);
      console.log(`Devices: ${result.devices.map(d => `${d.type} (${d.id})`).join(', ')}`);
      console.log(`Policies: ${result.policies.map(p => `${p.name} (${p.id})`).join(', ')}`);
      console.log('\nUse --comprehensive or -c flag for full E2E test data');
      process.exit(0);
    })
    .catch(error => {
      logger.error('Database seed failed', { error });
      process.exit(1);
    });
}
