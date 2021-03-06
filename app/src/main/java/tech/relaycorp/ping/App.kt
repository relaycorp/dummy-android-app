package tech.relaycorp.ping

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import tech.relaycorp.ping.di.AppComponent
import tech.relaycorp.ping.di.AppModule
import tech.relaycorp.ping.di.DaggerAppComponent
import tech.relaycorp.awaladroid.GatewayClient
import tech.relaycorp.awaladroid.Awala
import javax.inject.Inject

class App : Application() {

    val coroutineContext = Dispatchers.IO + SupervisorJob()

    val component: AppComponent =
        DaggerAppComponent.builder()
            .appModule(AppModule(this))
            .build()

    @Inject
    lateinit var repository: Repository

    override fun onCreate() {
        super.onCreate()
        component.inject(this)

        CoroutineScope(coroutineContext).launch {
            Awala.setup(this@App)

            GatewayClient.receiveMessages().collect {
                val pingId = extractPingIdFromPongMessage(it.content)
                val pingMessage = repository.get(pingId) ?: PingMessage(pingId)
                repository.set(pingMessage.copy(received = System.currentTimeMillis()))

                it.ack()
            }
        }
    }
}
