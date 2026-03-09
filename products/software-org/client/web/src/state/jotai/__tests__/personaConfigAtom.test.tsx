import React from 'react';
import { render, screen, act } from '@testing-library/react';
import { Provider, useAtom } from 'jotai';
import { personaConfigAtom, userProfileAtom, userRoleAtom } from '../atoms';

declare global {
    interface Window {
        __testSetUserProfile?: (p: any) => void;
        __testSetUserRole?: (r: any) => void;
    }
}

function TestRenderer() {
    const [personaConfig] = useAtom(personaConfigAtom);
    const [, setUserProfile] = useAtom(userProfileAtom);
    const [, setUserRole] = useAtom(userRoleAtom);

    React.useEffect(() => {
        window.__testSetUserProfile = setUserProfile;
        window.__testSetUserRole = setUserRole;

        return () => {
            // cleanup
            // eslint-disable-next-line @typescript-eslint/ban-ts-comment
            // @ts-ignore
            window.__testSetUserProfile = undefined;
            // eslint-disable-next-line @typescript-eslint/ban-ts-comment
            // @ts-ignore
            window.__testSetUserRole = undefined;
        };
    }, [setUserProfile, setUserRole]);

    return (
        <div>
            <span data-testid="persona-role">{personaConfig?.role ?? 'null'}</span>
            <span data-testid="persona-display">{personaConfig?.displayName ?? 'null'}</span>
        </div>
    );
}

describe('personaConfigAtom', () => {
    beforeEach(() => {
        // Render the provider fresh for each test
        render(
            <Provider>
                <TestRenderer />
            </Provider>
        );
    });

    it('falls back to userRoleAtom when userProfileAtom is null', async () => {
        // Initially fallback role is 'engineer' per default userRoleAtom
        expect(screen.getByTestId('persona-role').textContent).toBe('engineer');

        // Change user role to 'lead' and verify the persona updates
        await act(async () => {
            window.__testSetUserRole && window.__testSetUserRole('lead');
        });

        expect(screen.getByTestId('persona-role').textContent).toBe('lead');
    });

    it('uses userProfileAtom role when present', async () => {
        await act(async () => {
            window.__testSetUserProfile && window.__testSetUserProfile({
                userId: 'test',
                name: 'Test User',
                email: 'test@example.com',
                role: 'admin',
                permissions: ['*'],
            });
        });

        expect(screen.getByTestId('persona-role').textContent).toBe('admin');
    });
});
