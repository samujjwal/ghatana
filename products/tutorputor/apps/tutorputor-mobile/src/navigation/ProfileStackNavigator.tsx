/**
 * Profile Stack Navigator
 *
 * Navigation stack for the Profile tab containing user profile,
 * downloads, settings, and achievements.
 *
 * @doc.type component
 * @doc.purpose Profile tab navigation stack
 * @doc.layer product
 * @doc.pattern Navigation
 */

import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';

import { ProfileScreen } from '../screens/ProfileScreen';
import { DownloadsScreen } from '../screens/DownloadsScreen';
import { SettingsScreen } from '../screens/SettingsScreen';
import { AchievementsScreen } from '../screens/AchievementsScreen';
import type { ProfileStackParamList } from './types';

const Stack = createNativeStackNavigator<ProfileStackParamList>();

export function ProfileStackNavigator(): React.ReactElement {
  return (
    <Stack.Navigator
      screenOptions={{
        headerStyle: {
          backgroundColor: '#4F46E5',
        },
        headerTintColor: '#fff',
        headerTitleStyle: {
          fontWeight: '600',
        },
      }}
    >
      <Stack.Screen
        name="ProfileMain"
        component={ProfileScreen}
        options={{ title: 'My Profile' }}
      />
      <Stack.Screen
        name="Downloads"
        component={DownloadsScreen}
        options={{ title: 'Downloads' }}
      />
      <Stack.Screen
        name="Settings"
        component={SettingsScreen}
        options={{ title: 'Settings' }}
      />
      <Stack.Screen
        name="Achievements"
        component={AchievementsScreen}
        options={{ title: 'Achievements' }}
      />
    </Stack.Navigator>
  );
}
