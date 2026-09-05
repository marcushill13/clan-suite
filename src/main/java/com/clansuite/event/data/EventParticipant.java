package com.clansuite.event.data;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Somebody taking part in an event.
 * <p>
 * {@code points} is everything they have — what was counted plus whatever was given or taken away by
 * hand. {@code adjustment} is only the second half of that, kept separate so a screen can say where a
 * score came from rather than leaving somebody to wonder why theirs moved.
 */
@Data
@NoArgsConstructor
public class EventParticipant
{
	private String rsn = "";
	private long joinedAt;

	/** Ticked by somebody who was there. The only evidence a social event ever has. */
	private boolean attended;

	private int points;
	private int adjustment;
}
