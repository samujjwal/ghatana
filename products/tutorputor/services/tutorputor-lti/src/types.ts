/**
 * @doc.type module
 * @doc.purpose Internal types for LTI service
 * @doc.layer product
 * @doc.pattern Types
 */

import type {
  TenantId,
  UserId,
  ModuleId,
  LtiPlatformId,
  LtiContextId,
  LtiResourceLinkId,
  LtiRole,
} from '@ghatana/tutorputor-contracts/v1/types';

/**
 * LTI Platform database record.
 */
export interface LtiPlatformRecord {
  id: string;
  tenantId: string;
  name: string;
  issuer: string;
  clientId: string;
  deploymentId: string;
  authLoginUrl: string;
  authTokenUrl: string;
  jwksUrl: string;
  publicKeyPem?: string;
  privateKeyPem?: string;
  isActive: boolean;
  createdAt: Date;
  updatedAt: Date;
}

/**
 * LTI Session database record.
 */
export interface LtiSessionRecord {
  id: string;
  platformId: string;
  deploymentId: string;
  userId?: string;
  ltiUserId: string;
  contextId: string;
  resourceLinkId: string;
  roles: string[];
  targetModuleId?: string;
  launchData: Record<string, unknown>;
  accessToken?: string;
  refreshToken?: string;
  tokenExpiresAt?: Date;
  createdAt: Date;
  expiresAt: Date;
}

/**
 * LTI Context (course) database record.
 */
export interface LtiContextRecord {
  id: string;
  platformId: string;
  ltiContextId: string;
  type: string;
  label: string;
  title: string;
  lineItemsUrl?: string;
  membershipsUrl?: string;
  createdAt: Date;
  updatedAt: Date;
}

/**
 * LTI User mapping record.
 */
export interface LtiUserMappingRecord {
  id: string;
  platformId: string;
  ltiUserId: string;
  userId: string;
  email?: string;
  name?: string;
  createdAt: Date;
  updatedAt: Date;
}

/**
 * LTI Line Item (grade column) record.
 */
export interface LtiLineItemRecord {
  id: string;
  platformId: string;
  contextId: string;
  ltiLineItemId: string;
  moduleId?: string;
  scoreMaximum: number;
  label: string;
  resourceId?: string;
  tag?: string;
  startDateTime?: Date;
  endDateTime?: Date;
  createdAt: Date;
  updatedAt: Date;
}

/**
 * LTI Score submission record.
 */
export interface LtiScoreRecord {
  id: string;
  lineItemId: string;
  userId: string;
  ltiUserId: string;
  scoreGiven: number;
  scoreMaximum: number;
  activityProgress: string;
  gradingProgress: string;
  comment?: string;
  submittedAt: Date;
  syncedAt?: Date;
  syncError?: string;
}

/**
 * OIDC State for login flow.
 */
export interface OidcState {
  tenantId: TenantId;
  platformId: LtiPlatformId;
  targetLinkUri: string;
  nonce: string;
  createdAt: Date;
}

/**
 * ID Token claims from LTI launch.
 */
export interface LtiIdTokenClaims {
  // Required OIDC claims
  iss: string;
  sub: string;
  aud: string | string[];
  exp: number;
  iat: number;
  nonce: string;

  // LTI 1.3 required claims
  'https://purl.imsglobal.org/spec/lti/claim/message_type': 'LtiResourceLinkRequest' | 'LtiDeepLinkingRequest';
  'https://purl.imsglobal.org/spec/lti/claim/version': '1.3.0';
  'https://purl.imsglobal.org/spec/lti/claim/deployment_id': string;
  'https://purl.imsglobal.org/spec/lti/claim/target_link_uri': string;
  'https://purl.imsglobal.org/spec/lti/claim/resource_link': {
    id: string;
    title?: string;
    description?: string;
  };

  // Optional claims
  'https://purl.imsglobal.org/spec/lti/claim/roles'?: string[];
  'https://purl.imsglobal.org/spec/lti/claim/context'?: {
    id: string;
    type?: string[];
    label?: string;
    title?: string;
  };
  'https://purl.imsglobal.org/spec/lti/claim/custom'?: Record<string, string>;
  'https://purl.imsglobal.org/spec/lti/claim/launch_presentation'?: {
    document_target?: 'frame' | 'iframe' | 'window';
    width?: number;
    height?: number;
    return_url?: string;
  };

  // Deep linking specific
  'https://purl.imsglobal.org/spec/lti-dl/claim/deep_linking_settings'?: {
    deep_link_return_url: string;
    accept_types: string[];
    accept_presentation_document_targets: string[];
    accept_multiple?: boolean;
    auto_create?: boolean;
    data?: string;
  };

  // AGS (Assignment and Grade Services)
  'https://purl.imsglobal.org/spec/lti-ags/claim/endpoint'?: {
    scope: string[];
    lineitems?: string;
    lineitem?: string;
  };

  // NRPS (Names and Role Provisioning Services)
  'https://purl.imsglobal.org/spec/lti-nrps/claim/namesroleservice'?: {
    context_memberships_url: string;
    service_versions: string[];
  };

  // User identity claims
  name?: string;
  given_name?: string;
  family_name?: string;
  email?: string;
  picture?: string;
}

/**
 * Tool configuration for platform registration.
 */
export interface ToolConfiguration {
  issuer: string;
  clientId: string;
  oidcInitiationUrl: string;
  targetLinkUri: string;
  deepLinkingUrl: string;
  jwksUrl: string;
  publicJwks: {
    keys: Array<{
      kty: string;
      kid: string;
      use: string;
      alg: string;
      n: string;
      e: string;
    }>;
  };
  scopes: string[];
}

/**
 * AGS API response for line items.
 */
export interface AgsLineItemsResponse {
  lineItems: Array<{
    id: string;
    scoreMaximum: number;
    label: string;
    resourceId?: string;
    tag?: string;
    resourceLinkId?: string;
    startDateTime?: string;
    endDateTime?: string;
  }>;
  nextPageUrl?: string;
}

/**
 * NRPS API response for members.
 */
export interface NrpsMembersResponse {
  id: string;
  context: {
    id: string;
    label: string;
    title: string;
  };
  members: Array<{
    user_id: string;
    roles: string[];
    status: 'Active' | 'Inactive' | 'Deleted';
    name?: string;
    email?: string;
    given_name?: string;
    family_name?: string;
    picture?: string;
  }>;
  nextPageUrl?: string;
}

/**
 * Access token for LTI services.
 */
export interface LtiAccessToken {
  access_token: string;
  token_type: 'Bearer';
  expires_in: number;
  scope: string;
}
