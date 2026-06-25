#!/usr/bin/env bash
set -euo pipefail

ENDPOINT="${AWS_ENDPOINT_URL:-http://localhost:4566}"
REGION="${AWS_REGION:-us-east-1}"

export AWS_ACCESS_KEY_ID="${AWS_ACCESS_KEY_ID:-test}"
export AWS_SECRET_ACCESS_KEY="${AWS_SECRET_ACCESS_KEY:-test}"
export AWS_DEFAULT_REGION="$REGION"

echo "Creating SQS queues via $ENDPOINT ..."

aws --endpoint-url="$ENDPOINT" sqs create-queue --queue-name code-submissions >/dev/null 2>&1 || true
aws --endpoint-url="$ENDPOINT" sqs create-queue --queue-name execution-results >/dev/null 2>&1 || true

SUBMISSION_URL="$(aws --endpoint-url="$ENDPOINT" sqs get-queue-url --queue-name code-submissions --query QueueUrl --output text)"
RESULT_URL="$(aws --endpoint-url="$ENDPOINT" sqs get-queue-url --queue-name execution-results --query QueueUrl --output text)"

cat <<EOF

Add these to your shell before starting api-service and worker-service:

export AWS_REGION=$REGION
export AWS_ENDPOINT_URL=$ENDPOINT
export AWS_ACCESS_KEY_ID=$AWS_ACCESS_KEY_ID
export AWS_SECRET_ACCESS_KEY=$AWS_SECRET_ACCESS_KEY
export SQS_SUBMISSION_QUEUE_URL=$SUBMISSION_URL
export SQS_RESULT_QUEUE_URL=$RESULT_URL

EOF
