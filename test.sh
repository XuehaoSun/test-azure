#!/bin/bash

# Copyright (C) 2024 Intel Corporation
# SPDX-License-Identifier: Apache-2.0

echo -e "::notice::This is a notice message\n"

echo -e "::warning::This is a warning message\n"

echo -e "::error::This is an error message\n"

echo "::add-mask::Mona The Octocat"
echo -e "Mask value: Mona The Octocat\n"

echo "::group::This is a group message"
echo "This is the output of the group message"
echo "::endgroup::"

echo "### Hello world! :rocket:" >>$GITHUB_STEP_SUMMARY
echo "This is the lead in sentence for the list" >>$GITHUB_STEP_SUMMARY
echo "" >>$GITHUB_STEP_SUMMARY # this is a blank line
echo "- Lets add a bullet point" >>$GITHUB_STEP_SUMMARY
echo "- Lets add a second bullet point" >>$GITHUB_STEP_SUMMARY
echo "- How about a third one?" >>$GITHUB_STEP_SUMMARY

echo "::group::test wget"
wget https://github.com/docker/compose/releases/download/v2.33.1/docker-compose-linux-x86_64
wget --tries=5 --no-verbose https://github.com/docker/compose/releases/download/v2.33.1/docker-compose-linux-x86_64
echo "::endgroup::"

echo "::group::test docker pull"
docker pull hello-world
docker pull --quiet hello-world
echo "::endgroup::"

echo "::group::test wget"
python -c "import tqdm; import time; for i in tqdm.tqdm(range(100), desc='Processing'): time.sleep(0.1)"
export TQDM_POSITION=-1    # fix progress bar on tty mode
export TQDM_MININTERVAL=60 # set refresh every 60s
python -c "import tqdm; import time; for i in tqdm.tqdm(range(100), desc='Processing'): time.sleep(0.1)"
echo "::endgroup::"

