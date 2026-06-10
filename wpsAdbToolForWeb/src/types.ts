/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

export type ConnectionType = 'wifi' | 'usb' | 'emulator';
export type DeviceStatus = 'online' | 'offline' | 'unauthorized';

export interface Device {
  id: string;
  name: string;
  serial: string;
  type: 'physical' | 'emulator';
  connectionType: ConnectionType;
  status: DeviceStatus;
  androidVersion: string;
  batteryLevel: number;
  isCharging: boolean;
  storageUsed: string;
  storageTotal: string;
  storagePercent: number;
  screenshot: string;
  screenDescription: string;
  apps: { name: string; packageName: string; icon: string }[];
  currentAppIndex: number; // For simulating screen transitions
  activityLog: string[];
}

export interface ADBLog {
  id: string;
  timestamp: string;
  tag: string;
  level: 'V' | 'D' | 'I' | 'W' | 'E';
  message: string;
  deviceId?: string;
}

export interface PairingState {
  currentStep: 1 | 2 | 3;
  tcpEnabled: boolean;
  usbConnected: boolean;
  targetIP: string;
  targetPort: string;
  isScanning: boolean;
  scanDeviceFound: boolean;
  connectionResult: 'loading' | 'success' | 'failure' | null;
}
