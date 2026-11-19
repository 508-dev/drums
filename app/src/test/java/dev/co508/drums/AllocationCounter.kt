package dev.co508.drums

/**
 * The current thread's allocated-bytes counter, from the JDK's com.sun.management
 * extension, or null on a JVM without it. Found reflectively: the Android compile
 * classpath doesn't have it. Reading it costs a few bytes itself.
 */
fun threadAllocatedBytes(): (() -> Long)? =
    runCatching {
        val bean = Class.forName("java.lang.management.ManagementFactory").getMethod("getThreadMXBean").invoke(null)
        val method = Class.forName("com.sun.management.ThreadMXBean").getMethod("getCurrentThreadAllocatedBytes")
        method.invoke(bean) as Long // Fails here, not later, if unsupported.
        return@runCatching { method.invoke(bean) as Long }
    }.getOrNull()

/** Allocation this small over thousands of calls means the calls allocate nothing; the counter itself costs a little. */
const val ALLOCATION_NOISE_BYTES = 1_024L
