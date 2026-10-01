package com.yuan3271.cloudrift.input

/**
 * The part of the input method window the keyboard UI is allowed to steer.
 *
 * The composables decide *what* frame the keyboard should have (they are the ones that know the
 * orientation and the user's setting) and the service applies it, because only the service owns the
 * window. Keeping it behind an interface also means the controller stays testable without a window.
 */
interface InputWindowHost {

    /**
     * @param floating a smaller card instead of the full width keyboard.
     * @param widthPercent width of the floating card, as a percentage of the screen.
     */
    fun applyInputFrame(floating: Boolean, widthPercent: Int)

    /** Moves the floating keyboard by a drag delta, in pixels. */
    fun moveInputWindowBy(dx: Float, dy: Float)
}
