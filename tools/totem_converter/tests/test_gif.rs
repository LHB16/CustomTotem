use image::codecs::gif::GifEncoder;
use image::{Delay, Frame, Rgba, RgbaImage};
use std::fs::File;
use totem_converter::converter::{self, CropMode};

#[test]
fn test_gif_conversion() {
    let gif_path = std::env::temp_dir().join("test_animated.gif");
    {
        let file = File::create(&gif_path).unwrap();
        let mut encoder = GifEncoder::new(file);
        for i in 0..5 {
            let img = RgbaImage::from_pixel(32, 32, Rgba([i * 40, 200, 100, 255]));
            let frame = Frame::from_parts(img, 0, 0, Delay::from_numer_denom_ms(100, 1));
            encoder.encode_frame(frame).unwrap();
        }
    }

    let frames = converter::decode_gif(&gif_path, 64, CropMode::CenterCrop).unwrap();
    assert_eq!(frames.len(), 5);
    assert_eq!(frames[0].image.width(), 64);
    assert_eq!(frames[0].image.height(), 64);

    let sheet = converter::build_spritesheet(&frames, 64).unwrap();
    assert_eq!(sheet.width(), 64);
    assert_eq!(sheet.height(), 320);

    let _ = std::fs::remove_file(&gif_path);
}
