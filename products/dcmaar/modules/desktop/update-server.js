// update-server.js
import express from 'express';
import { fileURLToPath } from 'url';
import { dirname } from 'path';
import { createRequire } from 'module';

const _require = createRequire(import.meta.url);
const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const app = express();
const port = 3001;

app.use(express.static('updates'));

app.use((req, res, next) => {
  res.header('Access-Control-Allow-Origin', '*');
  res.header('Access-Control-Allow-Headers', 'Origin, X-Requested-With, Content-Type, Accept');
  next();
});

app.get('/update/:target/:version', (req, res) => {
  const { target, version } = req.params;
  console.log(`Checking for updates for ${target} (current version: ${version})`);
  
  res.json({
    name: `v1.0.1`,
    notes: 'Test update',
    pub_date: new Date().toISOString(),
    url: `http://localhost:3001/dcmaer-desktop_${target}_v1.0.1.zip`
  });
});

app.listen(port, () => {
  console.log(`Update server running at http://localhost:${port}`);
});