import type { UplinkBridgeMessage } from "../../../renderer-contract/scene-bridge-contract.ts";

export interface CefJsBridgeApi {
  send(rawJson: string): void;
  onMessage(callback: ((rawJson: string) => void) | null): (() => void) | void;
}

/**
 * CefBridgeSender
 * Dedicated outbound channel responsible for serializing Uplink messages
 * and passing the raw JSON string frame to the CEF runtime.
 */
export class CefBridgeSender {
  private readonly bridge?: CefJsBridgeApi;

  constructor(bridge?: CefJsBridgeApi) {
    this.bridge = bridge;
  }

  send(message: UplinkBridgeMessage): void {
    if (!this.bridge) {
      console.warn("CefBridgeSender: bridge is not available, message dropped", message.type);
      return;
    }
    const rawJson = JSON.stringify(message);
    this.bridge.send(rawJson);
  }
}
