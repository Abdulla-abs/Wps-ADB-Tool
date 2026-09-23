import * as THREE from "three";
import { GLTFLoader } from "three/examples/jsm/loaders/GLTFLoader.js";
import type {
  SceneAssetDescriptor,
  SceneDescriptor,
} from "../../../renderer-contract/scene-bridge-contract.ts";

export interface LoadedSceneResult {
  root: THREE.Group;
  objectsById: Map<string, THREE.Object3D>;
  pickableObjects: THREE.Object3D[];
  hasEnvironmentModel: boolean;
}

export interface SceneLoaderOptions {
  contentGroup: THREE.Group;
  baseUrlPrefix?: string;
  onError?: (error: Error, category: string) => void;
}

/**
 * SceneLoader
 * Responsible for loading environment and asset GLBs via Three.js GLTFLoader,
 * traversing nodes to map and tag bindable objects, and providing safe fallback
 * when models are not provided or fail to load.
 */
export class SceneLoader {
  private readonly contentGroup: THREE.Group;
  private readonly baseUrlPrefix: string;
  private readonly loader: GLTFLoader;
  private readonly onError?: (error: Error, category: string) => void;

  private currentSceneId: string | null = null;
  private objectsById = new Map<string, THREE.Object3D>();
  private pickableObjects: THREE.Object3D[] = [];

  constructor(options: SceneLoaderOptions) {
    this.contentGroup = options.contentGroup;
    this.baseUrlPrefix = options.baseUrlPrefix ?? "/scenes";
    this.onError = options.onError;
    this.loader = new GLTFLoader();
  }

  /**
   * Loads a complete scene based on the provided SceneDescriptor.
   * Clears existing content and builds object indices.
   */
  async loadScene(descriptor: SceneDescriptor): Promise<LoadedSceneResult> {
    this.clear();
    this.currentSceneId = descriptor.id;

    let hasEnvironmentModel = false;

    // 1. Load Environment GLB if specified
    if (descriptor.environmentFileName) {
      const envUrl = `${this.baseUrlPrefix}/${encodeURIComponent(descriptor.id)}/${encodeURIComponent(descriptor.environmentFileName)}`;
      try {
        const gltf = await this.loader.loadAsync(envUrl);
        const envRoot = gltf.scene || gltf.scenes[0];
        if (envRoot) {
          envRoot.name = "__environment_model__";
          envRoot.userData.objectType = "environment";
          this.indexSceneNodes(envRoot, descriptor.bindableObjectIds, "environment");
          this.contentGroup.add(envRoot);
          hasEnvironmentModel = true;
          console.log(`[SceneLoader] Environment GLB successfully loaded: ${descriptor.environmentFileName}`);
        }
      } catch (err) {
        const error = err instanceof Error ? err : new Error(String(err));
        console.warn(`[SceneLoader] Failed to load environment GLB (${envUrl}), falling back to staging grid:`, error.message);
        this.onError?.(error, "ENVIRONMENT_LOAD_FAILED");
      }
    }

    // 2. Load additional assets if specified
    if (descriptor.assets && descriptor.assets.length > 0) {
      for (const asset of descriptor.assets) {
        await this.loadAsset(descriptor.id, asset, descriptor.bindableObjectIds);
      }
    }

    // Built-in bindable slots are part of the scene even when an environment GLB
    // or imported assets are present. Add placeholders only for IDs that were not
    // supplied by either GLB, so importing an asset never removes the original slots.
    const missingBindableIds = (descriptor.bindableObjectIds ?? []).filter(
      (objectId) => !this.objectsById.has(objectId),
    );
    if (missingBindableIds.length > 0) {
      this.createFallbackObjects(missingBindableIds);
    }

    return {
      root: this.contentGroup,
      objectsById: this.objectsById,
      pickableObjects: this.pickableObjects,
      hasEnvironmentModel,
    };
  }

  private createFallbackObjects(bindableObjectIds: string[]): void {
    bindableObjectIds.forEach((objectId, index) => {
      const geometry = new THREE.BoxGeometry(1.2, 2.2, 0.35);
      const material = new THREE.MeshStandardMaterial({
        color: 0x334155,
        emissive: 0x000000,
        roughness: 0.7,
        metalness: 0.1,
      });

      const object = new THREE.Mesh(geometry, material);
      object.name = objectId;
      object.position.set(
        (index % 4) * 2.0 - 3.0,
        1.1,
        Math.floor(index / 4) * 1.5,
      );

      this.registerObject(objectId, object, "slot");
      this.contentGroup.add(object);
    });
  }

