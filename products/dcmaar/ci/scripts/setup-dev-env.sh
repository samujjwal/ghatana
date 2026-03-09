#!/bin/bash
set -e

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${YELLOW}🚀 Setting up DCMAAR development environment...${NC}"

# Check for required tools
check_command() {
    if ! command -v $1 &> /dev/null; then
        echo -e "${YELLOW}⚠️  $1 not found. Installing...${NC}"
        return 1
    else
        echo -e "${GREEN}✅ $1 found${NC}"
        return 0
    fi
}

# Install buf if not found
if ! check_command buf; then
    # Detect OS
    if [[ "$OSTYPE" == "darwin"* ]]; then
        # macOS
        brew install bufbuild/buf/buf
    else
        # Linux
        curl -sSL https://buf.build/install.sh | sh
        if [[ ":$PATH:" != *":$HOME/bin:"* ]]; then
            echo 'export PATH="$HOME/bin:$PATH"' >> ~/.bashrc
            export PATH="$HOME/bin:$PATH"
        fi
    fi
fi

# Install protoc if not found
if ! check_command protoc; then
    if [[ "$OSTYPE" == "darwin"* ]]; then
        brew install protobuf
    else
        sudo apt-get update && sudo apt-get install -y protobuf-compiler
    fi
fi

# Install Go plugins if not found
install_go_plugin() {
    if ! command -v $1 &> /dev/null; then
        echo -e "${YELLOW}⚠️  $1 not found. Installing...${NC}"
        go install $2@latest
    else
        echo -e "${GREEN}✅ $1 found${NC}"
    fi
}

install_go_plugin protoc-gen-go google.golang.org/protobuf/cmd/protoc-gen-go
install_go_plugin protoc-gen-go-grpc google.golang.org/grpc/cmd/protoc-gen-go-grpc

# Install Node.js plugins if package.json exists
if [ -f "package.json" ]; then
    echo -e "${YELLOW}📦 Installing Node.js dependencies...${NC}"
    npm install -g ts-protoc-gen
    npm install
else
    echo -e "${YELLOW}ℹ️  No package.json found, skipping Node.js setup${NC}"
fi

# Set up git hooks
echo -e "${YELLOW}🔧 Setting up git hooks...${NC}"
chmod +x scripts/setup-git-hooks.sh
./scripts/setup-git-hooks.sh

# Generate protos
echo -e "${YELLOW}🔨 Generating protocol buffer code...${NC}"
make proto

# Generate test data
echo -e "${YELLOW}🧪 Generating test data...${NC}"
./scripts/generate-test-data.sh

echo -e "${GREEN}✨ Setup complete! You're ready to develop.${NC}"
echo -e "${YELLOW}Next steps:${NC}"
echo "1. Run 'make proto-verify' to validate your changes"
echo "2. Check out the documentation in docs/ for more information"
echo "3. Run 'make test' to run the test suite"
