import { ExpoConfig, ConfigContext } from 'expo/config';

export default ({ config }: ConfigContext): ExpoConfig => ({
  ...config,
  name: 'Flashit',
  slug: 'flashit',
  version: '0.1.0',
  orientation: 'portrait',
  icon: './assets/icon.png',
  userInterfaceStyle: 'automatic',
  splash: {
    image: './assets/splash.png',
    resizeMode: 'contain',
    backgroundColor: '#0ea5e9',
  },
  assetBundlePatterns: ['**/*'],
  plugins: [
    [
      'expo-audio',
      {
        microphonePermission: 'Allow Flashit to access your microphone to record voice moments.',
      },
    ],
    [
      'expo-video',
      {
        microphonePermission: 'Allow Flashit to access your microphone to record video moments.',
        cameraPermission: 'Allow Flashit to access your camera to capture video moments.',
      },
    ],
  ],
  ios: {
    supportsTablet: true,
    bundleIdentifier: 'org.name.Flashit',
    deploymentTarget: '15.1',
    buildNumber: '1',
    infoPlist: {
      NSCameraUsageDescription: 'Flashit needs access to your camera to capture video moments.',
      NSMicrophoneUsageDescription: 'Flashit needs access to your microphone to record voice moments.',
      NSPhotoLibraryUsageDescription: 'Flashit needs access to your photos to select images for moments.',
    },
  },
  android: {
    adaptiveIcon: {
      foregroundImage: './assets/adaptive-icon.png',
      backgroundColor: '#0ea5e9',
    },
    package: 'com.ghatana.flashit',
    permissions: [
      'android.permission.CAMERA',
      'android.permission.RECORD_AUDIO',
      'android.permission.READ_EXTERNAL_STORAGE',
      'android.permission.WRITE_EXTERNAL_STORAGE',
    ],
  },
  web: {
    favicon: './assets/favicon.png',
    bundler: 'metro',
    template: './web/index.html',
  },
  scheme: 'flashit',
  entryPoint: './index.js',
  extra: {
    apiUrl: process.env.EXPO_PUBLIC_API_URL || 'http://localhost:3002',
    eas: {
      projectId: "your-project-id"
    }
  },
});
