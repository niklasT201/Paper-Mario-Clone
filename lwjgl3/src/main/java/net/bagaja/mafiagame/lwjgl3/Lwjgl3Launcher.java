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
// collected item checks value and not just item, maybe changing to id
// number before mission list
// id under mission name
// C button to copy mission id
// npcs outside missions having dialogs, enemies too
// choosing character images via images, not paths
// visual screen for finished mission
// death resets mission
// timer turned to 0 before mission finished resets mission
// nickelodeons having real short films
// visual that a character whats to talk
// y=0 standard bottom only in editor mode
// weather events
// mission specific player inventory
// after specific missions, enemies change weapons
// no switch to fist for special missions
// hardcore gameplay mode: no weapon switching, only fist and one other weapon
// more options for starting a mission

// disablePoliceResponse: Boolean (Allow the player to cause chaos without getting a wanted level).
// increasedEnemySpawns: Boolean (For a horde-mode style mission).
// civiliansFleeOnSight: Boolean (Make all NPCs run away from the player for a "terrorize the city" mission).
// allCarsUnlocked: Boolean (Allow the player to steal any car during the mission).

// npcs choosing dialog for finishing objects
// Spawner, Fire, and Teleporter Systems (Placement Logic) for missions working
// Automatic/Linear Mode or Open World Mode
// missions objects can be removed after a mission, and be mission only
// light sources visible in user mode
// mission loader
// fast loading mission
// multiple profiles
// fire and light source being connected
// dialog preview image for characters
// option to check if damage is done by player
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

// ok, have we now implemented all modifiers that were required?
//my next question would be, is the player inventory already fully implemented?
//what i mean with this? can the game already understand what is in the players inventory, so if i have a mission that is for example a shoot out, where you have to carry gun or whatever context, it would be nice, when for this mission only, the player doesnt have the ability to switch to anither weapon, not even to fist, until the mission is finished, it has only this weapon, would also be nice when this could be changed. so yoz have for example a mission that requires you to get 3 weapons after and after each other, so not 3 at once, no one by one, and so that the inventory is able to switch the only weapon allowed when you get what i mean. second, what also would be nice is, that the mission could give you an item directly in inventory, so not spawning it next to the player, instead directly in the inventory, without replacing others already in inventory, and the last one i want for special missions and the inventory is, that the game can check if i already have a weapon in inventory. i want for example a mission at start at explains weapons and fighting system i have and for that i want that you cant switch between weapons, so the mission starts simple with a fist fight, so the mission will check, do you have a weapon in inventory, and will ONLY for this mission remove them, or switch to fist and doesnt allow you switch to other weapons, then the mission shows you knife/baseball bat fighting, and for that it should check is already a knife/baseball bat in inventory, if so it will simply switch to it and then disable switching for weapons and the player or if not already in inventory it will directly add it in the inventory and then diable switching. What should be the same for all missions that somehow modify the inventory, is that you get your normal inventory back after the mission. so if you start a mission and immidiatly lose your weapons and are stuck to fist, then switch to knife, the game will after mission completed OR failing give you back your inventory. i have it when games forget this and you lost your bought weapons bc they forgot to add giving back weapons for the mission end. i hope you get what i mean and can check if this is already working and if not, how to implemented it
