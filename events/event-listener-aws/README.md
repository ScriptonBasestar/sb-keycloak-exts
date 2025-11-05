# Keycloak AWS SQS/SNS Event Listener

AWS SQS 및 SNS 기반 Keycloak 이벤트 리스너 구현체입니다.

## 특징

- **AWS SQS/SNS 통합**: AWS SDK v2 사용
- **유연한 메시징**: SQS, SNS 또는 둘 다 사용 가능
- **Resilience Patterns**: Circuit Breaker, Retry Policy, DLQ, Batch Processing
- **AWS 인증**: Instance Profile, Static Credentials 지원
- **Prometheus 메트릭**: 실시간 모니터링
- **서버리스 연동**: Lambda, EventBridge 통합 용이

## 사용 사례

- AWS 클라우드 환경의 Keycloak
- Lambda 함수로 이벤트 처리
- SNS Fan-out 패턴 (여러 구독자)
- SQS DLQ 활용 실패 이벤트 관리
- CloudWatch 통합 모니터링

## AWS 특성과 설계 배경

### 왜 AWS SQS/SNS 조합인가?
- **서버리스 워크로드 최적화**: Lambda, EventBridge 등과 자연스럽게 연동되어 Keycloak 이벤트를 서버리스 마이크로서비스로 전달하기 용이합니다.
- **내장 DLQ & 재시도**: SQS의 Redrive 정책과 SNS 구독별 재시도를 활용해 별도 브로커 없이도 운영 탄력성을 확보할 수 있습니다.
- **보안 및 규제 준수**: AWS IAM, KMS, PrivateLink 등과 결합해 규정 준수 환경에서 Keycloak 이벤트를 안전하게 전달하려는 요구에 부합합니다.
- **관리형 서비스**: 브로커 운영 부담이 없고, 비용 구조가 사용량 기반으로 명확하여 Keycloak 규모 성장에 따라 유연하게 확장됩니다.

### 모듈 구조와 설계 의도
- **양방향 전송 추상화**: `AwsEventListenerConfig`와 `AwsDestinationPublisher` 계층을 통해 SQS, SNS, 혹은 둘 다를 동시에 사용할 수 있도록 설계했습니다.
- **Factory 주도 초기화**: `AwsEventListenerProviderFactory`가 AWS 자격 증명(AWS SDK v2 `DefaultCredentialsProvider`), 회복력 컴포넌트, 메트릭 수집 객체를 초기화하여 Provider는 이벤트 처리에 집중합니다.
- **메시지 모델링**: `AwsEventMessage`에 대상(queueUrl/topicArn), 전송 채널, EventMeta를 포함해 DLQ 및 관찰성 도구가 한눈에 파악할 수 있도록 구성했습니다.
- **공통 회복력 재사용**: CircuitBreaker, RetryPolicy, DeadLetterQueue, BatchProcessor는 `event-listener-common` 구현체를 그대로 활용해 다른 모듈과 동일한 운영 경험을 제공합니다.
- **자격 증명 추상화**: Instance Profile, 정적 키, STS AssumeRole 등 다양한 인증 방식을 지원하도록 AWS SDK v2의 표준 Credential Provider 체인을 그대로 노출합니다.
- **관찰성 통합**: `AwsEventMetrics`가 SQS/SNS 전송 건수, DLQ 적재 상황을 Prometheus에 수집하고 `events/grafana-dashboard.json` 대시보드와 연계됩니다.

## 의존성

- **AWS SDK v2**: 2.29.45
- **Keycloak**: 26.0.7

## 설정

### Keycloak 설정 (standalone.xml)

```xml
<spi name="eventsListener">
    <provider name="aws-event-listener" enabled="true">
        <properties>
            <!-- AWS 기본 설정 -->
            <property name="awsRegion" value="us-east-1"/>
            <property name="awsUseInstanceProfile" value="true"/>
            <!-- 또는 정적 자격 증명 -->
            <property name="awsAccessKeyId" value="AKIAIOSFODNN7EXAMPLE"/>
            <property name="awsSecretAccessKey" value="wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY"/>

            <!-- SQS 설정 -->
            <property name="awsUseSqs" value="true"/>
            <property name="awsSqsUserEventsQueueUrl" value="https://sqs.us-east-1.amazonaws.com/123456789012/keycloak-user-events"/>
            <property name="awsSqsAdminEventsQueueUrl" value="https://sqs.us-east-1.amazonaws.com/123456789012/keycloak-admin-events"/>

            <!-- SNS 설정 (선택) -->
            <property name="awsUseSns" value="false"/>
            <property name="awsSnsUserEventsTopicArn" value="arn:aws:sns:us-east-1:123456789012:keycloak-user-events"/>
            <property name="awsSnsAdminEventsTopicArn" value="arn:aws:sns:us-east-1:123456789012:keycloak-admin-events"/>

            <!-- Resilience Patterns -->
            <property name="enableCircuitBreaker" value="true"/>
            <property name="enableRetry" value="true"/>
            <property name="maxRetryAttempts" value="3"/>
            <property name="enableDeadLetterQueue" value="true"/>

            <!-- Prometheus -->
            <property name="enablePrometheus" value="true"/>
            <property name="prometheusPort" value="9093"/>
        </properties>
    </provider>
</spi>
```

> ℹ️ `aws.*` 키는 Realm Attribute → SPI Config Scope(`standalone.xml`, `keycloak.conf`) → JVM System Property 순으로 적용됩니다. 운영 환경에 맞는 레벨에서 같은 키를 덮어쓰면 됩니다.

### 환경 변수

