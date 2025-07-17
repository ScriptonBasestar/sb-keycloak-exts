package org.scriptonbasestar.kcexts.events.kafka.performance

import org.jboss.logging.Logger
import java.lang.management.GarbageCollectorMXBean
import java.lang.management.ManagementFactory
import java.lang.management.MemoryMXBean
import java.lang.management.MemoryUsage
import java.util.concurrent.atomic.AtomicLong

/**
 * JVM Performance Optimizer
 * Provides JVM tuning recommendations and monitoring
 */
class JvmOptimizer {
    companion object {
        private val logger = Logger.getLogger(JvmOptimizer::class.java)

        // Memory thresholds (percentages)
        private const val HEAP_WARNING_THRESHOLD = 80.0
        private const val HEAP_CRITICAL_THRESHOLD = 95.0
        private const val GC_TIME_WARNING_THRESHOLD = 5.0 // 5% of time spent in GC
        private const val GC_TIME_CRITICAL_THRESHOLD = 10.0 // 10% of time spent in GC

        // JVM profiles
        const val PROFILE_THROUGHPUT = "throughput"
        const val PROFILE_LOW_LATENCY = "low_latency"
        const val PROFILE_BALANCED = "balanced"
        const val PROFILE_MEMORY_CONSTRAINED = "memory_constrained"
    }

    private val memoryBean: MemoryMXBean = ManagementFactory.getMemoryMXBean()
    private val gcBeans: List<GarbageCollectorMXBean> = ManagementFactory.getGarbageCollectorMXBeans()

    private val lastGcTime = AtomicLong(0)
    private val lastGcCollectionTime = AtomicLong(0)

    /**
     * Get optimized JVM arguments for different performance profiles
     */
    fun getOptimizedJvmArgs(
        profile: String,
        heapSize: String = "1g",
        maxHeapSize: String = "2g",
    ): List<String> {
        val jvmArgs = mutableListOf<String>()

        // Basic memory settings
        jvmArgs.add("-Xms$heapSize")
        jvmArgs.add("-Xmx$maxHeapSize")

        when (profile.lowercase()) {
            PROFILE_THROUGHPUT -> addThroughputOptimizations(jvmArgs)
            PROFILE_LOW_LATENCY -> addLowLatencyOptimizations(jvmArgs)
            PROFILE_BALANCED -> addBalancedOptimizations(jvmArgs)
            PROFILE_MEMORY_CONSTRAINED -> addMemoryConstrainedOptimizations(jvmArgs)
            else -> {
                logger.warn("Unknown JVM profile: $profile, using balanced configuration")
                addBalancedOptimizations(jvmArgs)
            }
        }

        // Common optimizations
        addCommonOptimizations(jvmArgs)

        logger.info("Generated JVM arguments for $profile profile: ${jvmArgs.joinToString(" ")}")
        return jvmArgs
    }

    /**
     * Throughput-optimized JVM settings
     */
    private fun addThroughputOptimizations(jvmArgs: MutableList<String>) {
        // Use Parallel GC for maximum throughput
        jvmArgs.addAll(
            listOf(
                "-XX:+UseParallelGC",
                "-XX:+UseParallelOldGC",
                "-XX:ParallelGCThreads=4",
                "-XX:MaxGCPauseMillis=200",
                "-XX:GCTimeRatio=99", // Spend max 1% time in GC
                "-XX:AdaptiveSizePolicy", // Let JVM tune heap automatically
                "-XX:+UseAdaptiveSizePolicy",
            ),
        )

        logger.info("Applied throughput JVM optimizations: Parallel GC, adaptive sizing")
    }

