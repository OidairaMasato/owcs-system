package jp.oidaira.owlog.domain;

public enum RoleType {
    SUPPORT("サポート"), DAMAGE("ダメージ"), TANK("タンク");

    private final String label;

    RoleType(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
