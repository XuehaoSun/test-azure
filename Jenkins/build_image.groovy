import groovy.json.*
import hudson.model.*
import jenkins.model.*

credential = 'c09d6555-5e41-4b99-bf90-50f518319b49'
sys_lpot_val_credentialsId = "dcf0dff2-03fb-45b0-9e64-5b4db466bee5"
DOCKER_CREDENTIALS_ID = "cc6f7aca-13bb-4a07-97f0-b2d9a9fe171b"
DOCKER_REGISTRY_ADDRESS = "ccr-registry.caas.intel.com"
DOCKER_REGISTRY_PROJECT = "lpot"


// setting node_label
node_label = "lpot && clx8280 && docker"
if ('node_label' in params && params.node_label != '') {
    node_label = params.node_label
}
echo "Running on node ${node_label}"

// setting operating_system
operating_system = "centos7"
if ('operating_system' in params && params.operating_system != '') {
    operating_system = params.operating_system
}
echo "Building ${operating_system} image"

// setting IMAGE_TAG
IMAGE_TAG = "latest"
if ('IMAGE_TAG' in params && params.IMAGE_TAG != '') {
    IMAGE_TAG = params.IMAGE_TAG
}
echo "Building image with tag: ${IMAGE_TAG}"

// setting tensorflow_version
tensorflow_version = '2.6.0'
if ('tensorflow_version' in params && params.tensorflow_version != '') {
    tensorflow_version = params.tensorflow_version
}
echo "tensorflow_version: ${tensorflow_version}"

// setting mxnet_version
mxnet_version = '1.7.0'
if ('mxnet_version' in params && params.mxnet_version != '') {
    mxnet_version = params.mxnet_version
}
echo "mxnet_version: ${mxnet_version}"

// setting pytorch_version
pytorch_version = '1.9.0+cpu'
if ('pytorch_version' in params && params.pytorch_version != '') {
    pytorch_version = params.pytorch_version
}
echo "pytorch_version: ${pytorch_version}"

// setting torchvision_version
torchvision_version = '0.10.0+cpu'
if ('torchvision_version' in params && params.torchvision_version != '') {
    torchvision_version = params.torchvision_version
}
echo "torchvision_version: ${torchvision_version}"

// setting onnx_version
onnx_version = '1.9.0'
if ('onnx_version' in params && params.onnx_version != '') {
    onnx_version = params.onnx_version
}
echo "onnx_version: ${onnx_version}"

// setting onnxruntime version
onnxruntime_version = '1.8.0'
if ('onnxruntime_version' in params && params.onnxruntime_version != '') {
    onnxruntime_version = params.onnxruntime_version
}
println("onnxruntime_version: " + onnxruntime_version)

lpot_url="https://github.com/intel-innersource/frameworks.ai.lpot.intel-lpot"
if ('lpot_url' in params && params.lpot_url != ''){
    lpot_url = params.lpot_url
}
echo "lpot_url is ${lpot_url}"

lpot_branch = 'master'
if ('lpot_branch' in params && params.lpot_branch != '') {
    lpot_branch = params.lpot_branch
}
echo "lpot_branch: $lpot_branch"

python_version = "3.6"
if ('python_version' in params && params.python_version != '') {
    python_version = params.python_version
}
echo "Python version: ${python_version}"
val_branch="master"
if ('val_branch' in params && params.val_branch != ''){
    val_branch=params.val_branch
}
echo "val_branch: ${val_branch}"

def Cleanup() {
    try {
        sh '''#!/bin/bash -x
        cd $WORKSPACE
        rm -rf *
        rm -rf .git
        sudo rm -rf *
        sudo rm -rf .git
        git config --global user.email "sys_lpot_val@intel.com"
        git config --global user.name "sys-lpot-val"
        '''
    } catch(e) {
        echo "==============================================="
        echo "ERROR: Exception caught in cleanup()           "
        echo "ERROR: ${e}"
        echo "==============================================="

        echo ' '
        echo "Error while doing cleanup"
    }  // catch

}

