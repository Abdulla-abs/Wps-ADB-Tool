import * as THREE from "three";
import { TransformControls } from "three/examples/jsm/controls/TransformControls.js";
import type { OrbitControls } from "three/examples/jsm/controls/OrbitControls.js";
import type {
  InteractionMode,
  ObjectTransformChangedPayload,
  Vector3,
} from "../../../renderer-contract/scene-bridge-contract.ts";

export interface TransformControllerOptions {
  camera: THREE.Camera;
  domElement: HTMLElement;
  scene: THREE.Scene;
  orbitControls: OrbitControls;
  onTransformChanged?: (payload: ObjectTransformChangedPayload) => void;
}

/**
 * TransformController
 * Manages 3D transformation Gizmos (translate, rotate, scale) for imported assets in EDITING mode.
 * Ensures OrbitControls are disabled while dragging, and isolates operations strictly to assets.
 */
export class TransformController {
  private readonly camera: THREE.Camera;
  private readonly domElement: HTMLElement;
  private readonly scene: THREE.Scene;
  private readonly orbitControls: OrbitControls;
  private readonly onTransformChanged?: (payload: ObjectTransformChangedPayload) => void;

  private readonly controls: TransformControls;
  private readonly helper: THREE.Object3D;

  private interactionMode: InteractionMode = "VIEW";
  private attachedObject: THREE.Object3D | null = null;
  private attachedObjectId: string | null = null;
  private isDisposed = false;

  constructor(options: TransformControllerOptions) {
    this.camera = options.camera;
    this.domElement = options.domElement;
    this.scene = options.scene;
    this.orbitControls = options.orbitControls;
    this.onTransformChanged = options.onTransformChanged;

    this.controls = new TransformControls(this.camera, this.domElement);
    this.helper = this.controls.getHelper();

    this.controls.addEventListener("dragging-changed", this.handleDraggingChanged);
    this.controls.addEventListener("objectChange", this.handleObjectChange);
  }

  private handleDraggingChanged = (event: { value: boolean }): void => {
    if (this.isDisposed) return;
    this.orbitControls.enabled = !event.value;
  };

  private handleObjectChange = (): void => {
    if (this.isDisposed || !this.attachedObject || !this.attachedObjectId) return;

    const pos = this.attachedObject.position;
    const rot = this.attachedObject.rotation;
    const scl = this.attachedObject.scale;

    this.onTransformChanged?.({
      objectId: this.attachedObjectId,
      position: { x: pos.x, y: pos.y, z: pos.z },
      rotation: { x: rot.x, y: rot.y, z: rot.z },
      scale: { x: scl.x, y: scl.y, z: scl.z },
    });
  };

  /**
   * Sets current interaction mode (VIEW, BINDING, EDITING).
   */
  setInteractionMode(mode: InteractionMode): void {
    if (this.isDisposed) return;
    this.interactionMode = mode;

    if (mode !== "EDITING") {
      this.detach();
    } else if (this.attachedObject && this.attachedObjectId) {
      this.attach(this.attachedObjectId, this.attachedObject);
    }
  }

  getInteractionMode(): InteractionMode {
    return this.interactionMode;
  }

  /**
   * Updates current selection and attaches/detaches gizmo based on interaction mode and object type.
   */
  updateSelection(selectedObjectId: string | null, targetObject: THREE.Object3D | null): void {
    if (this.isDisposed) return;

    // Host echoes transform changes through STATE_SYNC while a gizmo is being
    // dragged. Do not re-attach the same object on each echo: re-attaching can
    // reset TransformControls' active drag state and make the gizmo appear to
    // move independently from the model.
    if (
      selectedObjectId === this.attachedObjectId &&
      targetObject === this.attachedObject &&
      targetObject?.userData?.objectType === "asset" &&
      this.interactionMode === "EDITING" &&
      this.controls.object === targetObject
    ) {
      return;
    }

    if (!selectedObjectId || !targetObject || targetObject.userData?.objectType !== "asset") {
      this.detach();
      this.attachedObjectId = null;
      this.attachedObject = null;
      return;
    }

    this.attachedObjectId = selectedObjectId;
    this.attachedObject = targetObject;

    if (this.interactionMode === "EDITING") {
      this.attach(selectedObjectId, targetObject);
    } else {
      this.detach();
    }
  }

  /**
   * Attaches gizmo to a specific object if it is an asset and we are in EDITING mode.
   */
  attach(objectId: string, object: THREE.Object3D): void {
    if (this.isDisposed) return;

    // Strict boundary: Only imported assets are allowed to have transform gizmos
    if (object.userData?.objectType !== "asset") {
      this.detach();
      this.attachedObjectId = null;
      this.attachedObject = null;
      return;
    }

    this.attachedObjectId = objectId;
    this.attachedObject = object;

    if (this.interactionMode === "EDITING") {
      this.controls.attach(object);
      if (!this.helper.parent) {
        this.scene.add(this.helper);
      }
    }
  }

  /**
   * Detaches gizmo and removes helper from scene.
   */
  detach(): void {
    if (this.isDisposed) return;
    this.controls.detach();
    if (this.helper.parent) {
      this.scene.remove(this.helper);
    }
  }

  /**
   * Updates object transform from Host (SET_OBJECT_TRANSFORM downlink).
   */
  applyTransform(objectId: string, object: THREE.Object3D, position: Vector3, rotation: Vector3, scale: Vector3): void {
    if (this.isDisposed) return;

    object.position.set(position.x, position.y, position.z);
    object.rotation.set(rotation.x, rotation.y, rotation.z);
    object.scale.set(scale.x, scale.y, scale.z);
    object.updateMatrix();
    object.updateMatrixWorld(true);
  }

  /**
   * Sets gizmo transform mode: "translate" | "rotate" | "scale".
   */
  setMode(mode: "translate" | "rotate" | "scale"): void {
    if (this.isDisposed) return;
    this.controls.setMode(mode);
  }

  getMode(): string {
    return this.controls.getMode();
  }

  getControls(): TransformControls {
    return this.controls;
  }

  getHelper(): THREE.Object3D {
    return this.helper;
  }

  getAttachedObject(): THREE.Object3D | null {
    return (this.controls.object as THREE.Object3D) ?? null;
  }

  getAttachedObjectId(): string | null {
    return this.controls.object ? this.attachedObjectId : null;
  }

  /**
   * Complete, safe disposal lifecycle.
   */
  dispose(): void {
    if (this.isDisposed) return;

    this.controls.removeEventListener("dragging-changed", this.handleDraggingChanged);
    this.controls.removeEventListener("objectChange", this.handleObjectChange);

    this.detach();

    // Ensure orbitControls are re-enabled if disposal happens during an active drag
    this.orbitControls.enabled = true;

    this.controls.dispose();

    this.attachedObject = null;
    this.attachedObjectId = null;
    this.isDisposed = true;
  }
}
