// Dynamic Loader & Renderer for Esoteric Software Spine WebGL (v4.1 & v4.0 fallback)
// Supports both remote URLs (Nikke-DB / Nikke.gg) and local drag-and-drop files (.skel/.json, .atlas, .png)

const SPINE_41_CDN = 'https://cdn.jsdelivr.net/npm/@esotericsoftware/spine-webgl@4.1.24/dist/iife/spine-webgl.min.js';
const SPINE_40_CDN = 'https://cdn.jsdelivr.net/npm/@esotericsoftware/spine-webgl@4.0.64/dist/iife/spine-webgl.min.js';

let spineRuntimeCache = {};

/**
 * Loads the spine-webgl runtime into an isolated global or returns cached runtime
 * @param {'4.1' | '4.0'} version
 */
export async function loadSpineRuntime(version = '4.1') {
  if (spineRuntimeCache[version]) {
    return spineRuntimeCache[version];
  }

  const url = version === '4.0' ? SPINE_40_CDN : SPINE_41_CDN;
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`Không thể tải thư viện Spine ${version} từ CDN (${response.status})`);
  }
  const code = await response.text();

  // Execute IIFE in an isolated scope so 4.0 and 4.1 don't overwrite each other
  const factory = new Function(`${code}; return spine;`);
  const spineInstance = factory();
  // In IIFE builds, classes may be directly on `spine` or under `spine.webgl`
  if (spineInstance.webgl) {
    Object.assign(spineInstance, spineInstance.webgl);
  }
  spineRuntimeCache[version] = spineInstance;
  return spineInstance;
}

/**
 * Inspects the binary header of a .skel file to detect whether it was exported with Spine 4.0 or 4.1
 */
export function detectSkelVersion(skelBuffer) {
  if (!skelBuffer) return '4.0';
  const bytes = skelBuffer instanceof Uint8Array ? skelBuffer : new Uint8Array(skelBuffer);
  const headerLen = Math.min(bytes.length, 64);
  let ascii = '';
  for (let i = 0; i < headerLen; i++) {
    const c = bytes[i];
    if (c >= 32 && c <= 126) ascii += String.fromCharCode(c);
  }
  if (ascii.includes('4.1.')) return '4.1';
  if (ascii.includes('4.0.')) return '4.0';
  return '4.0';
}

/**
 * Helper to load an HTMLImageElement with crossOrigin = "anonymous"
 */
export function loadImageElement(url) {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.crossOrigin = 'anonymous';
    img.onload = () => resolve(img);
    img.onerror = () => reject(new Error(`Không thể tải file ảnh texture: ${url}`));
    img.src = url;
  });
}

/**
 * Extracts all PNG filenames referenced inside a .atlas file
 */
export function parseAtlasPageNames(atlasText) {
  const lines = atlasText.split(/\r?\n/);
  const pages = [];
  for (let i = 0; i < lines.length; i++) {
    const line = lines[i].trim();
    if (!line) continue;
    // Page headers end with .png or .webp and do not contain ':'
    if (!line.includes(':') && /\.(png|webp|jpg|jpeg)$/i.test(line)) {
      pages.push(line);
    }
  }
  return pages;
}

/**
 * Calculates optimal grid dimensions for Minecraft Totem Spritesheet (same algorithm as TotemConverter.exe)
 */
export function calculateOptimalGrid(frameCount, targetSize) {
  if (frameCount * targetSize <= 8192) {
    return { cols: 1, rows: frameCount };
  }
  const cols = Math.ceil(Math.sqrt(frameCount));
  const rows = Math.ceil(frameCount / cols);
  return { cols, rows };
}
