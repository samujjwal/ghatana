#!/usr/bin/env node
/* eslint-disable */
/*
 * Simple PNG converter to ensure the Tauri icon is in RGBA format.
 * Uses pngjs to read and rewrite the file with alpha channel.
 *
 * Usage: node scripts/convert-icon-rgba.js <inputPath> [outputPath]
 */
const fs = require('fs');
const path = require('path');
const { PNG } = require('pngjs');

const input =
  process.argv[2] ||
  path.join(__dirname, '../apps/desktop/src-tauri/icons/icon.png');
const output = process.argv[3] || input;

if (!fs.existsSync(input)) {
  console.error('Input file not found:', input);
  process.exit(2);
}

fs.createReadStream(input)
  .pipe(new PNG())
  .on('parsed', function () {
    // pngjs gives us width, height, and data (RGBA buffer)
    // If the input was RGB (no alpha), pngjs will still supply data length as width*height*4
    // We simply re-encode the PNG which ensures proper RGBA channels are present.
    this.pack()
      .pipe(fs.createWriteStream(output))
      .on('finish', () => {
        console.log('Wrote RGBA icon to', output);
      });
  })
  .on('error', (err) => {
    console.error('Failed to read/convert PNG:', err.message || err);
    process.exit(1);
  });
