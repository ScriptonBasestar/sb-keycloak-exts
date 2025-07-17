#!/bin/bash

# Script to remove processed files from /tasks/done/ directory

DONE_DIR="/Users/archmagece/myopen/scripton/sb-keycloak-exts/tasks/done"

# Remove the specified files
rm -f "$DONE_DIR/github-oauth2-enhancement.md"
rm -f "$DONE_DIR/google-oauth2-enhancement.md"
rm -f "$DONE_DIR/kafka-event-listener-enhancement.md"
rm -f "$DONE_DIR/kafka-event-listener-tasks__DONE_20250717.md"
rm -f "$DONE_DIR/production-deployment-monitoring-20250717-110643.md"
rm -f "$DONE_DIR/gradle-modernization-phase1-20250717-151413.md"
rm -f "$DONE_DIR/testcontainers-integration-tests__DONE_20250717.md"

echo "Files removed successfully!"
echo "Remaining files in $DONE_DIR:"
ls -la "$DONE_DIR"