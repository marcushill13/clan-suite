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
import * as discord from './discord.js';

/** What an event is for. The hub's colour coding and the calendar both come off this later. */
const CATEGORIES = [
	'pvm', 'raids', 'skilling', 'minigame', 'social', 'clue', 'wilderness', 'pvp', 'special', 'custom'
];

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

	const observations = path.match(/^\/v1\/events\/([A-Za-z0-9]+)\/observations$/);
	if (observations && request.method === 'POST')
	{
		return submitObservations(observations[1].toUpperCase(), request, env, helpers);
	}

	const joining = path.match(/^\/v1\/events\/([A-Za-z0-9]+)\/join$/);
	if (joining && request.method === 'POST')
	{
		return joinEvent(joining[1].toUpperCase(), request, env, helpers);
	}

	const participants = path.match(/^\/v1\/events\/([A-Za-z0-9]+)\/participants$/);
	if (participants && request.method === 'GET')
	{
		return listParticipants(participants[1].toUpperCase(), request, env, json);
	}

	const participant = path.match(/^\/v1\/events\/([A-Za-z0-9]+)\/participants\/([^/]+)$/);
	if (participant && request.method === 'PATCH')
	{
		return markParticipant(
			participant[1].toUpperCase(), decodeURIComponent(participant[2]), request, env, helpers);
	}

	if (participant && request.method === 'DELETE')
	{
		return removeParticipant(
			participant[1].toUpperCase(), decodeURIComponent(participant[2]), request, env, json);
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

	// Whether the reader is taking part comes back with each event, because the plugin has to know
	// which events to report to and asking one by one would be a request per event on every login.
	const rows = can(me.role, 'EVENT_MANAGE')
		? await env.DB.prepare(
			`SELECT e.*, p.rsn AS joined_as FROM clan_events e
			 LEFT JOIN clan_event_participants p ON p.event_code = e.code AND p.rsn = ?
			 WHERE e.clan_code = ? ORDER BY e.starts_at DESC LIMIT ?`)
			.bind(me.rsn, clanCode, LIST_LIMIT).all()
		: await env.DB.prepare(
			`SELECT e.*, p.rsn AS joined_as FROM clan_events e
			 LEFT JOIN clan_event_participants p ON p.event_code = e.code AND p.rsn = ?
			 WHERE e.clan_code = ? AND e.status != 'draft' ORDER BY e.starts_at DESC LIMIT ?`)
			.bind(me.rsn, clanCode, LIST_LIMIT).all();

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

	// Announced the moment it goes on the calendar, and only then — a draft being worked on is nobody
	// else's business, and an event edited twice should not be announced twice.
	if (status === 'published' && event.status !== 'published')
	{
		await tell(env, event.clan_code, (clan) => discord.announcement(
			{ ...event, name: String(merged.name).trim(), starts_at: Math.trunc(Number(merged.startsAt)),
				ends_at: Math.trunc(Number(merged.endsAt)) }, clan));
	}

	if (body.config !== undefined)
	{
		// The rules changed, so what everybody has under them changed too. Same reasoning as the
		// challenge leaderboard: a points value fixed mid-week has to apply to the week, not the rest
		// of it.
		await rescoreEveryone(code, env);
	}

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
		createdAt: row.created_at,

		// Only ever set on the clan's own listing, where the reader is known.
		joined: row.joined_as !== undefined ? !!row.joined_as : undefined
	};
}

/**
 * Taking part.
 *
 * Signing up rather than being signed up: an event's leaderboard should hold the people who turned up,
 * not everybody in a five-hundred-strong clan sitting on nought. It also gives the trackers something
 * to check before they report anything — a kill counts towards an event the player actually joined.
 *
 * Only while the event could still be played. Joining one that finished last week would put somebody
 * on a leaderboard they were never at.
 */
async function joinEvent(code, request, env, { json, readJson })
{
	const event = await loadEvent(code, env);
	if (!event || event.status === 'draft')
	{
		return json({ error: 'No event with that code' }, 404);
	}

	const me = await memberFor(event.clan_code, request, env);
	if (!me)
	{
		return json({ error: 'Only members of the clan can join its events' }, 403);
	}

	if (event.status === 'cancelled')
	{
		return json({ error: 'That event was cancelled' }, 409);
	}

	if (Date.now() > event.ends_at)
	{
		return json({ error: 'That event has finished' }, 409);
	}

	await env.DB.prepare(
		`INSERT INTO clan_event_participants (event_code, rsn, joined_at, attended, points, adjustment)
		 VALUES (?, ?, ?, 0, 0, 0)
		 ON CONFLICT (event_code, rsn) DO NOTHING`)
		.bind(code, me.rsn, Date.now())
		.run();

	return json({ ok: true, rsn: me.rsn }, 201);
}

