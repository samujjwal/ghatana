/**
 * Learn Stack Navigator
 *
 * Navigation stack for the Learn tab containing dashboard,
 * modules, lessons, and AI tutor.
 *
 * @doc.type component
 * @doc.purpose Learn tab navigation stack
 * @doc.layer product
 * @doc.pattern Navigation
 */

import React from 'react';
import { createNativeStackNavigator } from '@react-navigation/native-stack';

import { DashboardScreen } from '../screens/DashboardScreen';
import { ModulesScreen } from '../screens/ModulesScreen';
import { ModuleDetailScreen } from '../screens/ModuleDetailScreen';
import { LessonScreen } from '../screens/LessonScreen';
import { QuizScreen } from '../screens/QuizScreen';
import { AITutorScreen } from '../screens/AITutorScreen';
import { EnrollmentsScreen } from '../screens/EnrollmentsScreen';
import type { LearnStackParamList } from './types';

const Stack = createNativeStackNavigator<LearnStackParamList>();

export function LearnStackNavigator(): React.ReactElement {
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
        name="Dashboard"
        component={DashboardScreen}
        options={{ headerShown: false }}
      />
      <Stack.Screen
        name="Enrollments"
        component={EnrollmentsScreen}
        options={{ title: 'My Learning' }}
      />
      <Stack.Screen
        name="Modules"
        component={ModulesScreen}
        options={{ title: 'Browse Modules' }}
      />
      <Stack.Screen
        name="ModuleDetail"
        component={ModuleDetailScreen}
        options={{ title: 'Module Details' }}
      />
      <Stack.Screen
        name="Lesson"
        component={LessonScreen}
        options={{ title: 'Lesson' }}
      />
      <Stack.Screen
        name="Quiz"
        component={QuizScreen}
        options={{ title: 'Assessment' }}
      />
      <Stack.Screen
        name="AITutor"
        component={AITutorScreen}
        options={{ title: 'AI Tutor', headerShown: false }}
      />
    </Stack.Navigator>
  );
}
