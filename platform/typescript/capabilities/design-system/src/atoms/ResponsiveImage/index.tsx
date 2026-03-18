import React, { useEffect, useState } from "react";
import { useImageOptimization } from "../../hooks/useImageOptimization";

export interface ResponsiveImageProps extends React.ImgHTMLAttributes<HTMLImageElement> {
  /** Base source for small screens/default */
  src: string;
  /** Source for medium screens */
  srcMd?: string;
  /** Source for large screens */
  srcLg?: string;
  /** Placeholder source */
  placeholder?: string;
  /** Whether to lazy load */
  lazy?: boolean;
  /** Alternative text */
  alt: string;
}

/**
 * Image component that optimizes loading based on viewport and lazy loading.
 */
export function ResponsiveImage({
  src,
  srcMd,
  srcLg,
  placeholder,
  lazy = true,
  className = "",
  alt,
  ...props
}: ResponsiveImageProps) {
  const [currentSrc, setCurrentSrc] = useState(placeholder || src);
  const { ref, isLoaded } = useImageOptimization({
    src: getSrcForViewport(),
    alt: alt || "",
    placeholder,
    lazy,
  });

  function getSrcForViewport() {
    if (typeof window === "undefined") return src;

    const width = window.innerWidth;
    if (width >= 1024 && srcLg) return srcLg;
    if (width >= 768 && srcMd) return srcMd;
    return src;
  }

  useEffect(() => {
    setCurrentSrc(getSrcForViewport());
  }, [src, srcMd, srcLg]);

  return (
    <img
      ref={ref}
      src={currentSrc}
      alt={alt}
      className={`transition-opacity ${isLoaded ? "opacity-100" : "opacity-50"} ${className}`}
      loading={lazy ? "lazy" : "eager"}
      {...props}
    />
  );
}
