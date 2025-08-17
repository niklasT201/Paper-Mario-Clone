package net.bagaja.mafiagame

import com.badlogic.gdx.graphics.Color
import com.badlogic.gdx.graphics.g3d.environment.PointLight

class RangePointLight : PointLight() {
    var range: Float = 50f // Default range

    fun set(color: Color, x: Float, y: Float, z: Float, intensity: Float, range: Float): RangePointLight {
        super.set(color, x, y, z, intensity)
        this.range = range
        return this
    }

    fun set(light: RangePointLight): RangePointLight {
        super.set(light)
        this.range = light.range
        return this
    }
}
