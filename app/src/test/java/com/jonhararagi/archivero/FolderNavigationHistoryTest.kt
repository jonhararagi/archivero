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
        val c = FolderNavigationEntry("c", "file:///storage/c")

        // Enter A from root, then go back to root before choosing another branch.
        val afterEnteringA = enterFolderNavigationHistory(FolderNavigationHistory<String>(), root)
        val afterBack = moveFolderBackward(afterEnteringA, a)!!
        val newBranch = enterFolderNavigationHistory(afterBack.history, root)

        assertEquals(listOf(root), newBranch.back)
        assertTrue(newBranch.forward.isEmpty())
        assertEquals(c.folder, "c")
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
        val history = FolderNavigationHistory<String>()
        val afterEnteringChild = enterFolderNavigationHistory(history, root)
        val grandchild = child.copy(folder = "grandchild", uri = "file:///storage/child/grandchild")
        val deeper = enterFolderNavigationHistory(afterEnteringChild, child)
        val moved = moveFolderBackward(deeper, grandchild)!!

        assertTrue(history.back.isEmpty())
        assertTrue(history.forward.isEmpty())
        assertEquals(listOf(grandchild), moved.history.forward)
    }
}
