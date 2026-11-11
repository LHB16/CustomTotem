// Pure JavaScript Store-Mode (Uncompressed) ZIP Builder with CRC32
// Fast, zero-dependency, handles hundreds of PNG frames instantly without double-compressing PNG data.

const CRC_TABLE = (() => {
  const table = new Uint32Array(256);
  for (let i = 0; i < 256; i++) {
    let c = i;
    for (let k = 0; k < 8; k++) {
      c = c & 1 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
    }
    table[i] = c >>> 0;
  }
  return table;
})();

function crc32(bytes) {
  let crc = 0xffffffff;
  for (let i = 0; i < bytes.length; i++) {
    crc = CRC_TABLE[(crc ^ bytes[i]) & 0xff] ^ (crc >>> 8);
  }
  return (crc ^ 0xffffffff) >>> 0;
}

/**
 * Creates a valid .zip Blob from an array of { name: string, data: Uint8Array }
 * @param {Array<{name: string, data: Uint8Array}>} files
 * @returns {Blob}
 */
export function createZipBlob(files) {
  const encoder = new TextEncoder();
  const fileEntries = files.map((file) => {
    const nameBytes = encoder.encode(file.name);
    const data = file.data instanceof Uint8Array ? file.data : new Uint8Array(file.data);
    const crc = crc32(data);
    return {
      nameBytes,
      data,
      crc,
      size: data.byteLength,
      offset: 0,
    };
  });

  // Calculate total size
  let localAreaSize = 0;
  let centralDirSize = 0;
  for (const entry of fileEntries) {
    entry.offset = localAreaSize;
    localAreaSize += 30 + entry.nameBytes.length + entry.size;
    centralDirSize += 46 + entry.nameBytes.length;
  }

  const totalSize = localAreaSize + centralDirSize + 22;
  const out = new Uint8Array(totalSize);
  const view = new DataView(out.buffer);

  // Write Local File Headers + File Data
  let ptr = 0;
  for (const entry of fileEntries) {
    view.setUint32(ptr, 0x04034b50, true); // Local file header signature
    view.setUint16(ptr + 4, 20, true);     // Version needed to extract (2.0)
    view.setUint16(ptr + 6, 0x0800, true); // General purpose bit flag (UTF-8)
    view.setUint16(ptr + 8, 0, true);      // Compression method (0 = Store)
    view.setUint16(ptr + 10, 0, true);     // Last mod file time
    view.setUint16(ptr + 12, 0, true);     // Last mod file date
    view.setUint32(ptr + 14, entry.crc, true);  // CRC-32
    view.setUint32(ptr + 18, entry.size, true); // Compressed size
    view.setUint32(ptr + 22, entry.size, true); // Uncompressed size
    view.setUint16(ptr + 26, entry.nameBytes.length, true); // File name length
    view.setUint16(ptr + 28, 0, true);     // Extra field length
    ptr += 30;

    out.set(entry.nameBytes, ptr);
    ptr += entry.nameBytes.length;

    out.set(entry.data, ptr);
    ptr += entry.size;
  }

  const centralDirOffset = ptr;

  // Write Central Directory Headers
  for (const entry of fileEntries) {
    view.setUint32(ptr, 0x02014b50, true); // Central file header signature
    view.setUint16(ptr + 4, 20, true);     // Version made by
    view.setUint16(ptr + 6, 20, true);     // Version needed to extract
    view.setUint16(ptr + 8, 0x0800, true); // General purpose bit flag (UTF-8)
    view.setUint16(ptr + 10, 0, true);     // Compression method (0 = Store)
    view.setUint16(ptr + 12, 0, true);     // Last mod file time
    view.setUint16(ptr + 14, 0, true);     // Last mod file date
    view.setUint32(ptr + 16, entry.crc, true);  // CRC-32
    view.setUint32(ptr + 20, entry.size, true); // Compressed size
    view.setUint32(ptr + 24, entry.size, true); // Uncompressed size
    view.setUint16(ptr + 28, entry.nameBytes.length, true); // File name length
    view.setUint16(ptr + 30, 0, true);     // Extra field length
    view.setUint16(ptr + 32, 0, true);     // File comment length
    view.setUint16(ptr + 34, 0, true);     // Disk number start
    view.setUint16(ptr + 36, 0, true);     // Internal file attributes
    view.setUint32(ptr + 38, 0, true);     // External file attributes
    view.setUint32(ptr + 42, entry.offset, true); // Relative offset of local header
    ptr += 46;

    out.set(entry.nameBytes, ptr);
    ptr += entry.nameBytes.length;
  }

  // Write End of Central Directory Record (EOCD)
  view.setUint32(ptr, 0x06054b50, true); // End of central dir signature
  view.setUint16(ptr + 4, 0, true);      // Number of this disk
  view.setUint16(ptr + 6, 0, true);      // Disk where central directory starts
  view.setUint16(ptr + 8, fileEntries.length, true);  // Number of central directory records on this disk
  view.setUint16(ptr + 10, fileEntries.length, true); // Total number of central directory records
  view.setUint32(ptr + 12, centralDirSize, true);     // Size of central directory
  view.setUint32(ptr + 16, centralDirOffset, true);   // Offset of start of central directory
  view.setUint16(ptr + 20, 0, true);     // Comment length

  return new Blob([out], { type: 'application/zip' });
}

/**
 * Helper to convert an HTMLCanvasElement to a PNG Uint8Array
 * @param {HTMLCanvasElement} canvas
 * @returns {Promise<Uint8Array>}
 */
export function canvasToPngBytes(canvas) {
  return new Promise((resolve, reject) => {
    canvas.toBlob(
      async (blob) => {
        if (!blob) {
          reject(new Error('Không thể xuất frame từ Canvas'));
          return;
        }
        const buf = await blob.arrayBuffer();
        resolve(new Uint8Array(buf));
      },
      'image/png'
    );
  });
}
