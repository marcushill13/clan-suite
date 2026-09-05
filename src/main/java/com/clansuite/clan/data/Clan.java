package com.clansuite.clan.data;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A clan as the service describes it. Everything here is public: what the hub shows a stranger is the
 * same object a member reads, because there is nothing on it worth hiding.
 */
@Data
@NoArgsConstructor
public class Clan
{
	/** What people paste to find it, the same shape as a challenge code. */
	private String code = "";

	private String name = "";
	private String tagline = "";
	private String ownerRsn = "";

	/** Whether it appears in the hub. A clan that is not listed is still reachable by its code. */
	private boolean listed = true;

	private boolean applicationsOpen = true;

	private int members;

	/** The game's own cap, sent rather than assumed so the two can never disagree. */
	private int memberLimit = 500;

	private boolean full;

	/**
	 * Whether the clan has somewhere to announce things.
	 * <p>
	 * Only whether, never where. Anyone holding the address could post to the clan's Discord as the
	 * clan, so the service does not send it back out — not even to the owner who typed it in.
	 */
	private boolean discord;

	/** "184 / 500", which is what the hub shows on every row. */
	public String membership()
	{
		return members + " / " + memberLimit;
	}

	/** Whether somebody could apply right now, which is what decides the button on a hub row. */
	public boolean acceptsApplications()
	{
		return applicationsOpen && !full;
	}
}
