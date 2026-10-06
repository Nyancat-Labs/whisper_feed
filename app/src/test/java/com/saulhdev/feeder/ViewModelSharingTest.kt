package com.saulhdev.feeder

import org.junit.After
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.java.KoinJavaComponent
import org.koin.androidx.viewmodel.dsl.viewModelOf

/**
 * Whether two surfaces asking for a view model get the same one.
 *
 * The app's feed resolves through `koinNeoViewModel`, which hands it to the
 * activity's ViewModelStore; the launcher panel has no activity and resolves
 * its own with `KoinJavaComponent.inject`. Both name the same binding.
 *
 * It decides whether a feature can be per-surface at all. The feed can now be
 * narrowed to one source, and that state lives in the view model: shared, a
 * filter set in the app would also narrow the launcher panel, with nothing on
 * that surface saying why — and the panel is somebody's home screen, so a feed
 * that had quietly lost most of itself would look like the app breaking.
 *
 * Answered by asking Koin rather than by reading the DSL, because the answer
 * is a property of this version and would change without anything in this
 * repository changing with it.
 */
class ViewModelSharingTest {

    private class Subject : androidx.lifecycle.ViewModel()

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `a viewModel binding resolved without an owner is a new instance each time`() {
        startKoin { modules(module { viewModelOf(::Subject) }) }

        val first = KoinJavaComponent.get<Subject>(Subject::class.java)
        val second = KoinJavaComponent.get<Subject>(Subject::class.java)

        assertNotSame(
            "viewModelOf now returns a shared instance, so the launcher panel and " +
                    "the app share one feed state — a source filter set in one " +
                    "would silently narrow the other",
            first,
            second,
        )
    }
}
