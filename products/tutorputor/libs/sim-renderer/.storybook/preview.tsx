import type { Preview } from '@storybook/react';
import React from 'react';

const preview: Preview = {
    parameters: {
        backgrounds: {
            default: 'light',
            values: [
                { name: 'light', value: '#f8fafc' },
                { name: 'dark', value: '#0f172a' },
                { name: 'gray', value: '#e2e8f0' },
            ],
        },
        controls: {
            matchers: {
                color: /(background|color)$/i,
                date: /Date$/i,
            },
        },
        viewport: {
            viewports: {
                small: { name: 'Small', styles: { width: '400px', height: '300px' } },
                medium: { name: 'Medium', styles: { width: '800px', height: '600px' } },
                large: { name: 'Large', styles: { width: '1200px', height: '800px' } },
            },
        },
    },
    decorators: [
        (Story) => (
            <div style={{ padding: '1rem' }}>
                <Story />
            </div>
        ),
    ],
};

export default preview;
