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
          this.indexSceneNodes(envRoot, descriptor.bindableObjectIds);
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

    return {
      root: this.contentGroup,
      objectsById: this.objectsById,
      pickableObjects: this.pickableObjects,
      hasEnvironmentModel,
    };
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
        assetRoot.position.set(asset.transform.position.x, asset.transform.position.y, asset.transform.position.z);
        assetRoot.rotation.set(asset.transform.rotation.x, asset.transform.rotation.y, asset.transform.rotation.z);
        assetRoot.scale.set(asset.transform.scale.x, asset.transform.scale.y, asset.transform.scale.z);

        // Register the asset itself as a bindable / pickable object
        this.registerObject(asset.id, assetRoot);

        // Index any tagged child nodes
        this.indexSceneNodes(assetRoot, bindableObjectIds);
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
  private indexSceneNodes(root: THREE.Object3D, bindableObjectIds: string[]): void {
    const bindableSet = new Set(bindableObjectIds);

    root.traverse((node: THREE.Object3D) => {
      if ((node as THREE.Mesh).isMesh) {
        node.castShadow = true;
        node.receiveShadow = true;
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
        this.registerObject(matchedId, node);
      } else if (node.userData?.isBindable && node.name) {
        this.registerObject(node.name, node);
      }
    });
  }

  /**
   * Registers an object into the lookup map and pickable list.
   */
  registerObject(objectId: string, object: THREE.Object3D): void {
    object.userData.objectId = objectId;
    this.objectsById.set(objectId, object);

    // Collect all descendant meshes (or object itself) for Raycaster picking
    object.traverse((child) => {
      if ((child as THREE.Mesh).isMesh) {
        child.userData.objectId = objectId;
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
