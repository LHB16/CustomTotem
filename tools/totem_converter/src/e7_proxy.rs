use std::collections::HashMap;
use std::io::{Read, Write};
use std::net::{TcpListener, TcpStream};
use std::process::Command;
use std::sync::{Mutex, OnceLock};
use std::thread;
use std::time::Duration;

#[cfg(windows)]
use std::os::windows::process::CommandExt;

const E7_STUDIO_HTML: &str = include_str!("../../e7_capture/e7_studio.html");
const MAX_CACHE_BYTES: usize = 192 * 1024 * 1024;
const MAX_SINGLE_CACHE_BYTES: usize = 32 * 1024 * 1024;

#[cfg(windows)]
const CREATE_NO_WINDOW: u32 = 0x0800_0000;

struct CacheStore {
    total_bytes: usize,
    entries: HashMap<String, (String, Vec<u8>)>,
}

static SERVER_PORT: Mutex<Option<u16>> = Mutex::new(None);
static CACHE: OnceLock<Mutex<CacheStore>> = OnceLock::new();

fn cache_store() -> &'static Mutex<CacheStore> {
    CACHE.get_or_init(|| {
        Mutex::new(CacheStore {
            total_bytes: 0,
            entries: HashMap::new(),
        })
    })
}

/// Ensures the localhost Epic Seven CORS proxy + studio host is running and returns its port.
pub fn ensure_server_started() -> Result<u16, String> {
    let mut guard = SERVER_PORT
        .lock()
        .map_err(|_| "Proxy lock bị lỗi".to_string())?;

    if let Some(port) = *guard {
        return Ok(port);
    }

    let listener = TcpListener::bind("127.0.0.1:0")
        .map_err(|e| format!("Không thể khởi chạy local server Epic Seven: {}", e))?;
    let port = listener
        .local_addr()
        .map_err(|e| format!("Không đọc được cổng local server: {}", e))?
        .port();

    thread::spawn(move || {
        for stream in listener.incoming().flatten() {
            thread::spawn(move || {
                handle_client(stream);
            });
        }
    });

    *guard = Some(port);
    Ok(port)
}

/// Launches the local proxy server (if needed) and opens Epic Seven Studio in the default browser.
pub fn open_e7_studio() -> Result<String, String> {
    let port = ensure_server_started()?;
    let url = format!("http://127.0.0.1:{}/e7_studio.html", port);

    let mut cmd = Command::new("cmd");
    cmd.args(["/c", "start", "", &url]);
    #[cfg(windows)]
    cmd.creation_flags(CREATE_NO_WINDOW);
    cmd.spawn()
        .map_err(|e| format!("Không thể mở trình duyệt cho Epic Seven Studio: {}", e))?;

    Ok(url)
}

pub fn is_safe_rel_path(rel: &str) -> bool {
    if rel.is_empty() || rel.starts_with('/') || rel.contains("..") || rel.contains("//") {
        return false;
    }
    rel.bytes()
        .all(|b| b.is_ascii_alphanumeric() || matches!(b, b'_' | b'.' | b'/' | b'-'))
}

pub fn guess_content_type(path: &str) -> &'static str {
    let lower = path.to_ascii_lowercase();
    if lower.ends_with(".json") {
        "application/json; charset=utf-8"
    } else if lower.ends_with(".js") {
        "application/javascript; charset=utf-8"
    } else if lower.ends_with(".png") {
        "image/png"
    } else if lower.ends_with(".webp") {
        "image/webp"
    } else if lower.ends_with(".atlas") || lower.ends_with(".txt") {
        "text/plain; charset=utf-8"
    } else if lower.ends_with(".html") {
        "text/html; charset=utf-8"
    } else {
        "application/octet-stream"
    }
}

pub fn split_curl_status_trailer(raw: &[u8]) -> Option<(&[u8], u16)> {
    const MARKER: &[u8] = b"\n__HTTP_CODE__:";
    let pos = raw.windows(MARKER.len()).rposition(|w| w == MARKER)?;
    let body = &raw[..pos];
    let code_bytes = &raw[pos + MARKER.len()..];
    let code_str = std::str::from_utf8(code_bytes).ok()?.trim();
    let status: u16 = code_str.parse().ok()?;
    Some((body, status))
}

fn read_http_request(stream: &mut TcpStream) -> Option<(String, String)> {
    let _ = stream.set_read_timeout(Some(Duration::from_secs(5)));
    let mut buf = Vec::with_capacity(1024);
    let mut chunk = [0u8; 1024];

    loop {
        let n = stream.read(&mut chunk).ok()?;
        if n == 0 {
            break;
        }
        buf.extend_from_slice(&chunk[..n]);
        if buf.windows(4).any(|w| w == b"\r\n\r\n") || buf.len() >= 16384 {
            break;
        }
    }

    let text = std::str::from_utf8(&buf).ok()?;
    let first_line = text.lines().next()?;
    let mut parts = first_line.split_whitespace();
    let method = parts.next()?.to_string();
    let raw_target = parts.next()?.to_string();
    let path = raw_target.split('?').next().unwrap_or("/").to_string();
    Some((method, path))
}

fn write_http_response(
    stream: &mut TcpStream,
    status_line: &str,
    content_type: &str,
    body: &[u8],
) {
    let header = format!(
        "HTTP/1.1 {}\r\nContent-Type: {}\r\nContent-Length: {}\r\nAccess-Control-Allow-Origin: *\r\nAccess-Control-Allow-Methods: GET, OPTIONS\r\nAccess-Control-Allow-Headers: *\r\nConnection: close\r\n\r\n",
        status_line,
        content_type,
        body.len()
    );
    let _ = stream.write_all(header.as_bytes());
    if !body.is_empty() {
        let _ = stream.write_all(body);
    }
    let _ = stream.flush();
}

