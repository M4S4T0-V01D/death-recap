package com.deathrecap;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.Component;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.ParamID;
import net.runelite.api.GameState;
import net.runelite.api.HitsplatID;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.Prayer;
import net.runelite.api.Projectile;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.AreaSoundEffectPlayed;
import net.runelite.api.kit.KitType;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.events.SoundEffectPlayed;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.game.ItemManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.util.ColorUtil;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.HotkeyListener;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Death Recap",
	description = "A tick-by-tick recap of what killed you",
	tags = {"death", "recap", "damage", "pvm", "killed"},
	internalName = "death-recap"
)
public class DeathRecapPlugin extends Plugin
{
	/** Hitsplats appear around when a projectile lands; allow this many client cycles either side. */
	private static final int PROJECTILE_EARLY_CYCLES = 15;
	private static final int PROJECTILE_LATE_CYCLES = 45;
	/** A hit within this many ticks of an attacker's animation is attributed to that attacker. */
	private static final int ATTACK_ATTRIBUTION_TICKS = 2;
	/** A close call needs a hit from a known attacker within this many ticks. */
	private static final int CLOSE_CALL_HIT_TICKS = 5;
	/** A close call is saved once no hit has landed for this many ticks... */
	private static final int CLOSE_CALL_CALM_TICKS = 10;
	/** ...or this long after it started, whichever comes first. */
	private static final int CLOSE_CALL_MAX_TICKS = 100;
	/** A death within this many ticks of a saved close call takes it back, even if HP recovered in between. */
	private static final int CLOSE_CALL_RETRACT_TICKS = 100;
	private static final Skill[] STATE_SKILLS = {Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.MAGIC,
		Skill.PRAYER, Skill.HITPOINTS};

	@Inject
	private Client client;

	@Inject
	private DeathRecapConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private DeathRecapOverlay overlay;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private Gson gson;

	@Inject
	private DrawManager drawManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ClientThread clientThread;

	@Inject
	private KeyManager keyManager;

	@Inject
	private EventBus eventBus;

	@Inject
	private WorldMapPointManager worldMapPointManager;

	@Inject
	private WorldMapOverlay worldMapOverlay;

	private volatile DeathMap deathMap;
	/** Encodes MP4s, one at a time, so a long export never holds up saving or loading. */
	private ExecutorService videoExecutor;

	/** True once Gielinor Wrapped has said hello, until it says goodbye. */
	private volatile boolean wrappedListening;

	/** How long the replay of a clip runs past the key press. */
	private static final long CLIP_AFTER_MS = 500;
	private long lastClipMillis;

	private final HotkeyListener clipListener = new HotkeyListener(() -> config.clipHotkey())
	{
		@Override
		public void hotkeyPressed()
		{
			clientThread.invoke(DeathRecapPlugin.this::saveClip);
		}
	};

	private DeathRecapPanel panel;
	private NavigationButton navButton;
	private DeathStore store;
	private DeathCapture capture;

	private final Deque<RecapEvent> events = new ArrayDeque<>();
	private final Deque<TickSnapshot> ticks = new ArrayDeque<>();
	private final Map<Actor, Integer> lastAttackTick = new WeakHashMap<>();
	private final Set<Projectile> seenProjectiles = Collections.newSetFromMap(new WeakHashMap<>());
	private final List<IncomingProjectile> incoming = new ArrayList<>();
	/** Everyone seen attacking the player, by name, with their latest combat level. */
	private final Map<String, Attacker> attackers = new LinkedHashMap<>();
	/** A death waiting for the next game tick to be finished. */
	private DeathRecord pendingDeath;
	private String lastProtection;
	private int lastPrayer = -1;
	private int lastDeathTick = -1;
	private int lastHitTick = -1;
	/** False after a close call until HP is back above half, so one fight gives one close call. */
	private boolean closeCallArmed = true;
	/** True while the client plays a replay's sound, so its sound event isn't recorded. Client thread only. */
	private boolean replayingSound;
	/** When this session started; deaths from then on count toward the session's totals. */
	private long sessionStart;

	/** Recaps for the logged-in account, newest first. Guarded by its own lock: the client, Swing and I/O threads all use it. */
	private final List<DeathRecord> records = new ArrayList<>();
	private volatile long loadedAccount = -1;
	/** Lifetime stats for the logged-in account; guarded by the {@link #records} lock. */
	private final List<StatEntry> stats = new ArrayList<>();
	/** A close call waiting for the fight to calm down; guarded by the {@link #records} lock. */
	private DeathRecord pendingCloseCall;
	/** The last saved close call and the tick it was saved, taken back if the player dies soon after. Client thread only. */
	private DeathRecord lastCloseCall;
	private int lastCloseCallTick;

	@Getter(AccessLevel.PACKAGE)
	private DeathRecord overlayRecord;
	@Getter(AccessLevel.PACKAGE)
	private RecapAnalyzer.Summary overlaySummary;
	@Getter(AccessLevel.PACKAGE)
	private Instant overlayUntil = Instant.EPOCH;

	private static final class IncomingProjectile
	{
		final String source;
		final String weapon;
		final int endCycle;

		IncomingProjectile(String source, String weapon, int endCycle)
		{
			this.source = source;
			this.weapon = weapon;
			this.endCycle = endCycle;
		}
	}

