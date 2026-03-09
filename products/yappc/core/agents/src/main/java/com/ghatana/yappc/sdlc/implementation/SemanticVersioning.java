package com.ghatana.yappc.sdlc.implementation;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Semantic Versioning implementation following SemVer 2.0.0 specification.
 * 
 * <p>Format: MAJOR.MINOR.PATCH[-PRERELEASE][+BUILD]
 * <ul>
 *   <li>MAJOR: Breaking changes</li>
 *   <li>MINOR: New features (backward compatible)</li>
 *   <li>PATCH: Bug fixes (backward compatible)</li>
 *   <li>PRERELEASE: Pre-release identifier (e.g., alpha, beta, rc)</li>
 *   <li>BUILD: Build metadata</li>
 * </ul>
 *
 * @doc.type class
 * @doc.purpose Semantic version management for artifacts
 * @doc.layer product
 * @doc.pattern Value Object
 */
public final class SemanticVersioning implements Comparable<SemanticVersioning> {
  
  private static final Pattern VERSION_PATTERN = Pattern.compile(
      "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)" +
      "(?:-((?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*)(?:\\.(?:0|[1-9]\\d*|\\d*[a-zA-Z-][0-9a-zA-Z-]*))*))?" +
      "(?:\\+([0-9a-zA-Z-]+(?:\\.[0-9a-zA-Z-]+)*))?$"
  );
  
  private final int major;
  private final int minor;
  private final int patch;
  private final String prerelease;
  private final String build;
  
  private SemanticVersioning(int major, int minor, int patch, String prerelease, String build) {
    this.major = major;
    this.minor = minor;
    this.patch = patch;
    this.prerelease = prerelease;
    this.build = build;
  }
  
  /**
   * Creates a new semantic version.
   * 
   * @param major Major version (breaking changes)
   * @param minor Minor version (new features)
   * @param patch Patch version (bug fixes)
   * @return New SemanticVersioning instance
   */
  public static SemanticVersioning of(int major, int minor, int patch) {
    return new SemanticVersioning(major, minor, patch, null, null);
  }
  
  /**
   * Creates a new semantic version with prerelease.
   * 
   * @param major Major version
   * @param minor Minor version
   * @param patch Patch version
   * @param prerelease Prerelease identifier (e.g., "alpha", "beta", "rc.1")
   * @return New SemanticVersioning instance
   */
  public static SemanticVersioning of(int major, int minor, int patch, String prerelease) {
    return new SemanticVersioning(major, minor, patch, prerelease, null);
  }
  
  /**
   * Parses a version string into a SemanticVersioning object.
   * 
   * @param version Version string to parse
   * @return Parsed SemanticVersioning
   * @throws IllegalArgumentException if version string is invalid
   */
  public static SemanticVersioning parse(String version) {
    Objects.requireNonNull(version, "version must not be null");
    
    Matcher matcher = VERSION_PATTERN.matcher(version);
    if (!matcher.matches()) {
      throw new IllegalArgumentException("Invalid semantic version: " + version);
    }
    
    int major = Integer.parseInt(matcher.group(1));
    int minor = Integer.parseInt(matcher.group(2));
    int patch = Integer.parseInt(matcher.group(3));
    String prerelease = matcher.group(4);
    String build = matcher.group(5);
    
    return new SemanticVersioning(major, minor, patch, prerelease, build);
  }
  
  /**
   * Increments major version (resets minor and patch to 0).
   * 
   * @return New version with incremented major
   */
  public SemanticVersioning incrementMajor() {
    return new SemanticVersioning(major + 1, 0, 0, null, null);
  }
  
  /**
   * Increments minor version (resets patch to 0).
   * 
   * @return New version with incremented minor
   */
  public SemanticVersioning incrementMinor() {
    return new SemanticVersioning(major, minor + 1, 0, null, null);
  }
  
  /**
   * Increments patch version.
   * 
   * @return New version with incremented patch
   */
  public SemanticVersioning incrementPatch() {
    return new SemanticVersioning(major, minor, patch + 1, null, null);
  }
  
