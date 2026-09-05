/**
 * The event rules, against the same real database the clan tests use.
 *
 *   node --test backend/test/events.test.mjs
 */

import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { DatabaseSync } from 'node:sqlite';
import { clanRoutes } from '../src/clans.js';
import { eventRoutes } from '../src/events.js';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const HOUR = 60 * 60 * 1000;

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

/** Either router answers, so a test can talk to the service without knowing which module owns a path. */
async function call(env, method, url, { body, token } = {})
{
	const headers = { 'Content-Type': 'application/json' };
	if (token)
	{
		headers['X-Clan-Token'] = token;
	}

	const request = new Request(`https://clan.suite${url}`, {
		method,
		headers,
		body: body === undefined ? undefined : JSON.stringify(body)
	});

	const at = new URL(request.url).pathname;
	const response = await eventRoutes(request, env, at, helpers)
		?? await clanRoutes(request, env, at, helpers);

	assert.ok(response, `no route for ${method} ${url}`);
	return { status: response.status, body: await response.json() };
}

async function clanWithStaff(env)
{
	const made = await call(env, 'POST', '/v1/clans', { body: { name: 'OCE Plankers', rsn: 'Owner' } });
	const clan = { code: made.body.clan.code, token: made.body.token };

	// Somebody who runs events but does not run the clan, which is the interesting rank here.
	await call(env, 'POST', `/v1/clans/${clan.code}/applications`, { body: { rsn: 'Organiser' } });
	await call(env, 'POST', `/v1/clans/${clan.code}/applications/Organiser`,
		{ body: { decision: 'accept' }, token: clan.token });
	await call(env, 'PATCH', `/v1/clans/${clan.code}/members/Organiser`,
		{ body: { role: 'admin' }, token: clan.token });

	// And an ordinary member.
	await call(env, 'POST', `/v1/clans/${clan.code}/applications`, { body: { rsn: 'Regular' } });
	await call(env, 'POST', `/v1/clans/${clan.code}/applications/Regular`,
		{ body: { decision: 'accept' }, token: clan.token });

	const token = (rsn) => env.DB.raw
		.prepare('SELECT token FROM clan_members WHERE clan_code = ? AND rsn = ?')
		.get(clan.code, rsn).token;

	return { ...clan, admin: token('Organiser'), member: token('Regular') };
}

function raidNight(overrides = {})
{
	const start = Date.now() + HOUR;
	return {
		name: 'TOA Raid Night',
		category: 'raids',
		template: 'raid_night',
		startsAt: start,
		endsAt: start + 4 * HOUR,
		timezone: 'Australia/Sydney',
		config: { track: ['completions', 'deaths', 'uniques'], points: [{ when: 'completion', points: 10 }] },
		leaderboard: 'points',
		...overrides
	};
}

test('whoever runs the events can create one', async () =>
{
	const env = { DB: database() };
	const clan = await clanWithStaff(env);

	const made = await call(env, 'POST', `/v1/clans/${clan.code}/events`,
		{ body: raidNight(), token: clan.admin });

	assert.equal(made.status, 201);
	assert.equal(made.body.event.name, 'TOA Raid Night');
	assert.equal(made.body.event.category, 'raids');
	assert.equal(made.body.event.template, 'raid_night');
	assert.equal(made.body.event.status, 'draft');
	assert.equal(made.body.event.createdBy, 'Organiser');
	assert.deepEqual(made.body.event.config.track, ['completions', 'deaths', 'uniques']);
});

test('a member cannot create one', async () =>
{
	const env = { DB: database() };
	const clan = await clanWithStaff(env);

	assert.equal((await call(env, 'POST', `/v1/clans/${clan.code}/events`,
		{ body: raidNight(), token: clan.member })).status, 403);

	assert.equal((await call(env, 'POST', `/v1/clans/${clan.code}/events`,
		{ body: raidNight() })).status, 403);
});

test('an event needs a name, a category and a sane schedule', async () =>
{
	const env = { DB: database() };
	const clan = await clanWithStaff(env);
	const bad = (body) => call(env, 'POST', `/v1/clans/${clan.code}/events`, { body, token: clan.admin });

	assert.equal((await bad(raidNight({ name: '' }))).status, 400);
	assert.equal((await bad(raidNight({ name: 'x'.repeat(61) }))).status, 400);
	assert.equal((await bad(raidNight({ category: 'dancing' }))).status, 400);
	assert.equal((await bad(raidNight({ startsAt: 'soon' }))).status, 400);

	// Ends before it starts, which is the one people actually type.
	const start = Date.now();
	assert.equal((await bad(raidNight({ startsAt: start, endsAt: start - HOUR }))).status, 400);
	assert.equal((await bad(raidNight({ startsAt: start, endsAt: start }))).status, 400);
});

