/**
 * Navigation configuration for Flashit Mobile
 * Uses React Navigation with stack navigator
 */

import React, { useEffect, useState } from 'react';
import { NavigationContainer } from '@react-navigation/native';
import { createNativeStackNavigator } from '@react-navigation/native-stack';
import { createBottomTabNavigator } from '@react-navigation/bottom-tabs';
import { useAtomValue } from 'jotai';
import { View, Text, ActivityIndicator } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { mobileAtoms } from '../state/localAtoms';
import { ApiProvider } from '../contexts/ApiContext';

// Screens
import LoginScreen from '../screens/LoginScreen';
import RegisterScreen from '../screens/RegisterScreen';
import DashboardScreen from '../screens/DashboardScreen';
import CaptureScreen from '../screens/CaptureScreen';
import MomentsScreen from '../screens/MomentsScreen';
import SpheresScreen from '../screens/SpheresScreen';
import LanguageInsightsScreen from '../screens/LanguageInsightsScreen';
import { UnifiedCaptureScreen } from '../screens/UnifiedCaptureScreen';
import { VoiceRecorderScreen } from '../screens/VoiceRecorderScreen';
import { ImageCaptureScreen } from '../screens/ImageCaptureScreen';
import { VideoRecorderScreen } from '../screens/VideoRecorderScreen';
import NotificationSettingsScreen from '../screens/NotificationSettingsScreen';
import SearchScreen from '../screens/SearchScreen';
import AnalyticsScreen from '../screens/AnalyticsScreen';
import BillingScreen from '../screens/BillingScreen';
import CollaborationScreen from '../screens/CollaborationScreen';
import ReflectionScreen from '../screens/ReflectionScreen';
import MemoryExpansionScreen from '../screens/MemoryExpansionScreen';
import { MultimediaTest } from '../components/MultimediaTest';

export type RootStackParamList = {
  Auth: undefined;
  Main: undefined;
  TextCapture: { audioUri?: string; imageUri?: string; videoUri?: string } | undefined;
  VoiceRecorder: undefined;
  ImageCapture: undefined;
  VideoRecorder: undefined;
  LanguageInsights: undefined;
  NotificationSettings: undefined;
  Search: undefined;
  Analytics: undefined;
  Billing: undefined;
  Collaboration: undefined;
  Reflection: undefined;
  MemoryExpansion: undefined;
  MultimediaTest: undefined;
  Login: undefined;
  Register: undefined;
};

export type MainTabParamList = {
  Dashboard: undefined;
  Moments: undefined;
  Capture: undefined;
  Spheres: undefined;
  Settings: undefined;
};

const Stack = createNativeStackNavigator<RootStackParamList>();
const Tab = createBottomTabNavigator<MainTabParamList>();

// Import SettingsScreen
import { SettingsScreen } from '../screens/SettingsScreen';

// Loading component for auth state
function AuthLoading() {
  return (
    <View style={{
      flex: 1,
      justifyContent: 'center',
      alignItems: 'center',
      backgroundColor: '#f0f9ff'
    }}>
      <ActivityIndicator size="large" color="#0ea5e9" />
      <Text style={{
        marginTop: 16,
        color: '#64748b',
        fontSize: 16
      }}>
        Loading...
      </Text>
    </View>
  );
}

