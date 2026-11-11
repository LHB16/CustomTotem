import React from 'react';
import { Sparkles, Plus, HelpCircle, GitBranch, Search, Film, LayoutGrid } from 'lucide-react';

export default function Header({
  activeView,
  setActiveView,
  searchTerm,
  setSearchTerm,
  onOpenUpload,
  onOpenHelp
}) {
  return (
    <header className="sticky top-0 z-30 border-b border-slate-800/80 bg-slate-950/90 backdrop-blur-md">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16 gap-3">
          
          {/* Logo & Mode Switcher */}
          <div className="flex items-center gap-4">
            <div
              onClick={() => setActiveView('l2d')}
              className="flex items-center gap-3 cursor-pointer"
            >
              <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-amber-500/20 to-orange-500/20 border border-amber-500/30 flex items-center justify-center text-amber-400 shadow-inner">
                <Sparkles className="w-5 h-5 text-amber-400" strokeWidth={1.75} />
              </div>
              <div>
                <div className="flex items-center gap-2">
                  <span className="font-extrabold text-lg text-slate-100 tracking-tight">NikkeL2D & TotemHub</span>
                  <span className="px-1.5 py-0.5 text-[10px] font-semibold bg-amber-500/10 text-amber-400 border border-amber-500/20 rounded">
                    60FPS Studio
                  </span>
                </div>
                <p className="text-xs text-slate-400 hidden sm:block">
                  Spine 2D Animation Exporter (WebM Alpha & 60FPS PNG ZIP)
                </p>
              </div>
            </div>

            {/* Navigation Switch Pills */}
            <div className="flex items-center bg-slate-900 border border-slate-800 p-1 rounded-xl">
              <button
                onClick={() => setActiveView('l2d')}
                className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold transition-all cursor-pointer ${
                  activeView === 'l2d'
                    ? 'bg-gradient-to-r from-amber-400 to-amber-500 text-slate-950 shadow-sm'
                    : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                <Film className="w-3.5 h-3.5" strokeWidth={2} />
                <span>Nikke Live2D Studio</span>
              </button>

              <button
                onClick={() => setActiveView('gallery')}
                className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg text-xs font-bold transition-all cursor-pointer ${
                  activeView === 'gallery'
                    ? 'bg-gradient-to-r from-amber-400 to-amber-500 text-slate-950 shadow-sm'
                    : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                <LayoutGrid className="w-3.5 h-3.5" strokeWidth={2} />
                <span>Thư Viện Totem</span>
              </button>
            </div>
          </div>

          {/* Search bar (Only in Gallery view) */}
          {activeView === 'gallery' && (
            <div className="flex-1 max-w-sm relative hidden lg:block">
              <Search className="w-4 h-4 text-slate-400 absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none" strokeWidth={1.75} />
              <input
                type="text"
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                placeholder="Tìm kiếm totem theo tên, tác giả, tag..."
                className="w-full bg-slate-900/90 border border-slate-800 rounded-xl pl-10 pr-4 py-2 text-sm text-slate-200 placeholder-slate-500 focus:outline-none focus:border-amber-500/50 focus:ring-1 focus:ring-amber-500/50 transition-all"
              />
            </div>
          )}

          {/* Right Action Buttons */}
          <div className="flex items-center gap-2">
            <button
              onClick={onOpenHelp}
              className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-medium text-slate-300 hover:text-white bg-slate-900 hover:bg-slate-800 border border-slate-800 rounded-xl transition-colors cursor-pointer"
              title="Hướng dẫn dùng với mod"
            >
              <HelpCircle className="w-4 h-4 text-slate-400" strokeWidth={1.75} />
              <span className="hidden sm:inline">Hướng Dẫn</span>
            </button>

            <a
              href="https://github.com/LHB16/CustomTotem"
              target="_blank"
              rel="noreferrer"
              className="inline-flex items-center gap-1.5 px-3 py-2 text-xs font-medium text-slate-300 hover:text-white bg-slate-900 hover:bg-slate-800 border border-slate-800 rounded-xl transition-colors"
              title="GitHub Repository"
            >
              <GitBranch className="w-4 h-4 text-slate-400" strokeWidth={1.75} />
              <span className="hidden sm:inline">GitHub</span>
            </a>

            <button
              onClick={onOpenUpload}
              className="inline-flex items-center gap-2 px-3.5 py-2 text-xs font-semibold text-slate-950 bg-gradient-to-r from-amber-400 to-amber-500 hover:from-amber-300 hover:to-amber-400 rounded-xl shadow-lg shadow-amber-500/20 transition-all active:scale-95 cursor-pointer"
            >
              <Plus className="w-4 h-4" strokeWidth={2} />
              <span className="hidden sm:inline">Đăng Totem</span>
            </button>
          </div>

        </div>
      </div>
    </header>
  );
}
