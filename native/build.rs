fn main() {
    slint_build::compile("ui/app.slint").expect("slint compile");

    #[cfg(windows)]
    {
        let mut res = winres::WindowsResource::new();
        res.set_icon("assets/airwave.ico");
        res.set("ProductName", "AIRWAVE PL-680");
        res.set("FileDescription", "AIRWAVE PL-680 · 中国网络收音机");
        res.compile().expect("winres");
    }
}
