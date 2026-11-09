use crate::converter::{self, CropMode, FrameData};
use eframe::egui::{self, Color32, ColorImage, RichText, TextureHandle, TextureOptions, Ui, Vec2};
use std::path::PathBuf;
use std::time::{Duration, Instant};

pub struct TotemApp {
    input_path: Option<PathBuf>,
    ffmpeg_path: Option<PathBuf>,
    totem_name: String,

    video_duration: f32,
    start_time: f32,
    end_time: f32,

    fps: u32,
    target_size: u32,
    crop_mode: CropMode,

    frames: Vec<FrameData>,
    current_frame_idx: usize,
    last_tick: Instant,
    is_playing: bool,

    preview_texture: Option<TextureHandle>,
    status_message: Option<(String, bool)>, // (message, is_error)
    is_processing: bool,
}

impl Default for TotemApp {
    fn default() -> Self {
        let ffmpeg = converter::find_ffmpeg();
        Self {
            input_path: None,
            ffmpeg_path: ffmpeg,
            totem_name: "custom_totem_anim".to_string(),

            video_duration: 3.0,
            start_time: 0.0,
            end_time: 3.0,

            fps: 15,
            target_size: 128,
            crop_mode: CropMode::CenterCrop,

            frames: Vec::new(),
            current_frame_idx: 0,
            last_tick: Instant::now(),
            is_playing: true,

            preview_texture: None,
            status_message: None,
            is_processing: false,
        }
    }
}

impl TotemApp {
    pub fn new(cc: &eframe::CreationContext<'_>) -> Self {
        setup_custom_fonts(&cc.egui_ctx);
        let mut app = Self::default();
        if app.ffmpeg_path.is_none() {
            app.status_message = Some((
                "Lưu ý: Không tìm thấy FFmpeg. File GIF vẫn xử lý bình thường, nhưng đối với Video (.mp4), vui lòng chọn đường dẫn ffmpeg.exe.".to_string(),
                false,
            ));
        }
        app
    }

    fn load_file(&mut self, path: PathBuf, ctx: &egui::Context) {
        self.input_path = Some(path.clone());
        if let Some(stem) = path.file_stem() {
            self.totem_name = stem.to_string_lossy().to_string();
        }

        let ext = path
            .extension()
            .and_then(|e| e.to_str())
            .unwrap_or("")
            .to_lowercase();

        if ext == "gif" {
            self.status_message = Some(("Đang giải mã GIF...".to_string(), false));
            match converter::decode_gif(&path, self.target_size, self.crop_mode) {
                Ok(frames) => {
                    self.status_message = Some((
                        format!("Đã nạp thành công {} khung hình từ GIF!", frames.len()),
                        false,
                    ));
                    self.frames = frames;
                    self.current_frame_idx = 0;
                    self.update_preview_texture(ctx);
                }
                Err(e) => {
                    self.status_message = Some((format!("Lỗi khi nạp GIF: {}", e), true));
                }
            }
        } else {
            // Video file
            if let Some(ref ff) = self.ffmpeg_path {
                match converter::get_video_duration(ff, &path) {
                    Ok(dur) => {
                        self.video_duration = dur;
                        self.start_time = 0.0;
                        self.end_time = dur.min(4.0); // Default to max 4 seconds for totem
                    }
                    Err(_) => {
                        self.video_duration = 5.0;
                        self.start_time = 0.0;
                        self.end_time = 3.0;
                    }
                }
                self.process_video(ctx);
            } else {
                self.status_message = Some((
                    "Cần file ffmpeg.exe để xử lý video. Hãy nhấn 'Chọn FFmpeg...' bên dưới.".to_string(),
                    true,
                ));
            }
        }
    }

