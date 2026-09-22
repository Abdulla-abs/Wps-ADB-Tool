import * as THREE from "three";
import type {
  CameraCommandPayload,
  DeviceVisualDescriptor,
  InitScenePayload,
  SelectionChangePayload,
  SyncStatePayload,
} from "../../../renderer-contract/scene-bridge-contract.ts";
import { CameraController } from "../scene/CameraController.ts";
import { DeviceSceneRenderer } from "../scene/DeviceSceneRenderer.ts";
import { SceneLoader } from "../scene/SceneLoader.ts";
import { ObjectPicker } from "../interaction/ObjectPicker.ts";
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
        this.runtime.sendCameraChanged(payload);
      },
    });

    // Hook camera damping update into render loop
    this.sceneRenderer.addOnBeforeRender(() => {
      this.cameraController.update();
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

    // 5. Connect store subscriptions for reactive state mirrors
    this.bindStoreListeners();
  }

  private bindStoreListeners(): void {
    let lastSceneId: string | null = null;
    let lastSelectionId: string | null = null;

    this.runtime.getStore().subscribe(async (state) => {
      if (this.isDisposed) return;

      // React to scene initialization
      if (state.activeScene && state.activeScene.id !== lastSceneId) {
        lastSceneId = state.activeScene.id;
        await this.handleSceneInit({ sceneDescriptor: state.activeScene });
      }

      // React to device status updates
      if (state.devices && state.devices.length > 0) {
        this.visualState.applyVisualDescriptors(
          state.devices,
          this.sceneLoader.getObjectsById(),
        );
      }

      // React to selection change
      if (state.selectedObjectId !== lastSelectionId) {
        lastSelectionId = state.selectedObjectId;
        this.handleSelectionChange({
          selectedObjectId: state.selectedObjectId,
          focusCamera: false,
        });
      }
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

  dispose(): void {
    if (this.isDisposed) return;
    this.isDisposed = true;

    this.objectPicker.dispose();
    this.cameraController.dispose();
    this.visualState.clear(this.sceneRenderer.getScene());
    this.sceneLoader.clear();
  }
}
