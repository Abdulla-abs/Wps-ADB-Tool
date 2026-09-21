import type { CefJsBridgeApi } from "./CefBridgeSender.ts";
import { ErrorBoundary } from "./ErrorBoundary.ts";
import { MessageDispatcher } from "./MessageDispatcher.ts";

/**
 * CefBridgeReceiver
 * Dedicated inbound channel responsible for listening to raw string frames from CEF,
 * parsing JSON, and delegating to MessageDispatcher.
 */
export class CefBridgeReceiver {
  private isConnected = false;
  private unsubscribe: (() => void) | null = null;
  private readonly bridge: CefJsBridgeApi | undefined;
  private readonly dispatcher: MessageDispatcher;
  private readonly errorBoundary: ErrorBoundary;

  constructor(
    bridge: CefJsBridgeApi | undefined,
    dispatcher: MessageDispatcher,
    errorBoundary: ErrorBoundary,
  ) {
    this.bridge = bridge;
    this.dispatcher = dispatcher;
    this.errorBoundary = errorBoundary;
  }

  connect(): void {
    if (this.isConnected) return;
    if (!this.bridge) {
      console.warn("CefBridgeReceiver: bridge is not available, cannot connect");
      return;
    }

    const unsub = this.bridge.onMessage((rawJson: string) => {
      this.handleIncoming(rawJson);
    });
    if (typeof unsub === "function") {
      this.unsubscribe = unsub;
    }

    this.isConnected = true;
  }

  disconnect(): void {
    this.isConnected = false;
    if (this.unsubscribe) {
      this.unsubscribe();
      this.unsubscribe = null;
    } else if (this.bridge && typeof this.bridge.onMessage === "function") {
      try {
        this.bridge.onMessage(null);
      } catch {
        // Safe fallback
      }
    }
  }

  isAttached(): boolean {
    return this.isConnected;
  }

  /**
   * Internal parser and router for raw inbound JSON frames.
   */
  handleIncoming(rawJson: string): void {
    if (!this.isConnected) {
      return;
    }
    if (typeof rawJson !== "string" || rawJson.trim().length === 0) {
      return;
    }

    let parsed: unknown;
    try {
      parsed = JSON.parse(rawJson);
    } catch (err) {
      this.errorBoundary.handleParseError(rawJson, err);
      return;
    }

    this.dispatcher.dispatch(parsed);
  }
}
