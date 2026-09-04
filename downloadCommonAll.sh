#!/bin/bash
set -eux
rm -fr workflowResult
run_id="$(gh run list --workflow common-all.yml --branch main --status success --limit 1 --json databaseId --jq '.[0].databaseId')"
gh run download "$run_id" --name common-artifacts --dir workflowResult
