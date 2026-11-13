package com.github.fakegps

/**
 * Bridge between the floating joystick view and the movement controller.
 */
interface IJoyStickPresenter {
    fun onSetLocationClick()
    fun onFlyClick()
    fun onBookmarkLocationClick()
    fun onCopyLocationClick()
    fun onArrowUpClick()
    fun onArrowDownClick()
    fun onArrowLeftClick()
    fun onArrowRightClick()
}
