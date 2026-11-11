import React, { useState, useEffect, useMemo } from 'react';
import Header from './components/Header';
import NikkeL2DViewer from './components/NikkeL2DViewer';
import TotemCard from './components/TotemCard';
import TotemModal from './components/TotemModal';
import UploadModal from './components/UploadModal';
import InstructionsModal from './components/InstructionsModal';
import { INITIAL_TOTEMS, CATEGORIES } from './data/totems';
import { Sparkles, Plus, Layers, HelpCircle, FileCode, CheckCircle2, Film } from 'lucide-react';

const STORAGE_KEY = 'custom_totem_hub_data_v1';

export default function App() {
  // Default view is 'l2d' (Nikke Live2D 60FPS Studio & Exporter)
  const [activeView, setActiveView] = useState('l2d');

  const [totems, setTotems] = useState(() => {
    try {
      const saved = localStorage.getItem(STORAGE_KEY);
      if (saved) {
        const parsed = JSON.parse(saved);
        if (Array.isArray(parsed) && parsed.length > 0) return parsed;
      }
    } catch (e) {
      console.warn("Could not load from localStorage", e);
    }
    return INITIAL_TOTEMS;
  });

  const [searchTerm, setSearchTerm] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('all');
  const [filterAnimatedOnly, setFilterAnimatedOnly] = useState(false);
  const [filterGlowOnly, setFilterGlowOnly] = useState(false);

  const [selectedTotem, setSelectedTotem] = useState(null);
  const [isUploadOpen, setIsUploadOpen] = useState(false);
  const [isHelpOpen, setIsHelpOpen] = useState(false);
  const [exportNotice, setExportNotice] = useState(false);

  // Sync to localStorage
  useEffect(() => {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(totems));
    } catch (e) {
      console.warn("Could not save to localStorage", e);
    }
  }, [totems]);

  // Handle adding new totem
  const handleAddTotem = (newTotem) => {
    setTotems((prev) => [newTotem, ...prev]);
  };

  // Export JSON configuration for easy GitHub commit
  const handleExportJson = () => {
    const dataStr = "data:text/json;charset=utf-8," + encodeURIComponent(JSON.stringify(totems, null, 2));
    const downloadAnchor = document.createElement('a');
    downloadAnchor.setAttribute("href", dataStr);
    downloadAnchor.setAttribute("download", "totems.json");
    document.body.appendChild(downloadAnchor);
    downloadAnchor.click();
    downloadAnchor.remove();

    setExportNotice(true);
    setTimeout(() => setExportNotice(false), 3000);
  };

  // Filtered totems
  const filteredTotems = useMemo(() => {
    return totems.filter((t) => {
      if (searchTerm.trim()) {
        const query = searchTerm.toLowerCase();
        const matchTitle = t.title.toLowerCase().includes(query);
        const matchAuthor = t.author.toLowerCase().includes(query);
        const matchDesc = t.description?.toLowerCase().includes(query);
        const matchTags = t.tags?.some((tag) => tag.toLowerCase().includes(query));
        if (!matchTitle && !matchAuthor && !matchDesc && !matchTags) return false;
      }
      if (selectedCategory !== 'all' && t.category !== selectedCategory) {
        return false;
      }
      if (filterAnimatedOnly && !t.isAnimated) {
        return false;
      }
      if (filterGlowOnly && !t.glow) {
        return false;
      }
      return true;
    });
  }, [totems, searchTerm, selectedCategory, filterAnimatedOnly, filterGlowOnly]);

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col">
      {/* Top Navigation */}
      <Header
        activeView={activeView}
        setActiveView={setActiveView}
        searchTerm={searchTerm}
        setSearchTerm={setSearchTerm}
        onOpenUpload={() => setIsUploadOpen(true)}
        onOpenHelp={() => setIsHelpOpen(true)}
      />

      {activeView === 'l2d' ? (
        <NikkeL2DViewer
          onSendToTotemGallery={(newTotem) => {
            handleAddTotem(newTotem);
          }}
        />
      ) : (
        <>
          {/* Hero Section */}
          <section className="relative overflow-hidden border-b border-slate-900 bg-gradient-to-b from-amber-500/5 via-slate-950/40 to-slate-950 py-12 sm:py-16">
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 relative z-10">
              <div className="text-center max-w-3xl mx-auto space-y-4">
                
                <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-amber-500/10 border border-amber-500/20 text-amber-400 text-xs font-semibold">
                  <Sparkles className="w-3.5 h-3.5" strokeWidth={1.75} />
                  <span>Dành riêng cho Custom Totem Mod (Fabric 1.21.11)</span>
                </div>

                <h1 className="text-3xl sm:text-5xl font-extrabold tracking-tight text-white">
                  Khám Phá & Chia Sẻ <br />
                  <span className="bg-gradient-to-r from-amber-400 via-amber-200 to-orange-400 bg-clip-text text-transparent">
                    Totem of Undying Độc Đáo
                  </span>
                </h1>

                <p className="text-sm sm:text-base text-slate-400 max-w-2xl mx-auto leading-relaxed">
                  Thư viện tổng hợp các mẫu Totem tùy biến: ảnh tĩnh, dải ảnh động (strip animation), hiệu ứng phát sáng Unshaded và tích hợp trực tiếp với Nikke Live2D 60FPS Studio.
                </p>

                <div className="pt-2 flex flex-wrap items-center justify-center gap-3">
                  <button
                    onClick={() => setActiveView('l2d')}
                    className="inline-flex items-center gap-2 px-5 py-2.5 rounded-xl bg-gradient-to-r from-amber-400 to-amber-500 hover:from-amber-300 hover:to-amber-400 text-slate-950 text-xs font-bold shadow-lg shadow-amber-500/20 transition-all active:scale-95 cursor-pointer"
                  >
                    <Film className="w-4 h-4" strokeWidth={2} />
                    <span>Mở Nikke Live2D 60FPS Studio</span>
                  </button>

                  <button
                    onClick={() => setIsUploadOpen(true)}
                    className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 text-slate-200 border border-slate-800 text-xs font-semibold transition-all cursor-pointer"
                  >
                    <Plus className="w-4 h-4 text-amber-400" strokeWidth={2} />
                    <span>Đăng Mẫu Totem Của Bạn</span>
                  </button>

                  <button
                    onClick={() => setIsHelpOpen(true)}
                    className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-slate-900 hover:bg-slate-800 text-slate-200 border border-slate-800 text-xs font-semibold transition-all cursor-pointer"
                  >
                    <HelpCircle className="w-4 h-4 text-slate-400" strokeWidth={1.75} />
                    <span>Xem Cách Cài Đặt</span>
                  </button>
                </div>

              </div>
            </div>
          </section>

          {/* Main Content Area */}
          <main className="flex-1 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-8 w-full">
            
            {/* Category & Filter Strip */}
            <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-6 border-b border-slate-900">
              
              {/* Categories */}
              <div className="flex items-center gap-1.5 overflow-x-auto pb-2 md:pb-0 no-scrollbar">
                {CATEGORIES.map((cat) => (
                  <button
                    key={cat.id}
                    onClick={() => setSelectedCategory(cat.id)}
                    className={`whitespace-nowrap px-3.5 py-1.5 rounded-xl text-xs font-semibold transition-all cursor-pointer ${
                      selectedCategory === cat.id
                        ? 'bg-amber-400 text-slate-950 shadow-md shadow-amber-500/20'
                        : 'bg-slate-900/80 hover:bg-slate-850 text-slate-400 hover:text-slate-200 border border-slate-800'
                    }`}
                  >
                    {cat.label}
                  </button>
                ))}
              </div>

              {/* Quick Filter Toggles & Export */}
              <div className="flex items-center gap-2 self-start md:self-auto">
                <button
                  onClick={() => setFilterAnimatedOnly((prev) => !prev)}
                  className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-medium border transition-colors cursor-pointer ${
                    filterAnimatedOnly
                      ? 'bg-emerald-500/20 border-emerald-500/40 text-emerald-300 font-semibold'
                      : 'bg-slate-900 border-slate-800 text-slate-400 hover:text-slate-300'
                  }`}
                >
                  <Layers className="w-3.5 h-3.5" strokeWidth={1.75} />
                  <span>Chỉ Animated</span>
                </button>

                <button
                  onClick={() => setFilterGlowOnly((prev) => !prev)}
                  className={`inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-medium border transition-colors cursor-pointer ${
                    filterGlowOnly
                      ? 'bg-amber-500/20 border-amber-500/40 text-amber-300 font-semibold'
                      : 'bg-slate-900 border-slate-800 text-slate-400 hover:text-slate-300'
                  }`}
                >
                  <Sparkles className="w-3.5 h-3.5" strokeWidth={1.75} />
                  <span>Chỉ Phát Sáng</span>
                </button>

                <button
                  onClick={handleExportJson}
                  className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-medium bg-slate-900 border border-slate-800 hover:border-slate-700 text-slate-400 hover:text-slate-200 transition-colors cursor-pointer"
                  title="Xuất danh sách totems.json để lưu trữ hoặc commit lên GitHub"
                >
                  <FileCode className="w-3.5 h-3.5" strokeWidth={1.75} />
                  <span className="hidden sm:inline">Xuất JSON</span>
                </button>
              </div>

            </div>

            {/* Export Toast Notice */}
            {exportNotice && (
              <div className="my-4 p-3 rounded-xl bg-emerald-500/10 border border-emerald-500/30 text-emerald-400 text-xs flex items-center gap-2 animate-in fade-in">
                <CheckCircle2 className="w-4 h-4" strokeWidth={1.75} />
                <span>Đã xuất file <strong>totems.json</strong> thành công! Bạn có thể lưu vào dự án hoặc chia sẻ cùng bạn bè.</span>
              </div>
            )}

            {/* Results Counter */}
            <div className="py-4 flex items-center justify-between text-xs text-slate-400">
              <span>Tìm thấy <strong className="text-slate-200 font-semibold">{filteredTotems.length}</strong> mẫu totem</span>
              {searchTerm && (
                <button
                  onClick={() => setSearchTerm('')}
                  className="text-amber-400 hover:underline cursor-pointer"
                >
                  Xóa tìm kiếm
                </button>
              )}
            </div>

            {/* Totem Grid */}
            {filteredTotems.length > 0 ? (
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-5">
                {filteredTotems.map((totem) => (
                  <TotemCard
                    key={totem.id}
                    totem={totem}
                    onClick={() => setSelectedTotem(totem)}
                  />
                ))}
              </div>
            ) : (
              <div className="text-center py-20 bg-slate-900/30 border border-slate-900 rounded-3xl">
                <Layers className="w-12 h-12 text-slate-700 mx-auto mb-3" strokeWidth={1.5} />
                <h3 className="text-base font-bold text-slate-300">Không tìm thấy mẫu Totem nào</h3>
                <p className="text-xs text-slate-500 mt-1 max-w-sm mx-auto">
                  Hãy thử tìm kiếm với từ khóa khác hoặc tạo Totem mới từ Nikke Live2D Studio!
                </p>
                <button
                  onClick={() => setActiveView('l2d')}
                  className="mt-4 px-4 py-2 bg-amber-500/10 hover:bg-amber-500/20 text-amber-400 border border-amber-500/30 rounded-xl text-xs font-semibold cursor-pointer"
                >
                  Mở Nikke Live2D Studio
                </button>
              </div>
            )}

          </main>

          {/* Footer */}
          <footer className="border-t border-slate-900 bg-slate-950 py-8 mt-12 text-center text-xs text-slate-500 space-y-2">
            <div className="max-w-7xl mx-auto px-4 flex flex-col sm:flex-row items-center justify-between gap-4">
              <p>
                © 2026 <strong>Custom Totem Manager & Nikke L2D Studio</strong> • Phát triển và duy trì bởi <span className="text-slate-300 font-semibold">BinhLH</span>.
              </p>
              <div className="flex items-center gap-4 text-slate-400">
                <a href="https://github.com/LHB16/CustomTotem" target="_blank" rel="noreferrer" className="hover:text-amber-400 transition-colors">
                  GitHub
                </a>
                <span>•</span>
                <a href="https://modrinth.com" target="_blank" rel="noreferrer" className="hover:text-amber-400 transition-colors">
                  Modrinth
                </a>
                <span>•</span>
                <span className="text-slate-500">Hỗ trợ code bởi AI (Google DeepMind)</span>
              </div>
            </div>
          </footer>
        </>
      )}

      {/* Modals */}
      {selectedTotem && (
        <TotemModal
          totem={selectedTotem}
          onClose={() => setSelectedTotem(null)}
        />
      )}

      {isUploadOpen && (
        <UploadModal
          onClose={() => setIsUploadOpen(false)}
          onAddTotem={handleAddTotem}
        />
      )}

      {isHelpOpen && (
        <InstructionsModal
          onClose={() => setIsHelpOpen(false)}
        />
      )}
    </div>
  );
}
