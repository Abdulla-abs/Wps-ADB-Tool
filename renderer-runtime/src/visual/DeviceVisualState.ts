import * as THREE from "three";
import type { DeviceVisualDescriptor } from "../../../renderer-contract/scene-bridge-contract.ts";

interface OriginalMaterialData {
  color: THREE.Color;
  emissive: THREE.Color;
  emissiveIntensity: number;
  opacity: number;
  transparent: boolean;
}

/**
 * DeviceVisualState
 * Manages incremental material visual state updates (ONLINE, OFFLINE, UNBOUND,
 * status colors, emissive effects, and selection highlight) without rebuilding meshes.
 */
export class DeviceVisualState {
  private readonly originalMaterials = new Map<THREE.Mesh, OriginalMaterialData>();
  private selectedObjectId: string | null = null;
  private selectionHighlights = new Map<string, THREE.BoxHelper>();

  /**
   * Applies a batch of device visual snapshots to the corresponding 3D objects.
   */
  applyVisualDescriptors(
    descriptors: DeviceVisualDescriptor[],
    objectsById: Map<string, THREE.Object3D>,
  ): void {
    for (const desc of descriptors) {
      const obj = objectsById.get(desc.objectId);
      if (!obj) continue;

      this.updateObjectVisual(obj, desc);
    }
  }

  /**
   * Updates visual state for a single object according to its descriptor.
   */
  updateObjectVisual(object: THREE.Object3D, descriptor: DeviceVisualDescriptor): void {
    const statusColor = new THREE.Color(descriptor.visual.statusColorHex);
    const isEmissive = descriptor.visual.isEmissive;
    const emissiveIntensity = descriptor.visual.emissiveIntensity ?? 0.5;
    const isDimmed = descriptor.visual.isDimmed;

    object.traverse((child) => {
      if ((child as THREE.Mesh).isMesh) {
        const mesh = child as THREE.Mesh;
        const mat = mesh.material;

        if (Array.isArray(mat)) {
          mat.forEach((m) => this.applyToMaterial(mesh, m, statusColor, isEmissive, emissiveIntensity, isDimmed));
        } else if (mat) {
          this.applyToMaterial(mesh, mat, statusColor, isEmissive, emissiveIntensity, isDimmed);
        }
      }
    });
  }

  private applyToMaterial(
    mesh: THREE.Mesh,
    mat: THREE.Material,
    statusColor: THREE.Color,
    isEmissive: boolean,
    emissiveIntensity: number,
    isDimmed: boolean,
  ): void {
    // Cache original material values if not already cached
    if (!this.originalMaterials.has(mesh)) {
      const stdMat = mat as THREE.MeshStandardMaterial;
      this.originalMaterials.set(mesh, {
        color: stdMat.color ? stdMat.color.clone() : new THREE.Color(0xffffff),
        emissive: stdMat.emissive ? stdMat.emissive.clone() : new THREE.Color(0x000000),
        emissiveIntensity: stdMat.emissiveIntensity ?? 0,
        opacity: mat.opacity,
        transparent: mat.transparent,
      });
    }

    const orig = this.originalMaterials.get(mesh)!;

    // Apply status color directly to material base color if supported
    if ("color" in mat && (mat as any).color instanceof THREE.Color) {
      (mat as any).color.copy(statusColor);
    }

    // Apply emissive state if supported
    if ("emissive" in mat && (mat as any).emissive instanceof THREE.Color) {
      const stdMat = mat as THREE.MeshStandardMaterial;
      if (isEmissive) {
        stdMat.emissive.copy(statusColor);
        stdMat.emissiveIntensity = emissiveIntensity;
      } else {
        stdMat.emissive.setHex(0x000000);
        stdMat.emissiveIntensity = 0;
      }
    }

    if (isDimmed) {
      mat.transparent = true;
      mat.opacity = 0.45;
    } else {
      mat.transparent = orig.transparent;
      mat.opacity = orig.opacity;
    }

    mat.needsUpdate = true;
  }

  /**
   * Updates selection highlight on objects in the scene.
   */
  updateSelection(
    newSelectedId: string | null,
    objectsById: Map<string, THREE.Object3D>,
    scene: THREE.Scene,
  ): void {
    if (this.selectedObjectId === newSelectedId) return;

    // Clear old selection helper
    if (this.selectedObjectId && this.selectionHighlights.has(this.selectedObjectId)) {
      const oldHelper = this.selectionHighlights.get(this.selectedObjectId)!;
      scene.remove(oldHelper);
      oldHelper.dispose();
      this.selectionHighlights.delete(this.selectedObjectId);
    }

    this.selectedObjectId = newSelectedId;

    // Create new selection helper if selected
    if (newSelectedId) {
      const targetObj = objectsById.get(newSelectedId);
      if (targetObj) {
        const helper = new THREE.BoxHelper(targetObj, 0x38bdf8);
        (helper.material as THREE.LineBasicMaterial).linewidth = 2;
        scene.add(helper);
        this.selectionHighlights.set(newSelectedId, helper);
      }
    }
  }

  /** Refreshes world-space selection bounds after an object transform. */
  updateSelectionHighlights(): void {
    for (const helper of this.selectionHighlights.values()) {
      helper.update();
    }
  }

  clear(scene: THREE.Scene): void {
    for (const helper of this.selectionHighlights.values()) {
      scene.remove(helper);
      helper.dispose();
    }
    this.selectionHighlights.clear();

    // Restore cached original material properties
    for (const [mesh, orig] of this.originalMaterials.entries()) {
      const mat = mesh.material;
      if (!mat) continue;
      if (Array.isArray(mat)) {
        mat.forEach((m) => this.restoreMaterial(m, orig));
      } else {
        this.restoreMaterial(mat, orig);
      }
    }
    this.originalMaterials.clear();
    this.selectedObjectId = null;
  }

  private restoreMaterial(mat: THREE.Material, orig: OriginalMaterialData): void {
    if ("color" in mat && (mat as any).color instanceof THREE.Color) {
      (mat as any).color.copy(orig.color);
    }
    if ("emissive" in mat && (mat as any).emissive instanceof THREE.Color) {
      (mat as any).emissive.copy(orig.emissive);
      (mat as any).emissiveIntensity = orig.emissiveIntensity;
    }
    mat.transparent = orig.transparent;
    mat.opacity = orig.opacity;
    mat.needsUpdate = true;
  }
}

