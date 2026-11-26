# Menu Studio 3D — Handoff Notes for Next Agent

## Project State
- Package: `com.menustudio.app` (`compileSdk = 34`, `minSdk = 26`, `targetSdk = 34`, Filament `1.56.0`).
- Verified state prior to packaging:
  - `food_textured_pbr.filamat` compiled (`926K`) with Filament `1.56.0` `matc -p mobile -a opengl -a vulkan`.
  - `./gradlew testDebugUnitTest assembleDebug` passed (`MenuStudioStateTest`: `11` tests, `0` failures, `0` skipped).
  - Live wireless ADB verification (`192.168.8.163:34137`) confirmed empty crash log and clean renders for **Signature Smash** and **Wood-Fired Pie**.

---

## Key Architectural Details & Subtle Fixes Already Applied

1. **Filament JNI Initialization Ordering (`MainActivity.kt` & `FoodTextureStore.kt`)**
   - `MenuStudioScreen` remembers `ProceduralFoodStagePlugin(appContext)` during initial Compose composition *before* `StageHost.attachSurfaceView()` runs.
   - `MainActivity.onCreate()` now calls `Filament.init()` and `Utils.init()` before `setContent { ... }`, and `FoodTextureStore.sampler` uses `by lazy(LazyThreadSafetyMode.NONE)` so no JNI (`TextureSampler.nCreateSampler`) runs prior to `libfilament-jni.so` loading.

2. **Triplanar + UV0 Hybrid Material (`app/src/main/materials/food_textured_pbr.mat`)**
   - `triplanarBlend = 1.0f`: `patty`, `crispy_chicken`, `portobello`, `brioche_base`, `sourdough_crust`, `cheddar`, `mozzarella`, `gruyere`, `rice_base`, `slate`.
   - `triplanarBlend = 0.0f`: `brioche_top` (vertical dome gradient), `tomato`, `lettuce`, `basil`, `bacon`, `pickles`, `avocado`, `house_sauce`, `san_marzano`, `crispy_onions`.
   - `matc` compiler traps avoided:
     - Helper functions inside `fragment { ... }` require `sampler2D` (capital `D`), whereas `parameters` requires `sampler2d`.
     - Object-space normal calculation in `materialVertex` uses `toTangentFrame(mesh_tangents, nObj)` guarded by `#if defined(HAS_ATTRIBUTE_TANGENTS) && !defined(USE_OPTIMIZED_DEPTH_VERTEX_SHADER)` so depth/shadow variants compile cleanly and triplanar coordinates remain locked to geometry during turntable rotation.

3. **SurfaceOrientation Tangent Frame Trap**
   - Small attachment meshes (e.g., `addSesameSeed()` in `BriocheCrownPrimitive.kt`) must assign **non-degenerate UVs** across triangle vertices; identical `(u, v)` on all 3 vertices causes `SurfaceOrientation.Builder()` to emit degenerate tangents that render dark/black.

4. **Procedural Textures (`ProceduralTextureBaker.kt`)**
   - All `stampHeightDot` / sharp `drawCircle` heightfield stamps were removed to eliminate embossed ring artifacts after Sobel filtering.
   - All textures now use seamless periodic `tileableFbm(...)` + 1-pass `boxBlur3x3Tileable(...)` prior to `sobelNormalFromHeight(...)`.
   - Dedicated texture keys added beyond the original set: `sourdough_crust`, `chicken`, `mushroom`, `mozzarella`, `basil`.

---

## Opportunities / Things to Watch for Next Pass

1. **Cold-Start Texture Bake Time (~4.5s on device)**
   - On cold launch, `ProceduralFoodStagePlugin` runs `FoodTextureStore.ensureBakedOffMainThread()` serially on the single `FoodBakeWorker` thread, computing multi-octave `tileableFbm` at `512x512` across 8+ textures before committing the first dish frame (~4.5s observed until initial frame rendered on device).
   - **Recommendation**: Pre-bake static `.webp`/`.png` maps into `app/src/main/assets/textures/<key>_{albedo,normal,orm}.webp` (`FoodTextureStore.tryLoadFromAssets()` already checks `assets/textures/` automatically!) or pre-warm/parallelize across a bounded pool / reduce internal height resolution.

2. **Orbit Pitch Floor Clamping (`FilamentStageHost.kt`)**
   - Home orbit `(0f, 3.4f, 9.0f)` is ~20.7 deg above horizontal. Dragging vertically downward more than ~20 deg pitches the camera underneath `y = 0`, showing the dark underside of `SlateBoardPrimitive`. Clamping camera pitch `eye.y >= 0.35f` or intercepting downward orbit deltas near the floor would improve UX.

3. **Harvest Miso Bowl (`BOWL` Archetype)**
   - Latest on-device screenshot verification covered `Signature Smash` (`STACK`) and `Wood-Fired Pie` (`FLAT`). `Harvest Miso Bowl` (`BOWL` — `rice_base`, `crispy_chicken`, `avocado`, `tomato`, `house_sauce`) passed mesh generation unit tests across all archetypes, so visually inspect `BOWL` on-device too when doing the next render pass.

---

## Build & Compile Commands (Tested)

```bash
# Recompile Filament material (.mat -> .filamat)
matc -p mobile -a opengl -a vulkan \
  -o app/src/main/assets/materials/food_textured_pbr.filamat \
  app/src/main/materials/food_textured_pbr.mat

# Run unit tests & build debug APK
./gradlew testDebugUnitTest assembleDebug --no-daemon
# On ARM64 Linux build hosts without x86 libdl.so.2, add:
#   -Pandroid.aapt2FromMavenOverride=/usr/bin/aapt2
# and run x86-64 matc with:
#   QEMU_LD_PREFIX=/usr/x86_64-linux-gnu
```
