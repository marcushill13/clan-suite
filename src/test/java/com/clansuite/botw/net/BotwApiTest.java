package com.clansuite.botw.net;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Telling "there is no such challenge" apart from "I could not ask".
 */
public class BotwApiTest
{
	/**
	 * The distinction the whole thing turns on.
	 * <p>
	 * A challenge that is gone gets cleared off the player's list and its queued events thrown away.
	 * Doing that to one that merely could not be reached would delete somebody's competition because
	 * their connection dropped for a moment, so an ordinary failure must never look gone.
	 */
	@Test
	public void onlyAnAnswerOfNoSuchThingCountsAsGone()
	{
		assertTrue(BotwApi.Result.gone("No challenge with that code").isGone());

		assertFalse(BotwApi.Result.failed("Could not reach the server").isGone());
		assertFalse(BotwApi.Result.failed("The server said no (500)").isGone());
		assertFalse(BotwApi.Result.failed("The server sent something unreadable").isGone());
	}

	@Test
	public void goneIsStillAFailure()
	{
		assertFalse(BotwApi.Result.gone("No challenge with that code").ok());
	}

	@Test
	public void aSuccessIsNeitherFailedNorGone()
	{
		BotwApi.Result<String> result = BotwApi.Result.of("fine");
		assertTrue(result.ok());
		assertFalse(result.isGone());
	}
}
