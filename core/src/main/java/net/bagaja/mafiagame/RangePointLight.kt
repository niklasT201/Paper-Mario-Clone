package net.bagaja.mafiagame

import com.badlogic.gdx.graphics.g3d.environment.PointLight

class RangePointLight : PointLight() {
    var range: Float = 50f // Default range

    fun set(light: RangePointLight): RangePointLight {
        super.set(light)
        this.range = light.range
        return this
    }
}
