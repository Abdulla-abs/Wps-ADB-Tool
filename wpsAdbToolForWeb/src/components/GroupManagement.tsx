/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from 'react';
import { 
  Users, 
  Play, 
  Terminal, 
  Trash2, 
  RotateCw, 
  CheckCircle2, 
  AlertCircle, 
  Monitor, 
  FileUp, 
  Radio,
  Zap
} from 'lucide-react';
import { Device } from '../types';

interface GroupManagementProps {
  devices: Device[];
  onBatchActionSuccess: (logLines: string[]) => void;
}

export default function GroupManagement({ devices, onBatchActionSuccess }: GroupManagementProps) {
  const [activeGroup, setActiveGroup] = useState<'all' | 'physical' | 'emulators'>('all');
  const [runningAction, setRunningAction] = useState<string | null>(null);
  const [percent, setPercent] = useState(0);
  const [batchConsole, setBatchConsole] = useState<string[]>([]);

  const groups = [
    { id: 'all', name: 'All Connected Devices', type: 'All', count: devices.filter(d => d.status === 'online').length },
    { id: 'physical', name: 'QA Physical Hardware Cluster', type: 'Physical', count: devices.filter(d => d.status === 'online' && d.type === 'physical').length },
    { id: 'emulators', name: 'Automated Emulator Bench', type: 'Emulator', count: devices.filter(d => d.status === 'online' && d.type === 'emulator').length },
  ];

  // Get active devices belonging to current selected group
  const activeDevices = devices.filter(d => {
    if (d.status !== 'online') return false;
    if (activeGroup === 'physical') return d.type === 'physical';
    if (activeGroup === 'emulators') return d.type === 'emulator';
    return true; // 'all'
  });

  const handleRunBatchAction = (actionKey: string, actionName: string) => {
    if (activeDevices.length === 0) {
      alert("No online devices in this group to run batch directives on!");
      return;
    }

    setRunningAction(actionName);
    setPercent(0);
    setBatchConsole([`Initiating parallel cluster batch operation: ${actionName}`]);

    let currentPercent = 0;
    const interval = setInterval(() => {
      currentPercent += Math.floor(Math.random() * 20) + 12;
      
      if (currentPercent >= 100) {
        currentPercent = 100;
        clearInterval(interval);
        
        // Final success logs
        const finalLogs = [
          ...activeDevices.map(d => `[PARALLELTHREAD] adb -s ${d.serial} ${actionKey} -> Exit Code 0 (Acomplished successfully)`),
          `✓ Batch Directive [${actionName}] executed successfully across ${activeDevices.length} sockets.`
        ];
        
        setBatchConsole(prev => [...prev, ...finalLogs]);
        onBatchActionSuccess(finalLogs);
        
        setTimeout(() => {
          setRunningAction(null);
        }, 1200);
      } else {
        // Intermediate logs
        const loadingMessage = `Broadcasting instruction batch... ${currentPercent}% (pushing bits to client interfaces)`;
        setBatchConsole(prev => [...prev, loadingMessage]);
      }
      setPercent(currentPercent);
    }, 400);
  };

  return (
    <div className="grid grid-cols-1 md:grid-cols-3 gap-6 text-left font-sans">
      
      {/* Left List of Groups */}
      <div className="space-y-4">
        <h3 className="text-base font-bold text-on-surface">
          Select Target Group
        </h3>
        
        <div className="space-y-2">
          {groups.map((grp) => (
            <div
              key={grp.id}
              onClick={() => setActiveGroup(grp.id as any)}
              className={`p-4 rounded-xl border transition-all cursor-pointer select-none flex justify-between items-center ${
                activeGroup === grp.id
                  ? 'bg-primary/5 border-primary shadow-sm shadow-primary/5'
                  : 'bg-surface-container border-outline-variant hover:border-secondary'
              }`}
            >
              <div className="flex gap-3 items-center">
                <Users className={`w-5 h-5 ${activeGroup === grp.id ? 'text-primary' : 'text-outline'}`} />
                <div>
                  <span className="text-xs font-bold block">{grp.name}</span>
                  <span className="text-[10px] text-outline mt-0.5 block uppercase tracking-wider">{grp.type} Group</span>
                </div>
              </div>
              <span className="text-xs font-mono font-bold bg-[#0c0e11] px-2.5 py-1 rounded border border-outline-variant/50 text-primary">
                {grp.count} On
              </span>
            </div>
          ))}
        </div>
      </div>

      {/* Right Batch Controls Panel */}
      <div className="md:col-span-2 space-y-4 flex flex-col justify-between">
        <div className="space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="text-base font-bold text-on-surface flex items-center gap-2">
              <Zap className="w-4 h-4 text-primary shrink-0" />
              <span>Batch Command Deck ({activeDevices.length} Target Sockets)</span>
            </h3>
            <span className="text-[10px] text-outline font-mono uppercase bg-surface-container px-2 py-0.5 rounded border border-outline-variant/30">
              Parallel Threading Ready
            </span>
          </div>

          <div className="p-4 bg-surface-container border border-outline-variant rounded-xl grid grid-cols-2 gap-3">
            <button
              disabled={!!runningAction}
              onClick={() => handleRunBatchAction('install-package com.droid.update', 'Batch Install Update APK')}
              className="p-3.5 bg-surface-container-low hover:bg-primary hover:text-on-primary hover:scale-[1.01] rounded-lg border border-outline-variant flex flex-col items-center justify-center gap-2 text-center transition-all cursor-pointer font-bold disabled:opacity-40 disabled:cursor-not-allowed group"
            >
              <FileUp className="w-5 h-5 text-secondary group-hover:text-inherit" />
              <div>
                <span className="text-xs font-bold block">Batch Sideload APK</span>
                <span className="text-[10px] font-medium text-outline group-hover:text-inherit block mt-0.5 leading-none">
                  Injects APK package file in Parallel
                </span>
              </div>
            </button>

            <button
              disabled={!!runningAction}
              onClick={() => handleRunBatchAction('shell pm clear com.android.settings', 'Wipe App Cache Storage')}
              className="p-3.5 bg-surface-container-low hover:bg-primary hover:text-on-primary hover:scale-[1.01] rounded-lg border border-outline-variant flex flex-col items-center justify-center gap-2 text-center transition-all cursor-pointer font-bold disabled:opacity-40 disabled:cursor-not-allowed group"
            >
              <Trash2 className="w-5 h-5 text-secondary group-hover:text-inherit" />
              <div>
                <span className="text-xs font-bold block">Wipe Cache Storage</span>
                <span className="text-[10px] font-medium text-outline group-hover:text-inherit block mt-0.5 leading-none">
                  Triggers PM package sweep
                </span>
              </div>
            </button>

            <button
              disabled={!!runningAction}
              onClick={() => handleRunBatchAction('reboot', 'Batch Reboot Devices')}
              className="p-3.5 bg-surface-container-low hover:bg-[#ff556c] hover:text-white hover:scale-[1.01] rounded-lg border border-outline-variant flex flex-col items-center justify-center gap-2 text-center transition-all cursor-pointer font-bold disabled:opacity-40 disabled:cursor-not-allowed group"
            >
              <RotateCw className="w-5 h-5 text-secondary group-hover:text-inherit" />
              <div>
                <span className="text-xs font-bold block">Batch Reboot Sockets</span>
                <span className="text-[10px] font-medium text-outline group-hover:text-inherit block mt-0.5 leading-none">
                  Forced cold restarts daemon
                </span>
              </div>
            </button>

            <button
              disabled={!!runningAction}
              onClick={() => handleRunBatchAction('shell dumpsys battery', 'Fetch Cluster Batteries')}
              className="p-3.5 bg-surface-container-low hover:bg-primary hover:text-on-primary hover:scale-[1.01] rounded-lg border border-outline-variant flex flex-col items-center justify-center gap-2 text-center transition-all cursor-pointer font-bold disabled:opacity-40 disabled:cursor-not-allowed group"
            >
              <Radio className="w-5 h-5 text-secondary group-hover:text-inherit" />
              <div>
                <span className="text-xs font-bold block">Get Battery Telemetry</span>
                <span className="text-[10px] font-medium text-outline group-hover:text-inherit block mt-0.5 leading-none">
                  Pulls power diagnostic metrics
                </span>
              </div>
            </button>
          </div>
        </div>

        {/* Dynamic Process Bar or Terminal outputs */}
        <div className="space-y-2 mt-4 flex-1 flex flex-col justify-end">
          {runningAction && (
            <div className="space-y-1 text-left pt-2">
              <div className="flex justify-between items-center text-xs font-sans text-[#bbcbbc]">
                <span>In-progress: <strong className="text-primary">{runningAction}</strong></span>
                <span className="font-mono">{percent}% Compiled</span>
              </div>
              <div className="h-2 bg-[#0c0e11] rounded-full overflow-hidden border border-outline-variant/40">
                <div 
                  className="h-full bg-primary rounded-full transition-all duration-300" 
                  style={{ width: `${percent}%` }}
                />
              </div>
            </div>
          )}

          {/* Running console reports */}
          <div className="bg-[#0c0e11] border border-outline-variant rounded-xl p-4 font-mono h-36 overflow-y-auto custom-scrollbar flex flex-col gap-1 text-left">
            {batchConsole.length === 0 ? (
              <p className="text-[11px] text-zinc-600 font-mono italic">
                Awaiting batch executor commands... Terminal active.
              </p>
            ) : (
              batchConsole.map((line, idx) => {
                const isSuccess = line.startsWith('✓') || line.includes('Exit Code 0');
                const isHeading = line.startsWith('Initiating');
                return (
                  <pre 
                    key={idx} 
                    className={`text-[10.5px] font-mono leading-tight whitespace-pre-wrap ${
                      isSuccess 
                        ? 'text-[#60f99e] font-semibold' 
                        : isHeading 
                        ? 'text-[#adc6ff] font-bold' 
                        : 'text-[#bbcbbc]'
                    }`}
                  >
                    {line}
                  </pre>
                );
              })
            )}
          </div>
        </div>

      </div>
    </div>
  );
}
