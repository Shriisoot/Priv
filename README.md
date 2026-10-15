# Spider-Man: Friendly Neighborhood Helper

A Fabric mod for **Minecraft 1.21.1** that adds a Spider-Man companion who works for you, plus a Web Shooter that lets you web-swing with real rope physics.

> **Status:** builds cleanly and passes a 20-check end-to-end test in the real game client (see [Testing](#testing)). The code was also put through an adversarial multi-reviewer pass against the decompiled 1.21.1 sources; every confirmed finding is fixed. It has not been played by a human yet, so swing feel and pacing may still want tuning.

## What's in it

### Spider-Man (companion)
Use the **Spider-Man Spawn Egg**. He belongs to the nearest player when he spawns. Otherwise, right-click him to claim him.

Talk to him in normal chat. Start the message with `spidey`, `spiderman` or `spider-man`:

| Say | He does |
| --- | --- |
| `spidey get 32 wood` | Goes and chops 32 logs, then brings them back to you |
| `spidey get stone` / `iron` / `coal` / `diamonds` / `sand` ... | Gathers that resource (default 16, `a stack` = 64). Amounts count items, so `get 16 redstone` means 16 dust |
| `spidey follow` / `come` | Follows you |
| `spidey stay` / `stop` / `guard` | Holds his spot in his signature crouch and guards it |
| `spidey give` / `deliver` | Hands over everything he's carrying ("give me the wood" delivers; it doesn't start a new job) |
| `spidey status` | Reports what he's doing, his load and his health |
| `spidey dismiss` | Drops his inventory and swings away for good. Only the bare word counts, so "don't leave!" is safe |

`/spidey <order>` does the same thing as a command.

How he gathers wood:
- He only chops logs that connect, log to log, to **natural leaves**. Your house, including a cabin at the edge of a forest, is left alone.
- Logs in reach get **punched**, with crack animation and hit sounds. Logs up the tree get **web-yanked**: a web shoots out, tugs, and the drops are reeled in on the strand. Webs pass through leaves.
- He only picks up drops he broke loose himself, so he never scoops up another player's death pile. Then he **delivers** them to you with a quip in chat.
- He obeys the `mobGriefing` game rule and spawn protection, like vanilla mobs that break blocks. If `mobGriefing` is off, he tells you so instead of failing silently.

How he gets around:
- He **crawls up walls** like a spider.
- When you're far away he **web-zips** after you. If you're very far, he swings in from off-screen (teleports).

How he fights:
- He fights hostile mobs near him, anything that hurts you, and anything you attack, but only recent fights. He never targets players or your pets, and he leaves creepers and neutral mobs alone.
- At range he **webs mobs**: they get slowed and yanked toward him.
- He has a **spider-sense**: a 30% chance to flip out of the way of a hit.
- He takes no fall damage, regenerates, and is never stuck in cobwebs.

Right-click him to open his 27-slot inventory. Sneak + right-click toggles follow/stay.

His animations are procedural and blended:
- athletic sprint
- perch crouch
- hand-over-hand wall crawl
- web-zip pose
- "thwip" pose: his arm locks onto the target
- alternating punches
- spider-sense dodge hop

### Web Shooter (your tool)
Crafted from cobweb, iron, redstone and string, or taken from the creative tab.

| Input | Action |
| --- | --- |
| Right-click | Shoot a web line (about 60 blocks). Right-click again to let go |
| Sneak + right-click | **Web-zip**: get pulled straight to the spot |
| `W` while swinging | Pump your swing. `A`/`D` steer |
| Hold sneak while swinging | Reel in |
| Jump in mid-air | Let go with a forward boost |
| Web hits a mob | Yanks it to you and slows it |

The swing is a real pendulum simulated on the client, so it feels responsive. Vanilla air drag is cancelled while you're attached, so you keep momentum through the arc. You get fall-damage grace while swinging and for 5 seconds after letting go; ender pearl damage still applies. In third person your arm points along the web.

Webs respect PvP settings, teams and creative-mode invulnerability, and never hit your own mount. Hanging on a web resets the server's "floating too long" check, so dedicated servers don't kick swinging players.

## Building

Requires JDK 21.

```bash
./gradlew build          # mod jar ends up in build/libs/
./gradlew runClient      # launches a dev client with the mod
```

Versions are pinned in `gradle.properties` (Fabric Loader 0.17.3, Fabric API 0.116.17+1.21.1, Loom 1.11.8). The project uses Mojang's official mappings.

## Testing

`src/autotest` is a dev-only test mod that is never packaged into the release jar. It boots the real client and creates a flat world. It builds a test scene with trees, a "player-built" log wall, a climbing wall and a swing beam. Then it drives everything end to end, taking screenshots along the way:

```bash
xvfb-run -a -s "-screen 0 960x540x24" ./gradlew runAutotest   # or just ./gradlew runAutotest on a desktop
# results: run-autotest/autotest-results.json, screenshots: run-autotest/screenshots/
```

It checks the following:
- spawning and ownership
- orders through chat and through `/spidey`
- the stay/perch pose
- gathering and delivering 10 logs while leaving the player-built wall untouched
- the web-yank and punch animations
- natural-language edge cases and `mobGriefing`
- wall-climbing
- web-zipping to a distant owner
- beating a zombie
- player web-swing lift, jump-release, sneak-zip, no fall damage, and yanking a mob

Latest results: 20/20 passing, on three consecutive runs with different random tree shapes.

## Regenerating assets

All textures and sounds are generated by scripts, so they can be tweaked in code:

```bash
python3 tools/generate_textures.py [preview_dir]   # HD skin (4x player-skin layout), web strand, Web Shooter icon, mod icon
python3 tools/generate_sounds.py                   # "thwip" x3 and web-impact x2 as mono Ogg Vorbis (needs ffmpeg)
```

## Layout

```
src/main/java/com/shriisoot/spiderman/
  SpiderManMod.java           registration, networking, fall-damage grace
  entity/SpiderManEntity.java the companion: modes, orders, inventory, web abilities, combat
  entity/WebLineEntity.java   web strand: projectile, swing anchor, block yank, mob strike
  entity/ai/                  Gather / Deliver / Follow / HoldPosition / DefendOwner goals, wall-climbing navigation
  entity/work/Resource.java   gatherable resources and their chat keywords
  item/                       Web Shooter, spawner-safe spawn egg
  mixin/                      resets the server's floating-kick counter while hanging on a web
  command/SpideyCommands.java chat listener and /spidey
src/client/java/com/shriisoot/spiderman/
  client/render/              animated model, renderers, web strand renderer
  client/WebSwingController   rope physics for the local player
  mixin/client/               aims the player's arm along their web
src/autotest/                 dev-only end-to-end test client (not shipped)
tools/                        texture and sound generators
```

## Note on the name

Spider-Man is Marvel/Disney IP. This is a personal fan project. Don't publish or sell it under that name. For a commercial release, rename it (lang file plus mod metadata) and reskin it (re-run the texture generator with a new palette and emblem).
