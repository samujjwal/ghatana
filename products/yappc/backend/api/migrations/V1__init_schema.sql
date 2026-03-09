-- YAPPC Database Migration: V1 - Init Schema
-- PostgreSQL 16
-- Initial schema setup and utility functions

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Create schema
CREATE SCHEMA IF NOT EXISTS yappc;

-- Create updated_at trigger function
CREATE OR REPLACE FUNCTION yappc.update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
