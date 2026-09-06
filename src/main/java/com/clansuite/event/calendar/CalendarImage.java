package com.clansuite.event.calendar;

import com.clansuite.event.data.ClanEvent;
import com.clansuite.event.data.EventCategory;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * The month, as a picture.
 * <p>
 * A clan posts one of these at the start of every month, and somebody makes it by hand in a drawing
 * program from a list they typed out in Discord. The events are already here, with their dates and
 * their colours, so this draws it instead.
 * <p>
 * Drawn in the plugin rather than on the service because Java has a drawing library and a Cloudflare
 * Worker does not, and because the person who wants the picture is sitting in front of the client.
 * <p>
 * The colours are the clan's own bands, the same ones the sidebar uses all week. That is the point of
 * having put them in one place: the picture and the plugin cannot drift apart.
 */
public final class CalendarImage
{
	/** Wide enough that "Blind Date Bossing – Yama" fits in a day rather than being cut in half. */
	private static final int WIDTH = 1760;
	private static final int MARGIN = 40;

	/** Room for the clan's name, the month, and the line under them. */
	private static final int HEADER = 170;

	private static final int WEEKDAYS = 44;
	private static final int LEGEND = 90;

	/** A day with nothing on is still this tall, so the grid stays a grid. */
	private static final int MIN_CELL = 132;

	private static final int PILL = 30;
	private static final int PILL_GAP = 5;

	/** Past this many on one day the cell says how many more rather than growing without limit. */
	private static final int MOST_PILLS = 4;

	private static final Color BACKGROUND = new Color(24, 25, 31);
	private static final Color CELL = new Color(33, 35, 43);
	private static final Color CELL_OUTSIDE = new Color(28, 29, 36);
	private static final Color TODAY = new Color(46, 49, 61);
	private static final Color GRID = new Color(54, 57, 70);
	private static final Color TEXT = new Color(232, 232, 236);
	private static final Color MUTED = new Color(146, 149, 160);
	private static final Color GOLD = new Color(240, 176, 62);

	/** Dark text on a bright pill, which is how the clan's own calendar reads. */
	private static final Color ON_PILL = new Color(22, 22, 26);

	/**
	 * Two ways of writing a time, because almost every clan event starts on the hour and "8pm" leaves
	 * room for the name of the event, which is the part people are reading for.
	 */
	private static final DateTimeFormatter ON_THE_HOUR = DateTimeFormatter.ofPattern("ha", Locale.ENGLISH);
	private static final DateTimeFormatter AT = DateTimeFormatter.ofPattern("h:mma", Locale.ENGLISH);

	private CalendarImage()
	{
	}

