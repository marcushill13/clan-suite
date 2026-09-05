/**
 * Telling Discord what happened.
 *
 * Posted from here rather than from the plugins, and that is the whole design. A clan of forty running
 * the same raid night would otherwise send forty identical messages about the same purple, or forty
 * announcements of the same event — one per client that noticed. The service is the only thing that
 * sees an event once.
 *
 * Best effort throughout. A webhook that has been deleted, a channel that has been archived, Discord
 * being Discord: none of it may cost anybody a point. Everything here is awaited only far enough to
 * log, and nothing calls it in a way that can fail a request.
 */

/**
 * Where a webhook may point.
 *
 * Checked because this is a service that will make an HTTP request to a URL somebody types into a
 * settings box. Without this, a clan's settings screen is a way of making Cloudflare send requests to
 * anywhere at all, signed as though they came from us.
 */
const ALLOWED = [
	'https://discord.com/api/webhooks/',
	'https://discordapp.com/api/webhooks/',
	'https://ptb.discord.com/api/webhooks/',
	'https://canary.discord.com/api/webhooks/'
];

/** The colours the plugin draws events in, so a Discord post and the sidebar agree. */
const COLOURS = {
	pvm: 0xd66054,
	raids: 0xa874d8,
	skilling: 0x58a878,
	minigame: 0x5c94d0,
	social: 0xe2a84c,
	custom: 0x8c909e
};

export function isWebhook(url)
{
	const trimmed = String(url ?? '').trim();
	return ALLOWED.some((prefix) => trimmed.startsWith(prefix));
}

/**
 * Sends one message. Never throws: whatever is wrong with somebody's webhook is not worth failing the
 * request that happened to notice.
 */
export async function post(url, body)
{
	if (!isWebhook(url))
	{
		return false;
	}

	try
	{
		const response = await fetch(url, {
			method: 'POST',
			headers: { 'Content-Type': 'application/json' },
			body: JSON.stringify(body)
		});

		if (!response.ok)
		{
			console.log(`Discord refused a message: ${response.status}`);
		}

		return response.ok;
	}
	catch (error)
	{
		console.log(`Could not reach Discord: ${error}`);
		return false;
	}
}

/** An event has been put on the calendar. */
export function announcement(event, clan)
{
	return {
		embeds: [{
			title: `📅 ${event.name}`,
			description: `**${clan.name}** has an event on the calendar.`,
			color: colourOf(event.category),
			fields: [
				{ name: 'Starts', value: when(event.starts_at, event.timezone), inline: true },
				{ name: 'Ends', value: when(event.ends_at, event.timezone), inline: true },
				{ name: 'Joining', value: `Open Clan Suite and take part — code \`${event.code}\`` }
			]
		}]
	};
}

/** Half an hour out, which is when people decide whether they are coming. */
export function startingSoon(event, minutes)
{
	return {
		embeds: [{
			title: `🔥 ${event.name}`,
			description: minutes <= 1
				? 'Starting now.'
				: `Starting in ${Math.round(minutes)} minutes.`,
			color: colourOf(event.category)
		}]
	};
}

/**
 * A drop worth telling people about.
 *
 * Only the ones the event named in its own rules — a clan that wrote "Tumeken's shadow is worth five
 * hundred" has said what it considers worth announcing, and nobody has to maintain a second list of
 * what counts as exciting.
 */
export function bigDrop(event, rsn, item, points)
{
	return {
		embeds: [{
			title: '💎 BIG DROP',
			description: `**${rsn}** received **${item}** during ${event.name}`,
			color: 0xf0b03e,
			fields: [{ name: 'Worth', value: `${points} points`, inline: true }]
		}]
	};
}

/** How it finished. */
export function results(event, board)
{
	const medals = ['🥇', '🥈', '🥉'];
	const top = board.slice(0, 10)
		.map((row, index) => `${medals[index] ?? `${index + 1}.`} **${row.rsn}** — ${row.points} pts`)
		.join('\n');

	return {
		embeds: [{
			title: `🏆 ${event.name} — complete`,
			description: top || 'Nobody took part.',
			color: colourOf(event.category),
			footer: { text: `${board.length} took part` }
		}]
	};
}

function colourOf(category)
{
	return COLOURS[String(category ?? '').toLowerCase()] ?? COLOURS.custom;
}

/**
 * Discord renders a timestamp in whoever is reading it's own timezone, which is the right answer for a
 * clan spread across three of them.
 */
function when(at, timezone)
{
	return `<t:${Math.floor(at / 1000)}:f>`;
}
