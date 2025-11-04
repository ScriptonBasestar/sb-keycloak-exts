package org.scriptonbasestar.kcexts.events.aws.config

import org.keycloak.events.EventType
import org.keycloak.models.KeycloakSession

/**
 * Configuration for AWS SQS/SNS Event Listener
 */
class AwsEventListenerConfig(
    session: KeycloakSession,
) {
    // AWS 기본 설정
    val awsRegion: String
    val awsAccessKeyId: String?
    val awsSecretAccessKey: String?
    val useInstanceProfile: Boolean

    // SQS 설정
    val useSqs: Boolean
    val sqsUserEventsQueueUrl: String
    val sqsAdminEventsQueueUrl: String

    // SNS 설정
    val useSns: Boolean
    val snsUserEventsTopicArn: String
    val snsAdminEventsTopicArn: String

    // 이벤트 필터링
    val includedEventTypes: Set<EventType>
    val enableAdminEvents: Boolean
    val enableUserEvents: Boolean

    init {
        val realmModel = session.context.realm
        val attributes = realmModel?.attributes ?: emptyMap()

        // AWS 기본 설정
        awsRegion =
            attributes["aws.region"]
                ?: System.getProperty("aws.region", "us-east-1")

        awsAccessKeyId =
            attributes["aws.access.key.id"]
                ?: System.getProperty("aws.access.key.id")

        awsSecretAccessKey =
            attributes["aws.secret.access.key"]
                ?: System.getProperty("aws.secret.access.key")

        useInstanceProfile =
            (
                attributes["aws.use.instance.profile"]
                    ?: System.getProperty("aws.use.instance.profile", "true")
            ).toBoolean()

        // SQS 설정
        useSqs =
            (
                attributes["aws.use.sqs"]
                    ?: System.getProperty("aws.use.sqs", "true")
            ).toBoolean()

        sqsUserEventsQueueUrl =
            attributes["aws.sqs.user.events.queue.url"]
                ?: System.getProperty("aws.sqs.user.events.queue.url", "")

        sqsAdminEventsQueueUrl =
            attributes["aws.sqs.admin.events.queue.url"]
                ?: System.getProperty("aws.sqs.admin.events.queue.url", "")

        // SNS 설정
        useSns =
            (
                attributes["aws.use.sns"]
                    ?: System.getProperty("aws.use.sns", "false")
            ).toBoolean()

        snsUserEventsTopicArn =
            attributes["aws.sns.user.events.topic.arn"]
                ?: System.getProperty("aws.sns.user.events.topic.arn", "")

        snsAdminEventsTopicArn =
            attributes["aws.sns.admin.events.topic.arn"]
                ?: System.getProperty("aws.sns.admin.events.topic.arn", "")

        // 이벤트 필터링
        val includedTypesStr =
            attributes["aws.included.event.types"]
                ?: System.getProperty("aws.included.event.types", "")

        includedEventTypes =
            if (includedTypesStr.isBlank()) {
                EventType.values().toSet()
            } else {
                includedTypesStr
                    .split(",")
                    .mapNotNull { typeName ->
                        try {
                            EventType.valueOf(typeName.trim().uppercase())
                        } catch (e: IllegalArgumentException) {
                            null
                        }
                    }.toSet()
            }

        enableAdminEvents =
            attributes["aws.enable.admin.events"]?.toBoolean()
                ?: System.getProperty("aws.enable.admin.events", "true").toBoolean()

        enableUserEvents =
            attributes["aws.enable.user.events"]?.toBoolean()
                ?: System.getProperty("aws.enable.user.events", "true").toBoolean()
    }
}
