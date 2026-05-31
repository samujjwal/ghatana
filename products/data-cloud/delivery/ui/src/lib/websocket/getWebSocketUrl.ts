export interface WebSocketUrlOptions {
  baseUrl: string;
  endpoint: string;
  tenantId?: string;
  params?: Record<string, string | number | boolean | undefined>;
  secure?: boolean;
}

export function getWebSocketUrl(options: WebSocketUrlOptions): string {
  const { baseUrl, endpoint, tenantId, params, secure } = options;
  let urlStr = baseUrl;

  if (
    !urlStr.startsWith("ws://") &&
    !urlStr.startsWith("wss://") &&
    !urlStr.startsWith("http://") &&
    !urlStr.startsWith("https://")
  ) {
    const isSecure =
      secure ??
      (typeof window !== "undefined" && window.location.protocol === "https:");
    urlStr = `${isSecure ? "wss" : "ws"}://${urlStr}`;
  } else {
    urlStr = urlStr
      .replace(/^http:\/\//, "ws://")
      .replace(/^https:\/\//, "wss://");
    if (secure && urlStr.startsWith("ws://")) {
      urlStr = urlStr.replace(/^ws:\/\//, "wss://");
    }
  }

  urlStr = urlStr.replace(/\/+$/, "");
  const pathStr = endpoint.startsWith("/") ? endpoint : `/${endpoint}`;

  try {
    const url = new URL(`${urlStr}${pathStr}`);
    if (tenantId) url.searchParams.set("tenantId", tenantId);
    if (params) {
      Object.entries(params).forEach(([key, value]) => {
        if (value !== undefined) url.searchParams.set(key, String(value));
      });
    }
    return url.toString();
  } catch {
    const searchParams = new URLSearchParams();
    if (tenantId) searchParams.set("tenantId", tenantId);
    if (params) {
      Object.entries(params).forEach(([key, value]) => {
        if (value !== undefined) searchParams.set(key, String(value));
      });
    }
    const qs = searchParams.toString();
    return qs ? `${urlStr}${pathStr}?${qs}` : `${urlStr}${pathStr}`;
  }
}
