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
    branch_name=$(git branch --show-current)
    echo "$branch_name is release branch"
}

function main() {
    check_branch_name
    pip install pip-tools --upgrade
    export -f freeze
    find . -name "requirements.txt" | xargs -n 1 -I {} bash -c 'freeze "$@"' _ {}
}

main
