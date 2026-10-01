package com.example.sendit.ui

import android.net.Uri
import androidx.compose.material3.Surface
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.sendit.data.RouteEntity
import com.example.sendit.domain.AttemptDetails
import com.example.sendit.ui.screens.AttemptFormScreen
import com.example.sendit.ui.theme.SendItTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

// Checks the route is picked from the saved routes, with a "+ New Route" option that opens a modal.
@RunWith(AndroidJUnit4::class)
class AttemptRouteSelectionTest {
    @get:Rule
    val compose = createComposeRule()

    private val overhang = RouteEntity("route-1", "Orange Overhang", "V6", "Northern Rocks")
    private val slab = RouteEntity("route-2", "Grey Slab", "V2", "Southern Quarry")
    private var submitted: AttemptDetails? = null

    // Checks the menu lists every saved route, then the new route option last.
    @Test
    fun routeMenuListsExistingRoutesThenNewRouteOption() {
        showForm(listOf(overhang, slab))

        openRouteMenu()

        compose.onNodeWithText("Orange Overhang").assertIsDisplayed()
        compose.onNodeWithText("Grey Slab").assertIsDisplayed()
        val lastRouteBottom = compose.onNodeWithText("Grey Slab").fetchSemanticsNode().boundsInRoot.bottom
        val createTop = compose.onNodeWithText("+ New Route").fetchSemanticsNode().boundsInRoot.top
        assertTrue(createTop >= lastRouteBottom)
    }

    // Checks an existing route is reused as saved, so the form does not ask for its grade or location again.
    @Test
    fun choosingExistingRouteSubmitsItAndHidesGradeAndLocation() {
        showForm(listOf(overhang, slab))

        openRouteMenu()
        compose.onNodeWithText("Grey Slab").performClick()

        compose.onNodeWithContentDescription("Location").assertDoesNotExist()
        compose.onNodeWithText("Grade").assertDoesNotExist()
        submit()
        assertEquals("route-2", submitted?.routeId)
        assertEquals("Grey Slab", submitted?.routeName)
    }

    // Checks "+ New Route" opens a modal name field, and adding the name selects it as a new route.
    @Test
    fun newRouteOpensModalAndSubmitsTypedName() {
        showForm(listOf(overhang))

        openRouteMenu()
        compose.onNodeWithText("+ New Route").performClick()
        compose.onNodeWithContentDescription("New route name").performTextInput("Purple Arete")
        compose.onNodeWithText("Add").performClick()

        compose.onNodeWithContentDescription("New route name").assertDoesNotExist()
        compose.onNodeWithText("Purple Arete").assertIsDisplayed()
        compose.onNodeWithContentDescription("Location").assertExists()
        submit()
        assertNull(submitted?.routeId)
        assertEquals("Purple Arete", submitted?.routeName)
    }

    // Checks cancelling the modal leaves the route choice as it was.
    @Test
    fun cancellingNewRouteModalKeepsPreviousChoice() {
        showForm(listOf(overhang, slab))
        openRouteMenu()
        compose.onNodeWithText("Grey Slab").performClick()

        openRouteMenu()
        compose.onNodeWithText("+ New Route").performClick()
        compose.onNodeWithContentDescription("New route name").performTextInput("Ignored")
        compose.onNodeWithText("Cancel").performClick()

        compose.onNodeWithContentDescription("New route name").assertDoesNotExist()
        submit()
        assertEquals("route-2", submitted?.routeId)
    }

    // Checks the first route ever entered still goes through the menu, which then holds only "+ New Route".
    @Test
    fun withNoSavedRoutesTheMenuOffersOnlyNewRoute() {
        showForm(emptyList())

        openRouteMenu()

        compose.onNodeWithText("+ New Route").assertIsDisplayed()
    }

    private fun openRouteMenu() {
        compose.onNodeWithContentDescription("Route selector").performScrollTo().performClick()
    }

    private fun submit() {
        compose.onNodeWithText("UPLOAD & ANALYSE").performScrollTo().performClick()
    }

    // A selected video is needed for the upload button to be enabled.
    private fun showForm(routes: List<RouteEntity>) {
        compose.setContent {
            SendItTheme {
                Surface {
                    AttemptFormScreen(
                        selectedVideo = Uri.parse("content://com.example.sendit.test/first.mp4"),
                        onVideoSelected = {},
                        existingRoutes = routes,
                        onSubmit = { submitted = it }
                    )
                }
            }
        }
    }
}
