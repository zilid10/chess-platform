# Chess Platform

## Overview

Chess Platform is a web-based chess application that allows users to:

- Play chess in real-time against other players
- Create accounts and manage profiles
- Send and manage friend requests
- View game history with full PGN notation
- Spectate ongoing games
- Chat during matches

---

## Project Architecture

### Repository Layout

```text
chess-platform/
├── backend/
│   ├── .mvn/                  # Maven wrapper configuration
│   ├── src/main/java/         # Application and chess logic
│   ├── src/main/resources/    # Spring configuration and db/migrations/
│   ├── src/test/              # Backend tests
│   ├── Dockerfile
│   ├── flyway.conf            # Local Flyway CLI configuration
│   ├── mvnw / mvnw.cmd
│   └── pom.xml
├── frontend/                  # React application, npm config, and Dockerfile
├── docker/volumes/            # Local runtime data (Git-ignored)
├── .github/                   # CI and dependency updates
└── docker-compose.yml         # Shared application stack
```

Run Docker Compose from the repository root, Maven and Flyway commands from
`backend/`, and npm commands from `frontend/`. Compose, CI, and the local Flyway
configuration all use `backend/src/main/resources/db/migrations/` as the migration source.
Migrations are applied externally; Spring Boot does not run Flyway automatically.

### Backend

- Java 21: with Virtual Threads
- Spring Boot 4.1.1: Core framework
- Spring Security: Session-based authentication
- Spring Session + Redis: Login sessions shared by every backend instance
- Spring WebSocket: Real-time bidirectional communication (STOMP over WebSocket)
- RabbitMQ 4.1: External STOMP broker, so WebSocket messages reach clients on any backend instance
- nginx: Load balancer in front of the backend instances (Docker Compose)
- Spring Data JPA: Data persistence layer
- PostgreSQL 17.5: Primary database
- Flyway: Database migration management
- Maven: Build and dependency management

### Frontend

- React + TypeScript
- Vite: Build tool
- TailwindCSS: Styling

### Backend Architecture

The backend uses a layered architecture under `backend/src/main/java/me/zilid/chessplatform/`:

| Package | Responsibility |
| --- | --- |
| `chess/` | Board state and legal moves; no Spring, account entities, or storage dependencies |
| `chess/game/` | Game lifecycle, player contracts, and reconstruction snapshots, time controls, and clocks |
| `chess/format/` | FEN and UCI; `pgn/` formats SAN and PGN from engine positions and moves |
| `rating/` | Framework-independent rating contracts and values; `elo/` implements Elo calculations |
| `controller/` | HTTP/STOMP entry points, authenticated identity conversion, and response publication |
| `controller/advice/` | Translate application failures into HTTP and STOMP error responses |
| `service/` | Application use cases and transaction boundaries; coordinate engines, persistence, and API mapping |
| `repository/` | Spring Data access to PostgreSQL |
| `repository/game/` | Redis game persistence, reconstruction, expiry, and distributed locking |
| `model/entity/` | JPA entities and persistence base classes |
| `model/dto/` | HTTP/STOMP request and response contracts |
| `model/converter/` | Map API contracts to/from JPA entities, including password encoding for account writes |
| `security/` | Spring Security configuration, the session-stored `UserPrincipal`, and its credential loader |
| `config/` | Spring and infrastructure wiring |
| `exception/` | Application exception types, independent of transport handlers |

The main dependency direction is `controller -> service -> repository`, with application code using the
`chess` and `rating` engines. These engines must not import controllers, services, persistence, or security.
Controllers pass `Player` or user IDs into services instead of passing Spring Security principals.
Repositories must not call application services or authentication loaders. Each Java package documents
its responsibility in `package-info.java` and retains its JSpecify `@NullMarked` default.

For active games, `MatchService` works with `GameStateStore` and engine `Game` objects. The store owns
the package-private `ActiveGameState` JSON record and `ActiveGameStateConverter`; it resolves player IDs
through `UserRepo` and reconstructs `RegisteredPlayer` values without loading credentials into a principal.
`GameSnapshot` remains the engine's reconstruction contract. Redis JSON field names and keys are unchanged.

