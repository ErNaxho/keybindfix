# KeybindFix

Mod Fabric **client-side** para Minecraft **1.21.11** que corrige tres bugs
vanilla relacionados con asignar teclas de teclado (en vez del ratón) a
"Usar Objeto / Colocar Bloque" (`useKey`) y "Seleccionar Bloque"
(`pickItemKey`), dentro de cualquier pantalla de inventario (cofres, hornos,
mesa de crafteo, inventario del jugador, dispensadores, contenedores de
otros mods, etc.).

## Bugs que arregla

- **[MC-117771](https://bugs.mojang.com/browse/MC/issues/MC-117771)** —
  Pick Block asignado a teclado no rellena varios slots al "arrastrar".
- **[MC-577](https://bugs.mojang.com/browse/MC/issues/MC-577)** — Pick/Use
  personalizados bloquean controles de inventario que no sean el bind por
  defecto.
- **[MC-19433](https://bugs.mojang.com/browse/MC/issues/MC-19433)** — No se
  puede colocar un solo objeto (click derecho) cuando "Usar Objeto" está
  remapeado a teclado, dentro de pantallas de inventario.

## Cómo funciona (resumen técnico)

**Importante**: Minecraft ignora deliberadamente el estado "pulsado" de las
keybinds de teclado mientras hay una pantalla (GUI) abierta — es un
comportamiento vanilla intencionado, no un bug. Por eso una implementación
basada en `KeyBinding#isPressed()` (como una versión anterior de este mod)
**no funciona** dentro de cofres/inventarios.

La solución correcta usa `ScreenKeyboardEvents` de Fabric API
(`fabric-screen-api-v1`), la API pensada exactamente para recibir
pulsaciones de teclado dentro de pantallas:

1. Un mixin `HandledScreenAccessor` (solo `@Accessor`/`@Invoker`, sin
   `@Inject`) expone `focusedSlot` (el slot bajo el cursor) y
   `onMouseClick(Slot, int, int, SlotActionType)` (la misma lógica que
   ejecuta un click de ratón real) desde fuera de `HandledScreen`.
2. En `KeybindFixClient`, por cada `HandledScreen` que se abre
   (`ScreenEvents.AFTER_INIT`), se registran:
   - `ScreenKeyboardEvents.afterKeyPress`: si la tecla coincide con
     `useKey`/`pickItemKey` y hay un slot bajo el cursor, llama a
     `onMouseClick` con `button=1(right)/PICKUP` o `button=2(middle)/CLONE`
     — arregla el click suelto (MC-19433, MC-577).
   - `ScreenKeyboardEvents.afterKeyRelease`: limpia el estado de "arrastre".
   - `ScreenEvents.afterRender`: mientras la tecla sigue "activa", cada
     frame comprueba si el cursor entró en un slot nuevo y repite el click
     ahí — arregla el arrastre multi-slot (MC-117771).

No se añade ninguna keybind ni menú de configuración nuevos — se reutilizan
tal cual las keybinds vanilla ya existentes, como pediste (opción 1).

## ⚠️ Antes de compilar por primera vez

Minecraft 1.21.11 reescribió el sistema de input (nuevos records
`KeyInput`, `Click`, `MouseButtonInfo` en `net.minecraft.client.input`).
He verificado en las mappings Yarn oficiales de `1.21.11+build.4` que:

- `HandledScreen.keyPressed(KeyInput)` / `keyReleased(KeyInput)` — firma
  usada en este mod.
- `GameOptions.useKey` / `pickItemKey` y `KeyBinding.matchesKey(KeyInput)` —
  confirmados.
- `HandledScreen.focusedSlot` y `onMouseClick(Slot, int, int, SlotActionType)`
  — sin cambios desde hace muchas versiones.
- `render(DrawContext, int, int, float)` — sin cambios.

Aun así, **antes del primer build real**, ejecuta:

```bash
./gradlew genSources
```

y abre la clase generada `HandledScreen` (en
`build/loom-cache` o vía tu IDE tras importar el proyecto) para confirmar
que las firmas exactas de `keyPressed`, `keyReleased` y `render` coinciden
con las usadas en el mixin. Si algún build posterior de Yarn cambia algo,
solo tendrás que ajustar la firma del método `@Inject` correspondiente.

## ⚠️ Paso previo obligatorio: generar el Gradle Wrapper

Este proyecto **no incluye el Gradle Wrapper** (`gradlew`, `gradlew.bat`,
`gradle/wrapper/*`) porque son binarios y no se pueden generar a mano de
forma fiable. Genéralo una vez, usando tu Gradle global instalado, **antes**
de compilar:

```powershell
gradle wrapper --gradle-version 9.7.1
```

**Importante**: Fabric Loom `1.14.10` requiere Gradle con
`plugin.api-version >= 9.2.0` — **no funciona con Gradle 8.x**, así que usa
9.7.1 (o cualquier 9.2+), nunca una versión 8.x.

A partir de ahí, usa siempre `./gradlew` (o `gradlew.bat` en Windows) en vez
de tu `gradle` global, para que la versión de Gradle quede fijada y el build
sea reproducible:

```powershell
.\gradlew.bat build
```

## Sobre el error "Unsupported class file major version 69" (Java 25)

Este error aparece si Gradle se ejecuta sobre un JDK 25 con una versión de
Gradle que no lo soporta (Gradle 8.x, o 9.0). Con el wrapper ya en
**9.7.1** (ver arriba) esto no debería reaparecer, ya que el soporte
completo de Java 25 llegó en Gradle 9.1.0. Aun así, **se recomienda seguir
usando JDK 21** para ejecutar Gradle en este proyecto (ver
`org.gradle.java.home` en `gradle.properties`), porque Loom, Mixin y los
decompiladores internos (basados en ASM) están pensados y probados contra
Java 21, no 25 — usar 25 podría dar errores sutiles más adelante aunque
Gradle en sí ya lo entienda.

Si cambias la ruta de `org.gradle.java.home` o quitas la línea, y vuelve a
salir este error, para los daemons viejos primero:

```powershell
.\gradlew.bat --stop
```

y confirma con `.\gradlew.bat --version` que el "Daemon JVM" sea el 21.

## Sobre el error "Unsupported unpick version"

Si ves este error, significa que la versión de **Fabric Loom** es demasiado
antigua para el formato `unpick v3` que usa Minecraft 1.21.11. Este proyecto
ya fija Loom en `1.14.10` en `build.gradle` (línea `id 'fabric-loom' version
'1.14.10'`). Si aun así falla, comprueba en
[fabricmc.net/develop](https://fabricmc.net/develop) si hay una versión de
Loom más reciente y actualiza esa línea.

## Instalación (para jugar)

1. Instala **Fabric Loader** ≥ 0.18.4 para Minecraft 1.21.11.
2. Descarga **Fabric API** para 1.21.11 y colócalo en `mods/`.
3. Compila el mod (ver abajo) o coloca el `.jar` ya compilado en `mods/`.
4. Lanza el perfil de Fabric 1.21.11.

## Compilar

```bash
./gradlew build
```

El `.jar` resultante aparece en `build/libs/keybindfix-1.0.0.jar`.

## Cómo probarlo

1. Abre **Opciones → Controles** y reasigna:
   - "Usar Objeto / Colocar Bloque" a una tecla de teclado, p.ej. `Control`.
   - "Seleccionar Bloque" a otra tecla de teclado, p.ej. `Alt`.
2. **MC-19433**: abre un cofre, horno, mesa de crafteo o tu inventario, coge
   una pila de un objeto y pulsa tu tecla de "Usar" sobre otro slot — debería
   depositar solo una unidad, igual que un click derecho real.
3. **MC-577**: repite la prueba anterior en distintos contenedores (horno,
   dispensador, mesa de crafteo) para confirmar que no se bloquea en ningún
   inventario.
4. **MC-117771**: en modo creativo, con un objeto en el cursor, mantén
   pulsada tu tecla de "Seleccionar Bloque" y mueve el ratón sobre varios
   slots vacíos del inventario — deberían rellenarse todos, igual que al
   mantener pulsado el click central.

## Posibles problemas

- **Error de compilación en el mixin por firma incorrecta**: `HandledScreenAccessor`
  solo referencia `focusedSlot` y `onMouseClick`, dos miembros muy estables
  desde hace muchas versiones. Si aun así falla, ejecuta `genSources` (ver
  arriba) para confirmar el nombre exacto del campo/método.
- **No pasa nada al pulsar la tecla**: comprueba que la tecla asignada no
  esté ya en conflicto con otra keybind (Minecraft avisa con un ⚠️ en la
  pantalla de Controles).
- **Funciona el click pero no el "arrastre" (MC-117771)**: confirma que
  sigues manteniendo la tecla físicamente pulsada (no es un toggle) y que el
  jugador está en modo creativo, ya que `CLONE` se descarta en supervivencia
  por el propio servidor.
- **"could not find any targets matching 'keyReleased'"**: este error era
  de una versión anterior del mod, que sí inyectaba directamente en
  `keyPressed`/`keyReleased`. La versión actual no usa `@Inject` sobre esos
  métodos en absoluto (ver arriba), así que no debería reaparecer.
- **Mods como Mouse Tweaks**: si usas mods que también modifican el
  comportamiento de clicks en inventarios (Mouse Tweaks, por ejemplo),
  y notas algún comportamiento raro combinándolos con KeybindFix, avisa —
  puede requerir un pequeño ajuste de orden/prioridad entre mods.
