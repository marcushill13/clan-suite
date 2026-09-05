/**
 * Clans, the people in them, and who is allowed to do what.
 *
 * Boss of the Week had two kinds of person: whoever made a challenge, and everyone who joined it.
 * That works for one competition and falls apart for a clan, where the same handful of people run
 * every event for a year and the rest turn up to play. So membership moves here, is granted once, and
 * carries a rank that every other part of the service can ask about.
 *
 * Ranks are named after the game's, because that is what a clan already understands. The cap is the
 * game's too — five hundred — so a clan that is full in game is full here, and the directory can say
 * so before somebody applies.
 *
 * One thing this cannot do, and no amount of code here would fix: it has no way to prove that the
 * person calling themselves Zezima is Zezima. The plugin reports the name of whoever is logged in, and
 * a modified client can report anything. That is why joining is by application rather than by walking
 * in: a human who knows the clan approves each one, which is the same check a clan already makes.
 */

/** The game's own limit. A clan that cannot take a five-hundred-and-first member in game should not here. */
export const MEMBER_LIMIT = 500;

/**
 * Ranks, most powerful first. The order is the whole of the "can I act on this person" rule: you may
 * only change somebody below you, and only to a rank below your own. Without it a deputy could demote
 * the owner, or an admin could promote themselves.
 */
export const ROLES = ['owner', 'deputy', 'admin', 'moderator', 'member'];

/**
 * What each rank may do.
 *
 * Capabilities rather than rank checks at the call site, so that adding a rank later is a line in this
 * table rather than a hunt through every handler. The plugin is told the caller's list and draws its
 * screens from it — but that is only so people are not shown buttons that would fail. Every one of
 * these is checked here as well, because a client can be modified and a server cannot.
 */
const CAPABILITIES = {
	owner: ['CLAN_SETTINGS', 'CLAN_DELETE', 'ROLE_ASSIGN', 'MEMBER_MANAGE', 'EVENT_MANAGE', 'POINTS_ADJUST', 'RESULT_VERIFY', 'MEMBER_VIEW'],
	deputy: ['CLAN_SETTINGS', 'ROLE_ASSIGN', 'MEMBER_MANAGE', 'EVENT_MANAGE', 'POINTS_ADJUST', 'RESULT_VERIFY', 'MEMBER_VIEW'],
	admin: ['MEMBER_MANAGE', 'EVENT_MANAGE', 'POINTS_ADJUST', 'RESULT_VERIFY', 'MEMBER_VIEW'],
	moderator: ['RESULT_VERIFY', 'POINTS_ADJUST', 'MEMBER_VIEW'],
	member: ['MEMBER_VIEW']
};

/** How many clans one listing request can return. The directory is browsed, not scraped. */
const DIRECTORY_LIMIT = 50;

const NAME_MAX = 40;
const TAGLINE_MAX = 120;
const MESSAGE_MAX = 300;

export function capabilitiesOf(role)
{
	return CAPABILITIES[role] ?? [];
}

export function can(role, capability)
{
	return capabilitiesOf(role).includes(capability);
}

/** Whether `actor` outranks `subject`. Equal ranks do not, which is what stops admins fighting. */
function outranks(actor, subject)
{
	return ROLES.indexOf(actor) < ROLES.indexOf(subject);
}

/**
 * Everything under /v1/clans. Returns null when the path is not ours, so the existing routes carry on
 * untouched.
 */
