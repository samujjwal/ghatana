import { describe, it, expect, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { RoleContext, ROLE_CONFIG } from '../../roles';
import { DevicesSection } from './DevicesSection';

vi.mock('../../hooks', () => {
    return {
        __esModule: true,
        useDevicesData: vi.fn(() => ({
            data: [
                {
                    id: 'device-1',
                    name: 'Child Phone',
                    childName: 'Emma',
                    status: 'online',
                },
            ],
            isLoading: false,
        })),
    };
});

describe('DevicesSection', () => {
    it('renders devices for parent role', () => {
        render(
            <RoleContext.Provider value={ROLE_CONFIG.parent}>
                <DevicesSection />
            </RoleContext.Provider>
        );

        expect(screen.getByText('Managed Devices')).toBeTruthy();
        expect(screen.getByText('Child Phone')).toBeTruthy();
        expect(screen.getByText('Emma')).toBeTruthy();
    });
});
