package org.scriptonbasestar.kcexts.events.aws.config

import org.keycloak.Config
import org.keycloak.events.EventType
import org.keycloak.models.KeycloakSession
import org.scriptonbasestar.kcexts.events.common.config.ConfigLoader

/**
 * Configuration for AWS SQS/SNS Event Listener
 */
class AwsEventListenerConfig(
    session: KeycloakSession,
    configScope: Config.Scope?,
) {
    companion object {
        private const val PREFIX = "aws"
    }

    private val configLoader = ConfigLoader.forRuntime(session, configScope, PREFIX)

    // AWS 기본 설정
    val awsRegion: String = configLoader.getString("region", "us-east-1") ?: "us-east-1"
    val awsAccessKeyId: String? = configLoader.getString("access.key.id")
    val awsSecretAccessKey: String? = configLoader.getString("secret.access.key")
    val useInstanceProfile: Boolean = configLoader.getBoolean("use.instance.profile", true)

    // SQS 설정
    val useSqs: Boolean = configLoader.getBoolean("use.sqs", true)
    val sqsUserEventsQueueUrl: String = configLoader.getString("sqs.user.events.queue.url", "") ?: ""
    val sqsAdminEventsQueueUrl: String = configLoader.getString("sqs.admin.events.queue.url", "") ?: ""

    // SNS 설정
    val useSns: Boolean = configLoader.getBoolean("use.sns", false)
    val snsUserEventsTopicArn: String = configLoader.getString("sns.user.events.topic.arn", "") ?: ""
    val snsAdminEventsTopicArn: String = configLoader.getString("sns.admin.events.topic.arn", "") ?: ""

    // 이벤트 필터링
    val includedEventTypes: Set<EventType> =
        configLoader
            .getString("included.event.types", "")
            .orEmpty()
            .takeIf { it.isNotBlank() }
            ?.split(",")
            ?.mapNotNull { typeName ->
                try {
                    EventType.valueOf(typeName.trim().uppercase())
                } catch (_: IllegalArgumentException) {
                    null
                }
            }?.toSet()
            ?: EventType.values().toSet()
    val enableAdminEvents: Boolean = configLoader.getBoolean("enable.admin.events", true)
    val enableUserEvents: Boolean = configLoader.getBoolean("enable.user.events", true)
}
