/**
 * PaginationHelper Utility
 *
 * Comprehensive pagination utility for consistent pagination across the TutorPutor platform.
 * Supports cursor-based and offset-based pagination with proper validation and metadata.
 *
 * @doc.type utility
 * @doc.purpose Standardized pagination
 * @doc.layer core
 * @doc.pattern Pagination
 */

export interface PaginationOptions {
  /** Page number for offset-based pagination (1-based) */
  page?: number;
  /** Number of items per page */
  limit?: number;
  /** Cursor for cursor-based pagination */
  cursor?: string;
  /** Direction for cursor-based pagination */
  direction?: "forward" | "backward";
  /** Maximum allowed limit */
  maxLimit?: number;
  /** Default limit if not specified */
  defaultLimit?: number;
}

export interface PaginationMetadata {
  /** Current page number (offset-based) */
  currentPage?: number;
  /** Number of items per page */
  limit: number;
  /** Total number of items (if available) */
  total?: number;
  /** Total number of pages (if total available) */
  totalPages?: number;
  /** Whether there's a next page */
  hasNextPage: boolean;
  /** Whether there's a previous page */
  hasPreviousPage: boolean;
  /** Next cursor (cursor-based) */
  nextCursor?: string;
  /** Previous cursor (cursor-based) */
  previousCursor?: string;
  /** Starting offset */
  offset?: number;
}

export interface PaginatedResult<T> {
  /** The paginated data */
  data: T[];
  /** Pagination metadata */
  pagination: PaginationMetadata;
}

export interface CursorInfo {
  /** Cursor value */
  cursor: string;
  /** Whether this cursor points to the first item */
  isFirst: boolean;
  /** Whether this cursor points to the last item */
  isLast: boolean;
}

/**
 * PaginationHelper class for consistent pagination operations
 */
export class PaginationHelper {
  private static readonly DEFAULT_LIMIT = 20;
  private static readonly MAX_LIMIT = 100;
  private static readonly DEFAULT_PAGE = 1;

  /**
   * Parse and validate pagination options from query parameters
   */
  static parseOptions(options: PaginationOptions = {}): {
    limit: number;
    page: number;
    maxLimit: number;
    defaultLimit: number;
  } {
    const maxLimit = options.maxLimit ?? this.MAX_LIMIT;
    const defaultLimit = options.defaultLimit ?? this.DEFAULT_LIMIT;

    const limit = Math.min(
      Math.max(options.limit ?? defaultLimit, 1),
      maxLimit,
    );

    const page = Math.max(options.page ?? this.DEFAULT_PAGE, 1);

    return { limit, page, maxLimit, defaultLimit };
  }

  /**
   * Calculate offset for database queries
   */
  static calculateOffset(page: number, limit: number): number {
    return (page - 1) * limit;
  }

  /**
   * Create pagination metadata for offset-based pagination
   */
  static createOffsetMetadata(
    options: ReturnType<typeof PaginationHelper.parseOptions>,
    totalItems: number,
    hasMore?: boolean,
  ): PaginationMetadata {
    const totalPages = Math.ceil(totalItems / options.limit);
    const hasNextPage = hasMore ?? options.page < totalPages;
    const hasPreviousPage = options.page > 1;

    return {
      currentPage: options.page,
      limit: options.limit,
      total: totalItems,
      totalPages,
      hasNextPage,
      hasPreviousPage,
      offset: this.calculateOffset(options.page, options.limit),
    };
  }

  /**
   * Create pagination metadata for cursor-based pagination
   */
  static createCursorMetadata(
    limit: number,
    nextCursor?: string,
    previousCursor?: string,
    hasMore?: boolean,
    hasPrevious?: boolean,
  ): PaginationMetadata {
    return {
      limit,
      hasNextPage: hasMore ?? !!nextCursor,
      hasPreviousPage: hasPrevious ?? !!previousCursor,
      ...(nextCursor ? { nextCursor } : {}),
      ...(previousCursor ? { previousCursor } : {}),
    };
  }

  /**
   * Create a paginated result with offset-based pagination
   */
  static createOffsetResult<T>(
    data: T[],
    options: ReturnType<typeof PaginationHelper.parseOptions>,
    totalItems: number,
    hasMore?: boolean,
  ): PaginatedResult<T> {
    return {
      data,
      pagination: this.createOffsetMetadata(options, totalItems, hasMore),
    };
  }

