const fs = require('fs');
const path = require('path');
const { createCanvas } = require('canvas');

// Create icons directory if it doesn't exist
const iconsDir = path.join(__dirname, '../public/icons');
if (!fs.existsSync(iconsDir)) {
  fs.mkdirSync(iconsDir, { recursive: true });
}

// Icon sizes we need
const sizes = [16, 32, 48, 128];

// Simple function to create a colored square icon
function createIcon(size, color = '#2563EB') {
  const canvas = createCanvas(size, size);
  const ctx = canvas.getContext('2d');
  
  // Draw background
  ctx.fillStyle = color;
  const padding = Math.max(1, Math.floor(size * 0.1)); // 10% padding
  const cornerRadius = Math.max(2, Math.floor(size * 0.2)); // 20% corner radius
  
  // Draw rounded rectangle
  ctx.beginPath();
  ctx.roundRect(
    padding, 
    padding, 
    size - 2 * padding, 
    size - 2 * padding, 
    cornerRadius
  );
  ctx.fill();
  
  return canvas.toBuffer('image/png');
}

// Generate all icon sizes
sizes.forEach(size => {
  const icon = createIcon(size);
  fs.writeFileSync(path.join(iconsDir, `icon${size}.png`), icon);
  console.log(`Generated icon-${size}.png`);
});

console.log('Icons generated successfully!');
