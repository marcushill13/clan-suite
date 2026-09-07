package com.clansuite;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

/**
 * A saved setting outlives the default it was saved over, which is how a client ends up still
 * talking to another plugin's service after the code has moved on.
 */
public class ServiceUrlTest
{
	@Test
	public void nothingSavedMeansTheDefault()
	{
		assertEquals(ServiceUrl.DEFAULT, ServiceUrl.of(null));
		assertEquals(ServiceUrl.DEFAULT, ServiceUrl.of(""));
		assertEquals(ServiceUrl.DEFAULT, ServiceUrl.of("   "));
	}

	@Test
	public void theBossOfTheWeekServiceIsNotThisPluginsToUse()
	{
		assertEquals(ServiceUrl.DEFAULT,
			ServiceUrl.of("https://botw.marcushill3313.workers.dev"));
		assertEquals(ServiceUrl.DEFAULT,
			ServiceUrl.of("  https://botw.marcushill3313.workers.dev/  "));
	}

	/** A clan running its own copy is the whole reason the box exists. */
	@Test
	public void anythingElseIsHonouredAsWritten()
	{
		assertEquals("https://clan.example.com", ServiceUrl.of("https://clan.example.com"));
		assertEquals("https://clan.example.com", ServiceUrl.of(" https://clan.example.com/ "));
	}
}
