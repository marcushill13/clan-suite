package com.clansuite.event.calendar;

import com.clansuite.event.data.ClanEvent;
import java.awt.image.BufferedImage;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Drawing the month.
 * <p>
 * The picture itself cannot be asserted on sensibly — nobody writes a test for whether a pill is
 * pretty. What can be checked is the arithmetic underneath it, which is where the bugs would be:
 * which day an event lands on when the clan is in one timezone and the reader in another, how many
 * rows a month needs, and that a month with nothing on still draws.
 */
public class CalendarImageTest
{
	private static final ZoneId BRISBANE = ZoneId.of("Australia/Brisbane");
	private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);

	@Test
	public void aMonthNeedsAsManyRowsAsItSpans()
	{
		// September 2026 starts on a Tuesday and has 30 days: five Monday-to-Sunday rows.
		assertEquals(5, CalendarImage.weeksBetween(LocalDate.of(2026, 8, 31), LocalDate.of(2026, 9, 30)));

		// February in a year where it starts on a Monday is exactly four.
		assertEquals(4, CalendarImage.weeksBetween(LocalDate.of(2027, 2, 1), LocalDate.of(2027, 2, 28)));

		// A month that spills into a sixth row.
		assertEquals(6, CalendarImage.weeksBetween(LocalDate.of(2026, 5, 25), LocalDate.of(2026, 6, 30)));
	}

	@Test
	public void eventsLandOnTheDayTheClanRunsThemOn()
	{
		// Eight in the evening in Brisbane is ten in the morning in London, on the same day here.
		ClanEvent evening = event("Nex Mass", LocalDateTime.of(2026, 9, 18, 20, 0));

		Map<LocalDate, List<ClanEvent>> byDay =
			CalendarImage.byDay(Collections.singletonList(evening), SEPTEMBER, BRISBANE);

		assertTrue(byDay.containsKey(LocalDate.of(2026, 9, 18)));
		assertEquals(1, byDay.get(LocalDate.of(2026, 9, 18)).size());
	}

	@Test
	public void aDaysEventsAreInTheOrderTheyRun()
	{
		List<ClanEvent> events = Arrays.asList(
			event("Skribbl.io", LocalDateTime.of(2026, 9, 18, 22, 0)),
			event("Nightmare Mass", LocalDateTime.of(2026, 9, 18, 20, 0)),
			event("Mahogany Homes Mass", LocalDateTime.of(2026, 9, 18, 21, 0)));

		List<ClanEvent> on = CalendarImage.byDay(events, SEPTEMBER, BRISBANE).get(LocalDate.of(2026, 9, 18));

		assertEquals("Nightmare Mass", on.get(0).getName());
		assertEquals("Mahogany Homes Mass", on.get(1).getName());
		assertEquals("Skribbl.io", on.get(2).getName());
	}

	@Test
	public void nothingFromAnotherMonthGetsIn()
	{
		List<ClanEvent> events = Arrays.asList(
			event("Last month", LocalDateTime.of(2026, 8, 31, 20, 0)),
			event("Next month", LocalDateTime.of(2026, 10, 1, 20, 0)),
			event("This month", LocalDateTime.of(2026, 9, 15, 20, 0)));

		Map<LocalDate, List<ClanEvent>> byDay = CalendarImage.byDay(events, SEPTEMBER, BRISBANE);

		assertEquals(1, byDay.size());
		assertTrue(byDay.containsKey(LocalDate.of(2026, 9, 15)));
	}

	/** A draft is not on the calendar; that is what being a draft means. */
	@Test
	public void draftsAreNotDrawn()
	{
		ClanEvent draft = event("Still deciding", LocalDateTime.of(2026, 9, 15, 20, 0));
		draft.setStatus("draft");

		assertTrue(CalendarImage.byDay(Collections.singletonList(draft), SEPTEMBER, BRISBANE).isEmpty());
	}

	/**
	 * A cancelled event stays on the picture. The clan told everybody it was on, and a calendar that
	 * quietly loses it is how people turn up to nothing.
	 */
	@Test
	public void cancelledEventsStayOnIt()
	{
		ClanEvent cancelled = event("Called off", LocalDateTime.of(2026, 9, 15, 20, 0));
		cancelled.setStatus("cancelled");

		assertFalse(CalendarImage.byDay(Collections.singletonList(cancelled), SEPTEMBER, BRISBANE).isEmpty());
	}

	@Test
	public void aMonthDrawsWithEventsAndWithout()
	{
		BufferedImage empty = CalendarImage.of("OCE Plankers", SEPTEMBER, new ArrayList<>(), BRISBANE);
		assertTrue(empty.getWidth() > 0);
		assertTrue(empty.getHeight() > 0);

		List<ClanEvent> busy = new ArrayList<>();
		for (int day = 1; day <= 30; day++)
		{
			for (int at = 18; at <= 22; at++)
			{
				busy.add(event("Event " + day + "-" + at, LocalDateTime.of(2026, 9, day, at, 0)));
			}
		}

		BufferedImage full = CalendarImage.of("OCE Plankers", SEPTEMBER, busy, BRISBANE);

		assertEquals(empty.getWidth(), full.getWidth());
		assertTrue("a busy month needs more room than an empty one",
			full.getHeight() > empty.getHeight());
	}

	@Test
	public void aClanCalledAnythingStillGetsAFileName()
	{
		assertEquals("OCE Plankers", CalendarFile.safe("OCE Plankers"));
		assertEquals("Zamorak Zealots", CalendarFile.safe("Zamorak/Zealots"));
		assertEquals("clan", CalendarFile.safe("///"));
		assertEquals("clan", CalendarFile.safe(null));
	}

	private static ClanEvent event(String name, LocalDateTime starts)
	{
		ClanEvent event = new ClanEvent();
		event.setName(name);
		event.setCategory("pvm");
		event.setStatus("published");
		event.setTimezone(BRISBANE.getId());
		event.setStartsAt(starts.atZone(BRISBANE).toInstant().toEpochMilli());
		event.setEndsAt(event.getStartsAt() + 3 * 60 * 60 * 1000L);
		return event;
	}
}
