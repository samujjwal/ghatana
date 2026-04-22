/**
 * @fileoverview Prisma schema extractor tests.
 */

import { describe, it, expect } from 'vitest';
import { parsePrismaSchema } from './schema-extractor';

describe('parsePrismaSchema', () => {
  it('should extract models with fields', () => {
    const schema = `model User {
  id        Int      @id @default(autoincrement())
  email     String   @unique
  name      String?
  createdAt DateTime @default(now())
  updatedAt DateTime @updatedAt
}`;

    const models = parsePrismaSchema(schema, 'schema.prisma');

    expect(models.length).toBe(1);
    expect(models[0]!.name).toBe('User');
    expect(models[0]!.fields.length).toBe(5);

    const idField = models[0]!.fields.find(f => f.name === 'id');
    expect(idField).toBeDefined();
    expect(idField!.type).toBe('Int');
    expect(idField!.isPrimaryKey).toBe(true);
    expect(idField!.required).toBe(true);

    const emailField = models[0]!.fields.find(f => f.name === 'email');
    expect(emailField).toBeDefined();
    expect(emailField!.unique).toBe(true);
    expect(emailField!.required).toBe(true);

    const nameField = models[0]!.fields.find(f => f.name === 'name');
    expect(nameField).toBeDefined();
    expect(nameField!.required).toBe(false); // name? is optional

    const createdAt = models[0]!.fields.find(f => f.name === 'createdAt');
    expect(createdAt).toBeDefined();
    expect(createdAt!.defaultValue).toEqual({ prismaDefault: 'now' });
  });

  it('should extract relations between models', () => {
    const schema = `model User {
  id    Int    @id @default(autoincrement())
  posts Post[]
}

model Post {
  id       Int    @id @default(autoincrement())
  author   User   @relation(fields: [authorId], references: [id])
  authorId Int
}`;

    const models = parsePrismaSchema(schema, 'schema.prisma');

    expect(models.length).toBe(2);

    const user = models.find(m => m.name === 'User');
    const post = models.find(m => m.name === 'Post');

    expect(user).toBeDefined();
    expect(post).toBeDefined();

    // User has a one-to-many relation via posts Post[]
    expect(user!.relations.length).toBe(1);
    expect(user!.relations[0]!.targetEntityId).toBe('Post');
    expect(user!.relations[0]!.kind).toBe('one-to-many');

    // Post has a many-to-one relation via author User
    expect(post!.relations.length).toBe(1);
    expect(post!.relations[0]!.targetEntityId).toBe('User');
    expect(post!.relations[0]!.kind).toBe('many-to-one');
  });

  it('should extract indexes', () => {
    const schema = `model Product {
  id    Int    @id @default(autoincrement())
  sku   String
  name  String

  @@index([sku, name])
  @@unique([sku])
}`;

    const models = parsePrismaSchema(schema, 'schema.prisma');

    expect(models.length).toBe(1);
    expect(models[0]!.indexes.length).toBe(2);

    const idx = models[0]!.indexes.find(i => i.unique);
    expect(idx).toBeDefined();
    expect(idx!.fields).toContain('sku');
    expect(idx!.unique).toBe(true);

    const nonUniqueIdx = models[0]!.indexes.find(i => !i.unique);
    expect(nonUniqueIdx).toBeDefined();
    expect(nonUniqueIdx!.fields).toEqual(['sku', 'name']);
  });

  it('should flag unsupported features', () => {
    const schema = `model User {
  id   Int    @id @default(autoincrement())
  name String @db.VarChar(255)

  @@map("users")
}`;

    const models = parsePrismaSchema(schema, 'schema.prisma');

    expect(models.length).toBe(1);
    expect(models[0]!.unsupportedFeatures.length).toBeGreaterThan(0);
    const hasMap = models[0]!.unsupportedFeatures.some(f => f.feature.includes('@@map'));
    expect(hasMap).toBe(true);
  });

  it('should return empty array for empty schema', () => {
    const models = parsePrismaSchema('', 'schema.prisma');
    expect(models.length).toBe(0);
  });

  it('should handle multiple models', () => {
    const schema = `model Category {
  id   Int    @id @default(autoincrement())
  name String
}

model Tag {
  id   Int    @id @default(autoincrement())
  name String @unique
}`;

    const models = parsePrismaSchema(schema, 'schema.prisma');
    expect(models.length).toBe(2);
    expect(models.map(m => m.name)).toContain('Category');
    expect(models.map(m => m.name)).toContain('Tag');
  });
});
