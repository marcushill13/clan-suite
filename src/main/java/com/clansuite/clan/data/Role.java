package com.clansuite.clan.data;

import com.clansuite.ui.Theme;
import java.awt.Color;
import java.util.Locale;

/**
 * What somebody is in their clan, named the way the game names it.
 * <p>
 * Ordered most powerful first, and that order is the whole of the rule about who may act on whom: you
 * can only change somebody below you. Declared here as well as in the service because the panel has to
 * decide what to draw, but the service is what actually enforces it — a rank the plugin believes in is
 * only a way of not showing people buttons that would fail.
 */
public enum Role
{
	OWNER("Owner", Theme.GOLD),
	DEPUTY("Deputy owner", Theme.SOCIAL),
	ADMIN("Administrator", Theme.RAIDS),
	MODERATOR("Moderator", Theme.MINIGAME),
	MEMBER("Member", Theme.NEUTRAL);

	private final String label;
	private final Color colour;

	Role(String label, Color colour)
	{
		this.label = label;
		this.colour = colour;
	}

	public String getLabel()
	{
		return label;
	}

	/** The strip down the left of this person's row, so a roster reads by rank at a glance. */
	public Color getColour()
	{
		return colour;
	}

	/** What the service calls it. */
	public String wire()
	{
		return name().toLowerCase(Locale.ROOT);
	}

	/**
	 * The rank the service named, or null for anyone who is not in the clan.
	 * <p>
	 * An unrecognised rank reads as null rather than as a member: a plugin that is older than the
	 * service should show somebody nothing rather than quietly demote them to the lowest rank it knows.
	 */
	public static Role of(String wire)
	{
		if (wire == null)
		{
			return null;
		}

		for (Role role : values())
		{
			if (role.name().equalsIgnoreCase(wire.trim()))
			{
				return role;
			}
		}

		return null;
	}

	/**
	 * The rank as a person says it. Swing asks a combo box's items for this, and without it the picker
	 * offers DEPUTY and MODERATOR in the enum's own shouting.
	 */
	@Override
	public String toString()
	{
		return label;
	}

	public boolean outranks(Role other)
	{
		return other != null && ordinal() < other.ordinal();
	}

	/** Whether this rank runs the clan, which is what the fuller dashboard is for. */
	public boolean isStaff()
	{
		return this != MEMBER;
	}
}
