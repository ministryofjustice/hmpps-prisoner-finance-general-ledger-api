#!/usr/bin/env bash

set -euo pipefail

# ---- Environment selection ----
echo "Select environment:"
select ENV in "dev" "preprod" "prod"; do
  [[ -n "$ENV" ]] && break
  echo "Invalid selection"
done

# ---- Command selection ----
echo "Select command to run:"
select ACTION in "describe-instance" "reboot-instance"; do
  [[ -n "$ACTION" ]] && break
  echo "Invalid selection"
done

# ---- Config ----
NAMESPACE="hmpps-prisoner-finance-general-ledger-${ENV}"
SECRET_NAME="rds-postgresql-instance-output"
SECRET_KEY="rds_instance_address"

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
echo "Action      : $ACTION"
echo "Namespace   : $NAMESPACE"
echo "Pod         : $POD_NAME"
echo "========================"
echo ""

echo "⚠️  WARNING: Rebooting a database drops all active connections. Please check this information carefully."

if [[ "$ACTION" == "reboot-instance" ]]; then
  CONFIRM_WORD="REBOOT"
else
  CONFIRM_WORD="YES"
fi

read -rp "Type $CONFIRM_WORD to proceed: " CONFIRM

if [[ "$CONFIRM" != "$CONFIRM_WORD" ]]; then
  echo "Aborted."
  exit 1
fi

# GUARD FOR ACCIDENTAL PROD REBOOTS
if [[ "$ACTION" == "reboot-instance" && "$ENV" == "prod" ]]; then
  echo "⚠️  You are about to REBOOT a PROD database."
  read -rp "Type REBOOT-PROD to confirm: " CONFIRM

  if [[ "$CONFIRM" != "REBOOT-PROD" ]]; then
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

RDS_ADDRESS=$(echo "$ENCODED_VALUE" | base64 --decode)

# Parse the DB Instance Identifier from the FQDN
# (e.g., extracts "my-db-instance" from "my-db-instance.cluster-xyz.eu-west-2.rds.amazonaws.com")
DB_IDENTIFIER=$(echo "$RDS_ADDRESS" | cut -d'.' -f1)

echo "Extracted DB Identifier: $DB_IDENTIFIER"

# ---- Execute action ----
echo "Running command in pod..."

if [[ "$ACTION" == "describe-instance" ]]; then
  kubectl exec -it "$POD_NAME" -n "$NAMESPACE" -- \
    aws rds describe-db-instances \
      --db-instance-identifier "$DB_IDENTIFIER"

elif [[ "$ACTION" == "reboot-instance" ]]; then
  echo "🚨 Rebooting RDS instance..."
  kubectl exec -it "$POD_NAME" -n "$NAMESPACE" -- \
    aws rds reboot-db-instance \
      --db-instance-identifier "$DB_IDENTIFIER"
fi

echo "Done."