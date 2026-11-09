use std::process::Command;
use totem_converter::converter::{self, CropMode};

#[test]
fn test_video_conversion() {
    let ffmpeg = converter::find_ffmpeg();
    assert!(ffmpeg.is_some(), "FFmpeg should be detected automatically");
    let ffmpeg_path = ffmpeg.unwrap();

    let test_mp4 = std::env::temp_dir().join("test_totem_vid.mp4");
    // Generate a 1-second test video using ffmpeg testsrc
    let status = Command::new(&ffmpeg_path)
        .args([
            "-f", "lavfi",
            "-i", "testsrc=duration=1:size=64x64:rate=10",
            "-pix_fmt", "yuv420p",
            "-y",
            test_mp4.to_str().unwrap(),
        ])
        .status()
        .expect("Failed to generate test mp4");
    assert!(status.success());

    // Test duration
    let dur = converter::get_video_duration(&ffmpeg_path, &test_mp4).unwrap();
    assert!(dur >= 0.8 && dur <= 1.2, "Duration should be ~1s, got {}", dur);

    // Test decode_video
    let frames = converter::decode_video(
        &ffmpeg_path,
        &test_mp4,
        0.0,
        1.0,
        10,
        64,
        CropMode::CenterCrop,
    ).unwrap();

    assert_eq!(frames.len(), 10, "Should have 10 frames at 10 fps");
    assert_eq!(frames[0].image.width(), 64);
    assert_eq!(frames[0].image.height(), 64);

    // Test build_spritesheet
    let sheet = converter::build_spritesheet(&frames, 64).unwrap();
    assert_eq!(sheet.width(), 64);
    assert_eq!(sheet.height(), 640);
    assert_eq!(sheet.height() % sheet.width(), 0);

    let _ = std::fs::remove_file(&test_mp4);
}