export async function clanRoutes(request, env, path, helpers)
{
	const { json, readJson, randomToken, randomCode } = helpers;

	if (path === '/v1/clans' && request.method === 'POST')
	{
		return createClan(request, env, helpers);
	}

	if (path === '/v1/clans' && request.method === 'GET')
	{
		return directory(request, env, json);
	}

	const byCode = path.match(/^\/v1\/clans\/([A-Za-z0-9]+)$/);
	if (byCode && request.method === 'GET')
	{
		return readClan(byCode[1].toUpperCase(), request, env, json);
	}

	if (byCode && request.method === 'PATCH')
	{
		return updateClan(byCode[1].toUpperCase(), request, env, helpers);
	}

	const members = path.match(/^\/v1\/clans\/([A-Za-z0-9]+)\/members$/);
	if (members && request.method === 'GET')
	{
		return listMembers(members[1].toUpperCase(), request, env, json);
	}

	const member = path.match(/^\/v1\/clans\/([A-Za-z0-9]+)\/members\/([^/]+)$/);
	if (member && request.method === 'PATCH')
	{
		return setRole(member[1].toUpperCase(), decodeURIComponent(member[2]), request, env, helpers);
	}

	if (member && request.method === 'DELETE')
	{
		return removeMember(member[1].toUpperCase(), decodeURIComponent(member[2]), request, env, json);
	}

	const applications = path.match(/^\/v1\/clans\/([A-Za-z0-9]+)\/applications$/);
	if (applications && request.method === 'POST')
	{
		return apply(applications[1].toUpperCase(), request, env, helpers);
	}

	if (applications && request.method === 'GET')
	{
		return listApplications(applications[1].toUpperCase(), request, env, json);
	}

	const application = path.match(/^\/v1\/clans\/([A-Za-z0-9]+)\/applications\/([^/]+)$/);
	if (application && request.method === 'POST')
	{
		return decide(application[1].toUpperCase(), decodeURIComponent(application[2]), request, env, helpers);
	}

	return null;
}

/**
 * Whoever creates the clan owns it. There is no invite to accept and nobody to ask, which is the point
 * — a clan leader should be able to set this up in the time it takes to type the name.
 */
async function createClan(request, env, { json, readJson, randomToken, randomCode })
{
	const body = await readJson(request);
	const name = String(body?.name ?? '').trim();
	const rsn = String(body?.rsn ?? '').trim();

	if (!name || name.length > NAME_MAX)
	{
		return json({ error: `A clan name is required, and no longer than ${NAME_MAX} characters` }, 400);
	}

	if (!rsn)
	{
		return json({ error: 'A RuneScape name is required' }, 400);
	}

	const tagline = String(body?.tagline ?? '').trim().slice(0, TAGLINE_MAX);
	const code = await unusedClanCode(env, randomCode);
	const token = randomToken();
	const now = Date.now();

	await env.DB.batch([
		env.DB.prepare(
			`INSERT INTO clans
				(code, name, tagline, owner_rsn, listed, applications_open, member_limit, members, created_at)
			VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?)`)
			.bind(code, name, tagline, rsn, body?.listed === false ? 0 : 1,
				body?.applicationsOpen === false ? 0 : 1, MEMBER_LIMIT, now),
		env.DB.prepare(
			'INSERT INTO clan_members (clan_code, rsn, role, token, joined_at) VALUES (?, ?, ?, ?, ?)')
			.bind(code, rsn, 'owner', token, now)
	]);

	const clan = await loadClan(code, env);
	return json({ clan: publicClan(clan), token, role: 'owner', capabilities: capabilitiesOf('owner') }, 201);
}

/**
 * The hub: clans that have chosen to be seen.
 *
 * A clan that hides itself is not listed here at all, but is still reachable by its code — that is the
 * difference between "we are not recruiting publicly" and "we do not exist". Fullness is worked out
 * here rather than left to the plugin, so every client draws the same badge.
 */
async function directory(request, env, json)
{
	const query = new URL(request.url).searchParams.get('q');
	const needle = query ? `%${query.trim().toLowerCase()}%` : null;

	const rows = needle
		? await env.DB.prepare(
			`SELECT * FROM clans WHERE listed = 1 AND (lower(name) LIKE ? OR lower(tagline) LIKE ?)
			 ORDER BY members DESC, created_at ASC LIMIT ?`)
			.bind(needle, needle, DIRECTORY_LIMIT).all()
		: await env.DB.prepare(
			'SELECT * FROM clans WHERE listed = 1 ORDER BY members DESC, created_at ASC LIMIT ?')
			.bind(DIRECTORY_LIMIT).all();

	return json({ clans: (rows.results ?? []).map(publicClan) });
}

