package `fun`.abbas.wps_adb.data

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.jmdns.JmDNS
import javax.jmdns.ServiceEvent
import javax.jmdns.ServiceListener

data class MdnsServiceEndpoint(
    val instanceName: String,
    val host: String,
    val port: Int,
)

class AdbMdnsDiscovery {
    companion object {
        const val PAIRING_TYPE = "_adb-tls-pairing._tcp.local."
        const val CONNECT_TYPE = "_adb-tls-connect._tcp.local."

        fun formatEndpoint(host: String, port: Int): String = "$host:$port"

        fun matchesInstanceName(expected: String, announced: String): Boolean =
            announced == expected
    }

    fun listen(
        serviceType: String,
        instanceFilter: (String) -> Boolean = { true },
    ): Flow<MdnsServiceEndpoint> = callbackFlow {
        val jmdns = JmDNS.create()
        val listener = object : ServiceListener {
            override fun serviceAdded(event: ServiceEvent) {
                jmdns.requestServiceInfo(event.type, event.name, true)
            }

            override fun serviceRemoved(event: ServiceEvent) = Unit

            override fun serviceResolved(event: ServiceEvent) {
                val info = event.info ?: return
                val name = info.name ?: return
                if (!instanceFilter(name)) return
                val host = info.hostAddresses?.firstOrNull { address ->
                    !address.contains(':')
                } ?: return
                trySend(MdnsServiceEndpoint(name, host, info.port))
            }
        }
        jmdns.addServiceListener(serviceType, listener)
        awaitClose {
            jmdns.removeServiceListener(serviceType, listener)
            jmdns.close()
        }
    }
}
