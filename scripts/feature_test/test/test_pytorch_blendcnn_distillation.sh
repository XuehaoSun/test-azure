#!/bin/bash
set -xe

PATTERN='[-a-zA-Z0-9_]*='
for i in "$@"
do
    case $i in
        --dataset_location=*)
            dataset_location=`echo $i | sed "s/${PATTERN}//"`;;
        --python_version=*)
            python_version=`echo $i | sed "s/${PATTERN}//"`;;
    esac
done

function main {
    # conda env
    create_conda_env
    # example
    cd ${WORKSPACE}/lpot-models/
    # execution
    blendcnn_distilling_log="${WORKSPACE}/blendcnn-distilling-test.log"
    distilling 2>&1 |tee ${blendcnn_distilling_log}
}

function create_conda_env {
    conda_env_name="blendcnn-distilling-test"

    if [ -f "${HOME}/miniconda3/etc/profile.d/conda.sh" ]; then
        . "${HOME}/miniconda3/etc/profile.d/conda.sh"
    else
        export PATH="${HOME}/miniconda3/bin:$PATH"
    fi
    conda remove --all -y -n ${conda_env_name}
    conda create python=${python_version} -y -n ${conda_env_name}
    conda activate ${conda_env_name}
    pip install -U pip

    # pip install -r requirements.txt
    pip install fire tqdm tensorflow==2.6.0
    pip install torch==1.6.0+cpu -f https://download.pytorch.org/whl/torch_stable.html

    # install inc
    pip install ${WORKSPACE}/neural_compressor*.whl

    # re-install pycocotools resolve the issue with numpy
    echo "re-install pycocotools resolve the issue with numpy..."
    pip uninstall pycocotools -y
    pip install --no-cache-dir pycocotools

    pip list

}

function distilling {
    cd examples/pytorch/nlp/blendcnn/distillation/eager
    # model and MRPC
    rsync -avz ${dataset_location}/ ./

    # fine-tune the pretrained BERT-Base model
    mkdir -p models/bert/mrpc
    python finetune.py config/finetune/mrpc/train.json

    # distilling the BlendCNN
    mkdir -p models/blendcnn/
    python distill.py --loss_weights 0.1 0.9
}

main
