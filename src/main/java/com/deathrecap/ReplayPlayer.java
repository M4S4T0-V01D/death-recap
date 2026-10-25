package com.deathrecap;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import javax.swing.AbstractAction;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import lombok.extern.slf4j.Slf4j;

/**
 * Plays a death replay at full size next to the recap's timeline. Clicking an event jumps to that
 * moment; while playing, the event list follows along. Space plays or pauses, the arrow keys step.
 */
@Slf4j
class ReplayPlayer extends JDialog
{
	private static final int MS_PER_TICK = 600;
	/** How long the player waits at the last frame before it counts as finished. */
	private static final int END_HOLD_MS = 1000;

	private final DeathRecord record;
	private final List<RecapEvent> events;
	private final JLabel status = new JLabel("Loading replay...", JLabel.CENTER);
	private final Timer timer = new Timer(0, e -> advance());

	private ReplayFrames.Decoded decoded;
	private int[] times;
	private int current = -1;
	private boolean updating;
	private JLabel image;
	private JSlider slider;
	private JLabel clock;
	private JButton play;
	private JList<RecapEvent> list;

	/**
	 * @param startTick event tick to open at, paused; or null to play from the start
	 */
	ReplayPlayer(Window owner, String title, DeathRecord record, byte[] gif, Integer startTick, Runnable openFolder,
		Executor executor)
	{
		super(owner, title);
		this.record = record;
		this.events = record.getEvents().stream()
			.sorted(Comparator.comparingInt(RecapEvent::getTick))
			.collect(Collectors.toList());
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);
		setResizable(true);
		timer.setRepeats(false);
		status.setBorder(new EmptyBorder(40, 80, 40, 80));
		add(status, BorderLayout.CENTER);
		addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosed(WindowEvent e)
			{
				timer.stop();
				decoded = null;
			}
		});
		pack();
		setLocationRelativeTo(owner);

		// Decoding takes a moment for long replays, so it runs off the Swing thread.
		executor.execute(() ->
		{
			try
			{
				ReplayFrames.Decoded d = ReplayFrames.decode(gif, n -> SwingUtilities.invokeLater(() ->
					status.setText("Loading replay... " + n + " frames")));
				SwingUtilities.invokeLater(() -> ready(d, startTick, openFolder));
			}
			catch (IOException | RuntimeException e)
			{
				log.warn("Unable to load death replay", e);
				SwingUtilities.invokeLater(() -> status.setText("Unable to load this replay."));
			}
		});
	}

	private void ready(ReplayFrames.Decoded d, Integer startTick, Runnable openFolder)
	{
		if (!isDisplayable())
		{
			return;
		}
		decoded = d;
		times = ReplayFrames.times(record.getReplayTimes(), d.getDurations());
		getContentPane().removeAll();

		image = new JLabel();
		JScrollPane view = new JScrollPane(image);
		view.setBorder(null);
		view.getVerticalScrollBar().setUnitIncrement(24);
		view.getHorizontalScrollBar().setUnitIncrement(24);
		add(view, BorderLayout.CENTER);

		list = new JList<>(events.toArray(new RecapEvent[0]));
		list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		list.setCellRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> l, Object value, int index, boolean selected, boolean focus)
			{
				RecapEvent e = (RecapEvent) value;
				super.getListCellRendererComponent(l, RecapText.offset(e.getTick(), record.getDeathTick()) + "  "
					+ RecapText.describe(e), index, selected, focus);
				if (!selected)
				{
					setForeground(DeathRecapPanel.colour(e));
				}
				return this;
			}
		});
		list.addListSelectionListener(e ->
		{
			if (!updating && !e.getValueIsAdjusting() && list.getSelectedValue() != null)
			{
				pause();
				seek(list.getSelectedValue().getTick());
			}
		});
		JScrollPane side = new JScrollPane(list);
		side.setPreferredSize(new Dimension(280, 0));
		JPanel right = new JPanel(new BorderLayout());
		JLabel hint = new JLabel("Click an event to jump to it");
		hint.setBorder(new EmptyBorder(4, 6, 4, 6));
		right.add(hint, BorderLayout.NORTH);
		right.add(side, BorderLayout.CENTER);
		add(right, BorderLayout.EAST);

		JPanel controls = new JPanel(new BorderLayout(8, 0));
		controls.setBorder(new EmptyBorder(4, 6, 4, 6));
		play = new JButton("Pause");
		play.addActionListener(e -> toggle());
		slider = new JSlider(0, times.length - 1, 0);
		slider.addChangeListener(e ->
		{
			if (!updating)
			{
				pause();
				show(slider.getValue());
			}
		});
		clock = new JLabel();
		JPanel west = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		west.add(play);
		west.add(clock);
		JButton folder = new JButton("Open folder");
		folder.addActionListener(e -> openFolder.run());
		controls.add(west, BorderLayout.WEST);
		controls.add(slider, BorderLayout.CENTER);
		controls.add(folder, BorderLayout.EAST);
		add(controls, BorderLayout.SOUTH);

		bind(KeyEvent.VK_SPACE, "toggle", this::toggle);
		bind(KeyEvent.VK_LEFT, "back", () -> step(-1));
		bind(KeyEvent.VK_RIGHT, "forward", () -> step(1));

		show(0);
		pack();
		// Full size where it fits; anything bigger scrolls instead of being shrunk.
		Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
		setSize(Math.min(getWidth(), usable.width), Math.min(getHeight(), usable.height));
		setLocationRelativeTo(getOwner());

		if (startTick != null)
		{
			pause();
			seek(startTick);
		}
		else
		{
			resume();
		}
	}

	private void bind(int key, String name, Runnable action)
	{
		JComponent root = getRootPane();
		root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, 0), name);
		root.getActionMap().put(name, new AbstractAction()
		{
			@Override
			public void actionPerformed(ActionEvent e)
			{
				action.run();
			}
		});
	}

	/** Shows the frame for an event's tick: ticks are 600 ms apart and the death is at time 0. */
	private void seek(int tick)
	{
		show(ReplayFrames.frameAt(times, (long) (tick - record.getDeathTick()) * MS_PER_TICK));
	}

	private void step(int by)
	{
		pause();
		show(Math.max(0, Math.min(times.length - 1, current + by)));
	}

	private void toggle()
	{
		if (timer.isRunning())
		{
			pause();
		}
		else
		{
			if (current >= times.length - 1)
			{
				show(0);
			}
			resume();
		}
	}

	private void pause()
	{
		timer.stop();
		if (play != null)
		{
			play.setText("Play");
		}
	}

	private void resume()
	{
		play.setText("Pause");
		schedule();
	}

	private void schedule()
	{
		int delay = current < times.length - 1 ? times[current + 1] - times[current] : END_HOLD_MS;
		timer.setInitialDelay(Math.max(20, Math.min(1000, delay)));
		timer.restart();
	}

	private void advance()
	{
		if (decoded == null)
		{
			return;
		}
		if (current >= times.length - 1)
		{
			pause();
			return;
		}
		show(current + 1);
		schedule();
	}

	private void show(int index)
	{
		if (decoded == null)
		{
			return;
		}
		try
		{
			image.setIcon(new ImageIcon(decoded.frame(index)));
		}
		catch (IOException e)
		{
			log.debug("Unable to show replay frame {}", index, e);
		}
		current = index;
		updating = true;
		try
		{
			slider.setValue(index);
			long time = times[index];
			clock.setText(String.format("%s%.1fs", time > 0 ? "+" : time < 0 ? "-" : "", Math.abs(time) / 1000.0));
			int at = -1;
			for (int i = 0; i < events.size(); i++)
			{
				if ((long) (events.get(i).getTick() - record.getDeathTick()) * MS_PER_TICK <= time)
				{
					at = i;
				}
			}
			if (at >= 0)
			{
				list.setSelectedIndex(at);
				list.ensureIndexIsVisible(at);
			}
			else
			{
				list.clearSelection();
			}
		}
		finally
		{
			updating = false;
		}
	}
}
