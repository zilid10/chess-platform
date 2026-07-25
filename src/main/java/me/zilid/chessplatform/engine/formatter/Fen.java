package me.zilid.chessplatform.engine.formatter;

import me.zilid.chessplatform.engine.*;

import java.util.regex.Pattern;

public class Fen {
    private static final Pattern castlingRightsPattern = Pattern.compile("^(-|(?!$)K?Q?k?q?)$");

    private Fen() {
    }

    public static Position parse(String fen) {
        String[] parsedFen = fen.split("\\s+");
        if (parsedFen.length != 6) {
            throw new IllegalArgumentException("Invalid fen: " + fen);
        }

        Board board = new Board();
        // i = FEN rank row (rank 8 first), j = file; square (file j, rank 8-i) lives at board[j][7 - i]
        int rank = 7, file = 0;
        for (char c : parsedFen[0].toCharArray()) {
            switch (c) {
                case 'Q', 'q', 'K', 'k', 'R', 'r', 'B', 'b', 'N', 'n', 'P', 'p' -> {
                    board.put(Square.of(file, rank), notationToPiece(c));
                    file++;
                }
                case '/' -> {
                    if (file != 8) {
                        throw new IllegalArgumentException("Too many files in FEN: " + fen);
                    }
                    if (rank <= 0) {
                        throw new IllegalArgumentException("Too many ranks in FEN: " + fen);
                    }
                    rank--;
                    file = 0;
                }
                default -> {
                    if (c > '8' || c < '1') {
                        throw new IllegalArgumentException("Invalid fen: " + fen);
                    }
                    file += c - '0';
                }
            }
            if (file > 8) {
                throw new IllegalArgumentException("Too many files in FEN: " + fen);
            }
        }
        if (file != 8 || rank != 0) {
            throw new IllegalArgumentException("Incomplete board in FEN: " + fen);
        }
        Color turnColor = notationToColor(parsedFen[1]);
        CastlingRights castlingRights = castlingRightsFromSymbol(parsedFen[2]);
        Square enPassantTarget = parsedFen[3].equals("-") ? null : Square.fromNotation(parsedFen[3]);
        try {
            int halfMoveClock = Integer.parseInt(parsedFen[4]);
            int fullMoveClock = Integer.parseInt(parsedFen[5]);
            return new Position(board, turnColor, castlingRights, enPassantTarget, halfMoveClock, fullMoveClock);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid fen: " + fen, e);
        }
    }

    /**
     * Get the fen representation of the current position
     */
    public static String format(Position position) {
        StringBuilder fen = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            int count = 0;
            for (int file = 0; file < 8; file++) {
                Piece piece = position.getBoard().pieceAt(Square.of(file, rank));
                if (piece != null && count != 0) {
                    fen.append(count);
                    fen.append(pieceToNotation(piece));
                    count = 0;
                } else if (piece != null) {
                    fen.append(pieceToNotation(piece));
                } else {
                    count++;
                }
                if (file == 7 && count != 0) {
                    fen.append(count);
                }
            }
            if (rank != 0) {
                fen.append("/");
            }
        }
        fen.append(" ").append(colorToNotation(position.getTurnColor()));
        fen.append(" ").append(castlingRightsToSymbol(position.getCastlingRights()));
        fen.append(" ").append(position.getEnPassantTarget() == null ? "-" : position.getEnPassantTarget().toNotation());
        fen.append(" ").append(position.getHalfMoveClock());
        fen.append(" ").append(position.getFullMoveClock());
        return fen.toString();
    }

    public static Piece notationToPiece(char c) {
        Color color = Character.isUpperCase(c) ? Color.WHITE : Color.BLACK;
        PieceType type = switch (c) {
            case 'Q', 'q' -> PieceType.QUEEN;
            case 'K', 'k' -> PieceType.KING;
            case 'R', 'r' -> PieceType.ROOK;
            case 'B', 'b' -> PieceType.BISHOP;
            case 'N', 'n' -> PieceType.KNIGHT;
            case 'P', 'p' -> PieceType.PAWN;
            default -> throw new IllegalArgumentException("Invalid notation: " + c);

        };
        return Piece.of(color, type);
    }

    public static char pieceToNotation(Piece piece) {
        char notation = switch (piece.type()) {
            case KING -> 'k';
            case QUEEN -> 'q';
            case ROOK -> 'r';
            case BISHOP -> 'b';
            case KNIGHT -> 'n';
            case PAWN -> 'p';
        };
        return piece.color().isWhite() ? Character.toUpperCase(notation) : Character.toLowerCase(notation);
    }

    public static char colorToNotation(Color color) {
        return switch (color) {
            case WHITE -> 'w';
            case BLACK -> 'b';
        };
    }

    public static Color notationToColor(String color) {
        if (color.equals("w")) {
            return Color.WHITE;
        }
        if (color.equals("b")) {
            return Color.BLACK;
        }
        throw new IllegalArgumentException("invalid color: " + color);
    }

    public static String castlingRightsToSymbol(CastlingRights castlingRights) {
        StringBuilder sb = new StringBuilder();
        if (castlingRights.has(Color.WHITE, CastlingSide.KINGSIDE)) {
            sb.append("K");
        }
        if (castlingRights.has(Color.WHITE, CastlingSide.QUEENSIDE)) {
            sb.append("Q");
        }
        if (castlingRights.has(Color.BLACK, CastlingSide.KINGSIDE)) {
            sb.append("k");
        }
        if (castlingRights.has(Color.BLACK, CastlingSide.QUEENSIDE)) {
            sb.append("q");
        }
        return sb.toString();
    }

    public static CastlingRights castlingRightsFromSymbol(String symbol) {
        if (symbol == null || !castlingRightsPattern.matcher(symbol).matches()) {
            throw new IllegalArgumentException("castling rights symbol is not valid: '" + symbol + "'");
        }

        CastlingRights castlingRights = CastlingRights.NONE;
        if (symbol.contains("K")) {
            castlingRights = castlingRights.with(Color.WHITE, CastlingSide.KINGSIDE);
        }
        if (symbol.contains("Q")) {
            castlingRights = castlingRights.with(Color.WHITE, CastlingSide.QUEENSIDE);
        }
        if (symbol.contains("k")) {
            castlingRights = castlingRights.with(Color.BLACK, CastlingSide.KINGSIDE);
        }
        if (symbol.contains("q")) {
            castlingRights = castlingRights.with(Color.BLACK, CastlingSide.QUEENSIDE);
        }
        return castlingRights;
    }
}

