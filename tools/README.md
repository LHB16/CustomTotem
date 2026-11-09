# Minecraft Totem Video Converter (Rust)

Ứng dụng desktop native siêu nhẹ viết bằng **Rust**, dùng để chuyển đổi video (`.mp4`, `.webm`, `.avi`, `.mov`), ảnh động (`.gif`) và **thư mục chuỗi ảnh (Image Sequence)** (`.png`, `.jpg`, `.jpeg`, `.webp`, `.bmp`) thành **Vertical Spritesheet PNG** đúng chuẩn của `CustomTotemMod`.

## 🚀 Tính năng nổi bật
* **1 file `.exe` duy nhất:** Độc lập hoàn toàn, không cần cài đặt Python, Node.js hay môi trường phụ.
* **Khởi động tức thì & Tiết kiệm tài nguyên:** Khởi động dưới 0.1 giây, tiêu tốn chưa đến 30 MB RAM.
* **Đa dạng nguồn đầu vào:** Hỗ trợ Video, GIF và Thư mục chứa chuỗi ảnh (frame_0000.png, frame_1.png, 0.png, v.v.).
* **Sắp xếp tự nhiên (Natural Sort):** Tự động nhận diện và ghép các file ảnh trong thư mục theo đúng thứ tự thời gian chữ và số.
* **Kéo thả trực tiếp:** Kéo thả video, GIF hoặc cả thư mục ảnh vào cửa sổ app.
* **Trình xem trước (Preview Animation):** Chạy hiệu ứng động vòng lặp trực tiếp với tốc độ mượt mà.
* **Tùy chỉnh linh hoạt:**
  * Kích thước mỗi frame: 32x32, 64x64, 128x128, 256x256, 512x512, 1024x1024.
  * Tốc độ khung hình (FPS): 10, 15, 20.
  * Căn chỉnh hình ảnh: Center Crop (cắt vuông từ tâm), Fit (giữ trọn hình với viền trong suốt), Stretch.
  * Cắt thời gian video: Chọn thời gian bắt đầu và kết thúc (Start/End).
* **Xuất file & Cài đặt 1 click:**
  * Nút "Xuất file PNG...": Lưu file bất kỳ đâu.
  * Nút "Cài thẳng vào Minecraft": Tự động nạp file vào thư mục `.minecraft/config/custom_totems/images/`.

## 📦 File thực thi
* Đường dẫn file: `tools/TotemConverter.exe`
