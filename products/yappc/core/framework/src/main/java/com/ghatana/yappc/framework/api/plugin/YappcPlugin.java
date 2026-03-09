package com.ghatana.yappc.framework.api.plugin;

/** Legacy plugin contract retained for compatibility. 
 * @doc.type interface
 * @doc.purpose Defines the contract for yappc plugin
 * @doc.layer core
 * @doc.pattern Plugin
*/
public interface YappcPlugin {
  String getName();

  String getVersion();

  String getDescription();
}
