import '@testing-library/jest-native/extend-expect';

// Mock native modules
jest.mock('react-native/Libraries/Animated/NativeAnimatedHelper');
jest.mock('./src/shared/native-bridge', () => ({
  NativeModules: {
    GuardianNativeModule: {
      getDeviceInfo: jest.fn(),
      getAppList: jest.fn(),
      initiateSync: jest.fn()
    }
  }
}));
