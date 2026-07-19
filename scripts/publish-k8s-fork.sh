#!/usr/bin/env bash
# Publish the local tree as GitHub repo 0pens0/cf-mcp-client-k8s.
# Requires a GitHub token that can create repositories under 0pens0.
set -euo pipefail

OWNER="${OWNER:-0pens0}"
REPO="${REPO:-cf-mcp-client-k8s}"
SRC_DIR="${SRC_DIR:-.}"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf "$WORK_DIR"' EXIT

echo "Preparing fork content in $WORK_DIR"
cp -a "$SRC_DIR"/. "$WORK_DIR"/
rm -rf "$WORK_DIR"/.git "$WORK_DIR"/target
cp "$SRC_DIR/README-k8s.md" "$WORK_DIR/README.md"

python3 - <<PY
from pathlib import Path
root = Path("$WORK_DIR")
pom = (root / "pom.xml").read_text()
pom = pom.replace("<artifactId>cf-mcp-client</artifactId>", "<artifactId>cf-mcp-client-k8s</artifactId>", 1)
pom = pom.replace("<name>cf-mcp-client</name>", "<name>cf-mcp-client-k8s</name>", 1)
(root / "pom.xml").write_text(pom)
df = root / "Dockerfile"
df.write_text(df.read_text().replace("cf-mcp-client-*.jar", "cf-mcp-client-k8s-*.jar"))
manifest = root / "manifest.yml"
if manifest.exists():
    text = manifest.read_text().replace("cf-mcp-client-sso", "cf-mcp-client-k8s")
    text = text.replace("cf-mcp-client-1.6.0.jar", "cf-mcp-client-k8s-1.6.0.jar")
    manifest.write_text(text)
PY

cd "$WORK_DIR"
git init -b main
git add -A
git -c user.email="${GIT_EMAIL:-cursor@users.noreply.github.com}" \
    -c user.name="${GIT_NAME:-cursor}" \
    commit -m "feat: initial cf-mcp-client-k8s fork for VKS/Tanzu Platform"

if gh repo view "$OWNER/$REPO" >/dev/null 2>&1; then
  echo "Repo $OWNER/$REPO already exists"
else
  echo "Creating $OWNER/$REPO"
  gh repo create "$OWNER/$REPO" --public \
    --description "Kubernetes/VKS fork of cf-mcp-client-sso for Tanzu Platform (chat + embed + postgres)"
fi

git remote add origin "https://github.com/$OWNER/$REPO.git"
git push -u origin main
echo "Published https://github.com/$OWNER/$REPO"
