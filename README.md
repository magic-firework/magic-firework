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

## License

This project is licensed under the **Mozilla Public License 2.0 (MPL-2.0)**.
See [LICENSE](LICENSE) for the full text.

In short: you may use, modify and redistribute this mod — including in
closed-source modpacks — but if you distribute modified versions of the
MPL-covered source files, those modified files must be made available in
source form under the MPL-2.0.
