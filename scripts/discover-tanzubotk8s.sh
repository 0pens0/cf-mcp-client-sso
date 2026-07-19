#!/usr/bin/env bash
# Discover tanzubotk8s on the current kubectl context and align deploy/k8s manifests.
set -euo pipefail

echo "Current context: $(kubectl config current-context 2>/dev/null || echo 'NONE')"
echo "--- Looking for tanzubot / tanzubotk8s / tanzubotdemo ---"
kubectl get deploy,svc,ingress,httpproxy -A 2>/dev/null | grep -iE 'tanzubot|mcp-client' || true
echo "--- Namespaces ---"
kubectl get ns 2>/dev/null || true
echo "--- Suggest: set NAMESPACE and IMAGE then run deploy/k8s apply ---"
