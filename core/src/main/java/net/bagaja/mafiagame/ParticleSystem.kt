package net.bagaja.mafiagame

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.*
import com.badlogic.gdx.graphics.g3d.*
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute
import com.badlogic.gdx.graphics.g3d.attributes.IntAttribute
import com.badlogic.gdx.graphics.g3d.attributes.TextureAttribute
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.math.collision.Ray
import com.badlogic.gdx.utils.Array
import kotlin.math.acos
import kotlin.random.Random

/**
 * Defines the properties of a particle effect, now matching your asset library.
 */
enum class ParticleEffectType(
    val displayName: String,
    val texturePaths: kotlin.Array<String>,
    val frameDuration: Float,
    val isLooping: Boolean,
    val particleLifetime: Float,
    val lifetimeVariance: Float = 0f, // How much to randomize lifetime
    val swingChance: Float = 0f, // Chance that a particle will swing
    val swingAmplitude: Float = 15f, // How far it swings in degrees
    val swingAmplitudeVariance: Float = 0f,
    val swingFrequency: Float = 2f, // How many full swings per second
    val swingFrequencyVariance: Float = 0f,
    val particleCount: IntRange,
    val initialSpeed: Float,
    val speedVariance: Float,
    val gravity: Float,
    val scale: Float,
    val scaleVariance: Float,
    val sizeRandomnessChance: Float = 1.0f,
    val fadeIn: Float = 0.1f, // Time to fade in
    val fadeOut: Float = 0.5f,  // Time to fade out
    val collidesWithWorld: Boolean = false,
    val randomizeTexture: Boolean = false
) {
    MUSIC_NOTE(
        displayName = "Music Note",
        texturePaths = arrayOf(
            "textures/particles/note/note_1.png",
            "textures/particles/note/note_2.png",
            "textures/particles/note/note_3.png",
            "textures/particles/note/note_4.png",
            "textures/particles/note/note_5.png"
        ),
        frameDuration = 0.1f,   // Not used for animation, but needs a value
        isLooping = false,      // This is NOT an animation loop
        particleLifetime = 2f,
        lifetimeVariance = 0.5f,  // Total lifetime will be between 1.0 and 2.0 seconds
        particleCount = 1..1,
        initialSpeed = 1.0f,      // A gentle initial push upwards
        speedVariance = 0.5f,
        gravity = 1.5f,           // Positive gravity makes it float up
        scale = 0.5f,
        scaleVariance = 0.3f,
        fadeIn = 0.2f,
        fadeOut = 0.5f,
        collidesWithWorld = false,
        randomizeTexture = true
    ),
    BLOOD_SPLATTER_1(
        "Blood Splatter 1",
        arrayOf("textures/particles/blood/blood_frame.png"), // Single image
        frameDuration = 0.1f, isLooping = false, particleLifetime = 10.0f, // Longer lifetime for splatters
        particleCount = 1..1, initialSpeed = 0f, speedVariance = 0f, gravity = -0.1f, // Sticks to ground
        scale = 1.2f, scaleVariance = 0.2f, fadeIn = 0.1f, fadeOut = 2.0f
    ),
    BLOOD_SPLATTER_2(
        "Blood Splatter 2",
        arrayOf("textures/particles/blood/blood_frame_2.png"), // Single image
        frameDuration = 0.1f, isLooping = false, particleLifetime = 10.0f,
        particleCount = 1..1, initialSpeed = 0f, speedVariance = 0f, gravity = -0.1f,
        scale = 1.3f, scaleVariance = 0.2f, fadeIn = 0.1f, fadeOut = 2.0f
    ),
    BLOOD_SPLATTER_3(
        "Blood Splatter 3",
        arrayOf("textures/particles/blood/blood_frame_3.png"), // Single image
        frameDuration = 0.1f, isLooping = false, particleLifetime = 10.0f,
        particleCount = 1..1, initialSpeed = 0f, speedVariance = 0f, gravity = -0.1f,
        scale = 1.1f, scaleVariance = 0.2f, fadeIn = 0.1f, fadeOut = 2.0f
    ),
    BLOOD_DRIP(
        "Blood Drip",
        arrayOf("textures/particles/blood/blood_drops.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 2.0f,
        particleCount = 5..10, initialSpeed = 5f, speedVariance = 3f, gravity = -20f,
        scale = 0.5f, scaleVariance = 0.2f
    ),

    // DUST PARTICLES
    DUST_SMOKE_LIGHT(
        "Light Dust Smoke",
        arrayOf("textures/particles/dust/smoke_1.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 1.5f,
        particleCount = 1..1, initialSpeed = 1.5f, speedVariance = 1f, gravity = 0.5f,
        scale = 1.0f, scaleVariance = 0.5f, fadeOut = 1.0f
    ),
    DUST_SMOKE_MEDIUM(
        "Medium Dust Smoke",
        arrayOf("textures/particles/dust/smoke_2.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 2.0f,
        particleCount = 1..1, initialSpeed = 0.2f, speedVariance = 0.1f, gravity = 0.2f,
        scale = 0.9f, scaleVariance = 0.3f, fadeOut = 1.8f
    ),
    DUST_SMOKE_HEAVY(
        "Heavy Dust Smoke",
        arrayOf("textures/particles/dust/smoke_3.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 1.8f,
        particleCount = 1..2, initialSpeed = 1.0f, speedVariance = 0.8f, gravity = 0.3f,
        scale = 1.2f, scaleVariance = 0.4f, fadeOut = 1.2f
    ),
    DUST_SMOKE_DEFAULT(
        "Heavy Dust Smoke",
        arrayOf("textures/particles/dust/smoke.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 1.8f,
        particleCount = 1..2, initialSpeed = 1.0f, speedVariance = 0.8f, gravity = 0.3f,
        scale = 1.2f, scaleVariance = 0.4f, fadeOut = 1.2f
    ),

    // GUN SMOKE PARTICLES
    GUN_SMOKE_INITIAL(
        "Gun Smoke Initial",
        arrayOf("textures/particles/gun_smoke/gun_smoke.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 1.2f,
        particleCount = 1..1, initialSpeed = 6f, speedVariance = 2f, gravity = 2f,
        scale = 1.5f, scaleVariance = 0.3f, fadeIn = 0.0f, fadeOut = 0.5f
    ),
    GUN_SMOKE_BURST_1(
        "Gun Smoke Burst 1",
        arrayOf("textures/particles/gun_smoke/gun_smoke_1.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 0.8f,
        particleCount = 1..1, initialSpeed = 8f, speedVariance = 1f, gravity = 1.5f,
        scale = 1.3f, scaleVariance = 0.2f, fadeIn = 0.0f, fadeOut = 0.4f
    ),
    GUN_SMOKE_DENSE(
        "Gun Smoke Dense",
        arrayOf("textures/particles/gun_smoke/gun_smoke_2.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 1.0f,
        particleCount = 1..1, initialSpeed = 7f, speedVariance = 1.5f, gravity = 1.8f,
        scale = 1.4f, scaleVariance = 0.25f, fadeIn = 0.0f, fadeOut = 0.45f
    ),
    GUN_SMOKE_BURST_2(
        "Gun Smoke Burst 2",
        arrayOf("textures/particles/gun_smoke/gun_smoke_3.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 0.6f,
        particleCount = 1..1, initialSpeed = 9f, speedVariance = 1f, gravity = 1.2f,
        scale = 1.2f, scaleVariance = 0.2f, fadeIn = 0.0f, fadeOut = 0.3f
    ),
    GUN_SMOKE_WISPY(
        "Gun Smoke Wispy",
        arrayOf("textures/particles/gun_smoke/gun_smoke_4.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 1.5f,
        particleCount = 1..1, initialSpeed = 5f, speedVariance = 2f, gravity = 2.5f,
        scale = 1.6f, scaleVariance = 0.4f, fadeIn = 0.0f, fadeOut = 0.7f
    ),
    GUN_SMOKE_BURST_3(
        "Gun Smoke Burst 3",
        arrayOf("textures/particles/gun_smoke/gun_smoke_5.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 0.7f,
        particleCount = 1..1, initialSpeed = 8.5f, speedVariance = 1.2f, gravity = 1.4f,
        scale = 1.25f, scaleVariance = 0.2f, fadeIn = 0.0f, fadeOut = 0.35f
    ),
    GUN_SMOKE_FINAL(
        "Gun Smoke Final",
        arrayOf("textures/particles/gun_smoke/gun_smoke_6.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 1.8f,
        particleCount = 1..1, initialSpeed = 4f, speedVariance = 2f, gravity = 3f,
        scale = 1.7f, scaleVariance = 0.3f, fadeIn = 0.0f, fadeOut = 0.8f
    ),
    GUN_SMOKE_DISSIPATING(
        "Gun Smoke Dissipating",
        arrayOf("textures/particles/gun_smoke/gun_smoke_7.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 2.0f,
        particleCount = 1..1, initialSpeed = 3f, speedVariance = 1.5f, gravity = 3.5f,
        scale = 1.8f, scaleVariance = 0.4f, fadeIn = 0.0f, fadeOut = 1.0f
    ),
    GUN_SMOKE_THIN(
        "Gun Smoke Thin",
        arrayOf("textures/particles/gun_smoke/gun_smoke_8.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 2.2f,
        particleCount = 1..1, initialSpeed = 2.5f, speedVariance = 1f, gravity = 4f,
        scale = 1.9f, scaleVariance = 0.5f, fadeIn = 0.0f, fadeOut = 1.2f
    ),

    // REGULAR SMOKE PARTICLES - Separated from animated versions
    SMOKE_FRAME_1(
        "Rising Smoke 1",
        arrayOf("textures/particles/snoke/smoke_frame.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 4.0f,
        particleCount = 1..1, initialSpeed = 1f, speedVariance = 0.5f, gravity = 1.5f,
        scale = 2.0f, scaleVariance = 0.5f, fadeIn = 0.5f, fadeOut = 2.0f
    ),
    SMOKE_FRAME_2(
        "Puffing Smoke 1",
        arrayOf("textures/particles/snoke/smoke_frame_2.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 3.5f,
        particleCount = 1..1, initialSpeed = 1.2f, speedVariance = 0.6f, gravity = 1.8f,
        scale = 1.8f, scaleVariance = 0.4f, fadeIn = 0.4f, fadeOut = 1.5f
    ),
    SMOKE_FRAME_3(
        "Rising Smoke 2",
        arrayOf("textures/particles/snoke/smoke_frame_3.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 4.2f,
        particleCount = 1..1, initialSpeed = 0.9f, speedVariance = 0.4f, gravity = 1.6f,
        scale = 2.1f, scaleVariance = 0.6f, fadeIn = 0.6f, fadeOut = 2.1f
    ),
    SMOKE_FRAME_4(
        "Puffing Smoke 2",
        arrayOf("textures/particles/snoke/smoke_frame_4.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 3.3f,
        particleCount = 1..1, initialSpeed = 1.3f, speedVariance = 0.7f, gravity = 1.9f,
        scale = 1.7f, scaleVariance = 0.4f, fadeIn = 0.3f, fadeOut = 1.4f
    ),
    SMOKE_FRAME_5(
        "Rising Smoke 3",
        arrayOf("textures/particles/snoke/smoke_frame_5.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 4.1f,
        particleCount = 1..1, initialSpeed = 0.8f, speedVariance = 0.3f, gravity = 1.4f,
        scale = 2.2f, scaleVariance = 0.7f, fadeIn = 0.7f, fadeOut = 2.2f
    ),
    SMOKE_FRAME_6(
        "Puffing Smoke 3",
        arrayOf("textures/particles/snoke/smoke_frame_6.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 3.4f,
        particleCount = 1..1, initialSpeed = 1.1f, speedVariance = 0.5f, gravity = 2.0f,
        scale = 1.9f, scaleVariance = 0.5f, fadeIn = 0.4f, fadeOut = 1.6f
    ),
    SMOKE_FRAME_7(
        "Rising Smoke 4",
        arrayOf("textures/particles/snoke/smoke_frame_7.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 3.9f,
        particleCount = 1..1, initialSpeed = 1.0f, speedVariance = 0.4f, gravity = 1.7f,
        scale = 2.0f, scaleVariance = 0.6f, fadeIn = 0.5f, fadeOut = 1.9f
    ),
    SMOKE_FRAME_8(
        "Puffing Smoke 4",
        arrayOf("textures/particles/snoke/smoke_frame_8.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 3.6f,
        particleCount = 1..1, initialSpeed = 1.4f, speedVariance = 0.8f, gravity = 2.1f,
        scale = 1.8f, scaleVariance = 0.4f, fadeIn = 0.3f, fadeOut = 1.7f
    ),
    SMOKE_FRAME_9(
        "Rising Smoke 5",
        arrayOf("textures/particles/snoke/smoke_frame_9.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 4.3f,
        particleCount = 1..1, initialSpeed = 0.7f, speedVariance = 0.3f, gravity = 1.3f,
        scale = 2.3f, scaleVariance = 0.8f, fadeIn = 0.8f, fadeOut = 2.3f
    ),

    // FACTORY SMOKE PARTICLES - Separated from animated versions
    FACTORY_SMOKE_INITIAL(
        "Chimney Smoke Initial",
        arrayOf("textures/particles/factory_smoke/factory_smoke.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 6.0f,
        particleCount = 1..1, initialSpeed = 2f, speedVariance = 0.5f, gravity = 3f,
        scale = 4.0f, scaleVariance = 1.0f, fadeIn = 1.0f, fadeOut = 2.5f
    ),
    FACTORY_SMOKE_BUILDING(
        "Chimney Smoke Building",
        arrayOf("textures/particles/factory_smoke/factory_smoke_1.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 5.5f,
        particleCount = 1..1, initialSpeed = 2.2f, speedVariance = 0.6f, gravity = 3.2f,
        scale = 3.8f, scaleVariance = 0.9f, fadeIn = 0.9f, fadeOut = 2.3f
    ),
    FACTORY_SMOKE_WISPY(
        "Wispy Factory Smoke",
        arrayOf("textures/particles/factory_smoke/factory_smoke_2.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 5.0f,
        particleCount = 1..1, initialSpeed = 1.0f, speedVariance = 0.3f, gravity = 1.0f,
        scale = 2.8f, scaleVariance = 0.4f, fadeIn = 1.0f, fadeOut = 2.0f
    ),
    FACTORY_SMOKE_THICK(
        "Thick Factory Smoke",
        arrayOf("textures/particles/factory_smoke/factory_smoke_3.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 2.5f,
        particleCount = 1..1, initialSpeed = 2.5f, speedVariance = 0.5f, gravity = 3.5f,
        scale = 3.0f, scaleVariance = 0.5f, fadeIn = 0.2f, fadeOut = 1.5f
    ),
    FACTORY_SMOKE_DENSE(
        "Dense Factory Smoke",
        arrayOf("textures/particles/factory_smoke/factory_smoke_4.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 5.8f,
        particleCount = 1..1, initialSpeed = 1.8f, speedVariance = 0.4f, gravity = 2.8f,
        scale = 3.9f, scaleVariance = 0.8f, fadeIn = 0.8f, fadeOut = 2.4f
    ),
    FACTORY_SMOKE_DISSIPATING(
        "Dissipating Factory Smoke",
        arrayOf("textures/particles/factory_smoke/factory_smoke_5.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 4.5f,
        particleCount = 1..1, initialSpeed = 1.2f, speedVariance = 0.4f, gravity = 1.2f,
        scale = 3.2f, scaleVariance = 0.6f, fadeIn = 1.2f, fadeOut = 2.2f
    ),

    // NON-SMOKE PARTICLES
    EXPLOSION(
        "Explosion",
        arrayOf("textures/particles/explosion_effect.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 0.5f,
        particleCount = 1..1, initialSpeed = 0f, speedVariance = 0f, gravity = 0f,
        scale = 3.0f, scaleVariance = 0f, fadeIn = 0.0f, fadeOut = 0.5f
    ),
    FIRE_FLAME(
        "Fire Flame",
        arrayOf("textures/particles/fire_flame.png"), // Can be made into an animation if you have more frames
        frameDuration = 0.1f, isLooping = true, particleLifetime = 3.0f,
        particleCount = 1..1, initialSpeed = 0.5f, speedVariance = 0.2f, gravity = 2f, // Flames go up
        scale = 1.5f, scaleVariance = 0.3f, fadeOut = 1.0f
    ),
    BODY_FLAME(
        "Body Flame",
        arrayOf("textures/particles/fire_flame.png"), // <-- CORRECTED: Using the detailed flame texture
        frameDuration = 0.1f, isLooping = false, particleLifetime = 0.5f, lifetimeVariance = 0.2f,
        particleCount = 1..1, initialSpeed = 1.0f, speedVariance = 0.5f, gravity = 3.5f, // Rises quickly
        scale = 0.6f, scaleVariance = 0.25f, fadeIn = 0.05f, fadeOut = 0.3f
    ),
    FIRE_SPREAD(
        displayName = "Spreading Fire",
        texturePaths = arrayOf(
            "textures/particles/fire_spread/fire_spread_frame_one.png",
            "textures/particles/fire_spread/fire_spread_frame_two.png",
            "textures/particles/fire_spread/fire_spread_frame_three.png",
            "textures/particles/fire_spread/fire_spread_frame_fourth.png",
            "textures/particles/fire_spread/fire_spread_frame_five.png"
        ),
        frameDuration = 0.15f, isLooping = true, particleLifetime = 10f,
        particleCount = 1..1, initialSpeed = 0f, speedVariance = 0f, gravity = 0f,
        scale = 10.0f, scaleVariance = 1.5f, fadeIn = 0.2f, fadeOut = 1.5f
    ),
    FIRED_SHOT(
        "Shot",
        arrayOf("textures/particles/gun_smoke/gun_smoke_6.png"), // Can be made into an animation if you have more frames
        frameDuration = 0.1f, isLooping = false, particleLifetime = 3.0f, lifetimeVariance = 0.4f, swingChance = 0.75f, swingAmplitude = 4f, swingAmplitudeVariance = 5f,  swingFrequencyVariance = 0.5f, swingFrequency = 0.5f,
        particleCount = 1..1, initialSpeed = 0.5f, speedVariance = 0.2f, gravity = 2f, // Flames go up
        scale = 1.5f, scaleVariance = 0.3f, fadeIn = 0.05f, fadeOut = 1.0f
    ),
    ITEM_GLOW(
        "Item Glow",
        arrayOf("textures/particles/item_glow.png"),
        frameDuration = 0.1f, isLooping = true, particleLifetime = 1.5f,
        particleCount = 1..1, initialSpeed = 0f, speedVariance = 0f, gravity = 0f,
        scale = 2.0f, scaleVariance = 0f, fadeIn = 0.5f, fadeOut = 0.5f
    ),
    BOOT_PRINTS(
        "Boot Prints",
        arrayOf("textures/particles/boot_prints.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 5.0f,
        particleCount = 1..1, initialSpeed = 0f, speedVariance = 0f, gravity = 0f, // No gravity, sticks to ground
        scale = 1.0f, scaleVariance = 0f, fadeIn = 0.2f, fadeOut = 2.0f
    ),
    MOVEMENT_WIPE(
        "Movement Wipe",
        arrayOf("textures/particles/wipe.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 0.4f,
        particleCount = 1..1, initialSpeed = 0f, speedVariance = 0f, gravity = 0f,
        scale = 1.5f, scaleVariance = 0.3f, fadeIn = 0.05f, fadeOut = 0.35f
    ),
    MOVEMENT_WIPE_VERTICAL(
        "Movement Wipe Vertical",
        arrayOf("textures/particles/wipe.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 0.4f,
        particleCount = 1..1, initialSpeed = 0f, speedVariance = 0f, gravity = 0f,
        scale = 1.5f, scaleVariance = 0.3f, fadeIn = 0.05f, fadeOut = 0.35f
    ),
    CAR_EXPLOSION(
        "Car Explosion",
        arrayOf(
            "textures/particles/car_explosion/car_explosion_start.png",
            "textures/particles/car_explosion/car_explosion.png"
        ),
        frameDuration = 0.2f,
        isLooping = false,
        particleLifetime = 0.6f, // The effect will last 1.2 seconds total
        particleCount = 1..1,
        initialSpeed = 0f,
        speedVariance = 0f,
        gravity = 0f,
        scale = 15f,
        scaleVariance = 2f,
        fadeIn = 0.2f,
        fadeOut = 0.1f // A quick fade at the very end
    ),
    CAR_EXPLOSION_BIG(
        "Big Car Explosion",
        arrayOf(
            "textures/particles/car_explosion/car_start_explosion.png",
            "textures/particles/car_explosion/car_explosion_big.png"
        ),
        frameDuration = 0.15f,
        isLooping = false,
        particleLifetime = 0.5f,
        particleCount = 1..1,
        initialSpeed = 0f,
        speedVariance = 0f,
        gravity = 0f,
        scale = 20f,
        scaleVariance = 3f,
        fadeIn = 0.1f,
        fadeOut = 0.3f
    ),
    BURNED_ASH(
        "Burned Ash",
        arrayOf("textures/particles/bones/burned_ash.png"),
        frameDuration = 0.1f,
        isLooping = false,
        particleLifetime = 15.0f, // Stays around for a while
        particleCount = 1..1,
        initialSpeed = 0f,      // Stays in place
        speedVariance = 0f,
        gravity = 0f,           // No gravity
        scale = 2f,
        scaleVariance = 0.5f,
        fadeIn = 1.5f,          // Fades IN over 1.5 seconds to match the enemy fade out
        fadeOut = 5.0f          // Fades out slowly after a long time
    ),
    DYNAMITE_EXPLOSION(
        "Dynamite Explosion",
        arrayOf("textures/particles/dynamite_explosion/dynamite_exploding.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 0.7f, // A quick, powerful blast
        particleCount = 1..1, initialSpeed = 0f, speedVariance = 0f, gravity = 0f,
        scale = 7f, scaleVariance = 1f, fadeIn = 0.0f, fadeOut = 0.3f
    ),
    DYNAMITE_EXPLOSION_AREA_ONE(
        "Dynamite Scorch 1",
        arrayOf("textures/particles/dynamite_explosion/explosion_area_one.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 25f,
        particleCount = 1..1, initialSpeed = 0f, speedVariance = 0f, gravity = 0f,
        scale = 17f, scaleVariance = 1.0f, fadeIn = 0.3f, fadeOut = 4.0f
    ),
    DYNAMITE_EXPLOSION_AREA_TWO(
        "Dynamite Scorch 2",
        arrayOf("textures/particles/dynamite_explosion/explosion_area_two.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 25f,
        particleCount = 1..1, initialSpeed = 0f, speedVariance = 0f, gravity = 0f,
        scale = 10f, scaleVariance = 1.0f, fadeIn = 0.3f, fadeOut = 4.0f
    ),
    FIRE_BURN_SPOT(
        "Fire Burn Spot",
        arrayOf("textures/particles/fire_spread/fire_spot.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 3600f,
        particleCount = 1..1, initialSpeed = 0f, speedVariance = 0f, gravity = 0f,
        scale = 8f, scaleVariance = 0.5f, fadeIn = 7.0f, fadeOut = 0f
    ),
    BULLET_HOLE_PLAYER(
        "Player Bullet Hole",
        arrayOf("textures/player/weapons/bullet_hole_player.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 60.0f, // Lasts for a minute
        particleCount = 1..1, initialSpeed = 0f, speedVariance = 0f, gravity = 0f, // Stays put
        scale = 0.5f, scaleVariance = 0.1f, fadeIn = 0.0f, fadeOut = 5.0f // Fades out slowly at the end
    ),
    BULLET_HOLE_ENEMY(
        "Enemy Bullet Hole",
        arrayOf("textures/player/weapons/bullet_hole_enemy.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 60.0f,
        particleCount = 1..1, initialSpeed = 0f, speedVariance = 0f, gravity = 0f,
        scale = 0.5f, scaleVariance = 0.1f, fadeIn = 0.0f, fadeOut = 5.0f
    ),
    RAIN_SPLASH(
        "Rain Splash",
        arrayOf(
            "textures/particles/rain/splash.png",
            "textures/particles/rain/splash_two.png"
        ),
        frameDuration = 0.08f, isLooping = false, particleLifetime = 0.16f,
        particleCount = 1..1, initialSpeed = 0f, speedVariance = 0f, gravity = 0f,
        scale = 1.0f, scaleVariance = 0.3f, fadeIn = 0.0f, fadeOut = 0.1f
    ),
    SHELL_CASING_PLAYER(
        "Player Shell Casing",
        arrayOf("textures/player/weapons/cartridge_player.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 4.0f, // Longer lifetime to see them on ground
        particleCount = 1..1, initialSpeed = 8f, speedVariance = 2f, gravity = -35f,
        scale = 0.3f, scaleVariance = 0.05f, fadeIn = 0.0f, fadeOut = 1.0f,
        collidesWithWorld = true
    ),
    SHELL_CASING_ENEMY(
        "Enemy Shell Casing",
        arrayOf("textures/player/weapons/cartridge_enemy.png"),
        frameDuration = 0.1f, isLooping = false, particleLifetime = 4.0f, // Longer lifetime
        particleCount = 1..1, initialSpeed = 8f, speedVariance = 2f, gravity = -35f,
        scale = 0.3f, scaleVariance = 0.05f, fadeIn = 0.0f, fadeOut = 1.0f,
        collidesWithWorld = true
    );

    val isAnimated: Boolean get() = texturePaths.size > 1
}

/**
 * Represents a single particle active in the world.
 */
data class GameParticle(
    val type: ParticleEffectType,
    val instance: ModelInstance,
    val animationSystem: AnimationSystem,
    val position: Vector3,
    val velocity: Vector3,
    var life: Float, // Remaining lifetime
    val initialLife: Float, // For calculating fade
    var scale: Float, // Store the particle's scale
    var animatesRotation: Boolean = false,
    var currentRotationY: Float = 0f,
    var targetRotationY: Float = 0f,
    var isSurfaceOriented: Boolean = false,
    val swings: Boolean,
    val swingAmplitude: Float,
    val swingFrequency: Float,
    var swingAngle: Float = 0f,
    val gravity: Float,
    var hasCollided: Boolean = false,
    val washAwayComponent: WashAwayComponent = WashAwayComponent() // Add the component
) {
    val material: Material = instance.materials.first()
    private val blendingAttribute: BlendingAttribute = material.get(BlendingAttribute.Type) as BlendingAttribute
    private val rotationSpeed: Float = 360f // Degrees per second, matches player

    fun update(deltaTime: Float, sceneManager: SceneManager) {
        if (!isSurfaceOriented && !hasCollided) {

            // Basic physics
            if (type.collidesWithWorld) {
                val ray = Ray(position, velocity.cpy().nor())
                val distanceThisFrame = velocity.len() * deltaTime

                // Perform a raycast to see if the particle will hit anything this frame
                val collisionResult = sceneManager.checkCollisionForRay(ray, distanceThisFrame)

                if (collisionResult != null && collisionResult.type == HitObjectType.BLOCK) {
                    hasCollided = true // It has landed
                    velocity.set(Vector3.Zero) // Stop all movement
                    position.set(collisionResult.hitPoint) // Snap to the impact point

                    // Final resting transform: lay it flat on the surface
                    instance.transform.setToTranslation(position)
                    // Use the surface normal to orient it correctly
                    val up = Vector3.Y
                    val rotationAxis = up.cpy().crs(collisionResult.surfaceNormal).nor()
                    val angle = Math.toDegrees(Math.acos(up.dot(collisionResult.surfaceNormal).toDouble())).toFloat()
                    instance.transform.rotate(rotationAxis, angle)
                    // Add a random final spin on the ground
                    instance.transform.rotate(collisionResult.surfaceNormal, Random.nextFloat() * 360f)
                    instance.transform.scale(scale, scale, scale)
                }
            }

            // Standard Physics (only if not collided)
            if (!hasCollided) {
                velocity.y += this.gravity * deltaTime
                position.add(velocity.x * deltaTime, velocity.y * deltaTime, velocity.z * deltaTime)
            }
        }

        // Lifetime and fading
        life -= deltaTime
        updateOpacity()

        // Only run these animations if the particle hasn't stopped on the ground
        if (!hasCollided) {
            // Animation
            if (type.isAnimated) {
                animationSystem.update(deltaTime)
                animationSystem.getCurrentTexture()?.let {
                    material.set(TextureAttribute.createDiffuse(it))
                }
            }

            if (animatesRotation) {
                updateRotation(deltaTime)
            }

            // Update swinging animation if enabled
            if (this.swings) {
                updateSwinging()
            }

            if (!isSurfaceOriented) {
                // Update the model's transform
                updateTransform()
            }
        }
    }

    private fun updateOpacity() {
        val timeAlive = initialLife - life
        var opacity = 1f

        // Fade In
        if (type.fadeIn > 0 && timeAlive < type.fadeIn) {
            opacity = timeAlive / type.fadeIn
        }

        // Fade Out
        if (type.fadeOut > 0 && life < type.fadeOut) {
            opacity = life / type.fadeOut
        }

        blendingAttribute.opacity = opacity.coerceIn(0f, 1f)
    }

    private fun updateRotation(deltaTime: Float) {
        var rotationDifference = targetRotationY - currentRotationY
        if (rotationDifference > 180f) rotationDifference -= 360f
        else if (rotationDifference < -180f) rotationDifference += 360f

        if (kotlin.math.abs(rotationDifference) > 1f) {
            val rotationStep = rotationSpeed * deltaTime
            if (rotationDifference > 0f) currentRotationY += kotlin.math.min(rotationStep, rotationDifference)
            else currentRotationY += kotlin.math.max(-rotationStep, rotationDifference)
            if (currentRotationY >= 360f) currentRotationY -= 360f
            else if (currentRotationY < 0f) currentRotationY += 360f
        } else {
            currentRotationY = targetRotationY
        }
    }

    private fun updateSwinging() {
        val timeAlive = initialLife - life
        swingAngle = kotlin.math.sin(timeAlive * this.swingFrequency * 2 * kotlin.math.PI.toFloat()) * this.swingAmplitude
    }

    fun updateTransform() {
        // For billboard particles
        instance.transform.idt()
        instance.transform.setTranslation(position) // Set position

        // 1. Apply the base Y-axis rotation first (for facing left/right)
        if (animatesRotation) {
            instance.transform.rotate(Vector3.Y, currentRotationY) // Apply animated rotation
        }

        // 2. Apply the swing/rocking animation
        if (this.swings) {
            instance.transform.rotate(Vector3.Z, swingAngle)
        }

        // 3. Re-apply scale last
        instance.transform.scale(scale, scale, scale) // Re-apply scale
    }
}

/**
 * Manages the creation, updating, and rendering of all particle effects.
 */
class ParticleSystem {
    private val activeParticles = Array<GameParticle>()
    private val billboardParticleModels = mutableMapOf<ParticleEffectType, Model>()
    private val groundParticleModels = mutableMapOf<ParticleEffectType, Model>()
    private val effectTextures = mutableMapOf<ParticleEffectType, List<Texture>>()

    private val billboardModelBatch: ModelBatch
    private val billboardShaderProvider: BillboardShaderProvider = BillboardShaderProvider().apply {
        setBillboardLightingStrength(0.9f)
        setMinLightLevel(0.5f)
    }

    var currentSelectedEffect: ParticleEffectType = ParticleEffectType.BLOOD_SPLATTER_1
    private val renderableInstances = Array<ModelInstance>()

    lateinit var sceneManager: SceneManager
    lateinit var raycastSystem: RaycastSystem
    private val groundPlane = com.badlogic.gdx.math.Plane(Vector3.Y, 0f)
    private val tempVec3 = Vector3()
    private var blockSize: Float = 4f

    init {
        billboardModelBatch = ModelBatch(billboardShaderProvider)
    }

    fun getActiveParticles(): Array<GameParticle> = activeParticles

    fun initialize(blockSize: Float) {
        this.blockSize = blockSize
        this.raycastSystem = RaycastSystem(blockSize)
        println("Initializing Particle System...")
        val modelBuilder = ModelBuilder()

        for (type in ParticleEffectType.entries) {
            try {
                if (type.texturePaths.isEmpty()) continue // Skip if no textures are defined

                // Load ALL textures for this effect type
                val texturesForType = type.texturePaths.map { path ->
                    Texture(Gdx.files.internal(path), true).apply {
                        setFilter(Texture.TextureFilter.MipMapLinearLinear, Texture.TextureFilter.Linear)
                    }
                }
                effectTextures[type] = texturesForType

                val material = Material(
                    TextureAttribute.createDiffuse(texturesForType.first()),
                    BlendingAttribute(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA, 1.0f),
                    IntAttribute.createCullFace(GL20.GL_NONE)
                )

                if (isGroundOrientedEffect(type)) {
                    // Create a HORIZONTAL plane for splatters, scorch marks, etc.
                    val model = modelBuilder.createRect(
                        -0.5f, 0f, 0.5f, -0.5f, 0f, -0.5f,
                        0.5f, 0f, -0.5f, 0.5f, 0f, 0.5f,
                        0f, 1f, 0f, // Normal pointing up
                        material,
                        (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong()
                    )
                    groundParticleModels[type] = model
                } else {
                    // Create a VERTICAL plane for smoke, explosions, etc.
                    val model = modelBuilder.createRect(
                        -0.5f, -0.5f, 0f,
                        0.5f, -0.5f, 0f,
                        0.5f, 0.5f, 0f,
                        -0.5f, 0.5f, 0f,
                        0f, 0f, 1f, // Normal facing forward
                        material,
                        (VertexAttributes.Usage.Position or VertexAttributes.Usage.Normal or VertexAttributes.Usage.TextureCoordinates).toLong()
                    )

                    // Check for special vertical wipe and pre-rotate its model
                    if (type == ParticleEffectType.MOVEMENT_WIPE_VERTICAL) {
                        model.meshes.first().transform(Matrix4().setToRotation(Vector3.X, 90f))
                    }

                    billboardParticleModels[type] = model
                }
            } catch (e: Exception) {
                println("ERROR: Could not load particle resources for ${type.displayName}: ${e.message}")
            }
        }
        println("Particle System initialized with ${billboardParticleModels.size + groundParticleModels.size} effect models.")
    }

    fun handlePlaceAction(ray: Ray) {
        var hitPoint: Vector3? = null
        var hitNormal: Vector3? = null

        // First, try to hit an existing block
        val hitBlock = raycastSystem.getBlockAtRay(ray, sceneManager.activeChunkManager.getAllBlocks())
        if (hitBlock != null) {
            val blockBounds = hitBlock.getBoundingBox(blockSize, BoundingBox())
            if (com.badlogic.gdx.math.Intersector.intersectRayBounds(ray, blockBounds, tempVec3)) {
                hitPoint = tempVec3.cpy()
                // A simple way to get the hit normal
                val relativePos = Vector3(tempVec3).sub(hitBlock.position)
                val absX = kotlin.math.abs(relativePos.x)
                val absY = kotlin.math.abs(relativePos.y)
                val absZ = kotlin.math.abs(relativePos.z)
                hitNormal = when {
                    absY > absX && absY > absZ -> Vector3(0f, if (relativePos.y > 0) 1f else -1f, 0f)
                    absX > absY && absX > absZ -> Vector3(if (relativePos.x > 0) 1f else -1f, 0f, 0f)
                    else -> Vector3(0f, 0f, if (relativePos.z > 0) 1f else -1f)
                }
            }
        }

        // If no block was hit, intersect with the ground plane
        if (hitPoint == null) {
            if (com.badlogic.gdx.math.Intersector.intersectRayPlane(ray, groundPlane, tempVec3)) {
                hitPoint = tempVec3.cpy()
                hitNormal = Vector3.Y // Normal is straight up from the ground
            }
        }

        // Spawn the effect if we found a point
        hitPoint?.let { pos ->
            val effectType = currentSelectedEffect
            // Check for gun smoke effects to determine direction
            val isGunSmokeEffect = effectType.name.startsWith("GUN_SMOKE")

            // If it's gun smoke, the direction is the ray. Otherwise, it's based on the surface normal.
            val direction = if (isGunSmokeEffect) ray.direction else hitNormal

            spawnEffect(effectType, pos, direction, hitNormal)
            println("Spawned ${effectType.displayName} at $pos")
        }
    }

    /**
     * Spawns a particle effect at a given position.
     */
    fun spawnEffect(
        type: ParticleEffectType,
        position: Vector3,
        baseDirection: Vector3? = null,
        surfaceNormal: Vector3? = null,
        initialRotation: Float? = null,
        targetRotation: Float? = null,
        overrideScale: Float? = null,
        gravityOverride: Float? = null
    ) {
        val model = if (isGroundOrientedEffect(type)) { // Can't spawn if model isn't loaded
            groundParticleModels[type]
        } else {
            billboardParticleModels[type]
        } ?: return

        val particleCount = type.particleCount.random()

        for (i in 0 until particleCount) {
            val instance = ModelInstance(model)
            instance.userData = "effect"

            if (type.randomizeTexture) {
                // Get the list of pre-loaded textures for this effect type
                val texturePool = effectTextures[type]
                if (!texturePool.isNullOrEmpty()) {
                    // Pick one texture at random and apply it to this specific particle instance
                    val randomTexture = texturePool.random()
                    val material = instance.materials.first()
                    material.set(TextureAttribute.createDiffuse(randomTexture))
                }
            }

            val lifeVariance = (Random.nextFloat() - 0.5f) * 2f * type.lifetimeVariance
            val life = (type.particleLifetime + lifeVariance).coerceAtLeast(0.1f)

            val animSystem = AnimationSystem()
            if (type.isAnimated && !type.randomizeTexture) {
                animSystem.createAnimation(type.name, type.texturePaths, type.frameDuration, type.isLooping)
                animSystem.playAnimation(type.name)
            }

            // Calculate velocity
            val velocity = if (baseDirection != null) {
                Vector3(baseDirection).nor()
            } else {
                // Random direction for explosions like blood
                Vector3(Random.nextFloat() - 0.5f, Random.nextFloat() - 0.5f, Random.nextFloat() - 0.5f).nor()
            }
            val speed = type.initialSpeed + (Random.nextFloat() - 0.5f) * 2f * type.speedVariance
            velocity.scl(speed)

            // Calculate scale
            val scale: Float = overrideScale ?: if (type.scaleVariance > 0f && Random.nextFloat() < type.sizeRandomnessChance) {
                val sizeVariance = (Random.nextFloat() - 0.5f) * 2f * type.scaleVariance
                // Ensure scale doesn't become zero or negative
                (type.scale + sizeVariance).coerceAtLeast(0.1f)
            } else {
                // Fallback to the default base size
                type.scale
            }

            // Per-particle randomization
            val willSwing = Random.nextFloat() < type.swingChance

            val ampVariance = (Random.nextFloat() - 0.5f) * 2f * type.swingAmplitudeVariance
            val particleAmplitude = (type.swingAmplitude + ampVariance).coerceAtLeast(0f)

            val freqVariance = (Random.nextFloat() - 0.5f) * 2f * type.swingFrequencyVariance
            val particleFrequency = (type.swingFrequency + freqVariance).coerceAtLeast(0.1f)
            val finalGravity = gravityOverride ?: type.gravity

            val particle = GameParticle(
                type = type,
                instance = instance,
                animationSystem = animSystem,
                position = position.cpy(),
                velocity = velocity,
                life = life,
                initialLife = life,
                scale = scale,
                swings = willSwing,
                swingAmplitude = particleAmplitude,
                swingFrequency = particleFrequency,
                gravity = finalGravity
            )

            if (isGroundOrientedEffect(type) || isSurfaceOrientedEffect(type)) {
                particle.isSurfaceOriented = true
                instance.transform.setToTranslation(particle.position)

                if (surfaceNormal != null) {
                    val isBulletHole = type == ParticleEffectType.BULLET_HOLE_PLAYER ||
                        type == ParticleEffectType.BULLET_HOLE_ENEMY

                    if (isBulletHole) {
                        val absX = kotlin.math.abs(surfaceNormal.x)
                        val absZ = kotlin.math.abs(surfaceNormal.z)

                        // Random spin angle (like spinning a coin on the wall)
                        val spinAngle = Random.nextFloat() * 360f

                        if (absX > absZ) {
                            // East/West face - align to YZ plane
                            if (surfaceNormal.x > 0) {
                                // East face (+X) - rotate around X axis for the spin, then orient to wall
                                instance.transform.rotate(Vector3.X, spinAngle)
                                instance.transform.rotate(Vector3.Y, 90f)
                            } else {
                                // West face (-X)
                                instance.transform.rotate(Vector3.X, spinAngle)
                                instance.transform.rotate(Vector3.Y, -90f)
                            }
                        } else {
                            // North/South face - align to XY plane
                            if (surfaceNormal.z > 0) {
                                // North face (+Z)
                                instance.transform.rotate(Vector3.Z, spinAngle)
                                instance.transform.rotate(Vector3.Y, 0f)
                            } else {
                                // South face (-Z)
                                instance.transform.rotate(Vector3.Z, spinAngle)
                                instance.transform.rotate(Vector3.Y, 180f)
                            }
                        }
                    } else {
                        // Original alignment logic for other decals
                        val up = Vector3.Y
                        val rotationAxis = up.cpy().crs(surfaceNormal).nor()
                        val angle = Math.toDegrees(acos(up.dot(surfaceNormal).toDouble())).toFloat()

                        // Apply the alignment rotation
                        instance.transform.rotate(rotationAxis, angle)
                        // Existing logic for ground-only decals
                        instance.transform.rotate(surfaceNormal, Random.nextFloat() * 360f) // The "spinning plate" rotation
                    }
                }

                // Apply scale last
                instance.transform.scale(particle.scale, particle.scale, particle.scale)
            } else {
                // Handle surface orientation before setting up other animations
                if (initialRotation != null && targetRotation != null) {
                    particle.animatesRotation = true
                    particle.currentRotationY = initialRotation
                    particle.targetRotationY = targetRotation
                }

                // Apply the initial transform
                particle.updateTransform()
            }

            activeParticles.add(particle)
        }
    }

    private fun isGroundOrientedEffect(type: ParticleEffectType): Boolean {
        return when (type) {
            ParticleEffectType.BLOOD_SPLATTER_1,
            ParticleEffectType.BLOOD_SPLATTER_2,
            ParticleEffectType.BLOOD_SPLATTER_3,
            ParticleEffectType.BOOT_PRINTS,
            ParticleEffectType.DYNAMITE_EXPLOSION_AREA_ONE,
            ParticleEffectType.DYNAMITE_EXPLOSION_AREA_TWO,
            ParticleEffectType.FIRE_BURN_SPOT -> true
            else -> false
        }
    }

    private fun isSurfaceOrientedEffect(type: ParticleEffectType): Boolean {
        return when (type) {
            ParticleEffectType.BULLET_HOLE_PLAYER,
            ParticleEffectType.BULLET_HOLE_ENEMY,

            ParticleEffectType.BLOOD_SPLATTER_1,
            ParticleEffectType.BLOOD_SPLATTER_2,
            ParticleEffectType.BLOOD_SPLATTER_3 -> true
            else -> false
        }
    }

    fun update(deltaTime: Float, weatherSystem: WeatherSystem, isInInterior: Boolean) {
        val visualRainIntensity = weatherSystem.getVisualRainIntensity()
        val iterator = activeParticles.iterator()

        while (iterator.hasNext()) {
            val particle = iterator.next()

            val isSmoke = particle.type.name.contains("SMOKE")
            if (visualRainIntensity > 0.01f && isSmoke && !particle.hasCollided && !isInInterior) {
                // 1. Reduce lifetime faster
                val lifetimeReduction = 1.5f * visualRainIntensity * deltaTime
                particle.life -= lifetimeReduction

                // 2. Increase gravity to pull it down
                val gravityIncrease = 40f * visualRainIntensity * deltaTime
                particle.velocity.y -= gravityIncrease

                // 3. (POLISH) Shrink the particle to make it look "dissipated"
                val scaleReduction = 0.3f * visualRainIntensity * deltaTime
                particle.scale = (particle.scale - scaleReduction).coerceAtLeast(0.1f)
            }

            particle.update(deltaTime, sceneManager) // This line needs to be updated

            // Wash-away logic for blood/ash
            val isWashable = particle.type in listOf(
                ParticleEffectType.BLOOD_SPLATTER_1,
                ParticleEffectType.BLOOD_SPLATTER_2,
                ParticleEffectType.BLOOD_SPLATTER_3,
                ParticleEffectType.BURNED_ASH
            )

            if (visualRainIntensity > 0.01f && isWashable && !isInInterior) {
                val component = particle.washAwayComponent
                component.timer -= deltaTime

                if (component.timer <= 0) {
                    when (component.state) {
                        WashAwayState.IDLE -> {
                            component.state = WashAwayState.SHRINKING
                            component.timer = Random.nextFloat() * 2.5f + 1.0f // Shrink for 1-3.5 seconds
                        }
                        WashAwayState.SHRINKING -> {
                            component.state = WashAwayState.IDLE
                            component.timer = Random.nextFloat() * 20f + 10f // Pause for 10-30 seconds
                        }
                    }
                }

                if (component.state == WashAwayState.SHRINKING) {
                    val shrinkRate = 0.4f * visualRainIntensity // Base shrink speed
                    particle.scale -= shrinkRate * deltaTime
                    // If scale becomes too small, mark for removal
                    if (particle.scale <= 0.05f) {
                        particle.life = 0f
                    }
                }
            }

            if (particle.life <= 0) {
                particle.animationSystem.dispose()
                iterator.remove()
            }
        }
    }

    fun render(camera: Camera, environment: Environment) {
        if (activeParticles.isEmpty) return

        billboardShaderProvider.setEnvironment(environment)
        billboardModelBatch.begin(camera)
        renderableInstances.clear()

        for (particle in activeParticles) {
            renderableInstances.add(particle.instance)
        }

        billboardModelBatch.render(renderableInstances, environment)
        billboardModelBatch.end()
    }

    fun nextEffect() {
        val currentIndex = ParticleEffectType.entries.indexOf(currentSelectedEffect)
        val nextIndex = (currentIndex + 1) % ParticleEffectType.entries.size
        currentSelectedEffect = ParticleEffectType.entries[nextIndex]
    }

    fun previousEffect() {
        val currentIndex = ParticleEffectType.entries.indexOf(currentSelectedEffect)
        val prevIndex = if (currentIndex > 0) currentIndex - 1 else ParticleEffectType.entries.size - 1
        currentSelectedEffect = ParticleEffectType.entries[prevIndex]
    }

    fun clearAllParticles() {
        val iterator = activeParticles.iterator()
        while (iterator.hasNext()) {
            val particle = iterator.next()
            particle.animationSystem.dispose()
            iterator.remove()
        }
        println("ParticleSystem: All active particles have been cleared.")
    }

    fun getTextureForEffect(type: ParticleEffectType): Texture? {
        // Lazy-load textures if not already loaded (good practice)
        if (!effectTextures.containsKey(type)) {
            effectTextures[type] = type.texturePaths.map { path ->
                try {
                    Texture(Gdx.files.internal(path))
                } catch (e: Exception) {
                    // Return a placeholder or handle the error
                    Pixmap(1, 1, Pixmap.Format.RGBA8888).let { p -> p.setColor(Color.MAGENTA); p.fill(); Texture(p).also { p.dispose() } }
                }
            }
        }
        return effectTextures[type]?.firstOrNull()
    }

    fun dispose() {
        billboardParticleModels.values.forEach { it.dispose() }
        billboardParticleModels.clear()
        groundParticleModels.values.forEach { it.dispose() }
        groundParticleModels.clear()

        activeParticles.forEach { it.animationSystem.dispose() }
        activeParticles.clear()
        billboardModelBatch.dispose()
        billboardShaderProvider.dispose()
    }
}
