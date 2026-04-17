const pick = <T>(values: T[]): T => values[Math.floor(Math.random() * values.length)] ?? values[0];

const randomInt = (min = 0, max = 1000): number => {
  const lower = Math.ceil(min);
  const upper = Math.floor(max);
  return Math.floor(Math.random() * (upper - lower + 1)) + lower;
};

const recentDate = (): Date => new Date(Date.now() - randomInt(1_000, 86_400_000));
const futureDate = (): Date => new Date(Date.now() + randomInt(1_000, 86_400_000));
const pastDate = (): Date => new Date(Date.now() - randomInt(86_400_000, 31_536_000_000));

export const faker = {
  internet: {
    email: () => `user${randomInt(1, 9999)}@example.test`,
    userName: () => `user_${randomInt(1, 9999)}`,
    password: (length = 12) => `Pass${'x'.repeat(Math.max(length - 4, 4))}`,
    url: () => `https://example.test/${randomInt(1, 9999)}`,
    color: () => `#${randomInt(0, 0xffffff).toString(16).padStart(6, '0')}`,
  },
  person: {
    fullName: () => `${pick(['Alex', 'Sam', 'Jordan', 'Taylor'])} ${pick(['Lee', 'Patel', 'Kim', 'Garcia'])}`,
  },
  date: {
    recent: recentDate,
    future: futureDate,
    past: pastDate,
  },
  lorem: {
    sentence: () => 'Generated test sentence.',
    paragraph: () => 'Generated test paragraph for local workspace validation.',
  },
  datatype: {
    boolean: () => Math.random() >= 0.5,
  },
  number: {
    int: ({ min = 0, max = 1000 }: { min?: number; max?: number } = {}) => randomInt(min, max),
    float: ({ min = 0, max = 100, precision = 2 }: { min?: number; max?: number; precision?: number } = {}) => {
      const scale = 10 ** precision;
      return Math.round((Math.random() * (max - min) + min) * scale) / scale;
    },
  },
};