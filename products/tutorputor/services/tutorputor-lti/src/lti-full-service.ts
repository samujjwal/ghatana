/**
 * @doc.type service
 * @doc.purpose Full LTI 1.3 implementation with OIDC, Deep Linking, AGS, and NRPS
 * @doc.layer product
 * @doc.pattern Service
 */

import type { PrismaClient } from '@prisma/client';
import * as jose from 'jose';
import { v4 as uuid } from 'uuid';
import type {
  TenantId,
  UserId,
  ModuleId,
  LtiPlatformId,
  LtiContextId,
  LtiResourceLinkId,
  LtiPlatform,
  LtiLaunchContext,
  LtiUserClaims,
  LtiDeepLinkingRequest,
  LtiContentItem,
  LtiScore,
  LtiMember,
  LtiSession,
  LtiGradePassbackResult,
  LtiLineItem,
  LtiRole,
  PaginationArgs,
  PaginatedResult,
  ClassroomId,
} from '@ghatana/tutorputor-contracts/v1/types';

import type {
  LtiPlatformService,
  LtiLaunchService,
  LtiDeepLinkingService,
  LtiGradeService,
  LtiRosterService,
} from '@ghatana/tutorputor-contracts/v1/services';

import type {
  LtiIdTokenClaims,
  OidcState,
  LtiAccessToken,
  ToolConfiguration,
} from './types.js.js';

// In-memory state store (use Redis in production)
const oidcStateStore = new Map<string, OidcState>();
const nonceStore = new Set<string>();

// Tool configuration
const TOOL_ISSUER = process.env.LTI_TOOL_ISSUER ?? 'https://tutorputor.ghatana.com';
const TOOL_CLIENT_ID = process.env.LTI_TOOL_CLIENT_ID ?? 'tutorputor-lti-tool';

/**
 * LTI Platform Service implementation.
 */
export class LtiPlatformServiceImpl implements LtiPlatformService {
  constructor(
    private readonly prisma: PrismaClient,
    private readonly keyPair: { publicKey: jose.KeyLike; privateKey: jose.KeyLike },
  ) {}

  async listPlatforms(args: {
    tenantId: TenantId;
    isActive?: boolean;
  }): Promise<LtiPlatform[]> {
    const where: any = { tenantId: args.tenantId };
    if (args.isActive !== undefined) {
      where.isActive = args.isActive;
    }

    const platforms = await this.prisma.lTIPlatform.findMany({
      where,
      orderBy: { createdAt: 'desc' },
    });

    return platforms.map((p) => this.mapToPlatform(p));
  }

  async getPlatform(args: {
    tenantId: TenantId;
    platformId: LtiPlatformId;
  }): Promise<LtiPlatform | null> {
    const platform = await this.prisma.lTIPlatform.findFirst({
      where: {
        id: args.platformId,
        tenantId: args.tenantId,
      },
    });

    return platform ? this.mapToPlatform(platform) : null;
  }

  async registerPlatform(args: {
    tenantId: TenantId;
    name: string;
    issuer: string;
    clientId: string;
    deploymentId: string;
    authLoginUrl: string;
    authTokenUrl: string;
    jwksUrl: string;
    publicKeyPem?: string;
  }): Promise<LtiPlatform> {
    const platform = await this.prisma.lTIPlatform.create({
      data: {
        tenantId: args.tenantId,
        platformName: args.name,
        issuer: args.issuer,
        clientId: args.clientId,
        deploymentId: args.deploymentId,
        authUrl: args.authLoginUrl,
        tokenUrl: args.authTokenUrl,
        jwksUrl: args.jwksUrl,
        publicKeyPem: args.publicKeyPem,
        isActive: true,
      },
    });

    return this.mapToPlatform(platform);
  }

  async updatePlatform(args: {
    tenantId: TenantId;
    platformId: LtiPlatformId;
    updates: Partial<Omit<LtiPlatform, 'id' | 'tenantId' | 'createdAt' | 'updatedAt'>>;
  }): Promise<LtiPlatform> {
    const platform = await this.prisma.lTIPlatform.update({
      where: { id: args.platformId },
      data: {
        platformName: args.updates.name,
        issuer: args.updates.issuer,
        clientId: args.updates.clientId,
        deploymentId: args.updates.deploymentId,
        authUrl: args.updates.authLoginUrl,
        tokenUrl: args.updates.authTokenUrl,
        jwksUrl: args.updates.jwksUrl,
        publicKeyPem: args.updates.publicKeyPem,
        isActive: args.updates.isActive,
        updatedAt: new Date(),
      },
    });

    return this.mapToPlatform(platform);
  }

  async deactivatePlatform(args: {
    tenantId: TenantId;
    platformId: LtiPlatformId;
  }): Promise<void> {
    await this.prisma.lTIPlatform.update({
      where: { id: args.platformId },
      data: { isActive: false, updatedAt: new Date() },
    });
  }

