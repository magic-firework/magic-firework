# Content format

This page is the semantic contract for the program language. The JSON Schemas in
`src/main/resources/schemas` remain the structural contract: they say which keys exist and
which ranges are legal, while this page says what those keys *do*. When the two disagree,
the schema wins for shape and this page must be fixed for meaning.

## Profile

A firework profile is `data/<namespace>/fireworks/<name>.json`:

```json
{
  "attributes": { "flight.speed": 1.2, "flight.ticks": 45, "show": "magic-firework:ring12" },
  "program": [
    { "op": "launch" },
    { "op": "wait" },
    { "op": "burst" }
  ]
}
```

`attributes` holds the profile defaults; an item's overlay can override them per stack. The
program is a flat, ordered list of ops. It starts on the tick the rocket is created and the
rocket dies when the list ends.

## Ops

| op | meaning |
|---|---|
| `launch` | Sets the velocity. `speed` is blocks per tick; `flight.direction.*` decides the line. |
| `wait` | Spends time. Without `ticks`, it spends the rocket's flight time, `flight.ticks`. With `ticks: n`, it spends exactly `n` ticks (1..400). |
| `burst` | Bakes and plays the profile's `show`. |
| `spawn` | Creates child rockets: `profile` (with `count` and `delay`), or `children` (item slot indices), never both. |
| `call` | Runs another profile's program inline. |
| `sound` | Plays one sound: `id`, optional `volume` and `pitch`. |
| `light` | Places a temporary light block for `ticks` (0..400). |
| `end` | Ends the program early. |

A `spawn` by `children` names slots in the launching item's nested tree; slot `n` is the
`n`-th entry of that item's child list, and a slot the item does not fill spawns nothing.
`count` is only for the `profile` form and expands at parse time, so the interpreter sees a
flat list of concrete children.

## Flight and time

Flight is one straight line. `launch` sets the speed and the runtime brakes by `0.08` blocks
per tick along that same line, so the rocket climbs, stops, and hovers until its `wait` ends.
A launch speed is therefore a height, roughly `v² / 0.16` blocks.

Direction comes only from attributes, never from an op: `flight.direction.yaw` and
`flight.direction.pitch` are measured from whoever launched the rocket. Both at 0 is a
straight ascent. Hitting a block ends the current `wait`, so an impact bursts on the spot.

## Show and flicker

`show` names an installed show id or carries an inline show document. A burst bakes the
points once and applies the per-burst attributes (`show.color`, `show.size`, `show.yaw`,
`show.pitch`) at send time.

`flicker: true` is an attribute, not an op: every burst of that rocket sparkles like a
vanilla firework star with Flicker set. An item may carry it as an overlay without touching
content.

## Limits

Structural bounds live in the schemas and `ContentLimits`; server policy (spawn budgets,
wait length, points per tick) lives in `FireworkLimits`. No attribute can raise either. A
data pack that exceeds a limit is rejected at load time and logged with its id; the reload
drops that file rather than keeping the previous version.
