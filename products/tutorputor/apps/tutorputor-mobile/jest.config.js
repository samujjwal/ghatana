module.exports = {
  preset: "@react-native/jest-preset",
  transform: {
    "^.+\\.(js|jsx|ts|tsx)$": "babel-jest",
  },
  transformIgnorePatterns: [
    "node_modules/(?!.*(react-native|@react-native|@react-native-community|@react-navigation|@tanstack/react-query|react-native-mmkv|react-native-safe-area-context|react-native-screens|react-native-keychain)).*",
  ],
  moduleNameMapper: {
    // Allow virtual mocks for native modules not yet installed
    "^react-native-keychain$":
      "<rootDir>/src/__mocks__/react-native-keychain.ts",
    "^react-test-renderer$":
      "<rootDir>/../../../../node_modules/.pnpm/react-test-renderer@19.2.4_react@19.2.4/node_modules/react-test-renderer",
    "^react-test-renderer/(.*)$":
      "<rootDir>/../../../../node_modules/.pnpm/react-test-renderer@19.2.4_react@19.2.4/node_modules/react-test-renderer/$1",
  },
  moduleFileExtensions: ["ts", "tsx", "js", "jsx", "json", "node"],
  testMatch: ["**/__tests__/**/*.test.ts?(x)"],
};
