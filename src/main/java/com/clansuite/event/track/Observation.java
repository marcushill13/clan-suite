package com.clansuite.event.track;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Something the plugin saw, on its way to the service.
 * <p>
 * What it means and what it is worth are two different questions, and only the first is answered here.
 * A kill is a kill; whether a kill is worth a point, or a tenth of one, or nothing at all is the
 * event's business and is decided where the player cannot edit it.
 * <p>
 * The id is made when it happens and never changes, so the same observation can be sent as many times
 * as necessary — the service keys on it and ignores a repeat. That is what lets a disconnect halfway
 * through a raid cost nothing.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Observation
{
	/** Which event this is for. One kill can matter to several at once. */
	private String eventCode = "";

	private String id = UUID.randomUUID().toString();

	/** {@code kc}, {@code drop}, {@code xp}, {@code death}, {@code completion}. */
	private String metric = "";

	/** What it was about — the boss, the item, the skill. Null where the metric says it all. */
	private String subject;

	/** Kills, quantity, experience. What it counts depends on the metric. */
	private int amount = 1;

	private long occurredAt;

	public static Observation of(
		String eventCode, String metric, String subject, int amount, long occurredAt)
	{
		return new Observation(eventCode, UUID.randomUUID().toString(), metric, subject,
			Math.max(0, amount), occurredAt);
	}
}