// Main Tab Navigator with accessibility
function MainTabNavigator() {
  return (
    <Tab.Navigator
      screenOptions={({ route }: { route: { name: string } }) => ({
        tabBarIcon: ({ focused, color, size }: { focused: boolean; color: string; size: number }) => {
          let iconName: keyof typeof Ionicons.glyphMap;

          if (route.name === 'Dashboard') {
            iconName = focused ? 'home' : 'home-outline';
          } else if (route.name === 'Moments') {
            iconName = focused ? 'time' : 'time-outline';
          } else if (route.name === 'Capture') {
            iconName = focused ? 'add-circle' : 'add-circle-outline';
          } else if (route.name === 'Spheres') {
            iconName = focused ? 'grid' : 'grid-outline';
          } else if (route.name === 'Settings') {
            iconName = focused ? 'settings' : 'settings-outline';
          } else {
            iconName = 'ellipse';
          }

          return <Ionicons name={iconName} size={size} color={color} />;
        },
        tabBarActiveTintColor: '#007aff',
        tabBarInactiveTintColor: '#8e8e93',
        tabBarStyle: {
          backgroundColor: '#ffffff',
          borderTopColor: '#e5e5e5',
          borderTopWidth: 1,
          paddingBottom: 8,
          paddingTop: 8,
          height: 88,
        },
        tabBarLabelStyle: {
          fontSize: 12,
          fontWeight: '600',
        },
        headerStyle: {
          backgroundColor: '#0ea5e9',
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
        options={{
          title: 'Home',
          tabBarAccessibilityLabel: 'View your dashboard',
          tabBarAccessibilityHint: 'Navigate to your main dashboard',
        }}
      />
      <Tab.Screen 
        name="Moments" 
        component={MomentsScreen}
        options={{
          title: 'Moments',
          tabBarAccessibilityLabel: 'View your moments',
          tabBarAccessibilityHint: 'Navigate to your captured moments',
        }}
      />
      <Tab.Screen 
        name="Capture" 
        component={UnifiedCaptureScreen}
        options={{
          title: 'Capture',
          tabBarAccessibilityLabel: 'Capture a new moment',
          tabBarAccessibilityHint: 'Create a new moment with text, voice, photo, or video',
        }}
      />
      <Tab.Screen 
        name="Spheres" 
        component={SpheresScreen}
        options={{
          title: 'Spheres',
          tabBarAccessibilityLabel: 'View your spheres',
          tabBarAccessibilityHint: 'Navigate to your contextual spheres',
        }}
      />
      <Tab.Screen 
        name="Settings" 
        component={SettingsScreen}
        options={{
          title: 'Settings',
          tabBarAccessibilityLabel: 'View settings',
          tabBarAccessibilityHint: 'Navigate to app settings and preferences',
        }}
      />
    </Tab.Navigator>
  );
}

function AppNavigator() {
  const isAuthenticated = useAtomValue(mobileAtoms.isAuthenticatedAtom);
  const [isInitializing, setIsInitializing] = useState(true);

  useEffect(() => {
    // Allow time for AsyncStorage to be read and auth state to be determined
    const timer = setTimeout(() => {
      setIsInitializing(false);
    }, 1000);

    return () => clearTimeout(timer);
  }, []);

  if (isInitializing) {
    return <AuthLoading />;
  }

  return (
    <Stack.Navigator
      screenOptions={{
        headerStyle: {
          backgroundColor: '#0ea5e9',
        },
        headerTintColor: '#fff',
        headerTitleStyle: {
          fontWeight: 'bold',
        },
      }}
    >
      {!isAuthenticated ? (
        // Auth screens
        <>
          <Stack.Screen
            name="Auth"
            component={AuthNavigator}
            options={{ headerShown: false }}
          />
        </>
      ) : (
        // App screens with bottom tabs
        <>
          <Stack.Screen
            name="Main"
            component={MainTabNavigator}
            options={{ headerShown: false }}
          />
          <Stack.Screen
            name="TextCapture"
            component={CaptureScreen}
            options={{ title: 'Text Capture' }}
          />
          <Stack.Screen
            name="VoiceRecorder"
            component={VoiceRecorderScreen}
            options={{
              title: 'Voice Recorder',
              headerShown: false,
              presentation: 'fullScreenModal'
            }}
          />
          <Stack.Screen
            name="ImageCapture"
            component={ImageCaptureScreen}
            options={{
              title: 'Camera',
              headerShown: false,
              presentation: 'fullScreenModal'
            }}
          />
          <Stack.Screen
            name="VideoRecorder"
            component={VideoRecorderScreen}
            options={{
              title: 'Video Recorder',
              headerShown: false,
              presentation: 'fullScreenModal'
            }}
          />
          <Stack.Screen
            name="LanguageInsights"
            component={LanguageInsightsScreen}
            options={{ title: 'Language Insights' }}
          />
          <Stack.Screen
            name="NotificationSettings"
            component={NotificationSettingsScreen}
            options={{ title: 'Notification Settings' }}
          />
          <Stack.Screen
            name="Search"
            component={SearchScreen}
            options={{ title: 'Search' }}
          />
          <Stack.Screen
            name="Analytics"
            component={AnalyticsScreen}
            options={{ title: 'Analytics' }}
          />
          <Stack.Screen
            name="Billing"
            component={BillingScreen}
            options={{ title: 'Subscription' }}
          />
          <Stack.Screen
            name="Collaboration"
            component={CollaborationScreen}
            options={{ title: 'Collaboration' }}
          />
          <Stack.Screen
            name="Reflection"
            component={ReflectionScreen}
            options={{ title: 'Reflection' }}
          />
          <Stack.Screen
            name="MemoryExpansion"
            component={MemoryExpansionScreen}
            options={{ title: 'Memory Expansion' }}
          />
          <Stack.Screen
            name="MultimediaTest"
            component={MultimediaTest}
            options={{ title: 'Multimedia Test' }}
          />
        </>
      )}
    </Stack.Navigator>
  );
}

// Auth Navigator for login/register screens
function AuthNavigator() {
  return (
    <Stack.Navigator>
      <Stack.Screen
        name="Login"
        component={LoginScreen}
        options={{ headerShown: false }}
      />
      <Stack.Screen
        name="Register"
        component={RegisterScreen}
        options={{ headerShown: false }}
      />
    </Stack.Navigator>
  );
}

export default function Navigation() {
  return (
    <NavigationContainer>
      <ApiProvider>
        <AppNavigator />
      </ApiProvider>
    </NavigationContainer>
  );
}

