// Minimal faker shim used for Storybook/dev/story factories.
// Provides a very small subset of the @faker-js/faker API used by our factories.
// This avoids pulling the full faker dependency into the browser preview.

/**
 *
 */
function randomInt(max = 1000000) {
  return Math.floor(Math.random() * max);
}

export const faker = {
  person: {
    firstName: () => `First${randomInt()}`,
    lastName: () => `Last${randomInt()}`,
    fullName: () => `First${randomInt()} Last${randomInt()}`,
  },
  string: {
    uuid: () => `fake-uuid-${randomInt(1e6)}`,
  },
  internet: {
    email: ({ firstName = 'user', lastName = '' } = {}) =>
      `${String(firstName).toLowerCase()}.${String(lastName).toLowerCase()}@example.com`,
    userName: () => `user${randomInt(1000)}`,
    password: (len = 12) => `p${randomInt(1000000)}`.slice(0, len),
    url: () => `https://example.com/${randomInt(10000)}`,
    color: () => `#${(randomInt(0xffffff)).toString(16).padStart(6, '0')}`,
  },
  image: {
    avatar: () => `https://api.dicebear.com/6.x/identicon/svg?seed=${randomInt()}`,
  },
  company: {
    name: () => `Acme ${randomInt(1000)}`,
    catchPhrase: () => `Doing great things ${randomInt(1000)}`,
  },
  date: {
    past: () => new Date(Date.now() - Math.floor(Math.random() * 1000 * 60 * 60 * 24 * 365)),
    recent: () => new Date(Date.now() - Math.floor(Math.random() * 1000 * 60 * 60 * 24 * 30)),
    future: () => new Date(Date.now() + Math.floor(Math.random() * 1000 * 60 * 60 * 24 * 365)),
  },
  commerce: {
    productName: () => `Product ${randomInt(1000)}`,
  },
  lorem: {
    sentence: () => `Lorem ipsum dolor sit amet ${randomInt(1000)}.`,
    paragraph: () => `Lorem ipsum paragraph ${randomInt(1000)}.`,
  },
  datatype: {
    boolean: () => Math.random() < 0.5,
  },
  number: {
    int: (max = 1000) => Math.floor(Math.random() * max),
    float: ({ min = 0, max = 1, precision = 2 } = {}) =>
      parseFloat((Math.random() * (max - min) + min).toFixed(precision)),
  },
};

export default faker;
