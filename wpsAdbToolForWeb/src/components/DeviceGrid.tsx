/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React from 'react';
import { 
  Wifi, 
  Usb, 
  Monitor, 
  Link2Off, 
  Battery, 
  BatteryCharging, 
  Tv, 
  Terminal, 
  Smartphone,
  Trash2,
  RefreshCw,
  MoreVertical
} from 'lucide-react';
import { Device } from '../types';

interface DeviceGridProps {
  devices: Device[];
  filterTab: 'all' | 'physical' | 'emulators';
  searchQuery: string;
  onMirrorDevice: (device: Device) => void;
  onTerminalDevice: (device: Device) => void;
  onActionClicked: (deviceId: string, action: string) => void;
}

export default function DeviceGrid({
  devices,
  filterTab,
  searchQuery,
  onMirrorDevice,
  onTerminalDevice,
  onActionClicked
}: DeviceGridProps) {
  
  // Filter devices by tab and search term
  const filteredDevices = devices.filter((device) => {
    // Tab criteria
    if (filterTab === 'physical' && device.type !== 'physical') return false;
    if (filterTab === 'emulators' && device.type !== 'emulator') return false;
    
    // Search query criteria
    const term = searchQuery.toLowerCase();
    return (
      device.name.toLowerCase().includes(term) ||
      device.serial.toLowerCase().includes(term) ||
      device.androidVersion.toLowerCase().includes(term)
    );
  });

  const getConnectionIcon = (type: string, status: string) => {
    if (status === 'offline') {
      return <Link2Off className="w-4 h-4 text-outline" />;
    }
    switch (type) {
      case 'wifi':
        return <Wifi className="w-4 h-4 text-primary" />;
      case 'usb':
        return <Usb className="w-4 h-4 text-secondary" />;
      case 'emulator':
        return <Monitor className="w-4 h-4 text-secondary-container" />;
      default:
        return <Wifi className="w-4 h-4" />;
    }
  };

  const getBatteryIcon = (level: number, isCharging: boolean) => {
    if (isCharging) {
      return <BatteryCharging className="w-3.5 h-3.5 text-primary" />;
    }
    return <Battery className="w-3.5 h-3.5 text-on-surface-variant" />;
  };

  return (
    <div>
      {filteredDevices.length === 0 ? (
        <div className="flex flex-col items-center justify-center py-20 border border-dashed border-outline-variant rounded-xl bg-surface-container-low">
          <Smartphone className="w-12 h-12 text-outline mb-4 opacity-40 animate-pulse" />
          <p className="text-sm font-medium text-on-surface-variant">
            No matching connected Android targets found
          </p>
          <p className="text-xs text-outline mt-1 font-mono">
            Check filter parameters or pair a new wireless device.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5 gap-4">
          {filteredDevices.map((device) => {
            const isOnline = device.status === 'online';
            return (
              <div
                key={device.id}
                className={`bg-surface-container border rounded-xl overflow-hidden group relative device-card-hover ${
                  !isOnline 
                  ? 'border-error/15 opacity-70' 
                  : 'border-outline-variant'
                }`}
              >
                {/* Header info */}
                <div className="p-4 border-b border-outline-variant/30 flex justify-between items-start">
                  <div>
                    <h3 className="text-sm font-bold text-on-surface line-clamp-1">
                      {device.name}
                    </h3>
                    <p className="text-[11px] font-mono text-outline leading-tight mt-0.5">
                      {device.serial}
                    </p>
                  </div>
                  
                  <div className="flex items-center gap-2">
                    {getConnectionIcon(device.connectionType, device.status)}
                    <span 
                      className={`w-2.5 h-2.5 rounded-full ${
                        device.status === 'online'
                          ? 'bg-primary status-glow-green'
                          : device.status === 'offline'
                          ? 'bg-error status-glow-red'
                          : 'bg-yellow-400'
                      }`}
                      title={`Status: ${device.status}`}
                    />
                  </div>
                </div>

                {/* Simulated Screen Mirror Aspect Box */}
                <div className="aspect-[9/16] bg-black m-3 rounded-lg relative overflow-hidden shadow-xl border border-white/5">
                  {isOnline && device.screenshot ? (
                    <>
                      <img 
                        alt={device.screenDescription} 
                        className="w-full h-full object-cover opacity-80 group-hover:opacity-100 transition-all duration-500"
                        src={device.screenshot}
                        referrerPolicy="no-referrer"
                      />
                      {/* Top Bar Overlay inside client shadow screen */}
                      <div className="absolute top-1 left-0 right-0 px-2.5 flex justify-between items-center bg-gradient-to-b from-black/80 to-transparent pointer-events-none select-none">
                        <span className="text-[9px] font-medium font-sans text-on-surface/90">
                          10:14
                        </span>
                        <div className="flex gap-1 items-center">
                          <span className="text-[9px] font-mono text-on-surface/80">
                            {device.batteryLevel}%
                          </span>
                          <span className="text-[9px] text-[#60f99e] font-mono">
                            LTE
                          </span>
                        </div>
                      </div>

                      {/* Display app label on card */}
                      <div className="absolute bottom-2 left-2 right-2 px-2 py-1 bg-black/60 rounded backdrop-blur-sm pointer-events-none">
                        <p className="text-[10px] text-[#bbcbbc] font-sans truncate">
                          App: <span className="text-primary font-medium">{device.apps[device.currentAppIndex]?.name || 'N/A'}</span>
                        </p>
                      </div>
                    </>
                  ) : (
                    <div className="absolute inset-0 flex flex-col items-center justify-center bg-surface-container-low text-center p-4">
                      <Smartphone className="w-8 h-8 text-outline/30 mb-2" />
                      <p className="text-xs uppercase tracking-widest font-bold text-outline">
                        Device Offline
                      </p>
                      <p className="text-[10px] text-outline/65 mt-1">
                        Awaiting handshaking socket
                      </p>
                    </div>
                  )}

                  {/* HOVER OVERLAY ACTIONS */}
                  {isOnline && (
                    <div className="absolute inset-0 glass-overlay opacity-0 group-hover:opacity-100 transition-all duration-300 flex flex-col items-center justify-center gap-2.5 p-4 z-20">
                      <button
                        onClick={() => onMirrorDevice(device)}
                        className="w-full max-w-[130px] bg-primary text-on-primary py-2 px-3 rounded-full font-bold text-xs flex items-center justify-center gap-1.5 hover:scale-105 active:scale-95 transition-all shadow-md shadow-primary/20"
                      >
                        <Tv className="w-3.5 h-3.5" />
                        <span>Interactive UI</span>
                      </button>

                      <div className="flex gap-1.5 w-full justify-center">
                        <button
                          onClick={() => onTerminalDevice(device)}
                          className="p-2 bg-surface-container-highest rounded-full text-on-surface hover:text-primary hover:bg-surface-container hover:scale-105 transition-all"
                          title="Shell Terminal"
                        >
                          <Terminal className="w-4 h-4" />
                        </button>

                        <button
                          onClick={() => onActionClicked(device.id, 'reboot')}
                          className="p-2 bg-surface-container-highest rounded-full text-on-surface hover:text-primary hover:bg-surface-container hover:scale-105 transition-all"
                          title="Reboot Client"
                        >
                          <RefreshCw className="w-4 h-4" />
                        </button>

                        <button
                          onClick={() => onActionClicked(device.id, 'disconnect')}
                          className="p-2 bg-surface-container-highest rounded-full text-on-surface hover:text-error hover:bg-surface-container hover:scale-105 transition-all"
                          title="Disconnect Port"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                    </div>
                  )}
                </div>

                {/* Card Parameters Footer */}
                <div className="px-4 pb-4 space-y-3">
                  <div className="flex items-center justify-between text-xs font-sans text-on-surface-variant">
                    <span className="font-medium bg-surface-container-high px-1.5 py-0.5 rounded text-[11px]">
                      {device.androidVersion}
                    </span>
                    <div className="flex items-center gap-1">
                      {getBatteryIcon(device.batteryLevel, device.isCharging)}
                      <span className="font-mono">{isOnline ? `${device.batteryLevel}%` : '--%'}</span>
                    </div>
                  </div>

                  {/* Storage progression metrics */}
                  {isOnline ? (
                    <div className="space-y-1">
                      <div className="flex justify-between text-[10px] text-outline font-mono uppercase tracking-tighter">
                        <span>Device Storage</span>
                        <span>{device.storagePercent}%</span>
                      </div>
                      <div className="h-1 bg-surface-container-highest rounded-full overflow-hidden">
                        <div 
                          className="h-full bg-secondary rounded-full"
                          style={{ width: `${device.storagePercent}%` }}
                        />
                      </div>
                    </div>
                  ) : (
                    <div className="h-4 flex items-center">
                      <span className="text-[10px] text-error font-mono font-medium">
                        • CONNECTION CLOSED
                      </span>
                    </div>
                  )}
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
