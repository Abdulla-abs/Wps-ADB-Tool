/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useRef, useState } from 'react';
import { 
  LayoutGrid, 
  Users, 
  Settings, 
  FileUp, 
  FileText, 
  HelpCircle, 
  Code,
  Terminal,
  Activity,
  CheckCircle2
} from 'lucide-react';

interface SidebarProps {
  activeTab: string;
  setActiveTab: (tab: string) => void;
  onAdpDropSuccess: (fileName: string) => void;
  isLogTrayOpen: boolean;
  setIsLogTrayOpen: (open: boolean) => void;
  onlineDeviceCount: number;
}

export default function Sidebar({
  activeTab,
  setActiveTab,
  onAdpDropSuccess,
  isLogTrayOpen,
  setIsLogTrayOpen,
  onlineDeviceCount
}: SidebarProps) {
  const [isDragging, setIsDragging] = useState(false);
  const [installingFile, setInstallingFile] = useState<string | null>(null);
  const [installProgress, setInstallProgress] = useState(0);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const handleDragOver = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(true);
  };

  const handleDragLeave = () => {
    setIsDragging(false);
  };

  const simulateInstall = (fileName: string) => {
    setInstallingFile(fileName);
    setInstallProgress(10);
    
    const interval = setInterval(() => {
      setInstallProgress((prev) => {
        if (prev >= 100) {
          clearInterval(interval);
          setTimeout(() => {
            setInstallingFile(null);
            onAdpDropSuccess(fileName);
          }, 1000);
          return 100;
        }
        return prev + Math.floor(Math.random() * 25) + 10;
      });
    }, 450);
  };

  const handleDrop = (e: React.DragEvent) => {
    e.preventDefault();
    setIsDragging(false);
    
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      const file = e.dataTransfer.files[0];
      if (file.name.endsWith('.apk')) {
        simulateInstall(file.name);
      } else {
        alert('Please drop valid Android application files (.apk only)');
      }
    }
  };

  const handleFileClick = () => {
    if (fileInputRef.current) {
      fileInputRef.current.click();
    }
  };

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      const file = e.target.files[0];
      simulateInstall(file.name);
    }
  };

  return (
    <aside className="w-[240px] h-screen fixed left-0 top-0 bg-surface-container-low border-r border-outline-variant flex flex-col py-4 z-50">
      {/* Brand Header */}
      <div className="px-6 mb-8 select-none">
        <div className="flex items-center gap-2">
          <Activity className="w-5 h-5 text-primary stroke-[2.5]" />
          <h1 className="text-lg font-bold text-on-surface tracking-tight font-sans">
            DroidCluster
          </h1>
        </div>
        <div className="text-xs font-mono text-outline mt-1 font-medium bg-surface-container px-2 py-0.5 rounded-md inline-block">
          ADB Daemon v1.0.41
        </div>
      </div>

      {/* Navigation Links */}
      <nav className="flex-1 px-3 space-y-1">
        <button
          onClick={() => setActiveTab('wall')}
          className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-lg font-medium transition-all text-left ${
            activeTab === 'wall'
              ? 'text-primary bg-surface-container-high border-r-2 border-primary shadow-sm shadow-primary/10'
              : 'text-outline hover:text-on-surface hover:bg-surface-container-low'
          }`}
        >
          <LayoutGrid className="w-4.5 h-4.5" />
          <span className="text-sm font-sans">Device Wall</span>
        </button>

        <button
          onClick={() => setActiveTab('groups')}
          className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-lg font-medium transition-all text-left ${
            activeTab === 'groups'
              ? 'text-primary bg-surface-container-high border-r-2 border-primary shadow-sm shadow-primary/10'
              : 'text-outline hover:text-on-surface hover:bg-surface-container'
          }`}
        >
          <Users className="w-4.5 h-4.5" />
          <span className="text-sm font-sans">Group Command</span>
        </button>

        <button
          onClick={() => setActiveTab('kmpCode')}
          className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-lg font-medium transition-all text-left ${
            activeTab === 'kmpCode'
              ? 'text-primary bg-surface-container-high border-r-2 border-primary shadow-sm shadow-primary/10'
              : 'text-outline hover:text-on-surface hover:bg-surface-container'
          }`}
        >
          <Code className="w-4.5 h-4.5 text-secondary" />
          <span className="text-sm font-sans flex items-center justify-between w-full">
            <span>KMP Code Hub</span>
            <span className="text-[10px] bg-secondary/10 text-secondary font-mono px-1.5 py-0.5 rounded uppercase weight-semibold">
              Shared UI
            </span>
          </span>
        </button>

        <button
          onClick={() => setActiveTab('settings')}
          className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-lg font-medium transition-all text-left ${
            activeTab === 'settings'
              ? 'text-primary bg-surface-container-high border-r-2 border-primary shadow-sm shadow-primary/10'
              : 'text-outline hover:text-on-surface hover:bg-surface-container'
          }`}
        >
          <Settings className="w-4.5 h-4.5" />
          <span className="text-sm font-sans">Global Settings</span>
        </button>
      </nav>

      {/* Interactive Drag-and-Drop / Sideloading APK Box */}
      <div className="px-4 mt-auto pt-6 border-t border-outline-variant/30">
        <input 
          type="file" 
          ref={fileInputRef} 
          onChange={handleFileChange} 
          className="hidden" 
          accept=".apk"
        />
        
        {installingFile ? (
          <div className="border border-primary/40 bg-primary/5 rounded-xl p-3 flex flex-col gap-2">
            <div className="flex items-center gap-2">
              <LoaderIcon className="w-4 h-4 text-primary animate-spin" />
              <span className="text-xs font-mono font-medium text-on-surface line-clamp-1">
                Installing APK...
              </span>
            </div>
            <p className="text-[10px] text-outline font-sans line-clamp-1">
              Piping {installingFile}
            </p>
            <div className="w-full bg-surface-container-highest rounded-full h-1 mt-1 overflow-hidden">
              <div 
                className="h-full bg-primary rounded-full transition-all duration-300" 
                style={{ width: `${installProgress}%` }}
              />
            </div>
            <span className="text-[10px] font-mono text-primary text-right">
              {installProgress}%
            </span>
          </div>
        ) : (
          <div
            onClick={handleFileClick}
            onDragOver={handleDragOver}
            onDragLeave={handleDragLeave}
            onDrop={handleDrop}
            className={`cursor-pointer group select-none border-2 border-dashed rounded-xl p-4 flex flex-col items-center justify-center gap-2 text-center transition-all ${
              isDragging 
                ? 'border-primary bg-primary/10 scale-[1.02]' 
                : 'border-outline-variant hover:border-primary/50 hover:bg-surface-container'
            }`}
          >
            <FileUp className={`w-6 h-6 transition-colors ${isDragging ? 'text-primary' : 'text-outline group-hover:text-primary'}`} />
            <div>
              <span className="text-[11px] font-sans font-medium uppercase tracking-widest text-[#bbcbbc] block">
                Drop APK to Install
              </span>
              <span className="text-[9px] text-[#869587] block mt-0.5">
                Will install on {onlineDeviceCount} online devices
              </span>
            </div>
          </div>
        )}

        {/* Documentation / Support links */}
        <div className="mt-6 space-y-2">
          <button 
            onClick={() => alert('Opening local deployment manual (ADB v1.0.41)')}
            className="flex items-center gap-3 w-full py-1 text-xs text-outline hover:text-primary transition-colors text-left"
          >
            <FileText className="w-4 h-4" />
            <span className="font-sans">ADB Guide Documentation</span>
          </button>
          
          <button 
            onClick={() => setIsLogTrayOpen(!isLogTrayOpen)}
            className={`flex items-center gap-3 w-full py-1 text-xs transition-colors text-left ${
              isLogTrayOpen ? 'text-primary' : 'text-outline hover:text-primary'
            }`}
          >
            <Terminal className="w-4 h-4" />
            <span className="font-sans">Toggle ADB Logcat Tray</span>
          </button>
        </div>
      </div>
    </aside>
  );
}

// Small inner loader icon
function LoaderIcon({ className }: { className?: string }) {
  return (
    <svg 
      className={className} 
      xmlns="http://www.w3.org/2000/svg" 
      fill="none" 
      viewBox="0 0 24 24"
    >
      <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
      <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
    </svg>
  );
}
