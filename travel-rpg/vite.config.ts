import { defineConfig } from 'vite';

export default defineConfig({
  // 相对路径构建：可静态托管在任意子目录 / 手机同 WiFi 直接访问
  base: './',
  server: { host: true, port: 5199 },
  preview: { host: true, port: 5199 },
});
