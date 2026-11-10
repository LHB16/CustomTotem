# TotemHub - Thư Viện Chia Sẻ Custom Totem

Trang web chia sẻ và xem trước các mẫu Totem of Undying tùy chỉnh dành cho [Custom Totem Mod](https://github.com/LHB16/CustomTotem).

## 🚀 Tính Năng

- 🎨 **Bộ sưu tập Totem**: Duyệt các mẫu totem phân loại theo Thể loại (Fantasy, Magic, Cosmic, Anime, PvP, Meme).
- 🎞️ **Xem trước động (Strip Animation)**: Tự động chạy ảnh động dải dọc ngay trên web với tùy chỉnh FPS mượt mà.
- 🔊 **Mô phỏng trong game & Âm thanh**: Xem thử góc nhìn cầm tay (Off-hand), hiệu ứng Pop khi kích hoạt và nghe thử âm thanh.
- 📋 **Copy Link Mod siêu tốc**: Bấm 1 nút để lấy link dán vào trong game (phím `K` -> Tải Qua URL).
- 📤 **Đăng Totem Mới**: Đăng tải ảnh totem trực tiếp từ máy tính hoặc link web, tự động nhận diện số frame animation, lưu trữ an toàn trong trình duyệt.
- 💾 **Xuất file JSON**: Xuất danh sách `totems.json` để chia sẻ hoặc cập nhật vào mã nguồn.

---

## 💻 Cách Khởi Chạy Local

1. Mở terminal tại thư mục `web/`:
```bash
cd web
npm install
npm run dev
```

2. Mở trình duyệt tại: `http://localhost:3000`

---

## 🌐 Triển Khai Lên Web (Hosting Miễn Phí)

### Cách 1: Vercel (Khuyên dùng - Nhanh nhất)
1. Đăng nhập [vercel.com](https://vercel.com) bằng tài khoản GitHub.
2. Chọn **"Add New Project"** và chọn repository `CustomTotem`.
3. Tại mục **Root Directory**, bấm chọn thư mục `web`.
4. Bấm **Deploy**. Sau 30 giây, bạn sẽ có một link web chính thức (VD: `https://custom-totem.vercel.app`) hoàn toàn miễn phí!

### Cách 2: GitHub Pages
1. Trong file `web/vite.config.js`, thêm thuộc tính `base: '/CustomTotem/'` nếu deploy lên `https://<user>.github.io/CustomTotem/`.
2. Chạy lệnh:
```bash
npm run build
```
3. Đẩy thư mục `dist` lên nhánh `gh-pages` hoặc dùng GitHub Actions.
