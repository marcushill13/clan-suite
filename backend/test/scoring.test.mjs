/**
 * What things are worth.
 *
 * The rules engine on its own first, because it is pure and every edge in it is a rule a clan might
 * plausibly write. Then the whole path: a plugin reporting what it saw, and a leaderboard coming out
 * the other end.
 *
 *   node --test backend/test/scoring.test.mjs
 */

import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { DatabaseSync } from 'node:sqlite';
import { clanRoutes } from '../src/clans.js';
import { eventRoutes, scoreOf } from '../src/events.js';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const HOUR = 60 * 60 * 1000;

function totals(...rows)
{
	return rows.map(([metric, subject, amount, times]) => ({ metric, subject, amount, times }));
}

test('a flat rule pays for every one of the thing', () =>
{
	assert.equal(scoreOf(totals(['completion', null, 3, 3]), [{ metric: 'completion', points: 10 }]), 30);

	// Deaths cost, which is a negative rule and nothing more special than that.
	assert.equal(scoreOf(totals(['death', null, 5, 5]), [{ metric: 'death', points: -5 }]), -25);
});

test('a threshold pays on the total, not on each report', () =>
{
	const rule = [{ metric: 'kc', per: 10, points: 1 }];

	// The bug this exists to prevent: three reports of four kills is twelve kills, so one point —
	// scoring each report on its own would find nought three times over.
	assert.equal(scoreOf(totals(['kc', 'Vorkath', 12, 3]), rule), 1);
	assert.equal(scoreOf(totals(['kc', 'Vorkath', 9, 2]), rule), 0);
	assert.equal(scoreOf(totals(['kc', 'Vorkath', 40, 7]), rule), 4);
});

test('a rule can name what it is about, or take everything of that kind', () =>
{
	const drops = totals(['drop', 'Vorki', 1, 1], ['drop', 'Draconic visage', 2, 2]);

	assert.equal(scoreOf(drops, [{ metric: 'drop', subject: 'Vorki', points: 500 }]), 500);
	assert.equal(scoreOf(drops, [{ metric: 'drop', subject: 'vorki', points: 500 }]), 500,
		'a rule written in lower case still matches');
	assert.equal(scoreOf(drops, [{ metric: 'drop', points: 10 }]), 30,
		'no subject means every drop of any kind');
});

test('experience is a threshold like any other', () =>
{
	assert.equal(scoreOf(totals(['xp', 'Runecraft', 2_450_000, 40]),
		[{ metric: 'xp', per: 1_000_000, points: 5 }]), 10);
});

test('rules that match nothing are worth nothing, and nonsense is ignored', () =>
{
	assert.equal(scoreOf(totals(['kc', 'Zulrah', 30, 3]), [{ metric: 'drop', points: 100 }]), 0);
	assert.equal(scoreOf(totals(['kc', 'Zulrah', 30, 3]), [{ points: 100 }]), 0);
	assert.equal(scoreOf(totals(['kc', 'Zulrah', 30, 3]), []), 0);
	assert.equal(scoreOf([], [{ metric: 'kc', per: 10, points: 1 }]), 0);
});

test('every rule adds up', () =>
{
	const raid = totals(
		['completion', null, 6, 6],
		['death', null, 2, 2],
		['drop', "Tumeken's shadow", 1, 1]);

	assert.equal(scoreOf(raid, [
		{ metric: 'completion', points: 10 },
		{ metric: 'death', points: -5 },
		{ metric: 'drop', subject: "Tumeken's shadow", points: 100 }
	]), 60 - 10 + 100);
});

// ---- the whole path ----

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
	const response = await eventRoutes(request, env, at, helpers)
		?? await clanRoutes(request, env, at, helpers);

	assert.ok(response, `no route for ${method} ${url}`);
	return { status: response.status, body: await response.json() };
}

/** A clan, a published event scored on kills, and a member with a token to report under. */
async function running(env, config)
{
	const made = await call(env, 'POST', '/v1/clans', { body: { name: 'OCE Plankers', rsn: 'Owner' } });
	const clan = { code: made.body.clan.code, token: made.body.token };

	await call(env, 'POST', `/v1/clans/${clan.code}/applications`, { body: { rsn: 'Player' } });
	await call(env, 'POST', `/v1/clans/${clan.code}/applications/Player`,
		{ body: { decision: 'accept' }, token: clan.token });

	const start = Date.now() - HOUR;
	const event = await call(env, 'POST', `/v1/clans/${clan.code}/events`, {
		body: {
			name: 'Barrows Bounty', category: 'pvm', template: 'boss_mass',
			startsAt: start, endsAt: start + 4 * HOUR, status: 'published', config
		},
		token: clan.token
	});

	return {
		clan,
		code: event.body.event.code,
		player: env.DB.raw.prepare('SELECT token FROM clan_members WHERE clan_code = ? AND rsn = ?')
			.get(clan.code, 'Player').token
	};
}

function seen(metric, subject, amount, occurredAt)
{
	return { id: crypto.randomUUID(), metric, subject, amount, occurredAt };
}

