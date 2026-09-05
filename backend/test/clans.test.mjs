/**
 * The clan rules, run against a real database.
 *
 * D1 is SQLite with a promise on the front, so the schema this exercises is the schema that ships:
 * schema.sql is read and executed as written, and the handlers are the ones the worker calls. A
 * mocked database would have proved that the code calls itself correctly and nothing else.
 *
 *   node --test backend/test
 */

import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { DatabaseSync } from 'node:sqlite';
import { clanRoutes, MEMBER_LIMIT } from '../src/clans.js';

const HERE = path.dirname(fileURLToPath(import.meta.url));

/** Enough of D1's shape for the handlers: prepare/bind/first/all/run, and batch. */
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

/** One call into the service. Returns the status and the parsed body, which is all any test wants. */
async function call(env, method, path, { body, token } = {})
{
	const headers = { 'Content-Type': 'application/json' };
	if (token)
	{
		headers['X-Clan-Token'] = token;
	}

	const request = new Request(`https://clan.suite${path}`, {
		method,
		headers,
		body: body === undefined ? undefined : JSON.stringify(body)
	});

	const response = await clanRoutes(request, env, new URL(request.url).pathname, helpers);
	assert.ok(response, `no route for ${method} ${path}`);
	return { status: response.status, body: await response.json() };
}

/** A clan with an owner, which is where every one of these tests starts. */
async function clanWithOwner(env, name = 'OCE Plankers')
{
	const made = await call(env, 'POST', '/v1/clans', { body: { name, rsn: 'Owner', tagline: 'Oceania' } });
	assert.equal(made.status, 201);
	return { code: made.body.clan.code, token: made.body.token };
}

/** Somebody applied to and accepted into the clan, then their own token found by the roster. */
async function memberOf(env, clan, rsn, staffToken)
{
	await call(env, 'POST', `/v1/clans/${clan.code}/applications`, { body: { rsn } });
	const decided = await call(env, 'POST', `/v1/clans/${clan.code}/applications/${rsn}`,
		{ body: { decision: 'accept' }, token: staffToken });
	assert.equal(decided.status, 200, JSON.stringify(decided.body));

	return env.DB.raw.prepare('SELECT token FROM clan_members WHERE clan_code = ? AND rsn = ?')
		.get(clan.code, rsn).token;
}

test('whoever creates a clan owns it', async () =>
{
	const env = { DB: database() };
	const made = await call(env, 'POST', '/v1/clans', { body: { name: 'OCE Plankers', rsn: 'Owner' } });

	assert.equal(made.status, 201);
	assert.equal(made.body.role, 'owner');
	assert.equal(made.body.clan.members, 1);
	assert.equal(made.body.clan.memberLimit, MEMBER_LIMIT);
	assert.ok(made.body.capabilities.includes('CLAN_SETTINGS'));
	assert.ok(made.body.token);
});

test('a clan needs a name', async () =>
{
	const env = { DB: database() };
	assert.equal((await call(env, 'POST', '/v1/clans', { body: { rsn: 'Owner' } })).status, 400);
	assert.equal((await call(env, 'POST', '/v1/clans', { body: { name: 'x'.repeat(41), rsn: 'Owner' } })).status, 400);
});

test('the hub lists clans that want to be seen, and not the ones that do not', async () =>
{
	const env = { DB: database() };
	const open = await clanWithOwner(env, 'Open Clan');
	const hidden = await clanWithOwner(env, 'Hidden Clan');

	await call(env, 'PATCH', `/v1/clans/${hidden.code}`, { body: { listed: false }, token: hidden.token });

	const listed = await call(env, 'GET', '/v1/clans');
	const names = listed.body.clans.map((clan) => clan.name);

	assert.deepEqual(names, ['Open Clan']);

	// Hidden is not gone, only unlisted: the code still finds it, which is how a private clan recruits.
	assert.equal((await call(env, 'GET', `/v1/clans/${hidden.code}`)).body.clan.name, 'Hidden Clan');
	assert.equal((await call(env, 'GET', '/v1/clans?q=open')).body.clans.length, 1);
	assert.equal((await call(env, 'GET', '/v1/clans?q=nothing')).body.clans.length, 0);
	void open;
});

test('the directory says how full a clan is', async () =>
{
	const env = { DB: database() };
	const clan = await clanWithOwner(env);
	await memberOf(env, clan, 'Second', clan.token);

	const [listed] = (await call(env, 'GET', '/v1/clans')).body.clans;
	assert.equal(listed.members, 2);
	assert.equal(listed.memberLimit, 500);
	assert.equal(listed.full, false);
});