fn fetch_upstream_cached(url: &str, rel: &str) -> Result<(u16, String, Vec<u8>), String> {
    if let Ok(guard) = cache_store().lock() {
        if let Some((ctype, data)) = guard.entries.get(url) {
            return Ok((200, ctype.clone(), data.clone()));
        }
    }

    let mut cmd = Command::new("curl.exe");
    cmd.args([
        "-sS",
        "-L",
        "--max-time",
        "45",
        "-A",
        "CustomTotemMod-E7Studio/1.0",
        "-w",
        "\n__HTTP_CODE__:%{http_code}",
        url,
    ]);
    #[cfg(windows)]
    cmd.creation_flags(CREATE_NO_WINDOW);

    let output = cmd
        .output()
        .map_err(|e| format!("Không chạy được curl.exe: {}", e))?;

    if !output.status.success() && output.stdout.is_empty() {
        let err = String::from_utf8_lossy(&output.stderr);
        return Err(format!("Lỗi tải tài nguyên từ upstream: {}", err.trim()));
    }

    let (body, status) = split_curl_status_trailer(&output.stdout)
        .ok_or_else(|| "Phản hồi curl không hợp lệ".to_string())?;
    let ctype = guess_content_type(rel).to_string();
    let body_vec = body.to_vec();

    if status == 200 && body_vec.len() <= MAX_SINGLE_CACHE_BYTES {
        if let Ok(mut guard) = cache_store().lock() {
            if guard.total_bytes + body_vec.len() > MAX_CACHE_BYTES {
                guard.entries.clear();
                guard.total_bytes = 0;
            }
            guard.total_bytes += body_vec.len();
            guard
                .entries
                .insert(url.to_string(), (ctype.clone(), body_vec.clone()));
        }
    }

    Ok((status, ctype, body_vec))
}

fn handle_client(mut stream: TcpStream) {
    let Some((method, path)) = read_http_request(&mut stream) else {
        return;
    };

    if method == "OPTIONS" {
        write_http_response(&mut stream, "204 No Content", "text/plain", &[]);
        return;
    }

    if method != "GET" {
        write_http_response(&mut stream, "405 Method Not Allowed", "text/plain", b"Method Not Allowed");
        return;
    }

    if path == "/favicon.ico" {
        write_http_response(&mut stream, "204 No Content", "image/x-icon", &[]);
        return;
    }

    if matches!(path.as_str(), "/" | "/index.html" | "/e7_studio.html") {
        write_http_response(
            &mut stream,
            "200 OK",
            "text/html; charset=utf-8",
            E7_STUDIO_HTML.as_bytes(),
        );
        return;
    }

    let upstream_url = if let Some(rel) = path.strip_prefix("/e7/") {
        if !is_safe_rel_path(rel) {
            write_http_response(&mut stream, "400 Bad Request", "text/plain", b"Invalid path");
            return;
        }
        (format!("https://e7codex.com/{}", rel), rel)
    } else if let Some(rel) = path.strip_prefix("/e7cdn/") {
        if !is_safe_rel_path(rel) {
            write_http_response(&mut stream, "400 Bad Request", "text/plain", b"Invalid path");
            return;
        }
        (format!("https://assets.e7codex.com/{}", rel), rel)
    } else {
        write_http_response(&mut stream, "404 Not Found", "text/plain", b"Not Found");
        return;
    };

    match fetch_upstream_cached(&upstream_url.0, upstream_url.1) {
        Ok((200, ctype, data)) => {
            write_http_response(&mut stream, "200 OK", &ctype, &data);
        }
        Ok((code, _, data)) => {
            let status_line = match code {
                404 => "404 Not Found",
                403 => "403 Forbidden",
                _ => "502 Bad Gateway",
            };
            write_http_response(&mut stream, status_line, "text/plain; charset=utf-8", &data);
        }
        Err(msg) => {
            write_http_response(
                &mut stream,
                "502 Bad Gateway",
                "text/plain; charset=utf-8",
                msg.as_bytes(),
            );
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_safe_rel_path_validation() {
        assert!(is_safe_rel_path("c1172/c1172.json"));
        assert!(is_safe_rel_path("data/units.json"));
        assert!(is_safe_rel_path("c2185_1/combat/c2185_1__luciella.atlas"));
        assert!(!is_safe_rel_path(""));
        assert!(!is_safe_rel_path("../secret"));
        assert!(!is_safe_rel_path("/abs/path"));
        assert!(!is_safe_rel_path("a//b"));
        assert!(!is_safe_rel_path("a?b=c"));
    }

    #[test]
    fn test_split_curl_status_trailer() {
        let sample = b"\x89PNG\r\n\x1a\nbinarydata\n__HTTP_CODE__:200";
        let (body, code) = split_curl_status_trailer(sample).expect("valid trailer");
        assert_eq!(code, 200);
        assert_eq!(body, b"\x89PNG\r\n\x1a\nbinarydata");
    }

    #[test]
    fn test_local_studio_server_serves_html() {
        let port = ensure_server_started().expect("server starts");
        let mut conn = TcpStream::connect(("127.0.0.1", port)).expect("connect");
        conn.write_all(b"GET /e7_studio.html HTTP/1.1\r\nHost: 127.0.0.1\r\n\r\n")
            .unwrap();
        let mut resp = String::new();
        conn.read_to_string(&mut resp).unwrap();
        assert!(resp.starts_with("HTTP/1.1 200 OK"));
        assert!(resp.contains("Access-Control-Allow-Origin: *"));
        assert!(resp.contains("Epic Seven Spine 60FPS Studio"));
    }
}
