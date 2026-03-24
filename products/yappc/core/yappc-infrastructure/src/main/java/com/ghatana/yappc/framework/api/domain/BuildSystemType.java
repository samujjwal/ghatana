package com.ghatana.yappc.framework.api.domain;

/** Supported build systems for generator plugins. 
 * @doc.type enum
 * @doc.purpose Enumerates build system type values
 * @doc.layer core
 * @doc.pattern Enum
*/
public enum BuildSystemType {
  MAVEN,
  GRADLE,
  NPM,
  PNPM,
  YARN,
  UNKNOWN
}
