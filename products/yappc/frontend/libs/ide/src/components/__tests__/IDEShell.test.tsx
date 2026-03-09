import React from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Provider } from 'jotai';
import { IDEShell } from '../IDEShell';

describe('IDEShell activity bar', () => {
    test('toggles search, source control, run and extensions panels', async () => {
        render(
            <Provider>
                <IDEShell initialWidth={800} initialHeight={600} showStatusBar={false} />
            </Provider>
        );

        const user = userEvent.setup();

        // Search button (🔍)
        const searchBtn = screen.getByRole('button', { name: /Search/i });
        await user.click(searchBtn);
        expect(screen.getByRole('dialog', { name: /Search panel/i })).toBeInTheDocument();
        await user.click(searchBtn);
        expect(screen.queryByRole('dialog', { name: /Search panel/i })).not.toBeInTheDocument();

        // Source control (🔀)
        const scBtn = screen.getByRole('button', { name: /Source control/i });
        await user.click(scBtn);
        expect(screen.getByRole('dialog', { name: /Source control panel/i })).toBeInTheDocument();

        // Run (▶️)
        const runBtn = screen.getByRole('button', { name: /Run and Debug/i });
        await user.click(runBtn);
        expect(screen.getByRole('dialog', { name: /Run and Debug panel/i })).toBeInTheDocument();

        // Extensions (🧩)
        const extBtn = screen.getByRole('button', { name: /Extensions/i });
        await user.click(extBtn);
        expect(screen.getByRole('dialog', { name: /Extensions panel/i })).toBeInTheDocument();
    });
});
