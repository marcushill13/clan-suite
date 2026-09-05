package com.clansuite.event.data;

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
	PVM("PvM", new Color(214, 96, 84)),
	RAIDS("Raids", new Color(168, 116, 216)),
	SKILLING("Skilling", new Color(88, 168, 120)),
	MINIGAME("Minigame", new Color(92, 148, 208)),
	SOCIAL("Social", new Color(226, 168, 76)),
	CUSTOM("Custom", new Color(140, 144, 158));

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
