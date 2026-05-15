const store = new Map();

const AsyncStorageMock = {
  getItem: jest.fn(async key => (store.has(key) ? store.get(key) : null)),
  setItem: jest.fn(async (key, value) => {
    store.set(key, String(value));
  }),
  removeItem: jest.fn(async key => {
    store.delete(key);
  }),
  clear: jest.fn(async () => {
    store.clear();
  }),
  getAllKeys: jest.fn(async () => Array.from(store.keys())),
  multiRemove: jest.fn(async keys => {
    for (const key of keys) {
      store.delete(key);
    }
  }),
};

module.exports = AsyncStorageMock;
