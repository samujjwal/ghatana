// Lightweight keystore wrapper with in-memory fallback for local/dev
export default class Keystore {
  private store: Map<string, string> = new Map();

  async get(key: string): Promise<string | null> {
    // TODO: wire OS keyring (keytar/node-keytar) in production
    return this.store.get(key) ?? null;
  }

  async set(key: string, value: string): Promise<void> {
    this.store.set(key, value);
  }

  async delete(key: string): Promise<void> {
    this.store.delete(key);
  }
}
