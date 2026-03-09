import React from 'react';
import rt from 'react-test-renderer';

describe('react-test-renderer minimal sanity', () => {
    function debugEnv(tag: string) {
         
        console.log(`[RT-MINIMAL] ${tag} IS_REACT_ACT_ENVIRONMENT:`, (global as any).IS_REACT_ACT_ENVIRONMENT);
         
        console.log('[RT-MINIMAL] react internals keys:', Object.keys((React as any).__SECRET_INTERNALS_DO_NOT_USE_OR_YOU_WILL_BE_FIRED || {}));
         
        console.log('[RT-MINIMAL] rt keys:', Object.keys(rt));
         
        console.log('[RT-MINIMAL] rt.act exists:', typeof (rt as any).act === 'function');
    }

    it('renders a plain host element (div)', () => {
        debugEnv('before-create');
        // sanity: render a simple DOM element
        // Try wrapping with act to see if behavior changes
        let renderer: any;
        if ((rt as any).act) {
            (rt as any).act(() => {
                renderer = rt.create(React.createElement('div', null, 'hello'));
            });
        } else {
            renderer = rt.create(React.createElement('div', null, 'hello'));
        }
        // debug after create
         
        console.log('[RT-MINIMAL] created renderer keys:', Object.keys(renderer || {}));
        // access root to ensure it's mounted
        const root = renderer.root;
        expect(root).toBeDefined();
        expect(root.children.length).toBeGreaterThanOrEqual(1);
        renderer.unmount();
    });

    it('renders a simple functional component', () => {
        function Foo() {
            return React.createElement('span', null, 'span');
        }
        debugEnv('before-create-fn');
        let renderer: any;
        if ((rt as any).act) {
            (rt as any).act(() => {
                renderer = rt.create(React.createElement(Foo));
            });
        } else {
            renderer = rt.create(React.createElement(Foo));
        }
         
        console.log('[RT-MINIMAL] created renderer keys (fn):', Object.keys(renderer || {}));
        expect(renderer.root).toBeDefined();
        renderer.unmount();
    });

    it('prints versions and resolved paths for debug', () => {
        // Print versions to help debug resolution issues
         
        console.log('[RT-MINIMAL] react version:', require('react/package.json').version);
         
        console.log('[RT-MINIMAL] react-test-renderer version:', require('react-test-renderer/package.json').version);
         
        console.log('[RT-MINIMAL] react resolved:', require.resolve('react'));
         
        console.log('[RT-MINIMAL] react-test-renderer resolved:', require.resolve('react-test-renderer'));
        expect(true).toBe(true);
    });
});
