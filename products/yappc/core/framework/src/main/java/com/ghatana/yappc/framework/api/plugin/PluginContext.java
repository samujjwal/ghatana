package com.ghatana.yappc.framework.api.plugin;

import com.ghatana.yappc.framework.api.domain.ProjectDescriptor;
import java.util.Map;

/** Runtime context passed to framework plugins.
 * @doc.type interface
 * @doc.purpose Defines the contract for plugin context
 * @doc.layer core
 * @doc.pattern ValueObject
*/
public interface PluginContext {
  ProjectDescriptor getCurrentProject();

  Map<String, Object> getPluginConfiguration();

  <T> T getConfigurationValue(String key, Class<T> type, T defaultValue);

  String getFrameworkVersion();

  <T> T getService(Class<T> serviceType);

  boolean hasService(Class<?> serviceType);

  void log(String level, String message, Object... args);
}
