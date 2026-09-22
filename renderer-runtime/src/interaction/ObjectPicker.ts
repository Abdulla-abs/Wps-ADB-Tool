import * as THREE from "three";
import type { ObjectClickedPayload } from "../../../renderer-contract/scene-bridge-contract.ts";

export interface ObjectPickerOptions {
  domElement: HTMLElement;
  camera: THREE.Camera;
  getPickableObjects: () => THREE.Object3D[];
  onClick: (payload: ObjectClickedPayload) => void;
  onHover?: (objectId: string | null) => void;
}

/**
 * ObjectPicker
 * Handles mouse/pointer interactions, distinguishes clicks from orbit drags,
 * performs raycasting against pickable objects, and dispatches click/hover events.
 */
export class ObjectPicker {
  private readonly domElement: HTMLElement;
  private readonly camera: THREE.Camera;
  private readonly getPickableObjects: () => THREE.Object3D[];
  private readonly onClick: (payload: ObjectClickedPayload) => void;
  private readonly onHover?: (objectId: string | null) => void;

  private readonly raycaster = new THREE.Raycaster();
  private readonly mouse = new THREE.Vector2();

  private pointerDownPos = { x: 0, y: 0 };
  private isPointerDown = false;
  private currentHoveredObjectId: string | null = null;
  private isDisposed = false;

  constructor(options: ObjectPickerOptions) {
    this.domElement = options.domElement;
    this.camera = options.camera;
    this.getPickableObjects = options.getPickableObjects;
    this.onClick = options.onClick;
    this.onHover = options.onHover;

    this.domElement.addEventListener("pointerdown", this.handlePointerDown);
    this.domElement.addEventListener("pointermove", this.handlePointerMove);
    this.domElement.addEventListener("pointerup", this.handlePointerUp);
  }

  private handlePointerDown = (e: PointerEvent): void => {
    if (this.isDisposed) return;
    this.isPointerDown = true;
    this.pointerDownPos = { x: e.clientX, y: e.clientY };
  };

  private handlePointerMove = (e: PointerEvent): void => {
    if (this.isDisposed) return;

    // Only handle hover when pointer is not dragging
    if (!this.isPointerDown) {
      const hit = this.raycast(e.clientX, e.clientY);
      const hoveredId = hit ? (hit.object.userData?.objectId ?? null) : null;

      if (hoveredId !== this.currentHoveredObjectId) {
        this.currentHoveredObjectId = hoveredId;
        this.domElement.style.cursor = hoveredId ? "pointer" : "default";
        this.onHover?.(hoveredId);
      }
    }
  };

  private handlePointerUp = (e: PointerEvent): void => {
    if (this.isDisposed || !this.isPointerDown) return;
    this.isPointerDown = false;

    // Distinguish click from drag (drag threshold: 5px)
    const moveDist = Math.hypot(e.clientX - this.pointerDownPos.x, e.clientY - this.pointerDownPos.y);
    if (moveDist > 5) {
      return; // Was an orbit or pan drag, not an object selection click
    }

    const hit = this.raycast(e.clientX, e.clientY);
    if (hit) {
      const objectId = hit.object.userData?.objectId;
      if (objectId) {
        this.onClick({
          objectId,
          screenX: e.clientX,
          screenY: e.clientY,
          isCtrlPressed: e.ctrlKey || e.metaKey,
          isShiftPressed: e.shiftKey,
        });
      }
    }
  };

  private raycast(clientX: number, clientY: number): THREE.Intersection | null {
    const rect = this.domElement.getBoundingClientRect();
    if (rect.width <= 0 || rect.height <= 0) return null;

    this.mouse.x = ((clientX - rect.left) / rect.width) * 2 - 1;
    this.mouse.y = -((clientY - rect.top) / rect.height) * 2 + 1;

    this.raycaster.setFromCamera(this.mouse, this.camera);
    const pickables = this.getPickableObjects();
    if (pickables.length === 0) return null;

    const intersects = this.raycaster.intersectObjects(pickables, true);
    if (intersects.length > 0) {
      // Find the first hit that has an objectId in its userData hierarchy
      for (const hit of intersects) {
        let curr: THREE.Object3D | null = hit.object;
        while (curr) {
          if (curr.userData?.objectId) {
            return { ...hit, object: curr };
          }
          curr = curr.parent;
        }
      }
      return intersects[0];
    }
    return null;
  }

  dispose(): void {
    if (this.isDisposed) return;
    this.isDisposed = true;

    this.domElement.removeEventListener("pointerdown", this.handlePointerDown);
    this.domElement.removeEventListener("pointermove", this.handlePointerMove);
    this.domElement.removeEventListener("pointerup", this.handlePointerUp);
    this.domElement.style.cursor = "default";
  }
}
