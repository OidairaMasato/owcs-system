/*
 * Service Worker（手書き。ビルドプラグインは入れていない）
 *
 * 方針:
 * - 画面（HTML）とアセットはキャッシュから即出しして、裏で更新する。
 * - /api/dashboard は「ネットワーク優先・失敗したら前回のレスポンス」。
 *   これにより、圏外や PC のバックエンドが落ちていても
 *   最後に取れた試合日程・結果を見ることができる。
 * - キャッシュ名の VERSION を上げると古いキャッシュを捨てる。
 *
 * ハマりどころ:
 * Vite のビルド成果物は /assets/index-<hash>.js のようにファイル名が毎回変わるため、
 * 固定のリストではプリキャッシュできない。
 * また初回ロード時点では SW がまだ制御下に入っておらず、JS/CSS はネットワークから
 * 直接読まれるのでキャッシュに残らない。この状態でオフラインにすると
 * HTML だけ返って画面が真っ白になる。
 * そこで install 時に index.html を読んでアセットの URL を抽出し、明示的にキャッシュする。
 *
 * もう一つ:
 * Vite の preview / 多くの静的サーバーはレスポンスに `Vary: Origin` を付ける。
 * Vite のビルド成果物は <script type="module" crossorigin> で読まれるため
 * ページからのリクエストには Origin ヘッダーが付くが、
 * SW が cache.addAll したときのリクエストには付かない。
 * この差で Vary 判定に引っかかり、キャッシュに入っているのにヒットしない。
 * そのため cache.match には必ず { ignoreVary: true } を渡す。
 */

const VERSION = "v4";
const SHELL = `shell-${VERSION}`;
const DATA = `data-${VERSION}`;

const PRECACHE = [
  "/",
  "/manifest.webmanifest",
  "/icon-192.png",
  "/icon-512.png",
  "/icon-maskable-512.png",
  "/apple-touch-icon.png",
];

self.addEventListener("install", (e) => {
  e.waitUntil(precache().then(() => self.skipWaiting()));
});

async function precache() {
  const cache = await caches.open(SHELL);
  await cache.addAll(PRECACHE);

  // index.html からハッシュ付きアセットを拾ってキャッシュする
  try {
    const res = await fetch("/", { cache: "no-cache" });
    const html = await res.text();
    const urls = [...html.matchAll(/(?:src|href)="(\/assets\/[^"]+)"/g)].map((m) => m[1]);
    if (urls.length) await cache.addAll([...new Set(urls)]);
  } catch (err) {
    // アセットが拾えなくても SW 自体は生かす（オフライン表示だけ諦める）
    console.warn("[sw] asset precache skipped", err);
  }
}

self.addEventListener("activate", (e) => {
  e.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(keys.filter((k) => k !== SHELL && k !== DATA).map((k) => caches.delete(k))),
      )
      .then(() => self.clients.claim())
      .then(warmData),
  );
});

/** 起動直後にダッシュボードのデータもキャッシュしておく。 */
async function warmData() {
  try {
    const cache = await caches.open(DATA);
    const res = await fetch("/api/dashboard");
    if (res.ok) await cache.put("/api/dashboard", res.clone());
  } catch (err) {
    console.warn("[sw] data warm-up skipped", err);
  }
}

self.addEventListener("message", (e) => {
  if (e.data === "skip-waiting") self.skipWaiting();
});

self.addEventListener("fetch", (event) => {
  const req = event.request;
  if (req.method !== "GET") return;

  const url = new URL(req.url);
  if (url.origin !== self.location.origin) return;

  // ダッシュボードのデータ: ネットワーク優先、失敗したら前回のレスポンス
  if (url.pathname.startsWith("/api/")) {
    event.respondWith(networkFirst(req, DATA));
    return;
  }

  // 画面遷移: ネットワーク優先、失敗したらキャッシュした "/"
  if (req.mode === "navigate") {
    event.respondWith(
      fetch(req).catch(() => caches.match("/", { cacheName: SHELL, ignoreVary: true })),
    );
    return;
  }

  // それ以外（JS / CSS / 画像）: キャッシュ即出し + 裏で更新
  event.respondWith(staleWhileRevalidate(req, SHELL));
});

async function networkFirst(req, cacheName) {
  const cache = await caches.open(cacheName);
  try {
    const res = await fetch(req);
    if (res.ok) cache.put(req, res.clone());
    return res;
  } catch (e) {
    const hit = await cache.match(req, { ignoreVary: true });
    if (hit) return hit;
    throw e;
  }
}

async function staleWhileRevalidate(req, cacheName) {
  const cache = await caches.open(cacheName);
  const hit = await cache.match(req, { ignoreVary: true });
  if (hit) {
    // 裏で更新するが、結果は待たない
    fetch(req)
      .then((res) => {
        if (res.ok) cache.put(req, res.clone());
      })
      .catch(() => {});
    return hit;
  }
  const res = await fetch(req);
  if (res.ok) cache.put(req, res.clone());
  return res;
}
