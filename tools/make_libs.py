import subprocess
import os

MAPPINGS = [
    ("kernel32", "kernel32"),
    ("userenv", "userenv"),
    ("ws2_32", "ws2_32"),
    ("ntdll", "ntdll"),
    ("dbghelp", "dbghelp"),
    ("advapi32", "advapi32"),
    ("user32", "user32"),
    ("gdi32", "gdi32"),
    ("ole32", "ole32"),
    ("shell32", "shell32"),
    ("bcrypt", "bcrypt"),
    ("crypt32", "crypt32"),
    ("secur32", "secur32"),
    ("ucrtbase", "ucrt"),
    ("opengl32", "opengl32"),
    ("msimg32", "msimg32"),
    ("winspool.drv", "winspool"),
]

out_dir = os.path.abspath("tools/sdk_libs")
os.makedirs(out_dir, exist_ok=True)

bin_dir = r"C:\Program Files (x86)\Microsoft Visual Studio\2022\BuildTools\VC\Tools\MSVC\14.44.35207\bin\HostX64\x64"
dumpbin = os.path.join(bin_dir, "dumpbin.exe")
lib_exe = os.path.join(bin_dir, "lib.exe")

for src_dll, out_name in MAPPINGS:
    if "." in src_dll:
        dll_path = os.path.join(r"C:\Windows\System32", src_dll)
    else:
        dll_path = os.path.join(r"C:\Windows\System32", f"{src_dll}.dll")
    if not os.path.exists(dll_path):
        print(f"Skipping {src_dll}, not found in System32")
        continue

    # Dump exports
    cmd = [dumpbin, "/EXPORTS", dll_path]
    res = subprocess.run(cmd, capture_output=True, text=True)
    
    exports = []
    start = False
    for line in res.stdout.splitlines():
        if "ordinal" in line and "hint" in line:
            start = True
            continue
        if start:
            sline = line.strip()
            if not sline:
                continue
            if sline == "Summary" or sline.startswith("Summary"):
                break
            parts = sline.split()
            if len(parts) >= 4 and "(forwarded" in sline:
                name = parts[2]
                exports.append(name)
            elif len(parts) >= 4:
                name = parts[3]
                exports.append(name)
            elif len(parts) == 3 and not parts[0].isdigit():
                exports.append(parts[0])

    if not exports:
        print(f"No exports parsed for {src_dll}")
        continue

    def_file = os.path.join(out_dir, f"{out_name}.def")
    lib_file = os.path.join(out_dir, f"{out_name}.lib")
    with open(def_file, "w") as f:
        f.write(f"LIBRARY {src_dll}.dll\nEXPORTS\n")
        for exp in sorted(set(exports)):
            f.write(f"  {exp}\n")

    cmd = [lib_exe, f"/def:{def_file}", f"/out:{lib_file}", "/machine:x64", "/nologo"]
    lres = subprocess.run(cmd, capture_output=True, text=True)
    if lres.returncode == 0 and os.path.exists(lib_file):
        print(f"Generated {out_name}.lib ({len(exports)} exports, {os.path.getsize(lib_file)} bytes)")
    else:
        print(f"Failed to generate {out_name}.lib: {lres.stderr}")

print("Done generating import libraries!")
