package gg.gianluca.topperrest.model;

import java.util.List;

public class TopBoard {
    private final String name;
    private final int total;
    private final int page;
    private final int pageSize;
    private final int totalPages;
    private final List<TopEntry> entries;

    public TopBoard(String name, int total, int page, int pageSize, List<TopEntry> entries) {
        this.name = name;
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
        this.totalPages = pageSize > 0 ? (int) Math.ceil((double) total / pageSize) : 0;
        this.entries = entries;
    }

    public String getName() { return name; }
    public int getTotal() { return total; }
    public int getPage() { return page; }
    public int getPageSize() { return pageSize; }
    public int getTotalPages() { return totalPages; }
    public List<TopEntry> getEntries() { return entries; }
}
