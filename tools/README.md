# Minecraft Totem Video Converter (Rust)

Ứng dụng desktop native siêu nhẹ viết bằng **Rust**, dùng để chuyển đổi video (`.mp4`, `.webm`, `.avi`, `.mov`), ảnh động (`.gif`), **thư mục chuỗi ảnh (Image Sequence)** (`.png`, `.jpg`, `.jpeg`, `.webp`, `.bmp`) và **dải ảnh Totem PNG có sẵn** thành **Vertical Spritesheet PNG** đúng chuẩn của `CustomTotemMod`.

## 🚀 Tính năng nổi bật
* **1 file `.exe` duy nhất:** Độc lập hoàn toàn, không cần cài đặt Python, Node.js hay môi trường phụ.
* **Khởi động tức thì & Tiết kiệm tài nguyên:** Khởi động dưới 0.1 giây, tiêu tốn chưa đến 30 MB RAM.
* **Đa dạng nguồn đầu vào:** Hỗ trợ Video, GIF, Thư mục chứa chuỗi ảnh (frame_0000.png, v.v.) và tự động phân tách dải ảnh Totem Spritesheet PNG có sẵn.
* **Tích hợp Nikke Live2D Online:** Nút bấm **"🌐 Tải Nikke Live2D..."** mở trực tiếp **Nikke Totem Studio** với hơn 50 nhân vật, hỗ trợ xuất dải Totem PNG trong suốt chỉ trong 0.5 giây!
* **Sắp xếp tự nhiên (Natural Sort):** Tự động nhận diện và ghép các file ảnh trong thư mục theo đúng thứ tự thời gian chữ và số.
* **Kéo thả trực tiếp:** Kéo thả video, GIF, thư mục ảnh hoặc file Totem PNG vào cửa sổ app.
* **Trình xem trước (Preview Animation):** Chạy hiệu ứng động vòng lặp trực tiếp với tốc độ mượt mà.
* **Bố cục Spritesheet linh hoạt:** Hỗ trợ cả **Dọc 1 cột** truyền thống và **Lưới 2D (Grid Atlas)** tự động tối ưu hóa khi số frame lớn (ví dụ 170+ frames), giữ nguyên chất lượng HD/Ultra HD 128x128 hoặc 256x256 mà không lo vượt giới hạn OpenGL của Minecraft.
* **Tùy chỉnh linh hoạt:**
  * Kích thước mỗi frame: 32x32, 64x64, 128x128, 256x256, 512x512, 1024x1024.
  * Tốc độ khung hình (FPS): 10, 15, 20.
  * Căn chỉnh hình ảnh: Center Crop (cắt vuông từ tâm), Fit (giữ trọn hình với viền trong suốt), Stretch.
  * Cắt thời gian video: Chọn thời gian bắt đầu và kết thúc (Start/End).
* **Xuất file & Cài đặt 1 click:**
  * Nút "Xuất file PNG...": Lưu file bất kỳ đâu.
  * Nút "Cài thẳng vào Minecraft": Tự động nạp file vào thư mục `.minecraft/config/custom_totems/images/`.

## 📦 File thực thi & Bộ công cụ phụ trợ
* **Totem Converter (`.exe`):** `tools/TotemConverter.exe`
* **Nikke Live2D Totem Studio:** `tools/nikke_capture/nikke_studio.html` (Giao diện Studio chọn nhân vật từ danh mục 50+ Nikke và xuất thẳng Totem 0.5s).
* **Nikke Bookmarklet / Userscript:** `tools/nikke_capture/install.html` (Tiện ích 1-click xuất dải Totem trực tiếp khi lướt `nikke.gg/live2d` hoặc `nikke-db.pages.dev`).
