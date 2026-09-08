import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import App from "./App";
import { sendHit } from "./api";
import "./styles.css";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <App />
  </StrictMode>,
);

/*
 * アクセス数を 1 回数える。
 *
 * 1 タブにつき 1 回だけにするため sessionStorage で印を付ける。
 * これが無いとリロードのたびに増え、数字が実態から離れる。
 * この印はブラウザを閉じれば消え、こちらへは送られない。
 *
 * 開発中は数えない。自分のリロードで本番の数字が汚れる。
 */
if (import.meta.env.PROD) {
  try {
    if (!sessionStorage.getItem("owcs.counted")) {
      sessionStorage.setItem("owcs.counted", "1");
      sendHit();
    }
  } catch {
    // sessionStorage が使えない環境では、単に数えない
  }
}

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
