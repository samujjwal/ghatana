// Minimal mock for @notifee/react-native used in tests
// Minimal mock for @notifee/react-native used in tests
// Expose simple constants (AndroidImportance, TriggerType) and the
// function surface used by NotificationService.
const AndroidImportance = {
  NONE: 0,
  MIN: 1,
  LOW: 2,
  DEFAULT: 3,
  HIGH: 4,
  MAX: 5,
};

const TriggerType = {
  TIMESTAMP: 'timestamp',
  INTERVAL: 'interval',
};

const notifee = {
  registerChannel: jest.fn(() => Promise.resolve()),
  getChannels: jest.fn(() => Promise.resolve([])),
  displayNotification: jest.fn(() => Promise.resolve()),
  cancelNotification: jest.fn(() => Promise.resolve()),
  AndroidImportance,
  TriggerType,
};

module.exports = notifee;
