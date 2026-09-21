import { test, describe } from "node:test";
import assert from "node:assert";
import { RendererStateStore } from "../src/store/RendererStateStore.ts";
import type {
  DeviceVisualDescriptor,
  SceneDescriptor,
  SceneVisualSnapshot,
} from "../../renderer-contract/scene-bridge-contract.ts";

describe("RendererStateStore Unit Tests", () => {
  test("initial state is DISCONNECTED with empty scene and devices", () => {
    const store = new RendererStateStore();
    const state = store.getState();

    assert.strictEqual(state.connectionState, "DISCONNECTED");
    assert.strictEqual(state.activeScene, null);
    assert.strictEqual(state.selectedObjectId, null);
    assert.strictEqual(state.devices.length, 0);
    assert.strictEqual(state.lastError, null);
  });

  test("initScene updates activeScene and cleans old devices and selection", () => {
    const store = new RendererStateStore();
    // Pre-populate with dummy devices
    store.syncState({
      sceneId: "old_scene",
      devices: [
        {
          objectId: "old_dev",
          deviceIdentity: "ID_OLD",
          displayName: "Old Phone",
          status: "ONLINE",
          connectionType: "USB",
          visual: { statusColorHex: "#fff", isEmissive: false, emissiveIntensity: 0, badgeText: "O", isDimmed: false },
        },
      ],
      selectedObjectId: "old_dev",
    });

    assert.strictEqual(store.getState().devices.length, 1);
    assert.strictEqual(store.getState().selectedObjectId, "old_dev");

    // Init new scene
    const newScene: SceneDescriptor = {
      id: "scene_fresh",
      name: "Fresh Scene",
      environmentFileName: null,
      camera: { position: { x: 0, y: 0, z: 0 }, target: { x: 0, y: 0, z: 0 }, fov: 45 },
      assets: [],
      bindableObjectIds: ["slot_1"],
    };

    store.initScene(newScene);

    const state = store.getState();
    assert.strictEqual(state.activeScene?.id, "scene_fresh");
    assert.strictEqual(state.devices.length, 0);
    assert.strictEqual(state.selectedObjectId, null);
  });

  test("syncState indexes devices in map for fast O(1) lookup", () => {
    const store = new RendererStateStore();
    const dev1: DeviceVisualDescriptor = {
      objectId: "slot_a",
      deviceIdentity: "DEV_A",
      displayName: "Phone A",
      status: "ONLINE",
      connectionType: "USB",
      visual: { statusColorHex: "#00ff00", isEmissive: true, emissiveIntensity: 0.5, badgeText: "USB", isDimmed: false },
    };
    const dev2: DeviceVisualDescriptor = {
      objectId: "slot_b",
      deviceIdentity: "DEV_B",
      displayName: "Phone B",
      status: "OFFLINE",
      connectionType: "WIFI",
      visual: { statusColorHex: "#666666", isEmissive: false, emissiveIntensity: 0, badgeText: "OFFLINE", isDimmed: true },
    };

    store.syncState({
      sceneId: "scene_1",
      devices: [dev1, dev2],
      selectedObjectId: "slot_a",
    });

    assert.strictEqual(store.getDevice("slot_a")?.displayName, "Phone A");
    assert.strictEqual(store.getDevice("slot_b")?.displayName, "Phone B");
    assert.strictEqual(store.getDevice("non_existent"), undefined);
    assert.strictEqual(store.getDeviceMap().size, 2);
    assert.strictEqual(store.getState().selectedObjectId, "slot_a");
  });

  test("subscribers receive state snapshots on mutation", () => {
    const store = new RendererStateStore();
    let notificationCount = 0;
    let lastSeenState = store.getState();

    const unsubscribe = store.subscribe((state) => {
      notificationCount++;
      lastSeenState = state;
    });

    store.setConnectionState("READY");
    assert.strictEqual(notificationCount, 1);
    assert.strictEqual(lastSeenState.connectionState, "READY");

    store.updateSelection("test_selection");
    assert.strictEqual(notificationCount, 2);
    assert.strictEqual(lastSeenState.selectedObjectId, "test_selection");

    // Setting same selection doesn't re-trigger notification
    store.updateSelection("test_selection");
    assert.strictEqual(notificationCount, 2);

    // Unsubscribe stops notifications
    unsubscribe();
    store.updateSelection("another_selection");
    assert.strictEqual(notificationCount, 2);
  });
});