    /**
     * Low-latency optimized JVM settings
     */
    private fun addLowLatencyOptimizations(jvmArgs: MutableList<String>) {
        // Use G1 GC for low latency
        jvmArgs.addAll(
            listOf(
                "-XX:+UseG1GC",
                "-XX:MaxGCPauseMillis=50", // Target 50ms max pause
                "-XX:G1HeapRegionSize=16m",
                "-XX:G1NewSizePercent=30",
                "-XX:G1MaxNewSizePercent=40",
                "-XX:G1MixedGCCountTarget=4",
                "-XX:G1MixedGCLiveThresholdPercent=85",
                "-XX:+G1UseAdaptiveIHOP",
                "-XX:G1MixedGCLiveThresholdPercent=85",
            ),
        )

        // Additional low-latency settings
        jvmArgs.addAll(
            listOf(
                "-XX:+UnlockExperimentalVMOptions",
                "-XX:+UseCGroupMemoryLimitForHeap",
                "-XX:+ExitOnOutOfMemoryError",
            ),
        )

        logger.info("Applied low-latency JVM optimizations: G1 GC with 50ms pause target")
    }

    /**
     * Balanced JVM settings
     */
    private fun addBalancedOptimizations(jvmArgs: MutableList<String>) {
        // Use G1 GC with moderate settings
        jvmArgs.addAll(
            listOf(
                "-XX:+UseG1GC",
                "-XX:MaxGCPauseMillis=200", // Target 200ms max pause
                "-XX:G1HeapRegionSize=16m",
                "-XX:G1NewSizePercent=20",
                "-XX:G1MaxNewSizePercent=30",
                "-XX:G1MixedGCCountTarget=8",
                "-XX:+G1UseAdaptiveIHOP",
            ),
        )

        logger.info("Applied balanced JVM optimizations: G1 GC with 200ms pause target")
    }

    /**
     * Memory-constrained JVM settings
     */
    private fun addMemoryConstrainedOptimizations(jvmArgs: MutableList<String>) {
        // Use Serial GC for minimal memory overhead
        jvmArgs.addAll(
            listOf(
                "-XX:+UseSerialGC",
                "-XX:MaxGCPauseMillis=500", // Accept longer pauses for less memory
                "-Xss256k", // Reduce stack size
                "-XX:MetaspaceSize=128m",
                "-XX:MaxMetaspaceSize=256m",
                "-XX:CompressedClassSpaceSize=64m",
            ),
        )

        // Aggressive memory optimization
        jvmArgs.addAll(
            listOf(
                "-XX:+UseCompressedOops",
                "-XX:+UseCompressedClassPointers",
                "-XX:+OptimizeStringConcat",
                "-XX:+UseStringDeduplication",
            ),
        )

        logger.info("Applied memory-constrained JVM optimizations: Serial GC, compressed OOPs")
    }

    /**
     * Common JVM optimizations applicable to all profiles
     */
    private fun addCommonOptimizations(jvmArgs: MutableList<String>) {
        // JIT compiler optimizations
        jvmArgs.addAll(
            listOf(
                "-XX:+TieredCompilation",
                "-XX:TieredStopAtLevel=4",
                "-XX:+UseCodeCacheFlushing",
                "-XX:ReservedCodeCacheSize=256m",
            ),
        )

        // Performance monitoring
        jvmArgs.addAll(
            listOf(
                "-XX:+PrintGC",
                "-XX:+PrintGCDetails",
                "-XX:+PrintGCTimeStamps",
                "-XX:+PrintGCApplicationStoppedTime",
                "-XX:+UseGCLogFileRotation",
                "-XX:NumberOfGCLogFiles=5",
                "-XX:GCLogFileSize=100m",
                "-Xloggc:/var/log/keycloak/gc.log",
            ),
        )

        // Security and debugging
        jvmArgs.addAll(
            listOf(
                "-Djava.security.egd=file:/dev/./urandom", // Faster random number generation
                "-Dfile.encoding=UTF-8",
                "-Duser.timezone=UTC",
                "-Djava.awt.headless=true",
            ),
        )

        // Network optimizations
        jvmArgs.addAll(
            listOf(
                "-Djava.net.preferIPv4Stack=true",
                "-Dsun.net.inetaddr.ttl=60",
                "-Dsun.net.inetaddr.negative.ttl=10",
            ),
        )

        // Kafka client optimizations
        jvmArgs.addAll(
            listOf(
                "-Dkafka.logs.dir=/var/log/keycloak/kafka",
                "-Dlog4j.configuration=file:/opt/keycloak/conf/log4j.properties",
            ),
        )
    }

