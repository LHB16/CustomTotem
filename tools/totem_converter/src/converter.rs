use image::{imageops, ImageBuffer, Rgba, RgbaImage};
use std::fs::File;
use std::io::{BufReader, Read};
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum CropMode {
    CenterCrop,
    Fit,
    Stretch,
}

impl CropMode {
    pub fn label(&self) -> &'static str {
        match self {
            CropMode::CenterCrop => "Center Crop (Cắt vuông tâm - Đẹp nhất)",
            CropMode::Fit => "Fit (Giữ nguyên tỉ lệ - Viền trong suốt)",
            CropMode::Stretch => "Stretch (Kéo giãn đầy khung hình)",
        }
    }
}

#[derive(Clone)]
pub struct FrameData {
    pub image: RgbaImage,
    pub delay_ms: u32,
}

pub fn find_ffmpeg() -> Option<PathBuf> {
    // 1. Current executable directory
    if let Ok(exe_path) = std::env::current_exe() {
        if let Some(dir) = exe_path.parent() {
            let p = dir.join("ffmpeg.exe");
            if p.exists() {
                return Some(p);
            }
        }
    }

    // 2. Current working directory
    let p = PathBuf::from("ffmpeg.exe");
    if p.exists() {
        return Some(p);
    }

    // 3. System PATH
    if let Ok(out) = Command::new("where.exe").arg("ffmpeg").output() {
        if out.status.success() {
            let s = String::from_utf8_lossy(&out.stdout);
            if let Some(line) = s.lines().next() {
                let p = PathBuf::from(line.trim());
                if p.exists() {
                    return Some(p);
                }
            }
        }
    }

    // 4. Python imageio-ffmpeg bundled binary in user's AppData
    if let Ok(local_app_data) = std::env::var("LOCALAPPDATA") {
        let python_dir = PathBuf::from(local_app_data).join("Programs").join("Python");
        if python_dir.exists() {
            if let Ok(entries) = std::fs::read_dir(&python_dir) {
                for entry in entries.flatten() {
                    let bin_dir = entry.path().join("Lib/site-packages/imageio_ffmpeg/binaries");
                    if bin_dir.exists() {
                        if let Ok(files) = std::fs::read_dir(&bin_dir) {
                            for f in files.flatten() {
                                let name = f.file_name().to_string_lossy().to_string();
                                if name.starts_with("ffmpeg-") && name.ends_with(".exe") {
                                    return Some(f.path());
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    None
}

pub fn decode_gif(path: &Path, target_size: u32, crop_mode: CropMode) -> Result<Vec<FrameData>, String> {
    let file = File::open(path).map_err(|e| format!("Không thể mở file GIF: {}", e))?;
    let reader = BufReader::new(file);

    use image::codecs::gif::GifDecoder;
    use image::AnimationDecoder;

    let decoder = GifDecoder::new(reader).map_err(|e| format!("Lỗi giải mã GIF: {}", e))?;
    let raw_frames = decoder.into_frames().collect_frames().map_err(|e| format!("Lỗi đọc khung hình GIF: {}", e))?;

    if raw_frames.is_empty() {
        return Err("File GIF không chứa khung hình nào".to_string());
    }

    let mut result = Vec::new();
    for f in raw_frames {
        let delay_ms = {
            let (num, denom) = f.delay().numer_denom_ms();
            if denom == 0 { 100 } else { (num / denom).max(20) }
        };
        let buffer = f.into_buffer();
        let processed = process_frame(&buffer, target_size, crop_mode);
        result.push(FrameData {
            image: processed,
            delay_ms,
        });
    }

    Ok(result)
}

pub fn get_video_duration(ffmpeg_path: &Path, path: &Path) -> Result<f32, String> {
    // Run ffmpeg -i <file> and parse Duration: 00:00:00.00 from stderr
    let output = Command::new(ffmpeg_path)
        .arg("-i")
        .arg(path)
        .stderr(Stdio::piped())
        .stdout(Stdio::null())
        .output()
        .map_err(|e| format!("Không thể thực thi ffmpeg: {}", e))?;

    let stderr = String::from_utf8_lossy(&output.stderr);
    for line in stderr.lines() {
        if let Some(pos) = line.find("Duration:") {
            let sub = &line[pos + 9..].trim();
            if let Some(comma) = sub.find(',') {
                let time_str = sub[..comma].trim();
                let parts: Vec<&str> = time_str.split(':').collect();
                if parts.len() == 3 {
                    let h: f32 = parts[0].parse().unwrap_or(0.0);
                    let m: f32 = parts[1].parse().unwrap_or(0.0);
                    let s: f32 = parts[2].parse().unwrap_or(0.0);
                    return Ok(h * 3600.0 + m * 60.0 + s);
                }
            }
        }
    }

    Ok(3.0) // Fallback default
}

pub fn decode_video(
    ffmpeg_path: &Path,
    video_path: &Path,
    start_sec: f32,
    duration_sec: f32,
    fps: u32,
    target_size: u32,
    crop_mode: CropMode,
) -> Result<Vec<FrameData>, String> {
    // Build ffmpeg filter
    let vf = match crop_mode {
        CropMode::CenterCrop => {
            format!(
                "fps={},scale={target}:{target}:force_original_aspect_ratio=increase,crop={target}:{target}",
                fps,
                target = target_size
            )
        }
        CropMode::Fit => {
            format!(
                "fps={},scale={target}:{target}:force_original_aspect_ratio=decrease,pad={target}:{target}:(ow-iw)/2:(oh-ih)/2:color=black@0.0",
                fps,
                target = target_size
            )
        }
        CropMode::Stretch => {
            format!("fps={},scale={target}:{target}", fps, target = target_size)
        }
    };

    let mut cmd = Command::new(ffmpeg_path);
    if start_sec > 0.0 {
        cmd.arg("-ss").arg(format!("{:.2}", start_sec));
    }
    if duration_sec > 0.0 {
        cmd.arg("-t").arg(format!("{:.2}", duration_sec));
    }

    cmd.arg("-i")
        .arg(video_path)
        .arg("-vf")
        .arg(&vf)
        .arg("-f")
        .arg("rawvideo")
        .arg("-pix_fmt")
        .arg("rgba")
        .arg("-");

    cmd.stdout(Stdio::piped()).stderr(Stdio::piped());

    let mut child = cmd.spawn().map_err(|e| format!("Không thể chạy FFmpeg: {}", e))?;
    let mut stdout = child.stdout.take().ok_or("Không thể mở stdout của FFmpeg")?;

    let frame_bytes = (target_size * target_size * 4) as usize;
    let mut buffer = vec![0u8; frame_bytes];
    let mut frames = Vec::new();
    let delay_ms = (1000 / fps).max(20);

    loop {
        match stdout.read_exact(&mut buffer) {
            Ok(()) => {
                if let Some(img) = ImageBuffer::<Rgba<u8>, _>::from_raw(target_size, target_size, buffer.clone()) {
                    frames.push(FrameData {
                        image: img,
                        delay_ms,
                    });
                }
            }
            Err(e) if e.kind() == std::io::ErrorKind::UnexpectedEof => {
                break;
            }
            Err(e) => {
                return Err(format!("Lỗi khi đọc khung hình video: {}", e));
            }
        }
    }

    let _ = child.wait();

    if frames.is_empty() {
        return Err("Không trích xuất được khung hình nào từ video".to_string());
    }

    Ok(frames)
}

pub fn process_frame(src: &RgbaImage, target_size: u32, crop_mode: CropMode) -> RgbaImage {
    let (orig_w, orig_h) = src.dimensions();
    if orig_w == 0 || orig_h == 0 {
        return RgbaImage::new(target_size, target_size);
    }

    match crop_mode {
        CropMode::Stretch => {
            imageops::resize(src, target_size, target_size, imageops::FilterType::Lanczos3)
        }
        CropMode::CenterCrop => {
            let side = orig_w.min(orig_h);
            let x = (orig_w - side) / 2;
            let y = (orig_h - side) / 2;
            let cropped = imageops::crop_imm(src, x, y, side, side).to_image();
            imageops::resize(&cropped, target_size, target_size, imageops::FilterType::Lanczos3)
        }
        CropMode::Fit => {
            let mut canvas = RgbaImage::from_pixel(target_size, target_size, Rgba([0, 0, 0, 0]));
            let scale = (target_size as f32 / orig_w as f32).min(target_size as f32 / orig_h as f32);
            let new_w = ((orig_w as f32 * scale).round() as u32).max(1);
            let new_h = ((orig_h as f32 * scale).round() as u32).max(1);
            let resized = imageops::resize(src, new_w, new_h, imageops::FilterType::Lanczos3);

            let offset_x = (target_size - new_w) / 2;
            let offset_y = (target_size - new_h) / 2;
            imageops::overlay(&mut canvas, &resized, offset_x as i64, offset_y as i64);
            canvas
        }
    }
}

pub fn build_spritesheet(frames: &[FrameData], target_size: u32) -> Result<RgbaImage, String> {
    if frames.is_empty() {
        return Err("Danh sách khung hình rỗng".to_string());
    }

    let frame_count = frames.len() as u32;
    let sheet_height = target_size * frame_count;

    // Safety limit: 16384 px max height for OpenGL textures
    if sheet_height > 16384 {
        return Err(format!(
            "Tổng chiều cao ảnh quá lớn ({sheet_height}px, {} frames x {target_size}px). Giới hạn tối đa là 16384px. Hãy giảm số FPS hoặc rút ngắn thời gian video.",
            frames.len()
        ));
    }

    let mut sheet = RgbaImage::new(target_size, sheet_height);

    for (i, frame) in frames.iter().enumerate() {
        let y_offset = (i as u32) * target_size;
        imageops::overlay(&mut sheet, &frame.image, 0, y_offset as i64);
    }

    Ok(sheet)
}
