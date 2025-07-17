#!/usr/bin/env python3

import os
import sys

def main():
    done_dir = "/Users/archmagece/myopen/scripton/sb-keycloak-exts/tasks/done"
    
    # List of files to remove
    files_to_remove = [
        "github-oauth2-enhancement.md",
        "google-oauth2-enhancement.md", 
        "kafka-event-listener-enhancement.md",
        "kafka-event-listener-tasks__DONE_20250717.md",
        "production-deployment-monitoring-20250717-110643.md",
        "gradle-modernization-phase1-20250717-151413.md",
        "testcontainers-integration-tests__DONE_20250717.md"
    ]
    
    removed_files = []
    not_found_files = []
    
    for filename in files_to_remove:
        filepath = os.path.join(done_dir, filename)
        if os.path.exists(filepath):
            try:
                os.remove(filepath)
                removed_files.append(filename)
                print(f"✓ Removed: {filename}")
            except Exception as e:
                print(f"✗ Error removing {filename}: {e}")
        else:
            not_found_files.append(filename)
            print(f"⚠ Not found: {filename}")
    
    print(f"\nSummary:")
    print(f"- Files removed: {len(removed_files)}")
    print(f"- Files not found: {len(not_found_files)}")
    
    print(f"\nRemaining files in {done_dir}:")
    try:
        remaining_files = os.listdir(done_dir)
        if remaining_files:
            for file in sorted(remaining_files):
                if not file.startswith('.'):
                    print(f"  - {file}")
        else:
            print("  (directory is empty)")
    except Exception as e:
        print(f"Error listing directory: {e}")

if __name__ == "__main__":
    main()