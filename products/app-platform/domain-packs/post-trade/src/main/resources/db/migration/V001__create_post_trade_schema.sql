-- V001__create_post_trade_schema.sql
-- D-09 Post-Trade Processing: trade confirmations, netting, settlement instructions

-- Trade confirmations
CREATE TABLE trade_confirmations (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id            UUID NOT NULL,
    fill_id             UUID,
    client_id           UUID NOT NULL,
    instrument_id       UUID NOT NULL,
    side                VARCHAR(4) NOT NULL,         -- BUY | SELL
    quantity            BIGINT NOT NULL,
    price               NUMERIC(20, 8) NOT NULL,
    fees                NUMERIC(20, 8) NOT NULL DEFAULT 0,
    net_amount          NUMERIC(20, 8) NOT NULL,
    currency            VARCHAR(3) NOT NULL,
    settlement_date     DATE NOT NULL,
    settlement_date_bs  VARCHAR(10),
    trade_date          DATE NOT NULL,
    trade_date_bs       VARCHAR(10),
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',  -- PENDING|SENT|DELIVERED|ACKNOWLEDGED
    template_version    VARCHAR(20),
    pdf_path            TEXT,
    json_payload        JSONB,
    acknowledged_at     TIMESTAMPTZ,
    delivered_at        TIMESTAMPTZ,
    escalation_at       TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_conf_status CHECK (status IN ('PENDING','SENT','DELIVERED','ACKNOWLEDGED','ESCALATED'))
);
CREATE INDEX idx_conf_order ON trade_confirmations(order_id);
CREATE INDEX idx_conf_client_status ON trade_confirmations(client_id, status);

-- Netting sets (bilateral and multilateral)
CREATE TABLE netting_sets (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    netting_type        VARCHAR(20) NOT NULL,   -- BILATERAL | MULTILATERAL
    run_date            DATE NOT NULL,
    run_date_bs         VARCHAR(10),
    cutoff_time         TIMESTAMPTZ NOT NULL,
    participant_ids     UUID[] NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'DRAFT',  -- DRAFT|COMPLETED|RECONCILED
    gross_trade_count   INT NOT NULL DEFAULT 0,
    net_reduction_pct   NUMERIC(8, 4),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Net positions within a netting set
CREATE TABLE netting_positions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    netting_set_id      UUID NOT NULL REFERENCES netting_sets(id),
    participant_id      UUID NOT NULL,
    counterparty_id     UUID,           -- NULL for multilateral (vs CCP)
    instrument_id       UUID NOT NULL,
    settlement_date     DATE NOT NULL,
    net_quantity        BIGINT NOT NULL,     -- positive = net receiver, negative = net deliverer
    net_cash            NUMERIC(20, 8) NOT NULL,
    currency            VARCHAR(3) NOT NULL
);
CREATE INDEX idx_netpos_set ON netting_positions(netting_set_id);

-- Settlement instructions
CREATE TABLE settlement_instructions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    netting_set_id      UUID REFERENCES netting_sets(id),
    netting_position_id UUID REFERENCES netting_positions(id),
    order_id            UUID,                   -- for non-netted instructions
    instrument_id       UUID NOT NULL,
    quantity            BIGINT NOT NULL,
    direction           VARCHAR(10) NOT NULL,   -- DELIVER | RECEIVE
    counterparty_id     UUID NOT NULL,
    settlement_date     DATE NOT NULL,
    settlement_date_bs  VARCHAR(10),
    settlement_account  VARCHAR(64),
    currency            VARCHAR(3) NOT NULL,
    amount              NUMERIC(20, 8) NOT NULL,
    status              VARCHAR(20) NOT NULL DEFAULT 'GENERATED',
    csd_reference       VARCHAR(64),            -- external CSD reference
    matched_instruction UUID REFERENCES settlement_instructions(id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_si_status CHECK (status IN ('GENERATED','MATCHED','AFFIRMED','SETTLED','FAILED','CANCELLED'))
);
CREATE INDEX idx_si_settlement_date ON settlement_instructions(settlement_date);
CREATE INDEX idx_si_status ON settlement_instructions(status);
CREATE INDEX idx_si_counterparty ON settlement_instructions(counterparty_id);
