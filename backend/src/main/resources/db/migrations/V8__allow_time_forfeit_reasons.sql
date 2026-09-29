-- Games can end on time: a flag, or a flag against a side that cannot checkmate (a draw).
ALTER TABLE match_records DROP CONSTRAINT match_records_reason_check;
ALTER TABLE match_records ADD CONSTRAINT match_records_reason_check
    CHECK (reason IN ('CHECKMATE', 'RESIGNATION', 'STALEMATE', 'ACCEPT_DRAW', 'INSUFFICIENT_MATERIAL',
                      'THREE_FOLD_REPETITION', 'FIFTY_MOVE_RULE', 'FLAGGED', 'FLAGGED_INSUFFICIENT_MATERIAL'));
