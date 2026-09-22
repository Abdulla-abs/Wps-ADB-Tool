import { RendererRuntime } from "./RendererRuntime.ts";
import type { CefJsBridgeApi } from "./bridge/CefBridgeSender.ts";

console.log("[RendererRuntimeEntry] Starting official RendererRuntime...");

// 1. Setup CefJsBridgeApi adapter to window.cefQuery
const bridge: CefJsBridgeApi = {
  send(rawJson: string): void {
    const w = window as any;
    if (typeof w.cefQuery === "function") {
      w.cefQuery({
        request: rawJson,
        persistent: false,
        onSuccess: () => {},
        onFailure: (code: number, msg: string) => {
          console.error("[RendererRuntime] Bridge send failure:", code, msg);
        },
      });
    } else {
      console.warn("[RendererRuntime] window.cefQuery not available, message dropped");
    }
  },
  onMessage(callback: ((rawJson: string) => void) | null): (() => void) | void {
    const w = window as any;
    w.__cefMessageListener = callback;
    return () => {
      w.__cefMessageListener = null;
    };
  },
};

// Expose on window for diagnostics and inspection
(window as any).cefBridge = bridge;

// 2. Initialize and configure RendererRuntime
const runtime = new RendererRuntime({ bridge });
(window as any).__wpsRendererRuntime = runtime;

// Mount 3D Scene to container
const container = document.getElementById("canvas-container");
if (container) {
  runtime.mount(container);
  console.log("[RendererRuntimeEntry] 3D DeviceSceneRenderer mounted to #canvas-container");
}

// Wire HUD display to reactive store changes
runtime.getStore().subscribe((state) => {
  const statusEl = document.getElementById("hud-status");
  if (statusEl) {
    statusEl.innerHTML = `<b>Status:</b> ${state.connectionState}`;
  }
  const sceneEl = document.getElementById("hud-scene");
  if (sceneEl) {
    sceneEl.innerHTML = `<b>Scene:</b> ${state.activeScene?.name ?? "None"}`;
  }
});

// 3. Start runtime: hooks receiver and sends RENDERER_READY handshake!
runtime.start();
console.log("[RendererRuntimeEntry] RendererRuntime started, RENDERER_READY handshake sent.");