  /**
   * Create a paginated result with cursor-based pagination
   */
  static createCursorResult<T>(
    data: T[],
    limit: number,
    nextCursor?: string,
    previousCursor?: string,
    hasMore?: boolean,
    hasPrevious?: boolean,
  ): PaginatedResult<T> {
    return {
      data,
      pagination: this.createCursorMetadata(
        limit,
        nextCursor,
        previousCursor,
        hasMore,
        hasPrevious,
      ),
    };
  }

  /**
   * Generate a cursor from an item (typically ID or timestamp)
   */
  static generateCursor(
    item: { id?: string; createdAt?: Date },
    strategy: "id" | "timestamp" = "id",
  ): string {
    if (strategy === "id" && item.id) {
      return Buffer.from(item.id).toString("base64");
    }
    if (strategy === "timestamp" && item.createdAt) {
      return item.createdAt.getTime().toString();
    }
    throw new Error("Cannot generate cursor: missing required field");
  }

  /**
   * Decode a cursor to extract the value
   */
  static decodeCursor(
    cursor: string,
    strategy: "id" | "timestamp" = "id",
  ): string {
    if (strategy === "id") {
      return Buffer.from(cursor, "base64").toString();
    }
    if (strategy === "timestamp") {
      return cursor; // Already a string representation of timestamp
    }
    throw new Error(`Unknown cursor strategy: ${strategy}`);
  }

  /**
   * Validate cursor-based pagination options
   */
  static validateCursorOptions(options: PaginationOptions): {
    limit: number;
    cursor?: string;
    direction: "forward" | "backward";
  } {
    const limit = Math.min(
      Math.max(options.limit ?? this.DEFAULT_LIMIT, 1),
      options.maxLimit ?? this.MAX_LIMIT,
    );

    const direction = options.direction ?? "forward";

    if (options.cursor && !/^[A-Za-z0-9+/=]+$/.test(options.cursor)) {
      throw new Error("Invalid cursor format");
    }

    return {
      limit,
      ...(options.cursor ? { cursor: options.cursor } : {}),
      direction,
    };
  }

  /**
   * Get database query parameters for cursor-based pagination
   */
  static getCursorQueryParams(
    cursor: string,
    direction: "forward" | "backward",
    strategy: "id" | "timestamp" = "id",
  ): {
    operator: ">" | "<";
    value: string;
    orderBy: "ASC" | "DESC";
  } {
    const value = this.decodeCursor(cursor, strategy);

    if (direction === "forward") {
      return { operator: ">", value, orderBy: "ASC" as const };
    } else {
      return { operator: "<", value, orderBy: "DESC" as const };
    }
  }

  /**
   * Extract cursor information from data array
   */
  static extractCursors<T>(
    data: T[],
    strategy: "id" | "timestamp" = "id",
  ): { firstCursor?: string; lastCursor?: string } {
    if (data.length === 0) {
      return {};
    }

    const firstItem = data[0] as { id?: string; createdAt?: Date };
    const lastItem = data[data.length - 1] as { id?: string; createdAt?: Date };

    try {
      const firstCursor = this.generateCursor(firstItem, strategy);
      const lastCursor = this.generateCursor(lastItem, strategy);

      return { firstCursor, lastCursor };
    } catch {
      return {};
    }
  }

  /**
   * Create pagination links for REST APIs
   */
  static createLinks(
    baseUrl: string,
    options: ReturnType<typeof PaginationHelper.parseOptions>,
    totalPages?: number,
    hasNextPage?: boolean,
    hasPreviousPage?: boolean,
  ): {
    first?: string;
    previous?: string;
    next?: string;
    last?: string;
  } {
    const links: Record<string, string | undefined> = {};

    // First page
    links.first = `${baseUrl}?page=1&limit=${options.limit}`;

    // Previous page
    if (hasPreviousPage && options.page > 1) {
      links.previous = `${baseUrl}?page=${options.page - 1}&limit=${options.limit}`;
    }

    // Next page
    if (hasNextPage && totalPages && options.page < totalPages) {
      links.next = `${baseUrl}?page=${options.page + 1}&limit=${options.limit}`;
    }

    // Last page
    if (totalPages && totalPages > 1) {
      links.last = `${baseUrl}?page=${totalPages}&limit=${options.limit}`;
    }

    return links;
  }

