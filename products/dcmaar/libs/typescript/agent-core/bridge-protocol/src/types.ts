
export type BridgeDirection = 'extension→desktop' | 'desktop→extension';

export type BridgeMessageKind = 'telemetry' | 'command' | 'ack' | 'heartbeat';

export interface BridgeSignature {
  kid: string;
  value: string;
}

export interface BridgeMetadata {
  bridgeVersion?: string;
  workspaceId?: string;
  [key: string]: unknown;
}

export interface BridgeEnvelope<TPayload extends object = Record<string, unknown>> {
  id: string;
  issuedAt: string;
  direction: BridgeDirection;
  kind: BridgeMessageKind;
  payload: TPayload;
  correlationId?: string;
  metadata?: BridgeMetadata;
  signature?: BridgeSignature;
}

export interface TelemetryPayload<TSnapshot extends object = Record<string, unknown>> {
  batchId: string;
  collectedAt: string;
  data: TSnapshot;
  alerts?: Record<string, unknown>[];
  meta?: Record<string, unknown>;
  estimatedSizeBytes?: number;
}

export type CommandCategory = 'config' | 'policy' | 'action' | 'script';
export type CommandPriority = 'low' | 'medium' | 'high' | 'urgent';

export interface CommandPayload<TBody extends object = Record<string, unknown>> {
  commandId: string;
  category: CommandCategory;
  body: TBody;
  priority: CommandPriority;
  requestedAt: string;
  requestedBy?: string;
}

export interface AckPayload {
  ok: boolean;
  reason?: string;
  correlationId?: string;
  receivedAt: string;
  details?: Record<string, unknown>;
}

export interface HeartbeatPayload {
  sequence: number;
  reportedAt: string;
  status?: string;
  meta?: Record<string, unknown>;
}

export type BridgePayload =
  | TelemetryPayload
  | CommandPayload
  | AckPayload
  | HeartbeatPayload;

export type BridgeMessage = BridgeEnvelope<BridgePayload>;

export interface CreateEnvelopeOptions<TPayload extends object> {
  payload: TPayload;
  direction: BridgeDirection;
  kind: BridgeMessageKind;
  correlationId?: string;
  metadata?: BridgeMetadata;
  signature?: BridgeSignature;
}

export interface AckEnvelopeInput {
  correlationId: string;
  direction: BridgeDirection;
  ok?: boolean;
  reason?: string;
  metadata?: BridgeMetadata;
  details?: Record<string, unknown>;
}

export type BridgeEnvelopeUpdate<TPayload extends object> = Partial<
  BridgeEnvelope<TPayload>
> & {
  payload?: Partial<TPayload>;
  metadata?: Partial<BridgeMetadata>;
  signature?: BridgeSignature | null;
};