    /**
     * Monitor current JVM performance and provide recommendations
     */
    fun analyzeJvmPerformance(): JvmPerformanceAnalysis {
        val heapUsage = memoryBean.heapMemoryUsage
        val nonHeapUsage = memoryBean.nonHeapMemoryUsage

        val heapUtilization = (heapUsage.used.toDouble() / heapUsage.max.toDouble()) * 100
        val nonHeapUtilization = (nonHeapUsage.used.toDouble() / nonHeapUsage.max.toDouble()) * 100

        val gcStats = analyzeGarbageCollection()

        val issues = mutableListOf<PerformanceIssue>()
        val recommendations = mutableListOf<String>()

        // Analyze heap usage
        when {
            heapUtilization > HEAP_CRITICAL_THRESHOLD -> {
                issues.add(
                    PerformanceIssue(
                        severity = IssueSeverity.CRITICAL,
                        message = "Critical heap usage: ${heapUtilization.format(1)}%",
                        recommendation = "Increase heap size or tune application memory usage",
                    ),
                )
                recommendations.add("Consider increasing -Xmx value")
                recommendations.add("Review application for memory leaks")
            }
            heapUtilization > HEAP_WARNING_THRESHOLD -> {
                issues.add(
                    PerformanceIssue(
                        severity = IssueSeverity.MEDIUM,
                        message = "High heap usage: ${heapUtilization.format(1)}%",
                        recommendation = "Monitor heap usage and consider tuning",
                    ),
                )
            }
        }

        // Analyze GC performance
        if (gcStats.gcTimePercentage > GC_TIME_CRITICAL_THRESHOLD) {
            issues.add(
                PerformanceIssue(
                    severity = IssueSeverity.CRITICAL,
                    message = "Excessive GC time: ${gcStats.gcTimePercentage.format(1)}%",
                    recommendation = "Tune GC settings or increase heap size",
                ),
            )
            recommendations.add("Consider switching to G1 GC for better pause times")
            recommendations.add("Increase heap size to reduce GC pressure")
        } else if (gcStats.gcTimePercentage > GC_TIME_WARNING_THRESHOLD) {
            issues.add(
                PerformanceIssue(
                    severity = IssueSeverity.MEDIUM,
                    message = "High GC time: ${gcStats.gcTimePercentage.format(1)}%",
                    recommendation = "Monitor GC performance and consider tuning",
                ),
            )
        }

        // Analyze GC pause times
        if (gcStats.maxPauseTime > 1000) { // 1 second
            issues.add(
                PerformanceIssue(
                    severity = IssueSeverity.HIGH,
                    message = "Long GC pause: ${gcStats.maxPauseTime}ms",
                    recommendation = "Consider using G1 GC with lower pause time target",
                ),
            )
            recommendations.add("Add -XX:MaxGCPauseMillis=200 for G1 GC")
        }

        return JvmPerformanceAnalysis(
            heapUsage = heapUsage,
            nonHeapUsage = nonHeapUsage,
            heapUtilization = heapUtilization,
            nonHeapUtilization = nonHeapUtilization,
            gcStats = gcStats,
            issues = issues,
            recommendations = recommendations,
        )
    }

