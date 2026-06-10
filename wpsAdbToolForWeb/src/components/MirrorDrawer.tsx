/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from 'react';
import { 
  X, 
  Smartphone, 
  Heart, 
  CheckCircle2, 
  Settings, 
  Terminal, 
  Tv, 
  RotateCw, 
  Volume2, 
  PowerOff,
  Sparkles
} from 'lucide-react';
import { Device } from '../types';

interface MirrorDrawerProps {
  device: Device;
  onClose: () => void;
  onUpdateDevice: (device: Device) => void;
}

export default function MirrorDrawer({ device, onClose, onUpdateDevice }: MirrorDrawerProps) {
  const [activeAppIndex, setActiveAppIndex] = useState(device.currentAppIndex || 0);
  const [likesCount, setLikesCount] = useState(142);
  const [isLiked, setIsLiked] = useState(false);
  
  // Settings application variables
  const [brightness, setBrightness] = useState(78);
  const [wifiEnabled, setWifiEnabled] = useState(true);
  const [bluetoothEnabled, setBluetoothEnabled] = useState(false);
  const [developerMode, setDeveloperMode] = useState(true);

  // Device Terminal logs
  const [consoleInput, setConsoleInput] = useState('');
  const [consoleOut, setConsoleOut] = useState<string[]>(device.activityLog || []);

  const handleHeartClick = () => {
    if (isLiked) {
      setLikesCount(prev => prev - 1);
    } else {
      setLikesCount(prev => prev + 1);
    }
    setIsLiked(!isLiked);
  };

  const handleSendConsoleCmd = (cmd: string) => {
    if (!cmd.trim()) return;
    
    let reply = '';
    const cleanCmd = cmd.trim().toLowerCase();
    
    if (cleanCmd.includes('getprop')) {
      reply = `[ro.product.model]: [${device.name}]\n[ro.serialno]: [${device.serial}]\n[ro.build.version.release]: [${device.androidVersion.replace('Android ', '')}]\n[ro.product.cpu.abi]: [arm64-v8a]`;
    } else if (cleanCmd.includes('pm list packages')) {
      reply = device.apps.map(a => `package:${a.packageName}`).join('\n') + '\npackage:com.android.providers.media\npackage:com.google.android.googlequicksearchbox';
    } else if (cleanCmd.includes('dumpsys battery')) {
      reply = `Current Battery Service State:\n  AC powered: false\n  USB powered: ${device.connectionType === 'usb'}\n  status: ${device.batteryLevel === 100 ? '5' : '2'}\n  health: 2\n  present: true\n  level: ${device.batteryLevel}\n  scale: 100\n  temperature: 342`;
    } else if (cleanCmd.includes('clear')) {
      setConsoleOut([]);
      setConsoleInput('');
      return;
    } else if (cleanCmd.includes('help')) {
      reply = `Available ADB Sandbox Shell Commands:\n  getprop                Dump device parameters\n  pm list packages       List installed package files\n  dumpsys battery        Print current battery logs\n  clear                  Clear log feed`;
    } else {
      reply = `${cmd}: command not found (Try: "getprop", "dumpsys battery", "pm list packages")`;
    }

    setConsoleOut(prev => [...prev, `$ adb shell ${cmd}`, reply]);
    setConsoleInput('');
  };

  const handleToggleWifi = () => {
    const updatedState = !wifiEnabled;
    setWifiEnabled(updatedState);
    if (!updatedState && device.connectionType === 'wifi') {
      alert("Caution: Turning off Wi-Fi on an active TCP device will cause it to disconnect!");
    }
  };

  // Launch pre-fills
  const handleQuickCommand = (cmd: string) => {
    handleSendConsoleCmd(cmd);
  };

  return (
    <div className="fixed inset-y-0 right-0 w-[420px] bg-surface-container-low border-l border-outline-variant shadow-2xl z-60 flex flex-col animate-slide-in font-sans">
      
      {/* Title Header */}
      <div className="p-4 border-b border-outline-variant/40 flex items-center justify-between bg-surface-container-high">
        <div className="flex items-center gap-2">
          <Tv className="w-5 h-5 text-primary shrink-0" />
          <div>
            <h3 className="text-sm font-bold text-on-surface line-clamp-1">
              Active Screen Mirroring
            </h3>
            <p className="text-[10px] font-mono text-outline leading-none mt-1">
              {device.name} • {device.serial}
            </p>
          </div>
        </div>
        <button 
          onClick={onClose}
          className="p-1 hover:bg-surface-container-highest rounded-full transition-colors text-outline hover:text-on-surface"
        >
          <X className="w-5 h-5" />
        </button>
      </div>

      {/* Main body content scrollable */}
      <div className="flex-1 overflow-y-auto custom-scrollbar p-5 space-y-6">
        
        {/* PHYSICAL SMARTPHONE HARWARE FRAME */}
        <div className="mx-auto w-[240px] aspect-[9/18.5] bg-black rounded-[32px] p-[10px] shadow-2xl border-4 border-surface-bright relative outline outline-1 outline-outline-variant/50">
          {/* Top Camera Punchhole sensor */}
          <div className="absolute top-3.5 left-1/2 -translate-x-1/2 w-4 h-4 bg-black rounded-full border border-surface-bright/50 z-30 flex items-center justify-center">
            <div className="w-1.5 h-1.5 bg-[#0a122e] rounded-full" />
          </div>

          {/* SCREEN PANEL INNER CONTENT */}
          <div className="w-full h-full bg-surface-container-lowest rounded-[23px] overflow-hidden relative border border-white/5 flex flex-col justify-between">
            
            {/* Ambient status bar */}
            <div className="h-6 px-3 pt-1 flex justify-between items-center text-[9px] font-medium text-on-surface z-30 select-none bg-black/30 backdrop-blur-sm">
              <span>10:14</span>
              <div className="flex gap-1 items-center font-mono">
                {wifiEnabled && <span className="text-[8px] text-primary">◀ wi-fi</span>}
                <span>{device.batteryLevel}%</span>
              </div>
            </div>

            {/* SCREEN LAYOUTS */}
            <div className="flex-1 w-full relative overflow-hidden bg-[#0c1015]">
              
              {/* App 1: PhotoFlow Feed */}
              {activeAppIndex === 0 && (
                <div className="h-full flex flex-col justify-between animate-fade-in text-left">
                  <div className="p-2 border-b border-outline-variant/20 bg-black/20 flex items-center justify-between">
                    <span className="text-[10px] font-sans font-bold text-primary flex items-center gap-1">
                      <Sparkles className="w-2.5 h-2.5 text-primary" />
                      PhotoFlow Client
                    </span>
                    <span className="text-[8px] font-mono text-outline">v1.2.9</span>
                  </div>
                  
                  {/* Photo content placeholder */}
                  <div className="flex-1 relative overflow-hidden flex flex-col">
                    <img 
                      alt="feed content" 
                      className="w-full h-44 object-cover"
                      src={device.screenshot || "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=400&q=80"}
                      referrerPolicy="no-referrer"
                    />
                    
                    {/* User Profile / Comment details */}
                    <div className="p-2.5 flex-1 flex flex-col justify-between bg-gradient-to-t from-black to-transparent">
                      <div className="space-y-1 mt-auto">
                        <div className="flex items-center gap-1.5">
                          <div className="w-4 h-4 rounded-full bg-secondary text-[8px] font-sans font-black flex items-center justify-center text-on-secondary">
                            ID
                          </div>
                          <span className="text-[9px] font-bold text-on-surface">@droid-dev_cluster</span>
                        </div>
                        <p className="text-[9px] text-[#bbcbbc] leading-tight line-clamp-2">
                          Handshaking seamless compiling over Kotlin Multiplatform Compose UI desktop assets!
                        </p>
                      </div>

                      {/* Photo actions panel */}
                      <div className="flex justify-between items-center pt-2">
                        <button 
                          onClick={handleHeartClick}
                          className="flex items-center gap-1 bg-black/40 px-2 py-1 rounded hover:bg-black/60 transition-colors"
                        >
                          <Heart className={`w-3 h-3 ${isLiked ? 'text-red-500 fill-red-500' : 'text-on-surface'}`} />
                          <span className="text-[8px] text-on-surface font-mono">{likesCount}</span>
                        </button>
                        <span className="text-[7.5px] font-mono bg-primary/20 text-primary px-1.5 py-0.5 rounded uppercase leading-none">
                          Interactive Frame
                        </span>
                      </div>
                    </div>
                  </div>
                </div>
              )}

              {/* App 2: Settings App Layout */}
              {activeAppIndex === 1 && (
                <div className="h-full flex flex-col bg-[#14161a] animate-fade-in text-left">
                  <div className="p-2.5 border-b border-outline-variant/30 bg-surface-container-high flex items-center gap-1.5">
                    <Settings className="w-3.5 h-3.5 text-[#adc6ff]" />
                    <span className="text-[10px] font-sans font-bold text-[#adc6ff]">Settings</span>
                  </div>
                  
                  {/* Settings items */}
                  <div className="p-3.5 space-y-3 flex-1 overflow-y-auto custom-scrollbar">
                    
                    <div className="space-y-1">
                      <div className="flex justify-between items-center text-[9px] text-on-surface font-semibold">
                        <span>Display Brightness</span>
                        <span className="font-mono text-outline">{brightness}%</span>
                      </div>
                      <input 
                        type="range" 
                        min="10" 
                        max="100" 
                        value={brightness}
                        onChange={(e) => setBrightness(Number(e.target.value))}
                        className="w-full accent-primary bg-[#0c0e11] h-1 rounded"
                      />
                    </div>

                    <div className="border-t border-outline-variant/20 pt-2 space-y-2">
                      <div className="flex items-center justify-between">
                        <div>
                          <span className="text-[9px] text-on-surface font-semibold block leading-none">
                            Wi-Fi Interface
                          </span>
                          <span className="text-[7.5px] text-outline">Connect over 192.168.1</span>
                        </div>
                        <input 
                          type="checkbox" 
                          checked={wifiEnabled}
                          onChange={handleToggleWifi}
                          className="rounded border-[#3c4a3f] text-primary focus:ring-primary h-3.5 w-3.5 bg-black"
                        />
                      </div>

                      <div className="flex items-center justify-between">
                        <div>
                          <span className="text-[9px] text-on-surface font-semibold block leading-none">
                            Bluetooth Radio
                          </span>
                          <span className="text-[7.5px] text-outline">Search physical controllers</span>
                        </div>
                        <input 
                          type="checkbox" 
                          checked={bluetoothEnabled}
                          onChange={(e) => setBluetoothEnabled(e.target.checked)}
                          className="rounded border-[#3c4a3f] text-primary focus:ring-primary h-3.5 w-3.5 bg-black"
                        />
                      </div>

                      <div className="flex items-center justify-between">
                        <div>
                          <span className="text-[9px] text-on-surface font-semibold block leading-none">
                            Developer Settings
                          </span>
                          <span className="text-[7.5px] text-[#60f99e]">USB/TCP adb socket root</span>
                        </div>
                        <input 
                          type="checkbox" 
                          checked={developerMode}
                          onChange={(e) => setDeveloperMode(e.target.checked)}
                          className="rounded border-[#3c4a3f] text-primary focus:ring-primary h-3.5 w-3.5 bg-black"
                        />
                      </div>
                    </div>
                  </div>
                </div>
              )}
            </div>

            {/* Android bottom Soft Navigation Keys */}
            <div className="h-8.5 bg-black/60 border-t border-outline-variant/10 flex justify-around items-center px-4 shrink-0">
              <button 
                onClick={() => alert('Back gesture simulation')}
                className="w-4 h-4 flex items-center justify-center p-0.5"
              >
                <div className="w-1.5 h-2.5 border-l-2 border-b-2 border-[#bbcbbc] transform rotate-45" />
              </button>
              
              <button 
                onClick={() => setActiveAppIndex(0)}
                className="w-3.5 h-3.5 rounded-full border-2 border-[#bbcbbc]" 
              />
              
              <button 
                onClick={() => setActiveAppIndex(1)}
                className="w-3 h-3 hover:scale-105 border-2 border-[#bbcbbc] rounded-[3px]" 
              />
            </div>
          </div>
        </div>

        {/* COMPANION INTERACTIVE CHIPS */}
        <div className="space-y-2 border-t border-outline-variant/30 pt-4">
          <label className="text-[10px] font-bold uppercase tracking-wider text-outline block text-left">
            Active App Launcher
          </label>
          <div className="grid grid-cols-2 gap-2">
            <button
              onClick={() => setActiveAppIndex(0)}
              className={`py-2 px-3 rounded-lg text-xs font-semibold border flex items-center justify-center gap-1.5 transition-all ${
                activeAppIndex === 0
                  ? 'bg-primary/10 border-primary text-primary'
                  : 'bg-surface-container border-outline-variant hover:border-secondary text-on-surface-variant hover:text-on-surface'
              }`}
            >
              <Sparkles className="w-3.5 h-3.5" />
              <span>PhotoFlow Client</span>
            </button>
            
            <button
              onClick={() => setActiveAppIndex(1)}
              className={`py-2 px-3 rounded-lg text-xs font-semibold border flex items-center justify-center gap-1.5 transition-all ${
                activeAppIndex === 1
                  ? 'bg-[#adc6ff]/15 border-[#adc6ff] text-[#adc6ff]'
                  : 'bg-surface-container border-outline-variant hover:border-secondary text-on-surface-variant hover:text-on-surface'
              }`}
            >
              <Settings className="w-3.5 h-3.5" />
              <span>System Settings</span>
            </button>
          </div>
        </div>

        {/* LOCAL INTEGATED ADB TERMINAL */}
        <div className="space-y-2.5 border-t border-outline-variant/30 pt-4">
          <div className="flex items-center justify-between">
            <label className="text-[10px] font-bold uppercase tracking-wider text-outline block text-left">
              Hardware ADB Console Mock
            </label>
            <span className="text-[9px] font-mono font-bold bg-[#0c0e11] text-accent-green px-1.5 py-0.5 rounded font-mono border border-outline-variant/40">
              {device.serial}
            </span>
          </div>

          <div className="bg-[#0c0e11] border border-[#3c4a3f] rounded-lg p-3 font-mono text-left select-text relative">
            
            {/* Command quick pre-fills */}
            <div className="flex gap-1.5 mb-3 flex-wrap">
              <button 
                onClick={() => handleQuickCommand('getprop')}
                className="text-[9px] font-semibold bg-[#1a1c1f] hover:bg-secondary hover:text-on-secondary border border-outline-variant/60 px-1.5 py-0.5 rounded text-outline transition-colors"
              >
                adb getprop
              </button>
              <button 
                onClick={() => handleQuickCommand('dumpsys battery')}
                className="text-[9px] font-semibold bg-[#1a1c1f] hover:bg-secondary hover:text-on-secondary border border-outline-variant/60 px-1.5 py-0.5 rounded text-outline transition-colors"
              >
                adb dumpsys battery
              </button>
              <button 
                onClick={() => handleQuickCommand('pm list packages')}
                className="text-[9px] font-semibold bg-[#1a1c1f] hover:bg-secondary hover:text-on-secondary border border-outline-variant/60 px-1.5 py-0.5 rounded text-outline transition-colors"
              >
                list packages
              </button>
            </div>

            <div className="h-28 overflow-y-auto custom-scrollbar text-[11px] text-[#bbcbbc] space-y-1 pb-1">
              {consoleOut.length === 0 ? (
                <p className="text-outline italic text-[10px]">
                  Type shell instructions or tap quick helpers... (Try "help")
                </p>
              ) : (
                consoleOut.map((log, index) => (
                  <pre key={index} className="whitespace-pre-wrap font-mono py-0.5 border-b border-white/5 last:border-b-0 leading-tight">
                    {log}
                  </pre>
                ))
              )}
            </div>

            {/* Input terminal line */}
            <div className="flex items-center gap-1.5 border-t border-outline-variant/45 pt-2 mt-2">
              <span className="text-primary font-bold text-[11px] font-mono leading-none">
                $ adb shell
              </span>
              <input
                type="text"
                placeholder="Type command and press Enter..."
                value={consoleInput}
                onChange={(e) => setConsoleInput(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter') {
                    handleSendConsoleCmd(consoleInput);
                  }
                }}
                className="flex-grow bg-transparent border-0 focus:ring-0 p-0 text-[11px] font-mono text-white placeholder-outline-variant outline-none focus:outline-none"
              />
            </div>
          </div>
        </div>

      </div>
    </div>
  );
}
