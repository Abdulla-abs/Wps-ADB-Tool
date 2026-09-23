import { test, describe } from "node:test";
import assert from "node:assert";
import * as THREE from "three";
import { TransformController } from "../src/interaction/TransformController.ts";
import { RendererRuntime } from "../src/RendererRuntime.ts";
import { MockCefBridge } from "./MockCefBridge.ts";
import { WIRE_TYPES } from "../../renderer-contract/scene-bridge-contract.ts";

function createMockDomElement() {
  const listeners: Record<string, Function[]> = {};
  return {
    style: { touchAction: "" },
    addEventListener: (type: string, listener: Function) => {
      listeners[type] = listeners[type] || [];
      listeners[type].push(listener);
    },
    removeEventListener: (type: string, listener: Function) => {
      if (listeners[type]) {
        listeners[type] = listeners[type].filter((l) => l !== listener);
      }
    },
    getBoundingClientRect: () => ({ left: 0, top: 0, width: 800, height: 600 }),
    dispatchEvent: (e: any) => {
      listeners[e.type]?.forEach((l) => l(e));
    },
  };
}

describe("TransformController Unit Tests", () => {
  test("initial state: mode is VIEW and no object is attached", () => {
    const camera = new THREE.PerspectiveCamera();
    const domElement = createMockDomElement() as any;
    const scene = new THREE.Scene();
    const orbitControls = { enabled: true } as any;

    const controller = new TransformController({
      camera,
      domElement,
      scene,
      orbitControls,
    });

    assert.strictEqual(controller.getInteractionMode(), "VIEW");
    assert.strictEqual(controller.getAttachedObject(), null);
    assert.strictEqual(controller.getAttachedObjectId(), null);
    assert.strictEqual(controller.getHelper().parent, null);

    controller.dispose();
  });

  test("mode gating: only attaches gizmo in EDITING mode and only for objectType === 'asset'", () => {
    const camera = new THREE.PerspectiveCamera();
    const domElement = createMockDomElement() as any;
    const scene = new THREE.Scene();
    const orbitControls = { enabled: true } as any;

    const controller = new TransformController({
      camera,
      domElement,
      scene,
      orbitControls,
    });

    const envObject = new THREE.Mesh();
    envObject.userData = { objectId: "env_slot_1", objectType: "environment" };

    const assetObject = new THREE.Mesh();
    assetObject.userData = { objectId: "asset_phone_1", objectType: "asset", assetId: "asset_phone_1" };

    // 1. In VIEW mode, selecting asset does not attach
    controller.updateSelection("asset_phone_1", assetObject);
    assert.strictEqual(controller.getAttachedObject(), null);
    assert.strictEqual(controller.getHelper().parent, null);

    // 2. Switch to EDITING mode: now assetObject gets attached and helper added to scene
    controller.setInteractionMode("EDITING");
    assert.strictEqual(controller.getAttachedObject(), assetObject);
    assert.strictEqual(controller.getHelper().parent, scene);

    // 3. Selecting environment object in EDITING mode is rejected (boundary protection)
    controller.updateSelection("env_slot_1", envObject);
    assert.strictEqual(controller.getAttachedObject(), null);
    assert.strictEqual(controller.getHelper().parent, null);

    // 4. Selecting asset again attaches
    controller.updateSelection("asset_phone_1", assetObject);
    assert.strictEqual(controller.getAttachedObject(), assetObject);
    assert.strictEqual(controller.getHelper().parent, scene);

    // 5. Switching back to BINDING or VIEW detaches
    controller.setInteractionMode("BINDING");
    assert.strictEqual(controller.getAttachedObject(), null);
    assert.strictEqual(controller.getHelper().parent, null);

    controller.dispose();
  });

  test("does not reattach the selected asset when host echoes state during a drag", () => {
    const camera = new THREE.PerspectiveCamera();
    const domElement = createMockDomElement() as any;
    const scene = new THREE.Scene();
    const controller = new TransformController({
      camera,
      domElement,
      scene,
      orbitControls: { enabled: true } as any,
    });
    const asset = new THREE.Group();
    asset.userData.objectType = "asset";
    const controls = controller.getControls();
    const originalAttach = controls.attach.bind(controls);
    let attachCount = 0;
    controls.attach = ((object: THREE.Object3D) => {
      attachCount++;
      return originalAttach(object);
    }) as typeof controls.attach;

    controller.setInteractionMode("EDITING");
    controller.updateSelection("asset_1", asset);
    const countAfterInitialAttach = attachCount;

    // STATE_SYNC echoes caused by OBJECT_TRANSFORM_CHANGED should preserve the
    // active TransformControls attachment rather than restarting the drag.
    controller.updateSelection("asset_1", asset);

    assert.strictEqual(attachCount, countAfterInitialAttach);
    assert.strictEqual(controller.getControls().object, asset);
    controller.dispose();
  });

  test("dragging isolation: dragging-changed disables and restores OrbitControls", () => {
    const camera = new THREE.PerspectiveCamera();
    const domElement = createMockDomElement() as any;
    const scene = new THREE.Scene();
    const orbitControls = { enabled: true } as any;

    const controller = new TransformController({
      camera,
      domElement,
      scene,
      orbitControls,
    });

    const tc = controller.getControls();

    // Start drag
    tc.dispatchEvent({ type: "dragging-changed", value: true });
    assert.strictEqual(orbitControls.enabled, false);

    // End drag
    tc.dispatchEvent({ type: "dragging-changed", value: false });
    assert.strictEqual(orbitControls.enabled, true);

    controller.dispose();
  });

  test("objectChange event: reports position, rotation, scale payload", () => {
    const camera = new THREE.PerspectiveCamera();
    const domElement = createMockDomElement() as any;
    const scene = new THREE.Scene();
    const orbitControls = { enabled: true } as any;
    let reportedPayload: any = null;

    const controller = new TransformController({
      camera,
      domElement,
      scene,
      orbitControls,
      onTransformChanged: (payload) => {
        reportedPayload = payload;
      },
    });

    controller.setInteractionMode("EDITING");

    const assetObject = new THREE.Mesh();
    assetObject.userData = { objectId: "test_asset", objectType: "asset" };
    scene.add(assetObject);

    controller.updateSelection("test_asset", assetObject);

    // Mutate object transform
    assetObject.position.set(1.5, 2.5, 3.5);
    assetObject.rotation.set(0.1, 0.2, 0.3);
    assetObject.scale.set(2, 2, 2);

    controller.getControls().dispatchEvent({ type: "objectChange" });

    assert.ok(reportedPayload);
    assert.strictEqual(reportedPayload.objectId, "test_asset");
    assert.strictEqual(reportedPayload.position.x, 1.5);
    assert.strictEqual(reportedPayload.position.y, 2.5);
    assert.strictEqual(reportedPayload.position.z, 3.5);
    assert.strictEqual(reportedPayload.scale.x, 2);

    controller.dispose();
  });

  test("applyTransform: updates target object directly", () => {
    const camera = new THREE.PerspectiveCamera();
    const domElement = createMockDomElement() as any;
    const scene = new THREE.Scene();
    const orbitControls = { enabled: true } as any;

    const controller = new TransformController({
      camera,
      domElement,
      scene,
      orbitControls,
    });

    const assetObject = new THREE.Mesh();
    assetObject.userData = { objectId: "target_asset", objectType: "asset" };

    controller.applyTransform(
      "target_asset",
      assetObject,
      { x: 10, y: 20, z: 30 },
      { x: 0, y: 1.57, z: 0 },
      { x: 0.5, y: 0.5, z: 0.5 },
    );

    assert.strictEqual(assetObject.position.x, 10);
    assert.strictEqual(assetObject.position.y, 20);
    assert.strictEqual(assetObject.position.z, 30);
    assert.strictEqual(assetObject.rotation.y, 1.57);
    assert.strictEqual(assetObject.scale.x, 0.5);

    controller.dispose();
  });

  test("dispose lifecycle: cleans up helper, restores orbitControls and unregisters listeners", () => {
    const camera = new THREE.PerspectiveCamera();
    const domElement = createMockDomElement() as any;
    const scene = new THREE.Scene();
    const orbitControls = { enabled: false } as any;

    const controller = new TransformController({
      camera,
      domElement,
      scene,
      orbitControls,
    });

    controller.setInteractionMode("EDITING");
    const assetObject = new THREE.Mesh();
    assetObject.userData = { objectId: "test_asset", objectType: "asset" };
    controller.updateSelection("test_asset", assetObject);

    assert.strictEqual(controller.getHelper().parent, scene);

    controller.dispose();

    assert.strictEqual(controller.getHelper().parent, null);
    assert.strictEqual(controller.getAttachedObject(), null);
    assert.strictEqual(orbitControls.enabled, true);
  });
});

