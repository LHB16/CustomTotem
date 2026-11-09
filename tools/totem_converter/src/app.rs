use crate::converter::{self, CropMode, FrameData, SpritesheetLayout};
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
    layout: SpritesheetLayout,

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
            layout: SpritesheetLayout::Auto,

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

    pub fn load_folder(&mut self, path: PathBuf, ctx: &egui::Context) {
        self.input_path = Some(path.clone());
        if let Some(folder_name) = path.file_name() {
            self.totem_name = folder_name.to_string_lossy().to_string();
        }
        self.process_folder(&path, ctx);
    }

    fn process_folder(&mut self, path: &std::path::Path, ctx: &egui::Context) {
        self.status_message = Some(("Đang nạp chuỗi ảnh từ thư mục...".to_string(), false));
        self.is_processing = true;

        match converter::load_image_sequence(path, self.target_size, self.crop_mode, self.fps) {
            Ok(frames) => {
                let count = frames.len();
                self.frames = frames;
                self.current_frame_idx = 0;
                self.update_preview_texture(ctx);
                self.status_message = Some((
                    format!(
                        "Nạp thành công {} khung hình từ thư mục! (Kích thước frame: {}x{} px)",
                        count, self.target_size, self.target_size
                    ),
                    false,
                ));
            }
            Err(e) => {
                self.status_message = Some((format!("Lỗi nạp thư mục ảnh: {}", e), true));
            }
        }
        self.is_processing = false;
    }

    fn process_gif(&mut self, path: &std::path::Path, ctx: &egui::Context) {
        self.status_message = Some(("Đang giải mã GIF...".to_string(), false));
        match converter::decode_gif(path, self.target_size, self.crop_mode) {
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
    }

    fn reload_current(&mut self, ctx: &egui::Context) {
        let Some(ref path) = self.input_path.clone() else { return };
        if path.is_dir() {
            self.process_folder(&path, ctx);
        } else {
            let ext = path
                .extension()
                .and_then(|e| e.to_str())
                .unwrap_or("")
                .to_lowercase();
            if ext == "gif" {
                self.process_gif(&path, ctx);
            } else {
                self.process_video(ctx);
            }
        }
    }

    fn load_file(&mut self, path: PathBuf, ctx: &egui::Context) {
        if path.is_dir() {
            self.load_folder(path, ctx);
            return;
        }

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
            self.process_gif(&path, ctx);
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

        match converter::build_output_spritesheet(&self.frames, self.target_size, self.layout) {
            Ok((sheet, cols, rows)) => {
                let (w, h) = sheet.dimensions();
                let frame_count = self.frames.len();

                // If grid layout and filename does not yet have grid info, append _gridWxH_Nf and _fps
                let final_path = {
                    let file_stem = target_path.file_stem().unwrap_or_default().to_string_lossy();
                    let ext = target_path.extension().unwrap_or_default().to_string_lossy();
                    let mut stem_str = file_stem.to_string();
                    if cols > 1 && !stem_str.contains("_grid") {
                        stem_str = format!("{}_grid{}x{}_{}f", stem_str, cols, rows, frame_count);
                    }
                    if !stem_str.to_lowercase().contains("fps") {
                        stem_str = format!("{}_{}fps", stem_str, self.fps);
                    }
                    target_path.with_file_name(format!("{}.{}", stem_str, ext))
                };

                match sheet.save(&final_path) {
                    Ok(()) => {
                        // Also write companion sidecar JSON metadata (including grid and FPS info)
                        let json_path = final_path.with_extension("json");
                        let meta_content = format!(
                            "{{\n  \"columns\": {},\n  \"rows\": {},\n  \"frameCount\": {},\n  \"fps\": {}\n}}\n",
                            cols, rows, frame_count, self.fps
                        );
                        let _ = std::fs::write(&json_path, meta_content);

                        let layout_desc = if cols > 1 {
                            format!("Lưới 2D {}x{} ô", cols, rows)
                        } else {
                            "Dọc 1 cột".to_string()
                        };

                        self.status_message = Some((
                            format!(
                                "Xuất thành công: {} ({}x{} px, {} frames, {})!",
                                final_path.display(),
                                w,
                                h,
                                frame_count,
                                layout_desc
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
            let frame_count = self.frames.len() as u32;
            let use_grid = match self.layout {
                SpritesheetLayout::Grid => true,
                SpritesheetLayout::Vertical => false,
                SpritesheetLayout::Auto => (frame_count * self.target_size) > 8192,
            };

            let file_name = if use_grid {
                let (cols, rows) = converter::calculate_optimal_grid(frame_count);
                format!("{}_grid{}x{}_{}f_{}fps.png", clean_name, cols, rows, frame_count, self.fps)
            } else {
                format!("{}_{}fps.png", clean_name, self.fps)
            };

            let file_path = dir.join(file_name);
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
                ui.label(RichText::new("Chuyển đổi Video / GIF / Thư mục ảnh thành Totem động dạng Vertical Spritesheet chuẩn Minecraft").color(Color32::LIGHT_GRAY));
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
            ui.label(RichText::new("1. Chọn nguồn ảnh (Video, GIF hoặc Thư mục)").strong().size(15.0));
            ui.add_space(4.0);

            ui.horizontal_wrapped(|ui| {
                if ui.button("📂 Chọn Video / GIF...").clicked() {
                    let mut dialog = rfd::FileDialog::new()
                        .add_filter("Video & GIF", &["mp4", "gif", "webm", "avi", "mov", "mkv"]);

                    if let Some(ref p) = app.input_path {
                        if p.is_dir() {
                            dialog = dialog.set_directory(p);
                        } else if let Some(parent) = p.parent() {
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

                if ui.button("📁 Chọn Thư mục ảnh...").clicked() {
                    let mut dialog = rfd::FileDialog::new();

                    if let Some(ref p) = app.input_path {
                        if p.is_dir() {
                            dialog = dialog.set_directory(p);
                        } else if let Some(parent) = p.parent() {
                            dialog = dialog.set_directory(parent);
                        }
                    } else if let Ok(userprofile) = std::env::var("USERPROFILE") {
                        let downloads = PathBuf::from(&userprofile).join("Downloads");
                        if downloads.exists() {
                            dialog = dialog.set_directory(downloads);
                        }
                    }

                    if let Some(folder) = dialog.pick_folder() {
                        app.load_folder(folder, ctx);
                    }
                }
            });

            ui.add_space(4.0);
            if let Some(ref p) = app.input_path {
                let file_name = p.file_name().unwrap_or_default().to_string_lossy();
                if p.is_dir() {
                    ui.label(RichText::new(format!("📁 Thư mục: {}", file_name)).strong().color(Color32::from_rgb(255, 205, 90)));
                } else {
                    ui.label(RichText::new(format!("📄 File: {}", file_name)).strong().color(Color32::from_rgb(100, 200, 255)));
                }
            } else {
                ui.label(RichText::new("Kéo thả file hoặc thư mục vào cửa sổ...").italics().color(Color32::GRAY));
            }

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
                            app.reload_current(ctx);
                        }
                    }
                }
            });
            ui.add_space(6.0);

            // FPS
            ui.horizontal_wrapped(|ui| {
                ui.label("Tốc độ khung hình (FPS):");
                let fps_options = [10, 15, 20, 30, 45, 60];
                for f in fps_options {
                    let label = format!("{} FPS", f);
                    if ui.selectable_label(app.fps == f, label).clicked() {
                        app.fps = f;
                        if app.input_path.is_some() {
                            app.reload_current(ctx);
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
                            if app.input_path.is_some() { app.reload_current(ctx); }
                        }
                        if ui.selectable_value(&mut app.crop_mode, CropMode::Fit, CropMode::Fit.label()).clicked() {
                            if app.input_path.is_some() { app.reload_current(ctx); }
                        }
                        if ui.selectable_value(&mut app.crop_mode, CropMode::Stretch, CropMode::Stretch.label()).clicked() {
                            if app.input_path.is_some() { app.reload_current(ctx); }
                        }
                    });
            });
            ui.add_space(6.0);

            // Spritesheet Layout
            ui.horizontal(|ui| {
                ui.label("Bố cục ảnh:");
                egui::ComboBox::from_id_salt("layout_mode_combo")
                    .selected_text(app.layout.label())
                    .show_ui(ui, |ui| {
                        ui.selectable_value(&mut app.layout, SpritesheetLayout::Auto, SpritesheetLayout::Auto.label());
                        ui.selectable_value(&mut app.layout, SpritesheetLayout::Grid, SpritesheetLayout::Grid.label());
                        ui.selectable_value(&mut app.layout, SpritesheetLayout::Vertical, SpritesheetLayout::Vertical.label());
                    });
            });

            // Video Time Trimming (chỉ hiển thị khi file là video)
            if let Some(ref p) = app.input_path {
                if !p.is_dir() {
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

                    let frame_count = app.frames.len() as u32;
                    let is_grid = match app.layout {
                        SpritesheetLayout::Grid => true,
                        SpritesheetLayout::Vertical => false,
                        SpritesheetLayout::Auto => (frame_count * app.target_size) > 8192,
                    };

                    let (out_w, out_h, grid_text) = if is_grid {
                        let (cols, rows) = converter::calculate_optimal_grid(frame_count);
                        (app.target_size * cols, app.target_size * rows, format!("Lưới 2D: {}x{} ô ({}f)", cols, rows, frame_count))
                    } else {
                        (app.target_size, app.target_size * frame_count, format!("Dọc 1 cột: {}f", frame_count))
                    };

                    ui.label(RichText::new(format!("Kích thước xuất: {} x {} px ({})", out_w, out_h, grid_text)).size(12.0).color(Color32::LIGHT_GRAY));

                    if out_h > 16384 || out_w > 16384 {
                        ui.label(RichText::new("⚠ Vượt quá giới hạn OpenGL 16384px! Hãy chuyển sang 'Lưới 2D'").color(Color32::from_rgb(255, 100, 100)).size(12.0));
                    } else {
                        ui.label(RichText::new("✔ Kích thước an toàn tuyệt đối cho Minecraft").color(Color32::from_rgb(100, 255, 150)).size(12.0));
                    }
                }
            });

            ui.add_space(14.0);
            ui.separator();
            ui.add_space(14.0);

            // Export Actions
            let can_export = !app.frames.is_empty();

            if ui.add_enabled(can_export, egui::Button::new(RichText::new("💾 Xuất file PNG...").size(15.0).strong())).clicked() {
                let frame_count = app.frames.len() as u32;
                let use_grid = match app.layout {
                    SpritesheetLayout::Grid => true,
                    SpritesheetLayout::Vertical => false,
                    SpritesheetLayout::Auto => (frame_count * app.target_size) > 8192,
                };

                let default_name = if use_grid {
                    let (cols, rows) = converter::calculate_optimal_grid(frame_count);
                    format!("{}_grid{}x{}_{}f_{}fps.png", app.totem_name.replace(' ', "_"), cols, rows, frame_count, app.fps)
                } else {
                    format!("{}_{}fps.png", app.totem_name.replace(' ', "_"), app.fps)
                };

                let mut dialog = rfd::FileDialog::new()
                    .add_filter("PNG Image", &["png"])
                    .set_file_name(&default_name);

                // Smart default directory:
                // 1. Same folder as the input video/gif or the image sequence folder itself
                // 2. Pictures folder (safe and writable)
                if let Some(ref p) = app.input_path {
                    let dir = if p.is_dir() {
                        p.as_path()
                    } else if let Some(parent) = p.parent() {
                        parent
                    } else {
                        p.as_path()
                    };
                    dialog = dialog.set_directory(dir);
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
