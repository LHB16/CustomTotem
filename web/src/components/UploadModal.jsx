import React, { useState } from 'react';
import { X, Upload, Image as ImageIcon, Sparkles, Layers, FileUp, Check } from 'lucide-react';
import { CATEGORIES } from '../data/totems';

export default function UploadModal({ onClose, onAddTotem }) {
  const [title, setTitle] = useState('');
  const [author, setAuthor] = useState('BinhLH');
  const [category, setCategory] = useState('Fantasy');
  const [description, setDescription] = useState('');
  const [imageDataUrl, setImageDataUrl] = useState('');
  const [isAnimated, setIsAnimated] = useState(false);
  const [frameCount, setFrameCount] = useState(1);
  const [frameRate, setFrameRate] = useState(10);
  const [glow, setGlow] = useState(false);
  const [tags, setTags] = useState('');
  const [dragActive, setDragActive] = useState(false);

  const handleFile = (file) => {
    if (!file || !file.type.startsWith('image/')) {
      alert('Vui lòng chọn file hình ảnh (PNG, JPEG, WebP)!');
      return;
    }

    const reader = new FileReader();
    reader.onload = (e) => {
      const dataUrl = e.target.result;
      setImageDataUrl(dataUrl);

      // Auto-detect dimensions & animated strips
      const img = new Image();
      img.onload = () => {
        if (!title) {
          const cleanName = file.name.replace(/\.[^/.]+$/, '').replace(/[_-]/g, ' ');
          setTitle(cleanName.charAt(0).toUpperCase() + cleanName.slice(1));
        }

        // If height is a multiple of width (e.g. 16x128 -> 8 frames)
        if (img.height > img.width && img.height % img.width === 0) {
          const detectedFrames = Math.floor(img.height / img.width);
          if (detectedFrames > 1) {
            setIsAnimated(true);
            setFrameCount(detectedFrames);
          }
        }
      };
      img.src = dataUrl;
    };
    reader.readAsDataURL(file);
  };

  const handleDrop = (e) => {
    e.preventDefault();
    setDragActive(false);
    if (e.dataTransfer.files && e.dataTransfer.files[0]) {
      handleFile(e.dataTransfer.files[0]);
    }
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    if (!title.trim() || !imageDataUrl) {
      alert('Vui lòng nhập tên totem và chọn file ảnh!');
      return;
    }

    const tagList = tags
      ? tags.split(',').map((t) => t.trim()).filter(Boolean)
      : [category];

    const newTotem = {
      id: 'custom-' + Date.now(),
      title: title.trim(),
      author: author.trim() || 'Ẩn danh',
      category: category,
      description: description.trim() || 'Custom Totem of Undying tạo bởi ' + (author.trim() || 'người dùng'),
      imageUrl: imageDataUrl,
      downloadUrl: imageDataUrl,
      isAnimated: isAnimated,
      frameCount: isAnimated ? Math.max(1, frameCount) : 1,
      frameRate: isAnimated ? Math.max(1, frameRate) : 10,
      glow: glow,
      tags: tagList,
      downloads: 0,
      createdAt: new Date().toISOString().split('T')[0]
    };

    onAddTotem(newTotem);
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-950/80 backdrop-blur-sm animate-in fade-in duration-200">
      <div
        className="relative w-full max-w-xl bg-slate-900 border border-slate-800 rounded-3xl shadow-2xl overflow-hidden flex flex-col max-h-[90vh]"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-6 py-4 border-b border-slate-800">
          <div className="flex items-center gap-2">
            <div className="p-2 bg-amber-500/10 text-amber-400 rounded-xl border border-amber-500/20">
              <Upload className="w-4 h-4" strokeWidth={1.75} />
            </div>
            <div>
              <h2 className="text-base font-bold text-slate-100">Đăng Totem Mới</h2>
              <p className="text-xs text-slate-400">Chia sẻ mẫu totem của bạn lên thư viện</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-xl hover:bg-slate-800 text-slate-400 hover:text-white transition-colors cursor-pointer"
          >
            <X className="w-5 h-5" strokeWidth={1.75} />
          </button>
        </div>

        {/* Form Body */}
        <form onSubmit={handleSubmit} className="p-6 overflow-y-auto space-y-4">
          
          {/* File Upload Zone */}
          <div>
            <label className="block text-xs font-semibold text-slate-300 uppercase tracking-wider mb-2">
              File Hình Ảnh Totem (.PNG)
            </label>
            <div
              onDragOver={(e) => { e.preventDefault(); setDragActive(true); }}
              onDragLeave={() => setDragActive(false)}
              onDrop={handleDrop}
              className={`relative border-2 border-dashed rounded-2xl p-6 text-center transition-all cursor-pointer ${
                dragActive
                  ? 'border-amber-500 bg-amber-500/5'
                  : imageDataUrl
                  ? 'border-emerald-500/50 bg-emerald-500/5'
                  : 'border-slate-800 hover:border-slate-700 bg-slate-950/50'
              }`}
            >
              <input
                type="file"
                accept="image/*"
                onChange={(e) => e.target.files && handleFile(e.target.files[0])}
                className="absolute inset-0 w-full h-full opacity-0 cursor-pointer"
              />

              {imageDataUrl ? (
                <div className="flex items-center justify-center gap-4">
                  <img
                    src={imageDataUrl}
                    alt="Preview"
                    className="w-14 h-14 object-contain pixelated bg-slate-900 border border-slate-700 rounded-lg p-1"
                  />
                  <div className="text-left">
                    <span className="text-xs font-bold text-emerald-400 flex items-center gap-1">
                      <Check className="w-3.5 h-3.5" strokeWidth={2} />
                      Đã tải ảnh lên thành công
                    </span>
                    <span className="text-[11px] text-slate-400 block mt-0.5">Nhấp hoặc kéo thả để chọn ảnh khác</span>
                  </div>
                </div>
              ) : (
                <div className="flex flex-col items-center justify-center space-y-2">
                  <FileUp className="w-8 h-8 text-slate-500" strokeWidth={1.75} />
                  <div className="text-xs text-slate-300">
                    <span className="font-semibold text-amber-400">Nhấp để tải lên</span> hoặc kéo thả file PNG vào đây
                  </div>
                  <span className="text-[11px] text-slate-500">
                    Hỗ trợ ảnh tĩnh 16x16, 32x32 hoặc ảnh dải dọc (animated strip: 16x128,...)
                  </span>
                </div>
              )}
            </div>
          </div>

          {/* Title & Author */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-slate-300 mb-1">Tên Totem *</label>
              <input
                type="text"
                required
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                placeholder="VD: Neon Cyber Dragon"
                className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-200 focus:outline-none focus:border-amber-500"
              />
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-300 mb-1">Tác giả</label>
              <input
                type="text"
                value={author}
                onChange={(e) => setAuthor(e.target.value)}
                placeholder="VD: BinhLH"
                className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-200 focus:outline-none focus:border-amber-500"
              />
            </div>
          </div>

          {/* Category & Tags */}
          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-slate-300 mb-1">Thể loại</label>
              <select
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-200 focus:outline-none focus:border-amber-500"
              >
                {CATEGORIES.filter(c => c.id !== 'all').map(cat => (
                  <option key={cat.id} value={cat.id}>{cat.label}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-xs font-medium text-slate-300 mb-1">Thẻ tag (cách nhau bởi dấu phẩy)</label>
              <input
                type="text"
                value={tags}
                onChange={(e) => setTags(e.target.value)}
                placeholder="VD: Neon, Dragon, Animated"
                className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-200 focus:outline-none focus:border-amber-500"
              />
            </div>
          </div>

          {/* Description */}
          <div>
            <label className="block text-xs font-medium text-slate-300 mb-1">Mô tả chi tiết</label>
            <textarea
              rows={2}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Giới thiệu về mẫu totem, cảm hứng thiết kế..."
              className="w-full bg-slate-950 border border-slate-800 rounded-xl px-3 py-2 text-xs text-slate-200 focus:outline-none focus:border-amber-500 resize-none"
            />
          </div>

          {/* Mod Settings: Animated & Glow */}
          <div className="p-4 bg-slate-950/60 rounded-2xl border border-slate-800 space-y-3">
            <span className="text-xs font-semibold text-slate-400 block uppercase tracking-wider">
              Cấu hình hiển thị trong Mod
            </span>

            {/* Emissive Glow toggle */}
            <label className="flex items-center justify-between cursor-pointer">
              <span className="text-xs text-slate-300 flex items-center gap-1.5">
                <Sparkles className="w-3.5 h-3.5 text-amber-400" strokeWidth={1.75} />
                Hiệu ứng phát sáng Unshaded (Emissive Fullbright)
              </span>
              <input
                type="checkbox"
                checked={glow}
                onChange={(e) => setGlow(e.target.checked)}
                className="w-4 h-4 rounded accent-amber-500 cursor-pointer"
              />
            </label>

            {/* Animated toggle */}
            <label className="flex items-center justify-between cursor-pointer">
              <span className="text-xs text-slate-300 flex items-center gap-1.5">
                <Layers className="w-3.5 h-3.5 text-emerald-400" strokeWidth={1.75} />
                Ảnh động dải dọc (Animated Sprite Sheet)
              </span>
              <input
                type="checkbox"
                checked={isAnimated}
                onChange={(e) => setIsAnimated(e.target.checked)}
                className="w-4 h-4 rounded accent-amber-500 cursor-pointer"
              />
            </label>

            {isAnimated && (
              <div className="grid grid-cols-2 gap-3 pt-2 border-t border-slate-800">
                <div>
                  <label className="block text-[11px] text-slate-400 mb-1">Số frame (Frame Count)</label>
                  <input
                    type="number"
                    min="2"
                    max="64"
                    value={frameCount}
                    onChange={(e) => setFrameCount(Number(e.target.value))}
                    className="w-full bg-slate-900 border border-slate-800 rounded-lg px-2.5 py-1.5 text-xs text-slate-200"
                  />
                </div>
                <div>
                  <label className="block text-[11px] text-slate-400 mb-1">Tốc độ (FPS)</label>
                  <input
                    type="number"
                    min="1"
                    max="60"
                    value={frameRate}
                    onChange={(e) => setFrameRate(Number(e.target.value))}
                    className="w-full bg-slate-900 border border-slate-800 rounded-lg px-2.5 py-1.5 text-xs text-slate-200"
                  />
                </div>
              </div>
            )}

          </div>

          {/* Action buttons */}
          <div className="pt-2 flex items-center justify-end gap-3">
            <button
              type="button"
              onClick={onClose}
              className="px-4 py-2 text-xs font-medium text-slate-400 hover:text-slate-200 transition-colors cursor-pointer"
            >
              Hủy
            </button>
            <button
              type="submit"
              className="px-5 py-2.5 text-xs font-bold text-slate-950 bg-gradient-to-r from-amber-400 to-amber-500 hover:from-amber-300 hover:to-amber-400 rounded-xl shadow-lg shadow-amber-500/20 transition-all active:scale-95 cursor-pointer flex items-center gap-1.5"
            >
              <Upload className="w-3.5 h-3.5" strokeWidth={2} />
              <span>Lưu & Đăng Totem</span>
            </button>
          </div>

        </form>
      </div>
    </div>
  );
}