  async getToolConfiguration(args: {
    tenantId: TenantId;
  }): Promise<{
    issuer: string;
    clientId: string;
    publicJwks: object;
    authLoginUrl: string;
    launchUrl: string;
    deepLinkingUrl: string;
    jwksUrl: string;
  }> {
    const publicJwk = await jose.exportJWK(this.keyPair.publicKey);

    return {
      issuer: TOOL_ISSUER,
      clientId: TOOL_CLIENT_ID,
      publicJwks: {
        keys: [
          {
            ...publicJwk,
            kid: 'tutorputor-lti-key',
            use: 'sig',
            alg: 'RS256',
          },
        ],
      },
      authLoginUrl: `${TOOL_ISSUER}/lti/login`,
      launchUrl: `${TOOL_ISSUER}/lti/launch`,
      deepLinkingUrl: `${TOOL_ISSUER}/lti/deep-linking`,
      jwksUrl: `${TOOL_ISSUER}/.well-known/jwks.json`,
    };
  }

  private mapToPlatform(record: any): LtiPlatform {
    return {
      id: record.id as LtiPlatformId,
      tenantId: record.tenantId as TenantId,
      name: record.platformName,
      issuer: record.issuer,
      clientId: record.clientId,
      deploymentId: record.deploymentId ?? '',
      authLoginUrl: record.authUrl,
      authTokenUrl: record.tokenUrl,
      jwksUrl: record.jwksUrl,
      publicKeyPem: record.publicKeyPem ?? undefined,
      isActive: record.isActive,
      createdAt: record.createdAt.toISOString(),
      updatedAt: record.updatedAt.toISOString(),
    };
  }
}

/**
 * LTI Launch Service implementation.
 */
export class LtiLaunchServiceImpl implements LtiLaunchService {
  constructor(
    private readonly prisma: PrismaClient,
    private readonly keyPair: { publicKey: jose.KeyLike; privateKey: jose.KeyLike },
  ) {}

  async initiateLogin(args: {
    tenantId: TenantId;
    issuer: string;
    loginHint: string;
    ltiMessageHint?: string;
    targetLinkUri: string;
    clientId: string;
  }): Promise<{ redirectUrl: string; state: string; nonce: string }> {
    // Find platform by issuer
    const platform = await this.prisma.lTIPlatform.findFirst({
      where: {
        tenantId: args.tenantId,
        issuer: args.issuer,
        isActive: true,
      },
    });

    if (!platform) {
      throw new Error(`Platform not found for issuer: ${args.issuer}`);
    }

    // Generate state and nonce
    const state = uuid();
    const nonce = uuid();

    // Store state for later validation
    oidcStateStore.set(state, {
      tenantId: args.tenantId,
      platformId: platform.id as LtiPlatformId,
      targetLinkUri: args.targetLinkUri,
      nonce,
      createdAt: new Date(),
    });

    // Clean up old states (simple cleanup, use TTL in production)
    if (oidcStateStore.size > 10000) {
      const now = Date.now();
      for (const [key, value] of oidcStateStore.entries()) {
        if (now - value.createdAt.getTime() > 10 * 60 * 1000) {
          oidcStateStore.delete(key);
        }
      }
    }

    // Build redirect URL
    const params = new URLSearchParams({
      scope: 'openid',
      response_type: 'id_token',
      response_mode: 'form_post',
      prompt: 'none',
      client_id: args.clientId,
      redirect_uri: `${TOOL_ISSUER}/lti/launch`,
      login_hint: args.loginHint,
      state,
      nonce,
    });

    if (args.ltiMessageHint) {
      params.set('lti_message_hint', args.ltiMessageHint);
    }

    const redirectUrl = `${platform.authUrl}?${params.toString()}`;

    return { redirectUrl, state, nonce };
  }

