#!/bin/bash

# Copyright (C) 2024 Intel Corporation
# SPDX-License-Identifier: Apache-2.0

function freeze() {
    local file=$1
    local folder=$(dirname "$file")
    pip-compile --no-upgrade --output-file "$folder/freeze.txt" "$file"
    if [[ -e "$folder/freeze.txt" ]]; then
        mv "$folder/freeze.txt" "$file"
    fi
}

function check_branch_name() {
    if [[ "$GITHUB_REF_NAME" == *"rc" ]]; then
        echo "$GITHUB_REF_NAME is release branch"
    else
        echo "$GITHUB_REF_NAME is not release branch"
        # exit 0
    fi
}

function main() {
    check_branch_name
    echo "::group::pip install pip-tools" && pip install pip-tools --upgrade && echo "::endgroup::"
    export -f freeze
    find . -name "requirements.txt" | xargs -n 1 -I {} bash -c 'freeze "$@"' _ {}
}

main
