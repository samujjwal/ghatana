/**
 * User factory for generating test user data
 */

import { faker } from '../faker-shim';

import type { User } from '@ghatana/yappc-types';

/**
 *
 */
export interface UserFactoryOptions {
  /** Override user ID */
  id?: string;
  /** Override user name */
  name?: string;
  /** Override user email */
  email?: string;
  /** Override user avatar URL */
  avatarUrl?: string;
  /** Override user role */
  role?: 'admin' | 'user' | 'guest';
  /** Override user creation date */
  createdAt?: Date;
  /** Override user last login date */
  lastLoginAt?: Date;
}

/**
 * Create a test user with realistic data
 *
 * @param options - Override default user properties
 * @returns A user object for testing
 */
export function createUser(options: UserFactoryOptions = {}): User {
  const firstName = options.name?.split(' ')[0] || faker.person.firstName();
  const lastName = options.name?.split(' ')[1] || faker.person.lastName();
  const name = options.name || `${firstName} ${lastName}`;

  return {
    id: options.id || faker.string.uuid(),
    name,
    email:
      options.email ||
      faker.internet.email({ firstName, lastName }).toLowerCase(),
    avatar: options.avatarUrl || faker.image.avatar(),
  };
}

/**
 * Create multiple test users
 *
 * @param count - Number of users to create
 * @param baseOptions - Base options to apply to all users
 * @returns Array of user objects for testing
 */
export function createUsers(
  count: number,
  baseOptions: UserFactoryOptions = {}
): User[] {
  return Array.from({ length: count }, (_, index) =>
    createUser({
      ...baseOptions,
      // Allow overriding specific users in the array
      ...((baseOptions as unknown)[`user${index}`] || {}),
    })
  );
}

/**
 * Create an admin user
 *
 * @param options - Override default user properties
 * @returns An admin user object for testing
 */
export function createAdminUser(options: UserFactoryOptions = {}): User {
  return createUser({
    role: 'admin',
    ...options,
  });
}

/**
 * Create a guest user
 *
 * @param options - Override default user properties
 * @returns A guest user object for testing
 */
export function createGuestUser(options: UserFactoryOptions = {}): User {
  return createUser({
    role: 'guest',
    ...options,
  });
}
