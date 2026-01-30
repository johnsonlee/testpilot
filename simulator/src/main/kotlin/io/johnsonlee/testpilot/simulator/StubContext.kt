package io.johnsonlee.testpilot.simulator

/**
 * Minimal [android.content.Context] for Application and Activity base context.
 *
 * AOSP uses `ContextImpl` which is a massive class tied to system services.
 * We provide just enough for the launch sequence to work:
 * - `getPackageName()` — needed by Application and Activity
 * - `getApplicationContext()` — needed during attach
 * - `getClassLoader()` — needed for class loading
 * - `getApplicationInfo()` — needed by various framework internals
 */
class StubContext(
    private val pkg: String,
    private val appClassLoader: ClassLoader
) : android.content.ContextWrapper(null) {

    override fun getPackageName(): String = pkg

    override fun getApplicationContext(): android.content.Context = this

    override fun getClassLoader(): ClassLoader = appClassLoader

    override fun getApplicationInfo(): android.content.pm.ApplicationInfo {
        return android.content.pm.ApplicationInfo().apply {
            packageName = pkg
        }
    }
}
