package com.deathrecap;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(DeathRecapConfig.GROUP)
public interface DeathRecapConfig extends Config
{
	String GROUP = "death-recap";

	@ConfigSection(
		name = "Screenshot & replay",
		description = "Save a screenshot and an animated replay with each recap",
		position = 10
	)
	String MEDIA = "media";

	@Range(min = 5, max = 60)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "recapSeconds",
		name = "Recap length",
		description = "How far back before a death to record",
		position = 1
	)
	default int recapSeconds()
	{
		return 15;
	}

	@ConfigItem(
		keyName = "showOverlay",
		name = "Show on-screen recap",
		description = "Show a short recap on screen after you die",
		position = 2
	)
	default boolean showOverlay()
	{
		return true;
	}

	@Range(min = 3, max = 120)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "overlaySeconds",
		name = "On-screen duration",
		description = "How long the on-screen recap stays up",
		position = 3
	)
	default int overlaySeconds()
	{
		return 15;
	}

	@ConfigItem(
		keyName = "chatSummary",
		name = "Chat summary",
		description = "Post a one-line summary in chat after you die",
		position = 4
	)
	default boolean chatSummary()
	{
		return true;
	}

	@ConfigItem(
		keyName = "recordAttacks",
		name = "Record attack animations",
		description = "Include attackers' attack animations and incoming projectiles in the timeline",
		position = 5
	)
	default boolean recordAttacks()
	{
		return true;
	}

	@ConfigItem(
		keyName = "saveToDisk",
		name = "Save recaps",
		description = "Keep recaps between sessions",
		position = 6
	)
	default boolean saveToDisk()
	{
		return true;
	}

	@Range(min = 1, max = 200)
	@ConfigItem(
		keyName = "maxSaved",
		name = "Recaps to keep",
		description = "Older recaps are removed once this many are stored",
		position = 7
	)
	default int maxSaved()
	{
		return 25;
	}

	@ConfigItem(
		keyName = "closeCalls",
		name = "Record close calls",
		description = "Save a recap when a hit takes you very low and you survive. It's made after the fight"
			+ " calms down; nothing is shown while you're in combat.",
		position = 8
	)
	default boolean closeCalls()
	{
		return true;
	}

	@Range(min = 5, max = 50)
	@Units(Units.PERCENT)
	@ConfigItem(
		keyName = "closeCallPercent",
		name = "Close call below",
		description = "Counts as a close call when a hit takes your HP to this share of your maximum or lower",
		position = 9
	)
	default int closeCallPercent()
	{
		return 15;
	}

	@ConfigItem(
		keyName = "shareCardLayout",
		name = "Share card layout",
		description = "The layout listed first under the Share card button. Wide: screenshot with the recap below."
			+ " Side panel: recap beside the screenshot. Tall: stacked, for phones. Recap only: no screenshot.",
		section = MEDIA,
		position = 15
	)
	default ShareCardLayout shareCardLayout()
	{
		return ShareCardLayout.WIDE;
	}

	@ConfigItem(
		keyName = "saveScreenshot",
		name = "Screenshot on death",
		description = "Save a screenshot of the moment you die with the recap. Needs 'Save recaps'.",
		section = MEDIA,
		position = 11
	)
	default boolean saveScreenshot()
	{
		return true;
	}

	@ConfigItem(
		keyName = "recordReplay",
		name = "Death replay (GIF)",
		description = "Keep a rolling buffer of the last few seconds at full game size and save it as an animated GIF"
			+ " when you die. Uses about 20-80 MB of memory and some CPU on a background thread. Needs 'Save recaps'.",
		section = MEDIA,
		position = 12
	)
	default boolean recordReplay()
	{
		return false;
	}

	@Range(min = 3, max = 15)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "replaySeconds",
		name = "Replay length",
		description = "Seconds of gameplay before the death to include; about 2 more seconds after it are added",
		section = MEDIA,
		position = 13
	)
	default int replaySeconds()
	{
		return 8;
	}

	@Range(min = 2, max = 10)
	@ConfigItem(
		keyName = "replayFps",
		name = "Replay frame rate",
		description = "Frames per second captured for the replay",
		section = MEDIA,
		position = 14
	)
	default int replayFps()
	{
		return 6;
	}

	@ConfigItem(
		keyName = "clipHotkey",
		name = "Save clip hotkey",
		description = "Saves the last few seconds as a recap, with a replay, without dying. Needs Save recaps and Death replay on.",
		section = MEDIA,
		position = 15
	)
	default Keybind clipHotkey()
	{
		return Keybind.NOT_SET;
	}
}