`security.UserPrincipal` is stored in Redis-backed login sessions with Java serialization, so its qualified
class name, serialized fields, and `serialVersionUID` are part of the session format. Changing any of them
invalidates existing sessions: flush the Spring Session keys in Redis when deploying such a change.

This is a pragmatic layered application, not a set of independently deployable feature modules. Services
share API DTOs, DTOs reuse some domain/entity enums, and entities use chess/rating value types. Those are
explicit shared contracts; new use-case orchestration belongs in services, not in DTOs or converters.

### Frontend Boundaries

`pages/` owns routed screens, `components/` owns reused UI, and `context/` owns shared authentication state.
`services/` owns HTTP and STOMP access and error extraction; it must not import screens or React context.
`types/` owns shared API types and time-control constants. `App.tsx` composes routing and providers.
The current frontend is small enough that these folders remain useful without additional feature nesting.

---

## Advanced Features

### 1. Custom Chess Engine Implementation

Core Engine Components:

- `chess/game/Game.java` - Game lifecycle and move history
- `chess/MoveGenerator.java` - Legal move generation and validation
- `chess/Board.java` - Piece placement and attack detection
- `chess/Position.java` - Position state, move application, and undo
- `chess/Move.java` and `chess/UndoInfo.java` - Move representation and undo data
- `chess/format/` - FEN, UCI, SAN, and PGN notation

Chess Piece Implementation:

- Valid move calculation
- Attack pattern detection

Advanced Chess Rules Implemented:

1. Castling (kingside and queenside)
    - Validates king and rook haven't moved
    - Checks squares between are empty
    - Ensures king doesn't cross attacked squares
2. En Passant capture
    - Tracks double pawn moves
    - Validates en passant moves
3. Pawn Promotion (auto-promotes to Queen)
4. Check, Checkmate Detection
5. Draw Conditions:
    - Threefold Repetition
    - Fifty-Move Rule
    - Insufficient Material
    - Stalemate detection
6. FEN (Forsyth-Edwards Notation) generation for position state
7. PGN (Portable Game Notation) export for game history
8. File/Rank/Square Disambiguation for move notation

### 2. Real-Time WebSocket Communication

The platform uses Spring WebSocket with STOMP protocol for real-time gameplay:

WebSocket Features:

- Game Session Management (`MatchService.java`)
    - Active games are stored in Redis (`GameStateStore.java`), so they survive restarts and can be shared by
      several backend instances; each game expires after 1 hour without activity
    - Every change runs under a per-game Redis lock, so concurrent moves and joins are serialized across instances
    - Player connection/disconnection handling
    - Spectator support
    - Automatic cleanup after game completion (the Redis entry expires after 1 minute)
    - Games are persisted to database when completed
- Publishing:
    - `/app/game/{gameId}/join` - Player joins game
    - `/app/game/{gameId}/move` - Chess move execution
    - `/app/game/{gameId}/resign` - Player resignation
    - `/app/game/{gameId}/draw/offer` - Draw offer
    - `/app/game/{gameId}/draw/accept` - Draw acceptance
- Broadcasting (dot-separated, as RabbitMQ topic names cannot contain `/`):
    - `/topic/game.{gameId}` - Game state updates to all participants
    - `/topic/game.{gameId}.chat` - Chat messages
- Error Handling:
    - `/user/topic/errors` - User-specific error messages
    - `@MessageExceptionHandler` - Handle WebSocket exceptions
    - Custom exceptions (`GameNotFoundException`, `GameIsOverException`)
- Horizontal scaling (`WebsocketConfig.java`):
    - With `APP_WEBSOCKET_RELAY_ENABLED=true`, subscriptions live in RabbitMQ through Spring's STOMP broker relay
      instead of each instance's memory, so a move handled by one instance reaches players connected to another
    - User destinations are broadcast through the broker, so `/user/...` messages find users on any instance
    - Without the relay, each instance uses Spring's in-memory broker, which only works for a single instance
    - `WebSocketRelayIT` starts two instances against RabbitMQ and checks delivery between them

