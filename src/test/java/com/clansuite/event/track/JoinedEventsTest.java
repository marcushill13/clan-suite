package com.clansuite.event.track;

import com.clansuite.event.data.ClanEvent;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Which events want to hear about a thing that happened.
 */
public class JoinedEventsTest
{
	@Test
	public void anEventHearsAboutWhatItAskedFor()
	{
		ClanEvent raid = eventTracking("completions", "deaths");

		assertTrue(JoinedEvents.tracks(raid, "completions"));
		assertTrue(JoinedEvents.tracks(raid, "deaths"));
		assertFalse(JoinedEvents.tracks(raid, "xp"));
	}

	@Test
	public void aTemplateSpeltDifferentlyStillMatches()
	{
		assertTrue(JoinedEvents.tracks(eventTracking("XP"), "xp"));
		assertTrue(JoinedEvents.tracks(eventTracking("kc"), "KC"));
	}

	/**
	 * An empty box means "everything", not "nothing".
	 * <p>
	 * Somebody who made an event and never opened the tracking list meant for their event to count, and
	 * a competition that silently scores nought for a week is the worst way to find that out.
	 */
	@Test
	public void anEventThatSaysNothingWantsEverything()
	{
		ClanEvent bare = new ClanEvent();
		assertTrue(JoinedEvents.tracks(bare, "kc"));
		assertTrue(JoinedEvents.tracks(bare, "anything at all"));
	}

	/** Configuration from a newer plugin, or edited by hand. Better counted than dropped. */
	@Test
	public void configurationItCannotReadIsNotAReasonToStop()
	{
		ClanEvent odd = new ClanEvent();
		JsonObject config = new JsonObject();
		config.add("track", JsonParser.parseString("\"kc\""));
		odd.setConfig(config);

		assertTrue(JoinedEvents.tracks(odd, "kc"));
	}

	private static ClanEvent eventTracking(String... metrics)
	{
		JsonArray track = new JsonArray();
		for (String metric : metrics)
		{
			track.add(metric);
		}

		JsonObject config = new JsonObject();
		config.add("track", track);

		ClanEvent event = new ClanEvent();
		event.setConfig(config);
		return event;
	}
}
