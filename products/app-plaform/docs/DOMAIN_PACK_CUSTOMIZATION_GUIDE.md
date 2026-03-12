# Domain Pack Customization Guide

**Version**: 1.0.0  
**Date**: 2026-03-12  
**Purpose**: Comprehensive guide for customizing domain packs with pack-specific logic

---

## Overview

This guide explains how to customize domain packs by adding pack-specific logic, business rules, workflows, and integrations. It provides practical examples for banking, healthcare, and other domains.

---

## 1. Domain Pack Customization Layers

### 1.1 Customization Points

Domain packs can be customized at the following layers:

```
┌─────────────────────────────────────┐
│  Configuration Layer                 │
│  - Domain-specific settings         │
│  - Jurisdiction overrides          │
│  - Feature flags                    │
├─────────────────────────────────────┤
│  Business Rules Layer               │
│  - OPA/Rego rules                  │
│  - Validation logic                 │
│  - Calculation engines             │
├─────────────────────────────────────┤
│  Workflow Layer                     │
│  - Business processes              │
│  - Approval workflows              │
│  - State machines                  │
├─────────────────────────────────────┤
│  Integration Layer                  │
│  - External adapters               │
│  - Protocol handlers              │
│  - API mappings                    │
├─────────────────────────────────────┤
│  Data Model Layer                   │
│  - Entity schemas                  │
│  - Event schemas                   │
│  - API schemas                     │
├─────────────────────────────────────┤
│  UI Layer                          │
│  - Dashboards                      │
│  - Forms                           │
│  - Reports                         │
└─────────────────────────────────────┘
```

---

## 2. Banking Domain Pack Customization

### 2.1 Enhanced Domain Manifest

```yaml
# domain-packs/banking/pack.yaml
domainPack:
  id: "banking-v1.0"
  name: "Banking Domain"
  version: "1.0.0"
  description: "Advanced banking domain with retail, corporate, and investment banking"
  
  domainType: "financial-services"
  industry: "banking"
  subdomains:
    - "retail-banking"
    - "corporate-banking"
    - "investment-banking"
    - "payments"
    - "treasury"
    - "wealth-management"
  
  # Custom capabilities for banking
  capabilities:
    - "ACCOUNT_MANAGEMENT"
    - "PAYMENT_PROCESSING"
    - "LOAN_ORIGINATION"
    - "CREDIT_SCORING"
    - "FRAUD_DETECTION"
    - "AML_COMPLIANCE"
    - "TREASURY_MANAGEMENT"
    - "WEALTH_MANAGEMENT"
    - "TRADING_SUPPORT"
  
  # Custom kernel requirements
  requiredKernels:
    - "K-01"  # IAM - for customer authentication
    - "K-02"  # Config - for banking product configuration
    - "K-03"  # Rules - for credit scoring and fraud detection
    - "K-04"  # Plugin - for banking-specific plugins
    - "K-05"  # Events - for transaction events
    - "K-06"  # Observability - for transaction monitoring
    - "K-07"  # Audit - for regulatory compliance
    - "K-08"  # Data Gov - for customer data protection
    - "K-09"  # AI Gov - for ML credit scoring
    - "K-15"  # Calendar - for settlement dates
    - "K-16"  # Ledger - for account management
  
  # Custom configuration
  configuration:
    - file: "config/banking-products.json"
    - file: "config/credit-scoring-models.json"
    - file: "config/fraud-detection-rules.json"
    - file: "config/jurisdiction-overrides.json"
```

### 2.2 Custom Business Rules

#### Polyglot Business Rules Architecture

Domain packs support multiple business rule languages, allowing you to choose the most appropriate language for your specific use case:

```
┌─────────────────────────────────────────┐
│           Business Rules Layer           │
├─────────────────────────────────────────┤
│  OPA/Rego      │  JavaScript/TypeScript  │
│  • Simple rules │  • Complex calculations │
│  • Policy auth  │  • ML integration       │
│  • Quick dev    │  • Rich libraries      │
├─────────────────────────────────────────┤
│  Python         │  Java/Kotlin           │
│  • Data science │  • Enterprise rules   │
│  • ML models    │  • Type safety         │
│  • Analytics    │  • Spring integration  │
├─────────────────────────────────────────┤
│  SQL/PLpgSQL    │  Custom DSL            │
│  • Database rules│  • Domain-specific     │
│  • Set operations│  • Business language   │
│  • Performance  │  • Non-technical users │
└─────────────────────────────────────────┘
```

#### Option 1: Enhanced OPA/Rego (for Simple Rules)

**Credit Scoring Rules** (`rules/credit-scoring.rego`)

```rego
package banking.credit.scoring

# Default deny policy
default allow = false

# Credit score calculation
credit_score applicant {
    score := calculate_credit_score(applicant)
    score >= min_credit_score[applicant.product_type]
}

# Calculate credit score based on multiple factors
calculate_credit_score applicant {
    base_score := 300
    income_score := calculate_income_score(applicant.income)
    age_score := calculate_age_score(applicant.age)
    history_score := calculate_history_score(applicant.credit_history)
    debt_score := calculate_debt_score(applicant.debt_to_income)
    
    score := base_score + income_score + age_score + history_score - debt_score
}

# Income-based scoring
calculate_income_score income {
    income < 30000    -> 50
    income < 60000    -> 100
    income < 100000   -> 150
    income >= 100000  -> 200
}

# Minimum credit scores by product
min_credit_score = {
    "personal_loan": 600,
    "mortgage": 650,
    "credit_card": 580,
    "auto_loan": 620,
    "business_loan": 700
}

# Allow credit if score meets minimum
allow {
    credit_score(input.applicant)
}
```

#### Option 2: JavaScript/TypeScript (for Complex Logic)

**Advanced Credit Scoring Engine** (`rules/credit-scoring.ts`)

