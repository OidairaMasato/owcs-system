/**
 * 時刻表示。OWCS Korea は KST 開催で KST = JST なので、
 * 端末のローカル時刻（日本にいる限り JST）にそのまま落とせばよい。
 */

const WEEK = ["日", "月", "火", "水", "木", "金", "土"];

export function toDate(iso: string | null): Date | null {
  if (!iso) return null;
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? null : d;
}

/** 例: 10/4(土) 17:00 */
export function formatDateTime(iso: string | null): string {
  const d = toDate(iso);
  if (!d) return "日程未定";
  const md = `${d.getMonth() + 1}/${d.getDate()}`;
  const w = WEEK[d.getDay()];
  const hh = String(d.getHours()).padStart(2, "0");
  const mm = String(d.getMinutes()).padStart(2, "0");
  return `${md}(${w}) ${hh}:${mm}`;
}

/**
 * カウントダウンを「数値 + 単位」に分けて返す。
 * Broadcast 風の表示で数値だけ大きくするために使う。
 * 例: [{v:"2",u:"日"},{v:"05",u:"時間"}]
 */
export function countdownParts(iso: string | null, now: number): { v: string; u: string }[] {
  const d = toDate(iso);
  if (!d) return [];
  const sec = Math.floor((d.getTime() - now) / 1000);
  if (sec <= 0) return [];

  const day = Math.floor(sec / 86400);
  const hour = Math.floor((sec % 86400) / 3600);
  const min = Math.floor((sec % 3600) / 60);
  const s = sec % 60;

  const pad = (n: number) => String(n).padStart(2, "0");

  if (day > 0) return [{ v: String(day), u: "日" }, { v: pad(hour), u: "時間" }];
  if (hour > 0) return [{ v: String(hour), u: "時間" }, { v: pad(min), u: "分" }];
  return [{ v: String(min), u: "分" }, { v: pad(s), u: "秒" }];
}

/** 例: 3日 5時間 / 2時間 14分 / まもなく */
export function countdown(iso: string | null, now: number): string {
  const d = toDate(iso);
  if (!d) return "";
  const sec = Math.floor((d.getTime() - now) / 1000);
  if (sec <= 0) return "まもなく";

  const day = Math.floor(sec / 86400);
  const hour = Math.floor((sec % 86400) / 3600);
  const min = Math.floor((sec % 3600) / 60);

  if (day > 0) return `${day}日 ${hour}時間`;
  if (hour > 0) return `${hour}時間 ${min}分`;
  return `${min}分`;
}

/** 例: 14:58 */
export function formatLength(sec: number | null): string {
  if (sec == null) return "";
  const m = Math.floor(sec / 60);
  const s = sec % 60;
  return `${m}:${String(s).padStart(2, "0")}`;
}

/** 例: 5分前 / 2時間前 */
export function relativePast(iso: string | null, now: number): string {
  const d = toDate(iso);
  if (!d) return "未取得";
  const sec = Math.floor((now - d.getTime()) / 1000);
  if (sec < 60) return "たった今";
  if (sec < 3600) return `${Math.floor(sec / 60)}分前`;
  if (sec < 86400) return `${Math.floor(sec / 3600)}時間前`;
  return `${Math.floor(sec / 86400)}日前`;
}

/** twitch.tv/xxx → Twitch のような短いラベル */
export function streamLabel(url: string): string {
  try {
    const host = new URL(url).hostname.replace(/^www\./, "");
    if (host.includes("twitch")) return "Twitch で見る";
    if (host.includes("youtube") || host.includes("youtu.be")) return "YouTube で見る";
    if (host.includes("afreeca") || host.includes("sooplive")) return "SOOP で見る";
    return `${host} で見る`;
  } catch {
    return "配信を見る";
  }
}
