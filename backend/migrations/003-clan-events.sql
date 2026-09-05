-- Adds the clan's events.
--
-- schema.sql is written for a database that does not exist yet; this is for one that already does.
-- Nothing here touches the Boss of the Week tables or the clan tables added by 002.
--
-- Run once, against each deployment:
--   wrangler d1 execute botw --remote --file migrations/003-clan-events.sql

-- The clan's events.
--
-- Boss of the Week is one shape of competition. A clan runs raid nights, skilling weeks, minigame
-- evenings and socials, so what an event tracks and what it scores lives in a bag of configuration
-- rather than in columns — the columns here are only what every event has whatever kind it is: who it
-- belongs to, what it is called, and when it runs.
--
-- Nothing scores against this yet. These are the definitions; what actually happened comes next, and
-- inventing its tables before the trackers exist would be guessing at their shape.
CREATE TABLE IF NOT EXISTS clan_events (
	code        TEXT PRIMARY KEY,
	clan_code   TEXT NOT NULL REFERENCES clans(code) ON DELETE CASCADE,

	name        TEXT NOT NULL,

	-- pvm | raids | skilling | minigame | social | custom. What the calendar colours by.
	category    TEXT NOT NULL,

	-- Which template it was built from, kept so an event can be reopened in the screen that made it.
	template    TEXT NOT NULL,

	starts_at   INTEGER NOT NULL,
	ends_at     INTEGER NOT NULL,

	-- Only so the panel can show whoever made it the wall-clock time they chose; comparisons are UTC.
	timezone    TEXT NOT NULL DEFAULT 'UTC',

	-- What it tracks and what things are worth, as JSON. Opaque here on purpose: the plugin writes it
	-- from a template and reads it back, so its shape can change without a migration.
	config      TEXT NOT NULL DEFAULT '{}',

	-- What the leaderboard is sorted by: points, kc, xp, loot, time.
	leaderboard TEXT NOT NULL DEFAULT 'points',

	-- draft | published | cancelled. A cancelled event is kept, because people ask about those.
	status      TEXT NOT NULL DEFAULT 'draft',

	created_by  TEXT NOT NULL,
	created_at  INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS clan_events_by_clan ON clan_events (clan_code, starts_at DESC);
