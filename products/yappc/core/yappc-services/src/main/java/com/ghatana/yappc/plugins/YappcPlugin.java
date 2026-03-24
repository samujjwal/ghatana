package com.ghatana.yappc.plugins;

/** Base lifecycle plugin contract. 
 * @doc.type interface
 * @doc.purpose Defines the contract for yappc plugin
 * @doc.layer core
 * @doc.pattern Plugin
* @doc.gaa.lifecycle perceive
*/
public interface YappcPlugin {
  String getPluginId();

  String getVersion();

  default boolean isEnabled() {
    return true;
  }
}
