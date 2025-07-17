#!/bin/bash

# Navigate to the tasks/done directory
cd /Users/archmagece/myopen/scripton/sb-keycloak-exts/tasks/done

# Remove the specified files
rm -f github-oauth2-enhancement.md
rm -f google-oauth2-enhancement.md
rm -f kafka-event-listener-enhancement.md
rm -f kafka-event-listener-tasks__DONE_20250717.md
rm -f production-deployment-monitoring-20250717-110643.md
rm -f gradle-modernization-phase1-20250717-151413.md
rm -f testcontainers-integration-tests__DONE_20250717.md

echo "Cleanup completed!"
echo "Remaining files:"
ls -la