/**
 * One clan. A member gets their own rank and what it lets them do; anyone else gets what the directory
 * would have shown them, so that a code shared in Discord is enough to look before applying.
 */
async function readClan(code, request, env, json)
{
	const clan = await loadClan(code, env);
	if (!clan)
	{
		return json({ error: 'No clan with that code' }, 404);
	}

	const me = await memberFor(code, request, env);

	return json({
		clan: publicClan(clan),
		role: me?.role ?? null,
		capabilities: me ? capabilitiesOf(me.role) : []
	});
}

async function updateClan(code, request, env, { json, readJson })
{
	const me = await memberFor(code, request, env);
	if (!me || !can(me.role, 'CLAN_SETTINGS'))
	{
		return json({ error: 'Only the owner and their deputies can change the clan' }, 403);
	}

	const clan = await loadClan(code, env);
	if (!clan)
	{
		return json({ error: 'No clan with that code' }, 404);
	}

	const body = await readJson(request);
	const name = body?.name === undefined ? clan.name : String(body.name).trim();
	if (!name || name.length > NAME_MAX)
	{
		return json({ error: `A clan name is required, and no longer than ${NAME_MAX} characters` }, 400);
	}

	await env.DB.prepare(
		'UPDATE clans SET name = ?, tagline = ?, listed = ?, applications_open = ? WHERE code = ?')
		.bind(
			name,
			body?.tagline === undefined ? clan.tagline : String(body.tagline).trim().slice(0, TAGLINE_MAX),
			body?.listed === undefined ? clan.listed : (body.listed ? 1 : 0),
			body?.applicationsOpen === undefined ? clan.applications_open : (body.applicationsOpen ? 1 : 0),
			code)
		.run();

	return json({ clan: publicClan(await loadClan(code, env)), role: me.role, capabilities: capabilitiesOf(me.role) });
}

async function listMembers(code, request, env, json)
{
	const me = await memberFor(code, request, env);
	if (!me)
	{
		return json({ error: 'Only members can see the roster' }, 403);
	}

	const rows = await env.DB.prepare(
		'SELECT rsn, role, joined_at AS joinedAt FROM clan_members WHERE clan_code = ?').bind(code).all();

	// In rank order, then alphabetically, which is how the game lists a clan and so how people expect
	// to read one.
	const members = (rows.results ?? []).sort((a, b) =>
		ROLES.indexOf(a.role) - ROLES.indexOf(b.role) || a.rsn.localeCompare(b.rsn));

	return json({ members, role: me.role, capabilities: capabilitiesOf(me.role) });
}

/**
 * Promotion and demotion.
 *
 * Two rules, both about not being able to reach above yourself: you may only change somebody you
 * outrank, and only to a rank you outrank. The owner is nobody's subject, so a clan can never be taken
 * from underneath them by someone they trusted with a rank.
 */
async function setRole(code, rsn, request, env, { json, readJson })
{
	const me = await memberFor(code, request, env);
	if (!me || !can(me.role, 'ROLE_ASSIGN'))
	{
		return json({ error: 'You cannot set ranks in this clan' }, 403);
	}

	const body = await readJson(request);
	const role = String(body?.role ?? '').trim();
	if (!ROLES.includes(role))
	{
		return json({ error: 'That is not a rank' }, 400);
	}

	if (role === 'owner')
	{
		// Handing the clan over is a different thing from a promotion, and needs to move the old owner
		// down at the same time. Left out rather than half-done.
		return json({ error: 'Ownership cannot be given away yet' }, 400);
	}

	const subject = await env.DB.prepare(
		'SELECT rsn, role FROM clan_members WHERE clan_code = ? AND rsn = ?').bind(code, rsn).first();

	if (!subject)
	{
		return json({ error: 'They are not in this clan' }, 404);
	}

	if (!outranks(me.role, subject.role) || !outranks(me.role, role))
	{
		return json({ error: 'You can only change ranks below your own' }, 403);
	}

	await env.DB.prepare('UPDATE clan_members SET role = ? WHERE clan_code = ? AND rsn = ?')
		.bind(role, code, rsn).run();

	return json({ ok: true, rsn, role });
}

