const mockAsyncStorage = new Map();
const mockSecureStorage = new Map();

function mockCreateHostComponent(name) {
  const React = require('react');
  return React.forwardRef((props, ref) => React.createElement(name, { ...props, ref }, props.children));
}

globalThis.__phrMobileTestStorage = {
  asyncStorage: mockAsyncStorage,
  secureStorage: mockSecureStorage,
};

jest.mock('react-native', () => ({
  ActivityIndicator: mockCreateHostComponent('ActivityIndicator'),
  Alert: {
    alert: jest.fn(),
  },
  AppState: {
    currentState: 'active',
    addEventListener: jest.fn(() => ({ remove: jest.fn() })),
  },
  Platform: {
    OS: 'ios',
    select: (values) => values?.ios ?? values?.default,
  },
  Pressable: mockCreateHostComponent('Pressable'),
  SafeAreaView: mockCreateHostComponent('SafeAreaView'),
  ScrollView: mockCreateHostComponent('ScrollView'),
  StyleSheet: {
    create: (styles) => styles,
    flatten: (style) => {
      if (Array.isArray(style)) {
        return Object.assign({}, ...style.filter(Boolean));
      }
      return style ?? {};
    },
  },
  Text: mockCreateHostComponent('Text'),
  TextInput: mockCreateHostComponent('TextInput'),
  TouchableOpacity: mockCreateHostComponent('TouchableOpacity'),
  View: mockCreateHostComponent('View'),
}));

jest.mock('react-native-web/dist/exports/TextInput', () => mockCreateHostComponent('TextInput'));

jest.mock('@react-native-async-storage/async-storage', () => ({
  getItem: jest.fn(async (key) => mockAsyncStorage.get(key) ?? null),
  setItem: jest.fn(async (key, value) => {
    mockAsyncStorage.set(key, value);
  }),
  removeItem: jest.fn(async (key) => {
    mockAsyncStorage.delete(key);
  }),
  getAllKeys: jest.fn(async () => Array.from(mockAsyncStorage.keys())),
  clear: jest.fn(async () => {
    mockAsyncStorage.clear();
  }),
}));

jest.mock('expo-secure-store', () => ({
  AFTER_FIRST_UNLOCK: 'afterFirstUnlock',
  getItemAsync: jest.fn(async (key) => mockSecureStorage.get(key) ?? null),
  setItemAsync: jest.fn(async (key, value) => {
    mockSecureStorage.set(key, value);
  }),
  deleteItemAsync: jest.fn(async (key) => {
    mockSecureStorage.delete(key);
  }),
}));

jest.mock('expo-local-authentication', () => ({
  hasHardwareAsync: jest.fn(async () => true),
  isEnrolledAsync: jest.fn(async () => true),
  authenticateAsync: jest.fn(async () => ({ success: true })),
}));
