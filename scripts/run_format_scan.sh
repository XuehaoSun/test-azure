#!/bin/bash
# Script assumes that repository is currently on branch with new changes (source branch).

set -x
set -eo pipefail

PATTERN='[-a-zA-Z0-9_]*='
if [ $# != "3" ] ; then 
    echo 'ERROR:'
    echo "Expected 3 parameters got $#"
    printf 'Please use following parameters:
    --repo_dir=<path to repository>
    --tool=<pylint | bandit>
    --python_version=<conda python version>
    '
    exit 1
fi

for i in "$@"
do
    case $i in
        --repo_dir=*)
            REPO_DIR=`echo $i | sed "s/${PATTERN}//"`;;
        --tool=*)
            SCAN_TOOL=`echo $i | sed "s/${PATTERN}//"`;;
        --python_version=*)
            python_version=`echo $i | sed "s/${PATTERN}//"`;;
        *)
            echo "Parameter $i not recognized."; exit 1;;
    esac
done

main() {
    export PATH=${HOME}/miniconda3/bin/:$PATH
    source activate ilit-format_scan-${python_version}
    pip -V
    python -V

    # Install test requirements
    pip config set global.index-url https://pypi.douban.com/simple/
    pip install -U pip
    cd ${REPO_DIR}/test
    if [ -f "requirements.txt" ]; then
        sed -i '/ilit/d' requirements.txt
        python -m pip install --default-timeout=100 -r requirements.txt
        pip list
    else
        echo "Not found requirements.txt file."
    fi

    cd ${REPO_DIR}

    echo "Executing pylint on branch: $(git name-rev --name-only HEAD)."

    case ${SCAN_TOOL} in
        "pylint") run_pylint;;
        "bandit") run_bandit;;
        *)
            echo "Scan tool ${SCAN_TOOL} not supported."; exit 1;;
    esac
}

run_pylint() {
    pip install pylint
    python -m pylint -f json --disable=R,C,W,I,E0401,E0611,E0203 --enable=line-too-long --max-line-length=99 --extension-pkg-whitelist=numpy ilit > ${WORKSPACE}/ilit-pylint.json
    exit_code=$?
    if [ ${exit_code} -ne 0 ] ; then
        echo "PyLint exited with non-zero exit code."; exit 1
    fi
    exit 0
}

run_bandit() {
    pip install bandit
    python -m bandit -r -lll -iii ilit > ${WORKSPACE}/ilit-bandit.log
    exit_code=$?
    if [ ${exit_code} -ne 0 ] ; then
        echo "Bandit exited with non-zero exit code."; exit 1
    fi
    exit 0
}

main
