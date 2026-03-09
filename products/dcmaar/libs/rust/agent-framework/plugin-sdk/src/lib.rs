pub trait Plugin {
    fn name(&self) -> &'static str;
    fn init(&mut self) {}
    fn on_metric(&mut self, _name: &str, _value: f64) {}
    fn on_event(&mut self, _ty: &str, _msg: &str) {}
}