/**
 * The leaderboard, such as it is until the trackers arrive: who is in, what they have been given by
 * hand, and whether somebody has ticked them off as having turned up.
 */
async function listParticipants(code, request, env, json)
{
	const event = await loadEvent(code, env);
	if (!event)
	{
		return json({ error: 'No event with that code' }, 404);
	}

	const me = await memberFor(event.clan_code, request, env);
	if (!me)
	{
		return json({ error: 'Only members of the clan can see who is taking part' }, 403);
	}

	const rows = await env.DB.prepare(
		`SELECT rsn, joined_at AS joinedAt, attended, points, adjustment
		 FROM clan_event_participants WHERE event_code = ?
		 ORDER BY (points + adjustment) DESC, rsn ASC`)
		.bind(code).all();

	return json({
		participants: (rows.results ?? []).map((row) => ({
			rsn: row.rsn,
			joinedAt: row.joinedAt,
			attended: !!row.attended,
			points: row.points + row.adjustment,
			adjustment: row.adjustment
		}))
	});
}

/**
 * Marking somebody off, or correcting their score.
 *
 * Hide and seek, quizzes and scavenger hunts leave no trace a client could read, and they never will:
 * somebody who was there says who else was. That is not a gap in the tracking, it is the tracking, and
 * it needs to be as ordinary a thing to do as any other.
 *
 * Points given by hand are kept apart from points that were counted, the same way the challenge
 * leaderboard does it — so that when the trackers arrive and start writing the counted half, they
 * cannot quietly undo somebody's correction.
 */
async function markParticipant(code, rsn, request, env, { json, readJson })
{
	const event = await loadEvent(code, env);
	if (!event)
	{
		return json({ error: 'No event with that code' }, 404);
	}

	const me = await memberFor(event.clan_code, request, env);
	if (!me || !can(me.role, 'RESULT_VERIFY'))
	{
		return json({ error: 'You cannot mark results for this event' }, 403);
	}

	const existing = await env.DB.prepare(
		'SELECT rsn FROM clan_event_participants WHERE event_code = ? AND rsn = ?')
		.bind(code, rsn).first();

	const body = await readJson(request) ?? {};

	if (body.adjustment !== undefined && !Number.isFinite(Number(body.adjustment)))
	{
		return json({ error: 'An adjustment has to be a number' }, 400);
	}

	// Somebody who turned up without signing up is still somebody who turned up.
	if (!existing)
	{
		await env.DB.prepare(
			`INSERT INTO clan_event_participants (event_code, rsn, joined_at, attended, points, adjustment)
			 VALUES (?, ?, ?, 0, 0, 0)`)
			.bind(code, rsn, Date.now())
			.run();
	}

	await env.DB.prepare(
		`UPDATE clan_event_participants SET
			attended = CASE WHEN ? IS NULL THEN attended ELSE ? END,
			adjustment = CASE WHEN ? IS NULL THEN adjustment ELSE ? END
		 WHERE event_code = ? AND rsn = ?`)
		.bind(
			body.attended === undefined ? null : (body.attended ? 1 : 0),
			body.attended === undefined ? null : (body.attended ? 1 : 0),
			body.adjustment === undefined ? null : Math.trunc(Number(body.adjustment)),
			body.adjustment === undefined ? null : Math.trunc(Number(body.adjustment)),
			code, rsn)
		.run();

	return json({ ok: true });
}

/** Leaving, or being taken off by whoever runs the event. */
async function removeParticipant(code, rsn, request, env, json)
{
	const event = await loadEvent(code, env);
	if (!event)
	{
		return json({ error: 'No event with that code' }, 404);
	}

	const me = await memberFor(event.clan_code, request, env);
	if (!me)
	{
		return json({ error: 'Only members of the clan can do that' }, 403);
	}

	const leaving = me.rsn.toLowerCase() === rsn.toLowerCase();
	if (!leaving && !can(me.role, 'EVENT_MANAGE'))
	{
		return json({ error: 'You cannot take people off this event' }, 403);
	}

	await env.DB.prepare('DELETE FROM clan_event_participants WHERE event_code = ? AND rsn = ?')
		.bind(code, rsn).run();

	return json({ ok: true });
}

