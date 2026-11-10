// Dữ liệu mẫu ban đầu cho Thư viện Totem
export const INITIAL_TOTEMS = [
  {
    id: "sample-golden-phoenix",
    title: "Golden Phoenix Totem",
    author: "BinhLH",
    category: "Fantasy",
    description: "Totem Phượng Hoàng Hoàng Kim với ánh sáng vàng rực rỡ và độ phát sáng Unshaded cao.",
    imageUrl: "/totems/sample_1_golden_phoenix.png",
    downloadUrl: "/totems/sample_1_golden_phoenix.png",
    isAnimated: false,
    frameCount: 1,
    glow: true,
    tags: ["Gold", "Phoenix", "Mythic", "Glow"],
    downloads: 142,
    createdAt: "2026-11-09"
  },
  {
    id: "sample-sacred-heart",
    title: "Sacred Heart Animated",
    author: "BinhLH",
    category: "Magic",
    description: "Trái tim thiêng liêng đập nhịp nhàng với hiệu ứng hào quang tỏa sáng xung quanh.",
    imageUrl: "/totems/sample_2_sacred_heart_anim.png",
    downloadUrl: "/totems/sample_2_sacred_heart_anim.png",
    isAnimated: true,
    frameCount: 8,
    frameRate: 10,
    glow: true,
    tags: ["Heart", "Animated", "Magic", "Holy"],
    downloads: 289,
    createdAt: "2026-11-09"
  },
  {
    id: "sample-cosmic-ender",
    title: "Cosmic Ender Totem",
    author: "BinhLH",
    category: "Cosmic",
    description: "Năng lượng Hư Không vũ trụ với luồng sóng tím xoay chuyển liên tục cực kỳ ảo diệu.",
    imageUrl: "/totems/sample_3_cosmic_ender_anim.png",
    downloadUrl: "/totems/sample_3_cosmic_ender_anim.png",
    isAnimated: true,
    frameCount: 8,
    frameRate: 12,
    glow: true,
    tags: ["Ender", "Cosmic", "Purple", "Animated"],
    downloads: 367,
    createdAt: "2026-11-09"
  }
];

export const CATEGORIES = [
  { id: "all", label: "Tất cả" },
  { id: "Fantasy", label: "Huyền bí / Fantasy" },
  { id: "Magic", label: "Ma thuật / Magic" },
  { id: "Cosmic", label: "Vũ trụ / Cosmic" },
  { id: "Anime", label: "Anime / Manga" },
  { id: "PvP", label: "PvP Clean" },
  { id: "Meme", label: "Hài hước / Meme" }
];
