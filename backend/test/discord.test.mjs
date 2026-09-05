/**
 * What gets said to Discord, and what does not.
 *
 * Nothing here talks to Discord: fetch is replaced, so every test can read exactly what would have
 * been posted. That is the interesting half anyway — the failure mode with webhooks is not that the
 * request fails, it is that the wrong thing, or the same thing twice, arrives in somebody's channel.
 *
 *   node --test backend/test/discord.test.mjs
 */

import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { DatabaseSync } from 'node:sqlite';
import { clanRoutes } from '../src/clans.js';
import { announceDue, eventRoutes } from '../src/events.js';
import { isWebhook } from '../src/discord.js';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const HOUR = 60 * 60 * 1000;
const WEBHOOK = 'https://discord.com/api/webhooks/1234/abcd';

/** Everything that would have been sent, in order. */
const posted = [];

globalThis.fetch = async (url, options) =>
{
	posted.push({ url, body: JSON.parse(options.body) });
	return new Response('{}', { status: 204 });
};

function said()
{
	const messages = posted.map((message) => message.body.embeds[0]);
	posted.length = 0;
	return messages;
}

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

/** A clan with a webhook set, a member, and a draft event ready to publish. */
async function clanWithDiscord(env, config = {})
{
	const made = await call(env, 'POST', '/v1/clans', { body: { name: 'OCE Plankers', rsn: 'Owner' } });
	const clan = { code: made.body.clan.code, token: made.body.token };

	await call(env, 'PATCH', `/v1/clans/${clan.code}`,
		{ body: { discordWebhook: WEBHOOK }, token: clan.token });

	await call(env, 'POST', `/v1/clans/${clan.code}/applications`, { body: { rsn: 'Player' } });
	await call(env, 'POST', `/v1/clans/${clan.code}/applications/Player`,
		{ body: { decision: 'accept' }, token: clan.token });

	const start = Date.now() + HOUR;
	const event = await call(env, 'POST', `/v1/clans/${clan.code}/events`, {
		body: {
			name: 'TOA Raid Night', category: 'raids', template: 'raid_night',
			startsAt: start, endsAt: start + 4 * HOUR, config
		},
		token: clan.token
	});

	said();

	return {
		clan,
		code: event.body.event.code,
		player: env.DB.raw.prepare('SELECT token FROM clan_members WHERE clan_code = ? AND rsn = ?')
			.get(clan.code, 'Player').token
	};
}

test('only a Discord webhook is accepted', () =>
{
	assert.equal(isWebhook(WEBHOOK), true);
	assert.equal(isWebhook('https://ptb.discord.com/api/webhooks/1/x'), true);

	// The reason this check exists: without it, the settings box is a way of having the service make
	// requests to anywhere at all, on somebody else's behalf.
	assert.equal(isWebhook('https://example.com/webhook'), false);
	assert.equal(isWebhook('http://discord.com/api/webhooks/1/x'), false);
	assert.equal(isWebhook('https://discord.com.evil.example/api/webhooks/1/x'), false);
	assert.equal(isWebhook(''), false);
	assert.equal(isWebhook(null), false);
});

test('a bad address is refused rather than stored', async () =>
{
	const env = { DB: database() };
	const made = await call(env, 'POST', '/v1/clans', { body: { name: 'Clan', rsn: 'Owner' } });

	const refused = await call(env, 'PATCH', `/v1/clans/${made.body.clan.code}`,
		{ body: { discordWebhook: 'https://example.com/hook' }, token: made.body.token });

	assert.equal(refused.status, 400);
	assert.equal(env.DB.raw.prepare('SELECT webhook_url FROM clans WHERE code = ?')
		.get(made.body.clan.code).webhook_url, null);
});

test('the webhook address never comes back out', async () =>
{
	const env = { DB: database() };
	const at = await clanWithDiscord(env);

	const read = await call(env, 'GET', `/v1/clans/${at.clan.code}`, { token: at.clan.token });
	assert.equal(read.body.clan.discord, true, 'the settings screen needs to know there is one');
	assert.equal(JSON.stringify(read.body).includes('abcd'), false, 'but never the address itself');

	const listed = await call(env, 'GET', '/v1/clans');
	assert.equal(JSON.stringify(listed.body).includes('abcd'), false);
});

test('publishing announces, and only once', async () =>
{
	const env = { DB: database() };
	const at = await clanWithDiscord(env);

	await call(env, 'PATCH', `/v1/events/${at.code}`,
		{ body: { status: 'published' }, token: at.clan.token });

	const [announcement] = said();
	assert.match(announcement.title, /TOA Raid Night/);
	assert.match(announcement.description, /OCE Plankers/);

	// Editing a published event is not a second announcement.
	await call(env, 'PATCH', `/v1/events/${at.code}`,
		{ body: { name: 'TOA Raid Night (300 invo)' }, token: at.clan.token });
	await call(env, 'PATCH', `/v1/events/${at.code}`,
		{ body: { status: 'published' }, token: at.clan.token });

	assert.deepEqual(said(), []);
});