/** One request cannot carry more than this. Keeps a bad client from flooding the table. */
const MAX_OBSERVATIONS = 50;

/**
 * What the plugin saw.
 *
 * The client reports what happened — a kill, a drop, an amount of experience — and this works out what
 * it was worth. That division is the whole trust model, and it is the same one Boss of the Week has
 * always used: a modified client can claim a kill it never got, but it cannot decide that the kill was
 * worth five hundred points.
 *
 * Each observation carries an id made when it happened, so a resend after a disconnect lands on the
 * same row rather than a second one. That is what lets the plugin retry blindly.
 */
async function submitObservations(code, request, env, { json, readJson })
{
	const event = await loadEvent(code, env);
	if (!event || event.status === 'draft')
	{
		return json({ error: 'No event with that code' }, 404);
	}

	const me = await memberFor(event.clan_code, request, env);
	if (!me)
	{
		return json({ error: 'Only members of the clan can report to its events' }, 403);
	}

	const body = await readJson(request);
	const submitted = Array.isArray(body?.observations) ? body.observations : [];

	if (submitted.length > MAX_OBSERVATIONS)
	{
		return json({ error: `No more than ${MAX_OBSERVATIONS} at a time` }, 400);
	}

	const now = Date.now();
	const writes = [];

	for (const observation of submitted)
	{
		if (typeof observation.id !== 'string' || !observation.id)
		{
			continue;
		}

		const occurredAt = Number(observation.occurredAt);
		if (!Number.isFinite(occurredAt))
		{
			continue;
		}

		// Only what happened while the event was on. A kill from the morning before does not count
		// towards an evening's mass, however honestly it is reported.
		if (occurredAt < event.starts_at || occurredAt > event.ends_at)
		{
			continue;
		}

		const statement = env.DB.prepare(
			`INSERT OR IGNORE INTO event_observations
				(id, event_code, rsn, metric, subject, amount, occurred_at, recorded_at)
			VALUES (?, ?, ?, ?, ?, ?, ?, ?)`)
			.bind(
				observation.id, code, me.rsn,
				String(observation.metric ?? '').toLowerCase(),
				observation.subject === undefined || observation.subject === null
					? null
					: String(observation.subject),
				Math.max(0, Math.trunc(Number(observation.amount) || 0)),
				Math.trunc(occurredAt), now);

		statement.observationId = observation.id;
		writes.push(statement);
	}

	if (writes.length)
	{
		await env.DB.batch(writes);
	}

	let rules = [];
	try
	{
		rules = JSON.parse(event.config)?.points ?? [];
	}
	catch
	{
		rules = [];
	}

	rules = Array.isArray(rules) ? rules : [];

	// A bounty is the one thing one person's report can change for everybody: the first Fox whistle
	// takes the hundred points away from whoever the database thought had it, if a report that
	// happened earlier turns up late. So everyone is worked out again rather than only the reporter.
	const bounties = rules.filter(isBounty);
	const won = bounties.length ? await settleBounties(code, env, rules, mine(writes)) : [];

	const points = bounties.length
		? (await rescoreEveryone(code, env), await currentPoints(code, me.rsn, env))
		: await rescore(code, me.rsn, env, event);

	for (const bounty of won)
	{
		await tell(env, event.clan_code, () => discord.bountyWon(event, bounty.rsn, bounty.rule));
	}

	// Only the drops the event named in its own rules. A clan that wrote "Tumeken's shadow is worth
	// five hundred" has already said what it thinks is worth shouting about, so there is no second
	// list of exciting items to keep up to date.
	for (const worth of notable(submitted, event))
	{
		await tell(env, event.clan_code, () =>
			discord.bigDrop(event, me.rsn, worth.subject, worth.points));
	}

	return json({ ok: true, taken: writes.length, points });
}

/**
 * What somebody has, worked out from everything they have reported and the event's own rules.
 *
 * Recomputed from the observations rather than added up as they arrive, for the reason Boss of the
 * Week learned the hard way: a rule that pays a point per ten kills cannot be applied to each report
 * as it lands, because three reports of four kills is twelve kills and one point, not zero. Totals
 * first, rules second.
 *
 * It also means the creator can fix a points value mid-event and everybody's score can be rebuilt from
 * what actually happened.
 */
