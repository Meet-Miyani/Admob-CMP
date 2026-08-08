package dev.avinya.ads.nativead

import dev.avinya.ads.internal.NativeMemoryPressure
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplicationDidReceiveMemoryWarningNotification
import platform.darwin.NSObjectProtocol

internal interface IosMemoryWarnings { fun add(callback: () -> Unit): Any; fun remove(token: Any) }
internal class IosNativeMemorySignal(private val warnings: IosMemoryWarnings = SystemIosMemoryWarnings, private var callback: ((NativeMemoryPressure) -> Unit)?) : AutoCloseable {
    private var token: Any? = warnings.add { callback?.invoke(NativeMemoryPressure.Critical) }
    override fun close() { token?.let { warnings.remove(it); token = null }; callback = null }
}
private object SystemIosMemoryWarnings : IosMemoryWarnings {
    override fun add(callback: () -> Unit): Any = NSNotificationCenter.defaultCenter.addObserverForName(UIApplicationDidReceiveMemoryWarningNotification, null, null) { callback() }
    override fun remove(token: Any) { (token as? NSObjectProtocol)?.let { NSNotificationCenter.defaultCenter.removeObserver(it) } }
}
