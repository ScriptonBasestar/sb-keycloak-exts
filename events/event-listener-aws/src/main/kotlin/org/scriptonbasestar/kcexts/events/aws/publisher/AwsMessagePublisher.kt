package org.scriptonbasestar.kcexts.events.aws.publisher

import org.jboss.logging.Logger
import org.scriptonbasestar.kcexts.events.aws.config.AwsEventListenerConfig
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sns.SnsClient
import software.amazon.awssdk.services.sns.model.MessageAttributeValue
import software.amazon.awssdk.services.sns.model.PublishRequest
import software.amazon.awssdk.services.sqs.SqsClient
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue as SqsMessageAttributeValue
import software.amazon.awssdk.services.sqs.model.SendMessageRequest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AWS SQS/SNS message publisher for Keycloak events
 */
class AwsMessagePublisher(
    private val config: AwsEventListenerConfig,
) {
    private val logger = Logger.getLogger(AwsMessagePublisher::class.java)
    private val closed = AtomicBoolean(false)

    private val sqsClient: SqsClient?
    private val snsClient: SnsClient?

    init {
        val credentialsProvider = createCredentialsProvider()
        val region = Region.of(config.awsRegion)

        sqsClient =
            if (config.useSqs) {
                SqsClient
                    .builder()
                    .region(region)
                    .credentialsProvider(credentialsProvider)
                    .build()
                    .also {
                        logger.info("AWS SQS client initialized for region: ${config.awsRegion}")
                    }
            } else {
                logger.info("AWS SQS is disabled")
                null
            }

        snsClient =
            if (config.useSns) {
                SnsClient
                    .builder()
                    .region(region)
                    .credentialsProvider(credentialsProvider)
                    .build()
                    .also {
                        logger.info("AWS SNS client initialized for region: ${config.awsRegion}")
                    }
            } else {
                logger.info("AWS SNS is disabled")
                null
            }
    }

    private fun createCredentialsProvider(): AwsCredentialsProvider {
        return if (config.useInstanceProfile) {
            logger.info("Using AWS instance profile credentials")
            InstanceProfileCredentialsProvider.create()
        } else if (config.awsAccessKeyId != null && config.awsSecretAccessKey != null) {
            logger.info("Using AWS static credentials")
            StaticCredentialsProvider.create(
                AwsBasicCredentials.create(config.awsAccessKeyId, config.awsSecretAccessKey),
            )
        } else {
            logger.warn("No AWS credentials configured, using default provider chain")
            InstanceProfileCredentialsProvider.create()
        }
    }

    /**
     * Send message to SQS queue
     */
    fun sendToSqs(
        queueUrl: String,
        messageBody: String,
        attributes: Map<String, String>,
    ): String? {
        if (closed.get() || sqsClient == null) {
            logger.warn("SQS client is not available")
            return null
        }

        return try {
            val sqsAttributes =
                attributes.mapValues { (_, value) ->
                    SqsMessageAttributeValue
                        .builder()
                        .dataType("String")
                        .stringValue(value)
                        .build()
                }

            val request =
                SendMessageRequest
                    .builder()
                    .queueUrl(queueUrl)
                    .messageBody(messageBody)
                    .messageAttributes(sqsAttributes)
                    .build()

            val response = sqsClient.sendMessage(request)
            logger.debug("Message sent to SQS: queue=$queueUrl, messageId=${response.messageId()}")
            response.messageId()
        } catch (e: Exception) {
            logger.error("Error sending message to SQS: queue=$queueUrl", e)
            throw e
        }
    }

    /**
     * Send message to SNS topic
     */
    fun sendToSns(
        topicArn: String,
        messageBody: String,
        attributes: Map<String, String>,
    ): String? {
        if (closed.get() || snsClient == null) {
            logger.warn("SNS client is not available")
            return null
        }

        return try {
            val snsAttributes =
                attributes.mapValues { (_, value) ->
                    MessageAttributeValue
                        .builder()
                        .dataType("String")
                        .stringValue(value)
                        .build()
                }

            val request =
                PublishRequest
                    .builder()
                    .topicArn(topicArn)
                    .message(messageBody)
                    .messageAttributes(snsAttributes)
                    .build()

            val response = snsClient.publish(request)
            logger.debug("Message published to SNS: topic=$topicArn, messageId=${response.messageId()}")
            response.messageId()
        } catch (e: Exception) {
            logger.error("Error publishing message to SNS: topic=$topicArn", e)
            throw e
        }
    }

    /**
     * Send user event to configured destination
     */
    fun sendUserEvent(
        messageBody: String,
        attributes: Map<String, String>,
    ): String? {
        return when {
            config.useSqs && config.sqsUserEventsQueueUrl.isNotBlank() ->
                sendToSqs(config.sqsUserEventsQueueUrl, messageBody, attributes)
            config.useSns && config.snsUserEventsTopicArn.isNotBlank() ->
                sendToSns(config.snsUserEventsTopicArn, messageBody, attributes)
            else -> {
                logger.warn("No user events destination configured")
                null
            }
        }
    }

    /**
     * Send admin event to configured destination
     */
    fun sendAdminEvent(
        messageBody: String,
        attributes: Map<String, String>,
    ): String? {
        return when {
            config.useSqs && config.sqsAdminEventsQueueUrl.isNotBlank() ->
                sendToSqs(config.sqsAdminEventsQueueUrl, messageBody, attributes)
            config.useSns && config.snsAdminEventsTopicArn.isNotBlank() ->
                sendToSns(config.snsAdminEventsTopicArn, messageBody, attributes)
            else -> {
                logger.warn("No admin events destination configured")
                null
            }
        }
    }

    /**
     * Check if publisher is healthy
     */
    fun isHealthy(): Boolean {
        return try {
            !closed.get() &&
                ((sqsClient != null && config.useSqs) || (snsClient != null && config.useSns))
        } catch (e: Exception) {
            logger.warn("Health check failed", e)
            false
        }
    }

    /**
     * Close AWS clients
     */
    fun close() {
        if (closed.compareAndSet(false, true)) {
            try {
                sqsClient?.close()
                snsClient?.close()
                logger.info("AWS clients closed successfully")
            } catch (e: Exception) {
                logger.error("Error closing AWS clients", e)
            }
        }
    }
}
