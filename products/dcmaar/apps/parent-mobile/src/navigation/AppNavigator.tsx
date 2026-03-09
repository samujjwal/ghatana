import React from 'react';
// Diagnostic hook for Jest: prints when this module is evaluated
/* istanbul ignore next */
console.log('[JEST-DIAG] AppNavigator module loaded');
import { NavigationContainer } from '@react-navigation/native';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import Icon from '@react-native-vector-icons/material-icons';
import DashboardScreen from '@/screens/DashboardScreen';
import DevicesScreen from '@/screens/DevicesScreen';
import PoliciesScreen from '@/screens/PoliciesScreen';
import AlertsScreen from '@/screens/AlertsScreen';

// Type assertion workaround for React 19 + React Navigation compatibility
const Tab = createBottomTabNavigator() as any;
const NavContainer = NavigationContainer as any;

const AppNavigator: React.FC = () => {
  return (
    <NavContainer>
      <Tab.Navigator
        screenOptions={({ route }: any) => ({
          tabBarIcon: ({ _focused, color, size }: any) => {
            let iconName: string;

            switch (route.name) {
              case 'Dashboard':
                iconName = 'dashboard';
                break;
              case 'Devices':
                iconName = 'devices';
                break;
              case 'Policies':
                iconName = 'security';
                break;
              case 'Alerts':
                iconName = 'notifications';
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
          name="Devices"
          component={DevicesScreen}
          options={{ title: 'Devices' }}
        />
        <Tab.Screen
          name="Policies"
          component={PoliciesScreen}
          options={{ title: 'Policies' }}
        />
        <Tab.Screen
          name="Alerts"
          component={AlertsScreen}
          options={{ title: 'Alerts' }}
        />
      </Tab.Navigator>
    </NavContainer>
  );
};

export default AppNavigator;
