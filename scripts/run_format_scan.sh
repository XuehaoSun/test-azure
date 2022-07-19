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
    source activate lpot-format_scan-${python_version}-${CPU_NAME}
    pip -V
    python -V
    pip install -U pip

    # Install LPOT requirements
    cd ${REPO_DIR}
    if [ -f "requirements.txt" ]; then
        python -m pip install --default-timeout=100 -r requirements.txt
        pip list
    else
        echo "Not found requirements.txt file."
    fi

    # Install test requirements
    cd ${REPO_DIR}/test
    if [ -f "requirements.txt" ]; then
        sed -i '/neural-compressor/d;/tensorflow==/d;/torch==/d;/pytorch-ignite$/d;/mxnet==/d;/mxnet-mkl==/d;/torchvision==/d;/onnx$/d;/onnx==/d;/onnxruntime$/d;/onnxruntime==/d' requirements.txt
        python -m pip install --default-timeout=100 -r requirements.txt
        pip list
    else
        echo "Not found requirements.txt file."
    fi

    echo "Executing code scan on branch: $(git name-rev --name-only HEAD)."
    cd ${REPO_DIR}
    echo "Code scan working path ${REPO_DIR} ..."

    case ${SCAN_TOOL} in
        "pylint") run_pylint;;
        "bandit") run_bandit;;
        "pyspelling") run_pyspelling;;
        "cloc") run_cloc;;
        "pydocstyle") run_pydocstyle;;
        *)
            echo "Scan tool ${SCAN_TOOL} not supported."; exit 1;;
    esac
}

run_pylint() {
    pip install pylint==2.12.1
    # tf_utils.util will import some deps installed by tensorflow
    pip install intel-tensorflow
    python -m pylint -f json --disable=R,C,W,E1129 --enable=line-too-long --max-line-length=120 --extension-pkg-whitelist=numpy --ignored-classes=TensorProto,NodeProto --ignored-modules=tensorflow,torch,torch.quantization,torch.tensor,torchvision,mxnet,onnx,onnxruntime ./neural_compressor > ${WORKSPACE}/lpot-pylint.json
    exit_code=$?
    if [ ${exit_code} -ne 0 ] ; then
        echo "PyLint exited with non-zero exit code."; exit 1
    fi
    exit 0
}

run_bandit() {
    pip install bandit
    python -m bandit -r -lll -iii neural_compressor > ${WORKSPACE}/lpot-bandit.log
    exit_code=$?
    if [ ${exit_code} -ne 0 ] ; then
        echo "Bandit exited with non-zero exit code."; exit 1
    fi
    exit 0
}

run_pyspelling() {
    pip install pyspelling
    # Update paths to validation and lpot repositories
    VAL_REPO=${WORKSPACE}

    sed -i "s|\${VAL_REPO}|$VAL_REPO|g" ${VAL_REPO}/pyspelling_conf.yaml
    sed -i "s|\${LPOT_REPO}|$REPO_DIR|g" ${VAL_REPO}/pyspelling_conf.yaml
    echo "Modified config:"
    cat ${VAL_REPO}/pyspelling_conf.yaml
    pyspelling -c ${VAL_REPO}/pyspelling_conf.yaml > ${WORKSPACE}/pyspelling_output.log
    exit_code=$?
    if [ ${exit_code} -ne 0 ] ; then
        echo "Pyspelling exited with non-zero exit code."; exit 1
    fi
    exit 0
}

run_cloc() {
    cloc --include-lang=Python --csv --out=${WORKSPACE}/nc_code_lines_summary.csv ${REPO_DIR}/neural_compressor
}

run_pydocstyle() {
    pip install pydocstyle
    pydocstyle --convention=google ${REPO_DIR}/neural_compressor > ${WORKSPACE}/docstring.log
}

main
