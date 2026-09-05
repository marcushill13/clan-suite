package com.clansuite.event.data;

import com.google.gson.JsonObject;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One event on a clan's calendar, exactly as the service describes it.
 * <p>
 * What it tracks and what things are worth live in {@link #config}, which is a bag rather than a set of
 * fields because a raid night and a skilling week have almost nothing in common to put in columns. The
 * template says how to read it.
 */
@Data
@NoArgsConstructor
public class ClanEvent
{
	private String code = "";
	private String clanCode = "";
	private String name = "";

	/** As the service spells it; read through {@link #category()}. */
	private String category = "custom";

	private String template = "custom";

	private long startsAt;
	private long endsAt;
	private String timezone = "UTC";

	private JsonObject config = new JsonObject();

	private String leaderboard = "points";

	/** draft | published | cancelled. */
	private String status = "draft";

	private String createdBy = "";
	private long createdAt;

	public EventCategory category()
	{
		return EventCategory.of(category);
	}

	public EventTemplate template()
	{
		return EventTemplate.of(template);
	}

	public boolean isDraft()
	{
		return "draft".equalsIgnoreCase(status);
	}

	public boolean isCancelled()
	{
		return "cancelled".equalsIgnoreCase(status);
	}

	public boolean hasStarted(long now)
	{
		return now >= startsAt;
	}

	public boolean hasEnded(long now)
	{
		return now > endsAt;
	}

	/** Whether it is on right now, which is what the dashboard leads with. */
	public boolean isRunning(long now)
	{
		return !isCancelled() && !isDraft() && hasStarted(now) && !hasEnded(now);
	}

	/**
	 * The Boss of the Week challenge this event is being run as, if it is one.
	 * <p>
	 * That competition already has a service of its own, so an event of that template points at a
	 * challenge rather than duplicating it. Null for every other kind.
	 */
	public String challengeCode()
	{
		return config != null && config.has("challengeCode") && !config.get("challengeCode").isJsonNull()
			? config.get("challengeCode").getAsString()
			: null;
	}
}
