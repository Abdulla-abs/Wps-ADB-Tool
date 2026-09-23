import { test, describe } from "node:test";
import assert from "node:assert";
import * as THREE from "three";
import { SceneLoader } from "../src/scene/SceneLoader.ts";
import type { SceneDescriptor } from "../../renderer-contract/scene-bridge-contract.ts";

describe("SceneLoader Unit Tests", () => {
  test("indexes bindable objects with exact name, blender suffix, and userData.objectId", () => {
    const contentGroup = new THREE.Group();
    const loader = new SceneLoader({ contentGroup });

    // Manually register an environment tree
    const root = new THREE.Group();

    // 1. Exact match
    const meshExact = new THREE.Mesh(new THREE.BoxGeometry(), new THREE.MeshBasicMaterial());
    meshExact.name = "phone_slot_exact";
    root.add(meshExact);

    // 2. Blender duplicate suffix (.001)
    const meshBlender = new THREE.Mesh(new THREE.BoxGeometry(), new THREE.MeshBasicMaterial());
    meshBlender.name = "phone_slot_blender.001";
    root.add(meshBlender);

    // 3. glTF extras (userData.objectId)
    const meshExtras = new THREE.Mesh(new THREE.BoxGeometry(), new THREE.MeshBasicMaterial());
    meshExtras.name = "unrelated_node_name";
    meshExtras.userData = { objectId: "phone_slot_extras" };
    root.add(meshExtras);

    // Register via indexSceneNodes method
    (loader as any).indexSceneNodes(root, ["phone_slot_exact", "phone_slot_blender", "phone_slot_extras"]);

    const objectsMap = loader.getObjectsById();
    assert.strictEqual(objectsMap.has("phone_slot_exact"), true);
    assert.strictEqual(objectsMap.has("phone_slot_blender"), true);
    assert.strictEqual(objectsMap.has("phone_slot_extras"), true);

    const pickables = loader.getPickableObjects();
    assert.strictEqual(pickables.length, 3);
    assert.strictEqual(pickables.includes(meshExact), true);
    assert.strictEqual(pickables.includes(meshBlender), true);
    assert.strictEqual(pickables.includes(meshExtras), true);
  });

  test("gracefully isolates error and invokes onError when model load fails", async () => {
    const contentGroup = new THREE.Group();
    let reportedError: Error | null = null;
    let reportedCategory: string | null = null;

    const loader = new SceneLoader({
      contentGroup,
      onError: (err, category) => {
        reportedError = err;
        reportedCategory = category;
      },
    });

    const descriptor: SceneDescriptor = {
      id: "non_existent_scene",
      name: "Missing Scene",
      environmentFileName: "not_found.glb",
      camera: { position: { x: 0, y: 0, z: 0 }, target: { x: 0, y: 0, z: 0 }, fov: 45 },
      assets: [],
      bindableObjectIds: [],
    };

    // loadScene must NOT throw
    const result = await loader.loadScene(descriptor);
    assert.strictEqual(result.hasEnvironmentModel, false);
    assert.strictEqual(result.objectsById.size, 0);
    assert.strictEqual(reportedCategory, "ENVIRONMENT_LOAD_FAILED");
    assert.notStrictEqual(reportedError, null);
  });

  test("creates fallback bindable objects when no GLB exists", async () => {
    const contentGroup = new THREE.Group();
    const loader = new SceneLoader({ contentGroup });

    const defaultCamera = { position: { x: 0, y: 0, z: 0 }, target: { x: 0, y: 0, z: 0 }, fov: 45 };
    const result = await loader.loadScene({
      id: "scene_default",
      name: "Default",
      camera: defaultCamera,
      assets: [],
      bindableObjectIds: ["device_slot_1", "device_slot_2"],
    });

    assert.strictEqual(result.objectsById.has("device_slot_1"), true);
    assert.strictEqual(result.objectsById.has("device_slot_2"), true);
    assert.strictEqual(result.pickableObjects.length, 2);
  });

  test("keeps every missing built-in slot when assets are present", async () => {
    const contentGroup = new THREE.Group();
    const loader = new SceneLoader({ contentGroup });
    const loadAsset = (loader as any).loadAsset.bind(loader);
    (loader as any).loadAsset = async () => {
      const asset = new THREE.Mesh(new THREE.BoxGeometry(), new THREE.MeshBasicMaterial());
      asset.name = "phone_asset";
      loader.registerObject("phone_asset", asset, "asset", "phone_asset");
      contentGroup.add(asset);
    };

    const result = await loader.loadScene({
      id: "scene_with_asset",
      name: "Scene with asset",
      camera: { position: { x: 0, y: 0, z: 0 }, target: { x: 0, y: 0, z: 0 }, fov: 45 },
      assets: [{
        id: "phone_asset",
        fileName: "assets/phone.glb",
        name: "Phone",
        transform: {
          position: { x: 0, y: 0, z: 0 },
          rotation: { x: 0, y: 0, z: 0 },
          scale: { x: 1, y: 1, z: 1 },
        },
      }],
      bindableObjectIds: ["device_slot_1", "device_slot_2", "device_slot_3", "device_slot_4"],
    });

    assert.strictEqual(result.objectsById.has("phone_asset"), true);
    assert.strictEqual(result.objectsById.has("device_slot_1"), true);
    assert.strictEqual(result.objectsById.has("device_slot_2"), true);
    assert.strictEqual(result.objectsById.has("device_slot_3"), true);
    assert.strictEqual(result.objectsById.has("device_slot_4"), true);
    assert.strictEqual(contentGroup.children.length, 5);
  });

  test("keeps the asset ID mapped to the full GLTF root, not its last child mesh", () => {
    const contentGroup = new THREE.Group();
    const loader = new SceneLoader({ contentGroup });
    const assetRoot = new THREE.Group();
    const firstMesh = new THREE.Mesh(new THREE.BoxGeometry(), new THREE.MeshBasicMaterial());
    const lastMesh = new THREE.Mesh(new THREE.BoxGeometry(), new THREE.MeshBasicMaterial());
    firstMesh.name = "Phone Frame";
    lastMesh.name = "Volume Button";
    assetRoot.add(firstMesh, lastMesh);

    // Match loadAsset ordering: index authored child metadata, then register root.
    (loader as any).indexSceneNodes(assetRoot, [], "asset", "phone_asset");
    loader.registerObject("phone_asset", assetRoot, "asset", "phone_asset");

    assert.strictEqual(loader.getObjectsById().get("phone_asset"), assetRoot);
    assert.strictEqual(firstMesh.userData.objectId, "phone_asset");
    assert.strictEqual(lastMesh.userData.objectId, "phone_asset");
  });
});
