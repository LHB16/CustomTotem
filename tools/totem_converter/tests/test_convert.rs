use image::{Rgba, RgbaImage};

#[test]
fn test_spritesheet_dimension() {
    let target_size = 64;
    let mut frames = Vec::new();
    for _ in 0..10 {
        let frame = RgbaImage::from_pixel(target_size, target_size, Rgba([255, 215, 0, 255]));
        frames.push(totem_converter::converter::FrameData {
            image: frame,
            delay_ms: 66,
        });
    }

    let sheet = totem_converter::converter::build_spritesheet(&frames, target_size).unwrap();
    let (w, h) = sheet.dimensions();
    assert_eq!(w, 64);
    assert_eq!(h, 640);
    assert_eq!(h % w, 0);
    assert_eq!(h / w, 10);
}

#[test]
fn test_large_resolutions_512_and_1024() {
    for size in [512, 1024] {
        let mut frames = Vec::new();
        for _ in 0..5 {
            let frame = RgbaImage::from_pixel(size, size, Rgba([100, 150, 200, 255]));
            frames.push(totem_converter::converter::FrameData {
                image: frame,
                delay_ms: 66,
            });
        }

        let sheet = totem_converter::converter::build_spritesheet(&frames, size).unwrap();
        let (w, h) = sheet.dimensions();
        assert_eq!(w, size);
        assert_eq!(h, size * 5);
        assert_eq!(h % w, 0);
        assert_eq!(h / w, 5);
    }
}

#[test]
fn test_natural_sort_ordering() {
    use totem_converter::converter::natural_sort_key;

    let mut names = vec![
        "frame_10.png",
        "frame_1.png",
        "frame_2.png",
        "frame_20.png",
        "frame_0000.png",
        "frame_0003.png",
    ];

    names.sort_by(|a, b| natural_sort_key(a).cmp(&natural_sort_key(b)));

    assert_eq!(
        names,
        vec![
            "frame_0000.png",
            "frame_1.png",
            "frame_2.png",
            "frame_0003.png",
            "frame_10.png",
            "frame_20.png",
        ]
    );
}

#[test]
fn test_load_image_sequence_folder() {
    use totem_converter::converter::{load_image_sequence, CropMode};
    use std::fs;

    let test_dir = std::env::temp_dir().join("totem_test_seq");
    let _ = fs::remove_dir_all(&test_dir);
    fs::create_dir_all(&test_dir).unwrap();

    let img = RgbaImage::from_pixel(32, 32, Rgba([255, 0, 0, 255]));
    img.save(test_dir.join("frame_10.png")).unwrap();
    img.save(test_dir.join("frame_2.png")).unwrap();
    img.save(test_dir.join("frame_1.png")).unwrap();

    let frames = load_image_sequence(&test_dir, 64, CropMode::CenterCrop, 15).unwrap();
    assert_eq!(frames.len(), 3);
    assert_eq!(frames[0].image.dimensions(), (64, 64));

    let _ = fs::remove_dir_all(&test_dir);
}

#[test]
fn test_grid_spritesheet_170_frames() {
    use totem_converter::converter::{calculate_optimal_grid, build_output_spritesheet, FrameData, SpritesheetLayout};

    let (cols, rows) = calculate_optimal_grid(170);
    assert_eq!(cols, 14);
    assert_eq!(rows, 13);
    assert!(cols * rows >= 170);

    let target_size = 128;
    let mut frames = Vec::new();
    for _ in 0..170 {
        frames.push(FrameData {
            image: RgbaImage::from_pixel(target_size, target_size, Rgba([255, 215, 0, 255])),
            delay_ms: 66,
        });
    }

    let (sheet, c, r) = build_output_spritesheet(&frames, target_size, SpritesheetLayout::Grid).unwrap();
    assert_eq!(c, 14);
    assert_eq!(r, 13);
    let (w, h) = sheet.dimensions();
    assert_eq!(w, 14 * 128); // 1792
    assert_eq!(h, 13 * 128); // 1664
    assert!(w <= 16384);
    assert!(h <= 16384);
}
