// render_pdf.js — report.html → PDF（Playwright/Chromium 打印管线）
// 用法: NODE_PATH=$(npm root -g) node render_pdf.js report.html out.pdf
const path = require('path');
const { chromium } = require('playwright');

(async () => {
  const input = path.resolve(process.argv[2] || 'report.html');
  const output = path.resolve(process.argv[3] || 'report.pdf');
  const browser = await chromium.launch({ channel: 'chrome' });
  const page = await browser.newPage();
  await page.goto('file://' + input, { waitUntil: 'networkidle' });
  await page.pdf({
    path: output,
    width: '210mm',
    height: '297mm',
    printBackground: true,
    preferCSSPageSize: true,
    margin: { top: '0', right: '0', bottom: '0', left: '0' },
  });
  await browser.close();
  console.log('rendered', output);
})();
