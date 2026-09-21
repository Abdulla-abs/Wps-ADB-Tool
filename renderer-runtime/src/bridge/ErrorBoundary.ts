import {
  BridgeErrorCodes,
  createRendererErrorMessage,
  WIRE_TYPES,
} from "../../../renderer-contract/scene-bridge-contract.ts";
import type { BridgeErrorCode } from "../../../renderer-contract/scene-bridge-contract.ts";
import { RendererStateStore } from "../store/RendererStateStore.ts";
import { CefBridgeSender } from "./CefBridgeSender.ts";

export class ErrorBoundary {
  private isEmittingError = false;
  private readonly store: RendererStateStore;
  private readonly sender: CefBridgeSender;

  constructor(
    store: RendererStateStore,
    sender: CefBridgeSender,
  ) {
    this.store = store;
    this.sender = sender;
  }

  /**
   * Captures, isolates, and reports a runtime protocol error.
   *
   * @param code Standardized BridgeErrorCode
   * @param message Human-readable error description
   * @param category Optional domain classification (e.g. "HANDSHAKE", "PARSER")
   * @param sourceMessageType Type of message that triggered the error (used for loop protection)
   */
  reportError(
    code: BridgeErrorCode,
    message: string,
    category?: string,
    sourceMessageType?: string,
  ): void {
    // 1. Record error in store for observability
    this.store.recordError(code, message);

    // 2. Loop Protection:
    // If the error was triggered by an incoming RENDERER_ERROR message,
    // or if an error is already actively being emitted, do NOT send another RENDERER_ERROR.
    if (sourceMessageType === WIRE_TYPES.RENDERER_ERROR) {
      console.warn("ErrorBoundary: Suppressed error loop for incoming RENDERER_ERROR:", message);
      return;
    }

    if (this.isEmittingError) {
      console.warn("ErrorBoundary: Suppressed recursive error emission:", message);
      return;
    }

    this.isEmittingError = true;
    try {
      const errorMessage = createRendererErrorMessage(code, message, category);
      this.sender.send(errorMessage);
    } catch (err) {
      console.error("ErrorBoundary: Failed to transmit error uplink message:", err);
    } finally {
      this.isEmittingError = false;
    }
  }

  handleParseError(rawJson: string, error: unknown): void {
    const detail = error instanceof Error ? error.message : String(error);
    this.reportError(
      BridgeErrorCodes.INVALID_MESSAGE,
      `Failed to parse incoming JSON frame: ${detail}`,
      "PARSER",
    );
  }

  handleInvalidEnvelope(raw: unknown): void {
    this.reportError(
      BridgeErrorCodes.INVALID_MESSAGE,
      `Malformed envelope structure: ${JSON.stringify(raw)}`,
      "ENVELOPE",
    );
  }

  handleVersionMismatch(envelopeVersion: number, expectedVersion: number): void {
    this.reportError(
      BridgeErrorCodes.PROTOCOL_VERSION_MISMATCH,
      `Envelope schema version mismatch: received ${envelopeVersion}, expected ${expectedVersion}`,
      "HANDSHAKE",
    );
  }

  handleInvalidPayload(type: string, details?: string): void {
    this.reportError(
      BridgeErrorCodes.INVALID_PAYLOAD,
      `Invalid payload structure for message type '${type}'${details ? `: ${details}` : ""}`,
      "VALIDATION",
      type,
    );
  }
}
