import type { CefJsBridgeApi } from "../src/bridge/CefBridgeSender.ts";

export class MockCefBridge implements CefJsBridgeApi {
  public sentMessages: string[] = [];
  private messageCallback: ((rawJson: string) => void) | null = null;

  send(rawJson: string): void {
    this.sentMessages.push(rawJson);
  }

  onMessage(callback: ((rawJson: string) => void) | null): (() => void) | void {
    this.messageCallback = callback;
    return () => {
      if (this.messageCallback === callback) {
        this.messageCallback = null;
      }
    };
  }

  hasListener(): boolean {
    return this.messageCallback !== null;
  }

  emitIncoming(rawJson: string): void {
    if (!this.messageCallback) {
      throw new Error("MockCefBridge: Cannot emit incoming message, no listener registered via onMessage()");
    }
    this.messageCallback(rawJson);
  }

  getLastSentPayload<T = unknown>(): T {
    if (this.sentMessages.length === 0) {
      throw new Error("MockCefBridge: No messages have been sent");
    }
    const last = this.sentMessages[this.sentMessages.length - 1];
    return JSON.parse(last) as T;
  }

  clear(): void {
    this.sentMessages = [];
  }
}
