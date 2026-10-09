# Nova Client (Fabric 1.21.11)

* Open GUI: **Left Alt** (change `NovaClient.GUI_KEY`)
* Config: `.minecraft/config/nova.json` (auto-saved when the GUI closes / game exits)
* Build: add the Gradle wrapper from the Fabric example mod (or run `gradle wrapper`), then `./gradlew build`.
  Jar: `build/libs/nova-client-1.0.0.jar`

## Version-sensitive spots (1.21.11 changed rendering + input a lot)
| File | What may need a tweak |
|------|-----------------------|
| `render/EspRenderer.java` | RenderPipeline/RenderSetup/RenderLayer creation, `Camera#getCameraPos()` (older: `getPos()`), imports |
| `NovaClient.java` | `WorldRenderEvents.AFTER_TRANSLUCENT` + `ctx.matrices()` |
| `module/EspModule.java` | `ChestBlock.getFacing`, `getViewDistance()` |
