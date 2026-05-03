// @ts-nocheck
/* Use native crypto.randomUUID to avoid uuid dependency in tests */
const uuidv4 = () => crypto.randomUUID();

const randomInt = (min, max) => Math.floor(Math.random() * (max - min + 1)) + min;
const pick = (values) => values[randomInt(0, values.length - 1)];
const words = ['alpha', 'beta', 'gamma', 'delta', 'omega', 'pixel', 'vector', 'canvas'];
const names = ['Alex Chen', 'Sam Rivera', 'Taylor Brooks', 'Jordan Lee'];

const faker = {
  internet: {
    email: () => `user${randomInt(1000, 9999)}@example.com`,
    userName: () => `user_${randomInt(1000, 9999)}`,
    password: (length = 12) => `Pw${uuidv4().replace(/-/g, '').slice(0, Math.max(length, 4))}`,
    url: () => `https://example.com/${pick(words)}`,
    color: () => `#${randomInt(0, 0xffffff).toString(16).padStart(6, '0')}`,
  },
  person: {
    fullName: () => pick(names),
  },
  date: {
    recent: () => new Date(Date.now() - randomInt(0, 7) * 24 * 60 * 60 * 1000),
    past: () => new Date(Date.now() - randomInt(8, 365) * 24 * 60 * 60 * 1000),
    future: () => new Date(Date.now() + randomInt(1, 365) * 24 * 60 * 60 * 1000),
  },
  lorem: {
    sentence: () => `${pick(words)} ${pick(words)} ${pick(words)}.`,
    paragraph: () => `${pick(words)} ${pick(words)} ${pick(words)} ${pick(words)} ${pick(words)}.`,
  },
  datatype: {
    boolean: () => Math.random() >= 0.5,
  },
  number: {
    int: (input = {}) => {
      if (typeof input === 'number') {
        return randomInt(0, input);
      }
      return randomInt(input.min ?? 0, input.max ?? 1000);
    },
    float: ({ min = 0, max = 1 } = {}) => min + Math.random() * (max - min),
  },
};

/**
 *
 */
type FactoryOptions = {
  count?: number;
  overrides?: Record<string, unknown>;
};

/**
 *
 */
type FactoryFunction<T> = (overrides?: Partial<T>) => T;

export const createFactory = <T>(defaults: T): FactoryFunction<T> => {
  return (overrides = {}) => ({
    ...(typeof defaults === 'function' ? defaults() : { ...defaults }),
    ...overrides,
  });
};

export const createList = <T>(
  factory: FactoryFunction<T>,
  count: number = 3,
  overrides: Partial<T>[] = [],
): T[] => {
  return Array.from({ length: count }, (_, i) =>
    factory(overrides[i] || {})
  );
};

// Common test data generators
export const common = {
  id: () => uuidv4(),
  email: () => faker.internet.email().toLowerCase(),
  name: () => faker.person.fullName(),
  username: () => faker.internet.userName().toLowerCase(),
  password: () => faker.internet.password(12),
  url: () => faker.internet.url(),
  date: () => faker.date.recent().toISOString(),
  pastDate: () => faker.date.past().toISOString(),
  futureDate: () => faker.date.future().toISOString(),
  sentence: () => faker.lorem.sentence(),
  paragraph: () => faker.lorem.paragraph(),
  boolean: () => faker.datatype.boolean(),
  number: (min = 1, max = 1000) => faker.number.int({ min, max }),
  float: (min = 0, max = 100, precision = 2) => 
    parseFloat(faker.number.float({ min, max, precision }).toFixed(precision)),
  uuid: () => uuidv4(),
  hexColor: () => faker.internet.color(),
  imageUrl: (width = 640, height = 480) => 
    `https://picsum.photos/${width}/${height}?random=${faker.number.int(1000)}`,
  pick: <T>(array: T[]): T => 
    array[Math.floor(Math.random() * array.length)],
  timestamp: () => Math.floor(Date.now() / 1000),
};

// Example usage with a user model
/**
 *
 */
interface User {
  id: string;
  name: string;
  email: string;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export const userFactory = createFactory<User>({
  id: common.uuid(),
  name: common.name(),
  email: common.email(),
  isActive: common.boolean(),
  createdAt: common.date(),
  updatedAt: common.date(),
});

// Example usage with a post model
/**
 *
 */
interface Post {
  id: string;
  title: string;
  content: string;
  authorId: string;
  isPublished: boolean;
  createdAt: string;
  updatedAt: string;
}

export const postFactory = createFactory<Post>({
  id: common.uuid(),
  title: common.sentence(),
  content: common.paragraph(),
  authorId: common.uuid(),
  isPublished: common.boolean(),
  createdAt: common.date(),
  updatedAt: common.date(),
});

// Example of creating a list of users
// const testUsers = createList(userFactory, 5);

// Example of creating a single user with overrides
// const adminUser = userFactory({ isAdmin: true, name: 'Admin User' });
