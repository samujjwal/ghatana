/**
 * @ghatana/data-access-context — Kernel-owned data access context
 *
 * Provides tenant, principal, and request correlation information for database operations.
 */

export interface DataAccessContext {
  /** Tenant ID for multi-tenancy */
  readonly tenantId: string;
  /** Principal/user ID */
  readonly principalId: string;
  /** Correlation ID for request tracing */
  readonly correlationId?: string;
  /** Request ID for tracking */
  readonly requestId?: string;
  /** Client IP address */
  readonly clientIp?: string;
  /** User agent string */
  readonly userAgent?: string;
}

export class DataAccessContextBuilder {
  private tenantId?: string;
  private principalId?: string;
  private correlationId?: string;
  private requestId?: string;
  private clientIp?: string;
  private userAgent?: string;

  setTenantId(tenantId: string): this {
    this.tenantId = tenantId;
    return this;
  }

  setPrincipalId(principalId: string): this {
    this.principalId = principalId;
    return this;
  }

  setCorrelationId(correlationId: string): this {
    this.correlationId = correlationId;
    return this;
  }

  setRequestId(requestId: string): this {
    this.requestId = requestId;
    return this;
  }

  setClientIp(clientIp: string): this {
    this.clientIp = clientIp;
    return this;
  }

  setUserAgent(userAgent: string): this {
    this.userAgent = userAgent;
    return this;
  }

  build(): DataAccessContext {
    if (!this.tenantId || this.tenantId.length === 0) {
      throw new Error('tenantId is required');
    }
    if (!this.principalId || this.principalId.length === 0) {
      throw new Error('principalId is required');
    }

    return {
      tenantId: this.tenantId,
      principalId: this.principalId,
      correlationId: this.correlationId,
      requestId: this.requestId,
      clientIp: this.clientIp,
      userAgent: this.userAgent,
    };
  }
}

export function createDataAccessContext(
  tenantId: string,
  principalId: string,
  options?: {
    correlationId?: string;
    requestId?: string;
    clientIp?: string;
    userAgent?: string;
  }
): DataAccessContext {
  return new DataAccessContextBuilder()
    .setTenantId(tenantId)
    .setPrincipalId(principalId)
    .setCorrelationId(options?.correlationId ?? '')
    .setRequestId(options?.requestId ?? '')
    .setClientIp(options?.clientIp ?? '')
    .setUserAgent(options?.userAgent ?? '')
    .build();
}
