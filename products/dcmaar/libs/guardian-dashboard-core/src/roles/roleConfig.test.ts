import { describe, it, expect } from 'vitest';
import { ROLE_CONFIG, getRoleConfig, type UserRole } from '..';

describe('ROLE_CONFIG', () => {
    it('defines configs for all known roles', () => {
        const roles: UserRole[] = ['parent', 'child', 'admin'];

        for (const role of roles) {
            const cfg = ROLE_CONFIG[role];
            expect(cfg).toBeDefined();
            expect(cfg.role).toBe(role);
            expect(Array.isArray(cfg.sections)).toBe(true);
            expect(cfg.sections.length).toBeGreaterThan(0);
        }
    });

    it('getRoleConfig returns the same object as ROLE_CONFIG entry', () => {
        const roles: UserRole[] = ['parent', 'child', 'admin'];

        for (const role of roles) {
            expect(getRoleConfig(role)).toBe(ROLE_CONFIG[role]);
        }
    });

    it('child role does not have admin-only sections', () => {
        expect(ROLE_CONFIG.child.sections).not.toContain('admin');
    });
});
