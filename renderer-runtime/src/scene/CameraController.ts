import * as THREE from "three";
import { OrbitControls } from "three/examples/jsm/controls/OrbitControls.js";
import type {
  CameraChangedPayload,
  CameraCommandPayload,
  SceneCameraDescriptor,
} from "../../../renderer-contract/scene-bridge-contract.ts";

export interface CameraControllerOptions {
  camera: THREE.PerspectiveCamera;
  domElement: HTMLElement;
  initialTarget?: { x: number; y: number; z: number };
  onCameraChanged?: (payload: CameraChangedPayload) => void;
}

/**
 * CameraController
 * Manages viewport navigation using OrbitControls with damping,
 * supports Camera commands from Host, and provides object focus capabilities.
 */
export class CameraController {
  private readonly camera: THREE.PerspectiveCamera;
  private readonly controls: OrbitControls;
  private isDisposed = false;

  constructor(options: CameraControllerOptions) {
    this.camera = options.camera;
    this.controls = new OrbitControls(this.camera, options.domElement);

    // Smooth camera inertia
    this.controls.enableDamping = true;
    this.controls.dampingFactor = 0.05;
    this.controls.maxPolarAngle = Math.PI / 2 - 0.02; // Prevent going below floor
    this.controls.minDistance = 1;
    this.controls.maxDistance = 50;

    const target = options.initialTarget ?? { x: 0, y: 1, z: 0 };
    this.controls.target.set(target.x, target.y, target.z);
    this.controls.update();

    if (options.onCameraChanged) {
      this.controls.addEventListener("end", () => {
        if (this.isDisposed) return;
        options.onCameraChanged?.({
          position: { x: this.camera.position.x, y: this.camera.position.y, z: this.camera.position.z },
          target: { x: this.controls.target.x, y: this.controls.target.y, z: this.controls.target.z },
          fov: this.camera.fov,
        });
      });
    }
  }

  /**
   * Called on every animation frame to update damping.
   */
  update(): void {
    if (this.isDisposed) return;
    this.controls.update();
  }

  /**
   * Applies camera descriptor from SCENE_INIT or CAMERA_COMMAND.
   */
  applyDescriptor(descriptor: SceneCameraDescriptor | CameraCommandPayload): void {
    if (this.isDisposed) return;

    this.camera.position.set(descriptor.position.x, descriptor.position.y, descriptor.position.z);
    this.controls.target.set(descriptor.target.x, descriptor.target.y, descriptor.target.z);

    if (descriptor.fov && descriptor.fov > 0) {
      this.camera.fov = descriptor.fov;
      this.camera.updateProjectionMatrix();
    }

    this.controls.update();
  }

  /**
   * Smoothly shifts the orbit focus target to the center of the specified 3D object.
   */
  focusOnObject(object: THREE.Object3D): void {
    if (this.isDisposed) return;

    const box = new THREE.Box3().setFromObject(object);
    const center = new THREE.Vector3();
    box.getCenter(center);

    this.controls.target.copy(center);
    this.controls.update();
  }

  getControls(): OrbitControls {
    return this.controls;
  }

  dispose(): void {
    if (this.isDisposed) return;
    this.isDisposed = true;
    this.controls.dispose();
  }
}
