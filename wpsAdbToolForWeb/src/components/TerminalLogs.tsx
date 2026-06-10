/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState, useEffect, useRef } from 'react';
import { 
  Terminal, 
  Trash2, 
  Pause, 
  Play, 
  Filter, 
  Search, 
  FileDown, 
  X,
  AlertTriangle
} from 'lucide-react';
import { ADBLog } from '../types';

interface TerminalLogsProps {
  logs: ADBLog[];
  onClearLogs: () => void;
  onAddSimulatedLog: (log: string) => void;
  onClose: () => void;
}

export default function TerminalLogs({ logs, onClearLogs, onAddSimulatedLog, onClose }: TerminalLogsProps) {
  const [activeLevel, setActiveLevel] = useState<'ALL' | 'V' | 'D' | 'I' | 'W' | 'E'>('ALL');
  const [searchWord, setSearchWord] = useState('');
  const [autoScroll, setAutoScroll] = useState(true);
  const logEndRef = useRef<HTMLDivElement>(null);

  // Auto scroll effect
  useEffect(() => {
    if (autoScroll && logEndRef.current) {
      logEndRef.current.scrollIntoView({ behavior: 'smooth' });
    }
  }, [logs, autoScroll]);

  const levels = [
    { key: 'ALL', label: 'All Logcat Sockets', style: 'text-outline border-outline-variant/30 hover:border-outline' },
    { key: 'V', label: 'Verbose (V)', style: 'text-zinc-500 border-zinc-900 hover:border-zinc-700' },
    { key: 'D', label: 'Debug (D)', style: 'text-blue-400 border-blue-950 hover:border-blue-700' },
    { key: 'I', label: 'Info (I)', style: 'text-primary border-primary-container-lowest/10 hover:border-primary' },
    { key: 'W', label: 'Warning (W)', style: 'text-amber-500 border-amber-950 hover:border-amber-700' },
    { key: 'E', label: 'Error (E)', style: 'text-error border-error-container/10 hover:border-error' },
  ];

  const getLogColor = (level: string) => {
    switch (level) {
      case 'V': return 'text-zinc-500';
      case 'D': return 'text-blue-400';
      case 'I': return 'text-primary';
      case 'W': return 'text-amber-500';
      case 'E': return 'text-error font-medium bg-error/5 border border-error/10 px-1 rounded-sm';
      default: return 'text-on-surface';
    }
  };

  const filteredLogs = logs.filter(log => {
    // Level criteria
    if (activeLevel !== 'ALL' && log.level !== activeLevel) return false;
    
    // Search tags criteria
    if (searchWord.trim().length > 0) {
      const term = searchWord.toLowerCase();
      return (
        log.tag.toLowerCase().includes(term) ||
        log.message.toLowerCase().includes(term)
      );
    }
    return true;
  });

  const handleSimulateError = () => {
    onAddSimulatedLog('Triggered mock error packet request on remote client socket.');
  };

  return (
    <div className="bg-[#0c0e11] border-t border-outline-variant h-72 flex flex-col fixed bottom-0 right-0 left-[240px] z-40 font-mono text-left">
      
      {/* Console actions bar */}
      <div className="bg-surface-container-high px-4 py-2 border-b border-outline-variant/40 flex items-center justify-between font-sans shrink-0">
        <div className="flex items-center gap-4.5">
          <div className="flex items-center gap-1.5 text-primary text-xs font-semibold leading-none">
            <Terminal className="w-3.5 h-3.5" />
            <span>ADB Terminal Logger Logcat Stream</span>
          </div>

          <div className="flex items-center gap-1.5 overflow-x-auto">
            {levels.map((lvl) => (
              <button
                key={lvl.key}
                onClick={() => setActiveLevel(lvl.key as any)}
                className={`py-1 px-2.5 rounded font-mono text-[9px] font-bold border transition-all leading-none ${
                  activeLevel === lvl.key
                    ? 'bg-primary/15 text-primary border-primary/30 shadow-inner'
                    : 'bg-surface-container border-outline-variant hover:bg-surface-container-highest ' + lvl.style
                }`}
              >
                {lvl.key}
              </button>
            ))}
          </div>
        </div>

        {/* Console Search tool & buttons */}
        <div className="flex items-center gap-3">
          <div className="relative">
            <Search className="w-3.5 h-3.5 absolute left-2 top-1/2 -translate-y-1/2 text-outline-variant" />
            <input 
              type="text"
              value={searchWord}
              onChange={(e) => setSearchWord(e.target.value)}
              placeholder="Search Logcat Tag/Msg..."
              className="bg-surface-container-low border border-outline-variant rounded pl-7 pr-2.5 py-1 text-[10px] focus:outline-none focus:border-primary focus:ring-1 focus:ring-primary w-40 font-sans text-on-surface"
            />
          </div>

          <div className="flex items-center gap-1 border-l border-outline-variant/40 pl-3">
            <button
              onClick={handleSimulateError}
              className="p-1 px-2.5 hover:bg-error/10 text-error rounded text-[10px] font-bold transition-colors flex items-center gap-1 border border-error/15 font-sans cursor-pointer"
              title="Inject simulated runtime exception log stream"
            >
              <AlertTriangle className="w-3 h-3 text-error" />
              <span>Simulate Exception</span>
            </button>

            <button
              onClick={() => setAutoScroll(!autoScroll)}
              className={`p-1.5 rounded transition-colors ${autoScroll ? 'text-primary hover:bg-primary/5' : 'text-outline hover:bg-surface-container-highest'}`}
              title={autoScroll ? 'Pause autoscrolling' : 'Resume autoscrolling'}
            >
              {autoScroll ? <Pause className="w-3.5 h-3.5" /> : <Play className="w-3.5 h-3.5" />}
            </button>

            <button
              onClick={onClearLogs}
              className="p-1.5 text-outline hover:text-on-surface hover:bg-surface-container-highest rounded transition-colors"
              title="Clear terminal log logs"
            >
              <Trash2 className="w-3.5 h-3.5" />
            </button>

            <button
              onClick={onClose}
              className="p-1.5 text-outline hover:text-on-surface hover:bg-surface-container-highest rounded transition-colors ml-1"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      </div>

      {/* Log list */}
      <div className="flex-1 overflow-y-auto p-4 font-mono text-[11px] leading-relaxed space-y-1.5 bg-[#08090a] custom-scrollbar focus:outline-none select-text">
        {filteredLogs.length === 0 ? (
          <p className="text-zinc-600 font-mono italic text-xs py-4 select-none">
            - No cascading event threads on active filters -
          </p>
        ) : (
          filteredLogs.map((log) => (
            <div key={log.id} className="flex gap-4 font-mono border-b border-white/5 pb-1 last:border-b-0 hover:bg-white/2">
              <span className="text-zinc-600 font-mono shrink-0 select-none">
                {log.timestamp}
              </span>
              <span className={`w-3 shrink-0 font-mono font-bold text-center ${getLogColor(log.level)}`}>
                {log.level}
              </span>
              <span className="text-secondary font-mono font-semibold shrink-0 w-28 truncate">
                {log.tag}
              </span>
              <span className={`font-mono flex-1 leading-normal ${getLogColor(log.level)} whitespace-pre-wrap`}>
                {log.message}
              </span>
            </div>
          ))
        )}
        <div ref={logEndRef} />
      </div>

    </div>
  );
}
