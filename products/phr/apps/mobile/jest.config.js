module.exports = {
  preset: 'jest-expo/universal',
  setupFilesAfterEnv: ['./jest.setup.js'],
  transformIgnorePatterns: [
    'node_modules/(?!(react-native|@react-native|expo(nent)?|@expo(nent)?/.*|expo-.*|@expo/.*))',
  ],
};