import type { TeamView } from "../types";

type Props = { team: TeamView | undefined };

/**
 * カレンダー購読の入口。
 *
 * webcal:// で開くとカレンダーアプリが「購読」として登録し、
 * 以後は日程の追加・変更をアプリ側が自動で取り直す。
 * 通知も OS のカレンダーに任せられるので、Push を自前で実装しなくても
 * 「試合を見逃さない」という目的を満たせる。
 */
export default function CalendarLink({ team }: Props) {
  if (!team) return null;

  const path = `/calendar/team-${team.id}.ics`;
  const webcal = `webcal://${location.host}${path}`;

  return (
    <div className="cal-row">
      <a className="cal-btn" href={webcal}>
        カレンダーに登録
      </a>
      <a className="cal-alt" href={path} download>
        .ics をダウンロード
      </a>
    </div>
  );
}
