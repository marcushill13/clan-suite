package com.clansuite.event.calendar;

import com.clansuite.ClanSuiteFiles;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Locale;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;

/**
 * Where the month's picture is kept, and how it is sent.
 * <p>
 * Saved to the player's own machine first and always, whether or not it goes anywhere afterwards. A
 * clan that posts its calendar somewhere this plugin has never heard of — a forum, a spreadsheet, a
 * pinned message — should still be able to use this, and the file is the thing they need.
 */
@Slf4j
public final class CalendarFile
{
	private static final DateTimeFormatter NAMED = DateTimeFormatter.ofPattern("yyyy-MM", Locale.ENGLISH);

	private CalendarFile()
	{
	}

	/**
	 * Writes the picture out, named so a folder of them sorts into order.
	 *
	 * @return where it was written, or null if it could not be
	 */
	public static File save(BufferedImage image, String clanName, YearMonth month)
	{
		File folder = ClanSuiteFiles.calendars();

		if (!folder.exists() && !folder.mkdirs())
		{
			log.warn("Could not make {}", folder);
			return null;
		}

		File file = new File(folder, month.format(NAMED) + " " + safe(clanName) + ".png");

		try
		{
			ImageIO.write(image, "png", file);
			return file;
		}
		catch (IOException e)
		{
			log.warn("Could not write the calendar", e);
			return null;
		}
	}

	/** The picture as the service takes it: base64, the same way a screenshot travels. */
	public static String encode(BufferedImage image)
	{
		try (ByteArrayOutputStream bytes = new ByteArrayOutputStream())
		{
			ImageIO.write(image, "png", bytes);
			return Base64.getEncoder().encodeToString(bytes.toByteArray());
		}
		catch (IOException e)
		{
			log.warn("Could not encode the calendar", e);
			return null;
		}
	}

	/**
	 * A clan name that can be a file name, since clans are called all sorts of things.
	 * <p>
	 * Anything a file system would object to becomes a space rather than nothing, so "Zamorak/Zealots"
	 * files as two words rather than as one run-together one.
	 */
	static String safe(String name)
	{
		String cleaned = name == null
			? ""
			: name.replaceAll("[^a-zA-Z0-9 _-]", " ").replaceAll("\\s+", " ").trim();

		return cleaned.isEmpty() ? "clan" : cleaned;
	}
}