/**
 * Kicking, and leaving.
 *
 * Anyone may remove themselves — except the owner, who would leave the clan with nobody able to run
 * it. Removing somebody else needs a rank above theirs.
 */
async function removeMember(code, rsn, request, env, json)
{
	const me = await memberFor(code, request, env);
	if (!me)
	{
		return json({ error: 'Only members can do that' }, 403);
	}

	const leaving = me.rsn.toLowerCase() === rsn.toLowerCase();

	if (leaving && me.role === 'owner')
	{
		return json({ error: 'The owner cannot leave their own clan' }, 400);
	}

	if (!leaving)
	{
		if (!can(me.role, 'MEMBER_MANAGE'))
		{
			return json({ error: 'You cannot remove people from this clan' }, 403);
		}

		const subject = await env.DB.prepare(
			'SELECT role FROM clan_members WHERE clan_code = ? AND rsn = ?').bind(code, rsn).first();

		if (!subject)
		{
			return json({ error: 'They are not in this clan' }, 404);
		}

		if (!outranks(me.role, subject.role))
		{
			return json({ error: 'You can only remove people below your own rank' }, 403);
		}
	}

	await env.DB.batch([
		env.DB.prepare('DELETE FROM clan_members WHERE clan_code = ? AND rsn = ?').bind(code, rsn),
		env.DB.prepare('UPDATE clans SET members = members - 1 WHERE code = ? AND members > 0').bind(code)
	]);

	return json({ ok: true });
}

/**
 * Applying to join.
 *
 * Open to anyone who can see the clan, which is either because it is listed or because somebody gave
 * them the code. A clan that has closed applications takes none, and a full clan takes none either —
 * both checked here rather than only hidden in the plugin.
 */
async function apply(code, request, env, { json, readJson })
{
	const clan = await loadClan(code, env);
	if (!clan)
	{
		return json({ error: 'No clan with that code' }, 404);
	}

	if (!clan.applications_open)
	{
		return json({ error: 'This clan is not taking applications' }, 403);
	}

	if (clan.members >= clan.member_limit)
	{
		return json({ error: 'This clan is full' }, 409);
	}

	const body = await readJson(request);
	const rsn = String(body?.rsn ?? '').trim();
	if (!rsn)
	{
		return json({ error: 'A RuneScape name is required' }, 400);
	}

	const already = await env.DB.prepare(
		'SELECT 1 FROM clan_members WHERE clan_code = ? AND rsn = ?').bind(code, rsn).first();

	if (already)
	{
		return json({ error: 'You are already in this clan' }, 409);
	}

	// One row per person per clan, replaced on re-application: somebody who was turned down and has
	// since been vouched for should not have to be found among their own earlier attempts.
	await env.DB.prepare(
		`INSERT INTO clan_applications (clan_code, rsn, message, status, applied_at, decided_at, decided_by)
		 VALUES (?, ?, ?, 'pending', ?, NULL, NULL)
		 ON CONFLICT (clan_code, rsn) DO UPDATE SET
		 	message = excluded.message, status = 'pending', applied_at = excluded.applied_at,
		 	decided_at = NULL, decided_by = NULL`)
		.bind(code, rsn, String(body?.message ?? '').trim().slice(0, MESSAGE_MAX), Date.now())
		.run();

	return json({ ok: true, status: 'pending' }, 201);
}

