import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import "./styles.css";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);

/*
 * Service Worker の登録。
 *
 * 本番ビルドのときだけ登録する。開発中に登録すると、
 * 古いキャッシュが返って「直したのに変わらない」で消耗するため。
 *
 * なお Service Worker は HTTPS か localhost でしか動かない。
 * スマホから http://192.168.x.x:5173 で開いた場合は登録されず、
 * 通常の Web ページとして動く（iOS のホーム画面追加は可能）。
 */
if (import.meta.env.PROD && "serviceWorker" in navigator) {
  window.addEventListener("load", () => {
    navigator.serviceWorker.register("/sw.js").catch((e) => {
      console.warn("Service Worker の登録に失敗しました", e);
    });
  });
}