```typescript
import { MachineLearningModel } from '../ml/credit-model';
import { FinancialCalculator } from '../utils/financial-calculator';
import { RiskAssessment } from '../models/risk-assessment';

export class CreditScoringEngine {
    private mlModel: MachineLearningModel;
    private calculator: FinancialCalculator;

    constructor() {
        this.mlModel = new MachineLearningModel('credit_scoring_v2');
        this.calculator = new FinancialCalculator();
    }

    async evaluateCreditApplication(application: CreditApplication): Promise<CreditDecision> {
        // 1. Traditional credit scoring
        const traditionalScore = this.calculateTraditionalScore(application);
        
        // 2. ML-based risk assessment
        const mlRiskScore = await this.mlModel.predict(application);
        
        // 3. Cash flow analysis
        const cashFlowScore = this.analyzeCashFlow(application);
        
        // 4. Behavioral analysis
        const behavioralScore = this.analyzeBehavior(application.customer_id);
        
        // 5. Combine scores with weights
        const finalScore = this.combineScores({
            traditional: traditionalScore,
            ml_risk: mlRiskScore,
            cash_flow: cashFlowScore,
            behavioral: behavioralScore
        });

        // 6. Apply business rules
        const decision = this.applyBusinessRules(finalScore, application);
        
        // 7. Generate explanation
        decision.explanation = this.generateExplanation(decision, application);
        
        return decision;
    }

    private calculateTraditionalScore(application: CreditApplication): number {
        let score = 300; // Base score

        // Income scoring with non-linear curves
        score += this.calculateIncomeScore(application.income);
        
        // Age scoring with optimal range
        score += this.calculateAgeScore(application.age);
        
        // Credit history with depth analysis
        score += this.calculateHistoryScore(application.credit_history);
        
        // Debt-to-income with stress testing
        score += this.calculateDebtScore(application.debt_to_income);
        
        // Employment stability
        score += this.calculateEmploymentScore(application.employment);
        
        // Asset analysis
        score += this.calculateAssetScore(application.assets);

        return Math.min(850, Math.max(300, score));
    }

    private calculateIncomeScore(income: number): number {
        // Non-linear income scoring with diminishing returns
        if (income < 25000) return 30;
        if (income < 50000) return 80;
        if (income < 75000) return 140;
        if (income < 100000) return 180;
        if (income < 150000) return 210;
        if (income < 250000) return 230;
        return 240; // Max income score
    }

    private async analyzeCashFlow(application: CreditApplication): Promise<number> {
        // Get 6 months of transaction data
        const transactions = await this.getTransactionHistory(application.customer_id, 180);
        
        // Calculate cash flow metrics
        const avgMonthlyIncome = this.calculateAverageIncome(transactions);
        const avgMonthlyExpenses = this.calculateAverageExpenses(transactions);
        const volatility = this.calculateVolatility(transactions);
        const savingsRate = (avgMonthlyIncome - avgMonthlyExpenses) / avgMonthlyIncome;
        
        // Cash flow scoring
        let score = 0;
        score += savingsRate > 0.2 ? 50 : savingsRate > 0.1 ? 30 : savingsRate > 0 ? 10 : -20;
        score += volatility < 0.3 ? 30 : volatility < 0.5 ? 15 : 0;
        score += avgMonthlyIncome > application.expenses * 1.5 ? 20 : 0;
        
        return Math.max(0, Math.min(100, score));
    }

    private combineScores(scores: ScoreComponents): number {
        const weights = {
            traditional: 0.4,
            ml_risk: 0.3,
            cash_flow: 0.2,
            behavioral: 0.1
        };

        return Object.entries(scores).reduce((total, [key, value]) => {
            return total + (value * weights[key]);
        }, 0);
    }

    private applyBusinessRules(score: number, application: CreditApplication): CreditDecision {
        const productRules = this.getProductRules(application.product_type);
        
        if (score < productRules.minScore) {
            return {
                approved: false,
                score,
                reason: `Score ${score} below minimum ${productRules.minScore}`,
                conditions: []
            };
        }

        if (application.debt_to_income > productRules.maxDTI) {
            return {
                approved: false,
                score,
                reason: `DTI ${application.debt_to_income} exceeds maximum ${productRules.maxDTI}`,
                conditions: []
            };
        }

        // Conditional approval
        const conditions: string[] = [];
        if (score < productRules.preferredScore) {
            conditions.push('Additional collateral required');
            conditions.push('Higher interest rate applied');
        }

        return {
            approved: true,
            score,
            reason: 'Application meets all criteria',
            conditions,
            interestRate: this.calculateInterestRate(score, application.product_type),
            maxAmount: this.calculateMaxAmount(score, application.income)
        };
    }
}

// Interfaces
interface CreditApplication {
    customer_id: string;
    product_type: string;
    income: number;
    age: number;
    credit_history: CreditHistory;
    debt_to_income: number;
    employment: EmploymentInfo;
    assets: AssetInfo;
    expenses: number;
}

interface CreditDecision {
    approved: boolean;
    score: number;
    reason: string;
    conditions: string[];
    interestRate?: number;
    maxAmount?: number;
    explanation?: string;
}

interface ScoreComponents {
    traditional: number;
    ml_risk: number;
    cash_flow: number;
    behavioral: number;
}
```

#### Option 3: Python (for Data Science & ML Integration)

**Fraud Detection with ML** (`rules/fraud-detection.py`)