    fn process_video(&mut self, ctx: &egui::Context) {
        let Some(ref path) = self.input_path else { return };
        let Some(ref ff) = self.ffmpeg_path else {
            self.status_message = Some(("Không tìm thấy FFmpeg!".to_string(), true));
            return;
        };

        let duration = (self.end_time - self.start_time).max(0.1);
        self.status_message = Some(("Đang trích xuất khung hình video...".to_string(), false));
        self.is_processing = true;

        match converter::decode_video(
            ff,
            path,
            self.start_time,
            duration,
            self.fps,
            self.target_size,
            self.crop_mode,
        ) {
            Ok(frames) => {
                let count = frames.len();
                self.frames = frames;
                self.current_frame_idx = 0;
                self.update_preview_texture(ctx);
                self.status_message = Some((
                    format!(
                        "Trích xuất thành công {} khung hình ({}x{} px)!",
                        count,
                        self.target_size,
                        self.target_size * count as u32
                    ),
                    false,
                ));
            }
            Err(e) => {
                self.status_message = Some((format!("Lỗi trích xuất video: {}", e), true));
            }
        }
        self.is_processing = false;
    }

    fn update_preview_texture(&mut self, ctx: &egui::Context) {
        if self.frames.is_empty() {
            self.preview_texture = None;
            return;
        }

        let frame = &self.frames[self.current_frame_idx % self.frames.len()];
        let (w, h) = frame.image.dimensions();
        let color_image = ColorImage::from_rgba_unmultiplied([w as usize, h as usize], frame.image.as_raw());

        self.preview_texture = Some(ctx.load_texture(
            "totem_preview",
            color_image,
            TextureOptions::NEAREST, // Pixel-perfect rendering for Minecraft
        ));
    }

    fn export_png(&mut self, target_path: PathBuf) {
        if self.frames.is_empty() {
            self.status_message = Some(("Chưa có khung hình nào để xuất!".to_string(), true));
            return;
        }

        match converter::build_spritesheet(&self.frames, self.target_size) {
            Ok(sheet) => {
                let (w, h) = sheet.dimensions();
                match sheet.save(&target_path) {
                    Ok(()) => {
                        self.status_message = Some((
                            format!(
                                "Xuất thành công: {} (Kích thước: {}x{} px, {} frames)!",
                                target_path.display(),
                                w,
                                h,
                                self.frames.len()
                            ),
                            false,
                        ));
                    }
                    Err(e) => {
                        self.status_message = Some((format!("Lỗi khi lưu file ảnh: {}", e), true));
                    }
                }
            }
            Err(e) => {
                self.status_message = Some((format!("Lỗi tạo spritesheet: {}", e), true));
            }
        }
    }

    fn export_to_minecraft(&mut self) {
        let mut target_dir = None;

        // Check local CustomTotemMod config
        let local_cfg = PathBuf::from("config/custom_totems/images");
        if local_cfg.exists() {
            target_dir = Some(local_cfg);
        } else if let Ok(appdata) = std::env::var("APPDATA") {
            let mc_dir = PathBuf::from(appdata).join(".minecraft").join("config").join("custom_totems").join("images");
            let _ = std::fs::create_dir_all(&mc_dir);
            if mc_dir.exists() {
                target_dir = Some(mc_dir);
            }
        }

        if let Some(dir) = target_dir {
            let clean_name = self.totem_name.replace(' ', "_").to_lowercase();
            let file_path = dir.join(format!("{}.png", clean_name));
            self.export_png(file_path);
        } else {
            self.status_message = Some((
                "Không tìm thấy thư mục .minecraft. Bạn hãy dùng nút 'Xuất file PNG...' để tự chọn vị trí lưu.".to_string(),
                true,
            ));
        }
    }
}

fn setup_custom_fonts(ctx: &egui::Context) {
    let mut fonts = egui::FontDefinitions::default();
    let font_candidates = [
        r"C:\Windows\Fonts\segoeui.ttf",
        r"C:\Windows\Fonts\arial.ttf",
        r"C:\Windows\Fonts\tahoma.ttf",
    ];

    for path in font_candidates {
        if let Ok(font_bytes) = std::fs::read(path) {
            fonts.font_data.insert(
                "system_vietnamese".to_owned(),
                egui::FontData::from_owned(font_bytes),
            );

            fonts
                .families
                .entry(egui::FontFamily::Proportional)
                .or_default()
                .insert(0, "system_vietnamese".to_owned());

            fonts
                .families
                .entry(egui::FontFamily::Monospace)
                .or_default()
                .insert(0, "system_vietnamese".to_owned());

            ctx.set_fonts(fonts);
            break;
        }
    }
}