  async validateLaunch(args: {
    tenantId: TenantId;
    idToken: string;
    state: string;
  }): Promise<{
    valid: boolean;
    session?: LtiSession;
    userClaims?: LtiUserClaims;
    launchContext?: LtiLaunchContext;
    error?: string;
  }> {
    // Verify state
    const storedState = oidcStateStore.get(args.state);
    if (!storedState) {
      return { valid: false, error: 'Invalid or expired state' };
    }

    if (storedState.tenantId !== args.tenantId) {
      return { valid: false, error: 'Tenant mismatch' };
    }

    // Get platform
    const platform = await this.prisma.lTIPlatform.findUnique({
      where: { id: storedState.platformId },
    });

    if (!platform) {
      return { valid: false, error: 'Platform not found' };
    }

    try {
      // Fetch platform JWKS
      const jwks = jose.createRemoteJWKSet(new URL(platform.jwksUrl));

      // Verify and decode token
      const { payload } = await jose.jwtVerify(args.idToken, jwks, {
        issuer: platform.issuer,
        audience: platform.clientId,
      }) as { payload: LtiIdTokenClaims };

      // Verify nonce
      if (payload.nonce !== storedState.nonce) {
        return { valid: false, error: 'Nonce mismatch' };
      }

      // Check for replay
      if (nonceStore.has(payload.nonce)) {
        return { valid: false, error: 'Replay attack detected' };
      }
      nonceStore.add(payload.nonce);

      // Clean up old nonces
      if (nonceStore.size > 50000) {
        nonceStore.clear();
      }

      // Delete used state
      oidcStateStore.delete(args.state);

      // Verify LTI version
      if (payload['https://purl.imsglobal.org/spec/lti/claim/version'] !== '1.3.0') {
        return { valid: false, error: 'Invalid LTI version' };
      }

      // Extract user claims
      const userClaims: LtiUserClaims = {
        sub: payload.sub,
        name: payload.name,
        givenName: payload.given_name,
        familyName: payload.family_name,
        email: payload.email,
        picture: payload.picture,
        roles: (payload['https://purl.imsglobal.org/spec/lti/claim/roles'] ?? []) as LtiRole[],
      };

      // Extract context
      const contextClaim = payload['https://purl.imsglobal.org/spec/lti/claim/context'];
      const resourceLink = payload['https://purl.imsglobal.org/spec/lti/claim/resource_link'];

      const launchContext: LtiLaunchContext = {
        platformId: platform.id as LtiPlatformId,
        deploymentId: payload['https://purl.imsglobal.org/spec/lti/claim/deployment_id'],
        contextId: (contextClaim?.id ?? '') as LtiContextId,
        contextType: (contextClaim?.type?.[0] ?? 'CourseOffering') as any,
        contextLabel: contextClaim?.label ?? '',
        contextTitle: contextClaim?.title ?? '',
        resourceLinkId: resourceLink.id as LtiResourceLinkId,
        resourceLinkTitle: resourceLink.title ?? '',
        targetLinkUri: payload['https://purl.imsglobal.org/spec/lti/claim/target_link_uri'],
      };

      // Extract module ID from custom claims or target URI
      const customClaims = payload['https://purl.imsglobal.org/spec/lti/claim/custom'];
      let targetModuleId: ModuleId | undefined;
      if (customClaims?.module_id) {
        targetModuleId = customClaims.module_id as ModuleId;
      } else {
        // Try to extract from target_link_uri
        const match = launchContext.targetLinkUri.match(/\/modules?\/([a-z0-9-]+)/i);
        if (match) {
          targetModuleId = match[1] as ModuleId;
        }
      }

      // Create session
      const session = await this.prisma.ltiSession.create({
        data: {
          platformId: platform.id,
          deploymentId: launchContext.deploymentId,
          ltiUserId: payload.sub,
          contextId: launchContext.contextId,
          resourceLinkId: launchContext.resourceLinkId,
          roles: userClaims.roles,
          targetModuleId,
          launchData: payload as any,
          expiresAt: new Date(Date.now() + 24 * 60 * 60 * 1000), // 24 hours
        },
      });

      // Store AGS and NRPS endpoints in context if provided
      const agsEndpoint = payload['https://purl.imsglobal.org/spec/lti-ags/claim/endpoint'];
      const nrpsEndpoint = payload['https://purl.imsglobal.org/spec/lti-nrps/claim/namesroleservice'];

      if (contextClaim?.id && (agsEndpoint || nrpsEndpoint)) {
        await this.prisma.ltiContext.upsert({
          where: {
            platformId_ltiContextId: {
              platformId: platform.id,
              ltiContextId: contextClaim.id,
            },
          },
          create: {
            platformId: platform.id,
            ltiContextId: contextClaim.id,
            type: contextClaim.type?.[0] ?? 'CourseOffering',
            label: contextClaim.label ?? '',
            title: contextClaim.title ?? '',
            lineItemsUrl: agsEndpoint?.lineitems,
            membershipsUrl: nrpsEndpoint?.context_memberships_url,
          },
          update: {
            type: contextClaim.type?.[0] ?? 'CourseOffering',
            label: contextClaim.label ?? '',
            title: contextClaim.title ?? '',
            lineItemsUrl: agsEndpoint?.lineitems,
            membershipsUrl: nrpsEndpoint?.context_memberships_url,
            updatedAt: new Date(),
          },
        });
      }

      return {
        valid: true,
        session: {
          id: session.id,
          platformId: platform.id as LtiPlatformId,
          deploymentId: launchContext.deploymentId,
          userId: session.userId as UserId,
          ltiUserId: payload.sub,
          contextId: launchContext.contextId,
          resourceLinkId: launchContext.resourceLinkId,
          roles: userClaims.roles,
          targetModuleId,
          launchData: payload as any,
          createdAt: session.createdAt.toISOString(),
          expiresAt: session.expiresAt.toISOString(),
        },
        userClaims,
        launchContext,
      };
    } catch (error) {
      return {
        valid: false,
        error: error instanceof Error ? error.message : 'Token validation failed',
      };
    }
  }

  async resolveUser(args: {
    tenantId: TenantId;
    platformId: LtiPlatformId;
    userClaims: LtiUserClaims;
  }): Promise<{ userId: UserId; isNewUser: boolean }> {
    // Check for existing mapping
    const existingMapping = await this.prisma.ltiUserMapping.findFirst({
      where: {
        platformId: args.platformId,
        ltiUserId: args.userClaims.sub,
      },
    });

    if (existingMapping) {
      // Update session with userId
      return {
        userId: existingMapping.userId as UserId,
        isNewUser: false,
      };
    }

    // Check if user exists by email
    let user = args.userClaims.email
      ? await this.prisma.user.findFirst({
          where: {
            tenantId: args.tenantId,
            email: args.userClaims.email,
          },
        })
      : null;

    let isNewUser = false;

    if (!user) {
      // Create new user
      const displayName = args.userClaims.name ?? 
        [args.userClaims.givenName, args.userClaims.familyName].filter(Boolean).join(' ') ??
        args.userClaims.email ??
        `LTI User ${args.userClaims.sub.slice(0, 8)}`;

      // Determine role from LTI roles
      const isInstructor = args.userClaims.roles.some((r) =>
        r.includes('Instructor') || r.includes('Administrator'),
      );

      user = await this.prisma.user.create({
        data: {
          tenantId: args.tenantId,
          email: args.userClaims.email ?? `lti-${args.userClaims.sub}@tutorputor.local`,
          displayName,
          role: isInstructor ? 'teacher' : 'student',
          avatarUrl: args.userClaims.picture,
        },
      });

      isNewUser = true;
    }

    // Create mapping
    await this.prisma.ltiUserMapping.create({
      data: {
        platformId: args.platformId,
        ltiUserId: args.userClaims.sub,
        userId: user.id,
        email: args.userClaims.email,
        name: args.userClaims.name,
      },
    });

    return {
      userId: user.id as UserId,
      isNewUser,
    };
  }

