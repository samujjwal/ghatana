#!/usr/bin/env node
// Minimal static server to serve the tests/e2e/fixtures folder on localhost:3000
import http from 'http';
import fs from 'fs';
import path from 'path';

const projectRoot = process.cwd();

const mime: Record<string, string> = { html: 'text/html', js: 'application/javascript', css: 'text/css' };
// Use projectRoot instead of __dirname so compiled ESM JS runs correctly under "type": "module"
const root = path.resolve(projectRoot, 'tests', 'e2e', 'fixtures');
const port = Number(process.env.PORT || 3000);
const host = process.env.HOST || '127.0.0.1';

// health endpoint to speed up readiness checks
function handleHealth(reqUrl: string, res: http.ServerResponse) {
  if (reqUrl === '/__ready') {
    res.statusCode = 200;
    res.setHeader('Content-Type', 'text/plain');
    res.end('ok');
    return true;
  }
  return false;
}

const server = http.createServer((req, res) => {
  const url = req.url || '/';
  if (handleHealth(url, res)) return;

  let p = url === '/' ? '/index.html' : url;
  const fp = path.join(root, decodeURIComponent(p));
  fs.readFile(fp, (err, data) => {
    if (err) {
      res.statusCode = 404;
      res.end('Not found');
      return;
    }
    const ext = path.extname(fp).slice(1);
    res.setHeader('Content-Type', mime[ext] || 'text/plain');
    res.end(data);
  });
});

server.listen(port, host, () => console.log(`E2E fixture server listening http://${host}:${port}`));

function shutdown() {
  console.log('Shutting down fixture server');
  server.close(() => process.exit(0));
}
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