impl eframe::App for TotemApp {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        // Handle drag and drop files
        ctx.input(|i| {
            if let Some(dropped) = i.raw.dropped_files.first() {
                if let Some(ref path) = dropped.path {
                    self.load_file(path.clone(), ctx);
                }
            }
        });

        // Animation frame ticking
        if self.is_playing && !self.frames.is_empty() {
            let delay = self.frames[self.current_frame_idx % self.frames.len()].delay_ms;
            let elapsed = self.last_tick.elapsed();

            if elapsed >= Duration::from_millis(delay as u64) {
                self.current_frame_idx = (self.current_frame_idx + 1) % self.frames.len();
                self.last_tick = Instant::now();
                self.update_preview_texture(ctx);
            }

            ctx.request_repaint_after(Duration::from_millis(delay as u64));
        }

        egui::CentralPanel::default().show(ctx, |ui| {
            // Header
            ui.vertical_centered(|ui| {
                ui.heading(RichText::new("Minecraft Totem Video Converter").size(24.0).strong().color(Color32::from_rgb(0, 210, 140)));
                ui.label(RichText::new("Chuyển đổi Video / GIF thành Totem động dạng Vertical Spritesheet chuẩn Minecraft").color(Color32::LIGHT_GRAY));
            });
            ui.add_space(10.0);

            // Status message
            if let Some((ref msg, is_err)) = self.status_message {
                let bg_color = if is_err { Color32::from_rgb(80, 20, 20) } else { Color32::from_rgb(20, 60, 40) };
                let text_color = if is_err { Color32::from_rgb(255, 120, 120) } else { Color32::from_rgb(140, 255, 180) };

                egui::Frame::none()
                    .fill(bg_color)
                    .rounding(6.0)
                    .inner_margin(8.0)
                    .show(ui, |ui| {
                        ui.horizontal(|ui| {
                            ui.label(RichText::new(if is_err { "⚠" } else { "✔" }).size(16.0).color(text_color));
                            ui.label(RichText::new(msg).color(text_color).size(13.0));
                        });
                    });
                ui.add_space(8.0);
            }

            // Main 2-column layout
            ui.columns(2, |columns| {
                // LEFT COLUMN: Controls & Settings
                render_left_panel(&mut columns[0], self, ctx);

                // RIGHT COLUMN: Preview & Export
                render_right_panel(&mut columns[1], self);
            });
        });
    }
}