  /**
   * Estimate total items based on page data (when exact count is expensive)
   */
  static estimateTotal(
    currentPageItems: number,
    limit: number,
    currentPage: number,
    hasNextPage: boolean,
  ): {
    estimatedTotal: number;
    isExact: boolean;
  } {
    if (!hasNextPage) {
      // Exact count: this is the last page
      const exactTotal = (currentPage - 1) * limit + currentPageItems;
      return { estimatedTotal: exactTotal, isExact: true };
    }

    // Rough estimate: at least current page items, likely more
    const minTotal = (currentPage - 1) * limit + currentPageItems;
    const estimatedTotal = minTotal + Math.floor(limit / 2); // Rough estimate

    return { estimatedTotal, isExact: false };
  }

  /**
   * Validate pagination parameters
   */
  static validateParameters(options: PaginationOptions): void {
    if (
      options.page !== undefined &&
      (options.page < 1 || !Number.isInteger(options.page))
    ) {
      throw new Error("Page must be a positive integer");
    }

    if (
      options.limit !== undefined &&
      (options.limit < 1 || !Number.isInteger(options.limit))
    ) {
      throw new Error("Limit must be a positive integer");
    }

    if (
      options.maxLimit !== undefined &&
      options.limit &&
      options.limit > options.maxLimit
    ) {
      throw new Error(`Limit cannot exceed ${options.maxLimit}`);
    }

    if (options.cursor && typeof options.cursor !== "string") {
      throw new Error("Cursor must be a string");
    }

    if (
      options.direction &&
      !["forward", "backward"].includes(options.direction)
    ) {
      throw new Error('Direction must be either "forward" or "backward"');
    }
  }
}

/**
 * Convenience function for offset-based pagination
 */
export function paginate<T>(
  data: T[],
  options: PaginationOptions,
  totalItems?: number,
): PaginatedResult<T> {
  PaginationHelper.validateParameters(options);
  const parsedOptions = PaginationHelper.parseOptions(options);
  const offset = PaginationHelper.calculateOffset(
    parsedOptions.page,
    parsedOptions.limit,
  );

  const paginatedData = data.slice(offset, offset + parsedOptions.limit);
  const hasMore =
    totalItems !== undefined
      ? offset + parsedOptions.limit < totalItems
      : offset + parsedOptions.limit < data.length;

  return PaginationHelper.createOffsetResult(
    paginatedData,
    parsedOptions,
    totalItems ?? data.length,
    hasMore,
  );
}

/**
 * Convenience function for cursor-based pagination
 */
export function paginateWithCursor<T>(
  data: T[],
  options: PaginationOptions,
  strategy: "id" | "timestamp" = "id",
): PaginatedResult<T> {
  PaginationHelper.validateParameters(options);
  const { limit, cursor, direction } =
    PaginationHelper.validateCursorOptions(options);

  // For simplicity, this implementation assumes data is already sorted
  // In a real implementation, you'd apply cursor filtering at the database level
  let startIndex = 0;
  if (cursor) {
    // Find cursor position (simplified)
    const cursorValue = PaginationHelper.decodeCursor(cursor, strategy);
    startIndex = data.findIndex((item) => {
      const itemValue =
        strategy === "id"
          ? (item as { id: string }).id
          : (item as { createdAt: Date }).createdAt.getTime().toString();
      return itemValue === cursorValue;
    });

    if (startIndex === -1) {
      throw new Error("Cursor not found");
    }

    if (direction === "backward") {
      startIndex = Math.max(0, startIndex - limit + 1);
    } else {
      startIndex += 1;
    }
  }

  const endIndex = startIndex + limit;
  const paginatedData = data.slice(startIndex, endIndex);

  const { firstCursor, lastCursor } = PaginationHelper.extractCursors(
    paginatedData,
    strategy,
  );

  const hasNextPage = endIndex < data.length;
  const hasPreviousPage = startIndex > 0;

  return PaginationHelper.createCursorResult(
    paginatedData,
    limit,
    lastCursor,
    firstCursor,
    hasNextPage,
    hasPreviousPage,
  );
}
