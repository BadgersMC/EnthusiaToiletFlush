# Tech Stack

## Language & Toolchain
- Language: Java 21
- Build: Gradle 8.x (Kotlin DSL)
- JDK distribution: Temurin 21 (CI + dev)

## Runtime targets
- Velocity-CTD 3.5.x  (proxy module — `velocity/`)
- Paper 1.21.x        (backend companion — `paper-companion/`)

## Primary dependencies
| Coord | Version | Module | Purpose |
|---|---|---|---|
| com.velocityctd:velocity-api | 3.5.0-SNAPSHOT | velocity | Proxy API (queue, cluster, server registry) |
| io.papermc.paper:paper-api | 1.21.4-R0.1-SNAPSHOT | paper-companion | Backend API |
| net.kyori:adventure-text-minimessage | shipped via velocity-api | velocity | Player-facing strings |
| org.spongepowered:configurate-yaml | 4.1.2 | velocity | YAML config loading |
| com.cronutils:cron-utils | 9.2.1 | velocity | Cron parsing for schedules |
| me.branduzzo:CheckHacks | 1.2.0+ | paper-companion (soft) | Anticheat completion event |

## Repos
- velocityctd-snapshots: https://repo.velocityctd.com/snapshots
- papermc:               https://repo.papermc.io/repository/maven-public/

## Test stack
- JUnit Jupiter 5.10.x
- AssertJ 3.26.x
- Mockito 5.x (infrastructure adapters only)
- Konsist 0.17.x (architecture tests on JVM)

## AI rules
- Use Context7 for VelocityCTD / Paper / Adventure API lookups before reading source.
- Use Semgrep for layer-violation grepping when Konsist is unavailable.
- Never add deps not listed here without updating this file first.
