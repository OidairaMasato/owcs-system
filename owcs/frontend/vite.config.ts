import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// バックエンドは 8081（旧 owlog の 8080 と衝突させない）
const API = "http://localhost:8081";

export default defineConfig({
  plugins: [react()],
  // 開発サーバー。Service Worker は本番ビルドでしか登録しないので、ここでは動かない。
  server: {
    proxy: { "/api": API },
  },
  // 本番ビルドの確認用。PWA（Service Worker）の動作はこちらで見る。
  preview: {
    proxy: { "/api": API },
  },
});
