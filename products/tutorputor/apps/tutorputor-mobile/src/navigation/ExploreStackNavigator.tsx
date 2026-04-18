/**
 * Explore Stack Navigator
 *
 * Navigation stack for the Explore tab containing search,
 * content discovery, and marketplace.
 *
 * @doc.type component
 * @doc.purpose Explore tab navigation stack
 * @doc.layer product
 * @doc.pattern Navigation
 */

import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';

import { SearchScreen } from '../screens/SearchScreen';
import { MarketplaceScreen } from '../screens/MarketplaceScreen';
import { ModuleDetailScreen } from '../screens/ModuleDetailScreen';
import type { ExploreStackParamList } from './types';

const Stack = createNativeStackNavigator<ExploreStackParamList>();

export function ExploreStackNavigator(): React.ReactElement {
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
        name="Search"
        component={SearchScreen}
        options={{ title: 'Discover', headerShown: false }}
      />
      <Stack.Screen
        name="Marketplace"
        component={MarketplaceScreen}
        options={{ title: 'Marketplace' }}
      />
      <Stack.Screen
        name="ModuleDetail"
        component={ModuleDetailScreen}
        options={{ title: 'Module Details' }}
      />
    </Stack.Navigator>
  );
}