  private async loadAsset(
    sceneId: string,
    asset: SceneAssetDescriptor,
    bindableObjectIds: string[],
  ): Promise<void> {
    const assetUrl = `${this.baseUrlPrefix}/${encodeURIComponent(sceneId)}/${encodeURIComponent(asset.fileName)}`;
    try {
      const gltf = await this.loader.loadAsync(assetUrl);
      const assetRoot = gltf.scene || gltf.scenes[0];
      if (assetRoot) {
        assetRoot.name = asset.id;
        assetRoot.userData.assetId = asset.id;
        assetRoot.userData.objectType = "asset";
        assetRoot.position.set(asset.transform.position.x, asset.transform.position.y, asset.transform.position.z);
        assetRoot.rotation.set(asset.transform.rotation.x, asset.transform.rotation.y, asset.transform.rotation.z);
        assetRoot.scale.set(asset.transform.scale.x, asset.transform.scale.y, asset.transform.scale.z);

        // Index explicitly bindable child nodes before registering the asset root.
        // registerObject tags otherwise-unidentified descendants for picking; doing
        // that first would make this traversal mistake every child for the asset root
        // and overwrite the asset ID lookup with the last mesh in the GLB.
        this.indexSceneNodes(assetRoot, bindableObjectIds, "asset", asset.id);

        // The asset ID must resolve to the complete GLTF scene root so gizmo edits
        // transform the whole model rather than a single child mesh.
        this.registerObject(asset.id, assetRoot, "asset", asset.id);
        this.contentGroup.add(assetRoot);
        console.log(`[SceneLoader] Asset GLB loaded: ${asset.fileName} (id: ${asset.id})`);
      }
    } catch (err) {
      const error = err instanceof Error ? err : new Error(String(err));
      console.warn(`[SceneLoader] Failed to load asset GLB (${assetUrl}):`, error.message);
      this.onError?.(error, "ASSET_LOAD_FAILED");
    }
  }

  /**
   * Traverses a 3D subtree, enabling shadows and indexing bindable/pickable objects.
   * Supports exact name matching, Blender duplicate suffixes (.001), and glTF extras (userData.objectId).
   */
  private indexSceneNodes(
    root: THREE.Object3D,
    bindableObjectIds: string[],
    objectType = "environment",
    assetId?: string,
  ): void {
    const bindableSet = new Set(bindableObjectIds);

    root.traverse((node: THREE.Object3D) => {
      if ((node as THREE.Mesh).isMesh) {
        node.castShadow = true;
        node.receiveShadow = true;
      }

      if (!node.userData.objectType) {
        node.userData.objectType = objectType;
      }
      if (assetId && !node.userData.assetId) {
        node.userData.assetId = assetId;
      }

      // Priority 1: glTF extras / explicit userData.objectId
      let matchedId: string | null = null;
      if (typeof node.userData?.objectId === "string" && node.userData.objectId.length > 0) {
        matchedId = node.userData.objectId;
      } else if (node.name) {
        // Priority 2: Direct node name match
        if (bindableSet.has(node.name)) {
          matchedId = node.name;
        } else {
          // Priority 3: Blender duplicate suffix stripping (e.g. "phone_slot.001" -> "phone_slot")
          const normalized = node.name.replace(/\.\d+$/, "");
          if (bindableSet.has(normalized)) {
            matchedId = normalized;
          }
        }
      }

      if (matchedId) {
        this.registerObject(matchedId, node, objectType, assetId);
      } else if (node.userData?.isBindable && node.name) {
        this.registerObject(node.name, node, objectType, assetId);
      }
    });
  }

  /**
   * Registers an object into the lookup map and pickable list.
   */
  registerObject(
    objectId: string,
    object: THREE.Object3D,
    objectType = "environment",
    assetId?: string,
  ): void {
    object.userData.objectId = objectId;
    object.userData.objectType = objectType;
    if (assetId) {
      object.userData.assetId = assetId;
    }
    this.objectsById.set(objectId, object);

    // Collect all descendant meshes (or object itself) for Raycaster picking
    object.traverse((child) => {
      // Keep IDs declared by GLTF extras or registered child objects intact.
      // Unidentified descendants inherit the parent ID for picking fallback.
      if (!child.userData.objectId) {
        child.userData.objectId = objectId;
      }
      if (!child.userData.objectType) {
        child.userData.objectType = objectType;
      }
      if (assetId && !child.userData.assetId) {
        child.userData.assetId = assetId;
      }
      if ((child as THREE.Mesh).isMesh) {
        if (!this.pickableObjects.includes(child)) {
          this.pickableObjects.push(child);
        }
      }
    });
  }

  /**
   * Clears loaded models, materials, and indices.
   */
  clear(): void {
    this.contentGroup.clear();
    this.objectsById.clear();
    this.pickableObjects = [];
    this.currentSceneId = null;
  }

  getObjectsById(): Map<string, THREE.Object3D> {
    return this.objectsById;
  }

  getPickableObjects(): THREE.Object3D[] {
    return this.pickableObjects;
  }
}
