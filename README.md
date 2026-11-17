# Magic Firework

a firework mod for minecraft

## Requirements

- **JDK 25+** for Gradle itself (Fabric Loom 1.19 requires it). The wrapper picks it
  up automatically via [`gradle/gradle-daemon-jvm.properties`](gradle/gradle-daemon-jvm.properties),
  so `JAVA_HOME` may point elsewhere — just make sure a JDK 25 is installed.
- The mod is compiled for **Java 17** (Minecraft 1.20.1), and `fabric.mod.json`
  declares `java >= 17`.

## Build

```bash
./gradlew build
```

## Programs

A firework profile is `data/<namespace>/fireworks/<name>.json`: default `attributes` plus a
straight-line list of `program` ops. A program starts when the rocket spawns and the rocket dies
when the list ends.

```json
{
  "attributes": { "flight.speed": 0.2, "flight.ticks": 45, "show": "magic-firework:ring12" },
  "program": [
    { "op": "launch" },
    { "op": "wait" },
    { "op": "burst" }
  ]
}
```

| op | what it does |
| --- | --- |
| `launch` | sets the velocity: `speed`, and the direction from `flight.direction.*` |
| `wait` | spends time — no fields means the rocket's **flight time** (`flight.ticks`, what the crafting table raises with string), `ticks: n` spends a literal count |
| `burst` | bakes and plays the `show` |
| `spawn` | creates child rockets: `profile` for content, or `children: [n]` for the launching item's tree, plus `count` (profile only) and `delay` |
| `call` | runs another profile's program inline |
| `sound`, `light` | one-shot effects |
| `end` | ends the program early |

`flicker: true` is an attribute, not an op: it makes every burst of the rocket sparkle like a
vanilla firework star with Flicker set, and an item can carry it as an overlay
(`/give ... {MagicFirework:{profile:"...",overlay:{"flicker":true}}}`) without touching content.

Direction comes only from the attributes, never from an op: `flight.direction.mode` is `up` by
default, or `relative` with `flight.direction.yaw` / `pitch` measured from whoever launched the
rocket — that is what the crafting table's two dials write.

Hitting a block ends the current `wait`, so an impact bursts on the spot.

### Nesting

A rocket can carry child rockets in its item NBT, and a child can carry its own:

```text
MagicFirework: {
  profile: "magic-firework:ring12",
  overlay: { "flight.speed": 0.2 },
  children: [ { profile: "magic-firework:ring" }, { profile: "magic-firework:cube" } ]
}
```

`spawn` names a child by its slot; the tooltip lists the same `#0`, `#1`, ... numbering. A slot
the item does not fill spawns nothing. There is no in-game way to compose the tree yet — the
editor will build it; until then it comes from item NBT.

Reads are bounded by `FireworkLimits.maxNestingDepth` / `maxNestingNodes`, and `max.depth` /
`max.children` still decide at launch time how far a show may go.

## License

This project is licensed under the **Mozilla Public License 2.0 (MPL-2.0)**.
See [LICENSE](LICENSE) for the full text.

In short: you may use, modify and redistribute this mod — including in
closed-source modpacks — but if you distribute modified versions of the
MPL-covered source files, those modified files must be made available in
source form under the MPL-2.0.
