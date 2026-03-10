# LLM Prompt Pack for Learning Unit Generation

This directory contains prompt templates for AI-assisted Learning Unit authoring.

## Prompts

1. **generate-learning-unit.md** - Generate a complete Learning Unit from a topic
2. **validate-learning-unit.md** - Validate an existing Learning Unit
3. **generate-simulation.md** - Generate a simulation manifest from a claim
4. **suggest-claims.md** - Suggest claims from an intent statement

## Usage

These prompts are designed to be used with the TutorPutor AI Service.
They follow the Canonical Learning Unit Schema defined in `contracts/v1/learning-unit.ts`.

## Safety Guidelines

1. All LLM-generated content MUST be validated against the JSON Schema
2. Human review is required before publishing
3. Generated simulations should be tested in sandbox mode first
