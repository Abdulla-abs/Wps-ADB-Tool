import type {
  CameraCommandPayload,
  DeviceVisualDescriptor,
  InitScenePayload,
  InteractionMode,
  SelectionChangePayload,
  SetObjectTransformPayload,
  SyncStatePayload,
} from "../../../renderer-contract/scene-bridge-contract.ts";
import { CameraController } from "../scene/CameraController.ts";
import { DeviceSceneRenderer } from "../scene/DeviceSceneRenderer.ts";
import { SceneLoader } from "../scene/SceneLoader.ts";
import { ObjectPicker } from "../interaction/ObjectPicker.ts";
import { TransformController } from "../interaction/TransformController.ts";
import { DeviceVisualState } from "../visual/DeviceVisualState.ts";
import type { RendererRuntime } from "../RendererRuntime.ts";

export interface RendererSceneBridgeOptions {
  runtime: RendererRuntime;
  sceneRenderer: DeviceSceneRenderer;
  baseUrlPrefix?: string;
}

/**
 * RendererSceneBridge
 * Central coordination bridge wiring Three.js rendering components (SceneLoader,
 * CameraController, ObjectPicker, DeviceVisualState) to the RendererRuntime
 * lifecycle and message dispatching.
 */
export class RendererSceneBridge {
  private readonly runtime: RendererRuntime;
  private readonly sceneRenderer: DeviceSceneRenderer;
  private readonly sceneLoader: SceneLoader;
  private readonly cameraController: CameraController;
  private readonly objectPicker: ObjectPicker;
  private readonly visualState: DeviceVisualState;
  private readonly transformController: TransformController;

  private isDisposed = false;
  private sceneLoadSequence = 0;

  constructor(options: RendererSceneBridgeOptions) {
    this.runtime = options.runtime;
    this.sceneRenderer = options.sceneRenderer;

    // 1. Initialize SceneLoader
    this.sceneLoader = new SceneLoader({
      contentGroup: this.sceneRenderer.getContentGroup(),
      baseUrlPrefix: options.baseUrlPrefix,
      onError: (err, category) => {
        this.runtime.getErrorBoundary().reportError(
          "INTERNAL_ERROR",
          err.message,
          category,
        );
      },
    });

    // 2. Initialize CameraController
    this.cameraController = new CameraController({
      camera: this.sceneRenderer.getCamera(),
      domElement: this.sceneRenderer.getCanvas(),
      onCameraChanged: (payload) => {
        const state = this.runtime.getStore().getState();
        const activeSceneId = state.activeScene?.id;
        this.runtime.sendCameraChanged({
          ...payload,
          sceneId: activeSceneId,
          epoch: state.activeEpoch,
        });
      },
    });

    // Hook camera damping update into render loop
    this.sceneRenderer.addOnBeforeRender(() => {
      this.cameraController.update();
      this.visualState.updateSelectionHighlights();
    });

    // 3. Initialize Visual State
    this.visualState = new DeviceVisualState();

    // 4. Initialize ObjectPicker
    this.objectPicker = new ObjectPicker({
      domElement: this.sceneRenderer.getCanvas(),
      camera: this.sceneRenderer.getCamera(),
      getPickableObjects: () => this.sceneLoader.getPickableObjects(),
      onClick: (payload) => {
        // User clicked an object in 3D viewport -> send OBJECT_CLICKED uplink to Host
        this.runtime.sendObjectClicked(
          payload.objectId,
          payload.screenX,
          payload.screenY,
          payload.isCtrlPressed,
          payload.isShiftPressed,
        );
      },
    });

    // 5. Initialize TransformController
    this.transformController = new TransformController({
      camera: this.sceneRenderer.getCamera(),
      domElement: this.sceneRenderer.getCanvas(),
      scene: this.sceneRenderer.getScene(),
      orbitControls: this.cameraController.getControls(),
      onTransformChanged: (payload) => {
        const state = this.runtime.getStore().getState();
        const activeSceneId = state.activeScene?.id;
        this.runtime.sendObjectTransformChanged({
          ...payload,
          sceneId: activeSceneId,
          epoch: state.activeEpoch,
        });
      },
    });

    // 6. Connect store subscriptions for reactive state mirrors
    this.bindStoreListeners();
  }

  private bindStoreListeners(): void {
    let lastSceneDescriptor: InitScenePayload["sceneDescriptor"] | null = null;
    let lastSelectionId: string | null = null;
    let lastInteractionMode: InteractionMode = "VIEW";

    this.runtime.getStore().subscribe(async (state) => {
      if (this.isDisposed) return;

      // React to scene initialization
      if (state.activeScene && state.activeScene !== lastSceneDescriptor) {
        lastSceneDescriptor = state.activeScene;
        await this.handleSceneInit({ sceneDescriptor: state.activeScene });
      }

      // React to device status updates
      if (state.devices && state.devices.length > 0) {
        this.visualState.applyVisualDescriptors(
          state.devices,
          this.sceneLoader.getObjectsById(),
        );
      }

      // React to interaction mode change
      if (state.interactionMode !== lastInteractionMode) {
        lastInteractionMode = state.interactionMode;
        this.transformController.setInteractionMode(state.interactionMode);
      }

      // React to selection change or mode change
      if (state.selectedObjectId !== lastSelectionId) {
        lastSelectionId = state.selectedObjectId;
        this.handleSelectionChange({
          selectedObjectId: state.selectedObjectId,
          focusCamera: false,
        });
      }

      const targetObj = state.selectedObjectId
        ? this.sceneLoader.getObjectsById().get(state.selectedObjectId) ?? null
        : null;
      this.transformController.updateSelection(state.selectedObjectId, targetObj);
    });
  }

