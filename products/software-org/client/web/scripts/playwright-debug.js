import { chromium } from 'playwright';
import fs from 'fs';

(async () => {
    const BASE = process.env.BASE_URL || 'http://127.0.0.1:5173';
    const outPng = '/tmp/playwright-debug.png';
    const outHtml = '/tmp/playwright-debug.html';
    const outConsole = '/tmp/playwright-debug-console.json';

    const browser = await chromium.launch({ headless: false });
    const context = await browser.newContext({ serviceWorkers: 'allow' });
    const page = await context.newPage();

    const logs = [];
    page.on('console', (msg) => logs.push({ type: msg.type(), text: msg.text() }));
    page.on('pageerror', (err) => logs.push({ type: 'pageerror', text: err.message }));

    try {
        console.log('navigating to', BASE + '/');
        await page.goto(BASE + '/', { waitUntil: 'domcontentloaded', timeout: 60000 });
        // give app time to mount
        await page.waitForTimeout(3000);
    } catch (e) {
        console.error('navigation error', e && e.message);
    }

    try {
        await page.screenshot({ path: outPng, fullPage: true });
        const html = await page.content();
        fs.writeFileSync(outHtml, html);
        fs.writeFileSync(outConsole, JSON.stringify(logs, null, 2));
        console.log('wrote', outPng, outHtml, outConsole);
    } catch (e) {
        console.error('capture failed', e && e.message);
    }

    await browser.close();
})();
