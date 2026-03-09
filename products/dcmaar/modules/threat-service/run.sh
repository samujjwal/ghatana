#!/bin/bash

# Change to the script's directory
cd "$(dirname "$0")"

echo "Installing dependencies..."
go mod tidy

echo "Building server binary..."
go build -o bin/server ./cmd/server

# Check if build was successful
if [ $? -ne 0 ]; then
    echo "Failed to build server"
    exit 1
fi

# Start the server
echo "Starting server..."
./bin/server