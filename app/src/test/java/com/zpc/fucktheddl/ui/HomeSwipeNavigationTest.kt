package com.zpc.fucktheddl.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeSwipeNavigationTest {
    @Test
    fun horizontalSwipesMoveOneWorkspacePageAtATime() {
        assertEquals(
            WorkspacePage.Calendar,
            settleWorkspacePage(
                currentPage = WorkspacePage.Today,
                totalDragX = 96f,
                thresholdPx = 72f,
            ),
        )
        assertEquals(
            WorkspacePage.Todo,
            settleWorkspacePage(
                currentPage = WorkspacePage.Today,
                totalDragX = -96f,
                thresholdPx = 72f,
            ),
        )
        assertEquals(
            WorkspacePage.Today,
            settleWorkspacePage(
                currentPage = WorkspacePage.Todo,
                totalDragX = 96f,
                thresholdPx = 72f,
            ),
        )
        assertEquals(
            WorkspacePage.Today,
            settleWorkspacePage(
                currentPage = WorkspacePage.Calendar,
                totalDragX = -96f,
                thresholdPx = 72f,
            ),
        )
    }

    @Test
    fun smallOrOutOfBoundsSwipesStayOnCurrentWorkspacePage() {
        assertEquals(
            WorkspacePage.Today,
            settleWorkspacePage(
                currentPage = WorkspacePage.Today,
                totalDragX = -40f,
                thresholdPx = 72f,
            ),
        )
        assertEquals(
            WorkspacePage.Calendar,
            settleWorkspacePage(
                currentPage = WorkspacePage.Calendar,
                totalDragX = 96f,
                thresholdPx = 72f,
            ),
        )
        assertEquals(
            WorkspacePage.Todo,
            settleWorkspacePage(
                currentPage = WorkspacePage.Todo,
                totalDragX = -96f,
                thresholdPx = 72f,
            ),
        )
    }
}
