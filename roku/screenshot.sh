#!/bin/sh
# Saves what the Roku is showing to out/screen.<ext> (the dev channel must be running).
# Uses the same Keychain entry as deploy.sh.
set -e
cd "$(dirname "$0")"
ROKU_IP="${ROKU_IP:-192.168.2.33}"
PASS="$(security find-generic-password -s lumen-roku -a rokudev -w)"
PAGE="$(curl -s --digest -u "rokudev:$PASS" -F "mysubmit=Screenshot" -F "archive=" -F "passwd=" \
  "http://$ROKU_IP/plugin_inspect")"
SRC="$(printf '%s' "$PAGE" | grep -o 'pkgs/dev\.[a-z]*?time=[0-9]*' | head -1)"
[ -n "$SRC" ] || { echo "No screenshot in the response"; exit 1; }
EXT="$(printf '%s' "$SRC" | sed 's/.*dev\.\([a-z]*\)?.*/\1/')"
mkdir -p out
curl -s --digest -u "rokudev:$PASS" -o "out/screen.$EXT" "http://$ROKU_IP/$SRC"
echo "$(pwd)/out/screen.$EXT"
