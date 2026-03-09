use std::sync::Arc;
use std::time::Duration;

use anyhow::{anyhow, Context};
use wasmtime::{
    component::{Component, Linker},
    Config, Engine, Store,
};
use wasmtime_wasi::preview2::{
    self, DirPerms, FilePerms, Table, WasiCtx, WasiCtxBuilder, WasiView,
};

use crate::plugins::sdk::{
    PluginAdvisory, PluginConfigData, PluginEventData, PluginMetricData, ResourceLimits,
};

/// Configuration for the WASM runtime
#[derive(Debug, Clone)]
pub struct WasmRuntimeConfig {
    /// Maximum memory in bytes that the plugin can allocate
    pub max_memory: usize,
    /// Maximum number of CPU instructions (fuel) the plugin can execute
    pub max_fuel: u64,
    /// Maximum wall-clock time the plugin can run
    pub timeout: Duration,
    /// Whether to enable WASI preview2
    pub enable_wasi: bool,
    /// List of allowed host functions
    pub allowed_host_functions: Vec<String>,
    /// List of allowed environment variables
    pub allowed_env_vars: Vec<String>,
    /// List of allowed filesystem paths (prefixes)
    pub allowed_filesystem_paths: Vec<String>,
}

impl Default for WasmRuntimeConfig {
    fn default() -> Self {
        Self {
            max_memory: 16 * 1024 * 1024, // 16MB
            max_fuel: 1_000_000,           // 1M instructions
            timeout: Duration::from_secs(5),
            enable_wasi: true,
            allowed_host_functions: vec![
                "log".to_string(),
                "get_env".to_string(),
            ],
            allowed_env_vars: vec!["PATH".to_string(), "HOME".to_string()],
            allowed_filesystem_paths: vec![],
        }
    }
}

/// Context for a WASM plugin instance
pub struct WasmInstanceContext {
    wasi_ctx: WasiCtx,
    table: Table,
}

impl WasmInstanceContext {
    /// Create a new context with the given configuration
    pub fn new(config: &WasmRuntimeConfig) -> anyhow::Result<Self> {
        let mut wasi_builder = WasiCtxBuilder::new();
        
        // Initialize with minimal environment
        wasi_builder = wasi_builder.inherit_stdin();
        
        // Add allowed environment variables
        for var in &config.allowed_env_vars {
            if let Ok(value) = std::env::var(var) {
                wasi_builder = wasi_builder.env(var, &value)?;
            }
        }
        
        // Create the WASI context and table
        let wasi_ctx = wasi_builder.build();
        let table = Table::new();
        
        Ok(Self { wasi_ctx, table })
    }
}

/// A WebAssembly plugin host with resource limits and security constraints
pub struct WasmHost {
    engine: Engine,
    config: WasmRuntimeConfig,
}

impl WasmHost {
    /// Create a new WASM host with the given configuration
    pub fn new(config: WasmRuntimeConfig) -> anyhow::Result<Self> {
        let mut wasm_config = Config::new();
        
        // Enable WASM component model and WASI preview2
        wasm_config.wasm_component_model(true);
        wasm_config.wasm_multi_memory(true);
        wasm_config.wasm_multi_value(true);
        wasm_config.wasm_threads(false); // Disable threads for security
        
        // Enable fuel metering for CPU limits
        wasm_config.consume_fuel(true);
        
        // Set memory limits
        wasm_config.static_memory_maximum_size(0); // No static memory
        wasm_config.dynamic_memory_guard_size(wasmtime::Size::from_kb(64));
        wasm_config.static_memory_guard_size(wasmtime::Size::from_kb(64));
        wasm_config.dynamic_memory_reservation_for_maximum(0);
        
        // Create the engine with the configured settings
        let engine = Engine::new(&wasm_config)?;
        
        Ok(Self { engine, config })
    }
    
    /// Load and validate a WASM component
    pub fn load_component(&self, wasm_bytes: &[u8]) -> anyhow::Result<Component> {
        // Validate the WASM module before loading
        self.validate_wasm(wasm_bytes)?;
        
        // Create the component
        let component = Component::new(&self.engine, wasm_bytes)
            .context("Failed to create WASM component")?;
            
        Ok(component)
    }
    
    /// Validate WASM module against security constraints
    fn validate_wasm(&self, wasm_bytes: &[u8]) -> anyhow::Result<()> {
        // Check for forbidden imports/exports
        // This is a simplified example - in production, you'd want more thorough validation
        
        // Use wasmparser to analyze the module
        use wasmparser::Parser;
        
        for payload in Parser::new(0).parse_all(wasm_bytes) {
            let payload = payload?;
            
            // Check for forbidden sections
            match payload {
                wasmparser::Payload::ImportSection(imports) => {
                    for import in imports {
                        let import = import?;
                        // Check if the import is allowed
                        if !self.config.allowed_host_functions
                            .iter()
                            .any(|f| f == import.name)
                        {
                            return Err(anyhow!(
                                "Forbidden import: {}::{}",
                                import.module,
                                import.name
                            ));
                        }
                    }
                }
                _ => {}
            }
        }
        
        Ok(())
    }
    
    /// Create a new store for a plugin instance
    pub fn create_store(&self) -> anyhow::Result<Store<WasmInstanceContext>> {
        let instance_ctx = WasmInstanceContext::new(&self.config)?;
        let mut store = Store::new(&self.engine, instance_ctx);
        
        // Set the fuel limit
        store.add_fuel(self.config.max_fuel)?;
        
        // Set the timeout
        if !self.config.timeout.is_zero() {
            store.set_epoch_deadline(1);
            // Note: In a real implementation, you'd need to handle timeouts more carefully
            // This is a simplified example
        }
        
        Ok(store)
    }
    
    /// Create a linker with the host functions for our SDK
    pub fn create_linker(&self) -> anyhow::Result<Linker<WasmInstanceContext>> {
        let mut linker = Linker::new(&self.engine);
        
        // Add WASI preview2 support
        if self.config.enable_wasi {
            wasmtime_wasi::preview2::command::add_to_linker(&mut linker)
                .context("Failed to add WASI to linker")?;
        }
        
        // Add our SDK host functions
        self.add_sdk_functions(&mut linker)?;
        
        Ok(linker)
    }
    
    /// Add SDK host functions to the linker
    fn add_sdk_functions(
        &self,
        linker: &mut Linker<WasmInstanceContext>,
    ) -> anyhow::Result<()> {
        // Add logging function
        linker
            .func_wrap("sdk", "log", |caller: wasmtime::StoreContextMut<'_, WasmInstanceContext>, ptr: u32, len: u32| {
                // In a real implementation, this would read the string from wasm memory
                // and log it using the host's logging system
                println!("[WASM] Log: {} bytes at {}", len, ptr);
                Ok(())
            })?;
            
        // Add more SDK functions as needed...
        
        Ok(())
    }
    
    /// Execute a plugin with the given input
    pub async fn execute_plugin(
        &self,
        component: &Component,
        input: &PluginEventData,
    ) -> anyhow::Result<Vec<PluginAdvisory>> {
        let mut store = self.create_store()?;
        let mut linker = self.create_linker()?;
        
        // Instantiate the component
        let instance = linker.instantiate_async(&mut store, component).await?;
        
        // Get the "process_event" function if it exists
        if let Ok(process_event) = instance
            .get_typed_func::<(PluginEventData,), Vec<PluginAdvisory>>(&mut store, "process_event")
        {
            // Call the function with the input
            let result = process_event.call_async(&mut store, (input.clone(),)).await?;
            Ok(result)
        } else {
            // Fall back to default behavior
            Ok(Vec::new())
        }
    }
}
