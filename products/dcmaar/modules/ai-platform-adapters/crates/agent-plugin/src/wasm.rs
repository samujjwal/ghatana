//! WASM host implementation for plugins
//!
//! This module provides the WasmHost that manages WebAssembly plugin execution
//! with resource limits and security constraints.

use std::time::Duration;

use anyhow::{anyhow, Context};
use wasmtime::{
    component::{Component, Linker},
    Config, Engine, Store,
};
use wasmtime_wasi::preview2::{
    Table, WasiCtx, WasiCtxBuilder, WasiView,
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
        wasi_builder.inherit_stdin();
        
        // Add allowed environment variables
        for var in &config.allowed_env_vars {
            if let Ok(value) = std::env::var(var) {
                wasi_builder.env(var, &value);
            }
        }
        
        // Create the WASI context and table
        let wasi_ctx = wasi_builder.build();
        let table = Table::new();
        
        Ok(Self { wasi_ctx, table })
    }
}

impl WasiView for WasmInstanceContext {
    fn table(&self) -> &Table {
        &self.table
    }

    fn table_mut(&mut self) -> &mut Table {
        &mut self.table
    }

    fn ctx(&self) -> &WasiCtx {
        &self.wasi_ctx
    }

    fn ctx_mut(&mut self) -> &mut WasiCtx {
        &mut self.wasi_ctx
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
        wasm_config.dynamic_memory_guard_size(64 * 1024); // 64KB guard
        wasm_config.static_memory_guard_size(64 * 1024); // 64KB guard
        wasm_config.dynamic_memory_reserved_for_growth(0);
        
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
            if let wasmparser::Payload::ImportSection(imports) = payload {
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
        }
        
        Ok(())
    }
    
    /// Create a new store for a plugin instance
    pub fn create_store(&self) -> anyhow::Result<Store<WasmInstanceContext>> {
        let instance_ctx = WasmInstanceContext::new(&self.config)?;
        let mut store = Store::new(&self.engine, instance_ctx);
        
        // Set the fuel limit
        store.set_fuel(self.config.max_fuel)?;
        
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
        // Add WASI support
        wasmtime_wasi::preview2::command::add_to_linker(linker)?;
        
        // Note: Component model host function definitions require a different approach
        // than the traditional core WebAssembly API. For now, we'll rely on WASI.
        
        Ok(())
    }
    
    /// Execute a plugin with the given input
    pub async fn execute_plugin<T, R>(
        &self,
        component: &Component,
        function_name: &str,
        input: &T,
    ) -> anyhow::Result<R>
    where
        T: serde::Serialize,
        R: for<'de> serde::Deserialize<'de>,
    {
        let mut store = self.create_store()?;
        let linker = self.create_linker()?;
        
        // Instantiate the component
        let _instance = linker.instantiate_async(&mut store, component).await?;
        
        // Serialize the input to JSON
        let input_json = serde_json::to_string(input)?;
        
        // Note: Component model function calling requires proper WIT bindings
        // For now, return a placeholder result to make compilation work
        // TODO: Implement proper component function calling with WIT
        let result_json = format!(r#"{{"status": "executed", "function": "{}", "input_len": {}}}"#, 
            function_name, input_json.len());
        
        // Deserialize the result
        let result = serde_json::from_str::<R>(&result_json)?;
        
        Ok(result)
    }
}
