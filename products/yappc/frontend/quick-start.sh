#!/bin/bash

# Collaborative Polyglot IDE - Quick Start Script
# This script helps you quickly set up and test the IDE

echo "🚀 Collaborative Polyglot IDE - Quick Start"
echo "=========================================="

# Check if we're in the right directory
if [ ! -f "package.json" ] || [ ! -d "libs" ]; then
    echo "❌ Error: Please run this script from the frontend directory"
    echo "   Expected path: products/yappc/frontend"
    exit 1
fi

# Check Node.js version
NODE_VERSION=$(node --version | cut -d'v' -f2 | cut -d'.' -f1)
if [ "$NODE_VERSION" -lt 18 ]; then
    echo "❌ Error: Node.js 18+ is required. Current version: $(node --version)"
    exit 1
fi

# Check pnpm
if ! command -v pnpm &> /dev/null; then
    echo "❌ Error: pnpm is not installed"
    echo "   Install with: npm install -g pnpm"
    exit 1
fi

echo "✅ Prerequisites check passed"

# Install dependencies if needed
if [ ! -d "node_modules" ]; then
    echo "📦 Installing dependencies..."
    pnpm install
fi

# Build packages if needed (skip for now due to config issues)
# if [ ! -d "libs/code-editor/dist" ]; then
#     echo "🔨 Building packages..."
#     pnpm build:web
# fi

echo ""
echo "🎯 Choose what you want to do:"
echo "1. Start IDE development server"
echo "2. Run tests"
echo "3. Build for production"
echo "4. Clean and rebuild"
echo "5. Check setup status"
echo ""

read -p "Enter your choice (1-5): " choice

case $choice in
    1)
        echo "🌟 Starting IDE development server..."
        echo "   Open http://localhost:3000 when ready"
        echo "   Press Ctrl+C to stop"
        echo ""
        pnpm dev:web
        ;;
    2)
        echo "🧪 Running tests..."
        pnpm test
        ;;
    3)
        echo "🏗️ Building for production..."
        pnpm build:web
        echo "✅ Build complete! Check dist/ folder"
        ;;
    4)
        echo "🧹 Cleaning and rebuilding..."
        pnpm clean:generated
        pnpm install
        pnpm build:web
        echo "✅ Clean rebuild complete!"
        ;;
    5)
        echo "📊 Checking setup status..."
        echo ""
        
        # Check packages
        echo "📦 Packages:"
        for lib in libs/*/; do
            if [ -d "$lib" ]; then
                lib_name=$(basename "$lib")
                if [ -f "$lib/package.json" ]; then
                    echo "   ✅ $lib_name"
                else
                    echo "   ❌ $lib_name (missing package.json)"
                fi
            fi
        done
        
        echo ""
        echo "🔗 Dependencies:"
        if [ -d "node_modules" ]; then
            echo "   ✅ node_modules installed"
        else
            echo "   ❌ node_modules missing"
        fi
        
        echo ""
        echo "🏗️ Build status:"
        if [ -d "libs/code-editor/dist" ]; then
            echo "   ✅ Packages built"
        else
            echo "   ❌ Packages not built"
        fi
        
        echo ""
        echo "🌐 Network:"
        if command -v curl &> /dev/null; then
            if curl -s http://localhost:3000 > /dev/null 2>&1; then
                echo "   ✅ IDE running at http://localhost:3000"
            else
                echo "   ❌ IDE not running"
            fi
        fi
        ;;
    *)
        echo "❌ Invalid choice"
        exit 1
        ;;
esac
