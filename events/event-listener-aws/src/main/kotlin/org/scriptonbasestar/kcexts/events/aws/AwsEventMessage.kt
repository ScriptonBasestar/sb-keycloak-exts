package org.scriptonbasestar.kcexts.events.aws

import org.scriptonbasestar.kcexts.events.common.model.EventMeta

/**
 * AWS SQS/SNS event message wrapper for batch processing
 */
data class AwsEventMessage(
    val messageBody: String,
    val queueUrl: String?,
    val topicArn: String?,
    val messageAttributes: Map<String, String>,
    val meta: EventMeta,
)
