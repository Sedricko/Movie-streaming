package app.lumicast.data.stream

import android.content.Context
import android.net.ConnectivityManager
import java.net.Inet4Address

/** IPv4 da rede ativa (Wi-Fi) para montar a URL que a TV vai acessar. Retorna null se não houver. */
fun findLanIPv4(context: Context): String? {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return null
    val network = cm.activeNetwork ?: return null
    val props = cm.getLinkProperties(network) ?: return null
    return props.linkAddresses
        .map { it.address }
        .filterIsInstance<Inet4Address>()
        .firstOrNull { !it.isLoopbackAddress }
        ?.hostAddress
}
