import React from 'react';
import { X, BookOpen, Keyboard, Download, Layers, Smartphone, Sparkles, CheckCircle2 } from 'lucide-react';

export default function InstructionsModal({ onClose }) {
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
              <BookOpen className="w-4 h-4" strokeWidth={1.75} />
            </div>
            <div>
              <h2 className="text-base font-bold text-slate-100">Hướng Dẫn Cài Totem Vào Game</h2>
              <p className="text-xs text-slate-400">Cách nhanh nhất để dùng các totem từ web này</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 rounded-xl hover:bg-slate-800 text-slate-400 hover:text-white transition-colors cursor-pointer"
          >
            <X className="w-5 h-5" strokeWidth={1.75} />
          </button>
        </div>

        {/* Steps */}
        <div className="p-6 overflow-y-auto space-y-4">
          
          {/* Method 1: Tải trực tiếp trong game qua URL (Khuyên dùng) */}
          <div className="p-4 bg-slate-950/60 rounded-2xl border border-amber-500/30 space-y-2.5">
            <div className="flex items-center gap-2">
              <span className="w-5 h-5 rounded-full bg-amber-400 text-slate-950 font-bold text-xs flex items-center justify-center">
                1
              </span>
              <h3 className="font-bold text-xs text-amber-300 uppercase tracking-wide">
                Cách 1: Tải trực tiếp bằng URL trong game (Siêu Nhanh)
              </h3>
            </div>
            <ul className="text-xs text-slate-300 space-y-2 pl-7 list-disc">
              <li>
                Trên trang web này, bấm nút <strong className="text-amber-400">"Copy Link Mod"</strong> ở bất kỳ mẫu Totem nào bạn thích.
              </li>
              <li>
                Vào thế giới Minecraft, nhấn phím <strong className="text-white bg-slate-800 px-1.5 py-0.5 rounded border border-slate-700">K</strong> để mở menu <strong>Custom Totem Manager</strong>.
              </li>
              <li>
                Chọn mục <strong className="text-white">"Tải Qua Đường Dẫn (Download via URL)"</strong>.
              </li>
              <li>
                Dán đường link vừa sao chép và bấm <strong>Tải Xuống</strong>. Mod sẽ tự tải và kích hoạt totem ngay lập tức!
              </li>
            </ul>
          </div>

          {/* Method 2: Tải file thủ công */}
          <div className="p-4 bg-slate-950/40 rounded-2xl border border-slate-800 space-y-2.5">
            <div className="flex items-center gap-2">
              <span className="w-5 h-5 rounded-full bg-slate-800 text-slate-300 font-bold text-xs flex items-center justify-center border border-slate-700">
                2
              </span>
              <h3 className="font-bold text-xs text-slate-300 uppercase tracking-wide">
                Cách 2: Tải file .PNG bỏ vào thư mục
              </h3>
            </div>
            <ul className="text-xs text-slate-400 space-y-2 pl-7 list-disc">
              <li>
                Bấm biểu tượng tải xuống để lưu file <code className="text-slate-300 font-mono">.png</code> về máy tính.
              </li>
              <li>
                Trong menu mod (phím <strong className="text-white">K</strong>), nhấn <strong>"Mở Thư Mục Chứa Ảnh"</strong> (hoặc vào thư mục <code className="text-slate-300 font-mono">.minecraft/customtotem/</code>).
              </li>
              <li>
                Kéo file ảnh vào đó, sau đó nhấn nút <strong>"Làm Mới Danh Sách"</strong> là có thể chọn dùng.
              </li>
            </ul>
          </div>

          {/* Tips */}
          <div className="p-4 bg-slate-950/20 rounded-2xl border border-slate-800 text-xs text-slate-400 space-y-2">
            <span className="font-semibold text-slate-300 block flex items-center gap-1.5">
              <Sparkles className="w-3.5 h-3.5 text-amber-400" strokeWidth={1.75} />
              Mẹo hay với Custom Totem Mod:
            </span>
            <p>
              • Bạn có thể tinh chỉnh độ sáng phát sáng (Emissive fullbright), kích thước phóng to/thu nhỏ, xoay góc cầm tay hoặc đổi nền hòm đồ trong mục <strong>Cấu Hình Hiển Thị</strong> và <strong>Cấu Hình Hòm Đồ</strong>!
            </p>
          </div>

        </div>

        {/* Footer */}
        <div className="flex items-center justify-end px-6 py-4 border-t border-slate-800 bg-slate-950/50">
          <button
            onClick={onClose}
            className="px-5 py-2 text-xs font-semibold rounded-xl bg-amber-400 hover:bg-amber-300 text-slate-950 transition-colors cursor-pointer"
          >
            Đã Hiểu
          </button>
        </div>

      </div>
    </div>
  );
}
