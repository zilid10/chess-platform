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

### Backend
- Java 25: with Virtual Threads
- Spring Boot 4.0.0: Core framework
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

This project used layered architecture:
- `engine/` - Chess game management and chess engine logic (decoupled from Spring)
    - `pieces/` - Individual piece implementations
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
- `Game.java` - Thread safe game management
- `ChessEngine.java` - Chess engine implementation
- `Board.java` - Board state management and move validation
- `Move.java` & `MoveHistory.java` - Move tracking
- `Position.java` - Chess notation and coordinate translation

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
6. FEN (Forsyth-Edwards Notation) generation for board state
7. PGN (Portable Game Notation) export for game history
8. File/Rank/Square Disambiguation for move notation

### 2. Real-Time WebSocket Communication

The platform uses Spring WebSocket with STOMP protocol for real-time gameplay:

WebSocket Features:
- Game Session Management (`GameSocketController.java`)
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

### 4. Security & Authentication

Spring Security configuration with:
- Session-based authentication (JSESSIONID cookie)
- Password encryption with BCrypt (`DelegatingPasswordEncoder`)
- Custom `UserDetailsService` and `UserDetails` implementation
- CORS configuration for frontend

### 5. Database Schema with Flyway Migrations

Flyway manages database versioning with migration scripts:
- `V1__create_user_entity.sql` - User accounts
- `V2__create_match_record.sql` - Match history with PGN
- `V3__create_friendship_relationship.sql` - Friends
- `V4__create_friendship_request.sql` - Friend request system

---

## How to Run

###  Quick Start (Recommended)

Prerequisites
- Docker and Docker Compose installed
- Ports available: 3000 (frontend), 8080 (backend), 5432 (database)

```bash
docker compose up --build
```

### Manual Setup (Without Docker Compose, Not Recommended)

Manual setup can be very error-prone, docker compose setup is recommended.

1. Ensure PostgreSQL is running locally on port 5432
2. configure `flyway.conf` and migrate database
```shell
vim flyway.conf # some settings need to be changed (e.g., flyway.url, flyway.user, flyway.password)
flyway migrate
```
3. Start up backend
```shell
./mvnw clean install
./mvnw spring-boot:run -Dspring.datasource.url=jdbc:postgresql://localhost:5432/your_db \
                       -Dspring.datasource.username=your_db_user \
                       -Dspring.datasource.password=your_db_password
```
4. configure `vite.config.ts` and run frontend
```bash
vim vite.config.ts # some settings need to be changed (Change proxy target from 'http://backend:8080' to 'http://localhost:8080')
cd frontend
npm install
npm run dev
```

### Access the Application

- Frontend: http://localhost:3000
- Backend API: http://localhost:8080/api
    - Health Check: http://localhost:8080/actuator/health

Testing: To test the chess game, use two different browsers (or incognito/private windows) to log in with these test accounts (or create new accounts):
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
