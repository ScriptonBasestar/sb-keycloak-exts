package org.scriptonbasestar.kcexts.events.aws

/**
 * AWS SQS/SNS event message wrapper for batch processing
 */
data class AwsEventMessage(
    val messageBody: String,
    val queueUrl: String?,
    val topicArn: String?,
    val messageAttributes: Map<String, String>,
    val eventType: String,
    val realm: String,
)
