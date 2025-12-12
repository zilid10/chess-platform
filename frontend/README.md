# Chess Platform Frontend

A modern, real-time chess platform built with React, TypeScript, and Tailwind CSS.

## Features

- 🎮 Real-time multiplayer chess games with WebSocket
- 💬 In-game chat functionality
- 🎨 Modern, responsive UI with Tailwind CSS
- ♟️ Interactive chess board using react-chessboard
- 🔐 User authentication and authorization
- 🎯 Game state management with automatic reconnection

## Tech Stack

- **React 18** - UI framework
- **TypeScript** - Type safety
- **Vite** - Build tool
- **Tailwind CSS** - Styling
- **React Router** - Routing
- **Axios** - HTTP client
- **STOMP.js** - WebSocket messaging
- **chess.js** - Chess logic
- **react-chessboard** - Chess board component

## Prerequisites

- Node.js 18+ and npm/yarn
- Backend server running on http://localhost:8080

## Installation

1. Install dependencies:
```bash
cd frontend
npm install
```

2. Start the development server:
```bash
npm run dev
```

The application will be available at http://localhost:3000

## Building for Production

```bash
npm run build
```

The build output will be in the `dist` directory.

## Project Structure

```
frontend/
├── src/
│   ├── components/      # Reusable components
│   │   └── Navbar.tsx
│   ├── context/         # React contexts
│   │   └── AuthContext.tsx
│   ├── pages/           # Page components
│   │   ├── Login.tsx
│   │   ├── Register.tsx
│   │   ├── Dashboard.tsx
│   │   └── Game.tsx
│   ├── services/        # API and WebSocket services
│   │   ├── api.ts
│   │   ├── userService.ts
│   │   ├── friendService.ts
│   │   ├── gameService.ts
│   │   └── websocketService.ts
│   ├── types/           # TypeScript types
│   │   └── index.ts
│   ├── App.tsx          # Main app component
│   ├── main.tsx         # Entry point
│   └── index.css        # Global styles
├── public/              # Static assets
├── index.html           # HTML template
├── package.json
├── tsconfig.json
├── tailwind.config.js
└── vite.config.ts
```

## Available Scripts

- `npm run dev` - Start development server
- `npm run build` - Build for production
- `npm run preview` - Preview production build
- `npm run lint` - Run ESLint

## Usage

### Creating a Game

1. Log in to your account
2. Click "Create Game" on the dashboard
3. Share the generated Game ID with your friend

### Joining a Game

1. Log in to your account
2. Enter the Game ID provided by your friend
3. Click "Join Game"

### Playing

- Drag and drop pieces to make moves
- Use the chat panel to communicate
- Click "Resign" to forfeit the game
- Click "Offer Draw" to propose a draw

## WebSocket Endpoints

The frontend connects to the following WebSocket endpoints:

- `/ws` - WebSocket connection endpoint
- `/app/game/{gameId}/join` - Join a game
- `/app/game/{gameId}/move` - Make a move
- `/app/game/{gameId}/chat` - Send chat message
- `/app/game/{gameId}/resign` - Resign from game
- `/app/game/{gameId}/draw` - Offer/accept draw

## API Endpoints

The frontend communicates with these REST API endpoints:

- `POST /api/auth/login` - User login
- `POST /api/users` - Create user account
- `GET /api/users` - Search users
- `GET /api/friends` - Get friends list
- `POST /api/friends/request/{userId}` - Send friend request
- `POST /api/friends/accept/{userId}` - Accept friend request
- `GET /api/games/users/{userId}` - Get user's game history

## Environment Variables

The Vite proxy configuration automatically forwards API requests to the backend server. If you need to customize the backend URL, modify `vite.config.ts`:

```typescript
server: {
  proxy: {
    '/api': {
      target: 'http://your-backend-url',
      changeOrigin: true,
    }
  }
}
```

## Styling

The project uses Tailwind CSS with custom utility classes defined in `src/index.css`:

- `.btn` - Base button styles
- `.btn-primary` - Primary button variant
- `.btn-secondary` - Secondary button variant
- `.btn-danger` - Danger button variant
- `.input` - Input field styles
- `.card` - Card container styles

## License

This project is part of the Chess Platform application.
