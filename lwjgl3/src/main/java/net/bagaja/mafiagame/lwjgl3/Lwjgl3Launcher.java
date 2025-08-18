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

    private static Lwjgl3Application createApplication() {
        return new Lwjgl3Application(new MafiaGame(), getDefaultConfiguration());
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

// fire asset
// industrial assets
// more animations adding

// changing room transition effect colors
// edge houses
// improve enemy/npc falling
// bring back sky color black
// blocks being visible behind transparent enemies etc.
// improve UIManagers code base
// melee areas transparent
// weapon radius for fire and explosions
// damage gets less for higher radius
// fire and explosion having the same radius, fire less damage than explosions
// car maybe be able to step up half a block
// optional weapon light
// Chain Reactions with Explosives & Fire
// Spreading Fire
// lanterns use not visible light sources
// Environmental State Changes from Effects
// Knockback from Explosions
// Faction & Alert System
// car spawner
// shacky cam for explosions
// car ai to drive around
// maybe adding bouncy dynamite (1 bounce max)
// step up stair
// visual explosion range
// maybe improving billboard shader
// visible border of light sources
// faceculling for only one face blocks working correct
// working collisions for ramp, pillar and corner
// maybe adding player hiding tp point text behind it
// visual reload/charging bar
// missing knife, baseball bat and fist animations
// add player with revolver image
// add player with shotgun image
// add player with pistol image
// fix player with tommy gun image
// add new player with weapon images
// player hitting with baseball bat
// player stabbing with knife
// player wipe is switching to vertical when pressing W or S
// only new wipes rotating with players direction not old ones too
// south park like animation
// npc and enemy ui option for choosing death level
// map saving
// dialog system
// mission starting, saving and ending
// option for choosing door enter
// player visible optional in cars
// killed enemies drop items
// enemies have an inventory and collect items
// adding guns
// adding ammunition
// adding health
// adding multiple city areas

// maybe, not intended for now:
// Asynchronous/Multi-threaded Chunk Building
// Incremental Mesh Updates
// Time-Slicing the Rebuild
// Advanced AI and Physics Optimization: Spatial Partitioning
// Future-Proofing: Asset and World Management

// fbx-conv house.obj house.g3dj
// fbx-conv -f house.obj house.g3dj

//  as you see hopefully, when i place a house, the game calculates where the door entry should be. that is fine for most houses, but it can also be that it doesnt fit and so the door entry is not right. so i would like to know if its possible to like for the player point, also having the option to place a door point, so not a visible door, only a point, that i have to place if i want after placing the house with its room. if i dont want, i can simply press esc and then the house will calculate the door entry like currently, otherwise it waits unly i placed the door entry and then connects this point with the room, so when i press e at this position, i get into this house, and when im in the room, and leave it, i get spawned back at this door point. is it possible to add this?
// not coding, more like brainstorming. as you hopefully see, i have a lot of code, also for rooms and houses. now the needed part for later. i will need missions later, so some checks will be needed like, mission started, mission finished, in which mission are you, maybe special values for some missions, so for example that no cars in traffic spawn, unlimited ammunition, unlimited health and so on like in gta 3 or vc to make missions not completely unfair to play. also there will be missions where some events will be permant like a house burning down, so then it would be needed that the house in the world changes. please explain how this would be done and how good my code is already to implement this later

// can you help me with my kotlin 3d game? I have this billboard shader for my player, items, npcs and enemies. it worls fine but i would like to modify it a bit. as you maybe see, the shader makes, that the things that has a userdata set = player are full bright. so you can see them clear. this is fine, but originally i wanted to make it so, that when i place a light source and the light source shines at a billobard, the billboard gets shiny, and when i rotate so now the other side of the billboard is visible, it also gets shiny. as you maybe know, in default in libgdx this is not possible. a billboard can only get shined at from one side and not both. so i wanted to change this, but as it didnt work what i wanted, i made at so they are for now always completely bright. for the player i noticed that i liked this even more, but i dont like it so much for everything eslse. i would like it when the player would stay as it is, so always fully bright, but everything else can be shined from both sides and not only one. but the problem is, that it seems like when i place a light source that shines at something that uses this billboard shader, it completely ignores it, like these things dont exist. why is this happening and is there a way to fix it?
// when i place mutliple lights in a room for example, lets say 6, then the big room that should have only light where the light sources are, gets completely bright. BUT this is only a visual bug. when i move to the area where is no light placed and i move away from the light sources i placed, then the room area gets dark again. this is a really weird bug, and i think it has to do with this visible light area or maybe not. im not so sure
