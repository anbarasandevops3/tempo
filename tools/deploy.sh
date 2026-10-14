#!/usr/bin/env bash
# Publish dist/ to the gh-pages branch (GitHub Pages serves it at https://<user>.github.io/tempo/).
# Run via `npm run deploy`, which tests and builds first.
set -euo pipefail
cd "$(dirname "$0")/.."
rev=$(git rev-parse --short HEAD)
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
cp -r dist/. "$tmp"
touch "$tmp/.nojekyll"
cd "$tmp"
git init -q -b gh-pages
git add -A
git -c user.name="$(git -C "$OLDPWD" config user.name)" -c user.email="$(git -C "$OLDPWD" config user.email)" \
  commit -qm "Deploy $rev"
git push -qf "$(git -C "$OLDPWD" remote get-url origin)" gh-pages
echo "Deployed $rev to gh-pages"
