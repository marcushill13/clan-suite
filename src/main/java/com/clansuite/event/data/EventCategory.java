package com.clansuite.event.data;

import com.clansuite.ui.Theme;
import java.awt.Color;
import java.util.Locale;

/**
 * What an event is for.
 * <p>
 * The clan's monthly calendar is colour-coded by exactly this, and these are its own bands rather than
 * anything invented here — so the sidebar all week and the picture posted at the start of the month
 * agree about what colour a raid night is.
 */
public enum EventCategory
{
	SKILLING("Skilling", Theme.SKILLING),
	PVM("PvM", Theme.PVM),
	RAIDS("Raids", Theme.RAIDS),
	MINIGAME("Minigame", Theme.MINIGAME),
	CLUE("Clue", Theme.CLUE),
	SOCIAL("Social", Theme.SOCIAL),
	WILDERNESS("Wilderness", Theme.WILDERNESS),
	PVP("PvP", Theme.PVP),
	SPECIAL("Special", Theme.SPECIAL),
	CUSTOM("Custom", Theme.NEUTRAL);

	private final String label;
	private final Color colour;

	EventCategory(String label, Color colour)
	{
		this.label = label;
		this.colour = colour;
	}

	public String getLabel()
	{
		return label;
	}

	public Color getColour()
	{
		return colour;
	}

	public String wire()
	{
		return name().toLowerCase(Locale.ROOT);
	}

	@Override
	public String toString()
	{
		return label;
	}

	public static EventCategory of(String wire)
	{
		if (wire != null)
		{
			for (EventCategory category : values())
			{
				if (category.name().equalsIgnoreCase(wire.trim()))
				{
					return category;
				}
			}
		}

		return CUSTOM;
	}
}