  /**
   * Handles SCENE_INIT downlink message.
   */
  async handleSceneInit(payload: InitScenePayload): Promise<void> {
    if (this.isDisposed) return;
    const { sceneDescriptor } = payload;
    const loadToken = ++this.sceneLoadSequence;

    // 1. Clear previous visual state
    this.visualState.clear(this.sceneRenderer.getScene());

    // 2. Load GLB environment and assets
    await this.sceneLoader.loadScene(sceneDescriptor);
    if (this.isDisposed || loadToken !== this.sceneLoadSequence) return;

    // 3. Apply camera descriptor
    if (sceneDescriptor.camera) {
      this.cameraController.applyDescriptor(sceneDescriptor.camera);
    }

    // 4. Concurrency resolution: Replay current store device descriptors and selection
    // in case STATE_SYNC or SELECTION_CHANGE arrived while GLB was loading asynchronously.
    const currentState = this.runtime.getStore().getState();
    if (currentState.devices && currentState.devices.length > 0) {
      this.visualState.applyVisualDescriptors(
        currentState.devices,
        this.sceneLoader.getObjectsById(),
      );
    }
    if (currentState.selectedObjectId) {
      this.visualState.updateSelection(
        currentState.selectedObjectId,
        this.sceneLoader.getObjectsById(),
        this.sceneRenderer.getScene(),
      );
    }

    this.transformController.setInteractionMode(currentState.interactionMode);
    const targetObj = currentState.selectedObjectId
      ? this.sceneLoader.getObjectsById().get(currentState.selectedObjectId) ?? null
      : null;
    this.transformController.updateSelection(currentState.selectedObjectId, targetObj);
  }

  /**
   * Handles STATE_SYNC downlink message.
   */
  handleStateSync(payload: SyncStatePayload): void {
    if (this.isDisposed) return;
    const { snapshot } = payload;

    // Apply incremental device status updates
    this.visualState.applyVisualDescriptors(
      snapshot.devices,
      this.sceneLoader.getObjectsById(),
    );

    // Update selection highlight (host instruction, does NOT emit click)
    this.visualState.updateSelection(
      snapshot.selectedObjectId,
      this.sceneLoader.getObjectsById(),
      this.sceneRenderer.getScene(),
    );
  }

  /**
   * Handles SELECTION_CHANGE downlink message.
   */
  handleSelectionChange(payload: SelectionChangePayload): void {
    if (this.isDisposed) return;

    // Pure host instruction: updates visual highlight only, NEVER sends OBJECT_CLICKED back
    this.visualState.updateSelection(
      payload.selectedObjectId,
      this.sceneLoader.getObjectsById(),
      this.sceneRenderer.getScene(),
    );

    if (payload.focusCamera && payload.selectedObjectId) {
      const targetObj = this.sceneLoader.getObjectsById().get(payload.selectedObjectId);
      if (targetObj) {
        this.cameraController.focusOnObject(targetObj);
      }
    }
  }

  /**
   * Handles CAMERA_COMMAND downlink message.
   */
  handleCameraCommand(payload: CameraCommandPayload): void {
    if (this.isDisposed) return;
    this.cameraController.applyDescriptor(payload);
  }

  /**
   * Handles SET_OBJECT_TRANSFORM downlink message.
   */
  handleSetObjectTransform(payload: SetObjectTransformPayload): void {
    if (this.isDisposed) return;
    const targetObj = this.sceneLoader.getObjectsById().get(payload.objectId);
    if (targetObj && targetObj.userData?.objectType === "asset") {
      this.transformController.applyTransform(
        payload.objectId,
        targetObj,
        payload.position,
        payload.rotation,
        payload.scale,
      );
    }
  }

  getSceneLoader(): SceneLoader {
    return this.sceneLoader;
  }

  getCameraController(): CameraController {
    return this.cameraController;
  }

  getObjectPicker(): ObjectPicker {
    return this.objectPicker;
  }

  getVisualState(): DeviceVisualState {
    return this.visualState;
  }

  getTransformController(): TransformController {
    return this.transformController;
  }

  dispose(): void {
    if (this.isDisposed) return;
    this.isDisposed = true;

    this.transformController.dispose();
    this.objectPicker.dispose();
    this.cameraController.dispose();
    this.visualState.clear(this.sceneRenderer.getScene());
    this.sceneLoader.clear();
  }
}
