import React, { useState } from 'react';
import { X, Download, Copy, Check, Volume2, Sparkles, Layers, Monitor, Sliders } from 'lucide-react';
import AnimatedTotemPreview from './AnimatedTotemPreview';
import { playTotemSound } from '../utils/audio';

export default function TotemModal({ totem, onClose }) {
  const [copied, setCopied] = useState(false);
  const [previewMode, setPreviewMode] = useState('normal'); // 'normal' | 'hand' | 'pop'
  const [isPopping, setIsPopping] = useState(false);
  const [frameRate, setFrameRate] = useState(totem.frameRate || 10);

  if (!totem) return null;

  const getFullDownloadUrl = () => {
    if (totem.downloadUrl.startsWith('http://') || totem.downloadUrl.startsWith('https://')) {
      return totem.downloadUrl;
    }
    return window.location.origin + totem.downloadUrl;
  };

  const handleCopyLink = () => {
    const url = getFullDownloadUrl();
    navigator.clipboard.writeText(url);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleDownload = () => {
    const link = document.createElement('a');
    link.href = totem.downloadUrl;
    link.download = `${totem.title.toLowerCase().replace(/\s+/g, '_')}.png`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const triggerPopSimulation = () => {
    setIsPopping(true);
    playTotemSound();
    setTimeout(() => {
      setIsPopping(false);
    }, 1500);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm animate-in fade-in duration-200">
      <div
        className="relative w-full max-w-2xl bg-slate-900 border border-slate-800 rounded-3xl shadow-2xl overflow-hidden flex flex-col max-h-[90vh]"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-800">
          <div className="flex items-center gap-2.5">
            <h2 className="text-lg font-bold text-slate-100">{totem.title}</h2>
            <span className="text-xs px-2 py-0.5 rounded-full bg-slate-800 text-slate-300 border border-slate-700">
              {totem.category}
            </span>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-xl hover:bg-slate-800 text-slate-400 hover:text-white transition-colors cursor-pointer"
          >
            <X className="w-5 h-5" strokeWidth={1.75} />
          </button>
        </div>

        {/* Content body */}
        <div className="p-6 overflow-y-auto space-y-6">
          
          {/* Preview stage */}
          <div className="relative h-64 bg-slate-950 rounded-2xl border border-slate-800 flex items-center justify-center overflow-hidden">
            
            {/* Background grid pattern */}
            <div className="absolute inset-0 bg-[radial-gradient(#1e293b_1px,transparent_1px)] [background-size:16px_16px] opacity-40" />

            {/* Simulated Hand or Pop Overlay */}
            {isPopping ? (
              <div className="relative flex flex-col items-center justify-center animate-bounce duration-500 scale-125 z-10">
                <AnimatedTotemPreview
                  imageUrl={totem.imageUrl}
                  isAnimated={totem.isAnimated}
                  frameCount={totem.frameCount}
                  frameRate={frameRate}
                  glow={true}
                  className="w-32 h-32 drop-shadow-[0_0_24px_rgba(245,158,11,0.9)]"
                />
                <span className="text-xs font-bold text-amber-300 mt-2 tracking-widest uppercase animate-pulse">
                  ✨ Totem Popped!
                </span>
              </div>
            ) : previewMode === 'hand' ? (
              <div className="relative rotate-12 translate-y-6 translate-x-8 z-10 transition-transform duration-300">
                <AnimatedTotemPreview
                  imageUrl={totem.imageUrl}
                  isAnimated={totem.isAnimated}
                  frameCount={totem.frameCount}
                  frameRate={frameRate}
                  glow={totem.glow}
                  className="w-28 h-28"
                />
                <span className="absolute -bottom-4 left-0 text-[10px] text-slate-500 bg-slate-900/80 px-2 py-0.5 rounded">
                  Góc nhìn cầm tay (Off-hand)
                </span>
              </div>
            ) : (
              <div className="relative z-10">
                <AnimatedTotemPreview
                  imageUrl={totem.imageUrl}
                  isAnimated={totem.isAnimated}
                  frameCount={totem.frameCount}
                  frameRate={frameRate}
                  glow={totem.glow}
                  className="w-28 h-28"
                />
              </div>
            )}

            {/* Preview controls bar inside preview box */}
            <div className="absolute bottom-3 left-3 right-3 flex items-center justify-between text-xs z-20">
              <div className="flex items-center gap-1.5 bg-slate-900/90 backdrop-blur-sm p-1 rounded-xl border border-slate-800">
                <button
                  onClick={() => setPreviewMode('normal')}
                  className={`px-2.5 py-1 rounded-lg transition-colors cursor-pointer ${
                    previewMode === 'normal' ? 'bg-amber-500 text-slate-950 font-semibold' : 'text-slate-400 hover:text-slate-200'
                  }`}
                >
                  Gốc (2D)
                </button>
                <button
                  onClick={() => setPreviewMode('hand')}
                  className={`px-2.5 py-1 rounded-lg transition-colors cursor-pointer ${
                    previewMode === 'hand' ? 'bg-amber-500 text-slate-950 font-semibold' : 'text-slate-400 hover:text-slate-200'
                  }`}
                >
                  Cầm tay
                </button>
                <button
                  onClick={triggerPopSimulation}
                  className="px-2.5 py-1 rounded-lg bg-amber-500/20 hover:bg-amber-500/30 text-amber-300 border border-amber-500/30 transition-colors cursor-pointer flex items-center gap-1 font-medium"
                >
                  <Volume2 className="w-3.5 h-3.5" strokeWidth={1.75} />
                  Kích hoạt Pop!
                </button>
              </div>

              {totem.isAnimated && (
                <div className="hidden sm:flex items-center gap-2 bg-slate-900/90 backdrop-blur-sm px-2.5 py-1 rounded-xl border border-slate-800 text-slate-400 text-[11px]">
                  <Sliders className="w-3 h-3 text-amber-400" strokeWidth={1.75} />
                  <span>{frameRate} FPS</span>
                  <input
                    type="range"
                    min="1"
                    max="30"
                    value={frameRate}
                    onChange={(e) => setFrameRate(Number(e.target.value))}
                    className="w-16 accent-amber-500 h-1 cursor-pointer"
                  />
                </div>
              )}
            </div>

          </div>

          {/* Details & Specs */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-3 text-center">
            <div className="p-3 bg-slate-950/60 rounded-xl border border-slate-800/80">
              <span className="text-[10px] text-slate-500 block uppercase font-semibold">Tác giả</span>
              <span className="text-xs font-semibold text-slate-200 mt-0.5 block">{totem.author}</span>
            </div>
            <div className="p-3 bg-slate-950/60 rounded-xl border border-slate-800/80">
              <span className="text-[10px] text-slate-500 block uppercase font-semibold">Dạng Totem</span>
              <span className="text-xs font-semibold text-slate-200 mt-0.5 block">
                {totem.isAnimated ? `${totem.frameCount} Frames Anim` : 'Tĩnh (Static)'}
              </span>
            </div>
            <div className="p-3 bg-slate-950/60 rounded-xl border border-slate-800/80">
              <span className="text-[10px] text-slate-500 block uppercase font-semibold">Phát sáng</span>
              <span className="text-xs font-semibold text-amber-400 mt-0.5 block">
                {totem.glow ? 'Emissive Fullbright' : 'Standard'}
              </span>
            </div>
            <div className="p-3 bg-slate-950/60 rounded-xl border border-slate-800/80">
              <span className="text-[10px] text-slate-500 block uppercase font-semibold">Ngày tạo</span>
              <span className="text-xs font-semibold text-slate-200 mt-0.5 block">{totem.createdAt || 'Mới đây'}</span>
            </div>
          </div>

          {/* Description */}
          <div>
            <h4 className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1.5">Mô tả</h4>
            <p className="text-sm text-slate-300 leading-relaxed bg-slate-950/40 p-3.5 rounded-xl border border-slate-800/60">
              {totem.description}
            </p>
          </div>

          {/* Fast Link for In-Game Mod */}
          <div className="p-4 rounded-2xl bg-amber-500/5 border border-amber-500/20">
            <div className="flex items-center justify-between mb-2">
              <span className="text-xs font-bold text-amber-300 flex items-center gap-1.5">
                <Sparkles className="w-3.5 h-3.5" strokeWidth={1.75} />
                Đường dẫn tải trực tiếp vào Minecraft Mod
              </span>
              <span className="text-[10px] text-slate-400">Nhấn phím K trong game &rarr; Tải Qua URL</span>
            </div>
            <div className="flex items-center gap-2">
              <input
                type="text"
                readOnly
                value={getFullDownloadUrl()}
                className="flex-1 bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-300 font-mono select-all focus:outline-none"
              />
              <button
                onClick={handleCopyLink}
                className={`px-3 py-2 rounded-xl text-xs font-semibold flex items-center gap-1.5 transition-all cursor-pointer ${
                  copied
                    ? 'bg-emerald-500 text-slate-950'
                    : 'bg-amber-400 hover:bg-amber-300 text-slate-950'
                }`}
              >
                {copied ? (
                  <>
                    <Check className="w-3.5 h-3.5" strokeWidth={2} />
                    <span>Đã chép</span>
                  </>
                ) : (
                  <>
                    <Copy className="w-3.5 h-3.5" strokeWidth={1.75} />
                    <span>Sao chép</span>
                  </>
                )}
              </button>
            </div>
          </div>

        </div>

        {/* Footer */}
        <div className="flex items-center justify-end gap-3 px-6 py-4 border-t border-slate-800 bg-slate-950/50">
          <button
            onClick={onClose}
            className="px-4 py-2 text-xs font-medium text-slate-400 hover:text-slate-200 transition-colors cursor-pointer"
          >
            Đóng
          </button>
          <button
            onClick={handleDownload}
            className="inline-flex items-center gap-2 px-4 py-2 text-xs font-semibold rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-100 border border-slate-700 transition-colors cursor-pointer"
          >
            <Download className="w-4 h-4 text-slate-300" strokeWidth={1.75} />
            <span>Tải file .PNG về máy</span>
          </button>
        </div>

      </div>
    </div>
  );
}
