import { test, describe } from "node:test";
import assert from "node:assert";
import { MockCefBridge } from "./MockCefBridge.ts";
import { RendererRuntime } from "../src/RendererRuntime.ts";
import {
  CURRENT_BRIDGE_PROTOCOL_VERSION,
  WIRE_TYPES,
  VISUAL_STATUSES,
} from "../../renderer-contract/scene-bridge-contract.ts";
import type {
  RendererReadyMessage,
  ObjectClickedMessage,
  RendererErrorMessage,
} from "../../renderer-contract/scene-bridge-contract.ts";

describe("RendererRuntime Skeleton Integration Tests", () => {

  test("handshake: start() transmits RENDERER_READY and sets READY state", () => {
    const bridge = new MockCefBridge();
    const runtime = new RendererRuntime({ bridge });

    assert.strictEqual(runtime.getStore().getState().connectionState, "DISCONNECTED");

    runtime.start();

    assert.strictEqual(runtime.getStore().getState().connectionState, "READY");
    assert.strictEqual(bridge.sentMessages.length, 1);

    const sent = bridge.getLastSentPayload<RendererReadyMessage>();
    assert.strictEqual(sent.type, WIRE_TYPES.RENDERER_READY);
    assert.strictEqual(sent.version, CURRENT_BRIDGE_PROTOCOL_VERSION);
    assert.strictEqual(sent.payload.protocolVersion, CURRENT_BRIDGE_PROTOCOL_VERSION);
    assert.strictEqual(sent.payload.rendererVersion, "1.0");
  });

  test("SCENE_INIT: sets activeScene and clears previous device bindings", () => {
    const bridge = new MockCefBridge();
    const runtime = new RendererRuntime({ bridge });
    runtime.start();

    const sceneInitJson = JSON.stringify({
      type: "SCENE_INIT",
      version: 1,
      timestamp: 1000,
      payload: {
        sceneDescriptor: {
          id: "lab_01",
          name: "My Device Lab",
          environmentFileName: "lab.glb",
          camera: {
            position: { x: 0, y: 5, z: 10 },
            target: { x: 0, y: 0, z: 0 },
            fov: 45,
          },
          assets: [
            {
              id: "desk_1",
              fileName: "desk.glb",
              name: "Desk",
              transform: {
                position: { x: 0, y: 0, z: 0 },
                rotation: { x: 0, y: 0, z: 0 },
                scale: { x: 1, y: 1, z: 1 },
              },
            },
          ],
          bindableObjectIds: ["slot_phone_01"],
        },
      },
    });

    bridge.emitIncoming(sceneInitJson);

    const state = runtime.getStore().getState();
    assert.ok(state.activeScene);
    assert.strictEqual(state.activeScene!.id, "lab_01");
    assert.strictEqual(state.activeScene!.name, "My Device Lab");
    assert.strictEqual(state.devices.length, 0);
  });

  test("STATE_SYNC: updates devices map and selectedObjectId correctly", () => {
    const bridge = new MockCefBridge();
    const runtime = new RendererRuntime({ bridge });
    runtime.start();

    const stateSyncJson = JSON.stringify({
      type: "STATE_SYNC",
      version: 1,
      timestamp: 2000,
      payload: {
        snapshot: {
          sceneId: "lab_01",
          selectedObjectId: "slot_phone_01",
          devices: [
            {
              objectId: "slot_phone_01",
              deviceIdentity: "HW-PIXEL-8",
              displayName: "Pixel 8",
              status: "ONLINE",
              connectionType: "USB",
              visual: {
                statusColorHex: "#10B981",
                isEmissive: true,
                emissiveIntensity: 0.8,
                badgeText: "ONLINE",
                isDimmed: false,
              },
            },
            {
              objectId: "slot_phone_02",
              deviceIdentity: "HW-S24",
              displayName: "Galaxy S24",
              status: "OFFLINE",
              connectionType: "WIFI",
              visual: {
                statusColorHex: "#64748B",
                isEmissive: false,
                emissiveIntensity: 0.0,
                badgeText: "OFFLINE",
                isDimmed: true,
              },
            },
          ],
        },
      },
    });

    bridge.emitIncoming(stateSyncJson);

    const state = runtime.getStore().getState();
    assert.strictEqual(state.devices.length, 2);
    assert.strictEqual(state.selectedObjectId, "slot_phone_01");

    const pixel = runtime.getStore().getDevice("slot_phone_01");
    assert.ok(pixel);
    assert.strictEqual(pixel!.displayName, "Pixel 8");
    assert.strictEqual(pixel!.visual.statusColorHex, "#10B981");

    const s24 = runtime.getStore().getDevice("slot_phone_02");
    assert.ok(s24);
    assert.strictEqual(s24!.displayName, "Galaxy S24");
    assert.strictEqual(s24!.visual.isDimmed, true);
  });

  test("SELECTION_CHANGE: independently updates selection while keeping devices unchanged", () => {
    const bridge = new MockCefBridge();
    const runtime = new RendererRuntime({ bridge });
    runtime.start();

    // 1. Initial Sync
    bridge.emitIncoming(
      JSON.stringify({
        type: "STATE_SYNC",
        version: 1,
        timestamp: 100,
        payload: {
          snapshot: {
            sceneId: "lab_01",
            devices: [
              {
                objectId: "slot_1",
                deviceIdentity: "ID1",
                displayName: "Dev 1",
                status: "ONLINE",
                connectionType: "USB",
                visual: { statusColorHex: "#10B981", isEmissive: true, emissiveIntensity: 1, badgeText: "ON", isDimmed: false },
              },
            ],
            selectedObjectId: "slot_1",
          },
        },
      }),
    );

    // 2. Selection Change
    bridge.emitIncoming(
      JSON.stringify({
        type: "SELECTION_CHANGE",
        version: 1,
        timestamp: 200,
        payload: {
          selectedObjectId: null,
          focusCamera: false,
        },
      }),
    );

    const state = runtime.getStore().getState();
    assert.strictEqual(state.selectedObjectId, null);
    assert.strictEqual(state.devices.length, 1);
  });

  test("sendObjectClicked: emits OBJECT_CLICKED frame to bridge", () => {
    const bridge = new MockCefBridge();
    const runtime = new RendererRuntime({ bridge });
    runtime.start();
    bridge.clear();

    runtime.sendObjectClicked("phone_slot_3", 350.5, 240.0, true, false);

    assert.strictEqual(bridge.sentMessages.length, 1);
    const sent = bridge.getLastSentPayload<ObjectClickedMessage>();
    assert.strictEqual(sent.type, WIRE_TYPES.OBJECT_CLICKED);
    assert.strictEqual(sent.payload.objectId, "phone_slot_3");
    assert.strictEqual(sent.payload.screenX, 350.5);
    assert.strictEqual(sent.payload.screenY, 240.0);
    assert.strictEqual(sent.payload.isCtrlPressed, true);
    assert.strictEqual(sent.payload.isShiftPressed, false);
  });

  test("sendCameraChanged: emits CAMERA_CHANGED frame to bridge", () => {
    const bridge = new MockCefBridge();
    const runtime = new RendererRuntime({ bridge });
    runtime.start();
    bridge.clear();

    runtime.sendCameraChanged({
      position: { x: 5, y: 10, z: 15 },
      target: { x: 1, y: 2, z: 3 },
      fov: 60,
    });

    assert.strictEqual(bridge.sentMessages.length, 1);
    const sent = bridge.getLastSentPayload<{ type: string; payload: { position: { x: number; y: number; z: number }; target: { x: number; y: number; z: number }; fov: number } }>();
    assert.strictEqual(sent.type, WIRE_TYPES.CAMERA_CHANGED);
    assert.strictEqual(sent.payload.position.x, 5);
    assert.strictEqual(sent.payload.position.y, 10);
    assert.strictEqual(sent.payload.position.z, 15);
    assert.strictEqual(sent.payload.target.x, 1);
    assert.strictEqual(sent.payload.target.y, 2);
    assert.strictEqual(sent.payload.target.z, 3);
    assert.strictEqual(sent.payload.fov, 60);
  });

  test("isolation: invalid payload does not mutate store and reports RENDERER_ERROR", () => {
    const bridge = new MockCefBridge();
    const runtime = new RendererRuntime({ bridge });
    runtime.start();

    // Store state before bad message
    runtime.getStore().updateSelection("initial_selection");
    bridge.clear();

    // Emit corrupted payload for STATE_SYNC (missing snapshot)
    bridge.emitIncoming(
      JSON.stringify({
        type: "STATE_SYNC",
        version: 1,
        timestamp: 300,
        payload: {
          corruptedKey: 123,
        },
      }),
    );

    // Store must remain untouched
    const state = runtime.getStore().getState();
    assert.strictEqual(state.selectedObjectId, "initial_selection");

    // An error must have been reported
    assert.strictEqual(bridge.sentMessages.length, 1);
    const err = bridge.getLastSentPayload<RendererErrorMessage>();
    assert.strictEqual(err.type, WIRE_TYPES.RENDERER_ERROR);
    assert.strictEqual(err.payload.code, "INVALID_PAYLOAD");
  });

  test("error loop protection: receiving RENDERER_ERROR never bounces back an error", () => {
    const bridge = new MockCefBridge();
    const runtime = new RendererRuntime({ bridge });
    runtime.start();
    bridge.clear();

    // Host sends an error
    bridge.emitIncoming(
      JSON.stringify({
        type: "RENDERER_ERROR",
        version: 1,
        timestamp: 400,
        payload: {
          code: "HOST_ADAPTER_ERROR",
          message: "Internal host failure",
        },
      }),
    );

    // Store recorded the error
    const state = runtime.getStore().getState();
    assert.ok(state.lastError);
    assert.strictEqual(state.lastError!.code, "HOST_ADAPTER_ERROR");

    // Crucial: Bridge must NOT have sent another RENDERER_ERROR back to Host!
    assert.strictEqual(bridge.sentMessages.length, 0);
  });

  test("unknown type and unknown fields are tolerated without failure", () => {
    const bridge = new MockCefBridge();
    let unknownReceived = false;
    const runtime = new RendererRuntime({
      bridge,
      hooks: {
        onUnknownMessage: (msg) => {
          unknownReceived = true;
          assert.strictEqual(msg.type, "FUTURE_MESSAGE");
        },
      },
    });
    runtime.start();
    bridge.clear();

    // Unknown message
    bridge.emitIncoming(
      JSON.stringify({
        type: "FUTURE_MESSAGE",
        version: 1,
        timestamp: 500,
        payload: { someFutureProp: "hello" },
      }),
    );

    assert.strictEqual(unknownReceived, true);
    assert.strictEqual(bridge.sentMessages.length, 0);

    // Known message with extra fields (tolerated)
    bridge.emitIncoming(
      JSON.stringify({
        type: "SELECTION_CHANGE",
        version: 1,
        timestamp: 600,
        extraHostDebugField: "foo",
        payload: {
          selectedObjectId: "slot_extra",
          focusCamera: false,
          anotherFutureField: 42,
        },
      }),
    );

    assert.strictEqual(runtime.getStore().getState().selectedObjectId, "slot_extra");
  });

  test("malformed JSON framing: invalid JSON triggers ErrorBoundary without crashing runtime", () => {
    const bridge = new MockCefBridge();
    const runtime = new RendererRuntime({ bridge });
    runtime.start();
    bridge.clear();

    // Emit broken JSON string
    runtime.getReceiver().handleIncoming("{ invalid json :: broken");

    assert.strictEqual(bridge.sentMessages.length, 1);
    const err = bridge.getLastSentPayload<RendererErrorMessage>();
    assert.strictEqual(err.type, WIRE_TYPES.RENDERER_ERROR);
    assert.strictEqual(err.payload.code, "INVALID_MESSAGE");

    // Runtime is still alive and operational
    runtime.sendObjectClicked("test_after_error");
    assert.strictEqual(bridge.sentMessages.length, 2);
  });

  test("lifecycle: stop() unregisters listener and incoming messages after stop are ignored", () => {
    const bridge = new MockCefBridge();
    const runtime = new RendererRuntime({ bridge });
    runtime.start();
    assert.strictEqual(bridge.hasListener(), true);

    runtime.stop();
    assert.strictEqual(runtime.getStore().getState().connectionState, "DISCONNECTED");
    assert.strictEqual(bridge.hasListener(), false);

    // Incoming messages when stopped do not mutate store
    runtime.getReceiver().handleIncoming(
      JSON.stringify({
        type: "SELECTION_CHANGE",
        version: 1,
        timestamp: 900,
        payload: { selectedObjectId: "ignored_after_stop", focusCamera: false },
      }),
    );
    assert.strictEqual(runtime.getStore().getState().selectedObjectId, null);
  });

  test("lifecycle: start -> stop -> start reconnect does not duplicate listeners or messages", () => {
    const bridge = new MockCefBridge();
    const runtime = new RendererRuntime({ bridge });

    runtime.start();
    runtime.stop();
    runtime.start();

    let dispatchCount = 0;
    const initialSelected = runtime.getStore().getState().selectedObjectId;
    assert.strictEqual(initialSelected, null);

    // Track state changes
    runtime.getStore().subscribe(() => {
      dispatchCount++;
    });

    bridge.emitIncoming(
      JSON.stringify({
        type: "SELECTION_CHANGE",
        version: 1,
        timestamp: 1000,
        payload: { selectedObjectId: "reconnected_slot", focusCamera: false },
      }),
    );

    assert.strictEqual(runtime.getStore().getState().selectedObjectId, "reconnected_slot");
    // Listener only called once, proving no duplicate listener registration
    assert.strictEqual(dispatchCount, 1);
  });

  test("cross-language contract: VISUAL_STATUSES contains exact set of wire statuses", () => {
    const expected = new Set(["ONLINE", "OFFLINE", "UNBOUND"]);
    const actual = new Set(Object.values(VISUAL_STATUSES));
    assert.deepStrictEqual(actual, expected);
  });

  test("CAMERA_COMMAND: routes camera command through dispatcher and options hook exactly once", () => {
    const bridge = new MockCefBridge();
    let hookCameraCalls = 0;
    let hookReservedCalls = 0;
    let bridgeCallCount = 0;
    let bridgeReceivedPayload: unknown = null;

    const runtime = new RendererRuntime({
      bridge,
      hooks: {
        onCameraCommand: (cmd) => {
          hookCameraCalls++;
        },
        onReservedMessage: (envelope) => {
          hookReservedCalls++;
        },
      },
    });

    // Mock sceneBridge on runtime to verify handleCameraCommand invocation count
    (runtime as any).sceneBridge = {
      handleCameraCommand: (payload: unknown) => {
        bridgeCallCount++;
        bridgeReceivedPayload = payload;
      },
    };

    runtime.start();

    bridge.emitIncoming(
      JSON.stringify({
        type: "CAMERA_COMMAND",
        version: 1,
        timestamp: 1000,
        payload: {
          position: { x: 5, y: 15, z: 25 },
          target: { x: 0, y: 2, z: 0 },
          fov: 60,
        },
      }),
    );

    assert.strictEqual(bridgeCallCount, 1, "sceneBridge.handleCameraCommand must be invoked exactly once");
    assert.strictEqual(hookCameraCalls, 1, "hooks.onCameraCommand must be invoked exactly once");
    assert.strictEqual(hookReservedCalls, 0, "hooks.onReservedMessage must NOT be invoked for CAMERA_COMMAND");
    assert.deepStrictEqual(bridgeReceivedPayload, {
      position: { x: 5, y: 15, z: 25 },
      target: { x: 0, y: 2, z: 0 },
      fov: 60,
    });
  });
});
