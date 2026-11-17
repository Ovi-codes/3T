# AWS infrastructure

The deploy is moving from Azure to AWS: two containers on ECS behind CloudFront, in
eu-central-1. [ADR 0003](../docs/adr/0003-two-containers-on-ecs-behind-cloudfront.md) explains
why, and what it costs.

This folder gets the CloudFormation templates and the one-time setup steps in the next step of
#98. Until then, [`deploy.yml`](../.github/workflows/deploy.yml) builds the two images and pushes
them to GHCR, and deploys nothing.

The Azure Bicep template from Increment 14 (ADR 0002) was never provisioned, so it was removed.