  async getSession(args: { sessionId: string }): Promise<LtiSession | null> {
    const session = await this.prisma.ltiSession.findUnique({
      where: { id: args.sessionId },
    });

    if (!session) return null;

    // Check if expired
    if (new Date() > session.expiresAt) {
      return null;
    }

    return {
      id: session.id,
      platformId: session.platformId as LtiPlatformId,
      deploymentId: session.deploymentId,
      userId: session.userId as UserId,
      ltiUserId: session.ltiUserId,
      contextId: session.contextId as LtiContextId,
      resourceLinkId: session.resourceLinkId as LtiResourceLinkId,
      roles: session.roles as LtiRole[],
      targetModuleId: session.targetModuleId as ModuleId | undefined,
      launchData: session.launchData as Record<string, unknown>,
      createdAt: session.createdAt.toISOString(),
      expiresAt: session.expiresAt.toISOString(),
    };
  }

  async extendSession(args: {
    sessionId: string;
    additionalMinutes: number;
  }): Promise<LtiSession> {
    const session = await this.prisma.ltiSession.update({
      where: { id: args.sessionId },
      data: {
        expiresAt: new Date(Date.now() + args.additionalMinutes * 60 * 1000),
      },
    });

    return {
      id: session.id,
      platformId: session.platformId as LtiPlatformId,
      deploymentId: session.deploymentId,
      userId: session.userId as UserId,
      ltiUserId: session.ltiUserId,
      contextId: session.contextId as LtiContextId,
      resourceLinkId: session.resourceLinkId as LtiResourceLinkId,
      roles: session.roles as LtiRole[],
      targetModuleId: session.targetModuleId as ModuleId | undefined,
      launchData: session.launchData as Record<string, unknown>,
      createdAt: session.createdAt.toISOString(),
      expiresAt: session.expiresAt.toISOString(),
    };
  }
}

/**
 * LTI Deep Linking Service implementation.
 */
export class LtiDeepLinkingServiceImpl implements LtiDeepLinkingService {
  constructor(
    private readonly prisma: PrismaClient,
    private readonly keyPair: { publicKey: jose.KeyLike; privateKey: jose.KeyLike },
  ) {}

  async parseDeepLinkingRequest(args: {
    tenantId: TenantId;
    idToken: string;
  }): Promise<LtiDeepLinkingRequest> {
    // Decode token (assume already validated)
    const [, payloadB64] = args.idToken.split('.');
    const payload = JSON.parse(Buffer.from(payloadB64, 'base64url').toString()) as LtiIdTokenClaims;

    const settings = payload['https://purl.imsglobal.org/spec/lti-dl/claim/deep_linking_settings'];
    if (!settings) {
      throw new Error('Not a deep linking request');
    }

    // Get platform
    const platform = await this.prisma.lTIPlatform.findFirst({
      where: {
        tenantId: args.tenantId,
        issuer: payload.iss,
      },
    });

    if (!platform) {
      throw new Error('Platform not found');
    }

    return {
      platformId: platform.id as LtiPlatformId,
      deploymentId: payload['https://purl.imsglobal.org/spec/lti/claim/deployment_id'],
      deepLinkingSettingsData: settings.data ?? '',
      acceptTypes: settings.accept_types,
      acceptPresentationDocumentTargets: settings.accept_presentation_document_targets,
      acceptMultiple: settings.accept_multiple ?? true,
      autoCreate: settings.auto_create ?? false,
    };
  }

  async listAvailableContent(args: {
    tenantId: TenantId;
    contentTypes: string[];
    search?: string;
    pagination: PaginationArgs;
  }): Promise<PaginatedResult<{
    moduleId: ModuleId;
    title: string;
    description: string;
    thumbnailUrl?: string;
    domain: string;
    difficulty: string;
  }>> {
    const where: any = {
      tenantId: args.tenantId,
      status: 'PUBLISHED',
    };

    if (args.search) {
      where.OR = [
        { title: { contains: args.search, mode: 'insensitive' } },
        { description: { contains: args.search, mode: 'insensitive' } },
      ];
    }

    const [modules, total] = await Promise.all([
      this.prisma.module.findMany({
        where,
        orderBy: { title: 'asc' },
        take: args.pagination.limit,
        skip: args.pagination.cursor ? 1 : 0,
        cursor: args.pagination.cursor ? { id: args.pagination.cursor } : undefined,
      }),
      this.prisma.module.count({ where }),
    ]);

    return {
      items: modules.map((m) => ({
        moduleId: m.id as ModuleId,
        title: m.title,
        description: m.description ?? '',
        thumbnailUrl: m.thumbnailUrl ?? undefined,
        domain: m.domain,
        difficulty: m.difficulty,
      })),
      nextCursor: modules.length === args.pagination.limit
        ? modules[modules.length - 1].id
        : undefined,
      totalCount: total,
      hasMore: modules.length === args.pagination.limit,
    };
  }

