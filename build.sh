#!/bin/sh
set -e
cd "$(dirname "$0")"
podman build -t speconn-kotlin:build -f Containerfile.build --output type=local,dest=/tmp/speconn-kotlin-out .
echo "speconn-kotlin: build OK"
ls -la /tmp/speconn-kotlin-out/out/
