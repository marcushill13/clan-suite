-- Boss of the Week storage.
--
-- Events are kept individually rather than as a running total per player. It costs a little more
-- space and buys three things: the "where did my points come from" breakdown the panel shows, an
-- idempotent resubmit (the plugin can send the same kill twice after a disconnect without it
-- counting twice), and the ability to recompute every score if a challenge's points are edited
-- mid-week — which will happen, because someone always sets a number wrong.

CREATE TABLE IF NOT EXISTS challenges (
	code           TEXT PRIMARY KEY,
	name           TEXT NOT NULL,
	boss           TEXT NOT NULL,

	-- Epoch milliseconds. The timezone is stored alongside only so the panel can show the creator
	-- the wall-clock time they chose; every comparison is done in UTC.
	starts_at      INTEGER NOT NULL,
	ends_at        INTEGER NOT NULL,
	timezone       TEXT NOT NULL,

	-- How many kills earn kc_points. Both sides are the creator's choice.
	kc_per         INTEGER NOT NULL,
	kc_points      INTEGER NOT NULL,

	-- The drop list, as JSON: [{ "name": "Vorki", "itemId": 21992, "points": 20 }]
	drops          TEXT NOT NULL,

	-- Proves whoever is editing is the person who made it. Never sent to participants.
	creator_token  TEXT NOT NULL,
	creator_rsn    TEXT NOT NULL,

	created_at     INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS participants (
	challenge_code TEXT NOT NULL REFERENCES challenges(code) ON DELETE CASCADE,
	rsn            TEXT NOT NULL,
	token          TEXT NOT NULL,
	joined_at      INTEGER NOT NULL,

	-- Running totals, kept alongside the events rather than derived from them on every read.
	--
	-- The leaderboard is the most-read thing here and summing the event table to build it means
	-- reading every kill anyone has logged, every time anyone looks. A week of fifty people at a
	-- thousand kills is fifty thousand rows per glance, which burns through a day's read allowance in
	-- an afternoon. These three columns make that read cost one row per participant instead.
	--
	-- The events remain the source of truth: these are rebuilt from them whenever points change.
	points         INTEGER NOT NULL DEFAULT 0,
	kills          INTEGER NOT NULL DEFAULT 0,
	drops          INTEGER NOT NULL DEFAULT 0,

	-- What the creator has added or taken away by hand, kept apart from the tracked points rather than
	-- folded into them.
	--
	-- It has to be a column of its own because `points` is rebuilt from the events every time anything
	-- is written. Editing that directly would look right until the player's next kill, which would
	-- recompute the total and silently undo the creator. Stored as the difference, so `points` is
	-- always the tracked score plus this, and both halves keep working: a mobile player has no events
	-- and so is entirely this, and a tracked player given a bonus keeps counting kills underneath it.
	adjustment     INTEGER NOT NULL DEFAULT 0,

	-- Added by the creator rather than having joined from a plugin — a mobile player, whose kills
	-- nobody can see. Shown on the leaderboard, because a total that arrived by hand should not be
	-- passed off as one that was counted.
	--
	-- Cleared if they ever do join properly, which happens by itself when they enter the code.
	manual         INTEGER NOT NULL DEFAULT 0,

	PRIMARY KEY (challenge_code, rsn)
);

CREATE TABLE IF NOT EXISTS events (
	-- Made by the plugin, so a resend after a disconnect lands on the same row rather than a second
	-- one. This is the whole idempotency story.
	id             TEXT PRIMARY KEY,

	challenge_code TEXT NOT NULL REFERENCES challenges(code) ON DELETE CASCADE,
	rsn            TEXT NOT NULL,

	-- 'kc' or 'drop'.
	kind           TEXT NOT NULL,

	-- Null for a kill count event.
	item_name      TEXT,

	-- Kills for a 'kc' event, quantity for a 'drop'.
	amount         INTEGER NOT NULL,

	-- Worked out here from the challenge's own configuration, never taken from the client. The plugin
	-- reports what happened; what it is worth is not its decision.
	points         INTEGER NOT NULL,

	occurred_at    INTEGER NOT NULL,
	recorded_at    INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS events_by_challenge ON events (challenge_code, rsn);
CREATE INDEX IF NOT EXISTS challenges_by_creator ON challenges (creator_rsn);

-- Evidence.
--
-- The clan already verifies drops with screenshots sent to Discord, so the same pictures are kept
-- here instead — organised by challenge and by player, and gathered without anyone having to remember
-- to press a key at the moment a pet drops.
--
-- Stored as a downscaled JPEG inline rather than in object storage. A scoring drop is a rare event —
-- a unique or a pet, not every kill — so a week of a fifty-person clan is a few hundred images at
-- around two hundred kilobytes. That fits here comfortably and saves running a second service.
--
-- Two hundred rather than the forty it began at, because at forty the game's own writing could not be
-- read, and reading it is the whole point: a clan spots a faked drop by the drop message that is not
-- there, or by a script's text sitting where the mouse tooltip belongs.
--
-- The full-resolution original never leaves the player's machine; this is the readable copy.
CREATE TABLE IF NOT EXISTS shots (
	-- The event it belongs to, so an upload that is retried replaces rather than duplicates.
	event_id       TEXT PRIMARY KEY,

	challenge_code TEXT NOT NULL REFERENCES challenges(code) ON DELETE CASCADE,
	rsn            TEXT NOT NULL,
	item_name      TEXT NOT NULL,
	occurred_at    INTEGER NOT NULL,
	uploaded_at    INTEGER NOT NULL,

	-- base64 JPEG.
	image          TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS shots_by_challenge ON shots (challenge_code, rsn);

-- Clans.
--
-- Boss of the Week knew two kinds of person: whoever made a challenge, and whoever joined it. A clan
-- runs events for years with the same few people organising them, so membership is granted once and
-- carries a rank, rather than being re-established for every competition.
--
-- The member limit is the game's own five hundred, stored per clan rather than assumed, so a clan that
-- negotiates a different one later does not need a migration.
CREATE TABLE IF NOT EXISTS clans (
	code              TEXT PRIMARY KEY,
	name              TEXT NOT NULL,
	tagline           TEXT NOT NULL DEFAULT '',
	owner_rsn         TEXT NOT NULL,

	-- Whether the hub shows it. A clan that is not listed is still reachable by its code, which is the
	-- difference between "not recruiting publicly" and "not here".
	listed            INTEGER NOT NULL DEFAULT 1,

	applications_open INTEGER NOT NULL DEFAULT 1,

	member_limit      INTEGER NOT NULL DEFAULT 500,

	-- Kept alongside rather than counted on every read, for the same reason the leaderboard's totals
	-- are: the directory shows this for every clan on the screen at once.
	members           INTEGER NOT NULL DEFAULT 0,

	created_at        INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS clans_listed ON clans (listed, members DESC);

CREATE TABLE IF NOT EXISTS clan_members (
	clan_code  TEXT NOT NULL REFERENCES clans(code) ON DELETE CASCADE,
	rsn        TEXT NOT NULL,

	-- owner | deputy | admin | moderator | member. Ordered in the code, not here, because SQLite has
	-- nothing to enforce it with and a rank read back as a string is checked either way.
	role       TEXT NOT NULL,

	-- What proves a request is theirs. Never sent to anyone but the member it belongs to.
	token      TEXT NOT NULL,

	joined_at  INTEGER NOT NULL,

	PRIMARY KEY (clan_code, rsn)
);

CREATE INDEX IF NOT EXISTS clan_members_by_token ON clan_members (token);
CREATE INDEX IF NOT EXISTS clan_members_by_rsn ON clan_members (rsn);

-- Applications to join.
--
-- Kept after they are decided rather than deleted. Somebody reapplying the week after being turned
-- down should not arrive looking like a first-time applicant, and nobody should have to remember who
-- they already said no to.
CREATE TABLE IF NOT EXISTS clan_applications (
	clan_code  TEXT NOT NULL REFERENCES clans(code) ON DELETE CASCADE,
	rsn        TEXT NOT NULL,
	message    TEXT NOT NULL DEFAULT '',

	-- pending | accepted | denied
	status     TEXT NOT NULL DEFAULT 'pending',

	applied_at INTEGER NOT NULL,
	decided_at INTEGER,
	decided_by TEXT,

	PRIMARY KEY (clan_code, rsn)
);

CREATE INDEX IF NOT EXISTS clan_applications_pending ON clan_applications (clan_code, status, applied_at);

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
