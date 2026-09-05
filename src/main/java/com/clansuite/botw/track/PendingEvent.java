package com.clansuite.botw.track;

import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Something that happened and has not been told to the service yet.
 * <p>
 * Kills and drops are written down the moment they happen and sent afterwards, rather than sent as
 * they happen. A player killing a boss is regularly offline, mid-disconnect, or logging out the
 * instant a pet drops, and none of that should cost them points.
 * <p>
 * The id is made here and never changes, which is what lets the same event be sent as many times as
 * necessary: the service keys on it and ignores a repeat. Sending is therefore always safe to retry,
 * and the plugin never has to know whether a request that timed out actually landed.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PendingEvent
{
	/** Which challenge this belongs to. One kill can matter to more than one. */
	private String challengeCode = "";

	private String id = UUID.randomUUID().toString();

	/** {@code kc} or {@code drop}. */
	private String kind = "kc";

	/** Null for a kill. */
	private String itemName;

	/** Kills for a {@code kc} event, quantity for a {@code drop}. */
	private int amount = 1;

	private long occurredAt;

	public static PendingEvent kill(String challengeCode, long occurredAt)
	{
		return new PendingEvent(
			challengeCode, UUID.randomUUID().toString(), "kc", null, 1, occurredAt);
	}

	public static PendingEvent drop(String challengeCode, String itemName, int quantity, long occurredAt)
	{
		return new PendingEvent(
			challengeCode, UUID.randomUUID().toString(), "drop", itemName, Math.max(1, quantity), occurredAt);
	}
}
