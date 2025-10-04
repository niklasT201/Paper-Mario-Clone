package net.bagaja.mafiagame.lwjgl3;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import net.bagaja.mafiagame.MafiaGame;

/** Launches the desktop (LWJGL3) application. */
public class Lwjgl3Launcher {
    public static void main(String[] args) {
        if (StartupHelper.startNewJvmIfRequired()) return; // This handles macOS support and helps on Windows.
        System.out.println("Current Working Directory: " + new java.io.File(".").getAbsolutePath());
        createApplication();
    }

    private static void createApplication() {
        new Lwjgl3Application(new MafiaGame(), getDefaultConfiguration());
    }

    private static Lwjgl3ApplicationConfiguration getDefaultConfiguration() {
        Lwjgl3ApplicationConfiguration configuration = new Lwjgl3ApplicationConfiguration();
        configuration.setTitle("Paper Mario Clone");
        //// Vsync limits the frames per second to what your hardware can display, and helps eliminate
        //// screen tearing. This setting doesn't always work on Linux, so the line after is a safeguard.
        configuration.useVsync(true);
        //// Limits FPS to the refresh rate of the currently active monitor, plus 1 to try to match fractional
        //// refresh rates. The Vsync setting above should limit the actual FPS to match the monitor.
        configuration.setForegroundFPS(Lwjgl3ApplicationConfiguration.getDisplayMode().refreshRate + 1);
        //// If you remove the above line and set Vsync to false, you can get unlimited FPS, which can be
        //// useful for testing performance, but can also be very stressful to some hardware.
        //// You may also need to configure GPU drivers to fully disable Vsync; this can cause screen tearing.
        configuration.setWindowedMode(640, 480);
        //// You can change these files; they are in lwjgl3/src/main/resources/ .
        configuration.setWindowIcon("libgdx128.png", "libgdx64.png", "libgdx32.png", "libgdx16.png");
        return configuration;
    }
}

// Don being villian or helpful
// player character/moral compass: Forced prostitution/sexual violence, Violence against immigrants, Rape
// Graffiti like: “Moths go back to the lamp store!” or “Too many wings in this district!”
// “It’s not racist if it’s true. Moths do love lamps.”
// “Diversity is great… but only in the zoo.”
// “They call us pests, but they’re the ones living off crumbs of power.”
// “This city forgets who built it — and who’s still sweeping its alleys.”

// industrial assets
// more animations adding

// edge houses
// using new animation images
// collected ammunition label for bullets and weapons
// rusher stopping in front of the player
// fire system uses spread fire, fire or flame particle folder
// blocks being visible behind transparent enemies etc.
// car maybe be able to step up half a block
// Chain Reactions with Explosives & Fire
// lanterns use not visible light sources
// fire using not real light sources like muzzle flash and headlights
// Environmental State Changes from Effects
// Faction & Alert System
// maybe adding bouncy dynamite (1 bounce max)
// step up stair
// maybe improving billboard shader
// south park like animation
// Making Missions Dynamic & Fun
// Persistence & Consequences
// Save and Load Player Progress
// Expanding & Polishing
// save shaders and visual settings
// npcs outside missions having dialogs, enemies too
// choosing character images via images, not paths
// visual screen for finished mission
// death resets mission
// nickelodeons having real short films
// visual that a character wants to talk
// weather events
// hardcore gameplay mode: no weapon switching, only fist and one other weapon
// more options for starting a mission
// mission trigger not visible during other missions
// pulling out characters from vehicles
// pressing e to talk will no longer switch weapon
// add event button automatically adds new event without saving button press
// new events: PLAY_SOUND
// label to show how many enemies left for kill all enemies
// heavy weapon check for rotation
// fixing enemy reloading bug
// fixing flickering character image
// Objective Markers
// drive to location, visual arrow that shows direction
// SEQUENCE, LOSE_THE_COPS, INFLUENCE_NPC, CAPTURE_TERRITORY
// Enemy/NPC AI Override, One-Hit Kills, One-Hit Kills, Specific Weapon Infinite Ammo
// Cutscene System
// interact with interiors/objectives, cars, characters = give item, lose item, new event will happen

// disablePoliceResponse: Boolean (Allow the player to cause chaos without getting a wanted level).

// Automatic/Linear Mode or Open World Mode
// mission loader
// fast loading mission
// multiple profiles
// fire and light source being connected
// dialog preview image for characters
// option to check if damage is done by player
// trigger point changes design when switching mission trigger
// adding multiple city areas, district 2d polygons
// INTERACT_WITH_OBJECT Logic: Similar to the above, you don't have a generic "interaction" system for objects yet
// Advanced Rewards: The logic for GIVE_AMMO and GIVE_ITEM is in place, but things like UNLOCK_CAR_SPAWN would require a "garage" or "player property" system.

// maybe, not intended for now:
// Asynchronous/Multi-threaded Chunk Building
// Incremental Mesh Updates
// Time-Slicing the Rebuild
// Advanced AI and Physics Optimization: Spatial Partitioning
// Future-Proofing: Asset and World Management

// fbx-conv house.obj house.g3dj
// fbx-conv -f house.obj house.g3dj

// can you help me with my kotlin 3d game? I have this billboard shader for my player, items, npcs and enemies. it worls fine but i would like to modify it a bit. as you maybe see, the shader makes, that the things that has a userdata set = player are full bright. so you can see them clear. this is fine, but originally i wanted to make it so, that when i place a light source and the light source shines at a billobard, the billboard gets shiny, and when i rotate so now the other side of the billboard is visible, it also gets shiny. as you maybe know, in default in libgdx this is not possible. a billboard can only get shined at from one side and not both. so i wanted to change this, but as it didnt work what i wanted, i made at so they are for now always completely bright. for the player i noticed that i liked this even more, but i dont like it so much for everything eslse. i would like it when the player would stay as it is, so always fully bright, but everything else can be shined from both sides and not only one. but the problem is, that it seems like when i place a light source that shines at something that uses this billboard shader, it completely ignores it, like these things dont exist. why is this happening and is there a way to fix it?