async function rescore(code, rsn, env, event = null)
{
	const row = event ?? await loadEvent(code, env);
	if (!row)
	{
		return 0;
	}

	let rules = [];
	try
	{
		rules = JSON.parse(row.config)?.points ?? [];
	}
	catch
	{
		rules = [];
	}

	const totals = await env.DB.prepare(
		`SELECT metric, subject, SUM(amount) AS amount, COUNT(*) AS times
		 FROM event_observations WHERE event_code = ? AND rsn = ?
		 GROUP BY metric, subject`)
		.bind(code, rsn).all();

	const list = Array.isArray(rules) ? rules : [];
	const winners = await bountyWinners(code, env, list);

	const won = new Set();
	for (const [index, winner] of winners)
	{
		if (winner.rsn === rsn)
		{
			won.add(index);
		}
	}

	const points = scoreOf(totals.results ?? [], list, won);

	await env.DB.prepare(
		`INSERT INTO clan_event_participants (event_code, rsn, joined_at, attended, points, adjustment)
		 VALUES (?, ?, ?, 0, ?, 0)
		 ON CONFLICT (event_code, rsn) DO UPDATE SET points = excluded.points`)
		.bind(code, rsn, Date.now(), points)
		.run();

	return points;
}

/**
 * The rules engine, such as it is.
 *
 * A rule names a metric, optionally a subject, and either a flat number of points or a threshold —
 * "every ten kills is worth one". Deliberately small: the shapes here are the ones a clan actually
 * writes on a whiteboard, and a rule language nobody asked for would be a week of work and a fortnight
 * of bugs.
 *
 * Exported so the tests can reach it without a database.
 */
export function scoreOf(totals, rules, won = new Set())
{
	let points = 0;

	for (let index = 0; index < rules.length; index++)
	{
		const rule = rules[index];
		const metric = String(rule?.metric ?? '').toLowerCase();
		if (!metric)
		{
			continue;
		}

		// A bounty is not worth anything per kill or per drop. It is worth its points to whoever got
		// there first and nothing at all to everybody else, which is a fact about the whole event
		// rather than about this person's totals — so it is settled before this and handed in.
		if (isBounty(rule))
		{
			points += won.has(index) ? Math.trunc(Number(rule.points) || 0) : 0;
			continue;
		}

		const subject = rule.subject === undefined || rule.subject === null
			? null
			: String(rule.subject).toLowerCase();

		let amount = 0;
		let times = 0;

		for (const total of totals)
		{
			if (String(total.metric).toLowerCase() !== metric)
			{
				continue;
			}

			if (subject !== null && String(total.subject ?? '').toLowerCase() !== subject)
			{
				continue;
			}

			amount += Number(total.amount) || 0;
			times += Number(total.times) || 0;
		}

		if (!amount && !times)
		{
			continue;
		}

		const worth = Math.trunc(Number(rule.points) || 0);
		const per = Math.trunc(Number(rule.per) || 0);

		// A threshold pays on the running total, so four kills reported three times is twelve kills.
		// Without a threshold, every one of the thing is worth the same: five deaths at minus five is
		// minus twenty-five.
		points += per > 0 ? Math.floor(amount / per) * worth : amount * worth;
	}

	return points;
}

export function isBounty(rule)
{
	return String(rule?.kind ?? '').toLowerCase() === 'bounty';
}

/**
 * Who got there first.
 *
 * A bounty is the one rule that cannot be worked out from one person's own reports: "first to a Fox
 * whistle" is a question about everybody. Ordered by when it happened rather than when it arrived, so
 * somebody whose client was offline for ten minutes still wins the whistle they got first — with the
 * arrival time and then the id to break ties, because two people cannot both be first and the answer
 * must not depend on which row the database felt like returning.
 */
async function bountyWinners(code, env, rules)
{
	const winners = new Map();

	for (let index = 0; index < rules.length; index++)
	{
		const rule = rules[index];
		if (!isBounty(rule))
		{
			continue;
		}

		const metric = String(rule.metric ?? '').toLowerCase();
		if (!metric)
		{
			continue;
		}

		const subject = rule.subject === undefined || rule.subject === null
			? null
			: String(rule.subject).toLowerCase();

		const first = subject === null
			? await env.DB.prepare(
				`SELECT rsn, id FROM event_observations WHERE event_code = ? AND metric = ?
				 ORDER BY occurred_at ASC, recorded_at ASC, id ASC LIMIT 1`)
				.bind(code, metric).first()
			: await env.DB.prepare(
				`SELECT rsn, id FROM event_observations
				 WHERE event_code = ? AND metric = ? AND lower(subject) = ?
				 ORDER BY occurred_at ASC, recorded_at ASC, id ASC LIMIT 1`)
				.bind(code, metric, subject).first();

		if (first)
		{
			winners.set(index, first);
		}
	}

	return winners;
}

