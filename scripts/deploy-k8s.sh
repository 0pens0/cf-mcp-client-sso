#!/usr/bin/env bash
# Deploy cf-mcp-client-k8s to the current kubectl context (homelab VKS).
# Prerequisites: kubectl context, container image in a pullable registry,
# binding secrets (or ServiceBindings) for genai-chat, genai-embed, postgres-vector.
set -euo pipefail

NAMESPACE="${NAMESPACE:-demo}"
IMAGE="${IMAGE:?Set IMAGE to registry/cf-mcp-client-k8s:1.6.0}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

echo "==> Discovering sibling tanzubot workloads"
bash "$ROOT/scripts/discover-tanzubotk8s.sh" || true

echo "==> Applying namespace + config"
kubectl apply -f "$ROOT/deploy/k8s/00-namespace.yaml"
kubectl apply -f "$ROOT/deploy/k8s/10-configmap.yaml"

echo "==> Ensuring binding secrets exist (create if missing)"
for secret in genai-chat-binding genai-embed-binding postgres-vector-binding; do
  if ! kubectl -n "$NAMESPACE" get secret "$secret" >/dev/null 2>&1; then
    echo "Missing secret $NAMESPACE/$secret — create from deploy/k8s/secrets.example.yaml"
    exit 1
  fi
done

echo "==> Applying workload"
sed "s|image: cf-mcp-client-k8s:1.6.0|image: ${IMAGE}|; s|namespace: demo|namespace: ${NAMESPACE}|g" \
  "$ROOT/deploy/k8s/20-deployment.yaml" | kubectl apply -f -
sed "s|namespace: demo|namespace: ${NAMESPACE}|g" "$ROOT/deploy/k8s/30-service.yaml" | kubectl apply -f -
sed "s|namespace: demo|namespace: ${NAMESPACE}|g" "$ROOT/deploy/k8s/40-ingress.yaml" | kubectl apply -f -

kubectl -n "$NAMESPACE" rollout status deploy/cf-mcp-client-k8s --timeout=180s
kubectl -n "$NAMESPACE" get deploy,svc,ingress,pods -l app.kubernetes.io/name=cf-mcp-client-k8s
echo "Done. Open the Ingress host and verify chat + /upload."
