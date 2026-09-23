/**
 * WpsAdbTool 3D Scene Bridge Contract (TypeScript)
 *
 * Language-agnostic, protocol-only definitions for the Kotlin Host <-> JS Renderer bridge.
 * Protocol Version: 1
 *
 * NOTE: This contract contains NO Three.js, WebGL, DOM or Camera rendering dependencies.
 */

export const CURRENT_BRIDGE_PROTOCOL_VERSION = 1;
export const DEFAULT_RENDERER_VERSION = "1.0";

/**
 * Frozen Wire Types exchanged across the BridgeTransport channel.
 */
export const WIRE_TYPES = {
  // Host -> Renderer (Downlink)
  SCENE_INIT: "SCENE_INIT",
  STATE_SYNC: "STATE_SYNC",
  BINDING_UPDATE: "BINDING_UPDATE",
  SELECTION_CHANGE: "SELECTION_CHANGE",
  CAMERA_COMMAND: "CAMERA_COMMAND",
  SET_INTERACTION_MODE: "SET_INTERACTION_MODE",
  SET_OBJECT_TRANSFORM: "SET_OBJECT_TRANSFORM",

  // Renderer -> Host (Uplink)
  RENDERER_READY: "RENDERER_READY",
  CAMERA_CHANGED: "CAMERA_CHANGED",
  OBJECT_CLICKED: "OBJECT_CLICKED",
  OBJECT_HOVERED: "OBJECT_HOVERED",
  OBJECT_TRANSFORM_CHANGED: "OBJECT_TRANSFORM_CHANGED",
  RENDERER_ERROR: "RENDERER_ERROR",
} as const;

export type WireType = typeof WIRE_TYPES[keyof typeof WIRE_TYPES];

/**
 * Standard protocol error codes for RENDERER_ERROR.
 */
export const BridgeErrorCodes = {
  PROTOCOL_VERSION_MISMATCH: "PROTOCOL_VERSION_MISMATCH",
  INVALID_MESSAGE: "INVALID_MESSAGE",
  INVALID_PAYLOAD: "INVALID_PAYLOAD",
  INTERNAL_ERROR: "INTERNAL_ERROR",
} as const;

export type BridgeErrorCode = typeof BridgeErrorCodes[keyof typeof BridgeErrorCodes] | (string & {});

/**
 * 3D Vector spatial representation.
 */
export interface Vector3 {
  x: number;
  y: number;
  z: number;
}

/**
 * Top-level Envelope for all messages across the wire.
 */
export interface BridgeEnvelope<T = unknown> {
  type: string;
  version: number;
  timestamp: number;
  payload: T;
}

// ==========================================
// 1. Host -> Renderer (Downlink Contracts)
// ==========================================

export interface SceneCameraDescriptor {
  position: Vector3;
  target: Vector3;
  fov: number;
}

export interface SceneTransformDescriptor {
  position: Vector3;
  rotation: Vector3;
  scale: Vector3;
}

export interface SceneAssetDescriptor {
  id: string;
  fileName: string;
  name: string;
  transform: SceneTransformDescriptor;
}

export interface SceneDescriptor {
  id: string;
  name: string;
  environmentFileName: string | null;
  camera: SceneCameraDescriptor;
  assets: SceneAssetDescriptor[];
  bindableObjectIds: string[];
}

export interface InitScenePayload {
  sceneDescriptor: SceneDescriptor;
  epoch?: number;
}

export const VISUAL_STATUSES = {
  ONLINE: "ONLINE",
  OFFLINE: "OFFLINE",
  UNBOUND: "UNBOUND",
} as const;

export type VisualStatus = typeof VISUAL_STATUSES[keyof typeof VISUAL_STATUSES];

export interface VisualStyle {
  statusColorHex: string;
  isEmissive: boolean;
  emissiveIntensity: number;
  badgeText: string;
  isDimmed: boolean;
}

export interface DeviceVisualDescriptor {
  objectId: string;
  deviceIdentity: string | null;
  displayName: string;
  status: VisualStatus;
  connectionType: string;
  visual: VisualStyle;
}

export interface SceneVisualSnapshot {
  sceneId: string;
  devices: DeviceVisualDescriptor[];
  selectedObjectId: string | null;
}

export interface SyncStatePayload {
  snapshot: SceneVisualSnapshot;
}

export interface UpdateBindingPayload {
  device: DeviceVisualDescriptor;
}

export interface SelectionChangePayload {
  selectedObjectId: string | null;
  focusCamera: boolean;
}

export interface CameraCommandPayload {
  position: Vector3;
  target: Vector3;
  fov?: number;
}

export type InteractionMode = "VIEW" | "BINDING" | "EDITING";

export interface SetInteractionModePayload {
  mode: InteractionMode;
}

export interface SetObjectTransformPayload {
  objectId: string;
  position: Vector3;
  rotation: Vector3;
  scale: Vector3;
}

// ==========================================
// 2. Renderer -> Host (Uplink Contracts)
// ==========================================

export interface RendererReadyPayload {
  protocolVersion: number;
  rendererVersion: string;
}

export interface ObjectClickedPayload {
  objectId: string;
  screenX: number;
  screenY: number;
  isCtrlPressed: boolean;
  isShiftPressed: boolean;
}

export interface ObjectHoveredPayload {
  objectId: string | null;
}

export interface ObjectTransformChangedPayload {
  sceneId?: string;
  epoch?: number;
  objectId: string;
  position: Vector3;
  rotation: Vector3;
  scale: Vector3;
}

export interface RendererErrorPayload {
  code: BridgeErrorCode;
  message: string;
  category?: string;
}

export interface CameraChangedPayload {
  sceneId?: string;
  epoch?: number;
  position: Vector3;
  target: Vector3;
  fov: number;
}

