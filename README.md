# Clan Suite

Run your clan's events from inside RuneLite, without the admin.

Someone sets up an event and shares a code. Everyone else pastes the code in. From then on the plugin
counts what the event is about by itself and keeps a leaderboard everyone can see. No screenshots to
chase, no spreadsheet to maintain, no password to remember.

This grew out of the Boss of the Week plugin, which is now the first event Clan Suite knows how to
run. The rest — raid nights, skilling competitions, minigame events, socials — is being built on the
same foundation, one event type at a time. What is written below is Boss of the Week, and it works
exactly as it always has.

## Where this came from, and what it replaces

Clan Suite reads the challenges and unsent kills left behind by the Boss of the Week plugin, so
switching over keeps everything you had joined. Run one or the other, though: two plugins both
watching your kills would report each one twice, and the service has no way to tell that the second
report was the same kill.

# Clans

A clan is what everything else belongs to. Make one and you own it; the people in it have ranks, and
what somebody may do is decided by their rank rather than by who made what.

**Ranks**, named the way the game names them. Owner and deputy owner run the clan itself — settings,
and who holds which rank. Administrators run the events and the membership. Moderators check results.
Members take part and read everything.

**The hub** lists clans that have chosen to be listed, with how full each one is against the game's own
cap of five hundred. Apply from there and the clan's staff decide. A clan can hide itself from the list
and still recruit by passing its code around, and can close applications without hiding.

Two things worth being straight about. Nothing here can prove that somebody is who they say they are —
the plugin reports the name of whoever is logged in, and a modified client could report anything. That
is why joining is by application: a human who knows the clan approves each one, which is the check a
clan already makes. And what a rank allows is enforced by the service, not by the plugin. The panel
hides buttons you could not use, but hiding a button is not a permission — every action is checked
again where it cannot be edited.

# Boss of the Week

## Setting one up

1. **Create a challenge**, name it, and pick the start and end times in your own timezone
2. Search for the boss. Its uniques and its pet fill themselves in, each with a points box and an X if
   you do not want it counted
3. Add anything else by searching for it
4. Say what a kill count is worth "every 10 kills = 1 point", or whatever you like
5. Share the code it gives you

Joining is that code and a button.

## Players who cannot run the plugin

Mobile has no plugins, so those kills can never be counted. They do not have to sit the competition
out: the creator can add someone to the leaderboard by hand and set their points from the screenshots
they send in, the way the clan already does it. Those rows are marked *Manual / Mobile*, so a total
that was typed in is never passed off as one that was counted.

The same edit works on everyone else, for docking points or fixing a mistake. A tracked player's own
kills keep counting underneath it — the change is kept alongside their score rather than replacing
it, so it does not come undone at their next kill.

## What you see

A countdown to the start, which becomes a countdown to the end. The boss, the full points list, and a
leaderboard that keeps itself up to date. Your own points, and what they are made of.

## Pets

Pets are counted, and it does not matter where yours ends up. A pet is the one drop the game never
puts on the floor — it hands it to you directly, so it is not part of the loot and cannot be read like
the rest of it. The plugin reads the message instead, which covers all three endings: following you
home, into your inventory, or straight to the bank because your inventory was full and you already had
one out.

The message says a pet dropped without saying which, so the plugin takes it to be the pet your
challenge counts, and only when a kill of that boss happened within the minute. A Beaver on a Vorkath
week is not Vorki.

Duplicates count. The fiftieth Vorki scores what the first did, the same as a second visage would.

## What this sends, and to whom

The plugin talks to a small service so that everyone in a challenge can see the same leaderboard. A
RuneLite plugin only ever sees its own client, so there is no way to do that locally.

**Sent when you join a challenge, and only for challenges you have joined:**

- your RuneScape name, so the leaderboard has something to call you
- each kill of that challenge's boss, and any of its drops that the challenge counts
- a screenshot of each scoring drop, if you leave that setting on

**Not sent:** anything about accounts, anything from challenges you have not joined, anything at all
before you join one, and any kill of any other monster.

**Screenshots.** A scoring drop is photographed and saved on your own machine, under
`.runelite/botw/screenshots`, in a folder named after the challenge. A downscaled copy is sent to
whoever runs the challenge so they can verify it — this is what the clan would otherwise be asking
you to post in Discord. Only the creator can see them; other participants cannot. The full-size
original never leaves your machine. Both behaviours have their own setting and can be turned off.

**Screenshots are not kept for ever.** The shared copies are deleted a month after a challenge ends,
and deleting a challenge removes them straight away. Results are kept — an old leaderboard can still
be looked up — so it is only the pictures that expire, and the creator can export them as a zip at
any point before then.

**Points are worked out on the server**, not here. The plugin reports that a pet dropped; what a pet
is worth is the challenge's business.

The service is a Cloudflare Worker, and its source is in `backend/` in this repository. A clan that
would rather run its own can deploy it and change the address in the plugin's settings.

## How the code is laid out

- `com.clansuite` — the plugin itself, its settings, and where files are written
- `com.clansuite.ui` — the sidebar and the shared look: colours, fonts, cards, buttons
- `com.clansuite.capture` — screenshots, which every event type will want
- `com.clansuite.clan` — clans, ranks, the hub and applications
- `com.clansuite.botw` — Boss of the Week: its data, its tracker, its screens, its service calls

Anything under `botw` belongs to that one event. Everything above it is meant to be shared by every
event that follows, which is why the split exists at all.

## Honest about cheating

This is trust based, in the same way that screenshots posted to Discord are trust based.

RuneLite works out what a monster dropped by watching for items appearing as it dies, which cannot
tell your loot from something you dropped at that moment. That affects the Loot Tracker too. 

The screenshots make it visible, a faked drop has no drop message and no collection log entry anmd will be shown in a screenshot.

It is not proof. It is the same evidence a clan already asks for, gathered automatically and organised
by challenge and by player.

## Data

Boss drop tables come from the [OSRS Wiki](https://oldschool.runescape.wiki), used under CC BY-NC-SA
3.0. They are read at build time by `scripts/generate-boss-drops.mjs` and bundled, so the plugin never
calls the wiki while it is running.
