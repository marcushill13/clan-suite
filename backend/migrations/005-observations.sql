-- Adds what the plugins report, which is what events are scored from.
--
-- schema.sql is written for a database that does not exist yet; this is for one that already does.
-- Nothing here touches any earlier table.
--
-- Run once, against each deployment:
--   wrangler d1 execute botw --remote --file migrations/005-observations.sql

-- What the plugins saw.
--
-- The client reports what happened — a kill, a drop, an amount of experience — and the service works
-- out what it was worth from the event's own rules. That division is the whole trust model: a modified
-- client can claim a kill it never got, but it cannot decide the kill was worth five hundred points.
--
-- Stored one by one rather than as a running total, for the three reasons the challenge events are:
-- a resend after a disconnect lands on the same row, a threshold rule can be applied to the total
-- rather than to each report, and every score can be rebuilt when the rules are corrected mid-event.
CREATE TABLE IF NOT EXISTS event_observations (
	-- Made by the plugin when it happened and never changed, which is the whole idempotency story.
	id          TEXT PRIMARY KEY,

	event_code  TEXT NOT NULL REFERENCES clan_events(code) ON DELETE CASCADE,
	rsn         TEXT NOT NULL,

	-- kc | drop | xp | death | completion | time, and whatever the trackers learn to see next.
	metric      TEXT NOT NULL,

	-- What it was about: the boss, the item, the skill. Null where the metric says it all.
	subject     TEXT,

	-- Kills, quantity, experience, seconds. What it means depends on the metric.
	amount      INTEGER NOT NULL,

	occurred_at INTEGER NOT NULL,
	recorded_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS event_observations_by_person ON event_observations (event_code, rsn);
