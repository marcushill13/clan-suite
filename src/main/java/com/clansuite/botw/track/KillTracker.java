package com.clansuite.botw.track;

import com.clansuite.ClanSuiteConfig;
import com.clansuite.botw.data.BossDrops;
import com.clansuite.botw.data.Challenge;
import com.clansuite.capture.Screenshotter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.ItemComposition;
import net.runelite.api.NPC;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.http.api.loottracker.LootRecordType;

/**
 * Watches for kills of whichever boss the joined challenges are about.
 * <p>
 * Two events are listened for because one is not enough. {@link NpcLootReceived} fires when a monster
 * dies and drops something on the floor, which covers every ordinary boss and needs nothing else
 * switched on. The loot tracker's {@link LootReceived} covers the ones that pay out through a chest
 * instead — raids, Tombs of Amascut, the Nightmare — where nothing ever dies at your feet. Listening
 * only to the first would silently ignore a raid; only to the second would require the Loot Tracker
 * plugin to be enabled, and would miss the plain cases if it were not.
 * <p>
 * A pet arrives by neither. The game never drops one — it hands it straight to the player and says so
 * in the chat, so a plugin reading only loot events sees the rarest thing in the game happen and
 * records nothing. That is what {@link #onChatMessage} is for.
 * <p>
 * The two loot events overlap, and that overlap has to be cut out. The Loot Tracker takes every
 * {@link NpcLootReceived} and re-announces it as a {@link LootReceived} of type
 * {@link LootRecordType#NPC}, so an ordinary boss arrives here twice and would be counted twice. See
 * {@link #countsAsKill}.
 * <p>
 * Nothing is scored here. This decides what happened and hands it to the outbox; what it is worth is
 * the service's decision, and deliberately not the client's.
 */
@Slf4j
@Singleton
public class KillTracker
{
	/**
	 * What the game says when a pet drops, in the three places it can put one.
	 * <p>
	 * Followed home, into the last inventory slot, or — the one that started this — into the bank,
	 * when the player already had a pet out and no room to carry another. All three mean the same
	 * thing to a competition: they rolled it and they got it. Where it physically went is between
	 * them and Probita.
	 * <p>
	 * Matched as a fragment of the line rather than the whole of it, because the game punctuates these
	 * differently depending on where the pet went, and the tail of the sentence is not worth being
	 * wrong about. These are the same three the RuneLite screenshot plugin has matched for years.
	 */
	private static final List<String> PET_MESSAGES = Arrays.asList(
		"You have a funny feeling like you're being followed",
		"You feel something weird sneaking into your backpack",
		"You have a funny feeling like you would have been followed");

	/**
	 * How far apart a pet announcement and the kill it came from are allowed to be.
	 * <p>
	 * Needed because the announcement names no boss. Somebody who gets a Beaver while cutting yews on
	 * a Vorkath week has not got Vorki, and without this they would be paid for it.
	 * <p>
	 * Generous, and deliberately counted in both directions: at the raids the pet is announced when the
	 * raid ends but the kill is not recorded until the chest is opened, which is minutes of walking
	 * later at Tombs of Amascut. A minute covers the ordinary case in both orders; anything further out
	 * than that is not the same event.
	 */
	private static final long PET_WINDOW_MS = 60_000;

	private final Outbox outbox;
	private final ChallengeStore challenges;
	private final ItemManager itemManager;
	private final Screenshotter screenshotter;
	private final BossDrops bossDrops;
	private final ClanSuiteConfig config;

	/** When each challenge's boss was last killed, so a pet can be tied back to a kill. */
	private final Map<String, Long> lastKillAt = new HashMap<>();

	/** When a pet was last announced, or zero. Kept because the kill can be recorded afterwards. */
	private long petDroppedAt;

	/**
	 * How many pets have been announced this session, which is how one announcement is told from the
	 * next.
	 * <p>
	 * A count rather than the time it happened. Two announcements can share a millisecond, and keying
	 * on the clock quietly made the second of them the first one over again — a player who was owed two
	 * pets was paid for one.
	 */
	private long petAnnouncements;

	/** Which announcement each challenge has already been paid for, so it is not paid twice. */
	private final Map<String, Long> petPaidFor = new HashMap<>();