async function listApplications(code, request, env, json)
{
	const me = await memberFor(code, request, env);
	if (!me || !can(me.role, 'MEMBER_MANAGE'))
	{
		return json({ error: 'You cannot see this clan\'s applications' }, 403);
	}

	const rows = await env.DB.prepare(
		`SELECT rsn, message, applied_at AS appliedAt FROM clan_applications
		 WHERE clan_code = ? AND status = 'pending' ORDER BY applied_at ASC`)
		.bind(code).all();

	return json({ applications: rows.results ?? [] });
}

/**
 * Accepting or turning down an application.
 *
 * A denied application is kept rather than deleted, so the same person reapplying the next day does
 * not look like a new one — and so nobody has to remember who they already said no to.
 */
async function decide(code, rsn, request, env, { json, readJson })
{
	const me = await memberFor(code, request, env);
	if (!me || !can(me.role, 'MEMBER_MANAGE'))
	{
		return json({ error: 'You cannot decide applications for this clan' }, 403);
	}

	const body = await readJson(request);
	const decision = String(body?.decision ?? '').trim();
	if (decision !== 'accept' && decision !== 'deny')
	{
		return json({ error: 'Accept or deny' }, 400);
	}

	const application = await env.DB.prepare(
		'SELECT rsn FROM clan_applications WHERE clan_code = ? AND rsn = ? AND status = \'pending\'')
		.bind(code, rsn).first();

	if (!application)
	{
		return json({ error: 'No application from them' }, 404);
	}

	const now = Date.now();

	if (decision === 'deny')
	{
		await env.DB.prepare(
			'UPDATE clan_applications SET status = \'denied\', decided_at = ?, decided_by = ? WHERE clan_code = ? AND rsn = ?')
			.bind(now, me.rsn, code, rsn).run();

		return json({ ok: true, status: 'denied' });
	}

	const clan = await loadClan(code, env);
	if (clan.members >= clan.member_limit)
	{
		return json({ error: 'The clan is full' }, 409);
	}

	// The token is made here and handed back through the plugin's own poll, because the applicant is
	// not the one making this request and has nowhere to receive it in the moment.
	await env.DB.batch([
		env.DB.prepare(
			'INSERT INTO clan_members (clan_code, rsn, role, token, joined_at) VALUES (?, ?, \'member\', ?, ?)')
			.bind(code, rsn, crypto.randomUUID().replace(/-/g, ''), now),
		env.DB.prepare('UPDATE clans SET members = members + 1 WHERE code = ?').bind(code),
		env.DB.prepare(
			'UPDATE clan_applications SET status = \'accepted\', decided_at = ?, decided_by = ? WHERE clan_code = ? AND rsn = ?')
			.bind(now, me.rsn, code, rsn)
	]);

	return json({ ok: true, status: 'accepted' });
}

/** Who is calling, by the token the plugin holds. Null for anyone who is not a member. */
async function memberFor(code, request, env)
{
	const token = request.headers.get('X-Clan-Token');
	if (!token)
	{
		return null;
	}

	return env.DB.prepare('SELECT rsn, role FROM clan_members WHERE clan_code = ? AND token = ?')
		.bind(code, token)
		.first();
}

async function loadClan(code, env)
{
	return env.DB.prepare('SELECT * FROM clans WHERE code = ?').bind(code).first();
}

async function unusedClanCode(env, randomCode)
{
	for (let attempt = 0; attempt < 10; attempt++)
	{
		const code = randomCode();
		const taken = await env.DB.prepare('SELECT 1 FROM clans WHERE code = ?').bind(code).first();

		if (!taken)
		{
			return code;
		}
	}

	throw new Error('Could not find an unused clan code');
}

/** What anyone may see. Tokens never appear here. */
function publicClan(row)
{
	return {
		code: row.code,
		name: row.name,
		tagline: row.tagline,
		ownerRsn: row.owner_rsn,
		listed: !!row.listed,
		applicationsOpen: !!row.applications_open,
		members: row.members,
		memberLimit: row.member_limit,
		full: row.members >= row.member_limit
	};
}
