package jp.oidaira.owcs.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/** 配信リンク。ダッシュボードの「どこで見るか」を担う。 */
@Embeddable
public class StreamLink {

    @Column(nullable = false)
    private Short seq;

    @Column(name = "raw_url", nullable = false)
    private String rawUrl;

    private String lang;

    @Column(name = "is_main", nullable = false)
    private boolean main;

    @Column(name = "is_official", nullable = false)
    private boolean official;

    protected StreamLink() {
    }

    public StreamLink(int seq, String rawUrl, String lang, boolean main, boolean official) {
        this.seq = (short) seq;
        this.rawUrl = rawUrl;
        this.lang = lang;
        this.main = main;
        this.official = official;
    }

    public short getSeq() { return seq; }
    public String getRawUrl() { return rawUrl; }
    public String getLang() { return lang; }
    public boolean isMain() { return main; }
    public boolean isOfficial() { return official; }

    /**
     * 表示優先度。小さいほど上。
     * 日本語配信 > 公式 > メイン > その他、の順で拾いたい。
     */
    public int priority() {
        if ("ja".equalsIgnoreCase(lang)) return 0;
        if (official) return 1;
        if (main) return 2;
        return 3;
    }
}
