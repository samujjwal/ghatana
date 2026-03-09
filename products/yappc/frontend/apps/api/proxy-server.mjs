/**
 * Simple Proxy Server
 * Forwards port 7002 requests to mock server on 7003
 */

import http from 'http';

const PROXY_PORT = 7002;
const TARGET_PORT = 7003;
const TARGET_HOST = 'localhost';

const proxy = http.createServer((req, res) => {
  const options = {
    hostname: TARGET_HOST,
    port: TARGET_PORT,
    path: req.url,
    method: req.method,
    headers: req.headers,
  };

  const proxyReq = http.request(options, (proxyRes) => {
    res.writeHead(proxyRes.statusCode, proxyRes.headers);
    proxyRes.pipe(res);
  });

  proxyReq.on('error', (error) => {
    console.error(`Proxy error: ${error}`);
    res.writeHead(503, { 'Content-Type': 'application/json' });
    res.end(
      JSON.stringify({ error: 'Service Unavailable', details: error.message })
    );
  });

  req.pipe(proxyReq);
});

proxy.listen(PROXY_PORT, () => {
  console.log(`Proxy server listening on port ${PROXY_PORT}`);
  console.log(`Forwarding to ${TARGET_HOST}:${TARGET_PORT}`);
});

proxy.on('error', (error) => {
  if (error.code === 'EADDRINUSE') {
    console.error(`Port ${PROXY_PORT} is already in use`);
    process.exit(1);
  }
  throw error;
});
