#![windows_subsystem = "windows"]

mod app;
mod converter;

use app::TotemApp;
use eframe::egui;

fn main() -> eframe::Result<()> {
    let native_options = eframe::NativeOptions {
        viewport: egui::ViewportBuilder::default()
            .with_inner_size([860.0, 640.0])
            .with_min_inner_size([760.0, 580.0])
            .with_title("Minecraft Totem Video Converter")
            .with_drag_and_drop(true),
        ..Default::default()
    };

    eframe::run_native(
        "Minecraft Totem Video Converter",
        native_options,
        Box::new(|cc| Ok(Box::new(TotemApp::new(cc)))),
    )
}
