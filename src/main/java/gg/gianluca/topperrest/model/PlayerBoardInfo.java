package gg.gianluca.topperrest.model;

public class PlayerBoardInfo {
    private final String board;
    private final int rank;
    private final Double value;

    public PlayerBoardInfo(String board, int rank, Double value) {
        this.board = board;
        this.rank = rank;
        this.value = value;
    }

    public String getBoard() { return board; }
    /** 1-indexed rank. -1 if not ranked on this board. */
    public int getRank() { return rank; }
    public Double getValue() { return value; }
    public boolean isRanked() { return rank > 0; }
}