```python
import numpy as np
import pandas as pd
from sklearn.ensemble import IsolationForest
from sklearn.preprocessing import StandardScaler
from typing import Dict, List, Tuple
import tensorflow as tf
from datetime import datetime, timedelta

class FraudDetectionEngine:
    def __init__(self):
        self.isolation_forest = IsolationForest(contamination=0.1, random_state=42)
        self.scaler = StandardScaler()
        self.neural_network = self._load_neural_network()
        self.feature_extractor = FeatureExtractor()
        
    def analyze_transaction(self, transaction: Dict) -> FraudAnalysis:
        """Comprehensive fraud analysis using multiple techniques"""
        
        # 1. Extract features
        features = self.feature_extractor.extract_features(transaction)
        
        # 2. Anomaly detection (unsupervised)
        anomaly_score = self._detect_anomaly(features)
        
        # 3. Pattern recognition (supervised ML)
        pattern_score = self._detect_patterns(features)
        
        # 4. Behavioral analysis
        behavioral_score = self._analyze_behavior(transaction)
        
        # 5. Network analysis
        network_score = self._analyze_network(transaction)
        
        # 6. Temporal analysis
        temporal_score = self._analyze_temporal_patterns(transaction)
        
        # 7. Combine scores
        combined_score = self._combine_scores({
            'anomaly': anomaly_score,
            'pattern': pattern_score,
            'behavioral': behavioral_score,
            'network': network_score,
            'temporal': temporal_score
        })
        
        # 8. Generate decision
        decision = self._make_fraud_decision(combined_score, transaction)
        
        return decision
    
    def _detect_anomaly(self, features: np.ndarray) -> float:
        """Detect anomalies using isolation forest"""
        try:
            features_scaled = self.scaler.transform(features.reshape(1, -1))
            anomaly_score = self.isolation_forest.decision_function(features_scaled)[0]
            # Convert to 0-1 scale (higher = more anomalous)
            return 1 - (anomaly_score + 1) / 2
        except:
            return 0.0
    
    def _detect_patterns(self, features: np.ndarray) -> float:
        """Detect known fraud patterns using neural network"""
        try:
            features_scaled = self.scaler.transform(features.reshape(1, -1))
            prediction = self.neural_network.predict(features_scaled)[0][0]
            return float(prediction)
        except:
            return 0.0
    
    def _analyze_behavior(self, transaction: Dict) -> float:
        """Analyze customer behavioral patterns"""
        customer_id = transaction['customer_id']
        
        # Get customer's transaction history
        history = self._get_customer_history(customer_id, days=90)
        
        if len(history) < 5:  # Not enough history
            return 0.3
        
        # Calculate behavioral metrics
        avg_amount = np.mean([t['amount'] for t in history])
        std_amount = np.std([t['amount'] for t in history])
        current_amount = transaction['amount']
        
        # Z-score for amount
        amount_zscore = abs(current_amount - avg_amount) / (std_amount + 1e-6)
        
        # Time-based patterns
        transaction_time = datetime.fromisoformat(transaction['timestamp'])
        hour = transaction_time.hour
        day_of_week = transaction_time.weekday()
        
        # Unusual time detection
        unusual_time_score = 0.0
        if hour < 6 or hour > 22:
            unusual_time_score += 0.3
        if day_of_week >= 5:  # Weekend
            unusual_time_score += 0.2
        
        # Location analysis
        location_score = self._analyze_location_patterns(transaction, history)
        
        # Frequency analysis
        frequency_score = self._analyze_frequency_patterns(transaction, history)
        
        return min(1.0, (amount_zscore / 3) + unusual_time_score + location_score + frequency_score)
    
    def _analyze_network(self, transaction: Dict) -> float:
        """Analyze transaction network for fraud rings"""
        beneficiary = transaction.get('beneficiary_account')
        customer_id = transaction['customer_id']
        
        # Check if beneficiary is in known fraud network
        fraud_network_score = self._check_fraud_network(beneficiary)
        
        # Check for circular transactions
        circular_score = self._check_circular_transactions(customer_id, beneficiary)
        
        # Check for rapid money movement
        rapid_movement_score = self._check_rapid_movement(beneficiary)
        
        return min(1.0, fraud_network_score + circular_score + rapid_movement_score)
    
    def _analyze_temporal_patterns(self, transaction: Dict) -> float:
        """Analyze temporal patterns of transactions"""
        customer_id = transaction['customer_id']
        current_time = datetime.fromisoformat(transaction['timestamp'])
        
        # Get recent transactions
        recent_txns = self._get_customer_history(customer_id, hours=24)
        
        # Check for velocity attacks
        velocity_score = self._check_velocity(recent_txns, current_time)
        
        # Check for burst patterns
        burst_score = self._check_burst_patterns(recent_txns, current_time)
        
        return min(1.0, velocity_score + burst_score)
    
    def _combine_scores(self, scores: Dict[str, float]) -> float:
        """Combine different fraud detection scores"""
        weights = {
            'anomaly': 0.2,
            'pattern': 0.3,
            'behavioral': 0.25,
            'network': 0.15,
            'temporal': 0.1
        }
        
        combined = sum(score * weights[key] for key, score in scores.items())
        return min(1.0, combined)
    
    def _make_fraud_decision(self, score: float, transaction: Dict) -> FraudAnalysis:
        """Make final fraud decision"""
        
        if score > 0.8:
            action = 'BLOCK'
            reason = 'High fraud risk detected'
        elif score > 0.6:
            action = 'REVIEW'
            reason = 'Medium fraud risk - manual review required'
        elif score > 0.4:
            action = 'MONITOR'
            reason = 'Low to medium risk - enhanced monitoring'
        else:
            action = 'ALLOW'
            reason = 'Normal transaction'
        
        return FraudAnalysis(
            fraud_score=score,
            action=action,
            reason=reason,
            risk_factors=self._identify_risk_factors(score, transaction),
            recommended_actions=self._get_recommendations(action, score)
        )

class FeatureExtractor:
    """Extract features for fraud detection"""
    
    def extract_features(self, transaction: Dict) -> np.ndarray:
        """Extract numerical features from transaction"""
        features = [
            transaction['amount'],
            self._encode_time(transaction['timestamp']),
            self._encode_location(transaction['location']),
            self._encode_merchant(transaction['merchant_type']),
            self._encode_channel(transaction['channel']),
            transaction.get('customer_tenure', 0),
            transaction.get('customer_age', 0)
        ]
        
        return np.array(features, dtype=float)
    
    def _encode_time(self, timestamp: str) -> float:
        """Encode timestamp as cyclical features"""
        dt = datetime.fromisoformat(timestamp)
        hour_sin = np.sin(2 * np.pi * dt.hour / 24)
        hour_cos = np.cos(2 * np.pi * dt.hour / 24)
        dow_sin = np.sin(2 * np.pi * dt.weekday() / 7)
        dow_cos = np.cos(2 * np.pi * dt.weekday() / 7)
        return hour_sin + hour_cos + dow_sin + dow_cos
    
    def _encode_location(self, location: str) -> float:
        """Encode location (simplified)"""
        location_hash = hash(location) % 1000
        return location_hash / 1000.0
    
    def _encode_merchant(self, merchant_type: str) -> float:
        """Encode merchant type risk score"""
        risk_scores = {
            'electronics': 0.7,
            'jewelry': 0.8,
            'travel': 0.6,
            'restaurant': 0.3,
            'grocery': 0.2,
            'gas': 0.4
        }
        return risk_scores.get(merchant_type, 0.5)

# Data classes
@dataclass
class FraudAnalysis:
    fraud_score: float
    action: str
    reason: str
    risk_factors: List[str]
    recommended_actions: List[str]
```

#### Option 4: SQL/PLpgSQL (for Database-Centric Rules)

**Database-Level Credit Rules** (`rules/credit-rules.sql`)

