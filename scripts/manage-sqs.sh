#!/usr/bin/env bash

set -euo pipefail

# ---- Queue selection ----
echo "Select queue type:"
select QUEUE_TYPE in "main" "dlq"; do
  [[ -n "$QUEUE_TYPE" ]] && break
  echo "Invalid selection"
done

# ---- Environment selection ----
echo "Select environment:"
select ENV in "dev" "preprod" "prod"; do
  [[ -n "$ENV" ]] && break
  echo "Invalid selection"
done

# ---- Command selection ----
echo "Select command to run:"
select ACTION in "get-attributes" "receive-message" "purge-queue"; do
  [[ -n "$ACTION" ]] && break
  echo "Invalid selection"
done

# ---- Config ----
NAMESPACE="hmpps-prisoner-finance-general-ledger-${ENV}"
SECRET_KEY="sqs_queue_url"

# ---- Queue mapping ----
if [[ "$QUEUE_TYPE" == "main" ]]; then
  SECRET_NAME="prisoner-finance-general-ledger-queue-for-calculated-balances"
else
  SECRET_NAME="prisoner-finance-general-ledger-queue-for-calculated-balances-dlq"
fi

# ---- Auto-detect pod ----
echo "Looking for service pod..."

POD_NAME=$(kubectl get pods -n "$NAMESPACE" \
  --no-headers -o custom-columns=":metadata.name" | grep service-pod | head -n 1 || true)

if [[ -z "$POD_NAME" ]]; then
  echo "❌ No pod found containing 'service-pod' in namespace $NAMESPACE"
  exit 1
fi


# ---- Confirmation ----
echo ""
echo "===== CONFIRMATION ====="
if [[ "$ENV" == "prod" ]]; then
  echo "Environment : ⚠️  prod ⚠️"
else
  echo "Environment : $ENV"
fi
echo "Queue       : $QUEUE_TYPE"
echo "Action      : $ACTION"
echo "Namespace   : $NAMESPACE"
echo "Pod         : $POD_NAME"
echo "========================"
echo ""

echo "⚠️  WARNING: This may be destructive. Please check this information carefully."

if [[ "$ACTION" == "purge-queue" ]]; then
  CONFIRM_WORD="PURGE"
else
  CONFIRM_WORD="YES"
fi

read -rp "Type $CONFIRM_WORD to proceed: " CONFIRM

if [[ "$CONFIRM" != "$CONFIRM_WORD" ]]; then
  echo "Aborted."
  exit 1
fi

# GUARD FOR ACCIDENTAL PROD PURGES

if [[ "$ACTION" == "purge-queue" && "$ENV" == "prod" ]]; then
  echo "⚠️  You are about to PURGE a PROD queue."
  read -rp "Type PURGE-PROD to confirm: " CONFIRM

  if [[ "$CONFIRM" != "PURGE-PROD" ]]; then
    echo "Aborted."
    exit 1
  fi
fi


### POST CONFIRMATION ###

# ---- Get secret ----
RAW_SECRET=$(kubectl get secret "$SECRET_NAME" -n "$NAMESPACE" -o json)
ENCODED_VALUE=$(echo "$RAW_SECRET" | jq -r ".data.\"$SECRET_KEY\"")

if [[ "$ENCODED_VALUE" == "null" ]]; then
  echo "Could not find key '$SECRET_KEY' in secret '$SECRET_NAME'"
  exit 1
fi

QUEUE_URL=$(echo "$ENCODED_VALUE" | base64 --decode)

echo "Queue URL extracted."

# ---- Execute action ----
echo "Running command in pod..."

if [[ "$ACTION" == "get-attributes" ]]; then
  kubectl exec -it "$POD_NAME" -n "$NAMESPACE" -- \
    aws sqs get-queue-attributes \
      --queue-url "$QUEUE_URL" \
      --attribute-names All

elif [[ "$ACTION" == "receive-message" ]]; then
  kubectl exec -it "$POD_NAME" -n "$NAMESPACE" -- \
    aws sqs receive-message \
      --queue-url "$QUEUE_URL" \
      --max-number-of-messages 5 \
      --visibility-timeout 0 \
      --wait-time-seconds 1

elif [[ "$ACTION" == "purge-queue" ]]; then
  echo "🚨 Purging queue..."
  kubectl exec -it "$POD_NAME" -n "$NAMESPACE" -- \
    aws sqs purge-queue \
      --queue-url "$QUEUE_URL"
fi

echo "Done."