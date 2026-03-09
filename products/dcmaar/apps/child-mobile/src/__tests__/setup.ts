import '@testing-library/jest-native/extend-expect';
// Allow skipping the custom setup during diagnostics by setting
// JEST_SKIP_CUSTOM_SETUP=1 in the environment. This helps isolate whether
// the project's test setup is causing the react-test-renderer to be
// unmounted immediately during host-component detection.
if (!process.env.JEST_SKIP_CUSTOM_SETUP) {
  // Ensure React's act environment is enabled for the test runner
  // This makes React's test utils (act) behave correctly in Jest
  // and silences warnings about unsupported act environments.
  // See: https://react.dev/reference/react-dom/test-utils#testing-environments
  // and React 18/19 testing migration notes.
  // eslint-disable-next-line no-restricted-globals
  (global as any).IS_REACT_ACT_ENVIRONMENT = true;
  // Ensure React internals object exists for older test utilities (shallow renderer)
  // Some shallow renderer builds access React.__SECRET_INTERNALS_DO_NOT_USE_OR_YOU_WILL_BE_FIRED
  // directly and assume it's an object. Defensive-assign an empty object if missing.
  try {
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const React = require('react');
    if (!React.__SECRET_INTERNALS_DO_NOT_USE_OR_YOU_WILL_BE_FIRED) {
      React.__SECRET_INTERNALS_DO_NOT_USE_OR_YOU_WILL_BE_FIRED = {};
    }
  } catch (e) {
    // ignore if react cannot be required in this environment (rare)
  }
  // Print versions and resolved paths for React and react-test-renderer to help
  // diagnose multiple-copy or mismatched-version issues that cause the test
  // renderer to be unmounted during host-component detection.
  try {
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const React = require('react');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const rtPkg = require('react-test-renderer/package.json');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const reactPkg = require('react/package.json');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const reactResolved = require.resolve('react');
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const rtResolved = require.resolve('react-test-renderer');
    console.log('[JEST-DIAG] React version:', reactPkg.version || React.version);
    console.log('[JEST-DIAG] react-test-renderer version:', rtPkg.version);
    console.log('[JEST-DIAG] resolved react:', reactResolved);
    console.log('[JEST-DIAG] resolved react-test-renderer:', rtResolved);
  } catch (e) {
    console.log('[JEST-DIAG] version detection error:', e && e.stack ? e.stack : e);
  }
  // Temporary: augment console.error to also print a stack trace for debugging
  // failing mounts. We filter out the well-known deprecation message from
  // react-test-renderer to avoid noise.
  const _origConsoleError = console.error;
  console.error = (...args: any[]) => {
    try {
      const first = String(args[0] || '');
      if (!first.includes('react-test-renderer is deprecated')) {
        // Print a stack to help locate the original error during test runs
        _origConsoleError('[JEST-DEBUG] console.error called with:', ...args);
        _origConsoleError(new Error('console.error stack trace').stack);
      } else {
        _origConsoleError(...args);
      }
    } catch (e) {
      _origConsoleError(...args);
    }
  };
  // react-native-gesture-handler provides a jest setup helper. Require it
  // conditionally to avoid module-not-found errors in monorepo/test envs.
  try {
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    require('react-native-gesture-handler/jestSetup');
  } catch (e) {
    // not installed in this environment — ignore
  }

  global.fetch = jest.fn(() =>
    Promise.resolve({
      ok: true,
      json: async () => ({}),
    })
  ) as jest.Mock;

  jest.mock('@react-native-async-storage/async-storage', () =>
    require('@react-native-async-storage/async-storage/jest/async-storage-mock')
  );

  jest.mock('react-native/Libraries/Animated/NativeAnimatedHelper');

  // Replace @react-navigation/native with a lightweight test-friendly mock.
  // The real NavigationContainer and helpers depend on native behavior and
  // internal context; for Jest unit/e2e tests we provide simple passthrough
  // implementations and small hook stubs so AppNavigator can render.
  jest.mock('@react-navigation/native', () => {
    const React = require('react');

    // Minimal navigation ref factory used by some apps
    function createNavigationContainerRef() {
      return {
        current: null,
      };
    }

    // Passthrough NavigationContainer
    function NavigationContainer({ children }: any) {
      // Use react-native's View so RN testing library can detect host components
      // instead of using raw string tags which can confuse host detection.
      // eslint-disable-next-line @typescript-eslint/no-var-requires
      const { View } = require('react-native');
      return React.createElement(View, null, children);
    }

    // Hook stubs
    function useNavigation() {
      return {
        navigate: jest.fn(),
        goBack: jest.fn(),
        dispatch: jest.fn(),
      };
    }

    function useRoute() {
      return { name: 'mock' };
    }

    function useIsFocused() {
      return true;
    }

    // Provide CommonActions and a no-op linking helper to satisfy imports
    const CommonActions = {
      navigate: (...args: any[]) => ({ type: 'NAVIGATE', payload: args }),
    };

    return {
      NavigationContainer,
      createNavigationContainerRef,
      useNavigation,
      useRoute,
      useIsFocused,
      CommonActions,
    };
  });

  // react-native-screens uses native components that can cause the test renderer
  // to short-circuit (unmount) when running in a pure JS test environment.
  // Mock enableScreens to a no-op to keep react-navigation working in Jest.
  jest.mock('react-native-screens', () => ({
    enableScreens: jest.fn(),
  }));

  // Provide a lightweight mock for react-native-gesture-handler so components
  // expecting GestureHandlerRootView or other RNGH exports can render in Jest.
  try {
    // Only mock if the package is resolvable in this environment. In some
    // workspace setups the module may not be present at package level.
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    require.resolve('react-native-gesture-handler');

    jest.mock('react-native-gesture-handler', () => {
      const React = require('react');
      // Use react-native host View so RN testing lib sees expected host components
      // eslint-disable-next-line @typescript-eslint/no-var-requires
      const { View } = require('react-native');

      return {
        // Some RN libraries use GestureHandlerRootView as a wrapper component.
        GestureHandlerRootView: ({ children }: any) => React.createElement(View, null, children),
        // Expose any other commonly-used RNGH helpers as no-ops to avoid runtime errors.
        Swipeable: ({ children }: any) => React.createElement(View, null, children),
        PanGestureHandler: ({ children }: any) => React.createElement(View, null, children),
        State: {},
        // jestSetup is handled separately via moduleNameMapper => we still provide
        // the module shape here for direct imports.
      };
    });
  } catch (e) {
    // Not installed/resolvable in this package; skip mock.
  }

  jest.mock('@react-navigation/native-stack', () => ({
    createNativeStackNavigator: () => ({
      Navigator: ({ children }: any) => children,
      Screen: ({ children }: any) => children,
    }),
  }));

  jest.mock('@react-native-vector-icons/material-icons', () => {
    const React = require('react');
    const { View, Text } = require('react-native');
    // Return a lightweight component that renders a Text node so it's
    // recognized as a host component by RN testing utilities.
    return () => React.createElement(View, null, React.createElement(Text, null, 'icon'));
  });

  // Provide a minimal mock for bottom-tabs navigator used in AppNavigator. The
  // real implementation depends on native navigation internals, which are
  // unnecessary for our unit/e2e tests. This mock exposes a Navigator and
  // Screen that render a simple tab bar and switch screens when tab buttons
  // are pressed. It also adds accessibilityRole='tab' to tab buttons so tests
  // can find and press them.
  jest.mock('@react-navigation/bottom-tabs', () => {
    const React = require('react');

    function createBottomTabNavigator() {
      const Navigator: any = ({ children }: any) => {
        const screens = React.Children.toArray(children).filter(Boolean) as any[];
        const [activeIndex, setActiveIndex] = React.useState(0);

        // Render active screen component
        const ActiveScreen = () => {
          const child = screens[activeIndex];
          if (!child) return null;
          const Comp = child.props.component;
          return React.createElement(Comp, child.props);
        };

        // Use react-native host components for proper detection by RN testing lib
        // eslint-disable-next-line @typescript-eslint/no-var-requires
        const { View, Button } = require('react-native');
        return React.createElement(
          View,
          null,
          React.createElement(ActiveScreen, null),
          React.createElement(
            View,
            { accessibilityRole: 'tablist' },
            screens.map((child, i) =>
              React.createElement(Button, {
                key: i,
                accessibilityRole: 'tab',
                onPress: () => setActiveIndex(i),
                title: child.props.name || `tab-${i}`,
              })
            )
          )
        );
      };

      const Screen = ({ children }: any) => children || null;

      return { Navigator, Screen };
    }

    return { createBottomTabNavigator };
  });

  jest.mock('@/services/api', () => require('./mocks/api.mock'));

  beforeEach(() => {
    (global.fetch as jest.Mock).mockClear();
  });
} else {
  // When skipping custom setup, provide minimal shims required by tests
  // so they don't immediately fail due to missing globals.
  if (!(global as any).fetch) {
    // lightweight fetch shim
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (global as any).fetch = jest.fn(() => Promise.resolve({ ok: true, json: async () => ({}) }));
  }
}