	@Inject
	private KillTracker(
		Outbox outbox,
		ChallengeStore challenges,
		ItemManager itemManager,
		Screenshotter screenshotter,
		BossDrops bossDrops,
		ClanSuiteConfig config)
	{
		this.outbox = outbox;
		this.challenges = challenges;
		this.itemManager = itemManager;
		this.screenshotter = screenshotter;
		this.bossDrops = bossDrops;
		this.config = config;
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		NPC npc = event.getNpc();
		if (npc == null || npc.getName() == null)
		{
			return;
		}

		record(npc.getName(), event.getItems());
	}

	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		if (event.getName() == null || !countsAsKill(event.getType()))
		{
			return;
		}

		// A chest can pay out for several kills at once; the amount says how many.
		record(event.getName(), event.getItems(), Math.max(1, event.getAmount()));
	}

	/**
	 * The pet drops, which arrive as a line of chat and nothing else.
	 * <p>
	 * A pet is never part of the loot. It is handed to the player directly — walking behind them, in
	 * their inventory, or waiting at Probita when they had no room for either — and the only trace of
	 * it is the message. So the two loot listeners above cannot see one, and never could: a clan member
	 * with a full inventory and a pet already out banked theirs and scored nothing, and so would anyone
	 * whose hands were empty.
	 * <p>
	 * The message does not say which pet, so the challenge says it instead; see
	 * {@link BossDrops#petIn}. Nor does it say which boss, which is what the window is for.
	 */
	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}

		if (!isPetMessage(event.getMessage()))
		{
			return;
		}

		long now = System.currentTimeMillis();
		petDroppedAt = now;
		petAnnouncements++;

		List<PendingEvent> recorded = new ArrayList<>();

		for (Challenge challenge : challenges.joined())
		{
			Long killed = lastKillAt.get(challenge.getCode());

			// A kill this pet could have come from. Where there is none yet the announcement is kept
			// instead, and the next kill picks it up — the raids announce the pet before they pay out.
			if (challenge.isRunning(now) && killed != null && fromTheSameKill(now, killed))
			{
				recordPet(challenge, recorded);
			}
		}

		submit(recorded, "a pet");
	}

	/**
	 * Whether a pet announced at one time and a kill recorded at another are the same event.
	 * <p>
	 * Either order counts. The pet normally lands first — it is announced as the boss dies, while the
	 * kill is not recorded until the loot is worked out — but at the raids the gap runs the other way
	 * and is far longer. A zero on either side is nothing having happened, and pairs with nothing.
	 */
	static boolean fromTheSameKill(long petAt, long killAt)
	{
		return petAt > 0 && killAt > 0 && Math.abs(petAt - killAt) <= PET_WINDOW_MS;
	}

	/**
	 * Whether this line of chat is the game announcing a pet.
	 */
	static boolean isPetMessage(String message)
	{
		if (message == null)
		{
			return false;
		}

		for (String pet : PET_MESSAGES)
		{
			if (message.contains(pet))
			{
				return true;
			}
		}

		return false;
	}

	/**
	 * Whether a {@link LootReceived} is a kill in its own right, or one already counted elsewhere.
	 * <p>
	 * Only {@link LootRecordType#EVENT} is. That is the chest payouts — raids, Tombs of Amascut — which
	 * are the reason this event is listened to at all.
	 * <p>
	 * {@link LootRecordType#NPC} is deliberately refused. The Loot Tracker re-announces every
	 * {@link NpcLootReceived} under this event as well, so accepting it would count every ordinary boss
	 * twice over for anyone with that plugin switched on, which is nearly everyone. The first listener
	 * has already had that kill. {@link LootRecordType#PLAYER} and {@link LootRecordType#PICKPOCKET} are
	 * refused because killing someone in the Wilderness or robbing them is not a boss kill.
	 */
	static boolean countsAsKill(LootRecordType type)
	{
		return type == LootRecordType.EVENT;
	}

	private void record(String source, Collection<ItemStack> items)
	{
		record(source, items, 1);
	}

	/**
	 * @param source the boss's name as the game gave it
	 * @param items  everything that dropped
	 * @param kills  how many kills this payout covers, which is more than one for a chest
	 */
	private void record(String source, Collection<ItemStack> items, int kills)
	{
		long now = System.currentTimeMillis();
		List<PendingEvent> recorded = new ArrayList<>();

		// One kill can matter to more than one challenge at a time, and the player should not have to
		// choose. Each joined challenge is considered on its own.
		for (Challenge challenge : challenges.joined())
		{
			if (!matches(challenge.getBoss(), source) || !challenge.isRunning(now))
			{
				continue;
			}

			lastKillAt.put(challenge.getCode(), now);

			for (int i = 0; i < kills; i++)
			{
				recorded.add(PendingEvent.kill(challenge.getCode(), now));
			}

			if (items != null)
			{
				for (ItemStack stack : items)
				{
					String name = itemName(stack);
					if (name != null && challenge.counts(name))
					{
						recorded.add(scoringDrop(challenge, name, stack.getQuantity(), now));
					}
				}
			}

			// A pet announced just before this kill was recorded. That is the ordinary way round at the
			// raids: the pet is announced when the raid ends, the kill when the chest is opened.
			if (fromTheSameKill(petDroppedAt, now))
			{
				recordPet(challenge, recorded);
			}
		}

		submit(recorded, source);
	}

	/**
	 * Records the pet this challenge counts, if it counts one and has not already been paid for this
	 * announcement.
	 * <p>
	 * The guard is what lets a pet be recorded from either side of the window without being recorded
	 * twice: whichever of the announcement and the kill arrives second finds the challenge already
	 * paid and leaves it alone.
	 * <p>
	 * Nothing here cares whether the player owned that pet already. A duplicate is the same roll of the
	 * same odds, it is announced in the same words, and it scores the same — as a second visage would.
	 */
	private void recordPet(Challenge challenge, List<PendingEvent> into)
	{
		Long paid = petPaidFor.get(challenge.getCode());
		if (paid != null && paid.longValue() == petAnnouncements)
		{
			return;
		}

		String pet = bossDrops.petIn(challenge);
		if (pet == null)
		{
			// A challenge that scores no pet, or lists two and cannot say which. Neither is an error:
			// the creator sets what counts, and a pet they left off is a pet they did not want.
			log.debug("A pet dropped, but {} does not say what it would be worth", challenge.getCode());
			return;
		}

		petPaidFor.put(challenge.getCode(), petAnnouncements);
		into.add(scoringDrop(challenge, pet, 1, petDroppedAt));
	}

	/**
	 * A drop worth points, with the picture that proves it.
	 * <p>
	 * Screenshots are only for drops that actually score. One per kill would bury the ones worth
	 * keeping and fill someone's disk in a week.
	 */
	private PendingEvent scoringDrop(Challenge challenge, String itemName, int quantity, long occurredAt)
	{
		// Keyed to the event, so the creator's copy is tied to the drop it is evidence of rather than to
		// a timestamp that has to be matched up by eye.
		PendingEvent drop = PendingEvent.drop(challenge.getCode(), itemName, quantity, occurredAt);

		if (config.screenshotDrops())
		{
			// The code is only passed when sharing is on. Without it the picture is saved locally and
			// goes nowhere, which is what that setting means.
			screenshotter.capture(
				challenge.getName(),
				itemName,
				config.shareScreenshots() ? challenge.getCode() : null,
				config.shareScreenshots() ? drop.getId() : null);
		}

		return drop;
	}

	private void submit(List<PendingEvent> recorded, String source)
	{
		if (!recorded.isEmpty())
		{
			log.debug("Recorded {} events from {}", recorded.size(), source);
			outbox.add(recorded);
		}
	}

	/**
	 * Whether a kill of this thing counts toward a challenge for that boss.
	 * <p>
	 * Not an exact match, because the two names come from different places. A challenge says "Dagannoth
	 * Rex" while the game may hand back a form or a level suffix, and Tombs of Amascut pays out under
	 * the raid's name rather than the boss's.
	 */
	static boolean matches(String boss, String source)
	{
		if (boss == null || source == null)
		{
			return false;
		}

		String wanted = boss.toLowerCase(Locale.ROOT).trim();
		String got = source.toLowerCase(Locale.ROOT).trim();

		return got.equals(wanted) || got.startsWith(wanted + " (") || wanted.startsWith(got + " (");
	}

	/**
	 * The item's name. Names rather than ids because that is what the creator sets the points against,
	 * and because an item can arrive under any of several ids.
	 * <p>
	 * Safe to call here: both loot events are posted on the client thread, which is the only thread
	 * allowed to read an item's composition.
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
