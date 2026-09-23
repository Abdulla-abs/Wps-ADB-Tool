import {
  CURRENT_BRIDGE_PROTOCOL_VERSION,
  WIRE_TYPES,
} from "../../../renderer-contract/scene-bridge-contract.ts";
import type {
  BridgeEnvelope,
  CameraCommandPayload,
  SetObjectTransformPayload,
} from "../../../renderer-contract/scene-bridge-contract.ts";
import {
  isBridgeEnvelope,
  isCameraCommandPayload,
  isRendererErrorPayload,
  isSceneInitPayload,
  isSelectionChangePayload,
  isSetInteractionModePayload,
  isSetObjectTransformPayload,
  isSyncStatePayload,
} from "../protocol/validators.ts";
import { RendererStateStore } from "../store/RendererStateStore.ts";
import { ErrorBoundary } from "./ErrorBoundary.ts";

export interface DispatcherHooks {
  onUnknownMessage?: (envelope: BridgeEnvelope<unknown>) => void;
  onReservedMessage?: (envelope: BridgeEnvelope<unknown>) => void;
  onCameraCommand?: (payload: CameraCommandPayload) => void;
  onSetObjectTransform?: (payload: SetObjectTransformPayload) => void;
}

/**
 * MessageDispatcher
 * Validates envelope schema, checks version compatibility, and routes typed payloads
 * to the store while isolating failures via ErrorBoundary.
 */
export class MessageDispatcher {
  private readonly store: RendererStateStore;
  private readonly errorBoundary: ErrorBoundary;
  private readonly hooks: DispatcherHooks;

  constructor(
    store: RendererStateStore,
    errorBoundary: ErrorBoundary,
    hooks: DispatcherHooks = {},
  ) {
    this.store = store;
    this.errorBoundary = errorBoundary;
    this.hooks = hooks;
  }

  dispatch(raw: unknown): void {
    // 1. Envelope validation
    if (!isBridgeEnvelope(raw)) {
      this.errorBoundary.handleInvalidEnvelope(raw);
      return;
    }

    // 2. Version negotiation
    if (raw.version !== CURRENT_BRIDGE_PROTOCOL_VERSION) {
      this.errorBoundary.handleVersionMismatch(raw.version, CURRENT_BRIDGE_PROTOCOL_VERSION);
      return;
    }

    const { type, payload } = raw;

    try {
      switch (type) {
        case WIRE_TYPES.SCENE_INIT: {
          if (!isSceneInitPayload(payload)) {
            this.errorBoundary.handleInvalidPayload(type, "Missing or invalid sceneDescriptor");
            return;
          }
          this.store.initScene(payload.sceneDescriptor, payload.epoch ?? 0);
          break;
        }

        case WIRE_TYPES.STATE_SYNC: {
          if (!isSyncStatePayload(payload)) {
            this.errorBoundary.handleInvalidPayload(type, "Missing or invalid snapshot");
            return;
          }
          this.store.syncState(payload.snapshot);
          break;
        }

        case WIRE_TYPES.SELECTION_CHANGE: {
          if (!isSelectionChangePayload(payload)) {
            this.errorBoundary.handleInvalidPayload(type, "Invalid selectedObjectId");
            return;
          }
          this.store.updateSelection(payload.selectedObjectId);
          break;
        }

        case WIRE_TYPES.RENDERER_ERROR: {
          if (isRendererErrorPayload(payload)) {
            this.store.recordError(payload.code, payload.message);
          } else {
            this.store.recordError("UNKNOWN_ERROR", "Received malformed error message");
          }
          break;
        }

        case WIRE_TYPES.SET_INTERACTION_MODE: {
          if (!isSetInteractionModePayload(payload)) {
            this.errorBoundary.handleInvalidPayload(type, "Missing or invalid interaction mode");
            return;
          }
          this.store.setInteractionMode(payload.mode);
          break;
        }

        case WIRE_TYPES.SET_OBJECT_TRANSFORM: {
          if (!isSetObjectTransformPayload(payload)) {
            this.errorBoundary.handleInvalidPayload(type, "Missing or invalid object transform parameters");
            return;
          }
          if (this.hooks.onSetObjectTransform) {
            this.hooks.onSetObjectTransform(payload);
          } else {
            this.hooks.onReservedMessage?.(raw);
          }
          break;
        }

        case WIRE_TYPES.CAMERA_COMMAND: {
          if (!isCameraCommandPayload(payload)) {
            this.errorBoundary.handleInvalidPayload(type, "Missing or invalid camera command parameters");
            return;
          }
          if (this.hooks.onCameraCommand) {
            this.hooks.onCameraCommand(payload);
          } else {
            this.hooks.onReservedMessage?.(raw);
          }
          break;
        }

        case WIRE_TYPES.BINDING_UPDATE:
        case WIRE_TYPES.OBJECT_HOVERED:
        case WIRE_TYPES.OBJECT_TRANSFORM_CHANGED: {
          this.hooks.onReservedMessage?.(raw);
          break;
        }

        default: {
          this.hooks.onUnknownMessage?.(raw);
          break;
        }
      }
    } catch (err) {
      this.errorBoundary.reportError(
        "INTERNAL_ERROR",
        `Exception occurred in dispatcher for message type '${type}': ${err instanceof Error ? err.message : String(err)}`,
        "DISPATCHER",
        type,
      );
    }
  }
}
