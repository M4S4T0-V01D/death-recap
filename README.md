<p align="center">
  <img src="icon.png" width="96" alt="Death Recap icon">
</p>

<h1 align="center">Death Recap</h1>

<p align="center">
  <b>Died and have no idea what just happened? Death Recap shows you.</b><br>
  A tick-by-tick breakdown of every hit, heal, prayer and potion in the seconds before you died,<br>
  with a screenshot, a replay GIF and a share card ready to paste into Discord.
</p>

<!-- Screenshots and GIFs go here: the side panel, a share card and a replay. -->

---

## Why

You're fighting a boss, the screen fills with hitsplats, and you're suddenly at the gravestone. Was it a typhoon of small hits or one big spec? Was your prayer up? Did you even eat? RuneLite already records everything you need to answer that. Death Recap keeps the last few seconds of it and turns it into a clear story the moment you die.

## Features

### The recap
- **Killer and killing blow**: the last hit, who dealt it, and how that was worked out (projectile, attack animation or likely target).
- **Damage breakdown**: total damage taken, split by attacker, including poison, venom, burn and other damage over time.
- **Findings in plain language**: biggest hit, biggest single-tick burst, how fast your HP dropped, how long you sat at low HP, when you last ate or drank, which protection prayer was up for the killing blow, and when your prayer ran out.
- **Food left at death**: whether you died with food or potions still in your inventory, and which ones.
- **Timeline**: every hit, heal, attack, projectile, food, potion and prayer change, with how long before the death each one happened.
- **HP and prayer graph**: your HP line with a low-HP band, hits drawn as bars sized by damage (the killing blow outlined), food and potions marked, and a seconds axis. Hover it to see your exact HP and prayer at any moment.
- **State at death**: boosted and real stats, special attack and run energy, active prayers, weapon and attack style, skull and Protect Item.
- **Money and items lost**: an estimate of what you lost and what you kept, at Grand Exchange prices.

### Screenshot, replay and sharing
- **Death screenshot** taken the moment you die.
- **Replay GIF** of the seconds before the death and the death animation, at the game's full size so hitsplats and chat stay readable. The replay player shows the recap's timeline beside the video: click any event to jump to it, Space to play and pause, arrow keys to step frame by frame.
- **Share card**: one image with the screenshot, killer, gp lost, damage by source, the HP graph, key findings and items lost. It's copied to your clipboard, ready to paste. Pick a layout each time or set a default:

  | Layout | What you get |
  | --- | --- |
  | Wide | The screenshot with the recap in a strip below it |
  | Side panel | The recap in a column beside the screenshot |
  | Tall | Screenshot, then a big graph and the recap, stacked. Good for phones. |
  | Recap only | The recap and graph, no screenshot |

### Close calls and clips
- **Close calls**: when a hit takes you very low and you survive, the fight is saved as a "close call" with the same graph and findings. It's saved only once the fight calms down, so nothing pops up mid-fight.
- **Clip hotkey**: press a key to save the last few seconds as a recap with a replay, without dying. Use it for a great fight or a near miss you want to keep.

### Stats
- Deaths and close calls by attacker, the attacker that killed you most, and average damage taken before death.
- How many deaths came without eating, and how many with food still left.
- gp lost all time and since RuneLite started, and your most expensive death.

## Where it shows up

- A short **on-screen summary** right after you die.
- A one-line **chat message**: `Death Recap: killed by Vorkath (32). 87 damage taken. Lost about 1.2M gp (Abyssal whip, ...).`
- The **Death Recap side panel**, which keeps your recent recaps per account. Each one has Copy (as text), Share card and Delete buttons.

Each of these can be turned off.

## Settings

| Setting | Default |
| --- | --- |
| Recap length | 15 s (5–60) |
| Show on-screen recap / duration | On, 15 s |
| Chat summary | On |
| Record attack animations and projectiles | On |
| Save recaps / recaps to keep | On, 25 (deaths, close calls and clips each) |
| Record close calls / below | On, 15% of max HP (5–50) |
| Death screenshot | On |
| Death replay (GIF) / length / frame rate | Off, 8 s, 6 fps |
| Save clip hotkey | Not set (needs Save recaps and Death replay) |
| Share card layout | Wide |

## About the replay

The replay keeps a rolling buffer of compressed, full-size frames in memory: about 20–80 MB depending on its length, frame rate and window size. That's why it's off by default. A saved GIF is usually under 1 MB with a still camera and 10–20 MB when the camera moves a lot.

Frames are compressed on one background thread and GIFs are written on another, never on the game thread. Writing one replay (say, a close call just before a death) never pauses recording, so the death's replay always runs right up to the death and 2 s past it.

## Your data

Everything stays on your computer. Recaps are saved as JSON in the plugin's folder inside your RuneLite directory, one file per account, with screenshots and GIFs next to them. Use "Open folder" in the panel to find them. Deleting a recap also deletes its media. Nothing is uploaded anywhere.

## Notes

- **Items lost is an estimate.** It assumes the usual 3 kept items (0 when skulled, plus 1 with Protect Item) and uses GE prices, or high alchemy value for untradeables. Gravestones, untradeable item rules, PvP and some minigames can change what you actually lose.
- **It only looks back.** Death Recap describes what already happened. It never predicts attacks or recommends prayers during combat.

## Building from source

```sh
./gradlew build      # build and run the tests
./gradlew run        # start a development RuneLite client with the plugin loaded
```

Requires Java 11 or newer.

## License

BSD 2-Clause. See [LICENSE](LICENSE).
