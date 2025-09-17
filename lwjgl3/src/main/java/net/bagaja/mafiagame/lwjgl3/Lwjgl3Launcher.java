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

// changing room transition effect colors
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
// map saving
// Making Missions Dynamic & Fun
// Persistence & Consequences
// Save and Load Player Progress
// Expanding & Polishing
// save shaders and visual settings
// save particles for current session
// save room changes for current session
// locked houses dont allow to place door entry
// number before mission list
// id under mission name
// C button to copy mission id
// Spawner, Fire, and Teleporter Systems (Placement Logic) for missions working
// save spawner id
// loading in rooms, room will be placed in world
// missions objects can be removed after a mission, and be mission only
// light sources visible in user mode
// mission loader
// fast loading mission
// multiple profiles
// fire and light source being connected
// connect dialog and mission system
// trigger point changes design when switching mission trigger
// remove dialog placeholders
// adding multiple city areas, district 2d polygons
// INTERACT_WITH_OBJECT Logic: Similar to the above, you don't have a generic "interaction" system for objects yet
// COLLECT_ITEM Logic: As we discussed, this requires a proper player inventory system to be fully functional.
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

// ah ok. now we can talk about the dialog and mission handling. first, as you see, and did yourself, there is hard coded dialog. is this normal in game development, as i cant imagine this, maybe for some things, but mostly its probably a json, if im right? so that would mean, i need to add a way to implement, that missions/npcs know which dialog to choose, and a way that i can check which dialog i give this npcs. second, so you added that the npc needs to be talked to so that a mission for example is finsihed, but what when i want for example, that i have to talk to a npc, that it triggers another part of the mission, for example spawn a car and enter it, so you can move to another spot and kill all enemies. this is not implemented i think, that i can have like 6 steps to finish a mission, currently its like, start a mission, something spawns, do this whatever and then after that mission is finished and reward thing happens and maybe something spawns after mission finished. third, maybe there should be more ways to start a mission, like collect item, hurt enemy, enter car, enter house, and maybe something else if you think so. fourth, would i noticed and is probably the most important part, to have a good overview of this whole stuff, a mission tree, that shows everything from start to finish like:
//Mission name:
//start, what is needed to start it -> what happens after started -> what happens after that -> what happens after that -> mission finished -> does sometihng happen after that, new mission unlocked, or multiple side missions unlocked, and is somehthing new spawned
//
//i know thats a lot, but maybe you can analyze it and we can talk about it :=)