	@Override
	protected void startUp() throws Exception
	{
		sessionStart = System.currentTimeMillis();
		store = new DeathStore(gson, getPluginDirectory());
		capture = new DeathCapture(client, drawManager, config, store);
		capture.start();
		deathMap = new DeathMap(client, worldMapPointManager, worldMapOverlay);
		videoExecutor = Executors.newSingleThreadExecutor(r ->
		{
			Thread t = new Thread(r, "death-recap-video");
			t.setDaemon(true);
			return t;
		});
		panel = new DeathRecapPanel(this);
		BufferedImage icon = DeathRecapPanel.icon();
		navButton = NavigationButton.builder()
			.tooltip("Death Recap")
			.icon(icon)
			.priority(8)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		overlayManager.add(overlay);
		keyManager.registerKeyListener(clipListener);

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			loadAccount();
		}
		else
		{
			postReady(-1);
		}
	}

	@Override
	protected void shutDown() throws Exception
	{
		clientToolbar.removeNavigation(navButton);
		overlayManager.remove(overlay);
		keyManager.unregisterKeyListener(clipListener);
		capture.stop();
		deathMap.close();
		deathMap = null;
		videoExecutor.shutdownNow();
		resetBuffers();
		discardCloseCall();
		synchronized (records)
		{
			records.clear();
			stats.clear();
		}
		loadedAccount = -1;
		wrappedListening = false;
		overlayRecord = null;
		panel = null;
	}

	@Provides
	DeathRecapConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(DeathRecapConfig.class);
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			loadAccount();
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			discardCloseCall();
			resetBuffers();
			capture.clear();
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!DeathRecapConfig.GROUP.equals(event.getGroup()))
		{
			return;
		}
		if ("maxSaved".equals(event.getKey()))
		{
			trimAndSave();
		}
		else if ("deathMap".equals(event.getKey()) || "mapCloseCalls".equals(event.getKey()))
		{
			publish();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		Player local = client.getLocalPlayer();
		if (local == null)
		{
			return;
		}
		DeathRecord death = pendingDeath;
		if (death != null)
		{
			pendingDeath = null;
			finishDeath(death);
		}
		int tick = client.getTickCount();
		int prayer = client.getBoostedSkillLevel(Skill.PRAYER);
		String protection = activeProtection();

		if (lastPrayer > 0 && prayer == 0)
		{
			events.add(new RecapEvent(tick, RecapEvent.Type.PRAYER_EMPTY, 0, "Prayer", null));
		}
		if (lastPrayer >= 0 && !Objects.equals(protection, lastProtection))
		{
			if (lastProtection != null)
			{
				events.add(new RecapEvent(tick, RecapEvent.Type.PRAYER_OFF, 0, lastProtection, null));
			}
			if (protection != null)
			{
				events.add(new RecapEvent(tick, RecapEvent.Type.PRAYER_ON, 0, protection, null));
			}
		}
		lastPrayer = prayer;
		lastProtection = protection;

		int hp = client.getBoostedSkillLevel(Skill.HITPOINTS);
		int maxHp = client.getRealSkillLevel(Skill.HITPOINTS);
		ticks.add(new TickSnapshot(tick, hp, maxHp, prayer, protection));
		closeCalls(tick, hp, maxHp);
		trim(tick);
	}

	/**
	 * Notices close calls. Nothing is shown when one starts: the recap is only saved, and the chat line
	 * only sent, once the fight has calmed down.
	 */
	private void closeCalls(int tick, int hp, int maxHp)
	{
		DeathRecord pending;
		synchronized (records)
		{
			pending = pendingCloseCall;
		}
		if (pending != null)
		{
			if (tick - lastHitTick >= CLOSE_CALL_CALM_TICKS || tick - pending.getDeathTick() >= CLOSE_CALL_MAX_TICKS)
			{
				finishCloseCall(pending, tick);
			}
			return;
		}
		if (hp * 2 > maxHp)
		{
			closeCallArmed = true;
			return;
		}
		int threshold = (int) Math.ceil(maxHp * config.closeCallPercent() / 100.0);
		if (!config.closeCalls() || !closeCallArmed || hp <= 0 || hp > threshold || loadedAccount == -1)
		{
			return;
		}
		boolean recentHit = events.stream().anyMatch(e -> e.getType() == RecapEvent.Type.HIT
			&& tick - e.getTick() <= CLOSE_CALL_HIT_TICKS && !"Unknown".equals(e.getSource()));
		if (!recentHit)
		{
			return;
		}
		closeCallArmed = false;
		DeathRecord record = newRecord(tick);
		record.setCloseCall(true);
		record.setState(captureState(false));
		synchronized (records)
		{
			pendingCloseCall = record;
		}
		long hash = loadedAccount;
		capture.captureDeath(hash, record,
			(r, name, times, sounds) -> mediaSaved(hash, r, name, null, null, false),
			(r, name, times, sounds) -> mediaSaved(hash, r, name, times, sounds, true));
	}

	private void finishCloseCall(DeathRecord record, int tick)
	{
		int from = record.getDeathTick() - windowTicks();
		ticks.stream().filter(t -> t.getTick() >= from).forEach(record.getTicks()::add);
		events.stream().filter(e -> e.getTick() >= from).forEach(record.getEvents()::add);
		record.setAttackers(attackersIn(record));
		RecapAnalyzer.Summary summary = RecapAnalyzer.analyze(record);
		record.setKiller(summary.getKiller());
		StatEntry entry = StatEntry.of(record, summary);
		synchronized (records)
		{
			if (pendingCloseCall != record)
			{
				return;
			}
			pendingCloseCall = null;
			records.add(0, record);
			stats.add(entry);
		}
		lastCloseCall = record;
		lastCloseCallTick = tick;
		trimAndSave();
		saveStats();
		if (config.chatSummary())
		{
			int maxHp = record.getTicks().stream().mapToInt(TickSnapshot::getMaxHp).max().orElse(0);
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", String.format(
				"<col=ffc040>Death Recap:</col> close call - HP fell to %d/%d against %s. Open the Death Recap panel for details.",
				entry.getLowestHp(), maxHp, summary.getKiller()), null);
		}
	}

	/**
	 * A close call the player didn't survive in the end: the death came before HP got back above half, or
	 * soon after it was saved. Its recap, stats and media go, so only the death counts.
	 */
	private void retractCloseCall(int tick)
	{
		DeathRecord last = lastCloseCall;
		lastCloseCall = null;
		if (last == null || (closeCallArmed && tick - lastCloseCallTick > CLOSE_CALL_RETRACT_TICKS))
		{
			return;
		}
		synchronized (records)
		{
			// Gone already if the panel deleted it or another account was loaded since.
			if (!records.remove(last))
			{
				return;
			}
			stats.removeIf(s -> s.isCloseCall() && s.getTimestamp() == last.getTimestamp());
		}
		if (overlayRecord == last)
		{
			overlayRecord = null;
		}
		trimAndSave(List.of(last));
		saveStats();
	}

	/** Drops an unfinished close call, e.g. because the player died or logged out, and its media. */
	private void discardCloseCall()
	{
		DeathRecord pending;
		synchronized (records)
		{
			pending = pendingCloseCall;
			pendingCloseCall = null;
		}
		long hash = loadedAccount;
		if (pending != null && hash != -1 && config.saveToDisk())
		{
			executor.execute(() -> store.deleteMedia(hash, pending));
		}
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (event.getActor() != client.getLocalPlayer())
		{
			return;
		}
		int type = event.getHitsplat().getHitsplatType();
		int amount = event.getHitsplat().getAmount();
		int tick = client.getTickCount();

		String dot = damageOverTime(type);
		if (dot != null)
		{
			if (amount > 0)
			{
				events.add(new RecapEvent(tick, RecapEvent.Type.DAMAGE_OVER_TIME, amount, dot, null));
			}
			return;
		}
		if (type == HitsplatID.HEAL)
		{
			events.add(new RecapEvent(tick, RecapEvent.Type.HEAL, amount, "Heal", null));
			return;
		}
		if (!isHealthDamage(type))
		{
			return;
		}

		String[] source = attribute(tick);
		RecapEvent.Type kind = amount > 0 ? RecapEvent.Type.HIT : RecapEvent.Type.BLOCK;
		if (kind == RecapEvent.Type.HIT)
		{
			lastHitTick = tick;
		}
		events.add(new RecapEvent(tick, kind, amount, source[0], source[1], source[2]));
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		Actor actor = event.getActor();
		Player local = client.getLocalPlayer();
		if (actor == null || actor == local || local == null || actor.getInteracting() != local || actor.getAnimation() == -1)
		{
			return;
		}
		int tick = client.getTickCount();
		Integer previous = lastAttackTick.put(actor, tick);
		noteAttacker(actor);
		if (config.recordAttacks() && (previous == null || previous != tick))
		{
			events.add(new RecapEvent(tick, RecapEvent.Type.ATTACK, 0, name(actor), null, weaponOf(actor)));
		}
	}

	@Subscribe
	public void onProjectileMoved(ProjectileMoved event)
	{
		Projectile projectile = event.getProjectile();
		Player local = client.getLocalPlayer();
		if (local == null || projectile.getTargetActor() != local || !seenProjectiles.add(projectile))
		{
			return;
		}
		Actor from = projectile.getSourceActor();
		String source = from != null ? name(from) : "Unknown";
		String weapon = weaponOf(from);
		noteAttacker(from);
		incoming.add(new IncomingProjectile(source, weapon, projectile.getEndCycle()));
		if (config.recordAttacks())
		{
			events.add(new RecapEvent(client.getTickCount(), RecapEvent.Type.PROJECTILE, 0, source, null, weapon));
		}
	}

	@Subscribe
	public void onSoundEffectPlayed(SoundEffectPlayed event)
	{
		if (!replayingSound)
		{
			capture.sound(event.getSoundId(), false);
		}
	}

	@Subscribe
	public void onAreaSoundEffectPlayed(AreaSoundEffectPlayed event)
	{
		Player local = client.getLocalPlayer();
		Actor source = event.getSource();
		if (replayingSound || local == null || source == null)
		{
			return;
		}
		// Only the fight: sounds from the player, whoever they're fighting, and anything else close enough to hear.
		boolean fight = source == local || source.getInteracting() == local || local.getInteracting() == source;
		if (fight || source.getWorldLocation().distanceTo(local.getWorldLocation()) <= Math.min(event.getRange(), 5))
		{
			capture.sound(event.getSoundId(), true);
		}
	}

	/** Plays a replay's sound at the player's own volumes. Called from the replay window. */
	void playReplaySound(ReplaySound sound)
	{
		clientThread.invokeLater(() ->
		{
			if (!config.replaySounds() || client.getGameState() != GameState.LOGGED_IN)
			{
				return;
			}
			int volume = sound.isArea() ? client.getPreferences().getAreaSoundEffectVolume()
				: client.getPreferences().getSoundEffectVolume();
			if (volume <= 0)
			{
				return;
			}
			// The client reports sounds it plays for us too; don't record the replay into the next one.
			replayingSound = true;
			try
			{
				client.playSoundEffect(sound.getId(), volume);
			}
			finally
			{
				replayingSound = false;
			}
		});
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		String option = event.getMenuOption();
		RecapEvent.Type type;
		if ("Eat".equalsIgnoreCase(option))
		{
			type = RecapEvent.Type.EAT;
		}
		else if ("Drink".equalsIgnoreCase(option))
		{
			type = RecapEvent.Type.DRINK;
		}
		else
		{
			return;
		}
		String item = Text.removeTags(event.getMenuTarget());
		events.add(new RecapEvent(client.getTickCount(), type, 0, item.isEmpty() ? "food" : item, null));
	}

	@Subscribe
	public void onMenuOpened(MenuOpened event)
	{
		if (!config.deathMap())
		{
			return;
		}
		List<DeathMap.Marker> hovered = deathMap.hovered();
		for (int i = Math.min(hovered.size(), DeathMap.MAX_MENU) - 1; i >= 0; i--)
		{
			DeathMap.Marker m = hovered.get(i);
			client.getMenu().createMenuEntry(-1)
				.setOption("Open recap")
				.setTarget(ColorUtil.wrapWithColorTag(m.getKiller() + " (" + RecapText.date(m.getTimestamp()) + ")",
					DeathStats.colour(m.getKiller())))
				.setType(MenuAction.RUNELITE)
				.onClick(e -> SwingUtilities.invokeLater(() -> openRecap(m.getTimestamp(), m.getKiller())));
		}
	}

	/** Swing thread. Opens the panel on the recap saved at {@code timestamp}, or on the killer's page if it is gone. */
	private void openRecap(long timestamp, String killer)
	{
		DeathRecapPanel p = panel;
		if (p == null)
		{
			return;
		}
		clientToolbar.openPanel(navButton);
		p.focus(timestamp, killer);
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		Player local = client.getLocalPlayer();
		int tick = client.getTickCount();
		if (event.getActor() != local || tick == lastDeathTick)
		{
			return;
		}
		lastDeathTick = tick;
		discardCloseCall();
		retractCloseCall(tick);
		closeCallArmed = true;

		// The killing hitsplat can arrive after this event in the same tick, so the recap is finished on
		// the next game tick, once everything from this one has been recorded.
		DeathRecord record = newRecord(tick);
		record.setState(captureState(true));
		pendingDeath = record;
	}

	private void finishDeath(DeathRecord record)
	{
		int tick = record.getDeathTick();
		// The buffers run longer than the recap window while a close call is pending.
		int from = tick - windowTicks();
		ticks.stream().filter(t -> t.getTick() >= from).forEach(record.getTicks()::add);
		TickSnapshot last = ticks.peekLast();
		if (last == null || last.getTick() < tick)
		{
			record.getTicks().add(new TickSnapshot(tick, 0, client.getRealSkillLevel(Skill.HITPOINTS),
				client.getBoostedSkillLevel(Skill.PRAYER), activeProtection()));
		}
		events.stream().filter(e -> e.getTick() >= from).forEach(record.getEvents()::add);
		record.setAttackers(attackersIn(record));

		RecapAnalyzer.Summary summary = RecapAnalyzer.analyze(record);
		record.setKiller(summary.getKiller());
		resetBuffers();

		synchronized (records)
		{
			records.add(0, record);
			stats.add(StatEntry.of(record, summary));
		}
		trimAndSave();
		saveStats();

		long hash = loadedAccount;
		if (hash != -1)
		{
			capture.captureDeath(hash, record,
				(r, name, times, sounds) -> mediaSaved(hash, r, name, null, null, false),
				(r, name, times, sounds) -> mediaSaved(hash, r, name, times, sounds, true));
			if (wrappedListening && config.shareWithWrapped())
			{
				executor.execute(() -> sendToWrapped(hash, record));
			}
		}

		if (config.showOverlay())
		{
			overlayRecord = record;
			overlaySummary = summary;
			overlayUntil = Instant.now().plusSeconds(config.overlaySeconds());
		}
		if (config.chatSummary())
		{
			String blow = summary.getKillingBlow() != null ? " (" + summary.getKillingBlow().getAmount() + ")" : "";
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", String.format(
				"<col=ff4040>Death Recap:</col> killed by %s%s. %d damage taken.%s Open the Death Recap panel for details.",
				RecapText.killer(record, summary), blow, summary.getTotalDamage(), RecapText.lostSentence(record)), null);
		}
	}

	/** Saves the recap window up to now as a clip, with its replay. Client thread only. */
	private void saveClip()
	{
		long now = System.currentTimeMillis();
		if (client.getGameState() != GameState.LOGGED_IN || loadedAccount == -1 || now - lastClipMillis < 2000)
		{
			return;
		}
		if (!config.saveToDisk() || !config.recordReplay())
		{
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				"<col=ff4040>Death Recap:</col> turn on Save recaps and Death replay to save clips.", null);
			return;
		}
		lastClipMillis = now;
		int tick = client.getTickCount();
		DeathRecord record = newRecord(tick);
		record.setClip(true);
		record.setState(captureState(false));
		int from = tick - windowTicks();
		ticks.stream().filter(t -> t.getTick() >= from).forEach(record.getTicks()::add);
		events.stream().filter(e -> e.getTick() >= from).forEach(record.getEvents()::add);
		record.setAttackers(attackersIn(record));
		RecapAnalyzer.Summary summary = RecapAnalyzer.analyze(record);
		record.setKiller(summary.getKiller());

		synchronized (records)
		{
			records.add(0, record);
		}
		trimAndSave();
		long hash = loadedAccount;
		capture.captureDeath(hash, record, CLIP_AFTER_MS,
			(r, name, times, sounds) -> mediaSaved(hash, r, name, null, null, false),
			(r, name, times, sounds) -> mediaSaved(hash, r, name, times, sounds, true));
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", String.format(
			"<col=60b0ff>Death Recap:</col> clip saved (%ds, %d damage taken). It's in the Death Recap panel.",
			config.replaySeconds(), summary.getTotalDamage()), null);
	}

	private DeathRecord newRecord(int tick)
	{
		DeathRecord record = new DeathRecord();
		record.setTimestamp(System.currentTimeMillis());
		record.setWorld(client.getWorld());
		record.setDeathTick(tick);
		Player local = client.getLocalPlayer();
		WorldPoint wp = local != null ? WorldPoint.fromLocalInstance(client, local.getLocalLocation()) : null;
		if (wp != null)
		{
			record.setRegionId(wp.getRegionID());
			record.setX(wp.getX());
			record.setY(wp.getY());
			record.setPlane(wp.getPlane());
		}
		return record;
	}

	/** Reads stats, energy, prayers, weapon and, if {@code items}, worn and carried items. Client thread only. */
	private DeathState captureState(boolean items)
	{
		DeathState state = new DeathState();
		for (Skill skill : STATE_SKILLS)
		{
			state.getStats().add(new DeathState.Stat(skill.getName(), client.getBoostedSkillLevel(skill), client.getRealSkillLevel(skill)));
		}
		state.setSpecEnergy(client.getVarpValue(VarPlayerID.SA_ENERGY) / 10);
		state.setRunEnergy(client.getEnergy() / 100);
		for (Prayer prayer : Prayer.values())
		{
			if (isActive(prayer))
			{
				state.getPrayers().add(prayerName(prayer));
			}
		}
		Player local = client.getLocalPlayer();
		state.setSkulled(local != null && local.getSkullIcon() != -1);
		state.setProtectItem(isActive(Prayer.PROTECT_ITEM));

		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		Item weapon = worn != null ? worn.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx()) : null;
		state.setWeapon(weapon != null && weapon.getId() > 0 ? itemManager.getItemComposition(weapon.getId()).getName() : "Unarmed");
		state.setAttackStyle(attackStyle());
		if (items)
		{
			state.setEquipment(stacks(worn));
			ItemContainer inventory = client.getItemContainer(InventoryID.INV);
			state.setInventory(stacks(inventory));
			state.setFood(supplies(inventory, "Eat"));
			state.setPotions(supplies(inventory, "Drink"));
		}
		return state;
	}

	/** Carried items with the given inventory option, merged into one stack per item. */
	private List<DeathState.ItemStack> supplies(ItemContainer container, String option)
	{
		Map<Integer, DeathState.ItemStack> out = new LinkedHashMap<>();
		if (container == null)
		{
			return new ArrayList<>();
		}
		for (Item item : container.getItems())
		{
			if (item.getId() <= 0 || item.getQuantity() <= 0)
			{
				continue;
			}
			ItemComposition comp = itemManager.getItemComposition(item.getId());
			if (!Arrays.asList(comp.getInventoryActions()).contains(option))
			{
				continue;
			}
			DeathState.ItemStack s = out.get(item.getId());
			if (s != null)
			{
				s.setQuantity(s.getQuantity() + item.getQuantity());
			}
			else
			{
				out.put(item.getId(), new DeathState.ItemStack(item.getId(), comp.getName(), item.getQuantity(), 0));
			}
		}
		return new ArrayList<>(out.values());
	}

	private List<DeathState.ItemStack> stacks(ItemContainer container)
	{
		List<DeathState.ItemStack> out = new ArrayList<>();
		if (container == null)
		{
			return out;
		}
		for (Item item : container.getItems())
		{
			if (item.getId() <= 0 || item.getQuantity() <= 0)
			{
				continue;
			}
			ItemComposition comp = itemManager.getItemComposition(item.getId());
			long price = itemManager.getItemPrice(itemManager.canonicalize(item.getId()));
			out.add(new DeathState.ItemStack(item.getId(), comp.getName(), item.getQuantity(), price > 0 ? price : comp.getHaPrice()));
		}
		return out;
	}

	private String attackStyle()
	{
		if (client.getVarbitValue(VarbitID.AUTOCAST_SET) == 1)
		{
			return client.getVarbitValue(VarbitID.AUTOCAST_DEFMODE) == 1 ? "Defensive autocast" : "Autocast";
		}
		try
		{
			int category = client.getVarbitValue(VarbitID.COMBAT_WEAPON_CATEGORY);
			int index = client.getVarpValue(VarPlayerID.COM_MODE);
			EnumComposition styles = client.getEnum(client.getEnum(EnumID.WEAPON_STYLES).getIntValue(category));
			int[] structs = styles.getIntVals();
			if (index < 0 || index >= structs.length)
			{
				return null;
			}
			String name = client.getStructComposition(structs[index]).getStringValue(ParamID.ATTACK_STYLE_NAME);
			return name == null || name.isEmpty() ? null : Character.toUpperCase(name.charAt(0)) + name.substring(1);
		}
		catch (RuntimeException e)
		{
			log.debug("Unable to read the attack style", e);
			return null;
		}
	}

	/** "PROTECT_FROM_MELEE" to "Protect from Melee"; Ruinous Powers prayers lose their "RP_" prefix. */
	static String prayerName(Prayer prayer)
	{
		String[] words = prayer.name().replaceFirst("^RP_", "").toLowerCase(Locale.ROOT).split("_");
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < words.length; i++)
		{
			String w = words[i];
			if (i > 0)
			{
				sb.append(' ');
			}
			boolean minor = i > 0 && (w.equals("from") || w.equals("of") || w.equals("the"));
			sb.append(minor || w.isEmpty() ? w : Character.toUpperCase(w.charAt(0)) + w.substring(1));
		}
		return sb.toString();
	}

	/** Called from the panel on the Swing thread. */
	void deleteRecord(DeathRecord record)
	{
		boolean removed;
		synchronized (records)
		{
			removed = records.remove(record);
		}
		if (overlayRecord == record)
		{
			overlayRecord = null;
		}
		trimAndSave(removed ? List.of(record) : List.of());
	}

	/** Called from the panel on the Swing thread. */
	void clearRecords()
	{
		final List<DeathRecord> removed;
		synchronized (records)
		{
			removed = new ArrayList<>(records);
			records.clear();
		}
		overlayRecord = null;
		trimAndSave(removed);
	}

	void hideOverlay()
	{
		overlayRecord = null;
	}

	/** Called from the panel on the Swing thread. Recaps are kept; only the totals start over. */
	void resetStats()
	{
		synchronized (records)
		{
			stats.clear();
		}
		saveStats();
		publish();
	}

	/**
	 * Draws the share card for {@code record} off the Swing thread, saves it next to the screenshot and
	 * hands it to {@code done} on the Swing thread; {@code done} gets null if drawing failed.
	 */
	ShareCardLayout shareCardLayout()
	{
		return config.shareCardLayout();
	}

	void makeShareCard(DeathRecord record, ShareCardLayout layout, Consumer<BufferedImage> done)
	{
		long hash = loadedAccount;
		executor.execute(() ->
		{
			BufferedImage card = null;
			try
			{
				BufferedImage shot = null;
				if (record.getScreenshot() != null && hash != -1)
				{
					try
					{
						shot = ImageIO.read(new ByteArrayInputStream(store.read(hash, record.getScreenshot())));
					}
					catch (IOException e)
					{
						log.debug("No screenshot for the share card", e);
					}
				}
				card = ShareCard.render(shot, record, RecapAnalyzer.analyze(record),
					FontManager.getRunescapeFont(), FontManager.getRunescapeBoldFont(), layout);
				if (config.saveToDisk() && hash != -1)
				{
					String name = record.getTimestamp() + "-card.png";
					store.writePng(hash, name, card);
					boolean kept;
					synchronized (records)
					{
						kept = hash == loadedAccount && records.contains(record);
						if (kept)
						{
							record.setCard(name);
						}
					}
					if (kept)
					{
						trimAndSave();
					}
					else
					{
						store.media(hash, name).deleteIfExists();
					}
				}
			}
			catch (IOException | RuntimeException e)
			{
				log.warn("Unable to make the share card", e);
			}
			BufferedImage result = card;
			SwingUtilities.invokeLater(() -> done.accept(result));
		});
	}

	/**
	 * Reads a screenshot or replay of the current account on the background executor and hands the
	 * bytes to {@code done} on the Swing thread; {@code done} gets null if the file can't be read.
	 */
	void loadMedia(String name, Consumer<byte[]> done)
	{
		loadMedia(name, false, done);
	}

	/** Like {@link #loadMedia}, but reads a replay's full-quality frames when they're still kept. */
	void loadReplay(String replay, Consumer<byte[]> done)
	{
		loadMedia(replay, true, done);
	}

	private void loadMedia(String name, boolean replay, Consumer<byte[]> done)
	{
		long hash = loadedAccount;
		executor.execute(() ->
		{
			byte[] bytes = null;
			try
			{
				bytes = replay ? store.readReplay(hash, name) : store.read(hash, name);
			}
			catch (IOException e)
			{
				log.debug("Unable to read {}", name, e);
			}
			byte[] result = bytes;
			SwingUtilities.invokeLater(() -> done.accept(result));
		});
	}

	/** Like {@link #loadMedia}, but decodes and shrinks an image to {@code width} off the Swing thread. */
	void loadThumbnail(String name, int width, Consumer<BufferedImage> done)
	{
		long hash = loadedAccount;
		executor.execute(() ->
		{
			BufferedImage thumb = null;
			try
			{
				BufferedImage full = ImageIO.read(new ByteArrayInputStream(store.read(hash, name)));
				if (full != null)
				{
					int w = Math.min(width, full.getWidth());
					thumb = ImageUtil.resizeImage(full, w, Math.max(1, full.getHeight() * w / full.getWidth()));
				}
			}
			catch (IOException e)
			{
				log.debug("Unable to read {}", name, e);
			}
			BufferedImage result = thumb;
			SwingUtilities.invokeLater(() -> done.accept(result));
		});
	}

	/**
	 * Swing thread. Asks where to save, then turns the replay into an MP4 on the video thread. {@code status}
	 * is told the progress on the Swing thread, and finally either "Saved ..." or a failure message.
	 */
	void exportVideo(DeathRecord record, Component parent, Consumer<String> status)
	{
		String replay = record.getReplay();
		long hash = loadedAccount;
		if (replay == null || hash == -1)
		{
			return;
		}
		List<Filepath> chosen = new Filepath.Chooser()
			.setIsSave()
			.setAcceptsFiles()
			.setDialogTitle("Save the replay as a video")
			.addExtensionFilter("MP4 video", "mp4")
			.setDefaultExtension("mp4")
			.setFileName("death-recap-" + record.getTimestamp() + ".mp4")
			.showDialog(parent);
		if (chosen == null || chosen.isEmpty())
		{
			return;
		}
		Filepath dest = chosen.get(0);
		int maxWidth = config.videoSize().getMaxWidth();
		status.accept("Making the video...");
		videoExecutor.execute(() ->
		{
			String result;
			try
			{
				byte[] frames = store.readReplay(hash, replay);
				try (OutputStream out = dest.openOutputStream())
				{
					int[] last = {-1};
					ReplayVideo.write(frames, record, maxWidth, FontManager.getRunescapeFont(), out, pct ->
					{
						if (pct / 5 != last[0])
						{
							last[0] = pct / 5;
							SwingUtilities.invokeLater(() -> status.accept("Making the video... " + pct + "%"));
						}
					});
				}
				result = "Saved " + dest.getFileName();
			}
			catch (InterruptedIOException e)
			{
				deleteQuietly(dest);
				return;
			}
			catch (IOException | RuntimeException e)
			{
				log.warn("Unable to export the replay video", e);
				deleteQuietly(dest);
				result = "Unable to make the video";
			}
			String message = result;
			SwingUtilities.invokeLater(() -> status.accept(message));
		});
	}

	private static void deleteQuietly(Filepath file)
	{
		try
		{
			file.deleteIfExists();
		}
		catch (IOException e)
		{
			log.debug("Unable to delete {}", file, e);
		}
	}

	ScheduledExecutorService getExecutor()
	{
		return executor;
	}

	/** Copies the path of the current account's screenshot and replay folder to the clipboard. */
	void copyMediaFolderPath()
	{
		long hash = loadedAccount;
		if (hash != -1)
		{
			Toolkit.getDefaultToolkit().getSystemClipboard()
				.setContents(new StringSelection(store.mediaDir(hash).toString()), null);
		}
	}

	/** Runs on the media thread once a screenshot or replay for {@code record} has been written. */
	private void mediaSaved(long hash, DeathRecord record, String name, int[] times, List<ReplaySound> sounds, boolean replay)
	{
		boolean kept;
		synchronized (records)
		{
			kept = hash == loadedAccount && (records.contains(record) || pendingCloseCall == record);
			if (kept)
			{
				if (replay)
				{
					record.setReplay(name);
					record.setReplayTimes(times);
					record.setReplaySounds(sounds == null || sounds.isEmpty() ? null : sounds);
				}
				else
				{
					record.setScreenshot(name);
				}
			}
		}
		if (kept)
		{
			trimAndSave();
			if (record.isDeath() && wrappedListening && config.shareWithWrapped())
			{
				executor.execute(() -> sendToWrapped(hash, record));
			}
		}
		else
		{
			// The recap was deleted, or dropped off the list, before its file was ready.
			try
			{
				store.media(hash, name).deleteIfExists();
			}
			catch (IOException e)
			{
				log.debug("Unable to delete {}", name, e);
			}
		}
	}

	private void loadAccount()
	{
		long hash = client.getAccountHash();
		if (hash == -1 || hash == loadedAccount)
		{
			return;
		}
		loadedAccount = hash;
		capture.clear();
		discardCloseCall();
		synchronized (records)
		{
			records.clear();
			stats.clear();
		}
		publish();
		if (!config.saveToDisk())
		{
			postReady(hash);
			return;
		}
		executor.execute(() ->
		{
			List<DeathRecord> loaded = store.load(hash);
			List<StatEntry> savedStats = store.loadStats(hash);
			boolean backfill = savedStats == null;
			if (backfill)
			{
				// First run with stats: start them from the recaps already saved.
				savedStats = new ArrayList<>();
				for (DeathRecord r : loaded)
				{
					if (!r.isClip())
					{
						savedStats.add(StatEntry.of(r, RecapAnalyzer.analyze(r)));
					}
				}
			}
			else
			{
				backfill = fillDetails(savedStats, loaded);
			}
			synchronized (records)
			{
				if (loadedAccount != hash)
				{
					return;
				}
				// Keep any death that happened while the files were loading.
				records.addAll(loaded);
				records.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
				stats.addAll(0, savedStats);
			}
			store.deleteOrphans(hash, loaded);
			if (backfill)
			{
				saveStats();
			}
			publish();
			postReady(hash);
		});
	}

	/**
	 * Adds the location and fight details that stats from before 2.0 lack, from the recaps still saved.
	 * Returns true if anything changed.
	 */
	static boolean fillDetails(List<StatEntry> entries, List<DeathRecord> recaps)
	{
		Map<Long, DeathRecord> byTime = new HashMap<>();
		for (DeathRecord r : recaps)
		{
			if (!r.isClip())
			{
				byTime.put(r.getTimestamp(), r);
			}
		}
		boolean changed = false;
		for (StatEntry e : entries)
		{
			DeathRecord r = byTime.get(e.getTimestamp());
			if (r != null && e.missingDetails())
			{
				int x = e.getX();
				int hp = e.getHpWhenHit();
				int fight = e.getFightTicks();
				e.fillFrom(r, e.getKiller());
				changed |= x != e.getX() || hp != e.getHpWhenHit() || fight != e.getFightTicks();
			}
		}
		return changed;
	}

	@Subscribe
	public void onPluginMessage(PluginMessage event)
	{
		if (!WrappedBridge.NAMESPACE.equals(event.getNamespace()))
		{
			return;
		}
		if (WrappedBridge.GOODBYE.equals(event.getName()))
		{
			wrappedListening = false;
			return;
		}
		if (!WrappedBridge.HELLO.equals(event.getName()))
		{
			return;
		}
		wrappedListening = true;
		Map<String, Object> data = event.getData();
		long hash = WrappedBridge.account(data);
		if (hash == -1 || hash != loadedAccount || !config.shareWithWrapped())
		{
			return;
		}
		List<DeathRecord> missing = new ArrayList<>();
		synchronized (records)
		{
			for (DeathRecord r : records)
			{
				if (r.isDeath() && !WrappedBridge.has(data, r.getTimestamp()))
				{
					missing.add(r);
				}
			}
		}
		if (!missing.isEmpty())
		{
			executor.execute(() ->
			{
				// Oldest first, so they are logged in order.
				for (int i = missing.size() - 1; i >= 0; i--)
				{
					sendToWrapped(hash, missing.get(i));
				}
			});
		}
	}

	private void postReady(long hash)
	{
		eventBus.post(new PluginMessage(WrappedBridge.NAMESPACE, WrappedBridge.READY, WrappedBridge.ready(hash)));
	}

	/**
	 * Background executor. Sends one death to Gielinor Wrapped: a share card in the player's chosen layout, plus
	 * the screenshot and replay if they've been saved.
	 */
	private void sendToWrapped(long hash, DeathRecord record)
	{
		if (!wrappedListening || !config.shareWithWrapped() || !record.isDeath())
		{
			return;
		}
		String shotName;
		String replayName;
		int[] times;
		synchronized (records)
		{
			if (hash != loadedAccount)
			{
				return;
			}
			shotName = record.getScreenshot();
			replayName = record.getReplay();
			times = record.getReplayTimes();
		}
		try
		{
			byte[] shot = readOrNull(hash, shotName);
			byte[] replay = readOrNull(hash, replayName);
			BufferedImage shotImage = shot == null ? null : ImageIO.read(new ByteArrayInputStream(shot));
			RecapAnalyzer.Summary summary = RecapAnalyzer.analyze(record);
			BufferedImage card = ShareCard.render(shotImage, record, summary,
				FontManager.getRunescapeFont(), FontManager.getRunescapeBoldFont(), config.shareCardLayout());
			ByteArrayOutputStream png = new ByteArrayOutputStream();
			ImageIO.write(card, "png", png);
			eventBus.post(new PluginMessage(WrappedBridge.NAMESPACE, WrappedBridge.DEATH,
				WrappedBridge.death(hash, record, summary, png.toByteArray(), shot, replay, times)));
		}
		catch (IOException | RuntimeException e)
		{
			log.warn("Unable to send a death to Gielinor Wrapped", e);
		}
	}

	private byte[] readOrNull(long hash, String name)
	{
		if (name == null)
		{
			return null;
		}
		try
		{
			return store.read(hash, name);
		}
		catch (IOException e)
		{
			log.debug("Unable to read {}", name, e);
			return null;
		}
	}

	private void trimAndSave()
	{
		trimAndSave(List.of());
	}

	/**
	 * Applies the "Recaps to keep" limit, refreshes the panel and saves. Media of {@code removed} and of
	 * anything trimmed is deleted.
	 */
	private void trimAndSave(List<DeathRecord> removed)
	{
		final List<DeathRecord> snapshot;
		final List<DeathRecord> dropped = new ArrayList<>(removed);
		synchronized (records)
		{
			// Deaths, close calls and clips each get the full limit, so the others never push out deaths.
			int clips = 0;
			int deaths = 0;
			int closeCalls = 0;
			for (Iterator<DeathRecord> it = records.iterator(); it.hasNext(); )
			{
				DeathRecord r = it.next();
				int count = r.isCloseCall() ? ++closeCalls : r.isClip() ? ++clips : ++deaths;
				if (count > config.maxSaved())
				{
					it.remove();
					dropped.add(r);
				}
			}
			snapshot = new ArrayList<>(records);
		}
		publish();
		long hash = loadedAccount;
		if (config.saveToDisk() && hash != -1)
		{
			executor.execute(() ->
			{
				store.save(hash, snapshot);
				for (DeathRecord r : dropped)
				{
					store.deleteMedia(hash, r);
				}
			});
		}
	}

	private void saveStats()
	{
		final List<StatEntry> snapshot;
		synchronized (records)
		{
			snapshot = new ArrayList<>(stats);
		}
		long hash = loadedAccount;
		if (config.saveToDisk() && hash != -1)
		{
			executor.execute(() -> store.saveStats(hash, snapshot));
		}
	}

	private void publish()
	{
		final List<DeathRecord> snapshot;
		final List<StatEntry> statsSnapshot;
		final DeathStats.Report report;
		synchronized (records)
		{
			snapshot = new ArrayList<>(records);
			statsSnapshot = new ArrayList<>(stats);
			report = DeathStats.compute(stats, sessionStart);
		}
		DeathMap map = deathMap;
		if (map != null)
		{
			if (config.deathMap())
			{
				map.show(statsSnapshot, config.mapCloseCalls());
			}
			else
			{
				map.clear();
			}
		}
		DeathRecapPanel p = panel;
		if (p != null)
		{
			SwingUtilities.invokeLater(() -> p.setRecords(snapshot, report));
		}
	}

	private String[] attribute(int tick)
	{
		int cycle = client.getGameCycle();
		IncomingProjectile best = null;
		for (IncomingProjectile p : incoming)
		{
			if (p.endCycle <= cycle + PROJECTILE_EARLY_CYCLES && p.endCycle >= cycle - PROJECTILE_LATE_CYCLES
				&& (best == null || Math.abs(p.endCycle - cycle) < Math.abs(best.endCycle - cycle)))
			{
				best = p;
			}
		}
		if (best != null)
		{
			incoming.remove(best);
			return new String[]{best.source, "projectile", best.weapon};
		}

		Actor attacker = null;
		int attackerTick = Integer.MIN_VALUE;
		for (Map.Entry<Actor, Integer> e : lastAttackTick.entrySet())
		{
			if (tick - e.getValue() <= ATTACK_ATTRIBUTION_TICKS && e.getValue() > attackerTick)
			{
				attacker = e.getKey();
				attackerTick = e.getValue();
			}
		}
		if (attacker != null)
		{
			return new String[]{name(attacker), null, weaponOf(attacker)};
		}

		Player local = client.getLocalPlayer();
		Actor target = local != null ? local.getInteracting() : null;
		if (target != null && target.getInteracting() == local)
		{
			noteAttacker(target);
			return new String[]{name(target), "likely", weaponOf(target)};
		}
		return new String[]{"Unknown", null, null};
	}

	private void noteAttacker(Actor actor)
	{
		if (actor == null)
		{
			return;
		}
		if (attackers.size() > 100)
		{
			attackers.clear();
		}
		String name = name(actor);
		attackers.remove(name);
		attackers.put(name, new Attacker(name, actor.getCombatLevel(), actor instanceof Player));
	}

	/** The attackers that appear in the record's events. */
	private List<Attacker> attackersIn(DeathRecord record)
	{
		List<Attacker> list = new ArrayList<>();
		for (Attacker a : attackers.values())
		{
			if (record.getEvents().stream().anyMatch(e -> a.getName().equals(e.getSource())))
			{
				list.add(new Attacker(a.getName(), a.getCombatLevel(), a.isPlayer()));
			}
		}
		return list;
	}

	/** The weapon another player is holding, or null for NPCs. Client thread only. */
	private String weaponOf(Actor actor)
	{
		if (!(actor instanceof Player))
		{
			return null;
		}
		PlayerComposition comp = ((Player) actor).getPlayerComposition();
		if (comp == null)
		{
			return null;
		}
		int id = comp.getEquipmentId(KitType.WEAPON);
		return id > 0 ? itemManager.getItemComposition(id).getName() : "Unarmed";
	}

	private void trim(int tick)
	{
		int oldest = tick - windowTicks();
		synchronized (records)
		{
			if (pendingCloseCall != null)
			{
				// Keep everything since the start of the close call's window until it's saved.
				oldest = Math.min(oldest, pendingCloseCall.getDeathTick() - windowTicks());
			}
		}
		while (!events.isEmpty() && events.peekFirst().getTick() < oldest)
		{
			events.pollFirst();
		}
		while (!ticks.isEmpty() && ticks.peekFirst().getTick() < oldest)
		{
			ticks.pollFirst();
		}
		int cycle = client.getGameCycle();
		for (Iterator<IncomingProjectile> it = incoming.iterator(); it.hasNext(); )
		{
			if (it.next().endCycle < cycle - PROJECTILE_LATE_CYCLES)
			{
				it.remove();
			}
		}
		lastAttackTick.values().removeIf(t -> tick - t > ATTACK_ATTRIBUTION_TICKS);
	}

	private int windowTicks()
	{
		return (int) Math.ceil(config.recapSeconds() / RecapAnalyzer.TICK_SECONDS);
	}

	private void resetBuffers()
	{
		events.clear();
		ticks.clear();
		incoming.clear();
		lastAttackTick.clear();
		attackers.clear();
		pendingDeath = null;
		seenProjectiles.clear();
		lastProtection = null;
		lastPrayer = -1;
	}

	private String activeProtection()
	{
		if (isActive(Prayer.PROTECT_FROM_MAGIC))
		{
			return "Protect from Magic";
		}
		if (isActive(Prayer.PROTECT_FROM_MISSILES))
		{
			return "Protect from Missiles";
		}
		if (isActive(Prayer.PROTECT_FROM_MELEE))
		{
			return "Protect from Melee";
		}
		return null;
	}

	private boolean isActive(Prayer prayer)
	{
		return client.getVarbitValue(prayer.getVarbit()) == 1;
	}

	private static String name(Actor actor)
	{
		String name = actor.getName();
		return name == null || name.isEmpty() ? "Unknown" : Text.removeTags(name);
	}

	static String damageOverTime(int type)
	{
		switch (type)
		{
			case HitsplatID.POISON:
				return "Poison";
			case HitsplatID.VENOM:
				return "Venom";
			case HitsplatID.DISEASE:
				return "Disease";
			case HitsplatID.BURN:
				return "Burn";
			case HitsplatID.BLEED:
				return "Bleed";
			case HitsplatID.DOOM:
				return "Doom";
			case HitsplatID.CORRUPTION:
				return "Corruption";
			default:
				return null;
		}
	}

	static boolean isHealthDamage(int type)
	{
		switch (type)
		{
			case HitsplatID.BLOCK_ME:
			case HitsplatID.BLOCK_OTHER:
			case HitsplatID.DAMAGE_ME:
			case HitsplatID.DAMAGE_OTHER:
			case HitsplatID.DAMAGE_ME_CYAN:
			case HitsplatID.DAMAGE_OTHER_CYAN:
			case HitsplatID.DAMAGE_ME_ORANGE:
			case HitsplatID.DAMAGE_OTHER_ORANGE:
			case HitsplatID.DAMAGE_ME_YELLOW:
			case HitsplatID.DAMAGE_OTHER_YELLOW:
			case HitsplatID.DAMAGE_ME_WHITE:
			case HitsplatID.DAMAGE_OTHER_WHITE:
			case HitsplatID.DAMAGE_MAX_ME:
			case HitsplatID.DAMAGE_MAX_ME_CYAN:
			case HitsplatID.DAMAGE_MAX_ME_ORANGE:
			case HitsplatID.DAMAGE_MAX_ME_YELLOW:
			case HitsplatID.DAMAGE_MAX_ME_WHITE:
			case HitsplatID.DAMAGE_ME_POISE:
			case HitsplatID.DAMAGE_OTHER_POISE:
			case HitsplatID.DAMAGE_MAX_ME_POISE:
				return true;
			default:
				return false;
		}
	}
}
