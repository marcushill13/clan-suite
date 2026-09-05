/**
 * What a clan has done, and who did it best.
 *
 * Every event ends and stops being interesting within the week. What people actually argue about a
 * year later is the rest of it: who has turned up to the most, who has never missed one, whose was the
 * biggest drop anybody has seen. That is what this is.
 *
 * Worked out from what is already stored rather than kept alongside it. A running total is a thing
 * that goes wrong quietly — a correction, a deleted event, a recount — and none of this is read often
 * enough to be worth that risk. A clan with a hundred events and fifty members is a few thousand rows,
 * which SQLite reads without noticing.
 *
 * Only finished events count towards any of it. An event still running has a leaderboard of its own,
 * and letting it into the records would mean the clan's all-time best changed every time somebody
 * killed something on a Tuesday.
 */

import { can, memberFor } from './clans.js';

/** How many rows a record or a table of members comes back with. */
const TOP = 10;

export async function recordRoutes(request, env, path, helpers)
{
	const { json } = helpers;

	const records = path.match(/^\/v1\/clans\/([A-Za-z0-9]+)\/records$/);
	if (records && request.method === 'GET')
	{
		return clanRecords(records[1].toUpperCase(), request, env, json);
	}

	const stats = path.match(/^\/v1\/clans\/([A-Za-z0-9]+)\/statistics$/);
	if (stats && request.method === 'GET')
	{
		return clanStatistics(stats[1].toUpperCase(), request, env, json);
	}

	const player = path.match(/^\/v1\/clans\/([A-Za-z0-9]+)\/statistics\/([^/]+)$/);
	if (player && request.method === 'GET')
	{
		return playerStatistics(
			player[1].toUpperCase(), decodeURIComponent(player[2]), request, env, json);
	}

	return null;
}

/**
 * The clan's permanent records.
 *
 * Each is a name, a number and what it was for, because a record with no context is a boast rather
 * than a record: "1.2B" means nothing without "from a Tumeken's shadow, at the raid night in March".
 */
async function clanRecords(code, request, env, json)
{
	const me = await memberFor(code, request, env);
	if (!me)
	{
		return json({ error: 'Only members can see the clan\'s records' }, 403);
	}

	const now = Date.now();

	const biggestDrop = await env.DB.prepare(
		`SELECT o.rsn, o.subject, MAX(o.amount) AS amount, e.name AS event, o.occurred_at AS at
		 FROM event_observations o
		 JOIN clan_events e ON e.code = o.event_code
		 WHERE e.clan_code = ? AND e.ends_at <= ? AND o.metric = 'loot'`)
		.bind(code, now).first();

	const bestScore = await env.DB.prepare(
		`SELECT p.rsn, (p.points + p.adjustment) AS amount, e.name AS event, e.ends_at AS at
		 FROM clan_event_participants p
		 JOIN clan_events e ON e.code = p.event_code
		 WHERE e.clan_code = ? AND e.ends_at <= ?
		 ORDER BY amount DESC LIMIT 1`)
		.bind(code, now).first();

	const mostKills = await env.DB.prepare(
		`SELECT o.rsn, SUM(o.amount) AS amount, e.name AS event, e.ends_at AS at
		 FROM event_observations o
		 JOIN clan_events e ON e.code = o.event_code
		 WHERE e.clan_code = ? AND e.ends_at <= ? AND o.metric = 'kc'
		 GROUP BY o.event_code, o.rsn ORDER BY amount DESC LIMIT 1`)
		.bind(code, now).first();

	const mostExperience = await env.DB.prepare(
		`SELECT o.rsn, SUM(o.amount) AS amount, e.name AS event, e.ends_at AS at
		 FROM event_observations o
		 JOIN clan_events e ON e.code = o.event_code
		 WHERE e.clan_code = ? AND e.ends_at <= ? AND o.metric = 'xp'
		 GROUP BY o.event_code, o.rsn ORDER BY amount DESC LIMIT 1`)
		.bind(code, now).first();

	const attended = await env.DB.prepare(
		`SELECT p.rsn, COUNT(*) AS amount FROM clan_event_participants p
		 JOIN clan_events e ON e.code = p.event_code
		 WHERE e.clan_code = ? AND e.ends_at <= ?
		 GROUP BY p.rsn ORDER BY amount DESC, p.rsn ASC LIMIT 1`)
		.bind(code, now).first();

	const wins = await winners(code, env, now);
	const mostWins = [...wins.entries()].sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0]))[0];

	return json({
		records: [
			record('Biggest drop', biggestDrop, biggestDrop?.subject),
			record('Highest score in an event', bestScore),
			record('Most kills in an event', mostKills),
			record('Most experience in an event', mostExperience),
			record('Most events attended', attended),
			mostWins ? { title: 'Most events won', rsn: mostWins[0], amount: mostWins[1] } : null
		].filter(Boolean)
	});
}

function record(title, row, detail)
{
	if (!row || !row.rsn || !row.amount)
	{
		return null;
	}

	return {
		title,
		rsn: row.rsn,
		amount: row.amount,
		detail: detail ?? undefined,
		event: row.event ?? undefined,
		at: row.at ?? undefined
	};
}

/**
 * Everything the clan has done together, and the table of who has done what.
 */
