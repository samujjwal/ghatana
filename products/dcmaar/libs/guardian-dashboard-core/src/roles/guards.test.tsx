import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/react';
import { RoleContext, ROLE_CONFIG, PermissionGuard, SectionGuard } from '.';

function renderWithRole(roleKey: keyof typeof ROLE_CONFIG, ui: React.ReactElement) {
    return render(
        <RoleContext.Provider value={ROLE_CONFIG[roleKey]}>
            {ui}
        </RoleContext.Provider>
    );
}

describe('PermissionGuard', () => {
    it('renders children when permission is granted', () => {
        renderWithRole('parent', (
            <PermissionGuard permission="canViewDevices">
                <div data-testid="allowed">allowed</div>
            </PermissionGuard>
        ));

        expect(screen.getByTestId('allowed')).toBeTruthy();
    });

    it('renders fallback when permission is denied', () => {
        renderWithRole('child', (
            <PermissionGuard
                permission="canViewDevices"
                fallback={<div data-testid="fallback">fallback</div>}
            >
                <div data-testid="allowed">allowed</div>
            </PermissionGuard>
        ));

        expect(screen.queryByTestId('allowed')).toBeNull();
        expect(screen.getByTestId('fallback')).toBeTruthy();
    });
});

describe('SectionGuard', () => {
    it('renders children when section is visible for role', () => {
        renderWithRole('parent', (
            <SectionGuard section="devices">
                <div data-testid="devices">Devices</div>
            </SectionGuard>
        ));

        expect(screen.getByTestId('devices')).toBeTruthy();
    });

    it('hides children when section is not visible', () => {
        renderWithRole('child', (
            <SectionGuard section="devices">
                <div data-testid="devices">Devices</div>
            </SectionGuard>
        ));

        expect(screen.queryByTestId('devices')).toBeNull();
    });
});
