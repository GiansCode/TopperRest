package gg.gianluca.topperrest.model;

import java.util.UUID;

public class TopEntry {
    private final int position;
    private final String uuid;
    private final String name;
    private final double value;
    private final boolean bedrock;

    public TopEntry(int position, UUID uuid, String name, double value, boolean bedrock) {
        this.position = position;
        this.uuid = uuid.toString();
        this.name = name;
        this.value = value;
        this.bedrock = bedrock;
    }

    public int getPosition() { return position; }
    public String getUuid() { return uuid; }
    public String getName() { return name; }
    public double getValue() { return value; }
    public boolean isBedrock() { return bedrock; }
}
