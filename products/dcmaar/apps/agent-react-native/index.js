/**
 * Guardian Agent - React Native entry point
 */

import { AppRegistry } from 'react-native';
import App from './src/App';
import { expo } from './app.json';

AppRegistry.registerComponent(expo.name || 'GuardianAgent', () => App);
