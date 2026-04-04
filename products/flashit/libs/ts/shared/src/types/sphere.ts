/**
 * Sphere-related types shared across Flashit applications
 */

export type SphereType =
  | "PERSONAL"
  | "WORK"
  | "HEALTH"
  | "LEARNING"
  | "SOCIAL"
  | "CREATIVE"
  | "FAMILY"
  | "PROJECT"
  | "CUSTOM"
  | string;
export type SphereVisibility =
  | "PRIVATE"
  | "SHARED"
  | "INVITE_ONLY"
  | "LINK_SHARED"
  | "PUBLIC"
  | string;
export type SphereRole = "OWNER" | "EDITOR" | "VIEWER";

export interface Sphere {
  id: string;
  userId: string;
  name: string;
  description: string | null;
  type: SphereType;
  visibility: SphereVisibility;
  createdAt: string;
  updatedAt: string;
  deletedAt: string | null;
  userRole?: SphereRole;
  momentCount?: number;
}

export interface CreateSphereRequest {
  name: string;
  description?: string;
  type: SphereType;
  visibility?: SphereVisibility;
}

export interface UpdateSphereRequest {
  name?: string;
  description?: string;
  type?: SphereType;
  visibility?: SphereVisibility;
}