### 3. Security & Authentication

Spring Security configuration with:

- Session-based authentication (JSESSIONID cookie), with sessions stored in Redis by Spring Session so any backend
  instance can serve any request
- Password encryption with BCrypt (`DelegatingPasswordEncoder`)
- Custom `UserDetailsService` and `UserDetails` implementation
- CORS configuration for frontend
- Set `APP_ALLOWED_ORIGINS` to a comma-separated list of frontend origins when deploying outside the local defaults; it
  applies to both HTTP and WebSocket requests.

### 4. Database Schema with Flyway Migrations

Flyway manages database versioning with migration scripts:

- `V1__create_user_entity.sql` - User accounts
- `V2__create_match_record.sql` - Match history with PGN
- `V3__create_friendship_relationship.sql` - Friends
- `V4__create_friendship_request.sql` - Friend request system

---

## How to Run

### Quick Start (Recommended)

Prerequisites

- Docker and Docker Compose installed
- Ports available: 3000 (frontend), 8080 (backend load balancer), 5432 (database), 6379 (Redis),
  61613 and 15672 (RabbitMQ STOMP and management UI)

```bash
docker compose up --build
```

Compose starts two backend instances behind an nginx load balancer (`backend-lb`). Change `deploy.replicas` under
`backend` in `docker-compose.yml` to run more or fewer, then restart `backend-lb` so it picks up the new instances.

### Manual Setup (Without Docker Compose, Not Recommended)

Manual setup can be very error-prone, docker compose setup is recommended.

1. Ensure PostgreSQL is running locally on port 5432 and Redis on port 6379. Create the database and user. A single
   backend instance does not need RabbitMQ; it uses the in-memory broker unless `APP_WEBSOCKET_RELAY_ENABLED=true`.
2. From the repository root, enter `backend/`, configure `flyway.conf`, and migrate the database:

```shell
cd backend
vim flyway.conf # Set flyway.url, flyway.user, and flyway.password for your local database
flyway -configFiles=flyway.conf migrate
```

3. Start the backend from the same `backend/` directory using JDK 21:

```shell
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/your_db
export SPRING_DATASOURCE_USERNAME=your_db_user
export SPRING_DATASOURCE_PASSWORD=your_db_password
export SPRING_DATA_REDIS_HOST=localhost
./mvnw spring-boot:run
```

4. In a separate terminal at the repository root, configure and run the frontend:

```bash
cd frontend
vim vite.config.ts # Change both proxy targets from 'http://backend-lb:8080' to 'http://localhost:8080'
npm ci
npm run dev
```

### Validation

- From `backend/`: `./mvnw test` for the quick suite. Run `./mvnw verify` with
  PostgreSQL, Redis, applied migrations, and the environment variables above for
  database-backed tests and integration tests. `WebSocketRelayIT` also needs RabbitMQ with the STOMP plugin and
  `APP_WEBSOCKET_RELAY_HOST`, `APP_WEBSOCKET_RELAY_LOGIN`, and `APP_WEBSOCKET_RELAY_PASSCODE` (Compose uses
  `chess` / `password`).
- From `frontend/`: `npm ci`, `npm run lint`, `npm test`, and `npm run build`.

### Access the Application

- Frontend: http://localhost:3000
- Backend API: http://localhost:8080/api
    - Health Check: http://localhost:8080/actuator/health
- RabbitMQ management UI: http://localhost:15672 (`chess` / `password`)

Testing: To test the chess game, use two different browsers (or incognito/private windows) to log in with these test
accounts (or create new accounts):

- Username: `anyu`, Password: `anyu`
- Username: `zili`, Password: `zili`

---

## Future Enhancements

Potential areas for expansion:

- ELO rating system and matching system
- Timed control with clocks
- Game analysis engine
- AI opponent (Stockfish integration)
- Opening book integration
- Tournament system
