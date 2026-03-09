#!/bin/bash

echo "🔍 Flashit Web API - Setup Verification"
echo "========================================"
echo ""

PASS=0
FAIL=0

# Helper functions
check_pass() {
  echo "✅ $1"
  ((PASS++))
}

check_fail() {
  echo "❌ $1"
  ((FAIL++))
}

check_info() {
  echo "ℹ️  $1"
}

# Check 1: .env file
echo "1. Environment Configuration"
if [ -f ".env" ]; then
  if grep -q "DATABASE_URL" .env; then
    check_pass ".env exists with DATABASE_URL"
  else
    check_fail ".env exists but no DATABASE_URL"
  fi
else
  check_fail ".env file not found"
fi
echo ""

# Check 2: Schema file
echo "2. Prisma Schema"
if [ -f "prisma/schema.prisma" ]; then
  check_pass "prisma/schema.prisma exists"
  
  if grep -q 'provider = "postgresql"' prisma/schema.prisma; then
    check_pass "Provider is PostgreSQL"
  else
    check_fail "Provider is not PostgreSQL"
  fi
  
  if grep -q 'output = "./generated/prisma"' prisma/schema.prisma; then
    check_pass "Output path is isolated: ./generated/prisma"
  else
    check_fail "Output path is not ./generated/prisma"
  fi
else
  check_fail "prisma/schema.prisma not found"
fi
echo ""

# Check 3: Package.json scripts
echo "3. NPM Configuration"
if grep -q '"db:generate"' package.json; then
  check_pass "db:generate script exists"
else
  check_fail "db:generate script not found"
fi

if grep -q '"db:clean"' package.json; then
  check_pass "db:clean script exists"
else
  check_fail "db:clean script not found"
fi

if grep -q '"dev"' package.json; then
  check_pass "dev script exists"
else
  check_fail "dev script not found"
fi
echo ""

# Check 4: Dependencies
echo "4. Dependencies"
if [ -d "node_modules/@prisma/client" ]; then
  check_pass "@prisma/client is installed"
else
  check_fail "@prisma/client not installed"
fi

if [ -d "node_modules/@prisma/adapter-pg" ]; then
  check_pass "@prisma/adapter-pg is installed"
else
  check_fail "@prisma/adapter-pg not installed"
fi

if [ -d "node_modules/pg" ]; then
  check_pass "pg driver is installed"
else
  check_fail "pg driver not installed"
fi
echo ""

# Check 5: Application code
echo "5. Source Code"
if [ -f "src/lib/prisma.ts" ]; then
  check_pass "src/lib/prisma.ts exists"
  
  if grep -q '../../generated/prisma/index.js' src/lib/prisma.ts; then
    check_pass "Import path is correct: ../../generated/prisma/index.js"
  else
    check_fail "Import path is incorrect"
  fi
  
  if grep -q '@prisma/adapter-pg' src/lib/prisma.ts; then
    check_pass "Uses @prisma/adapter-pg adapter"
  else
    check_fail "Does not use @prisma/adapter-pg adapter"
  fi
else
  check_fail "src/lib/prisma.ts not found"
fi
echo ""

# Check 6: Generated files
echo "6. Generated Artifacts"
if [ -d "generated/prisma" ]; then
  check_pass "generated/prisma/ folder exists"
  
  if [ -f "generated/prisma/index.js" ]; then
    check_pass "generated/prisma/index.js exists"
  else
    check_fail "generated/prisma/index.js not found"
  fi
  
  if [ -f "generated/prisma/index.d.ts" ]; then
    check_pass "generated/prisma/index.d.ts exists"
  else
    check_fail "generated/prisma/index.d.ts not found"
  fi
else
  check_fail "generated/prisma/ folder not found"
  check_info "Run: npm run db:generate"
fi
echo ""

# Check 7: Git configuration
echo "7. Git Configuration"
if grep -q "generated/" .gitignore; then
  check_pass "generated/ is in .gitignore"
else
  check_fail "generated/ is not in .gitignore"
fi
echo ""

# Summary
echo "========================================"
echo "Summary:"
echo "  ✅ Passed: $PASS"
echo "  ❌ Failed: $FAIL"
echo ""

if [ $FAIL -eq 0 ]; then
  echo "🎉 All checks passed!"
  echo ""
  echo "Ready to start with:"
  echo "  npm run dev"
  echo ""
  echo "Or generate first with:"
  echo "  npm run db:generate"
  exit 0
else
  echo "⚠️  Some checks failed. See above for details."
  echo ""
  echo "Try:"
  echo "  npm install                 # Install dependencies"
  echo "  npm run db:generate         # Generate Prisma client"
  echo "  npm run dev                 # Start development server"
  exit 1
fi