async function clanStatistics(code, request, env, json)
{
	const me = await memberFor(code, request, env);
	if (!me)
	{
		return json({ error: 'Only members can see the clan\'s statistics' }, 403);
	}

	const now = Date.now();

	const events = await env.DB.prepare(
		`SELECT COUNT(*) AS held FROM clan_events
		 WHERE clan_code = ? AND ends_at <= ? AND status = 'published'`)
		.bind(code, now).first();

	const turnout = await env.DB.prepare(
		`SELECT COUNT(*) AS attendances, COUNT(DISTINCT p.rsn) AS people,
		        SUM(p.points + p.adjustment) AS points
		 FROM clan_event_participants p
		 JOIN clan_events e ON e.code = p.event_code
		 WHERE e.clan_code = ? AND e.ends_at <= ?`)
		.bind(code, now).first();

	const counted = await env.DB.prepare(
		`SELECT o.metric, SUM(o.amount) AS amount FROM event_observations o
		 JOIN clan_events e ON e.code = o.event_code
		 WHERE e.clan_code = ? AND e.ends_at <= ?
		 GROUP BY o.metric`)
		.bind(code, now).all();

	const totals = {};
	for (const row of counted.results ?? [])
	{
		totals[row.metric] = row.amount;
	}

	const wins = await winners(code, env, now);

	const table = await env.DB.prepare(
		`SELECT p.rsn, COUNT(*) AS attended, SUM(p.points + p.adjustment) AS points
		 FROM clan_event_participants p
		 JOIN clan_events e ON e.code = p.event_code
		 WHERE e.clan_code = ? AND e.ends_at <= ?
		 GROUP BY p.rsn ORDER BY points DESC, attended DESC, p.rsn ASC LIMIT ?`)
		.bind(code, now, TOP).all();

	return json({
		clan: {
			eventsHeld: events?.held ?? 0,
			attendances: turnout?.attendances ?? 0,
			people: turnout?.people ?? 0,
			points: turnout?.points ?? 0,
			totals
		},
		members: (table.results ?? []).map((row) => ({
			rsn: row.rsn,
			attended: row.attended,
			points: row.points ?? 0,
			won: wins.get(row.rsn) ?? 0
		}))
	});
}

/**
 * One person's history.
 *
 * The streak is the interesting number and the fiddly one: it counts back through every finished
 * event the clan has held, not through the ones they attended, because a streak is about the ones
 * they did not miss.
 */
async function playerStatistics(code, rsn, request, env, json)
{
	const me = await memberFor(code, request, env);
	if (!me)
	{
		return json({ error: 'Only members can see this' }, 403);
	}

	const now = Date.now();

	const held = await env.DB.prepare(
		`SELECT code FROM clan_events WHERE clan_code = ? AND ends_at <= ? AND status = 'published'
		 ORDER BY ends_at DESC`)
		.bind(code, now).all();

	const attended = await env.DB.prepare(
		`SELECT p.event_code, (p.points + p.adjustment) AS points FROM clan_event_participants p
		 JOIN clan_events e ON e.code = p.event_code
		 WHERE e.clan_code = ? AND e.ends_at <= ? AND p.rsn = ?`)
		.bind(code, now, rsn).all();

	const mine = new Map((attended.results ?? []).map((row) => [row.event_code, row.points ?? 0]));
	const order = (held.results ?? []).map((row) => row.code);

	const streaks = streakOf(order, mine);
	const wins = await winners(code, env, now);

	const counted = await env.DB.prepare(
		`SELECT o.metric, SUM(o.amount) AS amount FROM event_observations o
		 JOIN clan_events e ON e.code = o.event_code
		 WHERE e.clan_code = ? AND e.ends_at <= ? AND o.rsn = ?
		 GROUP BY o.metric`)
		.bind(code, now, rsn).all();

	const totals = {};
	for (const row of counted.results ?? [])
	{
		totals[row.metric] = row.amount;
	}

	let points = 0;
	let best = 0;
	for (const score of mine.values())
	{
		points += score;
		best = Math.max(best, score);
	}

	return json({
		rsn,
		attended: mine.size,
		eventsHeld: order.length,
		won: wins.get(rsn) ?? 0,
		points,
		best,
		streak: streaks.current,
		longestStreak: streaks.longest,
		totals
	});
}

/**
 * How many events each person has won.
 *
 * A win is the top of a finished event's board, and a draw is a win for everybody who drew — two
 * people on the same points both turned up and both did as well as anybody. Nobody wins an event
 * nobody scored in.
 */
async function winners(code, env, now)
{
	const rows = await env.DB.prepare(
		`SELECT p.event_code, p.rsn, (p.points + p.adjustment) AS points
		 FROM clan_event_participants p
		 JOIN clan_events e ON e.code = p.event_code
		 WHERE e.clan_code = ? AND e.ends_at <= ?`)
		.bind(code, now).all();

	const best = new Map();
	for (const row of rows.results ?? [])
	{
		const top = best.get(row.event_code) ?? 0;
		if (row.points > top)
		{
			best.set(row.event_code, row.points);
		}
	}

	const won = new Map();
	for (const row of rows.results ?? [])
	{
		if (row.points > 0 && row.points === best.get(row.event_code))
		{
			won.set(row.rsn, (won.get(row.rsn) ?? 0) + 1);
		}
	}

	return won;
}

/**
 * The run of events somebody has not missed, now and at its longest.
 *
 * @param order   every finished event, newest first
 * @param attended the ones they were at
 * Exported for the tests, which is the only way to check a streak without inventing a year of events.
 */
export function streakOf(order, attended)
{
	let current = 0;
	let longest = 0;
	let running = 0;
	let counting = true;

	for (const code of order)
	{
		if (attended.has ? attended.has(code) : attended.includes(code))
		{
			running++;
			longest = Math.max(longest, running);

			if (counting)
			{
				current = running;
			}
		}
		else
		{
			running = 0;
			counting = false;
		}
	}

	return { current, longest };
}
