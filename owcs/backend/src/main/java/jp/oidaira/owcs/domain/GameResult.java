package jp.oidaira.owcs.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * マップ 1 本ぶんの結果。
 * PandaScore の Overwatch データにはマップ名もヒーロー構成も入っていないため、
 * 勝者と所要時間しか持てない（detailed_stats: false）。
 */
@Embeddable
public class GameResult {

    @Column(name = "game_no", nullable = false)
    private Short gameNo;

    @Column(nullable = false)
    private String status;

    @Column(name = "winner_id")
    private Integer winnerId;

    @Column(name = "length_sec")
    private Integer lengthSec;

    protected GameResult() {
    }

    public GameResult(int gameNo, String status, Integer winnerId, Integer lengthSec) {
        this.gameNo = (short) gameNo;
        this.status = status;
        this.winnerId = winnerId;
        this.lengthSec = lengthSec;
    }

    public short getGameNo() { return gameNo; }
    public String getStatus() { return status; }
    public Integer getWinnerId() { return winnerId; }
    public Integer getLengthSec() { return lengthSec; }
}