  async buildContentItem(args: {
    tenantId: TenantId;
    moduleId: ModuleId;
    includeLineItem?: boolean;
    customParameters?: Record<string, string>;
  }): Promise<LtiContentItem> {
    const module = await this.prisma.module.findUnique({
      where: { id: args.moduleId },
    });

    if (!module) {
      throw new Error('Module not found');
    }

    const item: LtiContentItem = {
      type: 'ltiResourceLink',
      title: module.title,
      text: module.description ?? undefined,
      url: `${TOOL_ISSUER}/lti/launch/${module.id}`,
      icon: module.thumbnailUrl
        ? { url: module.thumbnailUrl, width: 100, height: 100 }
        : undefined,
      custom: {
        module_id: module.id,
        module_slug: module.slug,
        domain: module.domain,
        difficulty: module.difficulty,
        ...args.customParameters,
      },
    };

    if (args.includeLineItem) {
      item.lineItem = {
        scoreMaximum: 100,
        label: module.title,
        resourceId: module.id,
        tag: 'tutorputor-module',
      };
    }

    return item;
  }

  async createDeepLinkingResponse(args: {
    tenantId: TenantId;
    platformId: LtiPlatformId;
    deepLinkingSettingsData: string;
    contentItems: LtiContentItem[];
  }): Promise<{ jwt: string; redirectUrl: string }> {
    const platform = await this.prisma.lTIPlatform.findUnique({
      where: { id: args.platformId },
    });

    if (!platform) {
      throw new Error('Platform not found');
    }

    // Build JWT payload
    const payload = {
      iss: TOOL_CLIENT_ID,
      aud: platform.issuer,
      iat: Math.floor(Date.now() / 1000),
      exp: Math.floor(Date.now() / 1000) + 300, // 5 minutes
      nonce: uuid(),
      'https://purl.imsglobal.org/spec/lti/claim/message_type': 'LtiDeepLinkingResponse',
      'https://purl.imsglobal.org/spec/lti/claim/version': '1.3.0',
      'https://purl.imsglobal.org/spec/lti/claim/deployment_id': platform.deploymentId,
      'https://purl.imsglobal.org/spec/lti-dl/claim/content_items': args.contentItems,
      'https://purl.imsglobal.org/spec/lti-dl/claim/data': args.deepLinkingSettingsData,
    };

    // Sign JWT
    const jwt = await new jose.SignJWT(payload)
      .setProtectedHeader({ alg: 'RS256', kid: 'tutorputor-lti-key' })
      .sign(this.keyPair.privateKey);

    // Get return URL from settings data (if embedded)
    let redirectUrl = platform.authUrl.replace('/auth', '/deep_link_return');

    return { jwt, redirectUrl };
  }
}

/**
 * LTI Grade Service implementation.
 */
export class LtiGradeServiceImpl implements LtiGradeService {
  constructor(
    private readonly prisma: PrismaClient,
    private readonly keyPair: { publicKey: jose.KeyLike; privateKey: jose.KeyLike },
  ) {}

  async listLineItems(args: {
    tenantId: TenantId;
    platformId: LtiPlatformId;
    contextId: LtiContextId;
  }): Promise<LtiLineItem[]> {
    const lineItems = await this.prisma.ltiLineItem.findMany({
      where: {
        platformId: args.platformId,
        contextId: args.contextId,
      },
      orderBy: { createdAt: 'desc' },
    });

    return lineItems.map((li) => ({
      scoreMaximum: li.scoreMaximum,
      label: li.label,
      resourceId: li.resourceId ?? undefined,
      tag: li.tag ?? undefined,
      startDateTime: li.startDateTime?.toISOString(),
      endDateTime: li.endDateTime?.toISOString(),
    }));
  }

