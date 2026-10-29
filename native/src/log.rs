use std::io::Write;

fn path() -> std::path::PathBuf {
    let base = std::env::var("LOCALAPPDATA").unwrap_or_else(|_| ".".into());
    std::path::PathBuf::from(base).join("AIRWAVE Native").join("airwave.log")
}

pub fn log(msg: impl AsRef<str>) {
    let secs = std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0);
    let stamp = format!(
        "{:02}:{:02}:{:02}",
        (secs / 3600) % 24,
        (secs / 60) % 60,
        secs % 60
    );
    if let Ok(mut f) = std::fs::OpenOptions::new()
        .create(true)
        .append(true)
        .open(path())
    {
        let _ = writeln!(f, "[{stamp}] {}", msg.as_ref());
    }
}

pub fn prepare() {
    if let Ok(meta) = std::fs::metadata(path()) {
        if meta.len() > 512 * 1024 {
            let _ = std::fs::remove_file(path());
        }
    }
}
