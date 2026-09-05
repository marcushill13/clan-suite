/**
 * The clan's events.
 *
 * Boss of the Week is one shape of competition: a boss, a week, kill counts and a drop list. A clan
 * runs raid nights, skilling weeks, minigame evenings and hide-and-seek, and building each of those as
 * its own plugin would mean six trackers, six leaderboards and six sets of the same bugs.
 *
 * So an event is a row with a schedule and a bag of configuration, and what makes one kind different
 * from another is what is in that bag. The bag is opaque here on purpose: the plugin writes it from a
 * template and reads it back, and when the scoring engine arrives it will read the same field. Storing
 * it as JSON now means the shape can change while the events already made keep working.
 *
 * What is not here yet, deliberately: nothing scores. These are the definitions — what is being run,
 * when, by whom, and what it counts. What actually happened is the next piece of work, and inventing
 * its tables before the trackers exist would be guessing.
 */

import { can, memberFor } from './clans.js';

/** What an event is for. The hub's colour coding and the calendar both come off this later. */
const CATEGORIES = ['pvm', 'raids', 'skilling', 'minigame', 'social', 'custom'];

/**
 * Draft is private to the people running it; published is what the clan sees. Cancelled is kept rather
 * than deleted, because an event that was called off is a thing people ask about afterwards.
 */
const STATUSES = ['draft', 'published', 'cancelled'];

const NAME_MAX = 60;
const CONFIG_MAX = 20000;

/** A clan's whole calendar is browsed at once; a year of weekly events is comfortably under this. */
const LIST_LIMIT = 200;

export async function eventRoutes(request, env, path, helpers)
{
	const { json } = helpers;

	const forClan = path.match(/^\/v1\/clans\/([A-Za-z0-9]+)\/events$/);
	if (forClan && request.method === 'POST')
	{
		return createEvent(forClan[1].toUpperCase(), request, env, helpers);
	}

	if (forClan && request.method === 'GET')
	{
		return listEvents(forClan[1].toUpperCase(), request, env, json);
	}

	const byCode = path.match(/^\/v1\/events\/([A-Za-z0-9]+)$/);
	if (byCode && request.method === 'GET')
	{
		return readEvent(byCode[1].toUpperCase(), request, env, json);
	}

	if (byCode && request.method === 'PATCH')
	{
		return updateEvent(byCode[1].toUpperCase(), request, env, helpers);
	}

	if (byCode && request.method === 'DELETE')
	{
		return deleteEvent(byCode[1].toUpperCase(), request, env, json);
	}

	return null;
}

async function createEvent(clanCode, request, env, { json, readJson, randomCode })
{
	const me = await memberFor(clanCode, request, env);
	if (!me || !can(me.role, 'EVENT_MANAGE'))
	{
		return json({ error: 'You cannot create events for this clan' }, 403);
	}

	const body = await readJson(request);
	const problem = invalid(body);
	if (problem)
	{
		return json({ error: problem }, 400);
	}

	const code = await unusedEventCode(env, randomCode);
	const now = Date.now();

	await env.DB.prepare(
		`INSERT INTO clan_events
			(code, clan_code, name, category, template, starts_at, ends_at, timezone,
			 config, leaderboard, status, created_by, created_at)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`)
		.bind(
			code, clanCode, String(body.name).trim(), body.category, String(body.template ?? 'custom'),
			Math.trunc(Number(body.startsAt)), Math.trunc(Number(body.endsAt)),
			String(body.timezone ?? 'UTC'), JSON.stringify(body.config ?? {}),
			String(body.leaderboard ?? 'points'),
			STATUSES.includes(body.status) ? body.status : 'draft',
			me.rsn, now)
		.run();

	return json({ event: publicEvent(await loadEvent(code, env)) }, 201);
}

/**
 * The clan's calendar.
 *
 * Members see what has been published. Whoever runs the events also sees the drafts, because a draft
 * is a thing being worked on rather than a thing being hidden.
 */
async function listEvents(clanCode, request, env, json)
{
	const me = await memberFor(clanCode, request, env);
	if (!me)
	{
		return json({ error: 'Only members can see this clan\'s events' }, 403);
	}

	const rows = can(me.role, 'EVENT_MANAGE')
		? await env.DB.prepare(
			'SELECT * FROM clan_events WHERE clan_code = ? ORDER BY starts_at DESC LIMIT ?')
			.bind(clanCode, LIST_LIMIT).all()
		: await env.DB.prepare(
			`SELECT * FROM clan_events WHERE clan_code = ? AND status != 'draft'
			 ORDER BY starts_at DESC LIMIT ?`)
			.bind(clanCode, LIST_LIMIT).all();

	return json({ events: (rows.results ?? []).map(publicEvent) });
}

