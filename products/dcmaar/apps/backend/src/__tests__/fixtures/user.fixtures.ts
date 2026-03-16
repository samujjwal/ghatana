/**
 * User Test Fixtures
 *
 * Provides realistic test data for users, children, and parents.
 *
 * <p><b>Purpose</b><br>
 * Centralized test data builders for user-related entities (parents, children, admins).
 * Enables consistent test setup across the entire test suite without duplicating mock data.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * import { parentUser, childUser, createRandomUser } from './user.fixtures';
 *
 * const user = await createTestUser(parentUser);
 * const randomChild = createRandomUser('child');
 * }</pre>
 *
 * <p><b>Test Coverage</b><br>
 * Used by:
 * - AuthService tests (login, registration, profile)
 * - ChildService tests (child profiles, relationships)
 * - Device pairing tests (user-device associations)
 * - Policy tests (policy assignments to children)
 *
 * @doc.type fixtures
 * @doc.purpose Test data builders for users, children, and parent relationships
 * @doc.layer backend
 * @doc.pattern Test Factory
 */

import { randomEmail, randomString } from "../setup";
import jwt from "jsonwebtoken";
import { query } from "../../db";

const JWT_SECRET = process.env.JWT_SECRET ?? "development-secret-key";

/**
 * Create a test user by inserting directly into the database and issuing a
 * local JWT access token. Replaces the removed authService.register() in tests.
 *
 * @param opts Optional overrides for email and displayName
 * @returns { id, email, accessToken } — mirrors the previous authService.register() surface
 */
export async function createTestUser(
  opts: { email?: string; displayName?: string } = {}
): Promise<{ id: string; email: string; display_name: string | null; accessToken: string }> {
  const email = opts.email ?? randomEmail();
  const displayName = opts.displayName ?? null;

  const result = await query<{ id: string; email: string; display_name: string | null }>(
    `INSERT INTO users (email, password_hash, display_name) VALUES ($1, $2, $3)
     RETURNING id, email, display_name`,
    [email, "$2a$12$placeholder_hash_for_testing_only", displayName]
  );

  const row = result[0];
  const accessToken = jwt.sign({ userId: row.id, type: "access" }, JWT_SECRET, {
    expiresIn: "1h",
  });

  return { ...row, accessToken };
}


export interface UserFixture {
  id?: string;
  email: string;
  password: string;
  displayName: string;
  role: "parent" | "child" | "admin";
  createdAt?: Date;
  updatedAt?: Date;
}

/**
 * Parent user fixture
 */
export const parentUser: UserFixture = {
  email: "parent@example.com",
  password: "ParentPassword123!",
  displayName: "Test Parent",
  role: "parent",
};

/**
 * Secondary parent user for multi-user tests
 */
export const parentUser2: UserFixture = {
  email: "parent2@example.com",
  password: "ParentPassword456!",
  displayName: "Test Parent 2",
  role: "parent",
};

/**
 * Child user fixture
 */
export const childUser: UserFixture = {
  email: "child@example.com",
  password: "ChildPassword123!",
  displayName: "Test Child",
  role: "child",
};

/**
 * Admin user fixture
 */
export const adminUser: UserFixture = {
  email: "admin@example.com",
  password: "AdminPassword123!",
  displayName: "Test Admin",
  role: "admin",
};

/**
 * Generate a random user fixture
 */
export function createRandomUser(
  role: "parent" | "child" | "admin" = "parent"
): UserFixture {
  return {
    email: randomEmail(),
    password: `Password${randomString(8)}!`,
    displayName: `Test ${role} ${randomString(5)}`,
    role,
  };
}

/**
 * Collection of user fixtures
 */
export const userFixtures = {
  parent: parentUser,
  parent2: parentUser2,
  child: childUser,
  admin: adminUser,
  createRandom: createRandomUser,
};

/**
 * Child profile fixture (linked to parent)
 */
export interface ChildFixture {
  id?: string;
  userId: string;
  name: string;
  age: number;
  birthDate: string;
  avatarUrl?: string;
  createdAt?: Date;
  updatedAt?: Date;
}

export const childProfile: ChildFixture = {
  userId: "", // Will be set in tests
  name: "Test Child Profile",
  age: 12,
  birthDate: "2010-05-15",
};

export const childProfile2: ChildFixture = {
  userId: "", // Will be set in tests
  name: "Test Child Profile 2",
  age: 10,
  birthDate: "2012-08-20",
};

export function createRandomChild(userId: string): ChildFixture {
  const year = 2008 + Math.floor(Math.random() * 10); // 2008-2017
  const month = String(Math.floor(Math.random() * 12) + 1).padStart(2, "0");
  const day = String(Math.floor(Math.random() * 28) + 1).padStart(2, "0");
  const birthDate = `${year}-${month}-${day}`;
  const age = new Date().getFullYear() - year;

  return {
    userId,
    name: `Child ${randomString(5)}`,
    age,
    birthDate,
  };
}

export const childFixtures = {
  child1: childProfile,
  child2: childProfile2,
  createRandom: createRandomChild,
};
