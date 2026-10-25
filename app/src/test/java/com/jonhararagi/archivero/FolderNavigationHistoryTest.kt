package com.jonhararagi.archivero

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderNavigationHistoryTest {
    @Test
    fun backAndForwardRestoreTheExactVisitedSequence() {
        val root = FolderNavigationEntry("root-folder", "content://provider/tree/root")
        val alpha = FolderNavigationEntry("alpha-folder", "content://provider/tree/root/document/alpha")
        val beta = FolderNavigationEntry("beta-folder", "content://provider/tree/root/document/beta")

        var history = FolderNavigationHistory<String>()
        history = enterFolderNavigationHistory(history, root)
        history = enterFolderNavigationHistory(history, alpha)

        val backToAlpha = moveFolderBackward(history, beta)!!
        assertEquals(alpha, backToAlpha.current)
        assertEquals(listOf(root), backToAlpha.history.back)
        assertEquals(listOf(beta), backToAlpha.history.forward)

        val backToRoot = moveFolderBackward(backToAlpha.history, backToAlpha.current)!!
        assertEquals(root, backToRoot.current)
        assertTrue(backToRoot.history.back.isEmpty())
        assertEquals(listOf(beta, alpha), backToRoot.history.forward)

        val forwardToAlpha = moveFolderForward(backToRoot.history, backToRoot.current)!!
        assertEquals(alpha, forwardToAlpha.current)
        assertEquals(listOf(root), forwardToAlpha.history.back)
        assertEquals(listOf(beta), forwardToAlpha.history.forward)

        val forwardToBeta = moveFolderForward(forwardToAlpha.history, forwardToAlpha.current)!!
        assertEquals(beta, forwardToBeta.current)
        assertEquals(listOf(root, alpha), forwardToBeta.history.back)
        assertTrue(forwardToBeta.history.forward.isEmpty())
    }

    @Test
    fun enteringANewFolderClearsTheForwardStack() {
        val root = FolderNavigationEntry("root", "file:///storage")
        val a = FolderNavigationEntry("a", "file:///storage/a")
        val b = FolderNavigationEntry("b", "file:///storage/b")
        val c = FolderNavigationEntry("c", "file:///storage/c")

        val rootHistory = enterFolderNavigationHistory(FolderNavigationHistory<String>(), root)
        val atA = enterFolderNavigationHistory(rootHistory, a)
        val afterBack = moveFolderBackward(atA, b)!!
        val newBranch = enterFolderNavigationHistory(afterBack.history, c)

        assertEquals(listOf(root), newBranch.back)
        assertTrue(newBranch.forward.isEmpty())
    }

    @Test
    fun historyMovesReturnNullAtEitherBoundary() {
        val location = FolderNavigationEntry("root", "file:///storage")
        val empty = FolderNavigationHistory<String>()
        assertNull(moveFolderBackward(empty, location))
        assertNull(moveFolderForward(empty, location))
        assertNull(moveFolderBackward(empty.copy(forward = listOf(location)), location))
        assertNull(moveFolderForward(empty.copy(back = listOf(location)), location.copy(folder = "child")))
    }

    @Test
    fun historyPreservesTheOriginalTreeUriAtTheRoot() {
        val root = FolderNavigationEntry(
            folder = "root-document",
            uri = "content://provider/tree/primary%3APictures"
        )
        val child = FolderNavigationEntry(
            folder = "child-document",
            uri = "content://provider/tree/primary%3APictures/document/primary%3APictures%2FAnime"
        )
        val afterEnter = enterFolderNavigationHistory(FolderNavigationHistory<String>(), root)
        val back = moveFolderBackward(afterEnter, child)!!

        assertEquals(root.uri, back.current.uri)
        assertEquals(child.uri, back.history.forward.single().uri)
    }

    @Test
    fun traversalDoesNotMutateThePreviousHistoryValue() {
        val root = FolderNavigationEntry("root", "file:///storage")
        val child = FolderNavigationEntry("child", "file:///storage/child")
        val history = enterFolderNavigationHistory(FolderNavigationHistory<String>(), root)
        val deeper = enterFolderNavigationHistory(history, child)
        val moved = moveFolderBackward(deeper, child.copy(folder = "grandchild", uri = "file:///storage/child/grandchild"))!!

        assertEquals(listOf(root), history.back)
        assertTrue(history.forward.isEmpty())
        assertEquals(listOf(child), moved.history.forward)
    }
}
