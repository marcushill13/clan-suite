package com.clansuite.event.data;

import com.clansuite.ui.Theme;
import java.awt.Color;
import java.util.Locale;

/**
 * What an event is for.
 * <p>
 * The clan's monthly calendar is colour-coded by exactly this, so the colours live here rather than in
 * whichever screen happens to draw them next.
 */
public enum EventCategory
{
	PVM("PvM", Theme.PVM),
	RAIDS("Raids", Theme.RAIDS),
	SKILLING("Skilling", Theme.SKILLING),
	MINIGAME("Minigame", Theme.MINIGAME),
	SOCIAL("Social", Theme.SOCIAL),
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
