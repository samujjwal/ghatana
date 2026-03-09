#!/bin/bash

# Create updates directory if it doesn't exist
mkdir -p updates

# Create a test update file
echo "This is a test update for DCMAR" > updates/update.txt

# Create a zip file with the update
zip -r updates/dcmaer-desktop_x64_v1.0.1.zip updates/update.txt

echo "Test update created at updates/dcmaer-desktop_x64_v1.0.1.zip"
echo "Make sure to run: pnpm dev:all to start all services"