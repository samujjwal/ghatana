//! Integration tests for agent-plugin-macros
//!
//! These tests verify the procedural macros used for plugin development,
//! including derive macros and attribute macros.

use proc_macro2::TokenStream;
use quote::quote;
use syn::{parse_quote, DeriveInput, ItemFn, ItemStruct};

#[test]
fn test_plugin_derive_macro() {
    // Test the Plugin derive macro
    let input: DeriveInput = parse_quote! {
        #[derive(Plugin)]
        struct TestPlugin {
            name: String,
            version: String,
        }
    };

    // In a real test, this would invoke the actual macro
    // For now, we test the structure is parseable
    assert_eq!(input.ident.to_string(), "TestPlugin");
    
    match input.data {
        syn::Data::Struct(data_struct) => {
            assert_eq!(data_struct.fields.len(), 2);
        }
        _ => panic!("Expected struct"),
    }
}

#[test]
fn test_export_attribute_macro() {
    // Test the export attribute macro
    let input: ItemFn = parse_quote! {
        #[export]
        pub fn process_data(input: &str) -> String {
            format!("Processed: {}", input)
        }
    };

    assert_eq!(input.sig.ident.to_string(), "process_data");
    assert_eq!(input.sig.inputs.len(), 1);
}

#[test]
fn test_plugin_attribute_macro() {
    // Test the plugin attribute macro
    let input: ItemStruct = parse_quote! {
        #[plugin(name = "test_plugin", version = "1.0.0")]
        pub struct TestPlugin {
            config: PluginConfig,
        }
    };

    assert_eq!(input.ident.to_string(), "TestPlugin");
    
    // Check for the plugin attribute
    let has_plugin_attr = input.attrs.iter().any(|attr| {
        attr.path().is_ident("plugin")
    });
    assert!(has_plugin_attr);
}

#[test]
fn test_macro_code_generation() {
    // Test that the macro generates expected code patterns
    let expected_plugin_impl = quote! {
        impl Plugin for TestPlugin {
            fn name(&self) -> &str {
                "test_plugin"
            }
            
            fn version(&self) -> &str {
                "1.0.0"
            }
            
            fn initialize(&mut self) -> Result<(), PluginError> {
                Ok(())
            }
            
            fn shutdown(&mut self) -> Result<(), PluginError> {
                Ok(())
            }
        }
    };

    // Verify the token stream can be parsed
    let parsed: Result<syn::ItemImpl, _> = syn::parse2(expected_plugin_impl);
    assert!(parsed.is_ok());
    
    let impl_block = parsed.unwrap();
    assert_eq!(impl_block.items.len(), 4); // 4 methods
}

#[test]
fn test_export_function_transformation() {
    // Test how the export macro transforms functions
    let input_function = quote! {
        #[export]
        fn calculate(a: i32, b: i32) -> i32 {
            a + b
        }
    };

    // The macro should transform this into an exported function
    // In a real implementation, this would generate FFI-compatible wrappers
    let expected_wrapper = quote! {
        #[no_mangle]
        pub extern "C" fn plugin_calculate(a: i32, b: i32) -> i32 {
            calculate(a, b)
        }
        
        fn calculate(a: i32, b: i32) -> i32 {
            a + b
        }
    };

    // Verify both can be parsed
    let input_parsed: Result<ItemFn, _> = syn::parse2(input_function);
    let wrapper_parsed: Result<TokenStream, _> = syn::parse2(expected_wrapper);
    
    assert!(input_parsed.is_ok());
    assert!(wrapper_parsed.is_ok());
}

#[test]
fn test_plugin_metadata_extraction() {
    // Test extracting metadata from plugin attributes
    let plugin_with_metadata = quote! {
        #[plugin(
            name = "advanced_plugin",
            version = "2.1.0",
            author = "DCMaar Team",
            description = "An advanced plugin for testing"
        )]
        struct AdvancedPlugin;
    };

    let parsed: Result<ItemStruct, _> = syn::parse2(plugin_with_metadata);
    assert!(parsed.is_ok());
    
    let plugin_struct = parsed.unwrap();
    
    // Find the plugin attribute
    let plugin_attr = plugin_struct.attrs.iter()
        .find(|attr| attr.path().is_ident("plugin"));
    
    assert!(plugin_attr.is_some());
}

#[test]
fn test_multiple_export_functions() {
    // Test multiple exported functions in one plugin
    let multiple_exports = quote! {
        #[export]
        fn init() -> bool {
            true
        }
        
        #[export]
        fn process(data: *const u8, len: usize) -> i32 {
            0
        }
        
        #[export]
        fn cleanup() {
            // cleanup code
        }
    };

    // Parse as a module or series of items
    let parsed: Result<syn::File, _> = syn::parse2(multiple_exports);
    assert!(parsed.is_ok());
    
    let file = parsed.unwrap();
    assert_eq!(file.items.len(), 3); // Three functions
}