test('applying, and being let in', async () =>
{
	const env = { DB: database() };
	const clan = await clanWithOwner(env);

	const applied = await call(env, 'POST', `/v1/clans/${clan.code}/applications`,
		{ body: { rsn: 'Hopeful', message: 'Been in the clan chat for months' } });
	assert.equal(applied.status, 201);

	const pending = await call(env, 'GET', `/v1/clans/${clan.code}/applications`, { token: clan.token });
	assert.equal(pending.body.applications.length, 1);
	assert.equal(pending.body.applications[0].rsn, 'Hopeful');

	const accepted = await call(env, 'POST', `/v1/clans/${clan.code}/applications/Hopeful`,
		{ body: { decision: 'accept' }, token: clan.token });
	assert.equal(accepted.status, 200);

	assert.equal((await call(env, 'GET', `/v1/clans/${clan.code}`)).body.clan.members, 2);
	assert.equal((await call(env, 'GET', `/v1/clans/${clan.code}/applications`, { token: clan.token }))
		.body.applications.length, 0);
});

test('turning somebody down is remembered', async () =>
{
	const env = { DB: database() };
	const clan = await clanWithOwner(env);

	await call(env, 'POST', `/v1/clans/${clan.code}/applications`, { body: { rsn: 'Hopeful' } });
	await call(env, 'POST', `/v1/clans/${clan.code}/applications/Hopeful`,
		{ body: { decision: 'deny' }, token: clan.token });

	assert.equal((await call(env, 'GET', `/v1/clans/${clan.code}/applications`, { token: clan.token }))
		.body.applications.length, 0);

	const denied = env.DB.raw.prepare(
		'SELECT status FROM clan_applications WHERE clan_code = ? AND rsn = ?').get(clan.code, 'Hopeful');
	assert.equal(denied.status, 'denied');

	// And they may try again, which puts them back in front of the staff rather than staying denied.
	await call(env, 'POST', `/v1/clans/${clan.code}/applications`, { body: { rsn: 'Hopeful' } });
	assert.equal((await call(env, 'GET', `/v1/clans/${clan.code}/applications`, { token: clan.token }))
		.body.applications.length, 1);
});

test('a clan that is closed or full takes nobody', async () =>
{
	const env = { DB: database() };
	const clan = await clanWithOwner(env);

	await call(env, 'PATCH', `/v1/clans/${clan.code}`,
		{ body: { applicationsOpen: false }, token: clan.token });
	assert.equal((await call(env, 'POST', `/v1/clans/${clan.code}/applications`,
		{ body: { rsn: 'Hopeful' } })).status, 403);

	await call(env, 'PATCH', `/v1/clans/${clan.code}`,
		{ body: { applicationsOpen: true }, token: clan.token });
	env.DB.raw.prepare('UPDATE clans SET member_limit = 1 WHERE code = ?').run(clan.code);

	assert.equal((await call(env, 'POST', `/v1/clans/${clan.code}/applications`,
		{ body: { rsn: 'Hopeful' } })).status, 409);
});

test('a full clan cannot accept the application it already had', async () =>
{
	const env = { DB: database() };
	const clan = await clanWithOwner(env);

	await call(env, 'POST', `/v1/clans/${clan.code}/applications`, { body: { rsn: 'Hopeful' } });
	env.DB.raw.prepare('UPDATE clans SET member_limit = 1 WHERE code = ?').run(clan.code);

	const accepted = await call(env, 'POST', `/v1/clans/${clan.code}/applications/Hopeful`,
		{ body: { decision: 'accept' }, token: clan.token });

	assert.equal(accepted.status, 409);
	assert.equal((await call(env, 'GET', `/v1/clans/${clan.code}`)).body.clan.members, 1);
});

test('members cannot see or decide applications', async () =>
{
	const env = { DB: database() };
	const clan = await clanWithOwner(env);
	const member = await memberOf(env, clan, 'Regular', clan.token);

	assert.equal((await call(env, 'GET', `/v1/clans/${clan.code}/applications`, { token: member })).status, 403);
	assert.equal((await call(env, 'GET', `/v1/clans/${clan.code}/applications`)).status, 403);
});

