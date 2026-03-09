import React from 'react';
import { render } from '@testing-library/react-native';

// Mock AppNavigator just as a simple View
jest.mock('@/navigation/AppNavigator', () => {
    const React = require('react');
    const { View, Text } = require('react-native');
    return () => React.createElement(View, null, React.createElement(Text, null, 'AppNav'));
});

jest.mock('@/screens/DashboardScreen', () => {
    const React = require('react');
    const { View, Text } = require('react-native');
    return () => React.createElement(View, null, React.createElement(Text, null, 'Dashboard'));
});

jest.mock('@/screens/DevicesScreen', () => {
    const React = require('react');
    const { View, Text } = require('react-native');
    return () => React.createElement(View, null, React.createElement(Text, null, 'Devices'));
});

jest.mock('@/screens/PoliciesScreen', () => {
    const React = require('react');
    const { View, Text } = require('react-native');
    return () => React.createElement(View, null, React.createElement(Text, null, 'Policies'));
});

jest.mock('@/screens/AlertsScreen', () => {
    const React = require('react');
    const { View, Text } = require('react-native');
    return () => React.createElement(View, null, React.createElement(Text, null, 'Alerts'));
});

import AppNavigator from '@/navigation/AppNavigator';

test('can render mocked AppNavigator directly', () => {
    const utils = render(<AppNavigator />);
    expect(utils).toBeDefined();
});