test('what the plugin reports becomes a score', async () =>
{
	const env = { DB: database() };
	const at = await running(env, {
		points: [
			{ metric: 'kc', per: 10, points: 1 },
			{ metric: 'drop', subject: 'Vorki', points: 500 }
		]
	});

	const now = Date.now();
	const sent = await call(env, 'POST', `/v1/events/${at.code}/observations`, {
		body: {
			observations: [
				seen('kc', 'Vorkath', 4, now), seen('kc', 'Vorkath', 4, now), seen('kc', 'Vorkath', 4, now),
				seen('drop', 'Vorki', 1, now)
			]
		},
		token: at.player
	});

	assert.equal(sent.status, 200);
	assert.equal(sent.body.taken, 4);
	assert.equal(sent.body.points, 501, 'twelve kills is one point, and the pet is five hundred');

	const board = await call(env, 'GET', `/v1/events/${at.code}/participants`, { token: at.player });
	assert.equal(board.body.participants[0].rsn, 'Player');
	assert.equal(board.body.participants[0].points, 501);
});

test('the same report twice is the same score', async () =>
{
	const env = { DB: database() };
	const at = await running(env, { points: [{ metric: 'kc', points: 2 }] });

	const observation = seen('kc', 'Vorkath', 5, Date.now());
	const body = { observations: [observation] };

	await call(env, 'POST', `/v1/events/${at.code}/observations`, { body, token: at.player });
	const again = await call(env, 'POST', `/v1/events/${at.code}/observations`, { body, token: at.player });

	assert.equal(again.body.points, 10, 'a resend after a timeout must not count twice');
});

test('nothing outside the event counts, however honestly it is reported', async () =>
{
	const env = { DB: database() };
	const at = await running(env, { points: [{ metric: 'kc', points: 1 }] });
	const event = env.DB.raw.prepare('SELECT starts_at, ends_at FROM clan_events WHERE code = ?')
		.get(at.code);

	const sent = await call(env, 'POST', `/v1/events/${at.code}/observations`, {
		body: {
			observations: [
				seen('kc', 'Vorkath', 10, event.starts_at - HOUR),
				seen('kc', 'Vorkath', 10, event.ends_at + HOUR),
				seen('kc', 'Vorkath', 3, event.starts_at + 60_000)
			]
		},
		token: at.player
	});

	assert.equal(sent.body.taken, 1);
	assert.equal(sent.body.points, 3);
});

test('correcting the rules corrects what everybody already has', async () =>
{
	const env = { DB: database() };
	const at = await running(env, { points: [{ metric: 'kc', per: 10, points: 1 }] });

	await call(env, 'POST', `/v1/events/${at.code}/observations`, {
		body: { observations: [seen('kc', 'Vorkath', 30, Date.now())] },
		token: at.player
	});

	assert.equal((await call(env, 'GET', `/v1/events/${at.code}/participants`, { token: at.player }))
		.body.participants[0].points, 3);

	// Somebody always sets a number wrong, and always notices halfway through.
	await call(env, 'PATCH', `/v1/events/${at.code}`, {
		body: { config: { points: [{ metric: 'kc', per: 10, points: 5 }] } },
		token: at.clan.token
	});

	assert.equal((await call(env, 'GET', `/v1/events/${at.code}/participants`, { token: at.player }))
		.body.participants[0].points, 15);
});

test('a correction by hand survives the next recount', async () =>
{
	const env = { DB: database() };
	const at = await running(env, { points: [{ metric: 'kc', points: 1 }] });

	await call(env, 'PATCH', `/v1/events/${at.code}/participants/Player`,
		{ body: { adjustment: 50 }, token: at.clan.token });

	await call(env, 'POST', `/v1/events/${at.code}/observations`, {
		body: { observations: [seen('kc', 'Vorkath', 7, Date.now())] },
		token: at.player
	});

	const [row] = (await call(env, 'GET', `/v1/events/${at.code}/participants`, { token: at.player }))
		.body.participants;

	assert.equal(row.points, 57, 'the counted seven plus the fifty somebody was given');
	assert.equal(row.adjustment, 50);
});

test('only the clan\'s own members can report to its events', async () =>
{
	const env = { DB: database() };
	const at = await running(env, { points: [{ metric: 'kc', points: 1 }] });
	const stranger = await call(env, 'POST', '/v1/clans', { body: { name: 'Other', rsn: 'Stranger' } });

	assert.equal((await call(env, 'POST', `/v1/events/${at.code}/observations`, {
		body: { observations: [seen('kc', 'Vorkath', 10, Date.now())] },
		token: stranger.body.token
	})).status, 403);

	assert.equal((await call(env, 'POST', `/v1/events/${at.code}/observations`, {
		body: { observations: [seen('kc', 'Vorkath', 10, Date.now())] }
	})).status, 403);
});

test('a client cannot flood the table', async () =>
{
	const env = { DB: database() };
	const at = await running(env, { points: [{ metric: 'kc', points: 1 }] });

	const many = Array.from({ length: 51 }, () => seen('kc', 'Vorkath', 1, Date.now()));
	assert.equal((await call(env, 'POST', `/v1/events/${at.code}/observations`,
		{ body: { observations: many }, token: at.player })).status, 400);
});