describe("Transform Protocol Integration Tests", () => {
  test("SET_INTERACTION_MODE downlink: updates store interactionMode", () => {
    const bridge = new MockCefBridge();
    const runtime = new RendererRuntime({ bridge });
    runtime.start();

    assert.strictEqual(runtime.getStore().getState().interactionMode, "VIEW");

    bridge.emitIncoming(
      JSON.stringify({
        type: WIRE_TYPES.SET_INTERACTION_MODE,
        version: 1,
        timestamp: Date.now(),
        payload: {
          mode: "EDITING",
        },
      }),
    );

    assert.strictEqual(runtime.getStore().getState().interactionMode, "EDITING");

    bridge.emitIncoming(
      JSON.stringify({
        type: WIRE_TYPES.SET_INTERACTION_MODE,
        version: 1,
        timestamp: Date.now(),
        payload: {
          mode: "BINDING",
        },
      }),
    );

    assert.strictEqual(runtime.getStore().getState().interactionMode, "BINDING");
  });

  test("SET_OBJECT_TRANSFORM downlink: dispatches through MessageDispatcher", () => {
    const bridge = new MockCefBridge();
    let receivedPayload: any = null;
    const runtime = new RendererRuntime({
      bridge,
      hooks: {
        onSetObjectTransform: (payload) => {
          receivedPayload = payload;
        },
      },
    });
    runtime.start();

    bridge.emitIncoming(
      JSON.stringify({
        type: "SET_OBJECT_TRANSFORM",
        version: 1,
        timestamp: Date.now(),
        payload: {
          objectId: "asset_01",
          position: { x: 1, y: 2, z: 3 },
          rotation: { x: 0, y: 0, z: 0 },
          scale: { x: 1, y: 1, z: 1 },
        },
      }),
    );

    assert.ok(receivedPayload);
    assert.strictEqual(receivedPayload.objectId, "asset_01");
    assert.strictEqual(receivedPayload.position.x, 1);
    assert.strictEqual(receivedPayload.position.y, 2);
    assert.strictEqual(receivedPayload.position.z, 3);
  });

  test("sendObjectTransformChanged: emits OBJECT_TRANSFORM_CHANGED frame to bridge", () => {
    const bridge = new MockCefBridge();
    const runtime = new RendererRuntime({ bridge });
    runtime.start();
    bridge.clear();

    runtime.sendObjectTransformChanged({
      objectId: "asset_01",
      position: { x: 4, y: 5, z: 6 },
      rotation: { x: 0.1, y: 0.2, z: 0.3 },
      scale: { x: 1.5, y: 1.5, z: 1.5 },
    });

    assert.strictEqual(bridge.sentMessages.length, 1);
    const sent = bridge.getLastSentPayload<any>();
    assert.strictEqual(sent.type, WIRE_TYPES.OBJECT_TRANSFORM_CHANGED);
    assert.strictEqual(sent.payload.objectId, "asset_01");
    assert.strictEqual(sent.payload.position.x, 4);
    assert.strictEqual(sent.payload.scale.x, 1.5);
  });
});
