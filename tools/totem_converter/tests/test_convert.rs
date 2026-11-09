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
