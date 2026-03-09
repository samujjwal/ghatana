/**
 * Base repository class for database operations.
 *
 * <p><b>Purpose</b><br>
 * Provides common CRUD operations and patterns for all domain repositories,
 * ensuring consistency and reducing code duplication.
 *
 * <p><b>Usage</b><br>
 * <pre>{@code
 * class UserRepository extends BaseRepository<User, 'user'> {
 *   constructor(prisma: PrismaClient) {
 *     super(prisma, 'user');
 *   }
 *
 *   async findByEmail(email: string) {
 *     return this.findOne({ email });
 *   }
 * }
 * }</pre>
 *
 * @doc.type module
 * @doc.purpose Base repository implementation
 * @doc.layer product
 * @doc.pattern Repository
 */

import { PrismaClient } from '@prisma/client';

/**
 * Generic repository base class for database operations.
 *
 * <p><b>Purpose</b><br>
 * Provides common CRUD operations for any Prisma model, reducing boilerplate
 * and ensuring consistent data access patterns.
 *
 * @template T - The entity type
 * @template M - The Prisma model name
 *
 * @doc.type class
 * @doc.purpose Base repository
 * @doc.layer product
 * @doc.pattern Repository
 */
export abstract class BaseRepository<T, M extends keyof PrismaClient> {
  /**
   * Creates a new repository instance.
   *
   * @param prisma - Prisma client instance
   * @param modelName - Name of the Prisma model
   */
  constructor(
    protected prisma: PrismaClient,
    protected modelName: M
  ) {}

  /**
   * Gets the Prisma model delegate.
   *
   * @returns The Prisma model delegate
   */
  protected getModel() {
    return this.prisma[this.modelName] as unknown;
  }

  /**
   * Finds a single entity by criteria.
   *
   * <p><b>Purpose</b><br>
   * Retrieves a single entity matching the given criteria.
   *
   * @param where - Query criteria
   * @returns The found entity or null
   *
   * @doc.type method
   * @doc.purpose Find single entity
   * @doc.layer product
   * @doc.pattern Repository
   */
  async findOne(where: Record<string, unknown>): Promise<T | null> {
    return this.getModel().findUnique({ where });
  }

  /**
   * Finds multiple entities by criteria.
   *
   * <p><b>Purpose</b><br>
   * Retrieves multiple entities matching the given criteria.
   *
   * @param where - Query criteria
   * @param options - Query options (skip, take, orderBy)
   * @returns Array of found entities
   *
   * @doc.type method
   * @doc.purpose Find multiple entities
   * @doc.layer product
   * @doc.pattern Repository
   */
  async findMany(
    where?: Record<string, unknown>,
    options?: { skip?: number; take?: number; orderBy?: Record<string, unknown> }
  ): Promise<T[]> {
    return this.getModel().findMany({
      where,
      ...options,
    });
  }

  /**
   * Finds all entities.
   *
   * <p><b>Purpose</b><br>
   * Retrieves all entities of this type.
   *
   * @param options - Query options (skip, take, orderBy)
   * @returns Array of all entities
   *
   * @doc.type method
   * @doc.purpose Find all entities
   * @doc.layer product
   * @doc.pattern Repository
   */
  async findAll(options?: { skip?: number; take?: number; orderBy?: Record<string, unknown> }): Promise<T[]> {
    return this.getModel().findMany(options);
  }

  /**
   * Counts entities matching criteria.
   *
   * <p><b>Purpose</b><br>
   * Returns the count of entities matching the given criteria.
   *
   * @param where - Query criteria
   * @returns Count of matching entities
   *
   * @doc.type method
   * @doc.purpose Count entities
   * @doc.layer product
   * @doc.pattern Repository
   */
  async count(where?: Record<string, unknown>): Promise<number> {
    return this.getModel().count({ where });
  }

  /**
   * Creates a new entity.
   *
   * <p><b>Purpose</b><br>
   * Creates and persists a new entity.
   *
   * @param data - Entity data
   * @returns The created entity
   *
   * @doc.type method
   * @doc.purpose Create entity
   * @doc.layer product
   * @doc.pattern Repository
   */
  async create(data: Record<string, unknown>): Promise<T> {
    return this.getModel().create({ data });
  }

  /**
   * Updates an entity.
   *
   * <p><b>Purpose</b><br>
   * Updates an existing entity.
   *
   * @param where - Entity identifier
   * @param data - Updated data
   * @returns The updated entity
   *
   * @doc.type method
   * @doc.purpose Update entity
   * @doc.layer product
   * @doc.pattern Repository
   */
  async update(where: Record<string, unknown>, data: Record<string, unknown>): Promise<T> {
    return this.getModel().update({ where, data });
  }

  /**
   * Deletes an entity.
   *
   * <p><b>Purpose</b><br>
   * Deletes an entity by identifier.
   *
   * @param where - Entity identifier
   * @returns The deleted entity
   *
   * @doc.type method
   * @doc.purpose Delete entity
   * @doc.layer product
   * @doc.pattern Repository
   */
  async delete(where: Record<string, unknown>): Promise<T> {
    return this.getModel().delete({ where });
  }

  /**
   * Upserts an entity (creates or updates).
   *
   * <p><b>Purpose</b><br>
   * Creates an entity if it doesn't exist, otherwise updates it.
   *
   * @param where - Entity identifier
   * @param create - Data for creation
   * @param update - Data for update
   * @returns The created or updated entity
   *
   * @doc.type method
   * @doc.purpose Upsert entity
   * @doc.layer product
   * @doc.pattern Repository
   */
  async upsert(
    where: Record<string, unknown>,
    create: Record<string, unknown>,
    update: Record<string, unknown>
  ): Promise<T> {
    return this.getModel().upsert({ where, create, update });
  }
}