```sql
-- Credit scoring function in PostgreSQL
CREATE OR REPLACE FUNCTION calculate_credit_score(
    p_customer_id UUID,
    p_product_type TEXT,
    p_loan_amount NUMERIC
) RETURNS TABLE (
    score INTEGER,
    decision TEXT,
    max_amount NUMERIC,
    interest_rate NUMERIC,
    explanation TEXT[]
) LANGUAGE plpgsql AS $$
DECLARE
    v_income NUMERIC;
    v_age INTEGER;
    v_credit_history JSONB;
    v_debt_to_income NUMERIC;
    v_employment_months INTEGER;
    v_base_score INTEGER := 300;
    v_income_score INTEGER;
    v_age_score INTEGER;
    v_history_score INTEGER;
    v_debt_score INTEGER;
    v_employment_score INTEGER;
    v_final_score INTEGER;
    v_min_score INTEGER;
    v_max_dti NUMERIC;
BEGIN
    -- Get customer data
    SELECT 
        c.annual_income,
        EXTRACT(YEAR FROM AGE(c.date_of_birth))::INTEGER,
        c.credit_history,
        c.total_monthly_debt,
        c.employment_months
    INTO v_income, v_age, v_credit_history, v_debt_to_income, v_employment_months
    FROM customers c
    WHERE c.id = p_customer_id;
    
    -- Calculate income score with non-linear scaling
    v_income_score := CASE 
        WHEN v_income < 25000 THEN 30
        WHEN v_income < 50000 THEN 80
        WHEN v_income < 75000 THEN 140
        WHEN v_income < 100000 THEN 180
        WHEN v_income < 150000 THEN 210
        WHEN v_income < 250000 THEN 230
        ELSE 240
    END;
    
    -- Calculate age score
    v_age_score := CASE 
        WHEN v_age < 25 THEN 20
        WHEN v_age < 35 THEN 50
        WHEN v_age < 50 THEN 80
        ELSE 100
    END;
    
    -- Calculate credit history score
    v_history_score := CASE 
        WHEN v_credit_history->>'rating' = 'excellent' THEN 150
        WHEN v_credit_history->>'rating' = 'good' THEN 100
        WHEN v_credit_history->>'rating' = 'fair' THEN 50
        ELSE 0
    END;
    
    -- Calculate debt score
    v_debt_to_income := COALESCE(v_debt_to_income, 0) / NULLIF(v_income / 12, 0);
    v_debt_score := CASE 
        WHEN v_debt_to_income > 0.5 THEN -100
        WHEN v_debt_to_income > 0.4 THEN -50
        WHEN v_debt_to_income > 0.3 THEN -20
        ELSE 0
    END;
    
    -- Calculate employment score
    v_employment_score := CASE 
        WHEN v_employment_months < 6 THEN 0
        WHEN v_employment_months < 12 THEN 20
        WHEN v_employment_months < 24 THEN 50
        WHEN v_employment_months < 60 THEN 80
        ELSE 100
    END;
    
    -- Calculate final score
    v_final_score := v_base_score + v_income_score + v_age_score + 
                   v_history_score + v_debt_score + v_employment_score;
    
    -- Get product-specific rules
    SELECT min_score, max_dti
    INTO v_min_score, v_max_dti
    FROM product_rules
    WHERE product_type = p_product_type;
    
    -- Generate decision
    IF v_final_score < v_min_score THEN
        RETURN QUERY SELECT 
            v_final_score,
            'REJECTED',
            0::NUMERIC,
            0::NUMERIC,
            ARRAY['Credit score below minimum', 
                  format('Score: %s, Required: %s', v_final_score, v_min_score)];
    ELSIF v_debt_to_income > v_max_dti THEN
        RETURN QUERY SELECT 
            v_final_score,
            'REJECTED',
            0::NUMERIC,
            0::NUMERIC,
            ARRAY['Debt-to-income ratio too high',
                  format('DTI: %s%%, Max: %s%%', v_debt_to_income * 100, v_max_dti * 100)];
    ELSE
        -- Calculate approved amount and interest rate
        DECLARE
            v_max_amount NUMERIC;
            v_rate NUMERIC;
        BEGIN
            v_max_amount := (v_income / 12) * 0.4 * 60; -- 40% of monthly income for 5 years
            v_rate := CASE 
                WHEN v_final_score >= 750 THEN 0.059
                WHEN v_final_score >= 700 THEN 0.069
                WHEN v_final_score >= 650 THEN 0.079
                WHEN v_final_score >= 600 THEN 0.089
                ELSE 0.099
            END;
            
            RETURN QUERY SELECT 
                v_final_score,
                'APPROVED',
                LEAST(p_loan_amount, v_max_amount),
                v_rate,
                ARRAY['Application approved',
                      format('Interest rate: %s%%', v_rate * 100)];
        END;
    END IF;
    
    RETURN;
END;
$$;

-- Usage example
SELECT * FROM calculate_credit_score(
    'customer-uuid',
    'personal_loan',
    50000
);
```

#### Option 5: Custom DSL (for Business Users)

**Business-Friendly Rule Language** (`rules/business-rules.dsl`)

```
# Credit scoring rules in business-friendly DSL

RULE "Personal Loan Eligibility"
WHEN
    customer.age >= 25
    AND customer.income >= 30000
    AND customer.credit_history.rating in ["good", "excellent"]
    AND customer.debt_to_income <= 0.43
THEN
    APPROVE loan
    WITH interest_rate = BASE_RATE - (customer.credit_score - 650) * 0.001
    AND max_amount = customer.income * 4
    AND conditions = ["Standard documentation required"]
    
RULE "High Risk Credit Card"
WHEN
    customer.credit_score < 600
    OR customer.debt_to_income > 0.4
    OR customer.employment_months < 12
THEN
    REFER_TO_UNDERWRITER
    WITH reason = "High risk profile"
    AND required_documents = ["Tax returns", "Bank statements", "Employment verification"]
    
RULE "Premium Banking Benefits"
WHEN
    customer.total_assets > 1000000
    AND customer.income > 200000
    AND customer.relationship_years > 5
THEN
    ASSIGN_PREMIUM_STATUS
    WITH benefits = ["Dedicated relationship manager", "Priority service", "Reduced fees"]
    AND credit_limit_multiplier = 2.0
    
RULE "Fraud Detection - Large Amount"
WHEN
    transaction.amount > customer.avg_transaction_amount * 5
    AND transaction.location NOT IN customer.common_locations
    AND transaction.time BETWEEN 22:00 AND 06:00
THEN
    FLAG_TRANSACTION
    WITH risk_level = "HIGH"
    AND action = "BLOCK_AND_REVIEW"
    AND notification_sent_to = ["fraud_team", "customer"]
    
RULE "Fraud Detection - High Frequency"
WHEN
    COUNT(transactions IN LAST 1 HOUR FOR customer) > 10
    OR SUM(transactions.amount IN LAST 24 HOURS FOR customer) > 50000
THEN
    FLAG_TRANSACTION
    WITH risk_level = "MEDIUM"
    AND action = "REQUIRE_ADDITIONAL_VERIFICATION"
    AND cooling_off_period = "30 minutes"
```

### 2.2.1 Rule Engine Configuration

**Polyglot Rule Engine Setup** (`config/rule-engine.json`)

```json
{
  "rule_engine": {
    "type": "polyglot",
    "languages": {
      "rego": {
        "engine": "opa",
        "use_case": "simple_authorization",
        "performance": "high",
        "learning_curve": "low",
        "kernel_integration": "k03-rego-runtime"
      },
      "typescript": {
        "engine": "node",
        "use_case": "complex_calculations",
        "performance": "medium",
        "learning_curve": "medium",
        "kernel_integration": "k03-js-runtime"
      },
      "python": {
        "engine": "python",
        "use_case": "ml_integration",
        "performance": "low",
        "learning_curve": "medium",
        "kernel_integration": "k03-python-runtime"
      },
      "sql": {
        "engine": "postgresql",
        "use_case": "database_rules",
        "performance": "high",
        "learning_curve": "medium",
        "kernel_integration": "k03-sql-runtime"
      },
      "dsl": {
        "engine": "custom",
        "use_case": "business_user_rules",
        "performance": "medium",
        "learning_curve": "low",
        "kernel_integration": "k03-dsl-runtime"
      },
      "java": {
        "engine": "jvm",
        "use_case": "enterprise_rules",
        "performance": "high",
        "learning_curve": "high",
        "kernel_integration": "k03-java-runtime"
      },
      "kotlin": {
        "engine": "jvm",
        "use_case": "enterprise_rules",
        "performance": "high",
        "learning_curve": "medium",
        "kernel_integration": "k03-kotlin-runtime"
      }
    },
    "routing": {
      "credit_scoring": "typescript",
      "fraud_detection": "python", 
      "authorization": "rego",
      "data_validation": "sql",
      "business_rules": "dsl",
      "enterprise_logic": "java",
      "mobile_rules": "kotlin"
    },
    "execution": {
      "timeout": 5000,
      "caching": true,
      "parallel_execution": true,
      "fallback_language": "rego",
      "kernel_module": "K-03",
      "security_isolation": true,
      "resource_monitoring": true
    },
    "kernel_integration": {
      "module_id": "K-03",
      "api_version": "v1.0",
      "endpoints": {
        "execute": "/api/v1/rules/execute",
        "validate": "/api/v1/rules/validate",
        "metrics": "/api/v1/rules/metrics",
        "health": "/api/v1/rules/health"
      },
      "security": {
        "iam_integration": "K-01",
        "audit_integration": "K-07",
        "data_governance": "K-08"
      },
      "observability": {
        "metrics_integration": "K-06",
        "tracing_integration": "K-06",
        "logging_integration": "K-06"
      }
    }
  }
}
```

