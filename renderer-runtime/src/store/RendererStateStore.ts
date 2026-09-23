import type {
  DeviceVisualDescriptor,
  InteractionMode,
  SceneDescriptor,
  SceneVisualSnapshot,
} from "../../../renderer-contract/scene-bridge-contract.ts";

export type BridgeLifecycleState = "DISCONNECTED" | "CONNECTING" | "CONNECTED" | "READY" | "ERROR";

export interface RuntimeErrorRecord {
  code: string;
  message: string;
  timestamp: number;
}

/**
 * Bridge Runtime State Snapshot
 * Acts as the in-memory mirror of the Kotlin Host state and bridge connection lifecycle.
 */
export interface RendererRuntimeState {
  connectionState: BridgeLifecycleState;
  activeScene: SceneDescriptor | null;
  activeEpoch: number;
  selectedObjectId: string | null;
  interactionMode: InteractionMode;
  devices: DeviceVisualDescriptor[];
  lastError: RuntimeErrorRecord | null;
}

export type StateListener = (state: RendererRuntimeState) => void;

/**
 * Bridge Runtime State Store
 * Holds pure state mirroring without any rendering or DOM logic.
 */
export class RendererStateStore {
  private connectionState: BridgeLifecycleState = "DISCONNECTED";
  private activeScene: SceneDescriptor | null = null;
  private activeEpoch: number = 0;
  private selectedObjectId: string | null = null;
  private interactionMode: InteractionMode = "VIEW";
  private deviceMap = new Map<string, DeviceVisualDescriptor>();
  private lastError: RuntimeErrorRecord | null = null;

  private listeners = new Set<StateListener>();

  /**
   * Returns an immutable snapshot of current bridge runtime state.
   */
  getState(): RendererRuntimeState {
    return {
      connectionState: this.connectionState,
      activeScene: this.activeScene,
      activeEpoch: this.activeEpoch,
      selectedObjectId: this.selectedObjectId,
      interactionMode: this.interactionMode,
      devices: Array.from(this.deviceMap.values()),
      lastError: this.lastError,
    };
  }

  /**
   * Fast O(1) indexed lookup for device by objectId.
   */
  getDevice(objectId: string): DeviceVisualDescriptor | undefined {
    return this.deviceMap.get(objectId);
  }

  /**
   * Returns read-only map view of devices indexed by objectId.
   */
  getDeviceMap(): ReadonlyMap<string, DeviceVisualDescriptor> {
    return this.deviceMap;
  }

  /**
   * Subscribes a listener to state changes.
   * Returns an unsubscribe function.
   */
  subscribe(listener: StateListener): () => void {
    this.listeners.add(listener);
    return () => {
      this.listeners.delete(listener);
    };
  }

  setConnectionState(state: BridgeLifecycleState): void {
    if (this.connectionState === state) return;
    this.connectionState = state;
    this.notify();
  }

  initScene(descriptor: SceneDescriptor, epoch = 0): void {
    this.activeScene = descriptor;
    this.activeEpoch = epoch;
    // Clearing previous bindings and selection upon scene initialization
    this.deviceMap.clear();
    this.selectedObjectId = null;
    this.notify();
  }

  syncState(snapshot: SceneVisualSnapshot): void {
    this.deviceMap.clear();
    for (const dev of snapshot.devices) {
      this.deviceMap.set(dev.objectId, dev);
    }
    this.selectedObjectId = snapshot.selectedObjectId ?? null;
    this.notify();
  }

  updateSelection(selectedObjectId: string | null): void {
    if (this.selectedObjectId === selectedObjectId) return;
    this.selectedObjectId = selectedObjectId;
    this.notify();
  }

  setInteractionMode(mode: InteractionMode): void {
    if (this.interactionMode === mode) return;
    this.interactionMode = mode;
    this.notify();
  }

  recordError(code: string, message: string): void {
    this.lastError = {
      code,
      message,
      timestamp: Date.now(),
    };
    this.notify();
  }

  reset(): void {
    this.connectionState = "DISCONNECTED";
    this.activeScene = null;
    this.selectedObjectId = null;
    this.interactionMode = "VIEW";
    this.deviceMap.clear();
    this.lastError = null;
    this.notify();
  }

  private notify(): void {
    const snapshot = this.getState();
    for (const listener of this.listeners) {
      try {
        listener(snapshot);
      } catch (err) {
        console.error("RendererStateStore: Listener error", err);
      }
    }
  }
}
