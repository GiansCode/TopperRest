# TopperRest

A REST API addon for the [Topper](https://github.com/Topper-MC/Topper-spigot) leaderboard plugin, targeting **Paper 1.21.1**.

Exposes all Topper leaderboard data via a lightweight embedded HTTP server with caching, rate limiting, bulk lookups, and Bedrock/Geyser player support.

---

## Requirements

| Dependency | Version | Notes |
|---|---|---|
| Paper | 1.21.1 | Server software |
| Topper (Spigot) | 4.6.0+ | Required |
| Floodgate | 2.x | Optional — enables Bedrock player support |

---

## Installation

1. Drop `TopperRest.jar` into your `plugins/` folder.
2. Ensure Topper is already installed.
3. Start the server — a default `config.yml` is generated under `plugins/TopperRest/`.
4. Optionally set `rest.api-key` in the config for authenticated access.

---

## Configuration (`plugins/TopperRest/config.yml`)

```yaml
rest:
  port: 4567
  bind-address: "0.0.0.0"
  api-key: ""           # Optional — set to require X-API-Key header
  max-connections: 50
  request-timeout: 10
  threads: 4

cache:
  ttl-seconds: 5        # How long board snapshots are cached
  player-name-ttl-seconds: 300

rate-limit:
  enabled: true
  max-requests: 100
  window-seconds: 60

geyser:
  bedrock-prefix: "~"  # Prefix for Bedrock username lookups
```

---

## API Reference

All endpoints are prefixed with `/api/v1`.

### Authentication

The API key is **user-defined** — you choose any secret string and write it into `config.yml`:

```yaml
rest:
  api-key: "my-super-secret-key-123"
```

When set, every request must include a matching `X-API-Key` header, otherwise a `401 Unauthorized` is returned. Leave the field empty (default) to disable authentication entirely.

```http
GET /api/v1/tops HTTP/1.1
Host: your-server.com:4567
X-API-Key: my-super-secret-key-123
```

### Health check

```
GET /api/v1/health
```

```json
{
  "status": "ok",
  "plugin": "TopperRest",
  "boards": 3
}
```

---

### Boards

#### List all boards

```
GET /api/v1/tops
```

```json
{
  "boards": ["kills", "money", "playtime"],
  "count": 3
}
```

---

#### Get a board (paginated)

```
GET /api/v1/tops/kills?page=1&size=5
```

| Query param | Default | Max | Description |
|---|---|---|---|
| `page` | `1` | — | Page number (1-indexed) |
| `size` | `10` | `200` | Entries per page |

```json
{
  "name": "kills",
  "total": 86,
  "page": 1,
  "pageSize": 5,
  "totalPages": 18,
  "entries": [
    { "position": 1, "uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5", "name": "Notch",      "value": 4812.0, "bedrock": false },
    { "position": 2, "uuid": "853c80ef-3c37-49fd-aa49-938b674adae6", "name": "jeb_",       "value": 3741.0, "bedrock": false },
    { "position": 3, "uuid": "b3c6b3b7-4e6e-4a57-a62f-d8e6b8c3e2f1", "name": "Dinnerbone", "value": 2905.0, "bedrock": false },
    { "position": 4, "uuid": "f7c77d99-9d0d-4a3e-87e5-5b1a7b8d9c0a", "name": "GeyserUser", "value": 2100.0, "bedrock": true  },
    { "position": 5, "uuid": "a1b2c3d4-e5f6-7890-abcd-ef1234567890", "name": "xX_PvP_Xx",  "value": 1988.0, "bedrock": false }
  ]
}
```

> Bedrock players (connected via Geyser + Floodgate) have `"bedrock": true`. Without Floodgate installed this field is always `false`.

---

#### Get entry at a specific position

Position is **1-indexed**.

```
GET /api/v1/tops/kills/1
```

```json
{
  "position": 1,
  "uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5",
  "name": "Notch",
  "value": 4812.0,
  "bedrock": false
}
```

```
GET /api/v1/tops/kills/999
```

```json
{ "error": "No entry at position 999 on board 'kills'" }
```

---

### Players

`{id}` accepts any of the following formats:

| Format | Example | Notes |
|---|---|---|
| UUID | `069a79f4-44e9-4726-a5be-fca90e38aaf5` | Always works |
| Java username | `Notch` | Resolved via Bukkit's offline-player lookup |
| Bedrock username | `~GeyserUser` | Requires Floodgate; player must be online |

---

#### Get a player across all boards

```
GET /api/v1/player/Notch
```

```json
{
  "uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5",
  "name": "Notch",
  "bedrock": false,
  "boards": [
    { "board": "kills",    "rank": 1,  "value": 4812.0,    "ranked": true  },
    { "board": "money",    "rank": 3,  "value": 150000.75, "ranked": true  },
    { "board": "playtime", "rank": -1, "value": null,      "ranked": false }
  ]
}
```

`rank: -1` means the player has no entry on that board. `ranked: false` is a convenience alias for the same thing.

---

#### Get a player on a specific board

```
GET /api/v1/player/069a79f4-44e9-4726-a5be-fca90e38aaf5/money
```

```json
{
  "uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5",
  "name": "Notch",
  "bedrock": false,
  "boards": [
    { "board": "money", "rank": 3, "value": 150000.75, "ranked": true }
  ]
}
```

---

#### Bedrock player lookup

```
GET /api/v1/player/~GeyserUser
```

```json
{
  "uuid": "f7c77d99-9d0d-4a3e-87e5-5b1a7b8d9c0a",
  "name": "GeyserUser",
  "bedrock": true,
  "boards": [
    { "board": "kills",    "rank": 4,  "value": 2100.0, "ranked": true  },
    { "board": "money",    "rank": 12, "value": 8400.0, "ranked": true  },
    { "board": "playtime", "rank": 2,  "value": 72000.0,"ranked": true  }
  ]
}
```

If the Bedrock player is not online or Floodgate is not installed:

```json
{ "error": "Player '~GeyserUser' not found" }
```

---

### Bulk

Maximum **50** items per bulk request.

#### Bulk board fetch

```http
POST /api/v1/bulk/tops
Content-Type: application/json

{
  "boards": ["kills", "money"],
  "page": 1,
  "size": 3
}
```

```json
{
  "kills": {
    "name": "kills",
    "total": 86,
    "page": 1,
    "pageSize": 3,
    "totalPages": 29,
    "entries": [
      { "position": 1, "uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5", "name": "Notch",  "value": 4812.0, "bedrock": false },
      { "position": 2, "uuid": "853c80ef-3c37-49fd-aa49-938b674adae6", "name": "jeb_",   "value": 3741.0, "bedrock": false },
      { "position": 3, "uuid": "b3c6b3b7-4e6e-4a57-a62f-d8e6b8c3e2f1", "name": "Dinnerbone", "value": 2905.0, "bedrock": false }
    ]
  },
  "money": {
    "name": "money",
    "total": 54,
    "page": 1,
    "pageSize": 3,
    "totalPages": 18,
    "entries": [
      { "position": 1, "uuid": "853c80ef-3c37-49fd-aa49-938b674adae6", "name": "jeb_",   "value": 9875432.5, "bedrock": false },
      { "position": 2, "uuid": "a1b2c3d4-e5f6-7890-abcd-ef1234567890", "name": "xX_PvP_Xx", "value": 500000.0, "bedrock": false },
      { "position": 3, "uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5", "name": "Notch",  "value": 150000.75, "bedrock": false }
    ]
  }
}
```

Boards not found are silently omitted from the response.

---

#### Bulk player fetch

```http
POST /api/v1/bulk/players
Content-Type: application/json

{
  "players": ["Notch", "853c80ef-3c37-49fd-aa49-938b674adae6", "~GeyserUser", "nobody"]
}
```

```json
{
  "players": {
    "069a79f4-44e9-4726-a5be-fca90e38aaf5": {
      "uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5",
      "name": "Notch",
      "bedrock": false,
      "boards": [
        { "board": "kills", "rank": 1, "value": 4812.0, "ranked": true },
        { "board": "money", "rank": 3, "value": 150000.75, "ranked": true }
      ]
    },
    "853c80ef-3c37-49fd-aa49-938b674adae6": {
      "uuid": "853c80ef-3c37-49fd-aa49-938b674adae6",
      "name": "jeb_",
      "bedrock": false,
      "boards": [
        { "board": "kills", "rank": 2, "value": 3741.0, "ranked": true },
        { "board": "money", "rank": 1, "value": 9875432.5, "ranked": true }
      ]
    }
  },
  "notFound": ["~GeyserUser", "nobody"]
}
```

The response is keyed by resolved UUID. Identifiers that couldn't be resolved appear in `notFound`.

---

## Commands

| Command | Permission | Description |
|---|---|---|
| `/topperrest reload` | `topperrest.admin` | Reloads config and restarts the REST server |

---

## Caching

- **Board snapshots** — cached for `cache.ttl-seconds` (default 5 s). Automatically invalidated when Topper fires a data-update event for that board.
- **Player names** — cached for `cache.player-name-ttl-seconds` (default 5 min).
- **Player ranks** — cached with the same TTL as board snapshots.

Expired entries are evicted every 60 seconds to keep memory usage bounded.

---

## Building

```bash
mvn clean package
```

The plugin JAR will be at `target/TopperRest-1.0.0.jar`.
