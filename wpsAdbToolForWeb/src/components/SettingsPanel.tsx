/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from 'react';
import { 
  Settings, 
  Save, 
  HelpCircle, 
  ShieldAlert, 
  Cpu, 
  Sliders, 
  Terminal,
  CheckCircle2
} from 'lucide-react';

export default function SettingsPanel() {
  const [adbPath, setAdbPath] = useState('/usr/local/bin/adb');
  const [minPort, setMinPort] = useState('5555');
  const [maxPort, setMaxPort] = useState('5585');
  const [scanInterval, setScanInterval] = useState(15);
  const [parallelThreads, setParallelThreads] = useState(4);
  const [logRetention, setLogRetention] = useState(2500);
  
  const [autoApproveKey, setAutoApproveKey] = useState(true);
  const [diagnosticTelemetry, setDiagnosticTelemetry] = useState(false);
  const [showToast, setShowToast] = useState(false);

  const handleSave = (e: React.FormEvent) => {
    e.preventDefault();
    setShowToast(true);
    setTimeout(() => setShowToast(false), 3000);
  };

  return (
    <form onSubmit={handleSave} className="space-y-6 text-left max-w-3xl font-sans relative">
      
      {/* Toast Alert bar */}
      {showToast && (
        <div className="fixed top-4 right-4 bg-primary text-on-primary font-sans font-bold text-xs py-2.5 px-4 rounded-lg shadow-xl flex items-center gap-2 z-100 animate-slide-in">
          <CheckCircle2 className="w-4 h-4" />
          <span>Local ADB configurations saved successfully!</span>
        </div>
      )}

      {/* Header text */}
      <div className="space-y-1">
        <div className="flex items-center gap-2">
          <Settings className="w-5 h-5 text-primary shrink-0" />
          <h2 className="text-xl font-bold text-on-surface">ADB Global Configuration Settings</h2>
        </div>
        <p className="text-xs text-on-surface-variant">
          Adjust environmental environment variables, local binary maps, parallel diagnostic workers, and subnet scanner behaviors.
        </p>
      </div>

      {/* Grid segments */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4 pt-1">
        
        {/* Card 1: Port maps */}
        <div className="p-4 bg-surface-container border border-outline-variant rounded-xl space-y-4">
          <h4 className="text-xs font-bold text-on-surface flex items-center gap-2 uppercase tracking-wide">
            <Sliders className="w-4 h-4 text-secondary shrink-0" />
            <span>Transport Bindings</span>
          </h4>

          <div className="space-y-3">
            <div className="space-y-1">
              <label className="text-[10px] uppercase font-bold tracking-wider text-outline block">
                Local ADB executable binary path
              </label>
              <input 
                type="text"
                value={adbPath}
                onChange={(e) => setAdbPath(e.target.value)}
                className="w-full bg-surface-container-lowest border border-outline-variant focus:border-primary focus:ring-1 focus:ring-primary focus:outline-none rounded px-3 py-2 font-mono text-xs text-[#bbcbbc] transition-all"
              />
            </div>

            <div className="grid grid-cols-2 gap-3.5 pt-1">
              <div className="space-y-1">
                <label className="text-[10px] uppercase font-bold tracking-wider text-outline block">
                  Adb Scopes Start Port
                </label>
                <input 
                  type="text"
                  value={minPort}
                  onChange={(e) => setMinPort(e.target.value)}
                  className="w-full bg-surface-container-lowest border border-outline-variant focus:border-primary focus:outline-none rounded px-3 py-2 font-mono text-xs text-[#bbcbbc] text-center"
                />
              </div>

              <div className="space-y-1">
                <label className="text-[10px] uppercase font-bold tracking-wider text-outline block">
                  Adb Scopes End Port
                </label>
                <input 
                  type="text"
                  value={maxPort}
                  onChange={(e) => setMaxPort(e.target.value)}
                  className="w-full bg-surface-container-lowest border border-outline-variant focus:border-primary focus:outline-none rounded px-3 py-2 font-mono text-xs text-[#bbcbbc] text-center"
                />
              </div>
            </div>
          </div>
        </div>

        {/* Card 1: Performance allocations */}
        <div className="p-4 bg-surface-container border border-outline-variant rounded-xl space-y-4">
          <h4 className="text-xs font-bold text-on-surface flex items-center gap-2 uppercase tracking-wide">
            <Cpu className="w-4 h-4 text-secondary shrink-0" />
            <span>Parallel Allocation Metrics</span>
          </h4>

          <div className="space-y-4">
            <div className="space-y-1">
              <div className="flex justify-between items-center text-[10px] uppercase font-bold tracking-wider text-outline">
                <span>Subnet Scan Interval</span>
                <span className="font-mono text-primary font-bold">{scanInterval}s</span>
              </div>
              <input 
                type="range"
                min="5"
                max="60"
                step="5"
                value={scanInterval}
                onChange={(e) => setScanInterval(Number(e.target.value))}
                className="w-full h-1 bg-surface-container-lowest accent-primary rounded-lg"
              />
              <p className="text-[8px] text-[#869587]">
                Defines timing buffers when ping-reconnoitering local TCP hardware profiles.
              </p>
            </div>

            <div className="space-y-1">
              <div className="flex justify-between items-center text-[10px] uppercase font-bold tracking-wider text-outline">
                <span>Max thread concurrency</span>
                <span className="font-mono text-primary font-bold">{parallelThreads} Thr</span>
              </div>
              <input 
                type="range"
                min="1"
                max="8"
                value={parallelThreads}
                onChange={(e) => setParallelThreads(Number(e.target.value))}
                className="w-full h-1 bg-surface-container-lowest accent-primary rounded-lg"
              />
              <p className="text-[8px] text-[#869587]">
                Pipes simultaneous pipelines for automated batch installer configurations.
              </p>
            </div>
          </div>
        </div>

        {/* Card 2: Logging configurations */}
        <div className="p-4 bg-surface-container border border-outline-variant rounded-xl space-y-4 md:col-span-2">
          <h4 className="text-xs font-bold text-on-surface flex items-center gap-2 uppercase tracking-wide">
            <Terminal className="w-4 h-4 text-secondary shrink-0" />
            <span>Developer Sandbox Security</span>
          </h4>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div className="space-y-3.5">
              <div className="flex items-center justify-between">
                <div>
                  <span className="text-xs text-on-surface font-semibold block leading-none">
                    Auto-Trust RSA Handshake Keys
                  </span>
                  <span className="text-[10px] text-outline mt-0.5 block leading-normal">
                    Skips ADB physical prompt verification on subsequent mounts
                  </span>
                </div>
                <input 
                  type="checkbox" 
                  checked={autoApproveKey}
                  onChange={(e) => setAutoApproveKey(e.target.checked)}
                  className="rounded border-[#3c4a3f] text-primary focus:ring-primary h-4.5 w-4.5 bg-black"
                />
              </div>

              <div className="flex items-center justify-between">
                <div>
                  <span className="text-xs text-on-surface font-semibold block leading-none">
                    Gather Crash Telemetry reports
                  </span>
                  <span className="text-[10px] text-outline mt-0.5 block leading-normal">
                    Transfers logs to main core engine to enhance diagnostics
                  </span>
                </div>
                <input 
                  type="checkbox" 
                  checked={diagnosticTelemetry}
                  onChange={(e) => setDiagnosticTelemetry(e.target.checked)}
                  className="rounded border-[#3c4a3f] text-primary focus:ring-primary h-4.5 w-4.5 bg-black"
                />
              </div>
            </div>

            <div className="space-y-2">
              <div className="space-y-1">
                <label className="text-[10px] uppercase font-bold tracking-wider text-outline block">
                  Logcat Line Retention cache limit
                </label>
                <input 
                  type="number"
                  value={logRetention}
                  onChange={(e) => setLogRetention(Number(e.target.value))}
                  className="w-full bg-surface-container-lowest border border-outline-variant focus:border-primary focus:outline-none rounded px-3 py-2 font-mono text-xs text-[#bbcbbc]"
                />
                <p className="text-[8px] text-[#869587]">
                  Caps memory footprint within browser storage during extended streaming scopes.
                </p>
              </div>
            </div>
          </div>
        </div>

      </div>

      {/* Trigger button */}
      <div className="pt-4 flex justify-end">
        <button
          type="submit"
          className="bg-primary text-on-primary py-3 px-6 rounded-lg text-xs font-bold font-sans flex items-center justify-center gap-2 hover:brightness-110 active:scale-[0.98] transition-all shadow-md shadow-primary/10 cursor-pointer"
        >
          <Save className="w-4 h-4" />
          <span>Save Changes to Environmental Manifest</span>
        </button>
      </div>

    </form>
  );
}
