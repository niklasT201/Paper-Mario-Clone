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

// occlusion optional
// room/open world transition effect being slower and smoother transition itself

// working collisions for ramp, pillar and corner
// teleportation points
// when shooting at characters, blood can but doesnt need to spawn, for less particles
// option to turn on screen light
// NPCs can flee or fight
// separate particle clouds
// player wipe is switching to vertical when pressing W or S
// only new wipes rotating with players direction not old ones too
// maybe water blocks
// south park like animation
// npc and enemy ui option for choosing death level
// spawner for particles
// improve performance for rooms (camera starting shaking)
// map saving
// dialog system
// mission starting, saving and ending
// enemies work in rooms
// separate lady and microphone
// maybe coded effects too
// option for choosing door enter
// fix car and player falling bug
// blood system
// black bars top/bottom or left/right
// when shooting flickering light gets spawned
// player visible optional in cars
// killed enemies drop items
// enemies have an inventory and collect items
// footprints when moving through dead enemies
// adding guns
// adding ammunition
// adding health
// adding multiple city areas
// item pickups have a glow

// fbx-conv house.obj house.g3dj
// fbx-conv -f house.obj house.g3dj

//  as you see hopefully, when i place a house, the game calculates where the door entry should be. that is fine for most houses, but it can also be that it doesnt fit and so the door entry is not right. so i would like to know if its possible to like for the player point, also having the option to place a door point, so not a visible door, only a point, that i have to place if i want after placing the house with its room. if i dont want, i can simply press esc and then the house will calculate the door entry like currently, otherwise it waits unly i placed the door entry and then connects this point with the room, so when i press e at this position, i get into this house, and when im in the room, and leave it, i get spawned back at this door point. is it possible to add this?
//  idea i have for the camera would be that instead of just not getting through the wall, it instead checks if the player is moving to a wall or being hidden by it, so the player so to be near it, and then instead of staying near the player, it rotates, so instead of showing the players front, it rotates and shows the players back, so instead of the block wall being in front of the player(s camera), the camera rotates and now the block wall is behind the player. if you get what i mean
// can you help me with my kotlin 3d game? i have a problem with my player movement :=( the falling/gravity is broken. i try to explain what is wrong. i build a block pillar, lets say 30 blocks stacked on top of each other, number not important. Now i get on the highest block, and then move away from it, what i means, now i fall. till this anything is right, but then the problematic part comes. when i moved away from the block to fall, my collision box stopped colliding with the block, whats right, but when i now move while falling back to the block (pillar), so colliding with it, the fall stops, at the moment i collide, so the collision breaks the falling and lets the game think im standing on top of a block again. when i stop holding the button that moved to the block pillar direction, and started the collision, you could think, then the falling starts again, but this is not happening, the falling is completely stopped. i have to literally press a different movement key to start the falling again, that means that the key calls the check if a block is under my feet again, that was not checked anymore. i also tested it by placing a block under the player while he was stuck at the block pillar, but it didnt change anything, and when i removed it again, the player also kept stuck, so the movement key is the only way to recall the gravity check
// not coding, more like brainstorming. as you hopefully see, i have a lot of code, also for rooms and houses. now the needed part for later. i will need missions later, so some checks will be needed like, mission started, mission finished, in which mission are you, maybe special values for some missions, so for example that no cars in traffic spawn, unlimited ammunition, unlimited health and so on like in gta 3 or vc to make missions not completely unfair to play. also there will be missions where some events will be permant like a house burning down, so then it would be needed that the house in the world changes. please explain how this would be done and how good my code is already to implement this later
