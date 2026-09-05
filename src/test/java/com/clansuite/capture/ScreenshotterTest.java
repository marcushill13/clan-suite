package com.clansuite.capture;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * How wide the creator's copy comes out, which decides whether the game's own text can be read in it.
 */
public class ScreenshotterTest
{
	@Test
	public void anOrdinaryClientIsBarelyShrunk()
	{
		// 1080p, the common case. Measured at this width, the drop message and the mouse tooltip read
		// cleanly; the 600 this used to be did not.
		assertEquals(1520, Screenshotter.targetWidth(1920));
	}

	@Test
	public void asmallClientIsLeftAlone()
	{
		// Nothing is gained by shrinking something already smaller than the target, and enlarging it
		// would cost bytes for detail that is not there.
		assertEquals(1280, Screenshotter.targetWidth(1280));
		assertEquals(800, Screenshotter.targetWidth(800));
	}

	/**
	 * The reason there is a floor as well as a target.
	 * <p>
	 * The game draws its text at a fixed pixel size whatever the window is, so legibility follows how
	 * far the picture is shrunk. Holding a big client to 1520 would more than halve it and take the
	 * writing with it.
	 */
	@Test
	public void aBigClientIsNotForcedDownToTheTarget()
	{
		assertEquals(1920, Screenshotter.targetWidth(2560));
		assertEquals(2880, Screenshotter.targetWidth(3840));
	}

	@Test
	public void nothingIsEverEnlarged()
	{
		for (int width : new int[]{320, 765, 1280, 1600, 1920, 2560, 3440, 3840, 5120})
		{
			assertTrue("enlarged a " + width + "px client", Screenshotter.targetWidth(width) <= width);
		}
	}
}