fn render_left_panel(ui: &mut Ui, app: &mut TotemApp, ctx: &egui::Context) {
    egui::Frame::group(ui.style())
        .rounding(8.0)
        .inner_margin(12.0)
        .show(ui, |ui| {
            ui.label(RichText::new("1. Chọn file nguồn (Video hoặc GIF)").strong().size(15.0));
            ui.add_space(4.0);

            ui.horizontal(|ui| {
                if ui.button("📂 Chọn File Video / GIF...").clicked() {
                    let mut dialog = rfd::FileDialog::new()
                        .add_filter("Video & GIF", &["mp4", "gif", "webm", "avi", "mov", "mkv"]);

                    if let Some(ref p) = app.input_path {
                        if let Some(parent) = p.parent() {
                            dialog = dialog.set_directory(parent);
                        }
                    } else if let Ok(userprofile) = std::env::var("USERPROFILE") {
                        let downloads = PathBuf::from(&userprofile).join("Downloads");
                        if downloads.exists() {
                            dialog = dialog.set_directory(downloads);
                        }
                    }

                    if let Some(path) = dialog.pick_file() {
                        app.load_file(path, ctx);
                    }
                }

                if let Some(ref p) = app.input_path {
                    let file_name = p.file_name().unwrap_or_default().to_string_lossy();
                    ui.label(RichText::new(format!("📄 {}", file_name)).strong().color(Color32::from_rgb(100, 200, 255)));
                } else {
                    ui.label(RichText::new("Kéo thả file vào cửa sổ...").italics().color(Color32::GRAY));
                }
            });

            ui.add_space(10.0);
            ui.separator();
            ui.add_space(10.0);

            ui.label(RichText::new("2. Thiết lập thông số").strong().size(15.0));
            ui.add_space(6.0);

            // Totem Name
            ui.horizontal(|ui| {
                ui.label("Tên Totem:");
                ui.text_edit_singleline(&mut app.totem_name);
            });
            ui.add_space(6.0);

            // Resolution
            ui.horizontal_wrapped(|ui| {
                ui.label("Kích thước frame:");
                let sizes = [32, 64, 128, 256, 512, 1024];
                for s in sizes {
                    let label = format!("{}x{}", s, s);
                    if ui.selectable_label(app.target_size == s, label).clicked() {
                        app.target_size = s;
                        if app.input_path.is_some() {
                            app.process_video(ctx);
                        }
                    }
                }
            });
            ui.add_space(6.0);

            // FPS
            ui.horizontal(|ui| {
                ui.label("Tốc độ khung hình (FPS):");
                let fps_options = [10, 15, 20];
                for f in fps_options {
                    let label = format!("{} FPS", f);
                    if ui.selectable_label(app.fps == f, label).clicked() {
                        app.fps = f;
                        if app.input_path.is_some() {
                            app.process_video(ctx);
                        }
                    }
                }
            });
            ui.add_space(6.0);

            // Crop Mode
            ui.horizontal(|ui| {
                ui.label("Căn chỉnh:");
                egui::ComboBox::from_id_salt("crop_mode_combo")
                    .selected_text(app.crop_mode.label())
                    .show_ui(ui, |ui| {
                        if ui.selectable_value(&mut app.crop_mode, CropMode::CenterCrop, CropMode::CenterCrop.label()).clicked() {
                            if app.input_path.is_some() { app.process_video(ctx); }
                        }
                        if ui.selectable_value(&mut app.crop_mode, CropMode::Fit, CropMode::Fit.label()).clicked() {
                            if app.input_path.is_some() { app.process_video(ctx); }
                        }
                        if ui.selectable_value(&mut app.crop_mode, CropMode::Stretch, CropMode::Stretch.label()).clicked() {
                            if app.input_path.is_some() { app.process_video(ctx); }
                        }
                    });
            });

            // Video Time Trimming
            if let Some(ref p) = app.input_path {
                let ext = p.extension().and_then(|e| e.to_str()).unwrap_or("").to_lowercase();
                if ext != "gif" {
                    ui.add_space(10.0);
                    ui.separator();
                    ui.add_space(6.0);
                    ui.label(RichText::new("Cắt thời gian Video (giây)").strong());

                    ui.horizontal(|ui| {
                        ui.label("Bắt đầu:");
                        ui.add(egui::DragValue::new(&mut app.start_time).speed(0.1).range(0.0..=app.video_duration).suffix("s"));
                        ui.label("Kết thúc:");
                        ui.add(egui::DragValue::new(&mut app.end_time).speed(0.1).range(0.0..=app.video_duration).suffix("s"));
                    });

                    if ui.button("Áp dụng cắt thời gian").clicked() {
                        app.process_video(ctx);
                    }
                }
            }

            ui.add_space(10.0);
            ui.separator();
            ui.add_space(6.0);

            // FFmpeg path config
            ui.horizontal(|ui| {
                if app.ffmpeg_path.is_some() {
                    ui.label(RichText::new("✔ FFmpeg sẵn sàng").color(Color32::from_rgb(0, 200, 120)));
                } else {
                    ui.label(RichText::new("✖ Chưa có FFmpeg").color(Color32::from_rgb(255, 100, 100)));
                }

                if ui.button("Đổi FFmpeg...").clicked() {
                    if let Some(ff) = rfd::FileDialog::new()
                        .add_filter("FFmpeg executable", &["exe"])
                        .pick_file()
                    {
                        app.ffmpeg_path = Some(ff);
                    }
                }
            });
        });
}