  /**
   * Returns version with prerelease identifier.
   * 
   * @param prerelease Prerelease identifier
   * @return New version with prerelease
   */
  public SemanticVersioning withPrerelease(String prerelease) {
    return new SemanticVersioning(major, minor, patch, prerelease, null);
  }
  
  /**
   * Returns version without prerelease (stable release).
   * 
   * @return New version without prerelease
   */
  public SemanticVersioning toStable() {
    return new SemanticVersioning(major, minor, patch, null, build);
  }
  
  /**
   * Checks if this is a prerelease version.
   * 
   * @return true if prerelease
   */
  public boolean isPrerelease() {
    return prerelease != null && !prerelease.isEmpty();
  }
  
  /**
   * Checks if this is a stable release (not prerelease).
   * 
   * @return true if stable
   */
  public boolean isStable() {
    return !isPrerelease();
  }
  
  public int getMajor() {
    return major;
  }
  
  public int getMinor() {
    return minor;
  }
  
  public int getPatch() {
    return patch;
  }
  
  public String getPrerelease() {
    return prerelease;
  }
  
  public String getBuild() {
    return build;
  }
  
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(major).append('.').append(minor).append('.').append(patch);
    if (prerelease != null) {
      sb.append('-').append(prerelease);
    }
    if (build != null) {
      sb.append('+').append(build);
    }
    return sb.toString();
  }
  
  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    SemanticVersioning that = (SemanticVersioning) o;
    return major == that.major &&
           minor == that.minor &&
           patch == that.patch &&
           Objects.equals(prerelease, that.prerelease);
    // Note: build metadata is ignored in equality comparison per SemVer spec
  }
  
  @Override
  public int hashCode() {
    return Objects.hash(major, minor, patch, prerelease);
  }
  
  @Override
  public int compareTo(SemanticVersioning other) {
    // Compare major, minor, patch
    int result = Integer.compare(this.major, other.major);
    if (result != 0) return result;
    
    result = Integer.compare(this.minor, other.minor);
    if (result != 0) return result;
    
    result = Integer.compare(this.patch, other.patch);
    if (result != 0) return result;
    
    // Compare prerelease
    if (this.prerelease == null && other.prerelease == null) return 0;
    if (this.prerelease == null) return 1; // No prerelease > prerelease
    if (other.prerelease == null) return -1;
    
    return comparePrerelease(this.prerelease, other.prerelease);
  }
  
  private int comparePrerelease(String a, String b) {
    String[] aParts = a.split("\\.");
    String[] bParts = b.split("\\.");
    
    int minLength = Math.min(aParts.length, bParts.length);
    for (int i = 0; i < minLength; i++) {
      int comparison = comparePrereleasePart(aParts[i], bParts[i]);
      if (comparison != 0) return comparison;
    }
    
    return Integer.compare(aParts.length, bParts.length);
  }
  
  private int comparePrereleasePart(String a, String b) {
    boolean aIsNumeric = a.matches("\\d+");
    boolean bIsNumeric = b.matches("\\d+");
    
    if (aIsNumeric && bIsNumeric) {
      return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
    }
    
    if (aIsNumeric) return -1; // Numeric identifiers have lower precedence
    if (bIsNumeric) return 1;
    
    return a.compareTo(b);
  }
  
  /**
   * Determines the next version based on change type.
   * 
   * @param changeType Type of change (MAJOR, MINOR, PATCH)
   * @return Next appropriate version
   */
  public SemanticVersioning nextVersion(ChangeType changeType) {
    return switch (changeType) {
      case MAJOR -> incrementMajor();
      case MINOR -> incrementMinor();
      case PATCH -> incrementPatch();
    };
  }
  
  /**
   * Types of changes for version incrementing.
   */
  public enum ChangeType {
    /** Breaking changes */
    MAJOR,
    /** New features (backward compatible) */
    MINOR,
    /** Bug fixes (backward compatible) */
    PATCH
  }
}