```bash
# AWS 인증
-Daws.region=us-east-1
-Daws.use.instance.profile=true

# SQS
-Daws.use.sqs=true
-Daws.sqs.user.events.queue.url=https://sqs.us-east-1.amazonaws.com/123456789012/keycloak-user-events

# SNS
-Daws.use.sns=false
```

## AWS 리소스 설정

### SQS 큐 생성

```bash
# 사용자 이벤트 큐
aws sqs create-queue \
  --queue-name keycloak-user-events \
  --attributes VisibilityTimeout=30,MessageRetentionPeriod=345600

# 관리자 이벤트 큐
aws sqs create-queue \
  --queue-name keycloak-admin-events \
  --attributes VisibilityTimeout=30,MessageRetentionPeriod=345600

# DLQ (Dead Letter Queue)
aws sqs create-queue \
  --queue-name keycloak-events-dlq \
  --attributes VisibilityTimeout=30,MessageRetentionPeriod=1209600
```

### SNS 토픽 생성

```bash
aws sns create-topic --name keycloak-user-events
aws sns create-topic --name keycloak-admin-events
```

### IAM 정책

Keycloak 인스턴스에 필요한 IAM 권한:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "sqs:SendMessage",
        "sqs:GetQueueUrl",
        "sqs:GetQueueAttributes"
      ],
      "Resource": [
        "arn:aws:sqs:us-east-1:123456789012:keycloak-user-events",
        "arn:aws:sqs:us-east-1:123456789012:keycloak-admin-events"
      ]
    },
    {
      "Effect": "Allow",
      "Action": [
        "sns:Publish",
        "sns:GetTopicAttributes"
      ],
      "Resource": [
        "arn:aws:sns:us-east-1:123456789012:keycloak-user-events",
        "arn:aws:sns:us-east-1:123456789012:keycloak-admin-events"
      ]
    }
  ]
}
```

## Lambda 소비 예제

### Python (boto3)

```python
import json

def lambda_handler(event, context):
    for record in event['Records']:
        # SQS 메시지
        if 'body' in record:
            keycloak_event = json.loads(record['body'])
            print(f"Event: {keycloak_event['type']}, User: {keycloak_event.get('userId')}")

        # SNS 메시지
        elif 'Sns' in record:
            message = json.loads(record['Sns']['Message'])
            print(f"Event: {message['type']}")

    return {'statusCode': 200}
```

### Node.js

```javascript
exports.handler = async (event) => {
  for (const record of event.Records) {
    // SQS 메시지
    if (record.body) {
      const keycloakEvent = JSON.parse(record.body);
      console.log(`Event: ${keycloakEvent.type}, User: ${keycloakEvent.userId}`);
    }

    // SNS 메시지
    if (record.Sns) {
      const message = JSON.parse(record.Sns.Message);
      console.log(`Event: ${message.type}`);
    }
  }

  return { statusCode: 200 };
};
```

## 아키텍처 패턴

### 1. Lambda 직접 처리

```
Keycloak → SQS → Lambda → DynamoDB/RDS
```

### 2. SNS Fan-out

```
Keycloak → SNS → ┬→ Lambda (알림)
                 ├→ SQS → Lambda (분석)
                 └→ EventBridge → Step Functions
```

### 3. DLQ 재처리

```
Keycloak → SQS → Lambda (실패) → DLQ → Lambda (재처리)
```

## 메트릭

Prometheus 메트릭 (포트: 9093):

```
keycloak_events_total{listener="aws",type="LOGIN"} 100
keycloak_aws_sqs_messages_sent_total 80
keycloak_aws_sns_messages_sent_total 20
keycloak_circuit_breaker_state{listener="aws"} 0.0
```

## 비용 최적화

### SQS 비용 (2024년 기준)

- 첫 100만 요청: 무료
- 이후: $0.40/100만 요청

**예상 비용**:
- 월 1천만 이벤트: $3.60

### SNS 비용

- 첫 100만 게시: 무료
- 이후: $0.50/100만 게시

## 빌드

```bash
./gradlew :events:event-listener-aws:build
```

## 배포

```bash
cp events/event-listener-aws/build/libs/keycloak-aws-event-listener.jar $KEYCLOAK_HOME/providers/
$KEYCLOAK_HOME/bin/kc.sh build
$KEYCLOAK_HOME/bin/kc.sh start
```

## 문제 해결

### 인증 실패

```bash
# Instance Profile 확인
aws sts get-caller-identity

# 권한 확인
aws sqs get-queue-attributes --queue-url <QUEUE_URL>
```

### 메시지 확인

```bash
# SQS 메시지 확인
aws sqs receive-message --queue-url <QUEUE_URL> --max-number-of-messages 10

# DLQ 확인
aws sqs get-queue-attributes --queue-url <DLQ_URL> --attribute-names ApproximateNumberOfMessages
```

## SQS vs SNS 선택 가이드

| 특징 | SQS | SNS |
|------|-----|-----|
| **처리 보장** | At-least-once | At-least-once |
| **순서** | FIFO 큐 가능 | 보장 안 함 |
| **팬아웃** | 불가 | 가능 (여러 구독자) |
| **재시도** | 자동 | 구독자별 설정 |
| **비용** | 낮음 | 약간 높음 |
| **사용 사례** | 단일 소비자 | 여러 소비자 |

## 참고 문서

- [AWS SQS 공식 문서](https://docs.aws.amazon.com/sqs/)
- [AWS SNS 공식 문서](https://docs.aws.amazon.com/sns/)
- [AWS SDK for Java v2](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/)
- [Resilience Patterns 가이드](../RESILIENCE_PATTERNS.md)
