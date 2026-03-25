package gg.gianluca.topperrest.cache;

public class CacheEntry<T> {
    private final T value;
    private final long expiresAt;

    public CacheEntry(T value, long ttlMillis) {
        this.value = value;
        this.expiresAt = System.currentTimeMillis() + ttlMillis;
    }

    public T getValue() { return value; }
    public boolean isExpired() { return System.currentTimeMillis() > expiresAt; }
}
