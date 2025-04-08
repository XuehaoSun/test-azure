#!/bin/bash

# Copyright (C) 2024 Intel Corporation
# SPDX-License-Identifier: Apache-2.0

echo "::debug::This is a debug message"
echo "::notice::This is a notice message"
echo "::warning::This is a warning message"
echo "::error::This is an error message"
echo "::add-mask::Mona The Octocat"

echo "::group::This is a group message"
echo "::set-output name=group_output::This is the output of the group message"
echo "::endgroup::This is the end of the group message"

echo "### Hello world! :rocket:" >>$GITHUB_STEP_SUMMARY
echo "This is the lead in sentence for the list" >>$GITHUB_STEP_SUMMARY
echo "" >>$GITHUB_STEP_SUMMARY # this is a blank line
echo "- Lets add a bullet point" >>$GITHUB_STEP_SUMMARY
echo "- Lets add a second bullet point" >>$GITHUB_STEP_SUMMARY
echo "- How about a third one?" >>$GITHUB_STEP_SUMMARY
