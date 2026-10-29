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
		name = "Recording",
		description = "What goes into a recap",
		position = 0
	)
	String RECORDING = "recording";

	@ConfigSection(
		name = "After a death",
		description = "What's shown right after you die",
		position = 10
	)
	String AFTER_DEATH = "afterDeath";

	@ConfigSection(
		name = "Screenshot & replay",
		description = "Save a screenshot and an animated replay with each recap",
		position = 20
	)
	String MEDIA = "media";

	@ConfigSection(
		name = "Sharing & export",
		description = "Share cards, MP4 export and Gielinor Wrapped",
		position = 30
	)
	String SHARING = "sharing";

	@ConfigSection(
		name = "World map",
		description = "Mark where you died on the world map",
		position = 40
	)
	String WORLD_MAP = "worldMap";

	@ConfigSection(
		name = "Storage",
		description = "Keeping recaps between sessions",
		position = 50
	)
	String STORAGE = "storage";

	@Range(min = 5, max = 60)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "recapSeconds",
		name = "Recap length",
		description = "How far back before a death to record",
		section = RECORDING,
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
		section = AFTER_DEATH,
		position = 11
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
		section = AFTER_DEATH,
		position = 12
	)
	default int overlaySeconds()
	{
		return 15;
	}

	@ConfigItem(
		keyName = "chatSummary",
		name = "Chat summary",
		description = "Post a one-line summary in chat after you die",
		section = AFTER_DEATH,
		position = 13
	)
	default boolean chatSummary()
	{
		return true;
	}

	@ConfigItem(
		keyName = "recordAttacks",
		name = "Record attack animations",
		description = "Include attackers' attack animations and incoming projectiles in the timeline",
		section = RECORDING,
		position = 2
	)
	default boolean recordAttacks()
	{
		return true;
	}

	@ConfigItem(
		keyName = "saveToDisk",
		name = "Save recaps",
		description = "Keep recaps between sessions",
		section = STORAGE,
		position = 51
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
		section = STORAGE,
		position = 52
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
		section = RECORDING,
		position = 3
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
		section = RECORDING,
		position = 4
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
		section = SHARING,
		position = 31
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
		position = 21
	)
	default boolean saveScreenshot()
	{
		return true;
	}

	@ConfigItem(
		keyName = "recordReplay",
		name = "Death replay (GIF)",
		description = "Keep a rolling buffer of the last few seconds at full game size (up to 1920 wide) and save it as an animated GIF"
			+ " when you die. Uses about 20-80 MB of memory and some CPU on a background thread. Needs 'Save recaps'.",
		section = MEDIA,
		position = 22
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
		position = 23
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
		position = 24
	)
	default int replayFps()
	{
		return 6;
	}

	@ConfigItem(
		keyName = "replaySounds",
		name = "Replay sounds",
		description = "Remember the game's sound effects during a replay and play them again when you watch it,"
			+ " at your sound effect volumes. MP4 exports stay silent.",
		section = MEDIA,
		position = 25
	)
	default boolean replaySounds()
	{
		return true;
	}

	@ConfigItem(
		keyName = "clipHotkey",
		name = "Save clip hotkey",
		description = "Saves the last few seconds as a recap, with a replay, without dying. Needs Save recaps and Death replay on.",
		section = MEDIA,
		position = 26
	)
	default Keybind clipHotkey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "shareWithWrapped",
		name = "Share with Gielinor Wrapped",
		description = "If the Gielinor Wrapped plugin is installed, send it each death (share card in your layout above,"
			+ " screenshot and replay) so it can log every death and put them in its recap videos.",
		section = SHARING,
		position = 34
	)
	default boolean shareWithWrapped()
	{
		return true;
	}

	@ConfigItem(
		keyName = "videoSize",
		name = "MP4 size",
		description = "Frame size of replays exported as MP4. Smaller files are quicker to make and to upload.",
		section = SHARING,
		position = 32
	)
	default VideoSize videoSize()
	{
		return VideoSize.HD;
	}

	@ConfigItem(
		keyName = "deathMap",
		name = "Show deaths on the map",
		description = "Put a marker on the world map where each death happened, coloured by killer."
			+ " Right-click a marker for its recap.",
		section = WORLD_MAP,
		position = 41
	)
	default boolean deathMap()
	{
		return true;
	}

	@ConfigItem(
		keyName = "mapCloseCalls",
		name = "Include close calls",
		description = "Mark close calls on the world map too",
		section = WORLD_MAP,
		position = 42
	)
	default boolean mapCloseCalls()
	{
		return false;
	}
}
