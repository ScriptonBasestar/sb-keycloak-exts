#!/bin/bash
# DLQ Reprocessing Tool for Keycloak Event Listeners
#
# 이 스크립트는 Dead Letter Queue에 저장된 실패한 이벤트를 재처리합니다.
#
# 사용법: ./dlq-reprocess.sh [options]
#
# 옵션:
#   -l, --listener <type>    리스너 타입 (kafka, rabbitmq, nats)
#   -p, --path <path>        DLQ 파일 경로 (기본: ./dlq)
#   -d, --dry-run            실제 재처리 없이 시뮬레이션만
#   -h, --help               도움말 표시
#
# 예시:
#   ./dlq-reprocess.sh --listener kafka --path /var/keycloak/dlq/kafka
#   ./dlq-reprocess.sh -l rabbitmq -d

set -e

# 기본 설정
LISTENER_TYPE=""
DLQ_PATH="./dlq"
DRY_RUN=false
KAFKA_BOOTSTRAP="localhost:9092"
RABBITMQ_HOST="localhost"
RABBITMQ_PORT="5672"
NATS_URL="nats://localhost:4222"

# 색상 코드
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 로그 함수
log_info() {
    echo -e "${GREEN}[INFO]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 도움말 표시
show_help() {
    cat << EOF
DLQ Reprocessing Tool for Keycloak Event Listeners

사용법:
    $0 [options]

옵션:
    -l, --listener <type>     리스너 타입 (kafka, rabbitmq, nats)
    -p, --path <path>         DLQ 파일 경로 (기본: ./dlq)
    -d, --dry-run             실제 재처리 없이 시뮬레이션만
    --kafka-bootstrap <addr>  Kafka 브로커 주소 (기본: localhost:9092)
    --rabbitmq-host <host>    RabbitMQ 호스트 (기본: localhost)
    --nats-url <url>          NATS 서버 URL (기본: nats://localhost:4222)
    -h, --help                도움말 표시

예시:
    $0 --listener kafka --path /var/keycloak/dlq/kafka
    $0 -l rabbitmq -d
    $0 --listener nats --nats-url nats://nats-server:4222

EOF
    exit 0
}

# 인자 파싱
while [[ $# -gt 0 ]]; do
    case $1 in
        -l|--listener)
            LISTENER_TYPE="$2"
            shift 2
            ;;
        -p|--path)
            DLQ_PATH="$2"
            shift 2
            ;;
        -d|--dry-run)
            DRY_RUN=true
            shift
            ;;
        --kafka-bootstrap)
            KAFKA_BOOTSTRAP="$2"
            shift 2
            ;;
        --rabbitmq-host)
            RABBITMQ_HOST="$2"
            shift 2
            ;;
        --nats-url)
            NATS_URL="$2"
            shift 2
            ;;
        -h|--help)
            show_help
            ;;
        *)
            log_error "Unknown option: $1"
            show_help
            ;;
    esac
done

# 필수 인자 확인
if [ -z "$LISTENER_TYPE" ]; then
    log_error "리스너 타입을 지정해주세요 (-l kafka|rabbitmq|nats)"
    exit 1
fi

if [ "$LISTENER_TYPE" != "kafka" ] && [ "$LISTENER_TYPE" != "rabbitmq" ] && [ "$LISTENER_TYPE" != "nats" ]; then
    log_error "지원하지 않는 리스너 타입: $LISTENER_TYPE"
    exit 1
fi

# DLQ 경로 확인
FULL_DLQ_PATH="$DLQ_PATH/$LISTENER_TYPE"
if [ ! -d "$FULL_DLQ_PATH" ]; then
    log_error "DLQ 경로를 찾을 수 없습니다: $FULL_DLQ_PATH"
    exit 1
fi

log_info "=== DLQ Reprocessing Tool ==="
log_info "리스너 타입: $LISTENER_TYPE"
log_info "DLQ 경로: $FULL_DLQ_PATH"
log_info "Dry-run 모드: $DRY_RUN"
log_info ""

# DLQ 파일 찾기
DLQ_FILES=$(find "$FULL_DLQ_PATH" -name "dlq-entry-*.json" 2>/dev/null)
FILE_COUNT=$(echo "$DLQ_FILES" | grep -c "dlq-entry" || echo "0")

if [ "$FILE_COUNT" -eq 0 ]; then
    log_warn "재처리할 DLQ 항목이 없습니다."
    exit 0
fi

log_info "발견된 DLQ 항목: $FILE_COUNT개"
log_info ""

# 통계 변수
SUCCESS_COUNT=0
FAILED_COUNT=0
SKIPPED_COUNT=0