def Download() {
    retry(5) {
        checkout changelog: true, poll: true, scm: [
                $class                           : 'GitSCM',
                branches                         : [[name: "${lpot_branch}"]],
                browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                doGenerateSubmoduleConfigurations: false,
                extensions                       : [
                        [$class: 'RelativeTargetDirectory', relativeTargetDir: "lpot-models"],
                        [$class: 'CloneOption', timeout: 5]
                ],
                submoduleCfg                     : [],
                userRemoteConfigs                : [
                        [credentialsId: "${credential}",
                        url          : "${lpot_url}"]
                ]
        ]
    }
}

def Prepare() {
    sh """
        #!/bin/bash
        # GCC
        cp -r /tf_dataset/utils/gcc .

        # Datasets
        mkdir -p datasets/COCORecord
        cp -r /tf_dataset/tensorflow/mini-coco-100.record datasets/COCORecord/mini-coco-100.record
        cp -r /tf_dataset/dataset/TF_mini_imagenet datasets/ImageRecord
        cp -r /tf_dataset2/datasets/mnist/FashionMNIST_small datasets/FashionMNIST
        cp -r /tf_dataset2/datasets/mini-imageraw datasets/ImageFolder
    """
}

def DockerLogin(registryAddress, credentialsId) {
    withCredentials([usernamePassword(credentialsId: "${credentialsId}",
                                          usernameVariable: 'DOCKER_USERNAME',
                                          passwordVariable: 'DOCKER_PASSWORD')]) {
        sh """
            #!/bin/bash
            echo \${DOCKER_PASSWORD} | docker login ${registryAddress} --username ${DOCKER_USERNAME} --password-stdin
        """
    }
}

def BuildImage(
        operating_system,
        python_version,
        tensorflow_version,
        pytorch_version,
        torchvision_version,
        mxnet_version,
        onnx_version,
        onnxruntime_version
    ) {
        image_name = "${DOCKER_REGISTRY_ADDRESS}/${DOCKER_REGISTRY_PROJECT}/${operating_system}"
        sh """
            docker build \
                --build-arg PYTHON_VERSION=${python_version} \
                --build-arg TENSORFLOW_VERSION=${tensorflow_version} \
                --build-arg PYTORCH_VERSION=${pytorch_version} \
                --build-arg TORCHVISION_VERSION=${torchvision_version} \
                --build-arg MXNET_VERSION=${mxnet_version} \
                --build-arg ONNX_VERSION=${onnx_version} \
                --build-arg ONNXRUNTIME_VERSION=${onnxruntime_version} \
                -f "${WORKSPACE}/lpot-validation/Jenkins/dockerfiles/${operating_system}/${operating_system}.dockerfile" \
                -t "${image_name}:${IMAGE_TAG}" \
                .
        """

    }

def PushImage(operating_system) {
    sh """
        docker push "${image_name}:${IMAGE_TAG}"
    """
}


node( node_label ) {
    try {
        stage("Cleanup") {
            Cleanup()
        }

        stage("Clone lpot-validation repository") {
            dir('lpot-validation') {
                retry(5) {
                    checkout scm
                }
            }
        }

        stage("Clone INC repository") {
            Download()
        }

        stage("Prepare data") {
            Prepare()
        }

        stage("Docker login") {
            DockerLogin(DOCKER_REGISTRY_ADDRESS, DOCKER_CREDENTIALS_ID)
        }

        stage("Build image") {
            BuildImage(
                operating_system,
                python_version,
                tensorflow_version,
                pytorch_version,
                torchvision_version,
                mxnet_version,
                onnx_version,
                onnxruntime_version
            )
        }

        stage("Push image"){
            PushImage(operating_system)
        }
    } catch (e) {
        // If there was an exception thrown, the build failed
        currentBuild.result = "FAILURE"
        error(e.toString())
    }
}
