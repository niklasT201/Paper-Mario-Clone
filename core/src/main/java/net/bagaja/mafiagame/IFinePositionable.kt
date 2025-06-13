package net.bagaja.mafiagame

/**
 * An interface for any system that supports fine-positioning of its objects.
 */
interface IFinePositionable {
    var finePosMode: Boolean
    val fineStep: Float

    fun toggleFinePosMode() {
        finePosMode = !finePosMode
        println("Fine positioning mode: ${if (finePosMode) "ON (use arrow keys/numpad for precise placement)" else "OFF"}")
    }
}
