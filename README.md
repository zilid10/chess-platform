# Chess Platform

[![CI](https://github.com/zilid10/chess-platform/actions/workflows/ci.yaml/badge.svg)](https://github.com/zilid10/chess-platform/actions/workflows/ci.yaml)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)
![Java 21](https://img.shields.io/badge/Java-21-orange)
![Spring Boot 4.1](https://img.shields.io/badge/Spring%20Boot-4.1-6db33f)
![React 19](https://img.shields.io/badge/React-19-61dafb)

A real-time multiplayer chess platform with a chess engine written from scratch, live clocks, Elo ratings, and a
backend that scales horizontally: any number of instances can serve the same game.

Players create a game on a clock such as `5+3`, share its ID, and play over WebSockets. Others who open the same game
watch it live and can chat. Finished games are rated, archived with their PGN, and listed in each player's history.

---

## Features

**Play**

- Real-time games over STOMP WebSockets, with spectators and in-game chat
- Eleven clock settings, from `1+0` bullet to `30+20` classical, with increments
- Games end on time even if a player disconnects: the server runs the clocks, not the browser
- Unstarted games are aborted: White has 30 seconds to make a first move, then Black has 30 seconds to reply
- Resignation, draw offers, and every standard ending: checkmate, stalemate, threefold repetition, the fifty-move rule,
  insufficient material, and a draw when time runs out but the opponent could never checkmate

**Compete**

- Separate Elo ratings for bullet, blitz, rapid, and classical, starting at 1200
- A K-factor that settles as players gain experience: 40 for a player's first 20 games, then 20 up to a 2100 rating,
  then 10
- Game history with rating changes, and PGN you can download or copy

**Connect**

- Accounts with profiles, and friend requests you can send, accept, or reject
- Session login shared across every backend instance

---

## Highlights

### A chess engine built from scratch

The rules engine in `backend/.../chess/` has no framework dependencies. It includes:

- legal move generation, with check detection, castling through unattacked squares, en passant, and promotion to any
  piece;
- move application with full undo;
- FEN and UCI parsing, and SAN and PGN output with file, rank, and square disambiguation.

A perft suite counts every legal move sequence from standard test positions and compares the totals with published
references, such as 197,281 positions at depth 4 from the starting position. That catches bugs in how castling,
en passant, promotion, and check interact.

### Clocks the server can enforce

Each player's clock deadline is indexed in a Redis sorted set. A sweeper checks it every 250 ms and ends overdue games,
so a player who disconnects still loses on time. Each game is updated under a per-game Redis lock, so when several
instances notice the same timeout, only one ends the game, publishes the result, and archives the match. Clients can
also claim a flag the moment a clock hits zero, but the server decides using its own clock.

### Horizontal scaling

Nothing about a game lives in one instance's memory:

- **Active games** are stored in Redis and changed under per-game locks, so concurrent moves are serialized across
  instances.
- **Login sessions** are stored in Redis with Spring Session.
- **WebSocket subscriptions** live in RabbitMQ through Spring's STOMP broker relay. A move handled by one instance
  reaches players connected to another, including private `/user/...` messages.

Docker Compose runs two backend instances behind nginx with the `app` profile.

```mermaid
flowchart LR
    Browser["Browser<br/>React + STOMP"] --> LB["nginx<br/>load balancer"]
    LB --> B1["Backend<br/>instance 1"]
    LB --> B2["Backend<br/>instance 2"]
    B1 & B2 --> PG[("PostgreSQL<br/>accounts, ratings,<br/>match records")]
    B1 & B2 --> R[("Redis<br/>active games, clocks,<br/>locks, sessions")]
    B1 & B2 <--> MQ["RabbitMQ<br/>STOMP broker"]
```

---

## Tech Stack

| Area           | Technologies                                                                                           |
|----------------|--------------------------------------------------------------------------------------------------------|
| Backend        | Java 21 (virtual threads), Spring Boot 4.1, Spring Security, Spring WebSocket (STOMP), Spring Data JPA |
| Data           | PostgreSQL 17.5 with Flyway migrations, Redis 8.6 with Spring Session                                  |
| Messaging      | RabbitMQ 4.1 as an external STOMP broker                                                               |
| Frontend       | React 19, TypeScript, Vite, Tailwind CSS, react-chessboard, chess.js, STOMP.js                         |
| Quality        | JUnit, Mockito, Testcontainers, JSpecify with NullAway, Vitest, ESLint                                 |
| Infrastructure | Docker Compose, nginx, GitHub Actions                                                                  |

---

## Getting Started

You need Docker and Docker Compose. These ports must be free: 3000, 8080, 5432, 6379, 61613, and 15672.

```bash
docker compose --profile app up --build
```

The `app` profile runs the whole application as it runs in production. Compose starts PostgreSQL, Redis, and RabbitMQ,
then two backend instances, which apply the database migrations on startup, and the frontend behind an nginx gateway
(`nginx.conf`). The gateway routes `/api` and `/ws` to the backend instances and everything else to the frontend. To
run more or fewer instances, add `--scale backend=<n>` or change `deploy.replicas` under `backend` in `compose.yaml`;
the gateway picks up the change within about 10 seconds.

| Service                         | URL                                           |
|---------------------------------|-----------------------------------------------|
| Frontend                        | http://localhost:3000                         |
| REST API                        | http://localhost:8080/api                     |
| API documentation (dev profile) | http://localhost:8080/swagger-ui.html         |
| Health check                    | http://localhost:8080/actuator/health         |
| RabbitMQ management UI          | http://localhost:15672 (`chess` / `password`) |

### Local development

Without the profile, Compose starts only PostgreSQL, Redis, and RabbitMQ:

```bash
docker compose up -d
```

Run the backend from your IDE (or `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` in `backend/`) with the
`dev` profile, which connects to those services on `localhost`. Then run `npm run dev` in `frontend/` with Node 24: Vite
serves the frontend on port 3000 and proxies `/api` and `/ws` to the backend on port 8080, so the URLs above stay the
same.

To try a game, register two accounts and sign in from two browsers, or from a normal and a private window. Create a
game on one, then join it from the other with the game ID.

The credentials in `compose.yaml` and `application-dev.yaml` are for local development only.

### Configuration

The backend reads these environment variables. Templates: `.env.example` for Docker Compose, which sets them from a
few credentials, and `backend/.env.example` for deploying the backend on its own.

| Variable                                                   | Purpose                                                                                    |
|------------------------------------------------------------|--------------------------------------------------------------------------------------------|
| `SPRING_PROFILES_ACTIVE`                                   | `dev` locally: API docs, all actuator endpoints, debug logging. Unset in production.       |
| `SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD`          | PostgreSQL connection                                                                      |
| `SPRING_DATA_REDIS_HOST`, `_PORT`                          | Redis connection                                                                           |
| `APP_ALLOWED_ORIGINS`                                      | Comma-separated frontend origins allowed for HTTP and WebSocket requests                   |
| `APP_WEBSOCKET_RELAY_ENABLED`                              | Route WebSocket messages through RabbitMQ. Required when running more than one instance.   |
| `APP_WEBSOCKET_RELAY_HOST`, `_PORT`, `_LOGIN`, `_PASSCODE` | RabbitMQ STOMP connection                                                                  |

---

## Testing

From `backend/`:

```bash
./mvnw test     # unit tests, no Docker needed
./mvnw verify   # also runs the integration tests
```

Integration tests (`*IT`) start PostgreSQL, Redis, and RabbitMQ with Testcontainers and apply the migrations with
Flyway, so they only need a running Docker daemon. `WebSocketRelayIT` starts two backend instances against one RabbitMQ
and checks that a move made on one reaches a player connected to the other.

From `frontend/`:

```bash
npm ci
npm run lint
npm test
npm run build   # includes the TypeScript check
```

GitHub Actions runs all of these on every push and pull request.

---

## API Overview

REST endpoints live under `/api`. With the dev profile, the full reference is at `/swagger-ui.html`.

| Area                | Endpoints                                                                                                                                                                                                  |
|---------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Accounts            | `POST /users` (register), `POST /login`, `GET /me`, `GET` / `PUT` / `DELETE /users`                                                                                                                        |
| Games               | `POST /games`, `POST /games/{gameId}/join`, `GET /games/{gameId}/state`, `GET /games/{gameId}/pgn`                                                                                                         |
| History and ratings | `GET /games/users/{userId}`, `GET /users/{userId}/ratings`                                                                                                                                                 |
| Friends             | `GET /friends`, `GET /friends/received`, `GET /friends/sent`, `POST /friends/send/{userId}`, `POST /friends/accept/{friendRequestId}`, `PUT /friends/reject/{friendRequestId}`, `DELETE /friends/{userId}` |

The STOMP endpoint is `/ws`. Topic names use dots because RabbitMQ topic names cannot contain `/`.

| Direction | Destination                                     | Purpose                                       |
|-----------|-------------------------------------------------|-----------------------------------------------|
| Send      | `/app/game/{gameId}/join`                       | Join as a player or spectator                 |
| Send      | `/app/game/{gameId}/move`                       | Make a move, with an optional promotion piece |
| Send      | `/app/game/{gameId}/flag`                       | Claim that the opponent's clock has run out   |
| Send      | `/app/game/{gameId}/resign`                     | Resign                                        |
| Send      | `/app/game/{gameId}/draw/offer`, `/draw/accept` | Offer or accept a draw                        |
| Send      | `/app/game/{gameId}/chat`                       | Send a chat message                           |
| Subscribe | `/topic/game.{gameId}`                          | Game state, including both clocks             |
| Subscribe | `/topic/game.{gameId}.chat`                     | Chat messages                                 |
| Subscribe | `/user/topic/errors`                            | Errors for your own actions                   |

---

## Project Structure

```text
chess-platform/
├── backend/                    # Spring Boot application (Maven wrapper, Dockerfile)
│   └── src/main/
│       ├── java/me/zilid/chessplatform/
│       │   ├── chess/          # Rules engine: board, move generation, game lifecycle, clocks, FEN/UCI/PGN
│       │   ├── rating/         # Rating contracts and the Elo implementation
│       │   ├── controller/     # REST and STOMP entry points, and error translation
│       │   ├── service/        # Use cases and transaction boundaries
│       │   ├── repository/     # PostgreSQL access, and Redis game state, deadlines, and locks
│       │   ├── model/          # JPA entities, API DTOs, and converters
│       │   ├── security/       # Spring Security setup and the session principal
│       │   └── config/         # Infrastructure wiring, including the WebSocket broker
│       └── resources/db/migrations/   # Flyway migrations
├── frontend/                   # React app: pages/, components/, services/, types/
├── .github/                    # CI workflow, Dependabot, and issue and PR templates
├── compose.yaml                # Local services; the app profile adds the full stack
└── nginx.conf                  # Gateway routing for the full stack
```

### Architecture notes

- Dependencies point one way: `controller -> service -> repository`. The `chess` and `rating` packages are
  independent of Spring, persistence, and security. Controllers pass `Player` values or user IDs into services, not
  Spring Security principals.
- Each backend package documents its responsibility in `package-info.java` and is `@NullMarked`. NullAway checks
  nullness at compile time.
- Spring Boot applies the Flyway migrations at startup, in every environment including tests. When several instances
  start together, Flyway's PostgreSQL lock lets one migrate while the others wait.
- `security.UserPrincipal` is stored in Redis sessions with Java serialization. Changing its class name, fields, or
  `serialVersionUID` invalidates existing sessions, so flush the Spring Session keys in Redis when deploying such a
  change.

---

## Roadmap

- Choose the promotion piece in the UI. The engine and API already support any piece; the board promotes to a queen.
- Matchmaking by rating
- Glicko-2 ratings
- Game analysis and an AI opponent with Stockfish
- Opening book
- Tournaments

---

## License

[MIT](LICENSE) © 2026 zilid10
