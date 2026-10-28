#!/bin/sh
# Builds Lumen and installs it on the Roku. The developer password comes from the macOS Keychain
# (service "lumen-roku"), saved once with:
#   security add-generic-password -s lumen-roku -a rokudev -w
set -e
cd "$(dirname "$0")"
ROKU_IP="${ROKU_IP:-192.168.2.33}"
PASS="$(security find-generic-password -s lumen-roku -a rokudev -w)"
sh build.sh
curl -s --digest -u "rokudev:$PASS" -F "mysubmit=Install" -F "archive=@out/lumen.zip" \
  "http://$ROKU_IP/plugin_install" | grep -o -E 'Install Success|Identical to previous version|Install Failure[^<"]*' | head -1
