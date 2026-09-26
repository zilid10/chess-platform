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
- Spring WebSocket: Real-time bidirectional communication (STOMP over WebSocket)
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

- `chess/` - Board state, move rules, game lifecycle, and notation
- `controller/` - API endpoints and WebSocket handlers
- `service/` - Business logic layer
- `repository/` - Data access layer
- `model/` - Data models
    - `entity/` - JPA entities
    - `dto/` - Data Transfer Objects
    - `converter/` - Entity-DTO converters
- `config/` - Configuration classes
    - `SecurityConfig.java` - Security configuration
    - `WebsocketConfig.java` - WebSocket configuration
- `exception/` - Custom exceptions and global exception handler

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
    - Thread safe management
    - Active games are stored in a `ConcurrentHashMap` (`MatchService.java`) for high-performance access
    - Player connection/disconnection handling
    - Spectator support
    - Automatic cleanup after game completion (1-minute delay)
    - Games are persisted to database when completed
- Publishing:
    - `/app/game/{gameId}/join` - Player joins game
    - `/app/game/{gameId}/move` - Chess move execution
    - `/app/game/{gameId}/resign` - Player resignation
    - `/app/game/{gameId}/draw/offer` - Draw offer
    - `/app/game/{gameId}/draw/accept` - Draw acceptance
- Broadcasting:
    - `/topic/game/{gameId}` - Game state updates to all participants
    - `/topic/game/{gameId}/chat` - Chat messages
- Error Handling:
    - `/queue/errors` - User-specific error messages;
    - `@MessageExceptionHandler` - Handle WebSocket exceptions
    - Custom exceptions (`GameNotFoundException`, `GameIsOverException`)

### 3. Security & Authentication

Spring Security configuration with:

- Session-based authentication (JSESSIONID cookie)
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
- Ports available: 3000 (frontend), 8080 (backend), 5432 (database)

```bash
docker compose up --build
```

### Manual Setup (Without Docker Compose, Not Recommended)

Manual setup can be very error-prone, docker compose setup is recommended.

1. Ensure PostgreSQL is running locally on port 5432 and Redis on port 6379. Create the database and user.
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
vim vite.config.ts # Change both proxy targets from 'http://backend:8080' to 'http://localhost:8080'
npm ci
npm run dev
```

### Validation

- From `backend/`: `./mvnw test` for the quick suite. Run `./mvnw verify` with
  PostgreSQL, Redis, applied migrations, and the environment variables above for
  database-backed tests and integration tests.
- From `frontend/`: `npm ci`, `npm run lint`, `npm test`, and `npm run build`.

### Access the Application

- Frontend: http://localhost:3000
- Backend API: http://localhost:8080/api
    - Health Check: http://localhost:8080/actuator/health

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
