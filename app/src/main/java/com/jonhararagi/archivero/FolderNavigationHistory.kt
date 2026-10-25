package com.jonhararagi.archivero

/** A location preserves the URI that the user was actually browsing, not only DocumentFile.uri. */
internal data class FolderNavigationEntry<T>(
    val folder: T,
    val uri: String
)

internal data class FolderNavigationHistory<T>(
    val back: List<FolderNavigationEntry<T>> = emptyList(),
    val forward: List<FolderNavigationEntry<T>> = emptyList()
)

internal data class FolderNavigationMove<T>(
    val current: FolderNavigationEntry<T>,
    val history: FolderNavigationHistory<T>
)

/** Entering a new child clears the forward stack, as browser navigation does. */
internal fun <T> enterFolderNavigationHistory(
    history: FolderNavigationHistory<T>,
    current: FolderNavigationEntry<T>
): FolderNavigationHistory<T> =
    history.copy(back = history.back + current, forward = emptyList())

internal fun <T> moveFolderBackward(
    history: FolderNavigationHistory<T>,
    current: FolderNavigationEntry<T>
): FolderNavigationMove<T>? {
    val previous = history.back.lastOrNull() ?: return null
    return FolderNavigationMove(
        current = previous,
        history = history.copy(
            back = history.back.dropLast(1),
            forward = history.forward + current
        )
    )
}

internal fun <T> moveFolderForward(
    history: FolderNavigationHistory<T>,
    current: FolderNavigationEntry<T>
): FolderNavigationMove<T>? {
    val next = history.forward.lastOrNull() ?: return null
    return FolderNavigationMove(
        current = next,
        history = history.copy(
            back = history.back + current,
            forward = history.forward.dropLast(1)
        )
    )
}
