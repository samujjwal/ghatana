/**
 * Tab Navigator
 *
 * Main tab-based navigation for the mobile app.
 * Provides Learn, Explore, and Profile tabs.
 *
 * @doc.type component
 * @doc.purpose Main tab navigation
 * @doc.layer product
 * @doc.pattern Navigation
 */

import React from 'react';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { Text, StyleSheet } from 'react-native';

import { LearnStackNavigator } from './LearnStackNavigator';
import { ExploreStackNavigator } from './ExploreStackNavigator';
import { ProfileStackNavigator } from './ProfileStackNavigator';
import type { TabParamList } from './types';

const Tab = createBottomTabNavigator<TabParamList>();

function TabIcon({ icon, label, focused }: { icon: string; label: string; focused: boolean }): React.ReactElement {
  return (
    <Text style={[styles.icon, focused && styles.iconFocused]}>
      {icon}
    </Text>
  );
}

export function TabNavigator(): React.ReactElement {
  return (
    <Tab.Navigator
      screenOptions={{
        headerShown: false,
        tabBarActiveTintColor: '#4F46E5',
        tabBarInactiveTintColor: '#6B7280',
        tabBarStyle: styles.tabBar,
        tabBarLabelStyle: styles.tabLabel,
      }}
    >
      <Tab.Screen
        name="Learn"
        component={LearnStackNavigator}
        options={{
          tabBarIcon: ({ focused }: { focused: boolean }) => <TabIcon icon="📚" label="Learn" focused={focused} />,
          tabBarLabel: 'Learn',
        }}
      />
      <Tab.Screen
        name="Explore"
        component={ExploreStackNavigator}
        options={{
          tabBarIcon: ({ focused }: { focused: boolean }) => <TabIcon icon="🔍" label="Explore" focused={focused} />,
          tabBarLabel: 'Explore',
        }}
      />
      <Tab.Screen
        name="Profile"
        component={ProfileStackNavigator}
        options={{
          tabBarIcon: ({ focused }: { focused: boolean }) => <TabIcon icon="👤" label="Profile" focused={focused} />,
          tabBarLabel: 'Profile',
        }}
      />
    </Tab.Navigator>
  );
}

const styles = StyleSheet.create({
  tabBar: {
    backgroundColor: '#FFFFFF',
    borderTopWidth: 1,
    borderTopColor: '#E5E7EB',
    paddingBottom: 8,
    paddingTop: 8,
    height: 64,
  },
  tabLabel: {
    fontSize: 12,
    fontWeight: '500',
  },
  icon: {
    fontSize: 24,
    opacity: 0.6,
  },
  iconFocused: {
    opacity: 1,
  },
});
