/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

import React, { useState } from 'react';
import { 
  Code, 
  Copy, 
  Check, 
  HelpCircle, 
  Layers, 
  Tv, 
  Cpu, 
  Monitor, 
  Laptop
} from 'lucide-react';
import { KMP_CODES } from '../data';

export default function KmpHub() {
  const [activeFile, setActiveFile] = useState<keyof typeof KMP_CODES>('App.kt');
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    navigator.clipboard.writeText(KMP_CODES[activeFile]);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="space-y-6 text-left max-w-4xl font-sans">
      
      {/* Intro Header */}
      <div className="space-y-2">
        <div className="flex items-center gap-2.5">
          <Layers className="w-5 h-5 text-secondary" />
          <h2 className="text-xl font-bold text-on-surface">
            Kotlin Multiplatform UI Sharing Hub
          </h2>
        </div>
        <p className="text-xs text-on-surface-variant leading-relaxed">
          DroidCluster relies on <strong>Compose Multiplatform (JetBrains)</strong> to distribute its highly polished Carbon UI layouts across Windows, macOS, and Linux targets. This eliminates duplicate client-side codebases while preserving pixel-perfect visual precision.
        </p>
      </div>

      {/* Info board cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-3.5 pt-1">
        <div className="p-4 bg-surface-container border border-outline-variant rounded-xl flex gap-3.5">
          <Cpu className="w-5 h-5 text-primary shrink-0 mt-0.5" />
          <div>
            <h4 className="text-xs font-bold text-on-surface">Shared Core Engine</h4>
            <p className="text-[10px] text-outline mt-1 leading-normal">
              Internal parameters, ADB transport sockets, device telemetry, and log parses are packaged in standard Kotlin Multiplatform common libraries.
            </p>
          </div>
        </div>

        <div className="p-4 bg-surface-container border border-outline-variant rounded-xl flex gap-3.5">
          <Laptop className="w-5 h-5 text-secondary shrink-0 mt-0.5" />
          <div>
            <h4 className="text-xs font-bold text-on-surface">macOS Navigation UI</h4>
            <p className="text-[10px] text-outline mt-1 leading-normal">
              Utilizes native Skia canvas render mapping for buttery-smooth fluid scrolling and high-refresh hardware display pipelines.
            </p>
          </div>
        </div>

        <div className="p-4 bg-surface-container border border-outline-variant rounded-xl flex gap-3.5">
          <Monitor className="w-5 h-5 text-secondary-container shrink-0 mt-0.5" />
          <div>
            <h4 className="text-xs font-bold text-on-surface">Windows Grid Control</h4>
            <p className="text-[10px] text-outline mt-1 leading-normal">
              Bridges effortlessly using Compose desktop. Compiles to modular native executable binaries (.exe / .msi) using JPackage.
            </p>
          </div>
        </div>
      </div>

      {/* Code Browser Layout */}
      <div className="space-y-3 pt-2">
        <div className="flex justify-between items-center bg-surface-container-high rounded-t-xl px-4 py-3 border-b border-outline-variant/40">
          <div className="flex items-center gap-2 overflow-x-auto custom-scrollbar">
            {(Object.keys(KMP_CODES) as Array<keyof typeof KMP_CODES>).map((fileName) => (
              <button
                key={fileName}
                onClick={() => setActiveFile(fileName)}
                className={`py-1.5 px-3 rounded text-xs font-semibold font-mono leading-none border transition-all ${
                  activeFile === fileName
                    ? 'bg-[#111316] text-[#60f99e] border-primary/20'
                    : 'bg-transparent text-outline border-transparent hover:text-on-surface'
                }`}
              >
                {fileName}
              </button>
            ))}
          </div>

          <button
            onClick={handleCopy}
            className="flex items-center gap-1.5 text-[11px] font-sans font-medium text-[#bbcbbc] hover:text-primary hover:bg-surface-container px-2.5 py-1.5 rounded border border-outline-variant transition-colors"
          >
            {copied ? (
              <>
                <Check className="w-3.5 h-3.5 text-primary" />
                <span className="text-primary">Copied KMP Code</span>
              </>
            ) : (
              <>
                <Copy className="w-3.5 h-3.5" />
                <span>Copy Code</span>
              </>
            )}
          </button>
        </div>

        {/* Monospaced Syntax Simulation container */}
        <div className="bg-[#0c0e11] border border-outline-variant rounded-b-xl p-4 md:p-5 relative overflow-x-auto text-left font-mono">
          <pre className="text-xs text-[#bbcbbc] leading-relaxed font-mono whitespace-pre select-text">
            {KMP_CODES[activeFile].split('\n').map((line, idx) => {
              // Custom lightweight visual styling parsing for standard highlight keywords
              const formattedLine = line
                .replace(/\b(package|import|fun|val|var|class|interface|return|val|var|if|else|when)\b/g, '<span class="text-pink-500 font-bold">$1</span>')
                .replace(/\b(Modifier|MaterialTheme|modifier|Window|rememberWindowState|Column|Row|Box|Sidebar|DeviceWall|Dialog|Surface|Card|Text|Spacer|DeviceCard|PrimaryGreen|CarbonSurface)\b/g, '<span class="text-blue-400 font-semibold">$1</span>')
                .replace(/("(.*?)")/g, '<span class="text-emerald-400">$1</span>')
                .replace(/(\/\/.*)$/g, '<span class="text-zinc-500 italic">$1</span>')
                .replace(/(@[a-zA-Z0-9_]+)/g, '<span class="text-amber-400">$1</span>');

              return (
                <div key={idx} className="grid grid-cols-[30px_1fr] gap-4 font-mono">
                  <span className="text-zinc-600 text-right select-none text-[10px] pr-2 border-r border-[#1e2023] font-mono leading-relaxed">
                    {idx + 1}
                  </span>
                  <span 
                    className="font-mono leading-relaxed"
                    dangerouslySetInnerHTML={{ __html: formattedLine || ' ' }}
                  />
                </div>
              );
            })}
          </pre>
        </div>
      </div>
    </div>
  );
}
