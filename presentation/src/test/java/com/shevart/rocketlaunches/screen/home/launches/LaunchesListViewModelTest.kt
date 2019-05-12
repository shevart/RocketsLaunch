package com.shevart.rocketlaunches.screen.home.launches

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.nhaarman.mockitokotlin2.*
import com.shevart.domain.usecase.contract.LaunchesUseCase
import com.shevart.rocketlaunches.models.UILaunch
import com.shevart.rocketlaunches.screen.home.launches.LaunchesListViewModel.Event.OpenLaunchDetail
import com.shevart.rocketlaunches.screen.home.launches.LaunchesListViewModel.State.Loading
import com.shevart.rocketlaunches.screen.home.launches.LaunchesListViewModel.State.ShowLaunchesList
import com.shevart.rocketlaunches.screen.util.launch
import com.shevart.rocketlaunches.screen.util.launchesList
import com.shevart.rocketlaunches.usecase.UILaunchesUseCase
import com.shevart.rocketlaunches.usecase.UILaunchesUseCase.GetNextUILaunchesPage.UIResult
import io.reactivex.Completable
import io.reactivex.Single
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class LaunchesListViewModelTest {
    @Rule
    @JvmField
    val instantExecutorRule = InstantTaskExecutorRule()
    private val getNextLaunchesPageUseCase = mock<UILaunchesUseCase.GetNextUILaunchesPage>()
    private val addLaunchToFavoritesUseCase = mock<LaunchesUseCase.AddLaunchToFavorites>()
    private val removeLaunchFromFavoritesUseCase = mock<LaunchesUseCase.RemoveLaunchFromFavorites>()
    private val updateUILaunchFavoriteFieldUseCase =
        object : UILaunchesUseCase.UpdateUILaunchFavoriteField {
            override fun execute(launch: UILaunch, favorite: Boolean): UILaunch {
                return launch.copy(favorite = favorite)
            }
        }

    private val singleLaunchesPage = UIResult(
        launches = launchesList,
        hasMoreItems = false
    )
    private val multiLaunchesPage = UIResult(
        launches = launchesList,
        hasMoreItems = true
    )
    private val singleLaunchesPageAllFavorites = UIResult(
        launches = launchesList.map { it.copy(favorite = true) },
        hasMoreItems = true
    )

    @Before
    fun setUp() {
        whenever(getNextLaunchesPageUseCase.execute(0))
            .thenReturn(Single.just(singleLaunchesPage))
        whenever(addLaunchToFavoritesUseCase.execute(any()))
            .thenReturn(Completable.complete())
        whenever(removeLaunchFromFavoritesUseCase.execute(any()))
            .thenReturn(Completable.complete())
    }

    @Test
    fun `test initial state`() {
        // prepare
        whenever(getNextLaunchesPageUseCase.execute(0)).thenReturn(Single.never())
        val viewModel = createViewModel()

        // perform
        val state = viewModel.getStateLiveData().value

        // check
        assertEquals(Loading, state)
    }

    @Test
    fun `test initial state - first page loaded, no more items`() {
        // prepare
        val viewModel = createViewModel()

        // perform
        val state = viewModel.getStateLiveData().value as ShowLaunchesList

        // check
        assertEquals(singleLaunchesPage.launches, state.launchesItems)
        assertEquals(false, state.showBottomListLoadingIndicator)
    }

    @Test
    fun `test initial state - first page loaded, can load more`() {
        // prepare
        whenever(getNextLaunchesPageUseCase.execute(0))
            .thenReturn(Single.just(multiLaunchesPage))
        val viewModel = createViewModel()

        // perform
        val state = viewModel.getStateLiveData().value as ShowLaunchesList

        // check
        assertEquals(singleLaunchesPage.launches, state.launchesItems)
        assertEquals(true, state.showBottomListLoadingIndicator)
    }

    @Test
    fun `test open launch detail`() {
        // prepare
        val viewModel = createViewModel()
        val eventsObserver = viewModel.getEventsObservable().test()

        // perform
        viewModel.openLaunchDetail(launch)

        // check
        val event = eventsObserver.values().first() as OpenLaunchDetail
        assertEquals(launch.id, event.launchId)
        eventsObserver.assertNoErrors()
    }

    @Test
    fun `test favorite click - add to favorites`() {
        // prepare
        val viewModel = createViewModel()
        val clickedItem = singleLaunchesPage.launches.first()

        // perform
        viewModel.favoriteButtonClick(clickedItem)

        // check
        val state = viewModel.getStateLiveData().value as ShowLaunchesList
        assertEquals(true, state.launchesItems.first().favorite)
        verify(addLaunchToFavoritesUseCase, times(1)).execute(clickedItem.id)
    }

    @Test
    fun `test favorite click - add to favorites, failed`() {
        // prepare
        whenever(addLaunchToFavoritesUseCase.execute(any()))
            .thenReturn(Completable.error(RuntimeException("Mock!")))
        val viewModel = createViewModel()
        val clickedItem = singleLaunchesPage.launches.first()

        // perform
        viewModel.favoriteButtonClick(clickedItem)

        // check
        val state = viewModel.getStateLiveData().value as ShowLaunchesList
        assertEquals(false, state.launchesItems.first().favorite)
        verify(addLaunchToFavoritesUseCase, times(1)).execute(clickedItem.id)
    }

    @Test
    fun `test favorite click - remove from favorites`() {
        // prepare
        whenever(getNextLaunchesPageUseCase.execute(0))
            .thenReturn(Single.just(singleLaunchesPageAllFavorites))
        val clickedItem = singleLaunchesPageAllFavorites.launches.first()
        val viewModel = createViewModel()

        // perform
        viewModel.favoriteButtonClick(clickedItem)

        // check
        val state = viewModel.getStateLiveData().value as ShowLaunchesList
        assertEquals(false, state.launchesItems.first().favorite)
        verify(removeLaunchFromFavoritesUseCase, times(1)).execute(clickedItem.id)
    }

    @Test
    fun `test favorite click - remove from favorites, failed`() {
        // prepare
        whenever(getNextLaunchesPageUseCase.execute(0))
            .thenReturn(Single.just(singleLaunchesPageAllFavorites))
        val clickedItem = singleLaunchesPageAllFavorites.launches.first()
        whenever(removeLaunchFromFavoritesUseCase.execute(any()))
            .thenReturn(Completable.error(RuntimeException("Mock!")))
        val viewModel = createViewModel()

        // perform
        viewModel.favoriteButtonClick(clickedItem)

        // check
        val state = viewModel.getStateLiveData().value as ShowLaunchesList
        assertEquals(true, state.launchesItems.first().favorite)
        verify(removeLaunchFromFavoritesUseCase, times(1)).execute(clickedItem.id)
    }

    private fun createViewModel() = LaunchesListViewModel(
        getNextLaunchesPageUseCase = getNextLaunchesPageUseCase,
        addLaunchToFavoritesUseCase = addLaunchToFavoritesUseCase,
        removeLaunchFromFavoritesUseCase = removeLaunchFromFavoritesUseCase,
        updateUILaunchFavoriteFieldUseCase = updateUILaunchFavoriteFieldUseCase
    )
}