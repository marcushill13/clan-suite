package com.clansuite;

import java.io.File;
import net.runelite.client.RuneLite;

/**
 * Every file this plugin writes, and the one place that decides where they go.
 * <p>
 * All of it lives under a folder of this plugin's own inside {@code .runelite}, which is what the
 * plugin hub requires: a plugin may write within its own subdirectory there and nowhere else. An
 * earlier version put screenshots in {@code .runelite/screenshots}, which is the client's folder
 * rather than this plugin's, and shared with everything else that takes a picture.
 * <p>
 * Gathered here rather than spread across the classes that need it so there is a single answer to
 * "where does this plugin write", both for a reviewer and for anyone changing it later.
 */
public final class ClanSuiteFiles
{
	/** Named for the plugin as the hub knows it, so the folder is identifiable from the outside. */
	private static final File ROOT = new File(RuneLite.RUNELITE_DIR, "clansuite");

	private ClanSuiteFiles()
	{
	}

	/**
	 * Where a challenge's screenshots go — one folder per challenge, because the question people ask is
	 * "who has proof of that visage on the Vorkath week", not "what happened in March".
	 *
	 * @param challengeName as the creator typed it, so it is already safe for a folder name
	 */
	public static File screenshots(String challengeName)
	{
		return new File(new File(ROOT, "screenshots"), challengeName);
	}

	/** Where an evidence export lands. */
	/** The month's calendar pictures, one file per month, named so they sort in order. */
	public static File calendars()
	{
		return new File(ROOT, "calendars");
	}

	public static File exports()
	{
		return new File(ROOT, "exports");
	}
}