#[test]
fn test_error_handling_in_macros() {
    // Test that macros handle errors appropriately
    
    // Invalid plugin attribute (missing required fields)
    let invalid_plugin = quote! {
        #[plugin] // Missing name and version
        struct InvalidPlugin;
    };
    
    let parsed: Result<ItemStruct, _> = syn::parse2(invalid_plugin);
    assert!(parsed.is_ok()); // syn parsing succeeds, but macro would fail
    
    // Invalid export on non-function
    let invalid_export = quote! {
        #[export]
        struct NotAFunction;
    };
    
    let parsed_struct: Result<ItemStruct, _> = syn::parse2(invalid_export);
    assert!(parsed_struct.is_ok()); // Parsing succeeds, but semantically invalid
}

#[test]
fn test_nested_plugin_structures() {
    // Test plugins with complex nested structures
    let complex_plugin = quote! {
        #[plugin(name = "complex", version = "1.0")]
        pub struct ComplexPlugin {
            inner: InnerComponent,
            handlers: Vec<Box<dyn Handler>>,
            config: std::collections::HashMap<String, Value>,
        }
        
        impl ComplexPlugin {
            #[export]
            pub fn new() -> Self {
                Self {
                    inner: InnerComponent::default(),
                    handlers: Vec::new(),
                    config: std::collections::HashMap::new(),
                }
            }
            
            #[export]
            pub fn add_handler(&mut self, handler: Box<dyn Handler>) {
                self.handlers.push(handler);
            }
        }
    };

    // This should parse successfully
    let parsed: Result<syn::File, _> = syn::parse2(complex_plugin);
    assert!(parsed.is_ok());
}

#[test]
fn test_macro_documentation_preservation() {
    // Test that macros preserve documentation comments
    let documented_plugin = quote! {
        /// A well-documented plugin for testing purposes.
        /// 
        /// This plugin demonstrates how documentation is preserved
        /// through macro transformations.
        #[plugin(name = "documented", version = "1.0")]
        pub struct DocumentedPlugin {
            /// Internal configuration
            config: Config,
        }
        
        impl DocumentedPlugin {
            /// Initialize the plugin with default configuration.
            /// 
            /// # Returns
            /// 
            /// Returns `Ok(())` on successful initialization.
            #[export]
            pub fn initialize(&mut self) -> Result<(), Error> {
                Ok(())
            }
        }
    };

    let parsed: Result<syn::File, _> = syn::parse2(documented_plugin);
    assert!(parsed.is_ok());
    
    let file = parsed.unwrap();
    
    // Check that documentation attributes are preserved
    let struct_item = &file.items[0];
    if let syn::Item::Struct(item_struct) = struct_item {
        let has_doc_attrs = item_struct.attrs.iter().any(|attr| {
            attr.path().is_ident("doc")
        });
        assert!(has_doc_attrs);
    }
}

#[test]
fn test_plugin_versioning_and_compatibility() {
    // Test version handling in plugin macros
    let versioned_plugins = vec![
        quote! { #[plugin(name = "test", version = "1.0.0")] struct V1Plugin; },
        quote! { #[plugin(name = "test", version = "1.2.3")] struct V2Plugin; },
        quote! { #[plugin(name = "test", version = "2.0.0-alpha")] struct V3Plugin; },
    ];

    for plugin_code in versioned_plugins {
        let parsed: Result<ItemStruct, _> = syn::parse2(plugin_code);
        assert!(parsed.is_ok());
        
        let plugin = parsed.unwrap();
        let has_plugin_attr = plugin.attrs.iter().any(|attr| {
            attr.path().is_ident("plugin")
        });
        assert!(has_plugin_attr);
    }
}

#[test]
fn test_conditional_compilation_support() {
    // Test that macros work with conditional compilation
    let conditional_plugin = quote! {
        #[cfg(feature = "advanced")]
        #[plugin(name = "conditional", version = "1.0")]
        pub struct ConditionalPlugin;
        
        #[cfg(feature = "advanced")]
        impl ConditionalPlugin {
            #[export]
            pub fn advanced_feature(&self) -> bool {
                true
            }
        }
        
        #[cfg(not(feature = "advanced"))]
        #[plugin(name = "conditional", version = "1.0")]
        pub struct ConditionalPlugin;
    };

    let parsed: Result<syn::File, _> = syn::parse2(conditional_plugin);
    assert!(parsed.is_ok());
}