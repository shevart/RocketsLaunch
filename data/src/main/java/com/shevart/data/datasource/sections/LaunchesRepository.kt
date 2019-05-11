package com.shevart.data.datasource.sections

import com.shevart.data.local.LocalDataProvider
import com.shevart.data.remote.RemoteDataProvider
import com.shevart.domain.contract.data.DataSource
import com.shevart.domain.contract.data.FetchPolicy
import com.shevart.domain.contract.data.FetchPolicy.REMOTE_ONLY
import com.shevart.domain.contract.data.PageRequest
import com.shevart.domain.contract.data.PageResult
import com.shevart.domain.models.common.DataWrapper
import com.shevart.domain.models.launch.RocketLaunch
import com.shevart.domain.util.mapByDataWrapper
import io.reactivex.Single
import javax.inject.Inject

class LaunchesRepository
@Inject constructor(
    remoteDataProvider: RemoteDataProvider,
    localDataProvider: LocalDataProvider
) : AbsSection(remoteDataProvider, localDataProvider), DataSource.LaunchesSection {
    private val launches = ArrayList<RocketLaunch>()
    private var totalLaunchesCount = 0

    override fun getLaunches(
        param: PageRequest,
        fetchPolicy: FetchPolicy
    ): Single<PageResult<RocketLaunch>> {
        val loadRemote =
            (fetchPolicy == REMOTE_ONLY) || param.biggerThanCacheItemsCount(launches.size)
        return if (loadRemote) {
            loadLaunchesListRemote(param)
        } else {
            getLaunchesListFromCache(param)
        }
    }

    override fun getLaunchById(
        launchId: Long,
        fetchPolicy: FetchPolicy
    ): Single<DataWrapper<RocketLaunch>> {
        val loadRemote =
            fetchPolicy == REMOTE_ONLY || !launches.hasLaunch(launchId)
        return if (loadRemote) {
            loadRocketLaunchRemote(launchId)
                .mapByDataWrapper()
        } else {
            getRocketLaunchFromLocal(launchId)
        }
    }

    private fun getLaunchesListFromCache(param: PageRequest) =
        Single.just(
            PageResult(
                items = launches.subList(param.offset, (param.offset + param.count)),
                count = param.count + 1,
                offset = param.offset,
                totalCount = totalLaunchesCount
            )
        )

    private fun loadLaunchesListRemote(param: PageRequest) =
        remote.getRocketLaunches(param.count, param.offset)
            .doOnSuccess { saveLaunchesList(param, it) }

    /**
     * There is very simple storage paged data to cache. It will be work
     * fine only if client will be request data consistently, page by page.
     * For other cases - data won't be saved to cache/local storage
     */
    private fun saveLaunchesList(param: PageRequest, result: PageResult<RocketLaunch>) {
        val firstPage = launches.isEmpty() && param.offset == 0
        val nextPage = launches.size == result.offset
        if (firstPage || nextPage) {
            launches.addAll(result.items)
        }
        totalLaunchesCount = result.totalCount
    }

    private fun loadRocketLaunchRemote(launchId: Long): Single<RocketLaunch> =
        remote.getRocketLaunchById(launchId)

    private fun getRocketLaunchFromLocal(launchId: Long): Single<DataWrapper<RocketLaunch>> =
        Single.fromCallable {
            val launch = launches.find { it.id == launchId }
            return@fromCallable DataWrapper(launch)
        }

    private companion object {
        private fun PageRequest.biggerThanCacheItemsCount(itemsCount: Int) =
            (this.offset + this.count) > itemsCount

        private fun List<RocketLaunch>.hasLaunch(launchId: Long) =
            this.find { it.id == launchId } != null
    }
}