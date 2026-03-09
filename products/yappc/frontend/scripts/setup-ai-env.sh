#!/bin/bash

###############################################################################
# Environment Setup Script for AI Features
# 
# This script helps you configure the required environment variables
# for AI features. It will prompt for missing values and validate them.
###############################################################################

set -e

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
RED='\033[0;31m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="${PROJECT_ROOT}/.env"
ENV_EXAMPLE="${PROJECT_ROOT}/.env.example"

echo -e "${BLUE}════════════════════════════════════════════════════════════════${NC}"
echo -e "${BLUE}         AI Features Environment Setup${NC}"
echo -e "${BLUE}════════════════════════════════════════════════════════════════${NC}"
echo ""

# Create .env from .env.example if it doesn't exist
if [ ! -f "$ENV_FILE" ]; then
    echo -e "${YELLOW}[INFO]${NC} .env file not found"
    if [ -f "$ENV_EXAMPLE" ]; then
        echo -e "${YELLOW}[INFO]${NC} Copying from .env.example..."
        cp "$ENV_EXAMPLE" "$ENV_FILE"
        echo -e "${GREEN}[SUCCESS]${NC} Created .env file"
    else
        echo -e "${YELLOW}[INFO]${NC} Creating new .env file..."
        touch "$ENV_FILE"
        echo -e "${GREEN}[SUCCESS]${NC} Created empty .env file"
    fi
    echo ""
fi

# Function to get or set environment variable
set_env_var() {
    local var_name="$1"
    local var_description="$2"
    local var_required="$3"
    local var_example="$4"
    
    # Check if variable already exists
    if grep -q "^${var_name}=" "$ENV_FILE"; then
        local current_value=$(grep "^${var_name}=" "$ENV_FILE" | cut -d '=' -f 2-)
        if [ -n "$current_value" ]; then
            echo -e "${GREEN}✓${NC} ${var_name} is already set"
            return 0
        fi
    fi
    
    # Prompt for value
    echo ""
    echo -e "${BLUE}${var_name}${NC}"
    echo -e "  ${var_description}"
    if [ -n "$var_example" ]; then
        echo -e "  Example: ${YELLOW}${var_example}${NC}"
    fi
    
    if [ "$var_required" = "true" ]; then
        echo -e "  ${RED}(Required)${NC}"
    else
        echo -e "  ${YELLOW}(Optional - press Enter to skip)${NC}"
    fi
    
    read -p "  Enter value: " var_value
    
    # Check if required and empty
    if [ "$var_required" = "true" ] && [ -z "$var_value" ]; then
        echo -e "${RED}[ERROR]${NC} This variable is required!"
        exit 1
    fi
    
    # Add or update in .env
    if [ -n "$var_value" ]; then
        if grep -q "^${var_name}=" "$ENV_FILE"; then
            # Update existing
            if [[ "$OSTYPE" == "darwin"* ]]; then
                sed -i '' "s|^${var_name}=.*|${var_name}=${var_value}|" "$ENV_FILE"
            else
                sed -i "s|^${var_name}=.*|${var_name}=${var_value}|" "$ENV_FILE"
            fi
        else
            # Add new
            echo "${var_name}=${var_value}" >> "$ENV_FILE"
        fi
        echo -e "${GREEN}[SUCCESS]${NC} ${var_name} configured"
    else
        echo -e "${YELLOW}[SKIPPED]${NC} ${var_name} not configured"
    fi
}

# Required variables
echo -e "${BLUE}Required Configuration:${NC}"
echo ""

set_env_var "DATABASE_URL" \
    "PostgreSQL connection string" \
    "true" \
    "postgresql://user:password@localhost:5432/yappc"

# Optional variables
echo ""
echo ""
echo -e "${BLUE}LLM Provider Configuration (Recommended: Ollama for Dev/Test):${NC}"
echo ""

set_env_var "DEFAULT_LLM_PROVIDER" \
    "LLM provider to use: 'local' (Ollama - free), 'openai', or 'anthropic'" \
    "false" \
    "local"

echo ""
echo -e "${YELLOW}Ollama (Local - Free, Recommended for Dev/Test)${NC}"
echo -e "  Install: ${BLUE}curl -fsSL https://ollama.com/install.sh | sh${NC}"
echo -e "  Start: ${BLUE}ollama serve${NC}"
echo -e "  Pull models: ${BLUE}ollama pull llama3.2:3b && ollama pull nomic-embed-text${NC}"
echo ""

set_env_var "OLLAMA_BASE_URL" \
    "Ollama API base URL" \
    "false" \
    "http://localhost:11434"

set_env_var "OLLAMA_CHAT_MODEL" \
    "Ollama chat model to use" \
    "false" \
    "llama3.2:3b"

set_env_var "OLLAMA_EMBEDDING_MODEL" \
    "Ollama embedding model to use" \
    "false" \
    "nomic-embed-text"

echo ""
echo -e "${YELLOW}OpenAI (Cloud - Paid, Use for Production)${NC}"
echo ""

set_env_var "OPENAI_API_KEY" \
    "OpenAI API key for GPT-4 and embeddings (only needed if DEFAULT_LLM_PROVIDER=openai)" \
    "false" \
    "sk-..."

set_env_var "OPENAI_MODEL" \
    "OpenAI chat model to use" \
    "false" \
    "gpt-4-turbo-preview"

set_env_var "OPENAI_EMBEDDING_MODEL" \
    "OpenAI embedding model to use" \
    "false" \
    "text-embedding-3-small"

echo ""
echo -e "${YELLOW}Anthropic (Cloud - Paid, Alternative to OpenAI)${NC}"
echo ""

set_env_var "ANTHROPIC_API_KEY" \
    "Anthropic API key for Claude (only needed if DEFAULT_LLM_PROVIDER=anthropic)" \
    "false" \
    "sk-ant-..."

set_env_var "ANTHROPIC_MODEL" \
    "Anthropic chat model to use" \
    "false" \
    "claude-3-5-sonnet-20241022"

echo ""
echo -e "${YELLOW}General AI Settings${NC}"
echo ""

set_env_var "AI_MAX_TOKENS" \
    "Maximum tokens for AI responses" \
    "false" \
    "2048"

set_env_var "AI_TEMPERATURE" \
    "Temperature for AI responses (0-1)" \
    "false" \
    "0.7"

set_env_var "EMBEDDING_PIPELINE_INTERVAL" \
    "Embedding pipeline interval in ms" \
    "false" \
    "900000"

# Summary
echo ""
echo ""
echo -e "${GREEN}════════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}         Configuration Complete!${NC}"
echo -e "${GREEN}════════════════════════════════════════════════════════════════${NC}"
echo ""
echo -e "Environment file: ${BLUE}${ENV_FILE}${NC}"
echo ""
echo -e "${YELLOW}Next Steps:${NC}"
echo "  1. Review your configuration:"
echo "     ${BLUE}cat .env${NC}"
echo ""
echo "  2. Run deployment script:"
echo "     ${BLUE}./scripts/deploy-ai-features.sh${NC}"
echo ""
echo "  3. Or run migration manually:"
echo "     ${BLUE}cd apps/api && npx prisma migrate dev --name add_ai_features${NC}"
echo ""
