#[no_mangle]
pub extern "C" fn plugin_name() -> *const u8 {
    // Cast to *const u8 expected by host
    c"echo-wasm".as_ptr() as *const u8
}

#[no_mangle]
pub extern "C" fn on_metric(_ptr: *const u8, _len: usize, _val: f64) {
    // mock: do nothing
}
