//! Procedural macros for the agent plugin system.
//!
//! This crate provides derive macros for implementing the plugin traits.

use proc_macro::TokenStream;
use quote::quote;
use syn::{parse_macro_input, DeriveInput};

/// Derive macro for the `Collector` trait
#[proc_macro_derive(Collector)]
pub fn derive_collector(input: TokenStream) -> TokenStream {
    let input = parse_macro_input!(input as DeriveInput);
    let name = &input.ident;
    
    let expanded = quote! {
        #[async_trait::async_trait]
        impl agent_plugin::sdk::Collector for #name {
            type Config = agent_plugin::sdk::CollectorConfig;
            type Output = serde_json::Value;
            
            fn new(config: Self::Config) -> agent_plugin::sdk::SdkResult<Self> {
                Ok(Self { config })
            }
            
            async fn collect(&self) -> agent_plugin::sdk::SdkResult<Self::Output> {
                self.collect_impl().await
            }
        }
    };
    
    TokenStream::from(expanded)
}

/// Derive macro for the `Enricher` trait
#[proc_macro_derive(Enricher)]
pub fn derive_enricher(input: TokenStream) -> TokenStream {
    let input = parse_macro_input!(input as DeriveInput);
    let name = &input.ident;
    
    let expanded = quote! {
        #[async_trait::async_trait]
        impl agent_plugin::sdk::Enricher for #name {
            type Config = agent_plugin::sdk::EnricherConfig;
            type Input = serde_json::Value;
            type Output = serde_json::Value;
            
            fn new(config: Self::Config) -> agent_plugin::sdk::SdkResult<Self> {
                Ok(Self { config })
            }
            
            async fn enrich(&self, input: Self::Input) -> agent_plugin::sdk::SdkResult<Self::Output> {
                self.enrich_impl(input).await
            }
        }
    };
    
    TokenStream::from(expanded)
}

/// Derive macro for the `Action` trait
#[proc_macro_derive(Action)]
pub fn derive_action(input: TokenStream) -> TokenStream {
    let input = parse_macro_input!(input as DeriveInput);
    let name = &input.ident;
    
    let expanded = quote! {
        #[async_trait::async_trait]
        impl agent_plugin::sdk::Action for #name {
            type Config = agent_plugin::sdk::ActionConfig;
            type Input = serde_json::Value;
            type Output = serde_json::Value;
            
            fn new(config: Self::Config) -> agent_plugin::sdk::SdkResult<Self> {
                Ok(Self { config })
            }
            
            async fn execute(&self, input: Self::Input) -> agent_plugin::sdk::SdkResult<Self::Output> {
                self.execute_impl(input).await
            }
        }
    };
    
    TokenStream::from(expanded)
}
