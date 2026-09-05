package com.clansuite;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("clansuite")
public interface ClanSuiteConfig extends Config
{
	@ConfigItem(
		keyName = "screenshotDrops",
		name = "Screenshot scoring drops",
		description = "Saves a picture under .runelite/botw whenever a drop scores you points.",
		position = 0
	)
	default boolean screenshotDrops()
	{
		return true;
	}

	@ConfigItem(
		keyName = "shareScreenshots",
		name = "Send screenshots to the challenge creator",
		description =
			"Sends a small copy of each scoring drop's screenshot to whoever runs the challenge, "
				+ "so they can verify it. Only they can see it. Turn this off to keep every screenshot "
				+ "on your own machine.",
		position = 1
	)
	default boolean shareScreenshots()
	{
		return true;
	}

	@ConfigItem(
		keyName = "serverUrl",
		name = "Server address",
		description = "Where challenges and leaderboards live. Leave this alone unless your clan runs its own.",
		position = 1
	)
	default String serverUrl()
	{
		return "https://botw.marcushill3313.workers.dev";
	}
}
