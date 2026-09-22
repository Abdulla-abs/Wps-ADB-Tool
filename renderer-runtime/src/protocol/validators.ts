import type {
  BridgeEnvelope,
  InitScenePayload,
  SyncStatePayload,
  SelectionChangePayload,
  RendererReadyPayload,
  RendererErrorPayload,
  CameraCommandPayload,
  Vector3,
} from "../../../renderer-contract/scene-bridge-contract.ts";

export function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

export function isBridgeEnvelope(value: unknown): value is BridgeEnvelope<Record<string, unknown>> {
  if (!isObject(value)) return false;
  return (
    typeof value.type === "string" &&
    value.type.trim().length > 0 &&
    typeof value.version === "number" &&
    typeof value.timestamp === "number" &&
    isObject(value.payload)
  );
}

export function isSceneInitPayload(payload: unknown): payload is InitScenePayload {
  if (!isObject(payload)) return false;
  const desc = payload.sceneDescriptor;
  if (!isObject(desc)) return false;
  return (
    typeof desc.id === "string" &&
    typeof desc.name === "string" &&
    Array.isArray(desc.assets) &&
    Array.isArray(desc.bindableObjectIds)
  );
}

export function isSyncStatePayload(payload: unknown): payload is SyncStatePayload {
  if (!isObject(payload)) return false;
  const snap = payload.snapshot;
  if (!isObject(snap)) return false;
  return typeof snap.sceneId === "string" && Array.isArray(snap.devices);
}

export function isSelectionChangePayload(payload: unknown): payload is SelectionChangePayload {
  if (!isObject(payload)) return false;
  const hasSelectedId = "selectedObjectId" in payload && (typeof payload.selectedObjectId === "string" || payload.selectedObjectId === null);
  const hasFocusCamera = "focusCamera" in payload ? typeof payload.focusCamera === "boolean" : true;
  return hasSelectedId && hasFocusCamera;
}

export function isRendererReadyPayload(payload: unknown): payload is RendererReadyPayload {
  if (!isObject(payload)) return false;
  return typeof payload.protocolVersion === "number" && typeof payload.rendererVersion === "string";
}

export function isRendererErrorPayload(payload: unknown): payload is RendererErrorPayload {
  if (!isObject(payload)) return false;
  return typeof payload.code === "string" && typeof payload.message === "string";
}

export function isVector3(value: unknown): value is Vector3 {
  return (
    isObject(value) &&
    typeof value.x === "number" &&
    typeof value.y === "number" &&
    typeof value.z === "number"
  );
}

export function isCameraCommandPayload(payload: unknown): payload is CameraCommandPayload {
  if (!isObject(payload)) return false;
  return (
    isVector3(payload.position) &&
    isVector3(payload.target) &&
    (payload.fov === undefined || typeof payload.fov === "number")
  );
}
