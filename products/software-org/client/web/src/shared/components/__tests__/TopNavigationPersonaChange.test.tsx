import React from 'react';
import { render, screen, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Provider, useSetAtom } from 'jotai';
import { MemoryRouter } from 'react-router';
import TopNavigation from '../TopNavigation';
import { userProfileAtom } from '@/state/jotai/atoms';
import { NavigationProvider } from '@/context/NavigationContext';

describe('TopNavigation Persona Switch', () => {
    it('updates role when persona clicked', async () => {
        render(
            <Provider>
                <MemoryRouter>
                    <NavigationProvider>
                        <TopNavigation />
                    </NavigationProvider>
                </MemoryRouter>
            </Provider>
        );

        // Find persona button for engineer (default is engineer)
        const adminButton = await screen.findByLabelText(/Switch to Admin persona/i);
        expect(adminButton).toBeInTheDocument();

        // If the badge isn't visible on small screen, we rely on aria-label
        await act(async () => {
            await userEvent.click(adminButton);
        });

        // After clicking admin, the nav badge shows the selected role in uppercase
        const roleBadge = await screen.findByText(/ADMIN/i);
        expect(roleBadge).toBeInTheDocument();
    });

    it('home link navigates to / for authenticated users', async () => {
        // Helper to set a fake logged in user
        function SetProfile() {
            const setProfile = useSetAtom(userProfileAtom);
            React.useEffect(() => {
                setProfile({
                    userId: 'u-1',
                    name: 'Test User',
                    email: 'test@x.com',
                    role: 'admin',
                    permissions: ['*'],
                });
            }, [setProfile]);
            return null;
        }

        render(
            <Provider>
                <MemoryRouter>
                    <NavigationProvider>
                        <SetProfile />
                        <TopNavigation />
                    </NavigationProvider>
                </MemoryRouter>
            </Provider>
        );

        const homeLink = await screen.findByLabelText(/Go to home page/i);
        // Link should route to / (HomePage) even when authenticated
        expect(homeLink.closest('a')?.getAttribute('href')).toBe('/');
    });

    it('home link navigates to / for unauthenticated users', async () => {
        // Ensure there is no user profile set
        function ClearProfile() {
            const setProfile = useSetAtom(userProfileAtom);
            React.useEffect(() => {
                setProfile(null);
            }, [setProfile]);
            return null;
        }

        render(
            <Provider>
                <MemoryRouter>
                    <NavigationProvider>
                        <ClearProfile />
                        <TopNavigation />
                    </NavigationProvider>
                </MemoryRouter>
            </Provider>
        );

        const homeLink = await screen.findByLabelText(/Go to home page/i);
        expect(homeLink.closest('a')?.getAttribute('href')).toBe('/');
    });

    it('home link uses provided homePath prop', async () => {
        render(
            <Provider>
                <MemoryRouter>
                    <NavigationProvider>
                        <TopNavigation homePath={'/dev'} />
                    </NavigationProvider>
                </MemoryRouter>
            </Provider>
        );

        const homeLink = await screen.findByLabelText(/Go to home page/i);
        expect(homeLink.closest('a')?.getAttribute('href')).toBe('/dev');
    });
});