### 2.2.2 Kernel Execution Interface

**Domain Pack Rule Execution** (`interfaces/rule-execution.ts`)

```typescript
// Domain packs interact with kernel through this interface
interface KernelRuleExecutor {
  // Execute business rule through kernel
  executeRule(request: RuleExecutionRequest): Promise<RuleExecutionResult>;
  
  // Validate rule syntax
  validateRule(rule: BusinessRuleReference): Promise<RuleValidationResult>;
  
  // Get execution metrics
  getRuleMetrics(ruleId: string): Promise<RuleMetrics>;
  
  // Subscribe to rule events
  subscribeToRuleEvents(filter: RuleEventFilter): Promise<EventStream>;
}

// Usage in domain pack
class BankingDomainPack {
  private kernelExecutor: KernelRuleExecutor;
  
  async evaluateCreditApplication(application: CreditApplication): Promise<CreditDecision> {
    const request: RuleExecutionRequest = {
      ruleId: 'credit-scoring-v2',
      language: 'typescript',
      input: { applicant: application },
      context: {
        timestamp: new Date().toISOString(),
        tenantId: this.tenantId,
        permissions: ['rule.execute', 'credit_scoring.read'],
        executionMode: 'sync',
        priority: 'high'
      },
      executionConfig: {
        timeout: 10000,
        memoryLimit: 512 * 1024 * 1024, // 512MB
        caching: { enabled: true, ttl: 300 },
        isolation: { sandbox: true, networkAccess: false }
      }
    };
    
    const result = await this.kernelExecutor.executeRule(request);
    
    if (result.success) {
      return result.result as CreditDecision;
    } else {
      throw new CreditEvaluationError(result.error?.message || 'Unknown error');
    }
  }
}
```

### 2.2.3 Kernel Runtime Registration

**Domain Pack Runtime Registration** (`pack/lifecycle.ts`)

```typescript
class BankingDomainPack implements DomainPack {
  readonly id = "banking-v1.0";
  readonly name = "Banking Domain";
  readonly version = "1.0.0";
  
  async onLoad(): Promise<void> {
    // Register business rules with kernel
    await this.registerBusinessRules();
    
    // Register rule execution configurations
    await this.registerRuleConfigs();
    
    // Register security policies
    await this.registerSecurityPolicies();
  }
  
  private async registerBusinessRules(): Promise<void> {
    const rules: BusinessRuleReference[] = [
      {
        ruleId: 'credit-scoring-v2',
        file: 'rules/credit-scoring.ts',
        language: 'typescript',
        version: '2.0.0',
        dependencies: ['@siddhanta/ml-models', '@siddhanta/financial-calculators'],
        testCases: [
          {
            name: 'high_credit_score_approval',
            input: { applicant: highScoreApplicant },
            expected: { approved: true }
          },
          {
            name: 'low_credit_score_rejection',
            input: { applicant: lowScoreApplicant },
            expected: { approved: false }
          }
        ],
        executionConfig: {
          timeout: 10000,
          memoryLimit: 512 * 1024 * 1024,
          caching: { enabled: true, ttl: 300 },
          isolation: { sandbox: true, networkAccess: false }
        }
      },
      {
        ruleId: 'fraud-detection-ml',
        file: 'rules/fraud-detection.py',
        language: 'python',
        version: '1.5.0',
        dependencies: ['numpy', 'pandas', 'scikit-learn', 'tensorflow'],
        executionConfig: {
          timeout: 15000,
          memoryLimit: 1024 * 1024 * 1024, // 1GB
          caching: { enabled: false },
          isolation: { sandbox: true, networkAccess: true }
        }
      },
      {
        ruleId: 'account-validation',
        file: 'rules/account-validation.sql',
        language: 'sql',
        version: '1.0.0',
        executionConfig: {
          timeout: 5000,
          memoryLimit: 256 * 1024 * 1024,
          caching: { enabled: true, ttl: 600 }
        }
      }
    ];
    
    // Register with kernel K-03
    await this.kernelClient.registerRules(rules);
  }
  
  private async registerRuleConfigs(): Promise<void> {
    const config = {
      routing: {
        'credit_scoring': 'typescript',
        'fraud_detection': 'python',
        'account_validation': 'sql',
        'transaction_limits': 'rego',
        'compliance_checks': 'dsl'
      },
      performance: {
        parallel_execution: true,
        max_concurrent_rules: 10,
        cache_size: 1000,
        cache_ttl: 300
      },
      security: {
        sandbox_enabled: true,
        resource_monitoring: true,
        audit_logging: true
      }
    };
    
    await this.kernelClient.updateRuleEngineConfig(config);
  }
  
  private async registerSecurityPolicies(): Promise<void> {
    const policies = [
      {
        name: 'banking-rule-execution',
        effect: 'allow',
        actions: ['rule.execute'],
        resources: ['rule:banking:*'],
        conditions: [
          {
            field: 'request.tenant',
            operator: 'equals',
            value: this.tenantId
          }
        ]
      }
    ];
    
    await this.kernelClient.registerSecurityPolicies(policies);
  }
}
```

### 2.3 Custom Workflows

#### Loan Origination Workflow (`workflows/loan-origination.yaml`)

```yaml
workflow:
  id: "loan-origination"
  name: "Loan Origination Process"
  version: "1.0.0"
  
  states:
    - id: "application_received"
      type: "start"
      actions:
        - validate_application
        - run_credit_check
        - calculate_debt_to_income
      
    - id: "credit_underwriting"
      type: "process"
      actions:
        - credit_scoring
        - risk_assessment
        - fraud_check
      transitions:
        - to: "approved"
          condition: "credit_score >= 650 AND debt_to_income < 0.43"
        - to: "manual_review"
          condition: "credit_score >= 600 AND credit_score < 650"
        - to: "rejected"
          condition: "credit_score < 600"
    
    - id: "manual_review"
      type: "human_task"
      assignee: "underwriter"
      actions:
        - review_application
        - request_additional_documents
        - make_decision
      transitions:
        - to: "approved"
          condition: "decision = 'approve'"
        - to: "rejected"
          condition: "decision = 'reject'"
    
    - id: "approved"
      type: "process"
      actions:
        - generate_loan_agreement
        - schedule_disbursement
        - create_account
      transitions:
        - to: "disbursed"
    
    - id: "rejected"
      type: "end"
      actions:
        - send_rejection_notice
        - update_credit_bureau
    
    - id: "disbursed"
      type: "end"
      actions:
        - activate_repayment_schedule
        - send_welcome_kit
  
  # Custom business rules
  business_rules:
    - file: "rules/credit-scoring.rego"
    - file: "rules/fraud-detection.rego"
    - file: "rules/compliance-checks.rego"
  
  # Required data
  required_data:
    - customer_profile
    - credit_history
    - income_verification
    - collateral_information
  
  # Notifications
  notifications:
    - event: "application_received"
      recipients: ["customer", "loan_officer"]
      template: "loan_application_received"
    
    - event: "loan_approved"
      recipients: ["customer", "loan_officer"]
      template: "loan_approval_notification"
    
    - event: "loan_rejected"
      recipients: ["customer", "loan_officer"]
      template: "loan_rejection_notification"
```