test('a draft says nothing at all', async () =>
{
	const env = { DB: database() };
	const at = await clanWithDiscord(env);

	await call(env, 'PATCH', `/v1/events/${at.code}`,
		{ body: { name: 'Still deciding' }, token: at.clan.token });

	assert.deepEqual(said(), []);
});

test('a clan with no webhook is silent', async () =>
{
	const env = { DB: database() };
	const at = await clanWithDiscord(env);

	await call(env, 'PATCH', `/v1/clans/${at.clan.code}`,
		{ body: { discordWebhook: '' }, token: at.clan.token });
	await call(env, 'PATCH', `/v1/events/${at.code}`,
		{ body: { status: 'published' }, token: at.clan.token });

	assert.deepEqual(said(), []);
});

test('the drops the clan named are announced, and ordinary loot is not', async () =>
{
	const env = { DB: database() };
	const at = await clanWithDiscord(env, {
		points: [
			{ metric: 'drop', subject: "Tumeken's shadow", points: 500 },
			{ metric: 'drop', points: 1 }
		]
	});

	const now = Date.now();
	env.DB.raw.prepare('UPDATE clan_events SET starts_at = ?, status = ? WHERE code = ?')
		.run(now - HOUR, 'published', at.code);
	said();

	await call(env, 'POST', `/v1/events/${at.code}/observations`, {
		body: {
			observations: [
				{ id: crypto.randomUUID(), metric: 'drop', subject: 'Coins', amount: 200000, occurredAt: now },
				{ id: crypto.randomUUID(), metric: 'drop', subject: "Tumeken's shadow", amount: 1, occurredAt: now }
			]
		},
		token: at.player
	});

	const messages = said();
	assert.equal(messages.length, 1, 'coins are not news');
	assert.match(messages[0].description, /Player/);
	assert.match(messages[0].description, /Tumeken's shadow/);
});

test('an event about to start is announced once, and its results once', async () =>
{
	const env = { DB: database() };
	const at = await clanWithDiscord(env);

	await call(env, 'PATCH', `/v1/events/${at.code}`,
		{ body: { status: 'published' }, token: at.clan.token });
	said();

	// Nothing yet: it is an hour away.
	await announceDue(env);
	assert.deepEqual(said(), []);

	// Twenty minutes out.
	const start = env.DB.raw.prepare('SELECT starts_at FROM clan_events WHERE code = ?').get(at.code)
		.starts_at;
	await announceDue(env, start - 20 * 60 * 1000);

	const [soon] = said();
	assert.match(soon.description, /Starting in 20 minutes/);

	// The timer runs every few minutes; it must not say it again each time.
	await announceDue(env, start - 15 * 60 * 1000);
	assert.deepEqual(said(), []);
});

test('the results say who won', async () =>
{
	const env = { DB: database() };
	const at = await clanWithDiscord(env);

	await call(env, 'PATCH', `/v1/events/${at.code}`,
		{ body: { status: 'published' }, token: at.clan.token });
	said();

	for (const [rsn, points] of [['Player', 40], ['Owner', 90]])
	{
		await call(env, 'PATCH', `/v1/events/${at.code}/participants/${rsn}`,
			{ body: { adjustment: points }, token: at.clan.token });
	}

	const ends = env.DB.raw.prepare('SELECT ends_at FROM clan_events WHERE code = ?').get(at.code).ends_at;
	await announceDue(env, ends + 1000);

	const [results] = said();
	assert.match(results.title, /complete/);
	assert.match(results.description, /🥇 \*\*Owner\*\* — 90 pts/);
	assert.match(results.description, /🥈 \*\*Player\*\* — 40 pts/);
	assert.match(results.footer.text, /2 took part/);

	await announceDue(env, ends + 60 * 60 * 1000);
	assert.deepEqual(said(), [], 'results are announced once, not every five minutes for ever');
});

test('Discord being broken costs nobody a point', async () =>
{
	const env = { DB: database() };
	const at = await clanWithDiscord(env, { points: [{ metric: 'drop', subject: 'Vorki', points: 500 }] });

	const now = Date.now();
	env.DB.raw.prepare('UPDATE clan_events SET starts_at = ?, status = ? WHERE code = ?')
		.run(now - HOUR, 'published', at.code);

	const working = globalThis.fetch;
	globalThis.fetch = async () =>
	{
		throw new Error('Discord is down');
	};

	try
	{
		const sent = await call(env, 'POST', `/v1/events/${at.code}/observations`, {
			body: {
				observations: [
					{ id: crypto.randomUUID(), metric: 'drop', subject: 'Vorki', amount: 1, occurredAt: now }
				]
			},
			token: at.player
		});

		assert.equal(sent.status, 200);
		assert.equal(sent.body.points, 500, 'the drop still scored');
	}
	finally
	{
		globalThis.fetch = working;
		posted.length = 0;
	}
});
