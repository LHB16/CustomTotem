import React, { useState } from 'react';
import { Download, Copy, Check, Sparkles, Eye, Volume2, Layers } from 'lucide-react';
import AnimatedTotemPreview from './AnimatedTotemPreview';
import { playTotemSound } from '../utils/audio';

export default function TotemCard({ totem, onClick }) {
  const [copied, setCopied] = useState(false);

  const getFullDownloadUrl = () => {
    if (totem.downloadUrl.startsWith('http://') || totem.downloadUrl.startsWith('https://')) {
      return totem.downloadUrl;
    }
    return window.location.origin + totem.downloadUrl;
  };

  const handleCopyLink = (e) => {
    e.stopPropagation();
    const url = getFullDownloadUrl();
    navigator.clipboard.writeText(url);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleDownload = (e) => {
    e.stopPropagation();
    const link = document.createElement('a');
    link.href = totem.downloadUrl;
    link.download = `${totem.title.toLowerCase().replace(/\s+/g, '_')}.png`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
  };

  const handlePlaySound = (e) => {
    e.stopPropagation();
    playTotemSound();
  };

  return (
    <div
      onClick={onClick}
      className="group relative bg-slate-900/70 hover:bg-slate-900/90 border border-slate-800/80 hover:border-amber-500/40 rounded-2xl p-4 transition-all duration-300 hover:shadow-xl hover:shadow-amber-500/5 cursor-pointer flex flex-col justify-between"
    >
      {/* Top badges */}
      <div className="flex items-center justify-between mb-3">
        <span className="text-[11px] font-medium px-2 py-0.5 rounded-full bg-slate-800 text-slate-300 border border-slate-700/60">
          {totem.category}
        </span>
        <div className="flex items-center gap-1.5">
          {totem.glow && (
            <span className="inline-flex items-center gap-1 text-[10px] font-semibold px-1.5 py-0.5 rounded bg-amber-500/10 text-amber-300 border border-amber-500/20" title="Phát sáng Emissive Fullbright">
              <Sparkles className="w-3 h-3" strokeWidth={1.75} />
              Glow
            </span>
          )}
          {totem.isAnimated ? (
            <span className="inline-flex items-center gap-1 text-[10px] font-semibold px-1.5 py-0.5 rounded bg-emerald-500/10 text-emerald-300 border border-emerald-500/20">
              <Layers className="w-3 h-3" strokeWidth={1.75} />
              {totem.frameCount}F Anim
            </span>
          ) : (
            <span className="text-[10px] font-medium px-1.5 py-0.5 rounded bg-slate-800/60 text-slate-400">
              Static
            </span>
          )}
        </div>
      </div>

      {/* Center Totem Graphic */}
      <div className="relative py-6 flex items-center justify-center bg-slate-950/60 rounded-xl border border-slate-800/50 group-hover:border-slate-800 transition-colors">
        <div className="w-24 h-24 flex items-center justify-center">
          <AnimatedTotemPreview
            imageUrl={totem.imageUrl}
            isAnimated={totem.isAnimated}
            frameCount={totem.frameCount}
            frameRate={totem.frameRate || 10}
            glow={totem.glow}
            className="w-20 h-20"
          />
        </div>

        {/* Floating Sound Test Button */}
        <button
          onClick={handlePlaySound}
          title="Nghe thử âm thanh Totem"
          className="absolute bottom-2 right-2 p-1.5 rounded-lg bg-slate-900/90 hover:bg-amber-500/20 border border-slate-800 hover:border-amber-500/30 text-slate-400 hover:text-amber-400 opacity-0 group-hover:opacity-100 transition-all cursor-pointer"
        >
          <Volume2 className="w-3.5 h-3.5" strokeWidth={1.75} />
        </button>
      </div>

      {/* Info */}
      <div className="mt-3.5 flex-1">
        <h3 className="font-bold text-slate-100 group-hover:text-amber-400 transition-colors text-base line-clamp-1">
          {totem.title}
        </h3>
        <p className="text-xs text-slate-400 mt-1 line-clamp-2 min-h-[32px]">
          {totem.description}
        </p>
        <div className="flex items-center gap-2 mt-2.5 text-[11px] text-slate-500">
          <span>Bởi <strong className="text-slate-300 font-medium">{totem.author}</strong></span>
          <span>•</span>
          <span>{totem.downloads || 0} lượt tải</span>
        </div>
      </div>

      {/* Actions */}
      <div className="mt-4 pt-3 border-t border-slate-800/60 flex items-center gap-2">
        <button
          onClick={handleCopyLink}
          className={`flex-1 inline-flex items-center justify-center gap-1.5 py-1.5 px-2.5 rounded-xl text-xs font-semibold border transition-all cursor-pointer ${
            copied
              ? 'bg-emerald-500/20 border-emerald-500/40 text-emerald-300'
              : 'bg-slate-800/80 hover:bg-slate-800 border-slate-700/60 text-slate-200 hover:text-white'
          }`}
          title="Copy link trực tiếp để dán vào Mod Minecraft (Nhấn K -> Tải Qua Đường Dẫn)"
        >
          {copied ? (
            <>
              <Check className="w-3.5 h-3.5 text-emerald-400" strokeWidth={2} />
              <span>Đã chép link</span>
            </>
          ) : (
            <>
              <Copy className="w-3.5 h-3.5 text-amber-400" strokeWidth={1.75} />
              <span>Copy Link Mod</span>
            </>
          )}
        </button>

        <button
          onClick={handleDownload}
          className="p-2 rounded-xl bg-slate-800/80 hover:bg-amber-500 hover:text-slate-950 border border-slate-700/60 text-slate-300 transition-all cursor-pointer"
          title="Tải ảnh PNG"
        >
          <Download className="w-3.5 h-3.5" strokeWidth={1.75} />
        </button>
      </div>
    </div>
  );
}