# Kafka로 재전송
reprocess_kafka() {
    local file=$1
    local event_data=$(jq -r '.eventData' "$file")
    local destination=$(jq -r '.destination' "$file")
    local key=$(jq -r '.metadata.key // "reprocess"' "$file")

    if [ "$DRY_RUN" = true ]; then
        log_info "[DRY-RUN] Kafka로 전송: topic=$destination, key=$key"
        return 0
    fi

    # kafka-console-producer를 사용한 전송
    echo "$event_data" | kafka-console-producer \
        --bootstrap-server "$KAFKA_BOOTSTRAP" \
        --topic "$destination" \
        --property "parse.key=true" \
        --property "key.separator=:" \
        2>/dev/null << EOF
$key:$event_data
EOF

    return $?
}

# RabbitMQ로 재전송
reprocess_rabbitmq() {
    local file=$1
    local event_data=$(jq -r '.eventData' "$file")
    local destination=$(jq -r '.destination' "$file")
    local routing_key=$(jq -r '.metadata.routingKey // "keycloak.event"' "$file")

    if [ "$DRY_RUN" = true ]; then
        log_info "[DRY-RUN] RabbitMQ로 전송: exchange=$destination, routing_key=$routing_key"
        return 0
    fi

    # amqp-publish 사용 (rabbitmq-server 패키지 필요)
    if ! command -v amqp-publish &> /dev/null; then
        log_error "amqp-publish 명령을 찾을 수 없습니다. rabbitmq-server 패키지를 설치하세요."
        return 1
    fi

    echo "$event_data" | amqp-publish \
        --url="amqp://guest:guest@$RABBITMQ_HOST:$RABBITMQ_PORT/" \
        --exchange="$destination" \
        --routing-key="$routing_key" \
        2>/dev/null

    return $?
}

# NATS로 재전송
reprocess_nats() {
    local file=$1
    local event_data=$(jq -r '.eventData' "$file")
    local subject=$(jq -r '.destination' "$file")

    if [ "$DRY_RUN" = true ]; then
        log_info "[DRY-RUN] NATS로 전송: subject=$subject"
        return 0
    fi

    # nats-pub 사용 (nats-io/natscli 필요)
    if ! command -v nats &> /dev/null; then
        log_error "nats 명령을 찾을 수 없습니다. natscli를 설치하세요."
        return 1
    fi

    echo "$event_data" | nats pub "$subject" --server="$NATS_URL" 2>/dev/null

    return $?
}

# 각 DLQ 파일 처리
for file in $DLQ_FILES; do
    filename=$(basename "$file")
    log_info "처리 중: $filename"

    # JSON 유효성 검사
    if ! jq empty "$file" 2>/dev/null; then
        log_error "  ✗ 잘못된 JSON 형식"
        ((FAILED_COUNT++))
        continue
    fi

    # 이벤트 정보 추출
    event_type=$(jq -r '.eventType' "$file")
    realm=$(jq -r '.realm' "$file")
    timestamp=$(jq -r '.timestamp' "$file")
    failure_reason=$(jq -r '.failureReason' "$file")

    log_info "  - 이벤트 타입: $event_type"
    log_info "  - Realm: $realm"
    log_info "  - 실패 시각: $timestamp"
    log_info "  - 실패 원인: $failure_reason"

    # 리스너별 재처리
    case $LISTENER_TYPE in
        kafka)
            if reprocess_kafka "$file"; then
                log_info "  ✓ 재처리 성공"
                ((SUCCESS_COUNT++))
                # 성공 시 DLQ 파일 삭제 (dry-run이 아닌 경우)
                if [ "$DRY_RUN" = false ]; then
                    rm "$file"
                fi
            else
                log_error "  ✗ 재처리 실패"
                ((FAILED_COUNT++))
            fi
            ;;
        rabbitmq)
            if reprocess_rabbitmq "$file"; then
                log_info "  ✓ 재처리 성공"
                ((SUCCESS_COUNT++))
                if [ "$DRY_RUN" = false ]; then
                    rm "$file"
                fi
            else
                log_error "  ✗ 재처리 실패"
                ((FAILED_COUNT++))
            fi
            ;;
        nats)
            if reprocess_nats "$file"; then
                log_info "  ✓ 재처리 성공"
                ((SUCCESS_COUNT++))
                if [ "$DRY_RUN" = false ]; then
                    rm "$file"
                fi
            else
                log_error "  ✗ 재처리 실패"
                ((FAILED_COUNT++))
            fi
            ;;
    esac

    log_info ""
done

# 최종 결과 출력
log_info "=== 재처리 완료 ==="
log_info "총 처리: $FILE_COUNT개"
log_info "성공: ${GREEN}$SUCCESS_COUNT${NC}개"
log_info "실패: ${RED}$FAILED_COUNT${NC}개"

if [ "$FAILED_COUNT" -gt 0 ]; then
    exit 1
fi

exit 0
