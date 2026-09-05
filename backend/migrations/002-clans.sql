-- Adds clans, their members and their applications.
--
-- schema.sql is written for a database that does not exist yet; this is for one that already does.
-- Nothing here touches the Boss of the Week tables, so every running challenge carries on untouched:
-- a challenge that belongs to no clan goes on working exactly as it did.
--
-- Run once, against each deployment:
--   wrangler d1 execute botw --remote --file migrations/002-clans.sql

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
