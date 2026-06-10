/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from 'react';
import { 
  X, 
  Radio, 
  Usb, 
  Terminal, 
  ArrowRight, 
  Radar, 
  Loader2, 
  CheckCircle2, 
  AlertTriangle,
  ArrowLeft,
  Smartphone
} from 'lucide-react';
import { Device } from '../types';

interface PairingModalProps {
  onClose: () => void;
  onDevicePaired: (newDevice: Device) => void;
}

export default function PairingModal({ onClose, onDevicePaired }: PairingModalProps) {
  const [currentStep, setCurrentStep] = useState<1 | 2 | 3>(1);
  const [usbConnected, setUsbConnected] = useState(false);
  const [tcpEnabled, setTcpEnabled] = useState(false);
  
  const [ipAddress, setIpAddress] = useState('');
  const [port, setPort] = useState('5555');
  
  const [isScanning, setIsScanning] = useState(false);
  const [scanResult, setScanResult] = useState<{ name: string; ip: string } | null>(null);
  
  const [connectingState, setConnectingState] = useState<'idle' | 'loading' | 'success' | 'failure'>('idle');

  // Trigger simulated local network scan
  const handleAutoScan = () => {
    setIsScanning(true);
    setScanResult(null);
    
    setTimeout(() => {
      setIsScanning(false);
      const foundIp = '192.168.1.105';
      setScanResult({
        name: 'Pixel 7 Pro',
        ip: foundIp
      });
      setIpAddress(foundIp);
    }, 1800);
  };

  // Launch simulated socket connection
  const handleConnectDevice = () => {
    setCurrentStep(3);
    setConnectingState('loading');
    
    setTimeout(() => {
      // 80% success mock
      if (ipAddress.trim().length > 0) {
        setConnectingState('success');
      } else {
        setConnectingState('failure');
      }
    }, 2400);
  };

  // Dispatch final device insertion
  const handleFinalize = () => {
    const pairedDevice: Device = {
      id: `paired_pixel7_${Date.now()}`,
      name: scanResult?.name || 'Pixel 7 Pro (Wireless)',
      serial: 'P7_WRLS_' + Math.floor(Math.random() * 900000 + 100000).toString(),
      type: 'physical',
      connectionType: 'wifi',
      status: 'online',
      androidVersion: 'Android 13',
      batteryLevel: 94,
      isCharging: false,
      storageUsed: '45GB',
      storageTotal: '128GB',
      storagePercent: 35,
      screenshot: 'https://lh3.googleusercontent.com/aida-public/AB6AXuArxeJhmFHsT2MVqW446qyOJToROLQXO4X_AiTgP-v5qTgDbRfIroiXMqTC-dkOh0WNuBYOkKnD4XrXSzHeNvL86yqS8ftj-FMtrPL5zuK8BU-yAp8IybcWtTqRL9aEeBKBCKG-gvcG7DHilUVRkFCVkhJT5a2DzOa3bLXvnOXzQ3-WmEvok5s9UK6lkVHHODGYHqor_rEQ7xmH7lovV6pjohHrhDgMBjsAWshByn9l4cSvY9LkurlK9MwRtC1En4Y-50c2IWbVc0Fc', // Hotlink
      screenDescription: 'Paired device system homepage UI',
      apps: [
        { name: 'PhotoFlow Feed', packageName: 'com.android.photoflow', icon: 'photo_library' },
        { name: 'Developer Diagnostics SDK', packageName: 'org.droid.diag', icon: 'dashboard' }
      ],
      currentAppIndex: 0,
      activityLog: [
        'handshake: Authenticated successful using SHA256 key pairing.',
        'adb_daemon: Executing tcp remote transport service binding on 5555',
        'activity_manager: Activity forced back priority setup'
      ]
    };
    onDevicePaired(pairedDevice);
  };

  return (
    <div className="fixed inset-0 z-100 flex items-center justify-center glass-overlay select-none animate-fade-in duration-200">
      <div className="w-full max-w-2xl carbon-surface bg-[#1e2023] border border-[#3c4a3f] rounded-xl shadow-[0_20px_50px_rgba(0,0,0,0.5)] overflow-hidden flex flex-col scale-100 transition-all duration-300">
        
        {/* Modal Header */}
        <div className="px-6 py-4.5 border-b border-outline-variant/40 flex items-center justify-between bg-surface-container-high">
          <div className="flex items-center gap-3">
            <Radio className="w-5 h-5 text-primary animate-pulse" />
            <h2 className="text-lg font-bold text-on-surface font-sans">
              Add Wireless Device
            </h2>
          </div>
          <button 
            onClick={onClose} 
            className="p-1 text-outline hover:text-on-surface hover:bg-surface-container-highest rounded-full transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Modal Body columns */}
        <div className="flex flex-1 min-h-[360px]">
          {/* Left Sidebar Steps */}
          <div className="w-60 bg-surface-container-low border-r border-outline-variant/40 p-6 flex flex-col gap-6">
            
            {/* Step 01 Prepare */}
            <div className={`flex gap-3 items-start ${currentStep === 1 ? 'step-active' : 'step-inactive text-outline'}`}>
              <div className={`w-5.5 h-5.5 rounded-full border flex items-center justify-center text-[10px] font-mono font-bold shrink-0 ${
                currentStep > 1 ? 'bg-primary/10 border-primary text-primary' : 'border-current'
              }`}>
                {currentStep > 1 ? '✓' : '01'}
              </div>
              <div className="text-left">
                <p className={`text-xs font-bold ${currentStep === 1 ? 'text-primary' : 'text-on-surface-variant'}`}>Prepare</p>
                <p className="text-[10px] text-outline leading-tight mt-0.5">Connect via USB & Enable TCP</p>
              </div>
            </div>

            {/* Step 02 Connect */}
            <div className={`flex gap-3 items-start ${currentStep === 2 ? 'step-active' : 'step-inactive'}`}>
              <div className={`w-5.5 h-5.5 rounded-full border flex items-center justify-center text-[10px] font-mono font-bold shrink-0 ${
                currentStep > 2 ? 'bg-primary/10 border-primary text-primary' : 'border-current'
              }`}>
                {currentStep > 2 ? '✓' : '02'}
              </div>
              <div className="text-left">
                <p className={`text-xs font-bold ${currentStep === 2 ? 'text-primary' : 'text-on-surface-variant'}`}>Connect</p>
                <p className="text-[10px] text-outline leading-tight mt-0.5">Enter IP & Port</p>
              </div>
            </div>

            {/* Step 03 Finalize */}
            <div className={`flex gap-3 items-start ${currentStep === 3 ? 'step-active' : 'step-inactive'}`}>
              <div className="w-5.5 h-5.5 rounded-full border flex items-center justify-center text-[10px] font-mono font-bold shrink-0 border-current">
                03
              </div>
              <div className="text-left">
                <p className={`text-xs font-bold ${currentStep === 3 ? 'text-primary' : 'text-on-surface-variant'}`}>Finalize</p>
                <p className="text-[10px] text-outline leading-tight mt-0.5">Verifying Connection</p>
              </div>
            </div>
          </div>

          {/* Right Dashboard Content */}
          <div className="flex-1 p-6 flex flex-col justify-between">
            
            {/* VIEW STEP 1: Preparation */}
            {currentStep === 1 && (
              <div className="space-y-4 text-left flex-1 flex flex-col justify-between">
                <div className="space-y-3">
                  <h3 className="text-base font-bold text-on-surface">
                    Initialize Debugging Mode
                  </h3>
                  <p className="text-xs text-on-surface-variant leading-relaxed">
                    Wireless ADB requires a one-time hand-shaking authorization over a local physical connection to securely establish TCP/IP access.
                  </p>

                  {/* Checklist cards mapped exactly with triggers! */}
                  <div className="space-y-2.5 pt-2">
                    <div 
                      onClick={() => setUsbConnected(!usbConnected)}
                      className={`flex items-center gap-3.5 p-3 rounded-lg border transition-all cursor-pointer select-none bg-surface-container-lowest ${
                        usbConnected 
                          ? 'border-primary bg-primary/5' 
                          : 'border-outline-variant hover:border-secondary'
                      }`}
                    >
                      <Usb className={`w-5 h-5 ${usbConnected ? 'text-primary' : 'text-outline'}`} />
                      <div className="flex-1">
                        <span className="text-xs font-semibold block">1. Connect device via USB</span>
                        <span className="text-[10px] text-outline">Click to tag physical link active</span>
                      </div>
                      <input 
                        type="checkbox" 
                        checked={usbConnected} 
                        readOnly
                        className="rounded border-[#3c4a3f] text-primary focus:ring-primary h-4.5 w-4.5 bg-black"
                      />
                    </div>

                    <div 
                      onClick={() => setTcpEnabled(!tcpEnabled)}
                      className={`flex items-center gap-3.5 p-3 rounded-lg border transition-all cursor-pointer select-none bg-surface-container-lowest ${
                        tcpEnabled 
                          ? 'border-primary bg-primary/5' 
                          : 'border-outline-variant hover:border-secondary'
                      }`}
                    >
                      <Terminal className={`w-5 h-5 ${tcpEnabled ? 'text-primary' : 'text-outline'}`} />
                      <div className="flex-1">
                        <span className="text-xs font-semibold block">2. Toggle 'Enable ADB over TCP/IP'</span>
                        <span className="text-[10px] text-outline">Establishes listener socket inside phone (port 5555)</span>
                      </div>
                      <input 
                        type="checkbox" 
                        checked={tcpEnabled} 
                        readOnly
                        className="rounded border-[#3c4a3f] text-primary focus:ring-primary h-4.5 w-4.5 bg-black"
                      />
                    </div>
                  </div>
                </div>

                <div className="pt-4">
                  <button
                    disabled={!usbConnected || !tcpEnabled}
                    onClick={() => setCurrentStep(2)}
                    className="w-full bg-secondary-container text-white py-3 font-bold rounded-lg flex items-center justify-center gap-2 hover:bg-[#3d7eff] active:scale-[0.98] transition-all disabled:opacity-40 disabled:cursor-not-allowed disabled:hover:bg-secondary-container"
                  >
                    <span>I've enabled TCP/IP</span>
                    <ArrowRight className="w-4 h-4" />
                  </button>
                </div>
              </div>
            )}

            {/* VIEW STEP 2: Keyboard parameters */}
            {currentStep === 2 && (
              <div className="space-y-4 text-left flex-1 flex flex-col justify-between">
                <div className="space-y-4">
                  <div className="flex items-center justify-between">
                    <h3 className="text-base font-bold text-on-surface">
                      Configure Target Host Parameters
                    </h3>
                    
                    <button
                      onClick={handleAutoScan}
                      disabled={isScanning}
                      className="text-[11px] font-sans font-medium flex items-center gap-1.5 text-primary border border-primary/25 px-2.5 py-1 rounded bg-primary/5 hover:bg-primary/10 active:scale-95 transition-all disabled:opacity-50"
                    >
                      {isScanning ? (
                        <Loader2 className="w-3 h-3 animate-spin" />
                      ) : (
                        <Radar className="w-3 h-3" />
                      )}
                      <span>{isScanning ? 'Scanning...' : 'Auto-Scan Subnet'}</span>
                    </button>
                  </div>

                  {/* Manual Inputs Grid */}
                  <div className="grid grid-cols-4 gap-3">
                    <div className="col-span-3 space-y-1">
                      <label className="text-[10px] uppercase font-bold tracking-wider text-outline">
                        ADB target IP Address
                      </label>
                      <input 
                        type="text" 
                        value={ipAddress}
                        onChange={(e) => setIpAddress(e.target.value)}
                        placeholder="e.g. 192.168.1.105"
                        className="w-full bg-[#0c0e11] border border-outline-variant hover:border-secondary focus:border-primary focus:ring-1 focus:ring-primary focus:outline-none rounded px-3.5 py-2.5 font-mono text-sm text-primary transition-all placeholder:opacity-30"
                      />
                    </div>
                    
                    <div className="col-span-1 space-y-1">
                      <label className="text-[10px] uppercase font-bold tracking-wider text-outline">
                        Port
                      </label>
                      <input 
                        type="text" 
                        value={port}
                        onChange={(e) => setPort(e.target.value)}
                        placeholder="5555"
                        className="w-full bg-[#0c0e11] border border-outline-variant focus:border-primary focus:ring-1 focus:ring-primary focus:outline-none rounded px-3.5 py-2.5 font-mono text-sm text-primary text-center transition-all"
                      />
                    </div>
                  </div>

                  {/* Scanning State or Auto-scan success results */}
                  {isScanning && (
                    <div className="p-3 bg-surface-container-lowest border border-dashed border-outline-variant rounded animate-pulse flex items-center gap-2.5">
                      <Loader2 className="w-4 h-4 text-primary animate-spin" />
                      <span className="text-[11px] font-mono text-outline">
                        Searching client targets on subnet 192.168.1.0/24...
                      </span>
                    </div>
                  )}

                  {!isScanning && scanResult && (
                    <div className="p-3 bg-primary/5 border border-primary/30 rounded flex items-center justify-between">
                      <div className="flex items-center gap-2.5">
                        <CheckCircle2 className="w-4 h-4 text-primary" />
                        <div>
                          <span className="text-xs text-on-surface font-semibold block leading-none">
                            Found Active Target: {scanResult.name}
                          </span>
                          <span className="text-[10px] text-outline font-mono mt-0.5 block">
                            Address resolved: {scanResult.ip}:{port}
                          </span>
                        </div>
                      </div>
                      <span className="bg-primary/20 text-primary border border-primary/30 text-[9px] font-mono font-bold px-1.5 py-0.5 rounded uppercase">
                        Latency: 11ms
                      </span>
                    </div>
                  )}
                </div>

                {/* Form buttons */}
                <div className="pt-4 flex flex-col gap-2">
                  <button
                    onClick={handleConnectDevice}
                    disabled={ipAddress.trim().length === 0}
                    className="w-full bg-primary text-on-primary py-3 font-semibold rounded-lg hover:brightness-110 active:scale-[0.98] transition-all disabled:opacity-40 disabled:cursor-not-allowed"
                  >
                    Connect to Wireless Target
                  </button>
                  
                  <button
                    onClick={() => setCurrentStep(1)}
                    className="w-full text-outline font-medium text-xs flex items-center justify-center gap-1 py-1 hover:text-on-surface transition-colors"
                  >
                    <ArrowLeft className="w-3.5 h-3.5" />
                    <span>Back to Preparation</span>
                  </button>
                </div>
              </div>
            )}

            {/* VIEW STEP 3: Connecting handshaking / Results */}
            {currentStep === 3 && (
              <div className="h-full flex flex-col items-center justify-center text-center p-4">
                
                {/* 1. CONNECTING STATE */}
                {connectingState === 'loading' && (
                  <div className="space-y-6">
                    <div className="relative inline-block">
                      <div className="w-20 h-20 rounded-full border-4 border-primary/10 border-t-primary animate-spin" />
                      <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2">
                        <Smartphone className="w-8 h-8 text-primary animate-pulse" />
                      </div>
                    </div>
                    <div>
                      <h3 className="text-base font-bold text-on-surface">
                        Handshaking with {ipAddress}:{port}...
                      </h3>
                      <p className="text-xs text-outline font-mono mt-2 bg-[#0c0e11] px-3 py-1.5 rounded inline-block border border-outline-variant/30">
                        adb connect {ipAddress}:{port}
                      </p>
                    </div>
                  </div>
                )}

                {/* 2. SUCCESS STATE */}
                {connectingState === 'success' && (
                  <div className="space-y-5 flex-1 flex flex-col justify-between w-full">
                    <div className="space-y-5 pt-8">
                      <div className="w-20 h-20 rounded-full bg-primary/15 flex items-center justify-center mx-auto status-pulse">
                        <CheckCircle2 className="w-10 h-10 text-primary shrink-0" />
                      </div>
                      <div>
                        <h3 className="text-lg font-bold text-primary">
                          Pairing Successful
                        </h3>
                        <p className="text-xs text-on-surface-variant leading-relaxed mt-2 max-w-sm mx-auto">
                          Parsed serial model <span className="font-semibold text-on-surface">{scanResult?.name || 'Pixel 7 Pro'}</span> on host {ipAddress}:{port} successfully loaded into operational cluster.
                        </p>
                      </div>
                    </div>
                    
                    <button
                      onClick={handleFinalize}
                      className="w-full bg-[#282a2d] hover:bg-primary hover:text-on-primary text-white py-3.5 font-bold rounded-lg transition-all"
                    >
                      Finalize & Go to Dashboard
                    </button>
                  </div>
                )}

                {/* 3. FAILURE DIAGNOSTIC */}
                {connectingState === 'failure' && (
                  <div className="space-y-5 flex-1 flex flex-col justify-between w-full">
                    <div className="space-y-5 pt-4">
                      <div className="w-16 h-16 rounded-full bg-error/10 flex items-center justify-center mx-auto border border-error/20">
                        <AlertTriangle className="w-8 h-8 text-error shrink-0" />
                      </div>
                      <div>
                        <h3 className="text-lg font-bold text-error">
                          Host Connection Failed
                        </h3>
                        <div className="text-xs text-on-surface-variant leading-relaxed mt-2 max-w-md mx-auto space-y-2 text-left bg-surface-container-lowest p-3 rounded-lg border border-outline-variant">
                          <p className="font-semibold text-on-surface">Diagnostics Checklist:</p>
                          <ul className="list-disc list-inside space-y-1 text-[11px] text-[#bbcbbc]">
                            <li>Verify the device lies on same Wi-Fi SSID subnet list.</li>
                            <li>Confirm port 5555 isn't blockaded by router routers security client.</li>
                            <li>Reload the 'Enable ADB over TCP/IP' option on developers profile.</li>
                          </ul>
                        </div>
                      </div>
                    </div>

                    <div className="flex gap-2.5 mt-4">
                      <button
                        onClick={() => setCurrentStep(2)}
                        className="flex-1 bg-surface-container-highest text-on-surface py-3 rounded-lg text-xs font-bold hover:brightness-125 transition-all"
                      >
                        Edit Host Details
                      </button>
                      <button
                        onClick={handleConnectDevice}
                        className="flex-1 bg-error-container text-on-error-container py-3 rounded-lg text-xs font-bold hover:bg-[#a9000c] transition-all"
                      >
                        Retry Connection Handshake
                      </button>
                    </div>
                  </div>
                )}
              </div>
            )}

          </div>
        </div>
      </div>
    </div>
  );
}
