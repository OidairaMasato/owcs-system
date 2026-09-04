package jp.oidaira.owcs.pandascore;

import java.util.List;
import jp.oidaira.owcs.OwcsProperties;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * PandaScore API クライアント。
 *
 * ハマりどころ（2026-09-04 に実機で確認済み）:
 * - videogame 配下のルート prefix は "/ow"。"/overwatch" は Route not found を返す。
 * - 単体取得は videogame 配下ではなく共通ルート "/matches/{id}"。
 * - レート制限は 1000 req/h。本アプリの定期取得は 1 時間あたり 10 req 未満。
 */
@Component
public class PandaScoreClient {

    private static final String OW = "/ow";

    private final RestClient rest;
    private final boolean configured;

    public PandaScoreClient(OwcsProperties props) {
        this.configured = props.pandascore().configured();
        this.rest = RestClient.builder()
                .baseUrl(props.pandascore().baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + props.pandascore().token())
                .defaultHeader(HttpHeaders.ACCEPT, "application/json")
                .build();
    }

    public boolean isConfigured() {
        return configured;
    }

    /** 予定の試合。開始が早い順。 */
    public List<Ps.Match> upcoming(int teamId, int perPage) {
        return list(OW + "/matches/upcoming", teamId, perPage, "begin_at");
    }

    /** 終了した試合。新しい順。 */
    public List<Ps.Match> past(int teamId, int perPage) {
        return list(OW + "/matches/past", teamId, perPage, "-begin_at");
    }

    /** 進行中の試合。 */
    public List<Ps.Match> running(int teamId, int perPage) {
        return list(OW + "/matches/running", teamId, perPage, "begin_at");
    }

    private List<Ps.Match> list(String path, int teamId, int perPage, String sort) {
        return rest.get()
                .uri(uri -> uri.path(path)
                        .queryParam("filter[opponent_id]", teamId)
                        .queryParam("per_page", perPage)
                        .queryParam("sort", sort)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<Ps.Match>>() {
                });
    }

    /** シリーズ配下のトーナメント一覧（Group Stage / Playoffs など）。 */
    public List<Ps.Tournament> tournaments(int serieId) {
        return rest.get()
                .uri(uri -> uri.path(OW + "/tournaments")
                        .queryParam("filter[serie_id]", serieId)
                        .queryParam("per_page", 50)
                        .build())
                .retrieve()
                .body(new ParameterizedTypeReference<List<Ps.Tournament>>() {
                });
    }

    /** 順位表。これも videogame 配下ではなく共通ルート。 */
    public List<Ps.Standing> standings(int tournamentId) {
        return rest.get()
                .uri("/tournaments/{id}/standings", tournamentId)
                .retrieve()
                .body(new ParameterizedTypeReference<List<Ps.Standing>>() {
                });
    }

    /** 1 試合の詳細。games を確実に取りたいときに使う。 */
    public Ps.Match match(int matchId) {
        return rest.get()
                .uri("/matches/{id}", matchId)
                .retrieve()
                .body(Ps.Match.class);
    }
}
