package jp.oidaira.owcs.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** 順位表の 1 行。 */
@Embeddable
public class StandingRow {

    @Column(name = "team_id", nullable = false)
    private Integer teamId;

    /** PandaScore の rank。同率があるので連番ではない（1,1,3,3,5...）。 */
    @Column(name = "rank_no")
    private Short rankNo;

    private Short wins;
    private Short losses;

    @Column(name = "game_wins")
    private Short gameWins;

    @Column(name = "game_losses")
    private Short gameLosses;

    protected StandingRow() {
    }

    public StandingRow(int teamId, Integer rankNo, Integer wins, Integer losses,
                       Integer gameWins, Integer gameLosses) {
        this.teamId = teamId;
        this.rankNo = toShort(rankNo);
        this.wins = toShort(wins);
        this.losses = toShort(losses);
        this.gameWins = toShort(gameWins);
        this.gameLosses = toShort(gameLosses);
    }

    private static Short toShort(Integer v) {
        return v == null ? null : v.shortValue();
    }

    public int getTeamId() { return teamId; }
    public Short getRankNo() { return rankNo; }
    public Short getWins() { return wins; }
    public Short getLosses() { return losses; }
    public Short getGameWins() { return gameWins; }
    public Short getGameLosses() { return gameLosses; }
}