    /**
     * Analyze garbage collection performance
     */
    private fun analyzeGarbageCollection(): GcStats {
        var totalCollections = 0L
        var totalCollectionTime = 0L
        var maxPauseTime = 0L

        for (gcBean in gcBeans) {
            totalCollections += gcBean.collectionCount
            totalCollectionTime += gcBean.collectionTime

            // Estimate max pause time (this is an approximation)
            if (gcBean.collectionCount > 0) {
                val avgPauseTime = gcBean.collectionTime / gcBean.collectionCount
                maxPauseTime = maxOf(maxPauseTime, avgPauseTime)
            }
        }

        val currentTime = System.currentTimeMillis()
        val runtime = ManagementFactory.getRuntimeMXBean().uptime

        val gcTimePercentage =
            if (runtime > 0) {
                (totalCollectionTime.toDouble() / runtime.toDouble()) * 100
            } else {
                0.0
            }

        return GcStats(
            totalCollections = totalCollections,
            totalCollectionTime = totalCollectionTime,
            gcTimePercentage = gcTimePercentage,
            maxPauseTime = maxPauseTime,
            avgPauseTime = if (totalCollections > 0) totalCollectionTime / totalCollections else 0L,
        )
    }

    /**
     * Get JVM tuning recommendations based on current performance
     */
    fun getJvmTuningRecommendations(analysis: JvmPerformanceAnalysis): List<String> {
        val recommendations = mutableListOf<String>()

        // Existing recommendations from analysis
        recommendations.addAll(analysis.recommendations)

        // Additional specific recommendations
        if (analysis.heapUtilization > 90) {
            recommendations.add("Consider increasing heap size by 50%")
            recommendations.add("Enable heap dump on OOM: -XX:+HeapDumpOnOutOfMemoryError")
        }

        if (analysis.gcStats.totalCollections > 1000 && analysis.gcStats.avgPauseTime > 100) {
            recommendations.add("Tune G1 young generation: -XX:G1NewSizePercent=30")
            recommendations.add("Adjust G1 region size: -XX:G1HeapRegionSize=32m")
        }

        if (analysis.nonHeapUtilization > 80) {
            recommendations.add("Increase metaspace size: -XX:MaxMetaspaceSize=512m")
            recommendations.add("Monitor class loading and unloading")
        }

        return recommendations.distinct()
    }

    /**
     * Generate JVM configuration file
     */
    fun generateJvmConfigFile(
        profile: String,
        heapSize: String,
        maxHeapSize: String,
        outputPath: String,
    ) {
        val jvmArgs = getOptimizedJvmArgs(profile, heapSize, maxHeapSize)
        val configContent =
            buildString {
                appendLine("# JVM Configuration for Keycloak Kafka Event Listener")
                appendLine("# Profile: $profile")
                appendLine("# Generated: ${java.time.LocalDateTime.now()}")
                appendLine()

                appendLine("# Memory Settings")
                appendLine("JAVA_OPTS=\"\$JAVA_OPTS ${jvmArgs.joinToString(" \"")}")
                appendLine()

                appendLine("# Environment Variables")
                appendLine("export KAFKA_HEAP_OPTS=\"-Xms512m -Xmx1g\"")
                appendLine("export KAFKA_JVM_PERFORMANCE_OPTS=\"-server -XX:+UseG1GC -XX:MaxGCPauseMillis=20\"")
            }

        // In a real implementation, this would write to a file
        logger.info("JVM configuration generated for $profile profile")
        logger.debug("Configuration content:\n$configContent")
    }

    private fun Double.format(digits: Int) = "%.${digits}f".format(this)
}

/**
 * JVM performance analysis result
 */
data class JvmPerformanceAnalysis(
    val heapUsage: MemoryUsage,
    val nonHeapUsage: MemoryUsage,
    val heapUtilization: Double,
    val nonHeapUtilization: Double,
    val gcStats: GcStats,
    val issues: List<PerformanceIssue>,
    val recommendations: List<String>,
)

/**
 * Garbage collection statistics
 */
data class GcStats(
    val totalCollections: Long,
    val totalCollectionTime: Long,
    val gcTimePercentage: Double,
    val maxPauseTime: Long,
    val avgPauseTime: Long,
)
