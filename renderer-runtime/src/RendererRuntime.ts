import {
  createObjectClickedMessage,
  createRendererReadyMessage,
  CURRENT_BRIDGE_PROTOCOL_VERSION,
  DEFAULT_RENDERER_VERSION,
} from "../../renderer-contract/scene-bridge-contract.ts";
import { CefBridgeReceiver } from "./bridge/CefBridgeReceiver.ts";
import { CefBridgeSender } from "./bridge/CefBridgeSender.ts";
import type { CefJsBridgeApi } from "./bridge/CefBridgeSender.ts";
import { ErrorBoundary } from "./bridge/ErrorBoundary.ts";
import { MessageDispatcher } from "./bridge/MessageDispatcher.ts";
import type { DispatcherHooks } from "./bridge/MessageDispatcher.ts";
import { RendererStateStore } from "./store/RendererStateStore.ts";

export interface RendererRuntimeOptions {
  bridge?: CefJsBridgeApi;
  rendererVersion?: string;
  hooks?: DispatcherHooks;
}

/**
 * RendererRuntime
 * Unified runtime facade managing bridge lifecycle, message routing,
 * state store mirroring, and failure isolation.
 */
export class RendererRuntime {
  private readonly store: RendererStateStore;
  private readonly sender: CefBridgeSender;
  private readonly errorBoundary: ErrorBoundary;
  private readonly dispatcher: MessageDispatcher;
  private readonly receiver: CefBridgeReceiver;
  private readonly rendererVersion: string;

  constructor(options: RendererRuntimeOptions = {}) {
    this.rendererVersion = options.rendererVersion ?? DEFAULT_RENDERER_VERSION;

    this.store = new RendererStateStore();
    this.sender = new CefBridgeSender(options.bridge);
    this.errorBoundary = new ErrorBoundary(this.store, this.sender);
    this.dispatcher = new MessageDispatcher(this.store, this.errorBoundary, options.hooks);
    this.receiver = new CefBridgeReceiver(options.bridge, this.dispatcher, this.errorBoundary);
  }

  /**
   * Starts the runtime, hooks inbound messages, and sends the RENDERER_READY handshake.
   */
  start(): void {
    this.store.setConnectionState("CONNECTING");
    this.receiver.connect();

    // Transmit handshake uplink to Host
    const readyMessage = createRendererReadyMessage(
      CURRENT_BRIDGE_PROTOCOL_VERSION,
      this.rendererVersion,
    );
    this.sender.send(readyMessage);

    this.store.setConnectionState("READY");
  }

  /**
   * Stops the runtime and resets connection state to DISCONNECTED.
   */
  stop(): void {
    this.receiver.disconnect();
    this.store.setConnectionState("DISCONNECTED");
  }

  /**
   * External interaction adapter hook:
   * Called by viewport / canvas interaction layers (when user clicks an object in 3D scene).
   */
  sendObjectClicked(
    objectId: string,
    screenX = 0,
    screenY = 0,
    isCtrlPressed = false,
    isShiftPressed = false,
  ): void {
    const message = createObjectClickedMessage(
      objectId,
      screenX,
      screenY,
      isCtrlPressed,
      isShiftPressed,
    );
    this.sender.send(message);
  }

  getStore(): RendererStateStore {
    return this.store;
  }

  getSender(): CefBridgeSender {
    return this.sender;
  }

  getReceiver(): CefBridgeReceiver {
    return this.receiver;
  }

  getDispatcher(): MessageDispatcher {
    return this.dispatcher;
  }

  getErrorBoundary(): ErrorBoundary {
    return this.errorBoundary;
  }
}
