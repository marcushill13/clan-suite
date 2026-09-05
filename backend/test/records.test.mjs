/**
 * What a clan has done, and who did it best.
 *
 *   node --test backend/test/records.test.mjs
 */

import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { DatabaseSync } from 'node:sqlite';
import { clanRoutes } from '../src/clans.js';
import { eventRoutes } from '../src/events.js';
import { recordRoutes, streakOf } from '../src/records.js';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;

function database()
{
	const db = new DatabaseSync(':memory:');
	db.exec(fs.readFileSync(path.join(HERE, '..', 'schema.sql'), 'utf8'));

	const statement = (sql) =>
	{
		let args = [];
		return {
			bind(...values) { args = values; return this; },
			async first() { return db.prepare(sql).get(...args) ?? null; },
			async all() { return { results: db.prepare(sql).all(...args) }; },
			async run() { const r = db.prepare(sql).run(...args); return { meta: { changes: r.changes } }; }
		};
	};

	return {
		raw: db,
		prepare: statement,
		async batch(statements) { for (const s of statements) { await s.run(); } }
	};
}

const helpers = {
	json: (body, status = 200) => new Response(JSON.stringify(body), {
		status, headers: { 'Content-Type': 'application/json' }
	}),
	readJson: async (request) => { try { return await request.json(); } catch { return null; } },
	randomToken: () => crypto.randomUUID().replace(/-/g, ''),
	randomCode: () => Array.from({ length: 6 },
		() => 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'[Math.floor(Math.random() * 32)]).join('')
};

async function call(env, method, url, { body, token } = {})
{
	const headers = { 'Content-Type': 'application/json' };
	if (token)
	{
		headers['X-Clan-Token'] = token;
	}

	const request = new Request(`https://clan.suite${url}`, {
		method, headers, body: body === undefined ? undefined : JSON.stringify(body)
	});

	const at = new URL(request.url).pathname;
	const response = await recordRoutes(request, env, at, helpers)
		?? await eventRoutes(request, env, at, helpers)
		?? await clanRoutes(request, env, at, helpers);

	assert.ok(response, `no route for ${method} ${url}`);
	return { status: response.status, body: await response.json() };
}

/** A clan with an owner and however many members are wanted, all holding tokens. */
async function clanOf(env, ...people)
{
	const made = await call(env, 'POST', '/v1/clans', { body: { name: 'OCE Plankers', rsn: 'Owner' } });
	const clan = { code: made.body.clan.code, token: made.body.token, tokens: { Owner: made.body.token } };

	for (const rsn of people)
	{
		await call(env, 'POST', `/v1/clans/${clan.code}/applications`, { body: { rsn } });
		await call(env, 'POST', `/v1/clans/${clan.code}/applications/${rsn}`,
			{ body: { decision: 'accept' }, token: clan.token });

		clan.tokens[rsn] = env.DB.raw
			.prepare('SELECT token FROM clan_members WHERE clan_code = ? AND rsn = ?')
			.get(clan.code, rsn).token;
	}

	return clan;
}

/**
 * An event that has already finished, with what everybody reported during it.
 *
 * Written straight into the database because the service refuses reports for an event that is over,
 * which is right, and makes it impossible to build a year of history through the front door.
 */
function finishedEvent(env, clan, { name, endsAt, config = {}, seen = [], scores = {} })
{
	const code = helpers.randomCode();
	const startsAt = endsAt - 4 * HOUR;

	env.DB.raw.prepare(
		`INSERT INTO clan_events
			(code, clan_code, name, category, template, starts_at, ends_at, timezone, config,
			 leaderboard, status, created_by, created_at)
		 VALUES (?, ?, ?, 'pvm', 'boss_mass', ?, ?, 'UTC', ?, 'points', 'published', 'Owner', ?)`)
		.run(code, clan.code, name, startsAt, endsAt, JSON.stringify(config), startsAt);

	for (const [rsn, points] of Object.entries(scores))
	{
		env.DB.raw.prepare(
			`INSERT INTO clan_event_participants (event_code, rsn, joined_at, attended, points, adjustment)
			 VALUES (?, ?, ?, 1, ?, 0)`)
			.run(code, rsn, startsAt, points);
	}

	for (const [rsn, metric, subject, amount] of seen)
	{
		env.DB.raw.prepare(
			`INSERT INTO event_observations
				(id, event_code, rsn, metric, subject, amount, occurred_at, recorded_at)
			 VALUES (?, ?, ?, ?, ?, ?, ?, ?)`)
			.run(crypto.randomUUID(), code, rsn, metric, subject, amount, startsAt + HOUR, startsAt + HOUR);
	}

	return code;
}

test('a streak counts the events somebody did not miss', () =>
{
	// Newest first, which is the order the records read them in.
	const held = ['e5', 'e4', 'e3', 'e2', 'e1'];

	assert.deepEqual(streakOf(held, ['e5', 'e4', 'e3']), { current: 3, longest: 3 });

	// Missed the most recent one: the run is over, but it was still their longest.
	assert.deepEqual(streakOf(held, ['e4', 'e3', 'e2']), { current: 0, longest: 3 });

	// A gap in the middle.
	assert.deepEqual(streakOf(held, ['e5', 'e3', 'e2', 'e1']), { current: 1, longest: 3 });

	assert.deepEqual(streakOf(held, []), { current: 0, longest: 0 });
	assert.deepEqual(streakOf([], ['e1']), { current: 0, longest: 0 });
});

test('the records name who, how much, and what for', async () =>
{
	const env = { DB: database() };
	const clan = await clanOf(env, 'Kaz', 'Willow');
	const now = Date.now();

	finishedEvent(env, clan, {
		name: 'TOA Raid Night',
		endsAt: now - DAY,
		scores: { Kaz: 140, Willow: 60 },
		seen: [
			['Kaz', 'loot', "Tumeken's shadow", 1_200_000_000],
			['Willow', 'loot', 'Coins', 250_000],
			['Kaz', 'kc', 'Tombs of Amascut', 6]
		]
	});

	const records = (await call(env, 'GET', `/v1/clans/${clan.code}/records`,
		{ token: clan.tokens.Kaz })).body.records;

	const biggest = records.find((row) => row.title === 'Biggest drop');
	assert.equal(biggest.rsn, 'Kaz');
	assert.equal(biggest.amount, 1_200_000_000);
	assert.equal(biggest.detail, "Tumeken's shadow");
	assert.equal(biggest.event, 'TOA Raid Night');

	const best = records.find((row) => row.title === 'Highest score in an event');
	assert.equal(best.rsn, 'Kaz');
	assert.equal(best.amount, 140);
});

test('records only count events that have finished', async () =>
{
	const env = { DB: database() };
	const clan = await clanOf(env, 'Kaz');
	const now = Date.now();

	// One finished, one still running with a bigger number in it.
	finishedEvent(env, clan, { name: 'Last week', endsAt: now - DAY, scores: { Kaz: 50 } });
	finishedEvent(env, clan, { name: 'On now', endsAt: now + DAY, scores: { Kaz: 900 } });

	const records = (await call(env, 'GET', `/v1/clans/${clan.code}/records`,
		{ token: clan.tokens.Kaz })).body.records;

	const best = records.find((row) => row.title === 'Highest score in an event');
	assert.equal(best.amount, 50, 'an event still being played is not a record yet');
});

test('winning is the top of a finished board, and a draw is a win for both', async () =>
{
	const env = { DB: database() };
	const clan = await clanOf(env, 'Kaz', 'Willow');
	const now = Date.now();

	finishedEvent(env, clan, { name: 'One', endsAt: now - 3 * DAY, scores: { Kaz: 100, Willow: 50 } });
	finishedEvent(env, clan, { name: 'Two', endsAt: now - 2 * DAY, scores: { Kaz: 40, Willow: 40 } });
	finishedEvent(env, clan, { name: 'Three', endsAt: now - DAY, scores: { Kaz: 0, Willow: 0 } });

	const stats = (await call(env, 'GET', `/v1/clans/${clan.code}/statistics`,
		{ token: clan.tokens.Kaz })).body;

	const kaz = stats.members.find((row) => row.rsn === 'Kaz');
	const willow = stats.members.find((row) => row.rsn === 'Willow');

	assert.equal(kaz.won, 2, 'one outright and one drawn');
	assert.equal(willow.won, 1, 'the drawn one');
	assert.equal(kaz.attended, 3);
});

test('the clan totals add up what everybody did', async () =>
{
	const env = { DB: database() };
	const clan = await clanOf(env, 'Kaz', 'Willow');
	const now = Date.now();

	finishedEvent(env, clan, {
		name: 'Wintertodt Mass',
		endsAt: now - DAY,
		scores: { Kaz: 30, Willow: 20 },
		seen: [
			['Kaz', 'xp', 'Firemaking', 1_500_000],
			['Willow', 'xp', 'Firemaking', 900_000],
			['Kaz', 'completion', 'Wintertodt', 12],
			['Willow', 'completion', 'Wintertodt', 8]
		]
	});

	const stats = (await call(env, 'GET', `/v1/clans/${clan.code}/statistics`,
		{ token: clan.tokens.Kaz })).body;

	assert.equal(stats.clan.eventsHeld, 1);
	assert.equal(stats.clan.attendances, 2);
	assert.equal(stats.clan.people, 2);
	assert.equal(stats.clan.points, 50);
	assert.equal(stats.clan.totals.xp, 2_400_000);
	assert.equal(stats.clan.totals.completion, 20);
});

test('one person\'s history, including the run they are on', async () =>
{
	const env = { DB: database() };
	const clan = await clanOf(env, 'Kaz', 'Willow');
	const now = Date.now();

	// Four events; Kaz missed the third one back.
	finishedEvent(env, clan, { name: 'One', endsAt: now - 4 * DAY, scores: { Kaz: 10, Willow: 5 } });
	finishedEvent(env, clan, { name: 'Two', endsAt: now - 3 * DAY, scores: { Willow: 5 } });
	finishedEvent(env, clan, { name: 'Three', endsAt: now - 2 * DAY, scores: { Kaz: 70, Willow: 5 } });
	finishedEvent(env, clan, {
		name: 'Four', endsAt: now - DAY, scores: { Kaz: 20, Willow: 5 },
		seen: [['Kaz', 'kc', 'Vorkath', 25]]
	});

	const kaz = (await call(env, 'GET', `/v1/clans/${clan.code}/statistics/Kaz`,
		{ token: clan.tokens.Kaz })).body;

	assert.equal(kaz.attended, 3);
	assert.equal(kaz.eventsHeld, 4);
	assert.equal(kaz.points, 100);
	assert.equal(kaz.best, 70);
	assert.equal(kaz.streak, 2, 'the two since the one they missed');
	assert.equal(kaz.longestStreak, 2);
	assert.equal(kaz.won, 3, 'ahead of Willow every time they turned up');
	assert.equal(kaz.totals.kc, 25);

	const willow = (await call(env, 'GET', `/v1/clans/${clan.code}/statistics/Willow`,
		{ token: clan.tokens.Willow })).body;

	assert.equal(willow.attended, 4);
	assert.equal(willow.streak, 4, 'never missed one');
	assert.equal(willow.won, 1, 'the one Kaz was not at');
});

test('none of it is anybody else\'s business', async () =>
{
	const env = { DB: database() };
	const clan = await clanOf(env, 'Kaz');
	const stranger = await call(env, 'POST', '/v1/clans', { body: { name: 'Other', rsn: 'Stranger' } });

	for (const url of ['records', 'statistics', 'statistics/Kaz'])
	{
		assert.equal((await call(env, 'GET', `/v1/clans/${clan.code}/${url}`)).status, 403);
		assert.equal((await call(env, 'GET', `/v1/clans/${clan.code}/${url}`,
			{ token: stranger.body.token })).status, 403);
	}
});

test('a clan that has done nothing yet says so rather than breaking', async () =>
{
	const env = { DB: database() };
	const clan = await clanOf(env);

	const records = await call(env, 'GET', `/v1/clans/${clan.code}/records`, { token: clan.token });
	assert.equal(records.status, 200);
	assert.deepEqual(records.body.records, []);

	const stats = await call(env, 'GET', `/v1/clans/${clan.code}/statistics`, { token: clan.token });
	assert.equal(stats.body.clan.eventsHeld, 0);
	assert.deepEqual(stats.body.members, []);

	const player = await call(env, 'GET', `/v1/clans/${clan.code}/statistics/Owner`, { token: clan.token });
	assert.equal(player.body.attended, 0);
	assert.equal(player.body.streak, 0);
});