fn render_right_panel(ui: &mut Ui, app: &mut TotemApp) {
    egui::Frame::group(ui.style())
        .rounding(8.0)
        .inner_margin(12.0)
        .show(ui, |ui| {
            ui.label(RichText::new("3. Xem trước Totem & Xuất file").strong().size(15.0));
            ui.add_space(8.0);

            // Preview Box
            let preview_size = Vec2::new(160.0, 160.0);
            ui.vertical_centered(|ui| {
                egui::Frame::none()
                    .fill(Color32::from_rgb(25, 25, 30))
                    .stroke(egui::Stroke::new(1.5_f32, Color32::from_rgb(50, 50, 60)))
                    .rounding(8.0)
                    .show(ui, |ui| {
                        let (rect, _response) = ui.allocate_exact_size(preview_size, egui::Sense::hover());

                        if let Some(ref tex) = app.preview_texture {
                            ui.painter().image(
                                tex.id(),
                                rect,
                                egui::Rect::from_min_max(egui::pos2(0.0, 0.0), egui::pos2(1.0, 1.0)),
                                Color32::WHITE,
                            );
                        } else {
                            ui.painter().text(
                                rect.center(),
                                egui::Align2::CENTER_CENTER,
                                "Chưa có ảnh\nxem trước",
                                egui::FontId::proportional(14.0),
                                Color32::GRAY,
                            );
                        }
                    });

                ui.add_space(6.0);
                if !app.frames.is_empty() {
                    ui.horizontal(|ui| {
                        let play_label = if app.is_playing { "⏸ Tạm dừng" } else { "▶ Tiếp tục" };
                        if ui.button(play_label).clicked() {
                            app.is_playing = !app.is_playing;
                        }

                        ui.label(format!(
                            "Frame: {} / {}",
                            app.current_frame_idx + 1,
                            app.frames.len()
                        ));
                    });

                    let total_h = app.target_size * app.frames.len() as u32;
                    ui.label(RichText::new(format!("Kích thước xuất: {} x {} px", app.target_size, total_h)).size(12.0).color(Color32::LIGHT_GRAY));
                }
            });

            ui.add_space(14.0);
            ui.separator();
            ui.add_space(14.0);

            // Export Actions
            let can_export = !app.frames.is_empty();

            if ui.add_enabled(can_export, egui::Button::new(RichText::new("💾 Xuất file PNG...").size(15.0).strong())).clicked() {
                let default_name = format!("{}.png", app.totem_name.replace(' ', "_"));
                let mut dialog = rfd::FileDialog::new()
                    .add_filter("PNG Image", &["png"])
                    .set_file_name(&default_name);

                // Smart default directory:
                // 1. Same folder as the input video/gif
                // 2. Pictures folder (safe and writable)
                if let Some(ref p) = app.input_path {
                    if let Some(parent) = p.parent() {
                        dialog = dialog.set_directory(parent);
                    }
                } else if let Ok(userprofile) = std::env::var("USERPROFILE") {
                    let pic_dir = PathBuf::from(&userprofile).join("Pictures");
                    if pic_dir.exists() {
                        dialog = dialog.set_directory(pic_dir);
                    }
                }

                if let Some(save_path) = dialog.save_file() {
                    app.export_png(save_path);
                }
            }

            ui.add_space(6.0);

            if ui.add_enabled(can_export, egui::Button::new(RichText::new("⚡ Cài thẳng vào Minecraft (.minecraft)").size(14.0))).clicked() {
                app.export_to_minecraft();
            }

            ui.add_space(6.0);

            if ui.button("📂 Mở thư mục Custom Totems...").clicked() {
                let path = if let Ok(appdata) = std::env::var("APPDATA") {
                    PathBuf::from(appdata).join(".minecraft").join("config").join("custom_totems").join("images")
                } else {
                    PathBuf::from("config/custom_totems/images")
                };
                let _ = std::process::Command::new("explorer").arg(path).spawn();
            }
        });
}
