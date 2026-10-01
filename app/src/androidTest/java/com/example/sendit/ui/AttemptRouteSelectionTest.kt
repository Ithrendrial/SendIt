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

// Checks the route name is picked from the saved routes, with an option to create a new one.
@RunWith(AndroidJUnit4::class)
class AttemptRouteSelectionTest {
    @get:Rule
    val compose = createComposeRule()

    private val overhang = RouteEntity("route-1", "Orange Overhang", "V6", "Northern Rocks")
    private val slab = RouteEntity("route-2", "Grey Slab", "V2", "Southern Quarry")
    private var submitted: AttemptDetails? = null

    // Checks the menu lists every saved route, then the create option last.
    @Test
    fun routeMenuListsExistingRoutesThenCreateNewRoute() {
        showForm(listOf(overhang, slab))

        openRouteMenu()

        compose.onNodeWithText("Orange Overhang").assertIsDisplayed()
        compose.onNodeWithText("Grey Slab").assertIsDisplayed()
        val lastRouteBottom = compose.onNodeWithText("Grey Slab").fetchSemanticsNode().boundsInRoot.bottom
        val createTop = compose.onNodeWithText("Create new route").fetchSemanticsNode().boundsInRoot.top
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

    // Checks creating a new route opens a name field, and the submitted attempt has no existing route id.
    @Test
    fun createNewRouteOpensNameFieldAndSubmitsTypedName() {
        showForm(listOf(overhang))

        openRouteMenu()
        compose.onNodeWithText("Create new route").performClick()
        compose.onNodeWithContentDescription("Route Name").performTextInput("Purple Arete")

        submit()
        assertNull(submitted?.routeId)
        assertEquals("Purple Arete", submitted?.routeName)
        compose.onNodeWithContentDescription("Location").assertExists()
    }

    // Checks the first route ever entered does not need an extra tap, because there is nothing to pick from.
    @Test
    fun withNoSavedRoutesTheNameFieldIsShownImmediately() {
        showForm(emptyList())

        compose.onNodeWithContentDescription("Route Name").assertIsDisplayed()
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
