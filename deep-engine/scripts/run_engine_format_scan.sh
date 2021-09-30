#!/bin/bash
# Script assumes that repository is currently on branch with new changes (source branch).

set -x
set -eo pipefail

PATTERN='[-a-zA-Z0-9_]*='
if [ $# != "2" ] ; then
    echo 'ERROR:'
    echo "Expected 3 parameters got $#"
    printf 'Please use following parameters:
    --repo_dir=<path to repository>
    --tool=<pylint | bandit>
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
        *)
            echo "Parameter $i not recognized."; exit 1;;
    esac
done

main() {
    export PATH=${HOME}/miniconda3/bin/:$PATH
    if [ $(conda info -e | grep 'deep-engine-code-scan' | wc -l) != 0 ]; then
        echo "deep-engine-code-scan exist!"
    else
        conda create python=3.7 -y -n deep-engine-code-scan
    fi
    source activate deep-engine-code-scan
    pip -V
    python -V
    pip install -U pip

    cd ${REPO_DIR}
    echo "Executing code scan on branch: $(git name-rev --name-only HEAD)."

    case ${SCAN_TOOL} in
        "cpplint") run_cpplint;;
        "pylint") run_pylint;;
        "bandit") run_bandit;;
        "pyspelling") run_pyspelling;;
        "cloc") run_cloc;;
        *)
            echo "Scan tool ${SCAN_TOOL} not supported."; exit 1;;
    esac
}

run_cpplint() {
    pip install cpplint
    log_path=${WORKSPACE}/engine_cpplint.log
    cpplint --recursive --quiet --linelength=99 ./deep_engine/ 2>&1| tee ${log_path}
    if [[ ! -f ${log_path} ]] || [[ $(grep -c "Total errors found:" ${log_path}) != 0 ]]; then
        exit 1
    fi
    exit 0
}

run_pylint() {
    pip install pylint
    # tf_utils.util will import some deps installed by tensorflow
    pip install intel-tensorflow

    python -m pylint -f json --disable=R,C,W,E1129 --enable=line-too-long --max-line-length=99 --extension-pkg-whitelist=numpy --ignored-classes=TensorProto,NodeProto --ignored-modules=onnx,onnxruntime,tensorflow,lpot,engine_py ./deep_engine > ${WORKSPACE}/engine-pylint.json

    exit_code=$?
    if [ ${exit_code} -ne 0 ] ; then
        echo "PyLint exited with non-zero exit code."; exit 1
    fi
    exit 0
}

run_bandit() {
    pip install bandit
    python -m bandit -r -lll -iii ./deep_engine/ > ${WORKSPACE}/engine-bandit.log
    exit_code=$?
    if [ ${exit_code} -ne 0 ] ; then
        echo "Bandit exited with non-zero exit code."; exit 1
    fi
    exit 0
}

run_pyspelling() {
    pip install pyspelling
    # Update paths to validation and lpot repositories
    VAL_REPO=${WORKSPACE}/lpot-validation

    sed -i "s|\${VAL_REPO}|$VAL_REPO|g" ${VAL_REPO}/deep-engine/engine_pyspelling_conf.yaml
    sed -i "s|\${SCAN_REPO}|$REPO_DIR|g" ${VAL_REPO}/deep-engine/engine_pyspelling_conf.yaml
    echo "Modified config:"
    cat ${VAL_REPO}/deep-engine/engine_pyspelling_conf.yaml
    pyspelling -c ${VAL_REPO}/deep-engine/engine_pyspelling_conf.yaml > ${WORKSPACE}/engine_pyspelling_output.log
    exit_code=$?
    if [ ${exit_code} -ne 0 ] ; then
        echo "Pyspelling exited with non-zero exit code."; exit 1
    fi
    exit 0
}

run_cloc() {
    cloc --include-lang=Python --csv --out=${WORKSPACE}/code_lines_summary.csv ${REPO_DIR}/neural_compressor
}

main