	/**
	 * Draws one month.
	 *
	 * @param clanName whose calendar it is
	 * @param month    the month to draw
	 * @param events   everything the clan has on; anything outside the month is ignored
	 * @param zone     the timezone the times are shown in, which is the clan's rather than the reader's
	 */
	public static BufferedImage of(
		String clanName, YearMonth month, List<ClanEvent> events, ZoneId zone)
	{
		Map<LocalDate, List<ClanEvent>> byDay = byDay(events, month, zone);

		LocalDate first = month.atDay(1).with(DayOfWeek.MONDAY);
		LocalDate last = month.atEndOfMonth();
		int weeks = weeksBetween(first, last);

		int[] heights = new int[weeks];
		int gridHeight = 0;

		for (int week = 0; week < weeks; week++)
		{
			int most = 0;
			for (int day = 0; day < 7; day++)
			{
				List<ClanEvent> on = byDay.get(first.plusDays(week * 7L + day));
				most = Math.max(most, on == null ? 0 : Math.min(on.size(), MOST_PILLS + 1));
			}

			heights[week] = Math.max(MIN_CELL, 56 + most * (PILL + PILL_GAP));
			gridHeight += heights[week];
		}

		int height = HEADER + WEEKDAYS + gridHeight + LEGEND;
		BufferedImage image = new BufferedImage(WIDTH, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = image.createGraphics();

		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setRenderingHint(
			RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		graphics.setColor(BACKGROUND);
		graphics.fillRect(0, 0, WIDTH, height);

		header(graphics, clanName, month);
		weekdays(graphics);
		grid(graphics, first, month, byDay, heights, zone);
		legend(graphics, byDay, height);

		graphics.dispose();
		return image;
	}

	private static void header(Graphics2D graphics, String clanName, YearMonth month)
	{
		graphics.setFont(new Font("SansSerif", Font.BOLD, 46));
		graphics.setColor(GOLD);
		graphics.drawString(clanName.toUpperCase(Locale.ENGLISH), MARGIN, MARGIN + 46);

		graphics.setFont(new Font("SansSerif", Font.BOLD, 32));
		graphics.setColor(TEXT);
		graphics.drawString(
			month.getMonth().getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH)
				.toUpperCase(Locale.ENGLISH) + " " + month.getYear(),
			MARGIN, MARGIN + 96);

		graphics.setColor(GRID);
		graphics.setStroke(new BasicStroke(2));
		graphics.drawLine(MARGIN, HEADER - 26, WIDTH - MARGIN, HEADER - 26);
	}

	private static void weekdays(Graphics2D graphics)
	{
		graphics.setFont(new Font("SansSerif", Font.BOLD, 17));
		graphics.setColor(MUTED);

		int column = (WIDTH - MARGIN * 2) / 7;
		String[] days = {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"};

		for (int i = 0; i < days.length; i++)
		{
			graphics.drawString(days[i], MARGIN + i * column + 10, HEADER + 22);
		}
	}

	private static void grid(
		Graphics2D graphics,
		LocalDate first,
		YearMonth month,
		Map<LocalDate, List<ClanEvent>> byDay,
		int[] heights,
		ZoneId zone)
	{
		int column = (WIDTH - MARGIN * 2) / 7;
		int y = HEADER + WEEKDAYS;
		LocalDate today = LocalDate.now(zone);

		for (int week = 0; week < heights.length; week++)
		{
			for (int day = 0; day < 7; day++)
			{
				LocalDate date = first.plusDays(week * 7L + day);
				int x = MARGIN + day * column;

				boolean thisMonth = YearMonth.from(date).equals(month);

				graphics.setColor(date.equals(today) ? TODAY : thisMonth ? CELL : CELL_OUTSIDE);
				graphics.fillRoundRect(x + 3, y + 3, column - 6, heights[week] - 6, 10, 10);

				graphics.setColor(GRID);
				graphics.setStroke(new BasicStroke(1));
				graphics.drawRoundRect(x + 3, y + 3, column - 6, heights[week] - 6, 10, 10);

				graphics.setFont(new Font("SansSerif", Font.BOLD, 20));
				graphics.setColor(thisMonth ? TEXT : MUTED.darker());
				graphics.drawString(String.valueOf(date.getDayOfMonth()), x + 14, y + 30);

				pills(graphics, byDay.get(date), x, y, column, zone);
			}

			y += heights[week];
		}
	}

	private static void pills(
		Graphics2D graphics, List<ClanEvent> on, int x, int y, int column, ZoneId zone)
	{
		if (on == null || on.isEmpty())
		{
			return;
		}

		int top = y + 44;
		int shown = Math.min(on.size(), on.size() > MOST_PILLS ? MOST_PILLS - 1 : MOST_PILLS);

		for (int i = 0; i < shown; i++)
		{
			ClanEvent event = on.get(i);
			EventCategory category = event.category();

			graphics.setColor(category.getColour());
			graphics.fillRoundRect(x + 8, top, column - 20, PILL, PILL, PILL);

			java.time.ZonedDateTime starts = Instant.ofEpochMilli(event.getStartsAt()).atZone(zone);
			String when = starts.format(starts.getMinute() == 0 ? ON_THE_HOUR : AT)
				.toLowerCase(Locale.ENGLISH);

			// The time in a lighter weight and the name in bold, so the eye goes to what the event is
			// rather than to a wall of eight o'clocks.
			graphics.setColor(ON_PILL);
			graphics.setFont(new Font("SansSerif", Font.PLAIN, 13));
			graphics.drawString(when, x + 18, top + 20);

			int after = 18 + graphics.getFontMetrics().stringWidth(when) + 7;

			graphics.setFont(new Font("SansSerif", Font.BOLD, 14));
			graphics.drawString(
				fit(graphics, event.getName(), column - 20 - after - 8), x + after, top + 20);

			top += PILL + PILL_GAP;
		}

		if (on.size() > shown)
		{
			graphics.setFont(new Font("SansSerif", Font.BOLD, 13));
			graphics.setColor(MUTED);
			graphics.drawString("+ " + (on.size() - shown) + " more", x + 16, top + 18);
		}
	}

	/**
	 * The colours used this month and what they mean, because a picture full of coloured pills is not
	 * self-explanatory to somebody who has not seen the plugin.
	 */
	private static void legend(Graphics2D graphics, Map<LocalDate, List<ClanEvent>> byDay, int height)
	{
		Set<EventCategory> used = new LinkedHashSet<>();
		for (List<ClanEvent> on : byDay.values())
		{
			for (ClanEvent event : on)
			{
				used.add(event.category());
			}
		}

		int x = MARGIN;
		int y = height - LEGEND + 30;

		graphics.setFont(new Font("SansSerif", Font.BOLD, 15));

		for (EventCategory category : used)
		{
			graphics.setColor(category.getColour());
			graphics.fillRoundRect(x, y - 13, 26, 18, 9, 9);

			graphics.setColor(MUTED);
			String label = category.getLabel();
			graphics.drawString(label, x + 34, y + 1);

			x += 34 + graphics.getFontMetrics().stringWidth(label) + 26;
		}

		graphics.setFont(new Font("SansSerif", Font.PLAIN, 13));
		graphics.setColor(MUTED.darker());
		graphics.drawString("Made by Clan Suite", MARGIN, height - 22);
	}

	/**
	 * Everything on in the month, by the day it starts, each day in the order the events run.
	 * <p>
	 * By the clan's timezone rather than the reader's: an event at eight in the evening in Brisbane is
	 * on that Tuesday for everybody, however early it is where they are reading this.
	 */
	static Map<LocalDate, List<ClanEvent>> byDay(
		List<ClanEvent> events, YearMonth month, ZoneId zone)
	{
		Map<LocalDate, List<ClanEvent>> byDay = new TreeMap<>();

		for (ClanEvent event : events)
		{
			if (event.isDraft())
			{
				// Not on the calendar yet, by definition.
				continue;
			}

			LocalDate day = Instant.ofEpochMilli(event.getStartsAt()).atZone(zone).toLocalDate();
			if (!YearMonth.from(day).equals(month))
			{
				continue;
			}

			byDay.computeIfAbsent(day, at -> new ArrayList<>()).add(event);
		}

		for (List<ClanEvent> on : byDay.values())
		{
			on.sort((a, b) -> Long.compare(a.getStartsAt(), b.getStartsAt()));
		}

		return byDay;
	}

	/** How many Monday-to-Sunday rows are needed to cover the month. */
	static int weeksBetween(LocalDate firstMonday, LocalDate lastDay)
	{
		int days = (int) (lastDay.toEpochDay() - firstMonday.toEpochDay()) + 1;
		return (days + 6) / 7;
	}

	/** As much of the text as fits, with an ellipsis where it does not. */
	private static String fit(Graphics2D graphics, String text, int width)
	{
		if (graphics.getFontMetrics().stringWidth(text) <= width)
		{
			return text;
		}

		String shortened = text;
		while (shortened.length() > 1
			&& graphics.getFontMetrics().stringWidth(shortened + "…") > width)
		{
			shortened = shortened.substring(0, shortened.length() - 1);
		}

		return shortened.trim() + "…";
	}
}