/** The ids this request actually wrote, so a bounty already settled is not announced again. */
function mine(writes)
{
	return new Set(writes.map((statement) => statement.observationId).filter(Boolean));
}

/**
 * Which bounties were decided by what just arrived.
 *
 * Only the ones whose winning observation is in this batch: everything else was already settled, and
 * a clan does not want the same whistle announced every time somebody reports a log.
 */
async function settleBounties(code, env, rules, justWritten)
{
	const winners = await bountyWinners(code, env, rules);
	const decided = [];

	for (const [index, winner] of winners)
	{
		if (justWritten.has(winner.id))
		{
			decided.push({ rsn: winner.rsn, rule: rules[index] });
		}
	}

	return decided;
}

async function currentPoints(code, rsn, env)
{
	const row = await env.DB.prepare(
		'SELECT points FROM clan_event_participants WHERE event_code = ? AND rsn = ?')
		.bind(code, rsn).first();

	return row?.points ?? 0;
}

/** Rebuilds every participant's score, for when the rules themselves change. */
async function rescoreEveryone(code, env)
{
	const rows = await env.DB.prepare(
		'SELECT DISTINCT rsn FROM event_observations WHERE event_code = ?').bind(code).all();

	for (const row of rows.results ?? [])
	{
		await rescore(code, row.rsn, env);
	}
}

/**
 * The drops in this batch that the event's own rules single out by name.
 */
function notable(submitted, event)
{
	let rules = [];
	try
	{
		rules = JSON.parse(event.config)?.points ?? [];
	}
	catch
	{
		return [];
	}

	const named = (Array.isArray(rules) ? rules : []).filter(
		(rule) => String(rule?.metric ?? '').toLowerCase() === 'drop' && rule?.subject);

	const found = [];

	for (const observation of submitted)
	{
		if (String(observation?.metric ?? '').toLowerCase() !== 'drop' || !observation?.subject)
		{
			continue;
		}

		const rule = named.find(
			(candidate) => String(candidate.subject).toLowerCase()
				=== String(observation.subject).toLowerCase());

		if (rule)
		{
			found.push({ subject: observation.subject, points: Math.trunc(Number(rule.points) || 0) });
		}
	}

	return found;
}

/**
 * Says something to a clan's Discord, if it has one.
 *
 * Best effort by design, and never in the way: whatever is wrong with somebody's webhook must not fail
 * the request that happened to notice, nor cost anybody a point.
 */
async function tell(env, clanCode, message)
{
	try
	{
		const clan = await env.DB.prepare('SELECT * FROM clans WHERE code = ?').bind(clanCode).first();
		if (!clan?.webhook_url)
		{
			return;
		}

		await discord.post(clan.webhook_url, message(clan));
	}
	catch (error)
	{
		console.log(`Could not tell Discord: ${error}`);
	}
}

/**
 * The things that are true at a moment rather than because somebody did something: an event about to
 * start, and one that has finished. Run from the worker's timer.
 *
 * Each is announced once, recorded on the event itself rather than worked out from the clock, so a
 * timer that runs twice or a deploy in the middle of one does not announce anything twice.
 */
export async function announceDue(env, now = Date.now())
{
	const soon = await env.DB.prepare(
		`SELECT * FROM clan_events
		 WHERE status = 'published' AND announced_start = 0 AND starts_at > ? AND starts_at <= ?`)
		.bind(now - 60 * 60 * 1000, now + 30 * 60 * 1000).all();

	for (const event of soon.results ?? [])
	{
		await tell(env, event.clan_code, () =>
			discord.startingSoon(event, Math.max(0, (event.starts_at - now) / 60000)));

		await env.DB.prepare('UPDATE clan_events SET announced_start = 1 WHERE code = ?')
			.bind(event.code).run();
	}

	const over = await env.DB.prepare(
		`SELECT * FROM clan_events
		 WHERE status = 'published' AND announced_end = 0 AND ends_at <= ?`)
		.bind(now).all();

	for (const event of over.results ?? [])
	{
		const board = await env.DB.prepare(
			`SELECT rsn, (points + adjustment) AS points FROM clan_event_participants
			 WHERE event_code = ? ORDER BY (points + adjustment) DESC, rsn ASC`)
			.bind(event.code).all();

		await tell(env, event.clan_code, () => discord.results(event, board.results ?? []));

		await env.DB.prepare('UPDATE clan_events SET announced_end = 1 WHERE code = ?')
			.bind(event.code).run();
	}
}
