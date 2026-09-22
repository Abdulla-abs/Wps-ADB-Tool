import { test, describe } from "node:test";
import assert from "node:assert";
import { MessageDispatcher } from "../src/bridge/MessageDispatcher.ts";
import { RendererStateStore } from "../src/store/RendererStateStore.ts";
import { ErrorBoundary } from "../src/bridge/ErrorBoundary.ts";
import { CefBridgeSender } from "../src/bridge/CefBridgeSender.ts";
import { MockCefBridge } from "./MockCefBridge.ts";
import { WIRE_TYPES } from "../../renderer-contract/scene-bridge-contract.ts";

describe("MessageDispatcher Unit Tests", () => {
  test("dispatcher isolation: non-envelope or invalid JSON does not crash and leaves store untouched", () => {
    const bridge = new MockCefBridge();
    const store = new RendererStateStore();
    const sender = new CefBridgeSender(bridge);
    const errorBoundary = new ErrorBoundary(store, sender);
    const dispatcher = new MessageDispatcher(store, errorBoundary);

    store.updateSelection("stable_selection");

    // Pass invalid envelope (primitive)
    dispatcher.dispatch("not an envelope object");
    assert.strictEqual(store.getState().selectedObjectId, "stable_selection");
    assert.strictEqual(bridge.sentMessages.length, 1);

    // Pass envelope missing version
    dispatcher.dispatch({ type: "SCENE_INIT", timestamp: 123, payload: {} });
    assert.strictEqual(store.getState().selectedObjectId, "stable_selection");
    assert.strictEqual(bridge.sentMessages.length, 2);
  });

  test("version mismatch triggers error boundary without mutating store", () => {
    const bridge = new MockCefBridge();
    const store = new RendererStateStore();
    const sender = new CefBridgeSender(bridge);
    const errorBoundary = new ErrorBoundary(store, sender);
    const dispatcher = new MessageDispatcher(store, errorBoundary);

    dispatcher.dispatch({
      type: "SCENE_INIT",
      version: 999, // Incompatible envelope version
      timestamp: 1234,
      payload: {},
    });

    assert.strictEqual(store.getState().activeScene, null);
    assert.strictEqual(bridge.sentMessages.length, 1);

    const err = bridge.getLastSentPayload<{ payload: { code: string } }>();
    assert.strictEqual(err.payload.code, "PROTOCOL_VERSION_MISMATCH");
  });

  test("reserved message hooks are dispatched without mutating store", () => {
    const bridge = new MockCefBridge();
    const store = new RendererStateStore();
    const sender = new CefBridgeSender(bridge);
    const errorBoundary = new ErrorBoundary(store, sender);

    let reservedHookCalled = false;
    const dispatcher = new MessageDispatcher(store, errorBoundary, {
      onReservedMessage: (envelope) => {
        if (envelope.type === WIRE_TYPES.BINDING_UPDATE) {
          reservedHookCalled = true;
        }
      },
    });

    dispatcher.dispatch({
      type: WIRE_TYPES.BINDING_UPDATE,
      version: 1,
      timestamp: 1234,
      payload: { dummy: true },
    });

    assert.strictEqual(reservedHookCalled, true);
    assert.strictEqual(bridge.sentMessages.length, 0);
  });

  test("CAMERA_COMMAND: valid payload invokes onCameraCommand hook", () => {
    const bridge = new MockCefBridge();
    const store = new RendererStateStore();
    const sender = new CefBridgeSender(bridge);
    const errorBoundary = new ErrorBoundary(store, sender);

    let receivedCameraCommand: unknown = null;
    const dispatcher = new MessageDispatcher(store, errorBoundary, {
      onCameraCommand: (payload) => {
        receivedCameraCommand = payload;
      },
    });

    dispatcher.dispatch({
      type: WIRE_TYPES.CAMERA_COMMAND,
      version: 1,
      timestamp: 1234,
      payload: {
        position: { x: 10, y: 20, z: 30 },
        target: { x: 0, y: 1, z: 2 },
        fov: 50,
      },
    });

    assert.deepStrictEqual(receivedCameraCommand, {
      position: { x: 10, y: 20, z: 30 },
      target: { x: 0, y: 1, z: 2 },
      fov: 50,
    });
    assert.strictEqual(bridge.sentMessages.length, 0);
  });

  test("CAMERA_COMMAND: invalid payload triggers ErrorBoundary", () => {
    const bridge = new MockCefBridge();
    const store = new RendererStateStore();
    const sender = new CefBridgeSender(bridge);
    const errorBoundary = new ErrorBoundary(store, sender);

    let receivedCameraCommand: unknown = null;
    const dispatcher = new MessageDispatcher(store, errorBoundary, {
      onCameraCommand: (payload) => {
        receivedCameraCommand = payload;
      },
    });

    dispatcher.dispatch({
      type: WIRE_TYPES.CAMERA_COMMAND,
      version: 1,
      timestamp: 1234,
      payload: {
        position: { x: "invalid" }, // Missing target and invalid position
      },
    });

    assert.strictEqual(receivedCameraCommand, null);
    assert.strictEqual(bridge.sentMessages.length, 1);
    const err = bridge.getLastSentPayload<{ payload: { code: string; message: string } }>();
    assert.strictEqual(err.payload.code, "INVALID_PAYLOAD");
  });
});
