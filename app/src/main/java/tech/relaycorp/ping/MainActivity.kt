package tech.relaycorp.ping

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tech.relaycorp.awaladroid.GatewayClient
import tech.relaycorp.awaladroid.endpoint.FirstPartyEndpoint
import tech.relaycorp.awaladroid.endpoint.PublicThirdPartyEndpoint
import tech.relaycorp.awaladroid.messaging.ParcelId
import tech.relaycorp.awaladroid.messaging.OutgoingMessage
import tech.relaycorp.relaynet.wrappers.x509.Certificate
import java.time.ZonedDateTime
import java.util.*
import javax.inject.Inject

class MainActivity : AppCompatActivity() {

    @Inject
    lateinit var repository: Repository

    private val component by lazy { (applicationContext as App).component }

    private val backgroundContext = lifecycleScope.coroutineContext + Dispatchers.IO
    private val backgroundScope = CoroutineScope(backgroundContext)
    lateinit var sender: FirstPartyEndpoint
    lateinit var recipient: PublicThirdPartyEndpoint

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        component.inject(this)
        setContentView(R.layout.activity_main)

        lifecycleScope.launch {
            send.isEnabled = false
            withContext(backgroundContext) {
                GatewayClient.bind()
                sender = FirstPartyEndpoint.register()
                recipient = PublicThirdPartyEndpoint.import(
                    "ping.awala.services",
                    Certificate.deserialize(
                        resources.openRawResource(R.raw.identity).use { it.readBytes() }
                    )
                )
            }
            send.isEnabled = true
        }

        repository
            .observe()
            .onEach {
                pings.text = it.joinToString("\n") { message ->
                    "Ping (sent=${Date(message.sent)}) (received=${
                        message.received?.let {
                            Date(
                                message.received
                            )
                        }
                    })"
                }
            }
            .launchIn(lifecycleScope)

        send.setOnClickListener {
            backgroundScope.launch {
                val id = ParcelId.generate()
                val authorization = sender.issueAuthorization(
                    recipient,
                    ZonedDateTime.now().plusDays(3)
                )
                val pingMessageSerialized = serializePingMessage(
                    id.value,
                    authorization.pdaSerialized,
                    authorization.pdaChainSerialized
                )
                val outgoingMessage = OutgoingMessage.build(
                    "application/vnd.relaynet.ping-v1.ping",
                    pingMessageSerialized,
                    senderEndpoint = sender,
                    recipientEndpoint = recipient,
                    parcelId = id
                )
                GatewayClient.sendMessage(outgoingMessage)
                val pingMessage = PingMessage(outgoingMessage.parcelId.value)
                repository.set(pingMessage)
            }
        }

        clear.setOnClickListener {
            backgroundScope.launch {
                repository.clear()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        GatewayClient.unbind()
    }
}
