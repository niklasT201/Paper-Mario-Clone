package net.bagaja.mafiagame.lwjgl3;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3WindowListener;
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

//        configuration.setWindowListener(new Lwjgl3WindowListener() {
//            @Override
//            public void focusLost() {
//                // This is called when you Alt+Tab or click another window.
//                // We get the running game instance and call its pause() method.
//                if (Gdx.app != null && Gdx.app.getApplicationListener() instanceof MafiaGame) {
//                    ((MafiaGame) Gdx.app.getApplicationListener()).pause();
//                }
//            }
//
//            @Override
//            public void focusGained() {
//                // This is called when you click back into the game window.
//                // We get the running game instance and call its resume() method.
//                if (Gdx.app != null && Gdx.app.getApplicationListener() instanceof MafiaGame) {
//                    ((MafiaGame) Gdx.app.getApplicationListener()).resume();
//                }
//            }
//
//            // --- Other listener methods (can be left empty) ---
//            @Override
//            public void created(com.badlogic.gdx.backends.lwjgl3.Lwjgl3Window window) {}
//
//            @Override
//            public void iconified(boolean isIconified) {}
//
//            @Override
//            public void maximized(boolean isMaximized) {}
//
//            @Override
//            public boolean closeRequested() { return true; }
//
//            @Override
//            public void filesDropped(String[] files) {}
//
//            @Override
//            public void refreshRequested() {}
//        });

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
// ambience steps pausing between loop
// whosh sound for melee weapons
// update melee images
// police spawning less in poor areas
// shooting sound echo doesnt move with player
// audio ui two items in a row
// enemy range visible in fist fights
// blocks being visible behind transparent enemies etc.
// car maybe be able to step up half a block
// Chain Reactions with Explosives & Fire
// Environmental State Changes from Effects
// maybe adding bouncy dynamite (1 bounce max)
// step up stair
// maybe improving billboard shader
// south park like animation
// burning character use fire sound, not damage sound
// Persistence & Consequences
// fire in rooms and mission not being connected right with its light source
// visual screen for finished mission
// bullet holes disappear when breaking blocks with them
// money label showing up at rewards
// nickelodeons having real short films
// hardcore gameplay mode: no weapon switching, only fist and one other weapon
// enemy cover up ai
// minimap for open world mode
// player can buy businesses (speakeasies, illegal casinos, protection rackets) that generate passive income
// silent takedowns (melee from behind) and the ability to hide bodies
// dead enemy flying behind players back
// bones can be moved by character and explosions
// add event button automatically adds new event without saving button press
// fixing enemy reloading bug
// fixing flickering character image
// SEQUENCE, LOSE_THE_COPS, INFLUENCE_NPC, CAPTURE_TERRITORY
// Cutscene System
// interact with interiors/objectives, cars, characters = give item, lose item, new event will happen

// Automatic/Linear Mode or Open World Mode
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

// more like brainstorming question.
//lets say i a have an item, for example a book png, and i want a shader ONLY for this book. maybe a shiny shader that this item/2d billboard looks shiny, would it be possible to create a shader ONLY EXCLUSIVE for this as currently i have shaders when i remember it right, that it works so that the shader for example looks for everything on pngs that is red, shaders knows, Ah okay here is red, and makes it gray. this would mean for my book shader, it would make things i dont want shiny as they have the same color like my book png

// can you help me with my kotlin 3d game? as you maybe see, i have multiple not editor mode uis like huds, start screen, pause menu, visual settings ui and so on. and now my problem, i noticed that when i press the return button on pause menu, and i should move back to start screen, nothing of the start screen shows up, just the background, nothing more, and this looks like when i start the game and one second before the start screen shows up, there is this placeholder background that has nothing on it, and then it shows the start screen, and the screen that pops up when pressing the return button looks exactly the same. so my thought is that the start screen maybe never loads right. this brings me my big point. Can you overhaul the whole ui, that is only meant for player mode, so pause menu, visual settings, start screen, loading screen, and i think that should be it. in game things like hud, money label and so on work pretty well and have no problem, but the uis for start, loading, visual and pause are really bad working. they look okay for now, but how they are loaded, connected and called is really really bad.

// can you help me with my kotlin 3d game? I have this billboard shader for my player, items, npcs and enemies. it worls fine but i would like to modify it a bit. as you maybe see, the shader makes, that the things that has a userdata set = player are full bright. so you can see them clear. this is fine, but originally i wanted to make it so, that when i place a light source and the light source shines at a billobard, the billboard gets shiny, and when i rotate so now the other side of the billboard is visible, it also gets shiny. as you maybe know, in default in libgdx this is not possible. a billboard can only get shined at from one side and not both. so i wanted to change this, but as it didnt work what i wanted, i made at so they are for now always completely bright. for the player i noticed that i liked this even more, but i dont like it so much for everything eslse. i would like it when the player would stay as it is, so always fully bright, but everything else can be shined from both sides and not only one. but the problem is, that it seems like when i place a light source that shines at something that uses this billboard shader, it completely ignores it, like these things dont exist. why is this happening and is there a way to fix it?

// i had the idea for a new feature, where i dont know if it could be good. as you hopefully see, enemies and npcs that are killed, they start disappearing, until they are fully vanished. and i want a new feature BUT only for ultra violence mode. when an enemy or npc is killed, it will disappear, BUT only after a certrain amount of time, maybe like one minute and then after this time the normal disappearing starts. but now the question, what happens during this one minute. simple, i want that the dead character lays dead on floor, and when the player stands next to it, or maybe literally collides with the npc/enemy, player can press a key, maybe p or so and "pick" up the character, and put it to another location, until the disappearing starts. i want that when you pick up the character, it will be behind the player, so when the player moves to the right for example, the dead character will fly a little bit above the ground and move behind the player being on the left side and follow, until it disappears or the player puts it down agian

// can you help me with my kotlin 3d game?
//i have this preview ui for dialogs, but this doesnt work for dialogs that are used for reward dialogs, so anything besides None. this is not so good, as it sometimes looks weird, how big the dialog gets just bc the reward, and i have no way to change it how i want it and it would be annyoing when i would have to modify every single dialog hardcoded. is there a way to add that i can also modify the dialog when they are used by an enemy/npc and have a reward, that i can see how the reward dialog will look and not only the created dialogs that i can later use for these characters