test('drafts are the staff\'s business until they are published', async () =>
{
	const env = { DB: database() };
	const clan = await clanWithStaff(env);

	const made = await call(env, 'POST', `/v1/clans/${clan.code}/events`,
		{ body: raidNight(), token: clan.admin });
	const code = made.body.event.code;

	// The clan does not see it in the calendar, and cannot read it by code either.
	assert.equal((await call(env, 'GET', `/v1/clans/${clan.code}/events`, { token: clan.member }))
		.body.events.length, 0);
	assert.equal((await call(env, 'GET', `/v1/events/${code}`, { token: clan.member })).status, 404);

	// The people running it do.
	assert.equal((await call(env, 'GET', `/v1/clans/${clan.code}/events`, { token: clan.admin }))
		.body.events.length, 1);

	const published = await call(env, 'PATCH', `/v1/events/${code}`,
		{ body: { status: 'published' }, token: clan.admin });
	assert.equal(published.body.event.status, 'published');

	assert.equal((await call(env, 'GET', `/v1/clans/${clan.code}/events`, { token: clan.member }))
		.body.events.length, 1);

	// And a published event can be read by anyone holding the code, which is how one is shared.
	assert.equal((await call(env, 'GET', `/v1/events/${code}`)).status, 200);
});

test('editing keeps what was not sent', async () =>
{
	const env = { DB: database() };
	const clan = await clanWithStaff(env);
	const made = await call(env, 'POST', `/v1/clans/${clan.code}/events`,
		{ body: raidNight(), token: clan.admin });

	const renamed = await call(env, 'PATCH', `/v1/events/${made.body.event.code}`,
		{ body: { name: 'TOA Raid Night (300 invo)' }, token: clan.admin });

	assert.equal(renamed.status, 200);
	assert.equal(renamed.body.event.name, 'TOA Raid Night (300 invo)');
	assert.equal(renamed.body.event.category, 'raids');
	assert.deepEqual(renamed.body.event.config.track, ['completions', 'deaths', 'uniques']);
	assert.equal(renamed.body.event.startsAt, made.body.event.startsAt);
});

test('an edit cannot make an event nonsense', async () =>
{
	const env = { DB: database() };
	const clan = await clanWithStaff(env);
	const made = await call(env, 'POST', `/v1/clans/${clan.code}/events`,
		{ body: raidNight(), token: clan.admin });
	const code = made.body.event.code;

	assert.equal((await call(env, 'PATCH', `/v1/events/${code}`,
		{ body: { endsAt: made.body.event.startsAt - HOUR }, token: clan.admin })).status, 400);
	assert.equal((await call(env, 'PATCH', `/v1/events/${code}`,
		{ body: { status: 'sometime' }, token: clan.admin })).status, 400);
	assert.equal((await call(env, 'PATCH', `/v1/events/${code}`,
		{ body: { name: 'Renamed' }, token: clan.member })).status, 403);
});

test('cancelling keeps the event; deleting is for mistakes', async () =>
{
	const env = { DB: database() };
	const clan = await clanWithStaff(env);
	const made = await call(env, 'POST', `/v1/clans/${clan.code}/events`,
		{ body: raidNight({ status: 'published' }), token: clan.admin });
	const code = made.body.event.code;

	const cancelled = await call(env, 'PATCH', `/v1/events/${code}`,
		{ body: { status: 'cancelled' }, token: clan.admin });
	assert.equal(cancelled.body.event.status, 'cancelled');

	// Still in the calendar, which is the point of cancelling rather than deleting.
	assert.equal((await call(env, 'GET', `/v1/clans/${clan.code}/events`, { token: clan.member }))
		.body.events.length, 1);

	assert.equal((await call(env, 'DELETE', `/v1/events/${code}`, { token: clan.member })).status, 403);
	assert.equal((await call(env, 'DELETE', `/v1/events/${code}`, { token: clan.admin })).status, 200);
	assert.equal((await call(env, 'GET', `/v1/clans/${clan.code}/events`, { token: clan.admin }))
		.body.events.length, 0);
});

test('one clan cannot read or edit another clan\'s events', async () =>
{
	const env = { DB: database() };
	const ours = await clanWithStaff(env);
	const theirs = await call(env, 'POST', '/v1/clans', { body: { name: 'Other Clan', rsn: 'Stranger' } });

	const made = await call(env, 'POST', `/v1/clans/${ours.code}/events`,
		{ body: raidNight(), token: ours.admin });

	assert.equal((await call(env, 'PATCH', `/v1/events/${made.body.event.code}`,
		{ body: { name: 'Hijacked' }, token: theirs.body.token })).status, 403);

	assert.equal((await call(env, 'GET', `/v1/clans/${ours.code}/events`,
		{ token: theirs.body.token })).status, 403);
});

test('the calendar comes back newest first', async () =>
{
	const env = { DB: database() };
	const clan = await clanWithStaff(env);
	const start = Date.now();

	for (const [name, at] of [['Monday', start], ['Wednesday', start + 48 * HOUR], ['Tuesday', start + 24 * HOUR]])
	{
		await call(env, 'POST', `/v1/clans/${clan.code}/events`, {
			body: raidNight({ name, startsAt: at, endsAt: at + HOUR, status: 'published' }),
			token: clan.admin
		});
	}

	const listed = await call(env, 'GET', `/v1/clans/${clan.code}/events`, { token: clan.member });
	assert.deepEqual(listed.body.events.map((e) => e.name), ['Wednesday', 'Tuesday', 'Monday']);
});

test('an event that does not exist says so', async () =>
{
	const env = { DB: database() };
	assert.equal((await call(env, 'GET', '/v1/events/ZZZZZZ')).status, 404);
	assert.equal((await call(env, 'PATCH', '/v1/events/ZZZZZZ', { body: { name: 'x' } })).status, 404);
	assert.equal((await call(env, 'DELETE', '/v1/events/ZZZZZZ')).status, 404);
});
