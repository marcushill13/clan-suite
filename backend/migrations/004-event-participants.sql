-- Adds the people taking part in an event.
--
-- schema.sql is written for a database that does not exist yet; this is for one that already does.
-- Nothing here touches the Boss of the Week tables, the clan tables from 002, or the events from 003.
--
-- Run once, against each deployment:
--   wrangler d1 execute botw --remote --file migrations/004-event-participants.sql

-- Who is taking part in an event.
--
-- Signing up rather than being signed up: a leaderboard should hold the people who turned up, not five
-- hundred clan members sitting on nought. It also gives the trackers something to check before they
-- report anything, so a kill counts towards the events the player actually joined.
--
-- Points are split the same way the challenge leaderboard splits them, and for the same reason: the
-- counted half will be rewritten every time the trackers recompute, and a correction typed in by hand
-- must not be undone by the next recount. Attendance is a column of its own because some events have
-- no other evidence — nothing a client can read proves somebody turned up to a hide and seek.
CREATE TABLE IF NOT EXISTS clan_event_participants (
	event_code TEXT NOT NULL REFERENCES clan_events(code) ON DELETE CASCADE,
	rsn        TEXT NOT NULL,
	joined_at  INTEGER NOT NULL,

	-- Ticked by a person who was there.
	attended   INTEGER NOT NULL DEFAULT 0,

	-- What was counted, and what was given or taken away by hand.
	points     INTEGER NOT NULL DEFAULT 0,
	adjustment INTEGER NOT NULL DEFAULT 0,

	PRIMARY KEY (event_code, rsn)
);

CREATE INDEX IF NOT EXISTS clan_event_participants_board
	ON clan_event_participants (event_code, points DESC);