// ==========================================
// Message Union & Discriminators
// ==========================================

export type InitSceneMessage = BridgeEnvelope<InitScenePayload> & { type: typeof WIRE_TYPES.SCENE_INIT };
export type SyncStateMessage = BridgeEnvelope<SyncStatePayload> & { type: typeof WIRE_TYPES.STATE_SYNC };
export type UpdateBindingMessage = BridgeEnvelope<UpdateBindingPayload> & { type: typeof WIRE_TYPES.BINDING_UPDATE };
export type SelectionChangeMessage = BridgeEnvelope<SelectionChangePayload> & { type: typeof WIRE_TYPES.SELECTION_CHANGE };
export type CameraCommandMessage = BridgeEnvelope<CameraCommandPayload> & { type: typeof WIRE_TYPES.CAMERA_COMMAND };
export type SetInteractionModeMessage = BridgeEnvelope<SetInteractionModePayload> & { type: typeof WIRE_TYPES.SET_INTERACTION_MODE };
export type SetObjectTransformMessage = BridgeEnvelope<SetObjectTransformPayload> & { type: typeof WIRE_TYPES.SET_OBJECT_TRANSFORM };

export type DownlinkBridgeMessage =
  | InitSceneMessage
  | SyncStateMessage
  | UpdateBindingMessage
  | SelectionChangeMessage
  | CameraCommandMessage
  | SetInteractionModeMessage
  | SetObjectTransformMessage;

export type RendererReadyMessage = BridgeEnvelope<RendererReadyPayload> & { type: typeof WIRE_TYPES.RENDERER_READY };
export type CameraChangedMessage = BridgeEnvelope<CameraChangedPayload> & { type: typeof WIRE_TYPES.CAMERA_CHANGED };
export type ObjectClickedMessage = BridgeEnvelope<ObjectClickedPayload> & { type: typeof WIRE_TYPES.OBJECT_CLICKED };
export type ObjectHoveredMessage = BridgeEnvelope<ObjectHoveredPayload> & { type: typeof WIRE_TYPES.OBJECT_HOVERED };
export type ObjectTransformChangedMessage = BridgeEnvelope<ObjectTransformChangedPayload> & { type: typeof WIRE_TYPES.OBJECT_TRANSFORM_CHANGED };
export type RendererErrorMessage = BridgeEnvelope<RendererErrorPayload> & { type: typeof WIRE_TYPES.RENDERER_ERROR };

export type UplinkBridgeMessage =
  | RendererReadyMessage
  | CameraChangedMessage
  | ObjectClickedMessage
  | ObjectHoveredMessage
  | ObjectTransformChangedMessage
  | RendererErrorMessage;

export type BridgeMessage = DownlinkBridgeMessage | UplinkBridgeMessage;

// ==========================================
// Type Guards
// ==========================================

export function isInitSceneMessage(msg: BridgeEnvelope<unknown>): msg is InitSceneMessage {
  return msg.type === WIRE_TYPES.SCENE_INIT && typeof msg.payload === "object" && msg.payload !== null && "sceneDescriptor" in msg.payload;
}

export function isSyncStateMessage(msg: BridgeEnvelope<unknown>): msg is SyncStateMessage {
  return msg.type === WIRE_TYPES.STATE_SYNC && typeof msg.payload === "object" && msg.payload !== null && "snapshot" in msg.payload;
}

export function isSelectionChangeMessage(msg: BridgeEnvelope<unknown>): msg is SelectionChangeMessage {
  return msg.type === WIRE_TYPES.SELECTION_CHANGE && typeof msg.payload === "object" && msg.payload !== null;
}

export function isRendererReadyMessage(msg: BridgeEnvelope<unknown>): msg is RendererReadyMessage {
  return msg.type === WIRE_TYPES.RENDERER_READY && typeof msg.payload === "object" && msg.payload !== null && "protocolVersion" in msg.payload;
}

export function isRendererErrorMessage(msg: BridgeEnvelope<unknown>): msg is RendererErrorMessage {
  return msg.type === WIRE_TYPES.RENDERER_ERROR && typeof msg.payload === "object" && msg.payload !== null && "code" in msg.payload;
}

// ==========================================
// Uplink Message Factory Helpers
// ==========================================

export function createRendererReadyMessage(
  protocolVersion: number = CURRENT_BRIDGE_PROTOCOL_VERSION,
  rendererVersion: string = DEFAULT_RENDERER_VERSION,
): RendererReadyMessage {
  return {
    type: WIRE_TYPES.RENDERER_READY,
    version: CURRENT_BRIDGE_PROTOCOL_VERSION,
    timestamp: Date.now(),
    payload: {
      protocolVersion,
      rendererVersion,
    },
  };
}

export function createObjectClickedMessage(
  objectId: string,
  screenX: number,
  screenY: number,
  isCtrlPressed: boolean = false,
  isShiftPressed: boolean = false,
): ObjectClickedMessage {
  return {
    type: WIRE_TYPES.OBJECT_CLICKED,
    version: CURRENT_BRIDGE_PROTOCOL_VERSION,
    timestamp: Date.now(),
    payload: {
      objectId,
      screenX,
      screenY,
      isCtrlPressed,
      isShiftPressed,
    },
  };
}

export function createRendererErrorMessage(
  code: BridgeErrorCode,
  message: string,
  category?: string,
): RendererErrorMessage {
  return {
    type: WIRE_TYPES.RENDERER_ERROR,
    version: CURRENT_BRIDGE_PROTOCOL_VERSION,
    timestamp: Date.now(),
    payload: {
      code,
      message,
      ...(category ? { category } : {}),
    },
  };
}
