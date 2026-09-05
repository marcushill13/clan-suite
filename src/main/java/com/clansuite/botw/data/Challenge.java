package com.clansuite.botw.data;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One Boss of the Week challenge, exactly as the service describes it.
 * <p>
 * A plain class with a no-arg constructor because this is read from JSON, both from the service and
 * from the local copy kept between sessions.
 */
@Data
@NoArgsConstructor
public class Challenge
{
	/** What people type to join. Six characters, no letters that look like digits. */
	private String code = "";

	private String name = "";
	private String boss = "";

	/** Epoch milliseconds, both. The timezone below is only for showing the creator what they chose. */
	private long startsAt;
	private long endsAt;

	private String timezone = "UTC";

	/** Every {@code kcPer} kills is worth {@code kcPoints}. Both are the creator's choice. */
	private int kcPer = 10;
	private int kcPoints = 1;

	private List<DropRule> drops = new ArrayList<>();

	private String creatorRsn = "";

	public boolean hasStarted(long now)
	{
		return now >= startsAt;
	}

	public boolean hasEnded(long now)
	{
		return now > endsAt;
	}

	/**
	 * Whether kills happening right now count. Everything the tracker does hangs off this.
	 */
	public boolean isRunning(long now)
	{
		return hasStarted(now) && !hasEnded(now);
	}

	/**
	 * The points a named item is worth, or zero if the challenge does not count it.
	 * <p>
	 * Matched without case because a drop's name comes from the game while the rule's came from the
	 * wiki, and the two do not always agree on capitalisation.
	 */
	public int pointsForDrop(String itemName)
	{
		for (DropRule rule : drops)
		{
			if (rule.getName().equalsIgnoreCase(itemName))
			{
				return rule.getPoints();
			}
		}

		return 0;
	}

	public boolean counts(String itemName)
	{
		return pointsForDrop(itemName) > 0;
	}
}
