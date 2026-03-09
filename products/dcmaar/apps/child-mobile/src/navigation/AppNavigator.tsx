import React from 'react';
// Diagnostic hook for Jest: prints when this module is evaluated
/* istanbul ignore next */
console.log('[JEST-DIAG] AppNavigator module loaded');
import { NavigationContainer } from '@react-navigation/native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import Icon from '@react-native-vector-icons/material-icons';
import DashboardScreen from '@/screens/DashboardScreen';
import UsageScreen from '@/screens/UsageScreen';
import BlocksScreen from '@/screens/BlocksScreen';
import SettingsScreen from '@/screens/SettingsScreen';

// Type assertion workaround for React 19 + React Navigation compatibility
const Tab = createBottomTabNavigator() as any;
const NavContainer = NavigationContainer as any;

const AppNavigator: React.FC = () => {
  return (
    <NavContainer>
      <Tab.Navigator
        screenOptions={({ route }: any) => ({
          tabBarIcon: ({ focused, color, size }: any) => {
            let iconName: string;

            switch (route.name) {
              case 'Dashboard':
                iconName = 'dashboard';
                break;
              case 'Usage':
                iconName = 'bar-chart';
                break;
              case 'Blocks':
                iconName = 'block';
                break;
              case 'Settings':
                iconName = 'settings';
                break;
              default:
                iconName = 'help';
            }

            return <Icon name={iconName} size={size} color={color} />;
          },
          tabBarActiveTintColor: '#3b82f6',
          tabBarInactiveTintColor: 'gray',
          headerStyle: {
            backgroundColor: '#3b82f6',
          },
          headerTintColor: '#fff',
          headerTitleStyle: {
            fontWeight: 'bold',
          },
        })}
      >
        <Tab.Screen
          name="Dashboard"
          component={DashboardScreen}
          options={{ title: 'Guardian' }}
        />
        <Tab.Screen
          name="Usage"
          component={UsageScreen}
          options={{ title: 'Usage' }}
        />
        <Tab.Screen
          name="Blocks"
          component={BlocksScreen}
          options={{ title: 'Blocks' }}
        />
        <Tab.Screen
          name="Settings"
          component={SettingsScreen}
          options={{ title: 'Settings' }}
        />
      </Tab.Navigator>
    </NavContainer>
  );
};

export default AppNavigator;
