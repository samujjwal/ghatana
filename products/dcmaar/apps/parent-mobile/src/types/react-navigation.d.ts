/**
 * Type declaration fixes for React Navigation v6 with React 18
 * This resolves compatibility issues between React Navigation's types
 * and React 18's ReactNode definition
 */

declare module '@react-navigation/native' {
  import type { ComponentType } from 'react';

  // Re-export NavigationContainer with correct types for React 18
  export const NavigationContainer: ComponentType<unknown>;
}

declare module '@react-navigation/stack' {
  import type { ComponentType } from 'react';

  // Re-export Stack navigator with correct types for React 18  
  export interface StackNavigatorType {
    Navigator: ComponentType<unknown>;
    Screen: ComponentType<unknown>;
  }

  export function createStackNavigator(): StackNavigatorType;
}

declare module '@react-navigation/bottom-tabs' {
  import type { ComponentType } from 'react';

  // Re-export Tab navigator with correct types for React 18
  export interface BottomTabNavigatorType {
    Navigator: ComponentType<unknown>;
    Screen: ComponentType<unknown>;
  }

  export function createBottomTabNavigator(): BottomTabNavigatorType;
}

// Fix for react-native-vector-icons missing types
declare module '@react-native-vector-icons/material-icons' {
  import { ComponentType } from 'react';
  import { TextProps } from 'react-native';

  export interface IconProps extends TextProps {
    name: string;
    size?: number;
    color?: string;
  }

  const Icon: ComponentType<IconProps>;
  export default Icon;
}
