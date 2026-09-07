package com.clansuite;

/**
 * Where the plugin should actually talk to, given what the settings say.
 * <p>
 * RuneLite writes a setting down the moment it is edited and then keeps it forever, so changing the
 * default in the code does nothing for anyone who has already been in that box. Two people had already
 * pointed their client at the Boss of the Week service, which is a different plugin's, so this reads
 * the saved value and corrects the two ways it can be wrong: empty, and left over from before Clan
 * Suite had a service of its own.
 * <p>
 * Anything else typed in is honoured as written. A clan running its own copy is the whole reason the
 * box exists.
 */
public final class ServiceUrl
{
	/** Clan Suite's own worker. Its own database, deliberately separate from Boss of the Week's. */
	public static final String DEFAULT = "https://clan-suite.marcushill3313.workers.dev";

	/** The Boss of the Week service, which this plugin used to be pointed at by mistake. */
	private static final String BOSS_OF_THE_WEEK = "https://botw.marcushill3313.workers.dev";

	private ServiceUrl()
	{
	}

	public static String of(String configured)
	{
		if (configured == null)
		{
			return DEFAULT;
		}

		String url = configured.trim();
		while (url.endsWith("/"))
		{
			url = url.substring(0, url.length() - 1);
		}

		if (url.isEmpty() || url.equalsIgnoreCase(BOSS_OF_THE_WEEK))
		{
			return DEFAULT;
		}

		return url;
	}
}