async function readEvent(code, request, env, json)
{
	const event = await loadEvent(code, env);
	if (!event)
	{
		return json({ error: 'No event with that code' }, 404);
	}

	const me = await memberFor(event.clan_code, request, env);

	// A draft is the clan's own business until it is published; a published event can be read by
	// anyone holding its code, which is what lets one be shared into a Discord channel.
	if (event.status === 'draft' && (!me || !can(me.role, 'EVENT_MANAGE')))
	{
		return json({ error: 'No event with that code' }, 404);
	}

	return json({ event: publicEvent(event), role: me?.role ?? null });
}

async function updateEvent(code, request, env, { json, readJson })
{
	const event = await loadEvent(code, env);
	if (!event)
	{
		return json({ error: 'No event with that code' }, 404);
	}

	const me = await memberFor(event.clan_code, request, env);
	if (!me || !can(me.role, 'EVENT_MANAGE'))
	{
		return json({ error: 'You cannot change this clan\'s events' }, 403);
	}

	const body = await readJson(request) ?? {};
	const merged = {
		name: body.name === undefined ? event.name : body.name,
		category: body.category === undefined ? event.category : body.category,
		template: event.template,
		startsAt: body.startsAt === undefined ? event.starts_at : body.startsAt,
		endsAt: body.endsAt === undefined ? event.ends_at : body.endsAt,
		config: body.config === undefined ? JSON.parse(event.config) : body.config
	};

	const problem = invalid(merged);
	if (problem)
	{
		return json({ error: problem }, 400);
	}

	const status = body.status === undefined ? event.status : body.status;
	if (!STATUSES.includes(status))
	{
		return json({ error: 'That is not a state an event can be in' }, 400);
	}

	await env.DB.prepare(
		`UPDATE clan_events SET name = ?, category = ?, starts_at = ?, ends_at = ?, timezone = ?,
		 config = ?, leaderboard = ?, status = ? WHERE code = ?`)
		.bind(
			String(merged.name).trim(), merged.category,
			Math.trunc(Number(merged.startsAt)), Math.trunc(Number(merged.endsAt)),
			String(body.timezone ?? event.timezone), JSON.stringify(merged.config),
			String(body.leaderboard ?? event.leaderboard), status, code)
		.run();

	return json({ event: publicEvent(await loadEvent(code, env)) });
}

/**
 * Removing an event.
 *
 * Only ever a mistake being undone — something entered twice, or created against the wrong clan. An
 * event that ran and was called off should be cancelled rather than deleted, so that it still appears
 * in the calendar with an explanation, which is why cancelling is a state and this is not the way to
 * reach it.
 */
async function deleteEvent(code, request, env, json)
{
	const event = await loadEvent(code, env);
	if (!event)
	{
		return json({ error: 'No event with that code' }, 404);
	}

	const me = await memberFor(event.clan_code, request, env);
	if (!me || !can(me.role, 'EVENT_MANAGE'))
	{
		return json({ error: 'You cannot change this clan\'s events' }, 403);
	}

	await env.DB.prepare('DELETE FROM clan_events WHERE code = ?').bind(code).run();
	return json({ ok: true });
}

/** Everything that would make an event nonsense, in the order somebody would notice it. */
function invalid(body)
{
	if (!body)
	{
		return 'An event is required';
	}

	const name = String(body.name ?? '').trim();
	if (!name || name.length > NAME_MAX)
	{
		return `An event needs a name, and no longer than ${NAME_MAX} characters`;
	}

	if (!CATEGORIES.includes(body.category))
	{
		return 'Pick a category';
	}

	const startsAt = Number(body.startsAt);
	const endsAt = Number(body.endsAt);

	if (!Number.isFinite(startsAt) || !Number.isFinite(endsAt))
	{
		return 'An event needs a start and an end';
	}

	if (endsAt <= startsAt)
	{
		return 'An event has to end after it starts';
	}

	if (JSON.stringify(body.config ?? {}).length > CONFIG_MAX)
	{
		return 'That is more configuration than an event can carry';
	}

	return null;
}

async function unusedEventCode(env, randomCode)
{
	for (let attempt = 0; attempt < 10; attempt++)
	{
		const code = randomCode();
		const taken = await env.DB.prepare('SELECT 1 FROM clan_events WHERE code = ?').bind(code).first();

		if (!taken)
		{
			return code;
		}
	}

	throw new Error('Could not find an unused event code');
}

async function loadEvent(code, env)
{
	return env.DB.prepare('SELECT * FROM clan_events WHERE code = ?').bind(code).first();
}

function publicEvent(row)
{
	let config = {};
	try
	{
		config = JSON.parse(row.config);
	}
	catch
	{
		// An unreadable bag of configuration should not make the whole event unreadable: the schedule
		// and the name are still worth showing, and the plugin can put the rest right.
		config = {};
	}

	return {
		code: row.code,
		clanCode: row.clan_code,
		name: row.name,
		category: row.category,
		template: row.template,
		startsAt: row.starts_at,
		endsAt: row.ends_at,
		timezone: row.timezone,
		config,
		leaderboard: row.leaderboard,
		status: row.status,
		createdBy: row.created_by,
		createdAt: row.created_at
	};
}
