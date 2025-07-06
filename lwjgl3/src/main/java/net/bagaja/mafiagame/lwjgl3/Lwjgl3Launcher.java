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

// room/open world transition effect being slower and smoother transition itself

// working collisions for ramp, pillar and corner
// improve performance for rooms
// map saving
// dialog system
// mission starting, saving and ending
// particle system
// separate lady and microphone
// option for choosing door enter
// fix car and player falling bug
// showing 3dmodel collisions
// blood system
// black bars top/bottom or left/right
// flickering light source
// when shooting flickering light gets spawned
// improve camera for interiors
// player visible optional in cars
// maybe changing 30 min daycicle to 50
// killed enemies drop items
// enemies have an inventory and collect items
// footprints when moving through dead enemies
// adding guns
// adding ammunition
// adding health
// adding multiple city areas
// spotlight effect
// key for light source rotation testing
// item pickups have a glow

// fbx-conv house.obj house.g3dj
// fbx-conv -f house.obj house.g3dj