  async createLineItem(args: {
    tenantId: TenantId;
    platformId: LtiPlatformId;
    contextId: LtiContextId;
    lineItem: Omit<LtiLineItem, 'id'>;
  }): Promise<LtiLineItem & { id: string }> {
    const context = await this.prisma.ltiContext.findFirst({
      where: {
        platformId: args.platformId,
        ltiContextId: args.contextId,
      },
    });

    if (!context?.lineItemsUrl) {
      throw new Error('AGS not available for this context');
    }

    // Get access token
    const accessToken = await this.getAccessToken(args.platformId, [
      'https://purl.imsglobal.org/spec/lti-ags/scope/lineitem',
    ]);

    // Create line item via AGS API
    const response = await fetch(context.lineItemsUrl, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Content-Type': 'application/vnd.ims.lis.v2.lineitem+json',
      },
      body: JSON.stringify({
        scoreMaximum: args.lineItem.scoreMaximum,
        label: args.lineItem.label,
        resourceId: args.lineItem.resourceId,
        tag: args.lineItem.tag,
        startDateTime: args.lineItem.startDateTime,
        endDateTime: args.lineItem.endDateTime,
      }),
    });

    if (!response.ok) {
      throw new Error(`Failed to create line item: ${response.statusText}`);
    }

    const result = await response.json();

    // Store locally
    const lineItem = await this.prisma.ltiLineItem.create({
      data: {
        platformId: args.platformId,
        contextId: args.contextId,
        ltiLineItemId: result.id,
        scoreMaximum: args.lineItem.scoreMaximum,
        label: args.lineItem.label,
        resourceId: args.lineItem.resourceId,
        tag: args.lineItem.tag,
        startDateTime: args.lineItem.startDateTime ? new Date(args.lineItem.startDateTime) : null,
        endDateTime: args.lineItem.endDateTime ? new Date(args.lineItem.endDateTime) : null,
      },
    });

    return {
      id: lineItem.id,
      scoreMaximum: lineItem.scoreMaximum,
      label: lineItem.label,
      resourceId: lineItem.resourceId ?? undefined,
      tag: lineItem.tag ?? undefined,
      startDateTime: lineItem.startDateTime?.toISOString(),
      endDateTime: lineItem.endDateTime?.toISOString(),
    };
  }

  async submitScore(args: {
    tenantId: TenantId;
    sessionId: string;
    lineItemId: string;
    score: LtiScore;
  }): Promise<LtiGradePassbackResult> {
    const session = await this.prisma.ltiSession.findUnique({
      where: { id: args.sessionId },
    });

    if (!session) {
      return {
        success: false,
        lineItemId: args.lineItemId,
        userId: args.score.userId,
        scoreGiven: args.score.scoreGiven,
        error: 'Session not found',
      };
    }

    const lineItem = await this.prisma.ltiLineItem.findUnique({
      where: { id: args.lineItemId },
    });

    if (!lineItem) {
      return {
        success: false,
        lineItemId: args.lineItemId,
        userId: args.score.userId,
        scoreGiven: args.score.scoreGiven,
        error: 'Line item not found',
      };
    }

    try {
      const accessToken = await this.getAccessToken(session.platformId as LtiPlatformId, [
        'https://purl.imsglobal.org/spec/lti-ags/scope/score',
      ]);

      const scoreUrl = `${lineItem.ltiLineItemId}/scores`;

      const response = await fetch(scoreUrl, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${accessToken}`,
          'Content-Type': 'application/vnd.ims.lis.v1.score+json',
        },
        body: JSON.stringify({
          userId: args.score.userId,
          scoreGiven: args.score.scoreGiven,
          scoreMaximum: args.score.scoreMaximum,
          activityProgress: args.score.activityProgress,
          gradingProgress: args.score.gradingProgress,
          timestamp: args.score.timestamp,
          comment: args.score.comment,
        }),
      });

      if (!response.ok) {
        throw new Error(`AGS API error: ${response.statusText}`);
      }

      // Store score record
      await this.prisma.ltiScore.create({
        data: {
          lineItemId: args.lineItemId,
          userId: session.userId ?? '',
          ltiUserId: args.score.userId,
          scoreGiven: args.score.scoreGiven,
          scoreMaximum: args.score.scoreMaximum,
          activityProgress: args.score.activityProgress,
          gradingProgress: args.score.gradingProgress,
          comment: args.score.comment,
          syncedAt: new Date(),
        },
      });

      return {
        success: true,
        lineItemId: args.lineItemId,
        userId: args.score.userId,
        scoreGiven: args.score.scoreGiven,
        passedAt: new Date().toISOString(),
      };
    } catch (error) {
      // Store failed attempt
      await this.prisma.ltiScore.create({
        data: {
          lineItemId: args.lineItemId,
          userId: session.userId ?? '',
          ltiUserId: args.score.userId,
          scoreGiven: args.score.scoreGiven,
          scoreMaximum: args.score.scoreMaximum,
          activityProgress: args.score.activityProgress,
          gradingProgress: args.score.gradingProgress,
          comment: args.score.comment,
          syncError: String(error),
        },
      });

      return {
        success: false,
        lineItemId: args.lineItemId,
        userId: args.score.userId,
        scoreGiven: args.score.scoreGiven,
        error: error instanceof Error ? error.message : 'Failed to submit score',
      };
    }
  }

  async submitScores(args: {
    tenantId: TenantId;
    platformId: LtiPlatformId;
    contextId: LtiContextId;
    lineItemId: string;
    scores: LtiScore[];
  }): Promise<LtiGradePassbackResult[]> {
    const results: LtiGradePassbackResult[] = [];

    for (const score of args.scores) {
      // Find session for user
      const session = await this.prisma.ltiSession.findFirst({
        where: {
          platformId: args.platformId,
          contextId: args.contextId,
          ltiUserId: score.userId,
        },
        orderBy: { createdAt: 'desc' },
      });

      if (session) {
        const result = await this.submitScore({
          tenantId: args.tenantId,
          sessionId: session.id,
          lineItemId: args.lineItemId,
          score,
        });
        results.push(result);
      } else {
        results.push({
          success: false,
          lineItemId: args.lineItemId,
          userId: score.userId,
          scoreGiven: score.scoreGiven,
          error: 'No session found for user',
        });
      }
    }

    return results;
  }

  async getScores(args: {
    tenantId: TenantId;
    platformId: LtiPlatformId;
    contextId: LtiContextId;
    lineItemId: string;
    userId?: string;
  }): Promise<LtiScore[]> {
    const where: any = { lineItemId: args.lineItemId };
    if (args.userId) {
      where.ltiUserId = args.userId;
    }

    const scores = await this.prisma.ltiScore.findMany({
      where,
      orderBy: { submittedAt: 'desc' },
    });

    return scores.map((s) => ({
      userId: s.ltiUserId,
      scoreGiven: s.scoreGiven,
      scoreMaximum: s.scoreMaximum,
      activityProgress: s.activityProgress as any,
      gradingProgress: s.gradingProgress as any,
      timestamp: s.submittedAt.toISOString(),
      comment: s.comment ?? undefined,
    }));
  }

  async syncGrades(args: {
    tenantId: TenantId;
    moduleId: ModuleId;
    contextId: LtiContextId;
  }): Promise<{ synced: number; failed: number; errors: string[] }> {
    // Find line items for this module
    const lineItems = await this.prisma.ltiLineItem.findMany({
      where: {
        contextId: args.contextId,
        moduleId: args.moduleId,
      },
    });

    if (lineItems.length === 0) {
      return { synced: 0, failed: 0, errors: ['No line items found for module'] };
    }

    // Get enrollments for module
    const enrollments = await this.prisma.enrollment.findMany({
      where: {
        moduleId: args.moduleId,
        tenantId: args.tenantId,
      },
      include: { user: true },
    });

    let synced = 0;
    let failed = 0;
    const errors: string[] = [];

    for (const enrollment of enrollments) {
      // Find LTI mapping for user
      const mapping = await this.prisma.ltiUserMapping.findFirst({
        where: { userId: enrollment.userId },
      });

      if (!mapping) continue;

      for (const lineItem of lineItems) {
        const score: LtiScore = {
          userId: mapping.ltiUserId,
          scoreGiven: enrollment.progressPercent,
          scoreMaximum: 100,
          activityProgress: enrollment.progressPercent >= 100 ? 'Completed' : 'InProgress',
          gradingProgress: 'FullyGraded',
          timestamp: new Date().toISOString(),
        };

        // Find session for user
        const session = await this.prisma.ltiSession.findFirst({
          where: {
            ltiUserId: mapping.ltiUserId,
            contextId: args.contextId,
          },
          orderBy: { createdAt: 'desc' },
        });

        if (session) {
          const result = await this.submitScore({
            tenantId: args.tenantId,
            sessionId: session.id,
            lineItemId: lineItem.id,
            score,
          });

          if (result.success) {
            synced++;
          } else {
            failed++;
            if (result.error) errors.push(result.error);
          }
        }
      }
    }

    return { synced, failed, errors };
  }

  private async getAccessToken(
    platformId: LtiPlatformId,
    scopes: string[],
  ): Promise<string> {
    const platform = await this.prisma.lTIPlatform.findUnique({
      where: { id: platformId },
    });

    if (!platform) {
      throw new Error('Platform not found');
    }

    // Create client assertion JWT
    const assertion = await new jose.SignJWT({
      iss: TOOL_CLIENT_ID,
      sub: TOOL_CLIENT_ID,
      aud: platform.tokenUrl,
      iat: Math.floor(Date.now() / 1000),
      exp: Math.floor(Date.now() / 1000) + 300,
      jti: uuid(),
    })
      .setProtectedHeader({ alg: 'RS256', kid: 'tutorputor-lti-key' })
      .sign(this.keyPair.privateKey);

    // Request token
    const response = await fetch(platform.tokenUrl, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body: new URLSearchParams({
        grant_type: 'client_credentials',
        client_assertion_type: 'urn:ietf:params:oauth:client-assertion-type:jwt-bearer',
        client_assertion: assertion,
        scope: scopes.join(' '),
      }),
    });

    if (!response.ok) {
      throw new Error(`Token request failed: ${response.statusText}`);
    }

    const token: LtiAccessToken = await response.json();
    return token.access_token;
  }
}

/**
 * LTI Roster Service implementation.
 */
export class LtiRosterServiceImpl implements LtiRosterService {
  constructor(
    private readonly prisma: PrismaClient,
    private readonly keyPair: { publicKey: jose.KeyLike; privateKey: jose.KeyLike },
    private readonly gradeService: LtiGradeServiceImpl,
  ) {}

  async fetchMembers(args: {
    tenantId: TenantId;
    platformId: LtiPlatformId;
    contextId: LtiContextId;
    role?: string;
  }): Promise<LtiMember[]> {
    const context = await this.prisma.ltiContext.findFirst({
      where: {
        platformId: args.platformId,
        ltiContextId: args.contextId,
      },
    });

    if (!context?.membershipsUrl) {
      throw new Error('NRPS not available for this context');
    }

    const accessToken = await this.getAccessToken(args.platformId);

    let url = context.membershipsUrl;
    if (args.role) {
      url += `?role=${encodeURIComponent(args.role)}`;
    }

    const response = await fetch(url, {
      headers: {
        'Authorization': `Bearer ${accessToken}`,
        'Accept': 'application/vnd.ims.lti-nrps.v2.membershipcontainer+json',
      },
    });

    if (!response.ok) {
      throw new Error(`NRPS request failed: ${response.statusText}`);
    }

    const data = await response.json();

    return data.members.map((m: any) => ({
      userId: m.user_id,
      roles: m.roles as LtiRole[],
      name: m.name,
      email: m.email,
      status: m.status ?? 'Active',
      ltiContextId: args.contextId,
    }));
  }

  async syncMembers(args: {
    tenantId: TenantId;
    platformId: LtiPlatformId;
    contextId: LtiContextId;
    classroomId?: ClassroomId;
    createMissing?: boolean;
  }): Promise<{
    synced: number;
    created: number;
    updated: number;
    errors: string[];
  }> {
    const members = await this.fetchMembers({
      tenantId: args.tenantId,
      platformId: args.platformId,
      contextId: args.contextId,
    });

    let synced = 0;
    let created = 0;
    let updated = 0;
    const errors: string[] = [];

    for (const member of members) {
      try {
        // Check for existing mapping
        let mapping = await this.prisma.ltiUserMapping.findFirst({
          where: {
            platformId: args.platformId,
            ltiUserId: member.userId,
          },
        });

        if (!mapping) {
          // Try to find by email
          if (member.email) {
            const user = await this.prisma.user.findFirst({
              where: {
                tenantId: args.tenantId,
                email: member.email,
              },
            });

            if (user) {
              mapping = await this.prisma.ltiUserMapping.create({
                data: {
                  platformId: args.platformId,
                  ltiUserId: member.userId,
                  userId: user.id,
                  email: member.email,
                  name: member.name,
                },
              });
              synced++;
            } else if (args.createMissing) {
              // Create new user
              const isInstructor = member.roles.some((r) =>
                r.includes('Instructor') || r.includes('Administrator'),
              );

              const newUser = await this.prisma.user.create({
                data: {
                  tenantId: args.tenantId,
                  email: member.email,
                  displayName: member.name ?? member.email,
                  role: isInstructor ? 'teacher' : 'student',
                },
              });

              mapping = await this.prisma.ltiUserMapping.create({
                data: {
                  platformId: args.platformId,
                  ltiUserId: member.userId,
                  userId: newUser.id,
                  email: member.email,
                  name: member.name,
                },
              });
              created++;
            }
          }
        } else {
          // Update existing mapping
          await this.prisma.ltiUserMapping.update({
            where: { id: mapping.id },
            data: {
              email: member.email,
              name: member.name,
              updatedAt: new Date(),
            },
          });
          updated++;
        }

        // Add to classroom if specified
        if (mapping && args.classroomId) {
          await this.prisma.classroomMember.upsert({
            where: {
              classroomId_userId: {
                classroomId: args.classroomId,
                userId: mapping.userId,
              },
            },
            create: {
              classroomId: args.classroomId,
              userId: mapping.userId,
              role: member.roles.some((r) => r.includes('Instructor'))
                ? 'instructor'
                : 'student',
            },
            update: {},
          });
        }
      } catch (error) {
        errors.push(`Failed to sync member ${member.userId}: ${error}`);
      }
    }

    return { synced, created, updated, errors };
  }

  async getMember(args: {
    tenantId: TenantId;
    platformId: LtiPlatformId;
    contextId: LtiContextId;
    ltiUserId: string;
  }): Promise<LtiMember | null> {
    const mapping = await this.prisma.ltiUserMapping.findFirst({
      where: {
        platformId: args.platformId,
        ltiUserId: args.ltiUserId,
      },
    });

    if (!mapping) return null;

    // Get latest session for roles
    const session = await this.prisma.ltiSession.findFirst({
      where: {
        platformId: args.platformId,
        ltiUserId: args.ltiUserId,
        contextId: args.contextId,
      },
      orderBy: { createdAt: 'desc' },
    });

    return {
      userId: args.ltiUserId,
      roles: (session?.roles ?? []) as LtiRole[],
      name: mapping.name ?? undefined,
      email: mapping.email ?? undefined,
      status: 'Active',
      ltiContextId: args.contextId,
    };
  }

  private async getAccessToken(platformId: LtiPlatformId): Promise<string> {
    // Reuse grade service's token acquisition
    return (this.gradeService as any).getAccessToken(platformId, [
      'https://purl.imsglobal.org/spec/lti-nrps/scope/contextmembership.readonly',
    ]);
  }
}

/**
 * Create all LTI services.
 */
export async function createLtiServices(prisma: PrismaClient): Promise<{
  platformService: LtiPlatformService;
  launchService: LtiLaunchService;
  deepLinkingService: LtiDeepLinkingService;
  gradeService: LtiGradeService;
  rosterService: LtiRosterService;
}> {
  // Generate or load key pair
  const { publicKey, privateKey } = await jose.generateKeyPair('RS256');

  const keyPair = { publicKey, privateKey };

  const platformService = new LtiPlatformServiceImpl(prisma, keyPair);
  const launchService = new LtiLaunchServiceImpl(prisma, keyPair);
  const deepLinkingService = new LtiDeepLinkingServiceImpl(prisma, keyPair);
  const gradeService = new LtiGradeServiceImpl(prisma, keyPair);
  const rosterService = new LtiRosterServiceImpl(prisma, keyPair, gradeService);

  return {
    platformService,
    launchService,
    deepLinkingService,
    gradeService,
    rosterService,
  };
}
