package com.deathrecap;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

class DeathRecapOverlay extends OverlayPanel
{
	private static final int RECENT_HITS = 5;
	private static final int FINDINGS = 3;
	private static final int LOST_ITEMS = 3;

	private final DeathRecapPlugin plugin;
	/** The lost-items estimate for {@link #itemsFor}, worked out once rather than every frame. */
	private DeathRecord itemsFor;
	private ItemsOnDeath.Result items;

	@Inject
	DeathRecapOverlay(DeathRecapPlugin plugin)
	{
		super(plugin);
		this.plugin = plugin;
		setPosition(OverlayPosition.TOP_LEFT);
		panelComponent.setPreferredSize(new Dimension(220, 0));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		DeathRecord record = plugin.getOverlayRecord();
		RecapAnalyzer.Summary summary = plugin.getOverlaySummary();
		if (record == null || summary == null)
		{
			return null;
		}
		if (Instant.now().isAfter(plugin.getOverlayUntil()))
		{
			plugin.hideOverlay();
			return null;
		}

		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Killed by " + RecapAnalyzer.label(record, summary.getKiller()))
			.color(new Color(0xFF, 0x60, 0x60))
			.build());
		RecapEvent blow = summary.getKillingBlow();
		if (blow != null && blow.getWeapon() != null)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("With")
				.right(blow.getWeapon())
				.rightColor(Color.WHITE)
				.build());
		}

		if (record.getState() != null)
		{
			if (itemsFor != record)
			{
				items = ItemsOnDeath.compute(record.getState());
				itemsFor = record;
			}
			panelComponent.getChildren().add(LineComponent.builder()
				.left(items.getAtRisk().isEmpty() ? "Nothing lost" : "Lost (estimate)")
				.right(items.getAtRisk().isEmpty() ? "" : ItemsOnDeath.gp(items.getRiskValue()) + " gp")
				.leftColor(HpGraph.FOOD)
				.rightColor(HpGraph.FOOD)
				.build());
			items.getAtRisk().stream().limit(LOST_ITEMS).forEach(s ->
				panelComponent.getChildren().add(LineComponent.builder()
					.left("  " + RecapText.stack(s))
					.leftColor(Color.LIGHT_GRAY)
					.right(s.getPrice() > 0 ? ItemsOnDeath.gp(s.getPrice() * s.getQuantity()) : "")
					.build()));
		}

		List<RecapEvent> hits = record.getEvents().stream()
			.filter(RecapEvent::isDamage)
			.sorted(Comparator.comparingInt(RecapEvent::getTick).reversed())
			.limit(RECENT_HITS)
			.collect(Collectors.toList());
		for (RecapEvent e : hits)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(RecapText.offset(e.getTick(), record.getDeathTick()) + " " + e.getSource())
				.right(String.valueOf(e.getAmount()))
				.rightColor(e.getType() == RecapEvent.Type.HIT ? Color.WHITE : new Color(0x6C, 0xC0, 0x4A))
				.build());
		}

		// The first finding repeats the killing blow shown above, so skip it.
		summary.getFindings().stream().skip(1).limit(FINDINGS).forEach(f ->
			panelComponent.getChildren().add(LineComponent.builder()
				.left(f)
				.leftColor(Color.LIGHT_GRAY)
				.build()));

		return super.render(graphics);
	}
}
