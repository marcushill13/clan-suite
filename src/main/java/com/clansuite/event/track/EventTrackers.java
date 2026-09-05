package com.clansuite.event.track;

import com.clansuite.event.data.ClanEvent;
import com.clansuite.event.data.Metric;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.StatChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;

/**
 * Watches the game on behalf of whatever events this account has joined.
 * <p>
 * One tracker rather than one per event type, because the game does not have event types — it has
 * kills, drops, experience and deaths, and which of those matter is the event's business. So this
 * reports what happened to every joined event that asked for that kind of thing, and the service works
 * out what any of it was worth.
 * <p>
 * Nothing is reported for an event the player has not joined, and nothing outside the hours it runs.
 * Somebody in a clan running a raid night has not agreed to have their evening counted unless they
 * said they were coming.
 * <p>
 * Experience is the one thing that cannot be sent as it happens: a skiller gains it several times a
 * second, and a row apiece would be tens of thousands of them in an evening. It is added up here and
 * sent in a lump, which is also all the service needs — a threshold rule counts the total.
 */
@Slf4j
@Singleton
public class EventTrackers
{
	private final JoinedEvents joined;
	private final ObservationOutbox outbox;
	private final ItemManager itemManager;
	private final Client client;

	/** Experience since the last flush, by skill. Emptied by {@link #flushExperience()}. */
	private final Map<String, Integer> experience = new LinkedHashMap<>();

	/**
	 * What the client last told us each skill was at.
	 * <p>
	 * The game reports totals, not gains, and reports every skill once on login. Without this, logging
	 * in would look like earning a lifetime of experience in a second.
	 */
	private final Map<String, Integer> lastTotals = new HashMap<>();

	@Inject
	private EventTrackers(
		JoinedEvents joined, ObservationOutbox outbox, ItemManager itemManager, Client client)
	{
		this.joined = joined;
		this.outbox = outbox;
		this.itemManager = itemManager;
		this.client = client;
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		NPC npc = event.getNpc();
		if (npc == null || npc.getName() == null)
		{
			return;
		}

		long now = System.currentTimeMillis();
		List<Observation> seen = new ArrayList<>();

		record(seen, Metric.KILL, npc.getName(), 1, now);
		recordDrops(seen, event.getItems(), now);

		outbox.add(seen);
	}

	/**
	 * The chest payouts — raids, Tombs of Amascut — which are a completion as well as a kill.
	 * <p>
	 * {@link LootRecordType#NPC} is refused for the same reason the challenge tracker refuses it: the
	 * Loot Tracker re-announces every ordinary kill under this event too, and counting both would count
	 * every boss twice for anyone running that plugin, which is nearly everyone.
	 */
	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		if (event.getName() == null || event.getType() != LootRecordType.EVENT)
		{
			return;
		}

		long now = System.currentTimeMillis();
		int times = Math.max(1, event.getAmount());
		List<Observation> seen = new ArrayList<>();

		record(seen, Metric.COMPLETION, event.getName(), times, now);
		record(seen, Metric.KILL, event.getName(), times, now);
		recordDrops(seen, event.getItems(), now);

		outbox.add(seen);
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		Actor dying = event.getActor();
		if (client.getLocalPlayer() == null || dying != client.getLocalPlayer())
		{
			return;
		}

		List<Observation> seen = new ArrayList<>();
		record(seen, Metric.DEATH, null, 1, System.currentTimeMillis());
		outbox.add(seen);
	}

	/**
	 * Experience, added up rather than sent.
	 * <p>
	 * The game hands over a skill's total whenever it changes, so the gain is the difference from the
	 * last one seen. The first change for a skill in a session sets the mark and counts for nothing —
	 * otherwise every login would look like a world record.
	 */
	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		Skill skill = event.getSkill();
		if (skill == null)
		{
			return;
		}

		String name = skill.getName();
		Integer previous = lastTotals.put(name, event.getXp());

		if (previous == null || event.getXp() <= previous)
		{
			return;
		}

		synchronized (experience)
		{
			experience.merge(name, event.getXp() - previous, Integer::sum);
		}
	}

	/**
	 * Sends what has been gained since the last time. Called on a timer, and once more on the way out
	 * so that a session's last few minutes are not lost.
	 */
	public void flushExperience()
	{
		Map<String, Integer> gained;

		synchronized (experience)
		{
			if (experience.isEmpty())
			{
				return;
			}

			gained = new LinkedHashMap<>(experience);
			experience.clear();
		}

		long now = System.currentTimeMillis();
		List<Observation> seen = new ArrayList<>();

		for (Map.Entry<String, Integer> skill : gained.entrySet())
		{
			record(seen, Metric.EXPERIENCE, skill.getKey(), skill.getValue(), now);
		}

		outbox.add(seen);
	}

	/** Forgets where every skill was, so the next login sets its marks again rather than counting. */
	public void forgetTotals()
	{
		lastTotals.clear();

		synchronized (experience)
		{
			experience.clear();
		}
	}

	private void recordDrops(List<Observation> seen, Iterable<ItemStack> items, long now)
	{
		if (items == null)
		{
			return;
		}

		for (ItemStack stack : items)
		{
			String name = itemName(stack);
			if (name == null)
			{
				continue;
			}

			record(seen, Metric.DROP, name, stack.getQuantity(), now);

			// What it came to, so a loot competition can be scored and "biggest drop" can be asked.
			// Reported per item rather than per kill, because the record people care about is the item
			// that dropped rather than the evening's takings.
			long worth = (long) itemManager.getItemPrice(stack.getId()) * Math.max(1, stack.getQuantity());
			if (worth > 0)
			{
				record(seen, Metric.LOOT, name, (int) Math.min(Integer.MAX_VALUE, worth), now);
			}
		}
	}

	/**
	 * One thing that happened, written down once for every event that wants it.
	 * <p>
	 * The same kill can matter to a boss mass and a kill-count competition at the same time, and the
	 * player should not have to choose. Each event gets its own observation with its own id, so one
	 * failing to send says nothing about the others.
	 */
	private void record(List<Observation> seen, String metric, String subject, int amount, long now)
	{
		if (amount <= 0)
		{
			return;
		}

		for (ClanEvent event : joined.watching(metric, now))
		{
			seen.add(Observation.of(event.getCode(), metric, subject, amount, now));
		}
	}

	/**
	 * The item's name. Safe here: both loot events are posted on the client thread, which is the only
	 * one allowed to read an item's composition.
	 */
	private String itemName(ItemStack stack)
	{
		if (stack == null)
		{
			return null;
		}

		ItemComposition composition = itemManager.getItemComposition(stack.getId());
		return composition == null ? null : composition.getName();
	}
}
