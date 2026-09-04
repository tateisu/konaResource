#!/bin/bash
set -eux
run_id="$(gh run list --workflow common-all.yml --branch main --status success --limit 1 --json databaseId --jq '.[0].databaseId')"
rm -fr workflowResult
gh run download "$run_id" --name common-artifacts --dir workflowResult
