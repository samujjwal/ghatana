# YAPPC App-Creator Web – Operations Guide

## 1. What is Being Operated

The app-creator web app is the main YAPPC frontend for building applications. Operations covers:

- Keeping the app-creator available and responsive.
- Ensuring it can reach required YAPPC backend services.

## 2. Dependencies & Pre-Conditions

- YAPPC backend services must be reachable and on compatible versions.
- Frontend must be configured with correct API base URLs and feature flags.

## 3. Health & Monitoring

Monitor:

- App availability and client-side error logs.
- Performance indicators (page load, large interactions) where available.

Pay particular attention to:

- Errors when saving or loading applications.
- Performance of canvas interactions and complex layouts.

## 4. Deployment & Rollout

- Build and deploy the app via the standard web build pipeline.
- Validate key flows (canvas, save/load, publish) in staging before production rollout.

Use a gradual rollout if supported by your hosting platform, and monitor both frontend and backend metrics.

This guide is self-contained and captures core operational considerations for the app-creator web app.