### 2.4 Custom Integrations

#### Banking System Integrations (`integrations/banking-systems.yaml`)

```yaml
integrations:
  # Core Banking System Integration
  - id: "core_banking"
    type: "rest_api"
    description: "Integration with core banking system"
    
    endpoints:
      - path: "/accounts"
        methods: ["GET", "POST", "PUT"]
        authentication: "oauth2"
        rate_limit: "1000/minute"
        
      - path: "/transactions"
        methods: ["GET", "POST"]
        authentication: "oauth2"
        rate_limit: "5000/minute"
        
      - path: "/customers"
        methods: ["GET", "POST", "PUT"]
        authentication: "oauth2"
        rate_limit: "2000/minute"
    
    data_mapping:
      account:
        from: "core_banking"
        to: "banking_domain"
        fields:
          account_number: "accountId"
          account_type: "accountType"
          balance: "currentBalance"
          customer_id: "customerId"
    
    error_handling:
      retry_policy: "exponential_backoff"
      max_retries: 3
      timeout: 30
  
  # Credit Bureau Integration
  - id: "credit_bureau"
    type: "soap_api"
    description: "Integration with credit bureau for credit reports"
    
    endpoints:
      - path: "/CreditReportService"
        methods: ["POST"]
        authentication: "certificate"
        rate_limit: "100/minute"
    
    request_mapping:
      credit_report_request:
        ssn: "customer.ssn"
        dob: "customer.dateOfBirth"
        name: "customer.fullName"
        address: "customer.currentAddress"
    
    response_mapping:
      credit_score: "report.creditScore"
      credit_history: "report.paymentHistory"
      outstanding_debts: "report.totalDebt"
    
    compliance:
      data_retention: "30_days"
      consent_required: true
      audit_logging: true
  
  # Payment Network Integration
  - id: "payment_network"
    type: "async_api"
    description: "Integration with payment networks (ACH, SWIFT, etc.)"
    
    events:
      - name: "payment_initiated"
        topic: "banking.payments.initiated"
        schema: "payment_initiation_event"
        
      - name: "payment_completed"
        topic: "banking.payments.completed"
        schema: "payment_completion_event"
        
      - name: "payment_failed"
        topic: "banking.payments.failed"
        schema: "payment_failure_event"
    
    processing:
      batch_size: 100
      processing_interval: "5_minutes"
      retry_policy: "3_attempts_with_backoff"
```

---

## 3. Healthcare Domain Pack Customization

### 3.1 Healthcare-Specific Manifest

```yaml
# domain-packs/healthcare/pack.yaml
domainPack:
  id: "healthcare-v1.0"
  name: "Healthcare Domain"
  version: "1.0.0"
  description: "Comprehensive healthcare domain with EMR, billing, and research"
  
  domainType: "healthcare"
  industry: "healthcare"
  subdomains:
    - "patient-management"
    - "clinical-workflows"
    - "medical-records"
    - "billing"
    - "research"
    - "pharmacy"
    - "laboratory"
  
  capabilities:
    - "PATIENT_MANAGEMENT"
    - "CLINICAL_WORKFLOWS"
    - "MEDICAL_RECORDS"
    - "BILLING_PROCESSING"
    - "RESEARCH_DATA"
    - "PRESCRIPTION_MANAGEMENT"
    - "LABORATORY_INTEGRATION"
    - "TELEHEALTH"
  
  # HIPAA compliance requirements
  compliance:
    standards: ["HIPAA", "HL7", "FHIR"]
    data_protection: "phi_encryption"
    audit_requirements: "access_logging"
    retention_policy: "7_years"
```

### 3.2 Healthcare Business Rules

#### Patient Eligibility Rules (`rules/patient-eligibility.rego`)

```rego
package healthcare.eligibility

# Check insurance eligibility
is_eligible service {
    insurance := get_patient_insurance(input.patient_id)
    coverage := check_coverage(insurance, service)
    coverage.is_covered
}

# Check coverage for specific services
check_coverage insurance, service {
    plan := insurance.plans[service.service_type]
    plan.coverage_percentage > 0
    service.cost <= plan.annual_limit - plan.used_amount
}

# HIPAA access control
allow_access user, patient_record {
    is_healthcare_provider(user)
    has_patient_relationship(user.user_id, patient_record.patient_id)
    need_to_know(user.role, patient_record.record_type)
}

# Check if user is healthcare provider
is_healthcare_provider user {
    user.role in ["doctor", "nurse", "technician", "administrator"]
}

# Check patient relationship
has_patient_relationship provider_id, patient_id {
    assignments := get_provider_assignments(provider_id)
    contains(patient_id, assignments.patients)
}

# Need-to-know basis
need_to_know role, record_type {
    allowed_access[role][_] = record_type
}

allowed_access = {
    "doctor": ["diagnosis", "treatment", "medication", "lab_results"],
    "nurse": ["medication", "vital_signs", "treatment_plan"],
    "technician": ["lab_results", "imaging"],
    "administrator": ["billing", "scheduling"]
}
```

### 3.3 Healthcare Workflows

#### Patient Admission Workflow (`workflows/patient-admission.yaml`)

```yaml
workflow:
  id: "patient_admission"
  name: "Patient Admission Process"
  version: "1.0.0"
  
  states:
    - id: "patient_arrival"
      type: "start"
      actions:
        - verify_appointment
        - collect_demographics
        - check_insurance
        - generate_medical_record_number
      
    - id: "clinical_assessment"
      type: "process"
      actions:
        - vital_signs_collection
        - medical_history_review
        - initial_diagnosis
        - treatment_planning
      transitions:
        - to: "insurance_verification"
          condition: "diagnosis.requires_admission"
        - to: "outpatient_processing"
          condition: "not diagnosis.requires_admission"
    
    - id: "insurance_verification"
      type: "process"
      actions:
        - eligibility_check
        - pre_authorization
        - benefit_verification
      transitions:
        - to: "bed_assignment"
          condition: "coverage.is_approved"
        - to: "financial_counseling"
          condition: "not coverage.is_approved"
    
    - id: "bed_assignment"
      type: "process"
      actions:
        - find_available_bed
        - assign_bed
        - notify_nursing_station
      transitions:
        - to: "admission_complete"
    
    - id: "admission_complete"
      type: "end"
      actions:
        - generate_admission_orders
        - notify_attending_physician
        - update_bed_board
  
  # HIPAA compliance
  compliance:
    phi_protection: true
    audit_logging: true
    consent_management: true
    data_minimization: true
```

