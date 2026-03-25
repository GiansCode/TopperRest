package gg.gianluca.topperrest.model;

import java.util.List;
import java.util.UUID;

public class PlayerInfo {
    private final String uuid;
    private final String name;
    private final boolean bedrock;
    private final List<PlayerBoardInfo> boards;

    public PlayerInfo(UUID uuid, String name, boolean bedrock, List<PlayerBoardInfo> boards) {
        this.uuid = uuid.toString();
        this.name = name;
        this.bedrock = bedrock;
        this.boards = boards;
    }

    public String getUuid() { return uuid; }
    public String getName() { return name; }
    public boolean isBedrock() { return bedrock; }
    public List<PlayerBoardInfo> getBoards() { return boards; }
}
