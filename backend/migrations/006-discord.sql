-- Adds Discord announcements: where a clan's go, and what has already been said.
--
-- schema.sql is written for a database that does not exist yet; this is for one that already does.
-- All three columns default to nothing, so a clan that has not set a webhook carries on silently and
-- an event that predates this is treated as not yet announced — which is right, since it was not.
--
-- Run once, against each deployment:
--   wrangler d1 execute botw --remote --file migrations/006-discord.sql

ALTER TABLE clans ADD COLUMN webhook_url TEXT;
ALTER TABLE clan_events ADD COLUMN announced_start INTEGER NOT NULL DEFAULT 0;
ALTER TABLE clan_events ADD COLUMN announced_end INTEGER NOT NULL DEFAULT 0;