---

## 4. Advanced Customization Techniques

### 4.1 Custom Domain Events

#### Banking Domain Events (`schemas/events.json`)

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "Banking Domain Events",
  "events": {
    "AccountOpened": {
      "type": "object",
      "properties": {
        "event_type": {"type": "string", "const": "AccountOpened"},
        "aggregate_id": {"type": "string", "description": "Account ID"},
        "causality_id": {"type": "string", "description": "Customer ID"},
        "trace_id": {"type": "string"},
        "timestamp_bs": {"type": "string", "format": "date-time"},
        "timestamp_gregorian": {"type": "string", "format": "date-time"},
        "data": {
          "type": "object",
          "properties": {
            "account_id": {"type": "string"},
            "customer_id": {"type": "string"},
            "account_type": {"type": "string", "enum": ["SAVINGS", "CURRENT", "FIXED_DEPOSIT"]},
            "initial_deposit": {"type": "number", "minimum": 0},
            "currency": {"type": "string", "pattern": "^[A-Z]{3}$"},
            "branch_code": {"type": "string"},
            "opened_by": {"type": "string"}
          },
          "required": ["account_id", "customer_id", "account_type", "initial_deposit", "currency"]
        }
      }
    },
    "LoanApproved": {
      "type": "object",
      "properties": {
        "event_type": {"type": "string", "const": "LoanApproved"},
        "aggregate_id": {"type": "string", "description": "Loan ID"},
        "causality_id": {"type": "string", "description": "Application ID"},
        "trace_id": {"type": "string"},
        "timestamp_bs": {"type": "string", "format": "date-time"},
        "timestamp_gregorian": {"type": "string", "format": "date-time"},
        "data": {
          "type": "object",
          "properties": {
            "loan_id": {"type": "string"},
            "application_id": {"type": "string"},
            "customer_id": {"type": "string"},
            "loan_type": {"type": "string"},
            "principal_amount": {"type": "number", "minimum": 0},
            "interest_rate": {"type": "number", "minimum": 0},
            "term_months": {"type": "integer", "minimum": 1},
            "monthly_payment": {"type": "number", "minimum": 0},
            "credit_score": {"type": "integer", "minimum": 300, "maximum": 850},
            "approved_by": {"type": "string"},
            "approval_date": {"type": "string", "format": "date"}
          },
          "required": ["loan_id", "application_id", "customer_id", "principal_amount", "interest_rate"]
        }
      }
    }
  }
}
```

### 4.2 Custom API Endpoints

#### Banking API Schema (`schemas/apis.json`)

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "Banking Domain APIs",
  "apis": {
    "AccountManagement": {
      "basePath": "/api/banking/v1/accounts",
      "endpoints": {
        "CreateAccount": {
          "path": "/",
          "method": "POST",
          "requestBody": {
            "schema": {
              "type": "object",
              "properties": {
                "customer_id": {"type": "string"},
                "account_type": {"type": "string", "enum": ["SAVINGS", "CURRENT", "FIXED_DEPOSIT"]},
                "initial_deposit": {"type": "number", "minimum": 0},
                "currency": {"type": "string", "pattern": "^[A-Z]{3}$"},
                "branch_code": {"type": "string"}
              },
              "required": ["customer_id", "account_type", "initial_deposit", "currency"]
            }
          },
          "responses": {
            "201": {
              "description": "Account created successfully",
              "schema": {
                "type": "object",
                "properties": {
                  "account_id": {"type": "string"},
                  "account_number": {"type": "string"},
                  "customer_id": {"type": "string"},
                  "account_type": {"type": "string"},
                  "balance": {"type": "number"},
                  "currency": {"type": "string"},
                  "created_at": {"type": "string", "format": "date-time"}
                }
              }
            }
          }
        },
        "GetAccountBalance": {
          "path": "/{account_id}/balance",
          "method": "GET",
          "parameters": {
            "account_id": {
              "in": "path",
              "required": true,
              "type": "string"
            }
          },
          "responses": {
            "200": {
              "description": "Account balance retrieved",
              "schema": {
                "type": "object",
                "properties": {
                  "account_id": {"type": "string"},
                  "available_balance": {"type": "number"},
                  "current_balance": {"type": "number"},
                  "currency": {"type": "string"},
                  "last_updated": {"type": "string", "format": "date-time"}
                }
              }
            }
          }
        }
      }
    }
  }
}
```

### 4.3 Custom UI Components

#### Banking Dashboard (`ui/banker-dashboard.json`)

```json
{
  "dashboard": {
    "id": "banker-dashboard",
    "name": "Banker Dashboard",
    "layout": "grid",
    "widgets": [
      {
        "id": "account-summary",
        "type": "metric-card",
        "title": "Account Summary",
        "dataSource": "accounts",
        "metrics": [
          {
            "label": "Total Accounts",
            "value": "{{data.total_accounts}}",
            "trend": "{{data.account_growth}}%"
          },
          {
            "label": "Total Deposits",
            "value": "{{format_currency(data.total_deposits)}}",
            "trend": "{{data.deposit_growth}}%"
          }
        ]
      },
      {
        "id": "loan-portfolio",
        "type": "chart",
        "title": "Loan Portfolio",
        "chartType": "pie",
        "dataSource": "loans",
        "dataMapping": {
          "labels": "loan_types",
          "values": "amounts"
        }
      },
      {
        "id": "recent-transactions",
        "type": "table",
        "title": "Recent Transactions",
        "dataSource": "transactions",
        "columns": [
          {"key": "transaction_id", "label": "Transaction ID"},
          {"key": "account_number", "label": "Account"},
          {"key": "amount", "label": "Amount", "format": "currency"},
          {"key": "type", "label": "Type"},
          {"key": "timestamp", "label": "Time", "format": "datetime"}
        ],
        "pageSize": 10
      },
      {
        "id": "pending-approvals",
        "type": "action-list",
        "title": "Pending Approvals",
        "dataSource": "pending_approvals",
        "actions": [
          {"label": "Approve", "action": "approve", "style": "primary"},
          {"label": "Reject", "action": "reject", "style": "danger"}
        ]
      }
    ]
  }
}
```

---

## 5. Configuration Customization

### 5.1 Domain-Specific Configuration

#### Banking Configuration (`config/banking-products.json`)

