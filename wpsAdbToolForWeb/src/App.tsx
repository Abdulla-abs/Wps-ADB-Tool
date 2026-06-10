/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect } from 'react';
import { 
  Plus, 
  Search, 
  RefreshCw, 
  ListFilter, 
  Terminal, 
  Power,
  Volume2, 
  Play, 
  PlayCircle,
  HelpCircle,
  Code2,
  Tv,
  CheckCircle2,
  AlertTriangle
} from 'lucide-react';

import { Device, ADBLog } from './types';
import { INITIAL_DEVICES, INITIAL_LOGS } from './data';

// Component imports
import Sidebar from './components/Sidebar';
import DeviceGrid from './components/DeviceGrid';
import PairingModal from './components/PairingModal';
import MirrorDrawer from './components/MirrorDrawer';
import TerminalLogs from './components/TerminalLogs';
import KmpHub from './components/KmpHub';
import GroupManagement from './components/GroupManagement';
import SettingsPanel from './components/SettingsPanel';

export default function App() {
  const [devices, setDevices] = useState<Device[]>(INITIAL_DEVICES);
  const [logs, setLogs] = useState<ADBLog[]>(INITIAL_LOGS);
  
  // Navigation & filtration
  const [activeTab, setActiveTab] = useState<string>('wall');
  const [filterTab, setFilterTab] = useState<'all' | 'physical' | 'emulators'>('all');
  const [searchQuery, setSearchQuery] = useState<string>('');
  const [sortParam, setSortParam] = useState<'name' | 'serial' | 'battery'>('name');

  // Modals / Drawer visibility
  const [isPairingModalOpen, setIsPairingModalOpen] = useState(false);
  const [isLogTrayOpen, setIsLogTrayOpen] = useState(true);
  const [mirroredDevice, setMirroredDevice] = useState<Device | null>(null);

  // Server state parameters
  const [isAdbActive, setIsAdbActive] = useState(true);
  const [isRestartingAdb, setIsRestartingAdb] = useState(false);

  // Cascading simulation log feed helper
  const addLog = (level: 'V' | 'D' | 'I' | 'W' | 'E', tag: string, message: string, deviceId?: string) => {
    const time = new Date();
    const timestamp = time.toTimeString().split(' ')[0] + '.' + String(time.getMilliseconds()).padStart(3, '0');
    const newLog: ADBLog = {
      id: `log_${Date.now()}_${Math.random()}`,
      timestamp,
      tag,
      level,
      message,
      deviceId
    };
    setLogs((prev) => [...prev, newLog]);
  };

  // Simulated live logcat logging feed (sends harmless background diagnostic messages)
  useEffect(() => {
    if (!isAdbActive) return;

    const interval = setInterval(() => {
      const activePhys = devices.filter(d => d.status === 'online');
      if (activePhys.length === 0) return;

      const randomDev = activePhys[Math.floor(Math.random() * activePhys.length)];
      const traceMsgs = [
        `battery_service: battery properties changed (level=${randomDev.batteryLevel})`,
        `power_manager: surface_flinger update state: render layers loaded`,
        `network_diag: tcp transport heartbeat ping successful (latency: ${Math.floor(Math.random() * 20) + 5}ms)`,
        `input_reader: processed motion event window action (focus=true)`
      ];
      
      const randomMsg = traceMsgs[Math.floor(Math.random() * traceMsgs.length)];
      addLog('D', 'SysMonitor', randomMsg, randomDev.id);
    }, 7200);

    return () => clearInterval(interval);
  }, [devices, isAdbActive]);

  // Bulk APK loader action handler
  const handleApkDropSuccess = (fileName: string) => {
    addLog('I', 'ApkInstaller', `Starting broadcast ADB sideload instllation of: ${fileName}`, 'system');
    
    // Send simulated log signals for each online phone
    devices.forEach((d) => {
      if (d.status === 'online') {
        addLog('I', 'ApkInstaller', `Pushing apk bundle payload to device serial: ${d.serial}`, d.id);
        
        setTimeout(() => {
          addLog('I', 'PackageInstaller', `Package com.droid.pushed successfully installed on interface: ${d.serial}`, d.id);
        }, 1400);
      }
    });

    addLog('I', 'ApkInstaller', `Batch deployment of ${fileName} concluded successfully.`, 'system');
  };

  // Client Action triggers (Mirror, shell terminal, reboot, disconnect)
  const handleMirrorDevice = (device: Device) => {
    setMirroredDevice(device);
    addLog('I', 'MirrorService', `Starting mirroring screen broadcast socket on target: ${device.serial}`, device.id);
  };

  const handleTerminalDevice = (device: Device) => {
    setIsLogTrayOpen(true);
    addLog('I', 'AdbTerminal', `Focused shell inspection stream on device target: ${device.serial}`, device.id);
  };

  const handleClientAction = (deviceId: string, action: string) => {
    const targetDev = devices.find(d => d.id === deviceId);
    if (!targetDev) return;

    if (action === 'reboot') {
      addLog('W', 'DeviceManager', `reboot instruction piped to target client: ${targetDev.serial}`, deviceId);
      
      // Temporarily mark offline to represent power cycle!
      setDevices(prev => prev.map(d => d.id === deviceId ? { ...d, status: 'offline' } : d));
      if (mirroredDevice?.id === deviceId) {
        setMirroredDevice(null);
      }

      // Restore as online 3 seconds later
      setTimeout(() => {
        setDevices(prev => prev.map(d => d.id === deviceId ? { ...d, status: 'online', batteryLevel: Math.min(100, d.batteryLevel + 2) } : d));
        addLog('I', 'DeviceManager', `Handshake restored. Target client initialized successfully: ${targetDev.serial}`, deviceId);
      }, 2500);

    } else if (action === 'disconnect') {
      addLog('E', 'DeviceManager', `Forced disconnection socket drop on request: ${targetDev.serial}`, deviceId);
      setDevices(prev => prev.map(d => d.id === deviceId ? { ...d, status: 'offline' } : d));
      if (mirroredDevice?.id === deviceId) {
        setMirroredDevice(null);
      }
    }
  };

  // Add newly authorized wireless target
  const handleDevicePaired = (newDevice: Device) => {
    setDevices(prev => [newDevice, ...prev]);
    setIsPairingModalOpen(false);
    addLog('I', 'AdbDaemon', `Client wireless handshaking paired successfully: [${newDevice.name}]`, newDevice.id);
    addLog('I', 'DeviceWall', `${newDevice.name} added onto grid network.`, newDevice.id);
  };

  // Global ADB triggers (Kill, Restart)
  const handleKillAdb = () => {
    if (!isAdbActive) return;
    setIsAdbActive(false);
    // Crash every connected client state
    setDevices(prev => prev.map(d => ({ ...d, status: 'offline' })));
    setMirroredDevice(null);
    addLog('E', 'AdbDaemon', 'ADB transport server aborted by user action. Sockets closed.', 'system');
  };

  const handleRestartAdb = () => {
    setIsRestartingAdb(true);
    addLog('W', 'AdbDaemon', 'Resetting active socket daemon... Rebuilding network bindings.', 'system');

    setTimeout(() => {
      setIsAdbActive(true);
      setIsRestartingAdb(false);
      // Restore default devices status
      setDevices(INITIAL_DEVICES);
      addLog('I', 'AdbDaemon', 'ADB Server successfully launched (v1.0.41). Sockets listening.', 'system');
    }, 1800);
  };

  // Sorting logic based on sortParam
  const sortedDevices = [...devices].sort((a, b) => {
    if (sortParam === 'serial') {
      return a.serial.localeCompare(b.serial);
    } else if (sortParam === 'battery') {
      return b.batteryLevel - a.batteryLevel;
    }
    return a.name.localeCompare(b.name);
  });

  return (
    <div className="flex h-screen bg-background text-on-surface font-sans selection:bg-primary/30 antialiased overflow-hidden">
      
      {/* 1. COMPACT PINNED LEFT SIDEBAR */}
      <Sidebar 
        activeTab={activeTab}
        setActiveTab={setActiveTab}
        onAdpDropSuccess={handleApkDropSuccess}
        isLogTrayOpen={isLogTrayOpen}
        setIsLogTrayOpen={setIsLogTrayOpen}
        onlineDeviceCount={devices.filter(d => d.status === 'online').length}
      />

      {/* 2. MAIN SYSTEM CONTENT WRAPPER */}
      <div className="flex-1 flex flex-col ml-[240px] relative h-screen transition-all duration-300">
        
        {/* Top Header Controls bar */}
        <header className="h-16 bg-surface border-b border-outline-variant/60 flex items-center justify-between px-6 shrink-0 z-10 select-none">
          {/* Search elements and quick view tabs */}
          <div className="flex items-center gap-6 flex-1 max-w-2xl text-left">
            <div className="relative w-full max-w-xs">
              <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-outline" />
              <input 
                type="text"
                placeholder="Filter devices..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full bg-[#1a1c1f] hover:border-secondary border border-outline-variant rounded-full pl-9 pr-4 py-1.5 text-xs text-on-surface focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary transition-all placeholder:opacity-50"
              />
            </div>

            {/* In-tab scopes navigation */}
            {activeTab === 'wall' && (
              <nav className="flex items-center gap-5">
                <button 
                  onClick={() => setFilterTab('all')}
                  className={`text-xs font-semibold pb-1.5 transition-all text-left relative ${
                    filterTab === 'all' 
                      ? 'text-primary' 
                      : 'text-outline hover:text-on-surface'
                  }`}
                >
                  All Devices
                  {filterTab === 'all' && <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-primary rounded" />}
                </button>
                <button 
                  onClick={() => setFilterTab('physical')}
                  className={`text-xs font-semibold pb-1.5 transition-all text-left relative ${
                    filterTab === 'physical' 
                      ? 'text-primary' 
                      : 'text-outline hover:text-on-surface'
                  }`}
                >
                  Physical Cluster
                  {filterTab === 'physical' && <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-primary rounded" />}
                </button>
                <button 
                  onClick={() => setFilterTab('emulators')}
                  className={`text-xs font-semibold pb-1.5 transition-all text-left relative ${
                    filterTab === 'emulators' 
                      ? 'text-primary' 
                      : 'text-outline hover:text-on-surface'
                  }`}
                >
                  Emulators Bench
                  {filterTab === 'emulators' && <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-primary rounded" />}
                </button>
              </nav>
            )}
          </div>

          {/* Action button additions */}
          <div className="flex items-center gap-3">
            {activeTab === 'wall' && (
              <>
                {/* Sort drop select helper */}
                <div className="flex items-center gap-1.5 border border-outline-variant/60 rounded px-2.5 py-1.5 bg-[#1a1c1f]">
                  <span className="text-[10px] text-outline font-semibold uppercase font-sans">Sort:</span>
                  <select
                    value={sortParam}
                    onChange={(e) => setSortParam(e.target.value as any)}
                    className="bg-transparent border-0 text-xs font-bold font-sans text-primary focus:ring-0 p-0 outline-none cursor-pointer"
                  >
                    <option value="name" className="bg-[#1a1c1f] text-on-surface">Model Name</option>
                    <option value="serial" className="bg-[#1a1c1f] text-on-surface">Serial No</option>
                    <option value="battery" className="bg-[#1a1c1f] text-on-surface">Battery Life</option>
                  </select>
                </div>

                <button 
                  onClick={() => alert("Re-discovering connected TCP endpoints...")}
                  className="p-2 border border-outline-variant hover:border-primary hover:text-primary rounded-lg bg-[#1a1c1f] transition-all cursor-pointer"
                  title="Force ADB device scanning update"
                >
                  <RefreshCw className="w-3.5 h-3.5" />
                </button>
              </>
            )}

            <button 
              onClick={() => setIsPairingModalOpen(true)}
              className="bg-primary hover:bg-secondary-container hover:text-white text-on-primary text-xs font-bold py-2 px-4 rounded-full flex items-center gap-1.5 active:scale-95 transition-all shadow-md shadow-primary/15 cursor-pointer"
            >
              <Plus className="w-4 h-4 shrink-0" />
              <span>Add Wireless Device</span>
            </button>
          </div>
        </header>

        {/* 3. SCROLLABLE WORKSPACE AREA */}
        <main className={`flex-1 overflow-y-auto custom-scrollbar p-6 ${isLogTrayOpen ? 'pb-80' : 'pb-16'}`}>
          
          {/* VIEW: WALL OF DEVICES */}
          {activeTab === 'wall' && (
            <div className="space-y-4">
              <div className="flex justify-between items-center text-left">
                <div>
                  <h2 className="text-lg font-bold text-on-surface font-sans">
                    Device Wall Canvas
                  </h2>
                  <p className="text-xs text-on-surface-variant font-sans">
                    Monitor interactive screen frames and remote debug connected clusters. Hover on a card to boot terminal shell loops.
                  </p>
                </div>
              </div>

              <DeviceGrid 
                devices={sortedDevices}
                filterTab={filterTab}
                searchQuery={searchQuery}
                onMirrorDevice={handleMirrorDevice}
                onTerminalDevice={handleTerminalDevice}
                onActionClicked={handleClientAction}
              />
            </div>
          )}

          {/* VIEW: GROUP COMMAND DECK */}
          {activeTab === 'groups' && (
            <div className="space-y-4">
              <div>
                <h2 className="text-lg font-bold text-on-surface font-sans">
                  Group command deck
                </h2>
                <p className="text-xs text-on-surface-variant font-sans">
                  Execute parallel action calls (APK install, reboot, telemetry dumps) simultaneously across custom device classes.
                </p>
              </div>

              <GroupManagement 
                devices={devices}
                onBatchActionSuccess={(logLines) => {
                  logLines.forEach(line => addLog('I', 'BatchExecutor', line, 'system'));
                }}
              />
            </div>
          )}

          {/* VIEW: KOTLIN MULTIPLATFORM SOURCE BROWSING */}
          {activeTab === 'kmpCode' && (
            <KmpHub />
          )}

          {/* VIEW: CORE CONFIG GLOBAL SETTINGS */}
          {activeTab === 'settings' && (
            <SettingsPanel />
          )}

        </main>

        {/* 4. FOOTER DIAGNOSTIC BAR STATUS */}
        <footer className="h-8 bg-surface-container-lowest border-t border-outline-variant/60 flex items-center justify-between px-4 fixed bottom-0 left-[240px] right-0 z-30 select-none">
          <div className="flex items-center gap-6">
            <p className="text-[11px] font-mono text-outline flex items-center gap-1.5">
              <span className={`w-1.5 h-1.5 rounded-full ${isAdbActive ? 'bg-primary animate-pulse' : 'bg-error'}`} />
              ADB Daemon:{' '}
              <span className={`font-bold ${isAdbActive ? 'text-primary' : 'text-error'}`}>
                {isRestartingAdb ? 'REBOOTING...' : isAdbActive ? 'Active (v1.0.41)' : 'Terminated'}
              </span>
            </p>
            
            <p className="text-[11px] font-mono text-outline">
              Operational Cluster: <span className="text-primary font-bold">{devices.filter(d => d.status === 'online').length} Sockets connected</span>
            </p>
          </div>

          <div className="flex items-center gap-4.5">
            <button 
              onClick={() => setIsLogTrayOpen(!isLogTrayOpen)}
              className="text-[11px] font-mono text-outline hover:text-primary transition-colors cursor-pointer"
            >
              {isLogTrayOpen ? 'Hide Logcat Console' : 'View Logcat Logs'}
            </button>
            <span className="text-outline-variant select-none">|</span>
            
            <button 
              disabled={!isAdbActive || isRestartingAdb}
              onClick={handleKillAdb}
              className="text-[11px] font-mono text-outline hover:text-error transition-colors disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer"
            >
              Kill Daemon Server
            </button>
            <span className="text-outline-variant select-none">|</span>

            <button 
              disabled={isRestartingAdb}
              onClick={handleRestartAdb}
              className="text-[11px] font-mono text-outline hover:text-primary transition-colors disabled:opacity-30 disabled:cursor-not-allowed cursor-pointer"
            >
              Restart ADB
            </button>
          </div>
        </footer>

        {/* 5. FLOATING SENSOR SCREENS (LOGCAT STREAM & SIDE SCREEN MIRROR DRAWER) */}
        {isAdbActive && isLogTrayOpen && (
          <TerminalLogs 
            logs={logs}
            onClearLogs={() => setLogs([])}
            onAddSimulatedLog={(msg) => addLog('E', 'SimulationCrash', msg, 'system')}
            onClose={() => setIsLogTrayOpen(false)}
          />
        )}

        {mirroredDevice && (
          <MirrorDrawer 
            device={mirroredDevice}
            onClose={() => setMirroredDevice(null)}
            onUpdateDevice={(updatedDev) => {
              setDevices(prev => prev.map(d => d.id === updatedDev.id ? updatedDev : d));
              setMirroredDevice(updatedDev);
            }}
          />
        )}

        {isPairingModalOpen && (
          <PairingModal 
            onClose={() => setIsPairingModalOpen(false)}
            onDevicePaired={handleDevicePaired}
          />
        )}

      </div>
    </div>
  );
}
