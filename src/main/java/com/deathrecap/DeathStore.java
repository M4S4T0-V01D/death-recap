package com.deathrecap;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.util.Filepath;

/**
 * Reads and writes one JSON file of recaps per account, lifetime stats, and a media folder of screenshots, replays and share cards. Callers must run these off the client thread.
 */
@Slf4j
class DeathStore
{
	private static final Type LIST_TYPE = new TypeToken<List<DeathRecord>>()
	{
	}.getType();
	private static final Type STATS_TYPE = new TypeToken<List<StatEntry>>()
	{
	}.getType();

	private final Gson gson;
	/** Ending of a replay's full-quality frames file. */
	static final String FRAMES = ".frames";
	private final Filepath directory;

	DeathStore(Gson gson, Filepath directory)
	{
		this.gson = gson;
		this.directory = directory;
	}

	List<DeathRecord> load(long accountHash)
	{
		Filepath file = file(accountHash);
		if (!file.exists())
		{
			return new ArrayList<>();
		}
		try (Reader reader = file.openReader())
		{
			List<DeathRecord> records = gson.fromJson(reader, LIST_TYPE);
			return records != null ? records : new ArrayList<>();
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Unable to read death recaps", e);
			return new ArrayList<>();
		}
	}

	void save(long accountHash, List<DeathRecord> records)
	{
		Filepath file = file(accountHash);
		try (Writer writer = file.openWriter())
		{
			gson.toJson(records, LIST_TYPE, writer);
		}
		catch (IOException e)
		{
			log.warn("Unable to save death recaps", e);
		}
	}

	/** Lifetime stats, or null if this account has none saved yet. */
	List<StatEntry> loadStats(long accountHash)
	{
		Filepath file = statsFile(accountHash);
		if (!file.exists())
		{
			return null;
		}
		try (Reader reader = file.openReader())
		{
			List<StatEntry> stats = gson.fromJson(reader, STATS_TYPE);
			return stats != null ? stats : new ArrayList<>();
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Unable to read death stats", e);
			return new ArrayList<>();
		}
	}

	void saveStats(long accountHash, List<StatEntry> stats)
	{
		try (Writer writer = statsFile(accountHash).openWriter())
		{
			gson.toJson(stats, STATS_TYPE, writer);
		}
		catch (IOException e)
		{
			log.warn("Unable to save death stats", e);
		}
	}

	Filepath media(long accountHash, String name)
	{
		return mediaDir(accountHash).join(name);
	}

	Filepath mediaDir(long accountHash)
	{
		return directory.join("media", Long.toUnsignedString(accountHash));
	}

	void writePng(long accountHash, String name, BufferedImage image) throws IOException
	{
		mediaDir(accountHash).createDirectories();
		try (OutputStream out = media(accountHash, name).openOutputStream())
		{
			ImageIO.write(image, "png", out);
		}
	}

	void writeGif(long accountHash, String name, GifWriter.Frames frames, int[] delays) throws IOException
	{
		mediaDir(accountHash).createDirectories();
		try (OutputStream out = media(accountHash, name).openOutputStream())
		{
			GifWriter.write(frames, delays, out);
		}
	}

	/** Writes a replay's full-quality frames; see {@link ReplayFrames#writeFrames}. */
	void writeFrames(long accountHash, String name, List<byte[]> jpegs, int[] times) throws IOException
	{
		mediaDir(accountHash).createDirectories();
		try (OutputStream out = media(accountHash, name).openOutputStream())
		{
			ReplayFrames.writeFrames(out, jpegs, times);
		}
	}

	/** The name of the full-quality frames saved beside a replay GIF, or null. */
	static String framesName(String replay)
	{
		return replay == null || !replay.endsWith(".gif") ? null : replay.substring(0, replay.length() - 4) + FRAMES;
	}

	/** A replay's full-quality frames if they're still kept, otherwise its GIF. */
	byte[] readReplay(long accountHash, String replay) throws IOException
	{
		String frames = framesName(replay);
		if (frames != null && media(accountHash, frames).isFile())
		{
			try
			{
				return read(accountHash, frames);
			}
			catch (IOException e)
			{
				log.debug("Unable to read {}", frames, e);
			}
		}
		return read(accountHash, replay);
	}

	/**
	 * Keeps full-quality frames for only the newest {@code keep} replays, as each takes 10-30 MB. Older replays
	 * still have their GIFs.
	 */
	void pruneFrames(long accountHash, int keep)
	{
		Filepath dir = mediaDir(accountHash);
		if (!dir.isDirectory())
		{
			return;
		}
		try (Stream<Filepath> files = dir.walk(1))
		{
			List<Filepath> all = files.filter(f -> f.isFile() && f.getFileName().endsWith(FRAMES))
				.sorted(Comparator.comparingLong(DeathStore::timeOf).reversed())
				.collect(Collectors.toList());
			for (Filepath f : all.subList(Math.min(keep, all.size()), all.size()))
			{
				f.deleteIfExists();
			}
		}
		catch (IOException e)
		{
			log.warn("Unable to tidy replay frames", e);
		}
	}

	private static long timeOf(Filepath file)
	{
		String name = file.getFileName();
		try
		{
			return Long.parseLong(name.substring(0, name.length() - FRAMES.length()));
		}
		catch (NumberFormatException e)
		{
			return 0;
		}
	}

	byte[] read(long accountHash, String name) throws IOException
	{
		try (InputStream in = media(accountHash, name).openInputStream())
		{
			return in.readAllBytes();
		}
	}

	void deleteMedia(long accountHash, DeathRecord record)
	{
		for (String name : new String[]{record.getScreenshot(), record.getReplay(), framesName(record.getReplay()), record.getCard()})
		{
			if (name != null)
			{
				try
				{
					media(accountHash, name).deleteIfExists();
				}
				catch (IOException e)
				{
					log.warn("Unable to delete {}", name, e);
				}
			}
		}
	}

	/**
	 * Deletes media files that no saved recap refers to, such as ones left behind by a crash.
	 */
	void deleteOrphans(long accountHash, List<DeathRecord> records)
	{
		Filepath dir = mediaDir(accountHash);
		if (!dir.isDirectory())
		{
			return;
		}
		Set<String> keep = new HashSet<>();
		for (DeathRecord r : records)
		{
			keep.add(r.getScreenshot());
			keep.add(r.getReplay());
			keep.add(framesName(r.getReplay()));
			keep.add(r.getCard());
		}
		try (Stream<Filepath> files = dir.walk(1))
		{
			for (Filepath f : files.filter(Filepath::isFile).collect(Collectors.toList()))
			{
				if (!keep.contains(f.getFileName()))
				{
					f.deleteIfExists();
				}
			}
		}
		catch (IOException e)
		{
			log.warn("Unable to clean up death recap media", e);
		}
	}

	private Filepath statsFile(long accountHash)
	{
		return directory.join("stats-" + Long.toUnsignedString(accountHash) + ".json");
	}

	private Filepath file(long accountHash)
	{
		return directory.join("deaths-" + Long.toUnsignedString(accountHash) + ".json");
	}
}