```json
{
  "banking_products": {
    "savings_account": {
      "minimum_balance": 1000,
      "interest_rate": 0.025,
      "maintenance_fee": 10,
      "transaction_limit": 6,
      "overdraft_allowed": false
    },
    "current_account": {
      "minimum_balance": 0,
      "interest_rate": 0.001,
      "maintenance_fee": 25,
      "transaction_limit": 1000,
      "overdraft_allowed": true,
      "overdraft_limit": 10000
    },
    "fixed_deposit": {
      "minimum_amount": 10000,
      "tenure_options": [6, 12, 24, 36],
      "interest_rates": {
        "6": 0.055,
        "12": 0.065,
        "24": 0.075,
        "36": 0.085
      },
      "premature_withdrawal_penalty": 0.01
    },
    "personal_loan": {
      "minimum_amount": 50000,
      "maximum_amount": 5000000,
      "tenure_options": [12, 24, 36, 48, 60],
      "interest_rates": {
        "12": 0.12,
        "24": 0.13,
        "36": 0.14,
        "48": 0.15,
        "60": 0.16
      },
      "processing_fee": 0.02,
      "prepayment_penalty": 0.04
    }
  },
  "risk_parameters": {
    "credit_score_thresholds": {
      "excellent": 750,
      "good": 700,
      "fair": 650,
      "poor": 600
    },
    "debt_to_income_limits": {
      "conservative": 0.28,
      "moderate": 0.36,
      "aggressive": 0.43
    },
    "fraud_detection": {
      "transaction_amount_multiplier": 5,
      "frequency_threshold": 10,
      "time_window_hours": 1
    }
  }
}
```

### 5.2 Jurisdiction Overrides

#### Nepal Banking Regulations (`config/jurisdiction-overrides.json`)

```json
{
  "jurisdictions": {
    "NP": {
      "banking": {
        "regulatory_body": "Nepal Rastra Bank",
        "requirements": {
          "kyc_verification": true,
          "credit_bureau_check": true,
          "minimum_balance_savings": 100,
          "maximum_interest_rate": 0.15,
          "reserve_requirement": 0.06
        },
        "reporting": {
          "transaction_reporting_threshold": 1000000,
          "suspicious_transaction_reporting": true,
          "currency_transaction_reporting": true
        },
        "business_rules": {
          "loan_to_value_ratio": 0.8,
          "debt_to_income_ratio": 0.4,
          "minimum_credit_score": 600
        }
      }
    },
    "US": {
      "banking": {
        "regulatory_body": "Federal Reserve",
        "requirements": {
          "kyc_verification": true,
          "credit_bureau_check": true,
          "minimum_balance_savings": 25,
          "maximum_interest_rate": 0.30,
          "reserve_requirement": 0.10
        },
        "reporting": {
          "transaction_reporting_threshold": 10000,
          "suspicious_transaction_reporting": true,
          "currency_transaction_reporting": true
        },
        "business_rules": {
          "loan_to_value_ratio": 0.95,
          "debt_to_income_ratio": 0.43,
          "minimum_credit_score": 580
        }
      }
    }
  }
}
```

---

## 6. Testing Customization

### 6.1 Domain-Specific Tests

#### Banking Tests (`tests/unit/banking-rules.test.js`)

```javascript
describe('Banking Domain Rules', () => {
  describe('Credit Scoring', () => {
    test('should approve loan for excellent credit', async () => {
      const applicant = {
        income: 80000,
        age: 35,
        credit_history: { excellent: true },
        debt_to_income: 0.25,
        product_type: 'personal_loan'
      };
      
      const result = await evaluateRule('banking.credit.scoring', { applicant });
      expect(result.allow).toBe(true);
      expect(result.score).toBeGreaterThan(700);
    });
    
    test('should reject loan for poor credit', async () => {
      const applicant = {
        income: 30000,
        age: 25,
        credit_history: { poor: true },
        debt_to_income: 0.5,
        product_type: 'personal_loan'
      };
      
      const result = await evaluateRule('banking.credit.scoring', { applicant });
      expect(result.allow).toBe(false);
      expect(result.score).toBeLessThan(600);
    });
  });
  
  describe('Fraud Detection', () => {
    test('should flag suspicious large transaction', async () => {
      const transaction = {
        id: 'txn_001',
        customer_id: 'cust_001',
        amount: 50000,
        location: 'Foreign',
        timestamp: new Date().toISOString()
      };
      
      const result = await evaluateRule('banking.fraud.detection', { transaction });
      expect(result.allow).toBe(false);
      expect(result.alerts).toHaveLength(1);
      expect(result.alerts[0].alert_type).toBe('SUSPICIOUS_TRANSACTION');
    });
  });
});
```

---

## 7. Deployment Customization

### 7.1 Domain-Specific Deployment Configuration

#### Banking Deployment (`deployment/banking-deployment.yaml`)

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: banking-domain-pack
  labels:
    domain: banking
    version: v1.0.0
spec:
  replicas: 3
  selector:
    matchLabels:
      app: banking-domain-pack
  template:
    metadata:
      labels:
        app: banking-domain-pack
    spec:
      containers:
      - name: banking-services
        image: siddhanta/banking-domain:v1.0.0
        ports:
        - containerPort: 8080
        env:
        - name: DOMAIN_CONFIG_PATH
          value: "/config/banking-config.json"
        - name: JURISDICTION
          value: "NP"
        - name: LOG_LEVEL
          value: "INFO"
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        volumeMounts:
        - name: config
          mountPath: /config
      volumes:
      - name: config
        configMap:
          name: banking-config
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: banking-config
data:
  banking-config.json: |
    {
      "domain": "banking",
      "version": "1.0.0",
      "features": {
        "credit_scoring": true,
        "fraud_detection": true,
        "mobile_banking": true
      },
      "integrations": {
        "core_banking": "http://core-banking:8080",
        "credit_bureau": "https://credit-bureau.example.com"
      }
    }
```

---

## 8. Best Practices for Domain Pack Customization

### 8.1 Design Principles

1. **Separation of Concerns**: Keep domain logic separate from kernel logic
2. **Configuration-Driven**: Externalize as much logic as possible
3. **Test Coverage**: Comprehensive testing for all custom logic
4. **Documentation**: Clear documentation for custom components
5. **Version Control**: Proper versioning of domain packs

### 8.2 Security Considerations

1. **Input Validation**: Validate all inputs at domain boundaries
2. **Access Control**: Implement proper authorization for domain resources
3. **Data Protection**: Encrypt sensitive domain-specific data
4. **Audit Logging**: Log all domain-specific operations
5. **Compliance**: Ensure domain-specific compliance requirements

### 8.3 Performance Optimization

1. **Caching**: Cache frequently accessed domain data
2. **Async Processing**: Use async processing for heavy domain operations
3. **Database Optimization**: Optimize database queries for domain data
4. **Resource Management**: Proper resource management for domain services
5. **Monitoring**: Monitor domain-specific performance metrics

---

## 9. Conclusion

This guide provides comprehensive customization options for domain packs, allowing organizations to create tailored solutions while leveraging the generic Siddhanta kernel. The modular architecture ensures that customizations are maintainable, testable, and upgradeable.

Key takeaways:
1. **Layered Customization**: Customize at multiple layers (config, rules, workflows, integrations)
2. **Domain-Specific Logic**: Implement business logic using OPA/Rego rules
3. **Extensible Architecture**: Add custom events, APIs, and UI components
4. **Testing & Deployment**: Comprehensive testing and deployment strategies
5. **Best Practices**: Follow security, performance, and maintainability best practices

The domain pack customization framework enables rapid development of industry-specific solutions while maintaining consistency and quality across the platform.
