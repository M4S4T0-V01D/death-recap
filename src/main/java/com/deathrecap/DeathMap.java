package com.deathrecap;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;

/**
 * A marker on the world map for every death (and, if wanted, close call) with a known location, coloured
 * by killer. Hovering shows who and when; right-clicking offers to open the recap.
 */
final class DeathMap
{
	private static final int SIZE = 11;
	/** The most "Open recap" entries added for a stack of markers. */
	static final int MAX_MENU = 5;

	static final class Marker extends WorldMapPoint
	{
		@Getter
		private final long timestamp;
		@Getter
		private final String killer;

		Marker(WorldPoint point, BufferedImage image, long timestamp, String killer)
		{
			super(point, image);
			this.timestamp = timestamp;
			this.killer = killer;
		}

		@Override
		public boolean equals(Object o)
		{
			return o == this;
		}

		@Override
		public int hashCode()
		{
			return System.identityHashCode(this);
		}
	}

	private final Client client;
	private final WorldMapPointManager points;
	private final WorldMapOverlay overlay;
	private final Map<String, BufferedImage> images = new HashMap<>();
	private volatile List<Marker> markers = List.of();
	/** Set when the plugin stops, so a late refresh can't put markers back. */
	private boolean closed;

	DeathMap(Client client, WorldMapPointManager points, WorldMapOverlay overlay)
	{
		this.client = client;
		this.points = points;
		this.overlay = overlay;
	}

	/** Replaces the markers with one per located entry. Any thread. */
	synchronized void show(List<StatEntry> entries, boolean closeCalls)
	{
		if (closed)
		{
			return;
		}
		List<Marker> next = new ArrayList<>();
		for (StatEntry e : entries)
		{
			if (!e.hasLocation() || e.isCloseCall() && !closeCalls)
			{
				continue;
			}
			String killer = DeathStats.name(e);
			Marker m = new Marker(new WorldPoint(e.getX(), e.getY(), e.getPlane()), image(killer, e.isCloseCall()),
				e.getTimestamp(), killer);
			m.setName("Death Recap");
			m.setTooltip(tooltip(e, killer));
			m.setSnapToEdge(false);
			m.setJumpOnClick(false);
			next.add(m);
		}
		points.removeIf(p -> p instanceof Marker);
		next.forEach(points::add);
		markers = next;
	}

	synchronized void close()
	{
		closed = true;
		clear();
	}

	synchronized void clear()
	{
		points.removeIf(p -> p instanceof Marker);
		markers = List.of();
	}

	static String tooltip(StatEntry e, String killer)
	{
		String what = e.isCloseCall() ? "Close call: " : "Died to ";
		return what + killer + "<br>" + RecapText.date(e.getTimestamp())
			+ (e.getLost() > 0 && !e.isCloseCall() ? " · " + ItemsOnDeath.gp(e.getLost()) + " gp" : "");
	}

	/** The markers under the mouse while the world map is open, newest first. Client thread. */
	List<Marker> hovered()
	{
		List<Marker> current = markers;
		Widget map = client.getWidget(InterfaceID.Worldmap.MAP_CONTAINER);
		if (current.isEmpty() || map == null || map.isHidden())
		{
			return List.of();
		}
		Point mouse = client.getMouseCanvasPosition();
		List<Marker> hit = new ArrayList<>();
		for (Marker m : current)
		{
			Point p = overlay.mapWorldPointToGraphicsPoint(m.getWorldPoint());
			if (p != null && Math.abs(p.getX() - mouse.getX()) <= SIZE / 2 + 1 && Math.abs(p.getY() - mouse.getY()) <= SIZE / 2 + 1
				&& map.getBounds().contains(mouse.getX(), mouse.getY()))
			{
				hit.add(m);
			}
		}
		hit.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
		return hit;
	}

	private BufferedImage image(String killer, boolean hollow)
	{
		return images.computeIfAbsent(killer + (hollow ? "/ring" : ""), k -> draw(DeathStats.colour(killer), hollow));
	}

	/** A dot in the killer's colour with a dark rim; close calls are a ring. */
	static BufferedImage draw(Color colour, boolean hollow)
	{
		BufferedImage img = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setColor(Color.BLACK);
		g.fillOval(0, 0, SIZE - 1, SIZE - 1);
		g.setColor(colour);
		if (hollow)
		{
			g.setStroke(new BasicStroke(2.5f));
			g.drawOval(2, 2, SIZE - 5, SIZE - 5);
		}
		else
		{
			g.fillOval(1, 1, SIZE - 3, SIZE - 3);
		}
		g.dispose();
		return img;
	}
}