test('only the owner and their deputies hand out ranks, and only below their own', async () =>
{
	const env = { DB: database() };
	const clan = await clanWithOwner(env);
	const deputy = await memberOf(env, clan, 'Deputy', clan.token);
	const admin = await memberOf(env, clan, 'Admin', clan.token);
	await memberOf(env, clan, 'Regular', clan.token);

	assert.equal((await call(env, 'PATCH', `/v1/clans/${clan.code}/members/Deputy`,
		{ body: { role: 'deputy' }, token: clan.token })).status, 200);
	assert.equal((await call(env, 'PATCH', `/v1/clans/${clan.code}/members/Admin`,
		{ body: { role: 'admin' }, token: clan.token })).status, 200);

	// Running the clan's events and deciding who runs them are different jobs. An admin does the first
	// and has no say in the second, however far below them the rank being handed out is.
	assert.equal((await call(env, 'PATCH', `/v1/clans/${clan.code}/members/Regular`,
		{ body: { role: 'moderator' }, token: admin })).status, 403);

	// A deputy may, because ranks are the owner's business and a deputy stands in for the owner.
	assert.equal((await call(env, 'PATCH', `/v1/clans/${clan.code}/members/Regular`,
		{ body: { role: 'moderator' }, token: deputy })).status, 200);

	// But not to their own rank, which would let them make a colleague they could no longer overrule.
	assert.equal((await call(env, 'PATCH', `/v1/clans/${clan.code}/members/Regular`,
		{ body: { role: 'deputy' }, token: deputy })).status, 403);

	// And not to the owner's. Handing the clan over is a different thing, and is not built yet.
	assert.equal((await call(env, 'PATCH', `/v1/clans/${clan.code}/members/Admin`,
		{ body: { role: 'owner' }, token: clan.token })).status, 400);

	// Nobody reaches the owner from below.
	assert.equal((await call(env, 'PATCH', `/v1/clans/${clan.code}/members/Owner`,
		{ body: { role: 'member' }, token: deputy })).status, 403);
});

test('kicking needs rank, leaving does not, and the owner may do neither to themselves', async () =>
{
	const env = { DB: database() };
	const clan = await clanWithOwner(env);
	const admin = await memberOf(env, clan, 'Admin', clan.token);
	const regular = await memberOf(env, clan, 'Regular', clan.token);
	await call(env, 'PATCH', `/v1/clans/${clan.code}/members/Admin`,
		{ body: { role: 'admin' }, token: clan.token });

	// A member cannot remove staff.
	assert.equal((await call(env, 'DELETE', `/v1/clans/${clan.code}/members/Admin`, { token: regular })).status, 403);

	// But can walk out.
	assert.equal((await call(env, 'DELETE', `/v1/clans/${clan.code}/members/Regular`, { token: regular })).status, 200);
	assert.equal((await call(env, 'GET', `/v1/clans/${clan.code}`)).body.clan.members, 2);

	// The owner is stuck with it until ownership can be handed over.
	assert.equal((await call(env, 'DELETE', `/v1/clans/${clan.code}/members/Owner`, { token: clan.token })).status, 400);

	// Staff may remove somebody below them.
	await memberOf(env, clan, 'Another', clan.token);
	assert.equal((await call(env, 'DELETE', `/v1/clans/${clan.code}/members/Another`, { token: admin })).status, 200);
});

test('only the owner and deputies change the clan itself', async () =>
{
	const env = { DB: database() };
	const clan = await clanWithOwner(env);
	const admin = await memberOf(env, clan, 'Admin', clan.token);
	await call(env, 'PATCH', `/v1/clans/${clan.code}/members/Admin`,
		{ body: { role: 'admin' }, token: clan.token });

	assert.equal((await call(env, 'PATCH', `/v1/clans/${clan.code}`,
		{ body: { name: 'Renamed' }, token: admin })).status, 403);

	const renamed = await call(env, 'PATCH', `/v1/clans/${clan.code}`,
		{ body: { name: 'Renamed', tagline: 'New', listed: false }, token: clan.token });

	assert.equal(renamed.status, 200);
	assert.equal(renamed.body.clan.name, 'Renamed');
	assert.equal(renamed.body.clan.listed, false);
});

test('the roster is for members, in rank order', async () =>
{
	const env = { DB: database() };
	const clan = await clanWithOwner(env);
	const member = await memberOf(env, clan, 'Zed', clan.token);
	await memberOf(env, clan, 'Admin', clan.token);
	await call(env, 'PATCH', `/v1/clans/${clan.code}/members/Admin`,
		{ body: { role: 'admin' }, token: clan.token });

	assert.equal((await call(env, 'GET', `/v1/clans/${clan.code}/members`)).status, 403);

	const roster = await call(env, 'GET', `/v1/clans/${clan.code}/members`, { token: member });
	assert.deepEqual(roster.body.members.map((m) => m.rsn), ['Owner', 'Admin', 'Zed']);
	assert.deepEqual(roster.body.capabilities, ['MEMBER_VIEW']);
});

test('a clan nobody has made cannot be read, joined or changed', async () =>
{
	const env = { DB: database() };

	assert.equal((await call(env, 'GET', '/v1/clans/ZZZZZZ')).status, 404);
	assert.equal((await call(env, 'POST', '/v1/clans/ZZZZZZ/applications', { body: { rsn: 'A' } })).status, 404);
	assert.equal((await call(env, 'PATCH', '/v1/clans/ZZZZZZ', { body: { name: 'x' }, token: 'nope' })).status, 403);
});

test('you cannot apply to a clan you are already in', async () =>
{
	const env = { DB: database() };
	const clan = await clanWithOwner(env);

	assert.equal((await call(env, 'POST', `/v1/clans/${clan.code}/applications`,
		{ body: { rsn: 'Owner' } })).status, 409);
});
