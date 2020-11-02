credential = "5da0b320-00b8-4312-b653-36d4cf980fcb"

node_label = "clx8280"
if ('node_label' in params && params.node_label != '') {
    node_label = params.node_label
}
echo "Running on node ${node_label}"

conda_env = "HOSTNAME"
if ('conda_env' in params && params.conda_env != '') {
    conda_env = params.conda_env
}
echo "Running ut on ${conda_env}"

ilit_url="https://gitlab.devtools.intel.com/chuanqiw/auto-tuning.git"
if ('ilit_url' in params && params.ilit_url != ''){
    ilit_url = params.ilit_url
}
echo "ilit_url is ${ilit_url}"

nigthly_test_branch = ''
MR_source_branch = ''
MR_target_branch = ''
if ('nigthly_test_branch' in params && params.nigthly_test_branch != '') {
    nigthly_test_branch = params.nigthly_test_branch

}else{
    MR_source_branch = params.MR_source_branch
    MR_target_branch = params.MR_target_branch
}
echo "nigthly_test_branch: $nigthly_test_branch"
echo "MR_source_branch: $MR_source_branch"
echo "MR_target_branch: $MR_target_branch"

binary_build_job="lastSuccessfulBuild"
if ('binary_build_job' in params && params.binary_build_job != ''){
    binary_build_job = params.binary_build_job
}
echo "binary_build_job is ${binary_build_job}"


python_version = "3.6"
if ('python_version' in params && params.python_version != '') {
    python_version = params.python_version
}
echo "Python version: ${python_version}"

// setting tensorflow_version
tensorflow_version = '1.15.2'
if ('tensorflow_version' in params && params.tensorflow_version != '') {
    tensorflow_version = params.tensorflow_version
}
echo "tensorflow_version: ${tensorflow_version}"

// setting mxnet_version
mxnet_version = '1.6.0'
if ('mxnet_version' in params && params.mxnet_version != '') {
    mxnet_version = params.mxnet_version
}
echo "mxnet_version: ${mxnet_version}"

// setting pytorch_version
pytorch_version = '1.5.0+cpu'
if ('pytorch_version' in params && params.pytorch_version != '') {
    pytorch_version = params.pytorch_version
}
echo "pytorch_version: ${pytorch_version}"

torchvision_versions = [
    "1.6.0": "0.7.0",
    "1.5.1": "0.6.1",
    "1.5.0": "0.6.0",
    "1.4.0": "0.5.0",
    "1.3.1": "0.4.2",
    "1.3.0": "0.4.1",
    "1.2.0": "0.4.0",
    "1.1.0": "0.3.0",
]

pytorch_version_base = pytorch_version.split('\\+')[0]
try {
    pytorch_version_postfix = pytorch_version.split('\\+')[1]
} catch(e) {
    pytorch_version_postfix = ""
}

torchvision_version = torchvision_versions[pytorch_version_base]

if (!torchvision_version) {
    error("Could not found torchvision for pytorch " + pytorch_version)
}

if (pytorch_version_postfix != "") {
    torchvision_version = torchvision_version + "+" + pytorch_version_postfix
}
println("torchvision_version: " + torchvision_version)

def cleanup() {

    try {
        sh '''#!/bin/bash -x
        cd $WORKSPACE
        sudo rm -rf *
        '''
    } catch(e) {
        echo "==============================================="
        echo "ERROR: Exception caught in cleanup()           "
        echo "ERROR: ${e}"
        echo "==============================================="

        echo ' '
        echo "Error while doing cleanup"
    }

}

def download() {
    dir(WORKSPACE) {
        retry(5) {
            checkout scm

            if(MR_source_branch != '') {
                checkout changelog: true, poll: true, scm: [
                        $class                           : 'GitSCM',
                        branches                         : [[name: "${MR_source_branch}"]],
                        browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                        doGenerateSubmoduleConfigurations: false,
                        extensions                       : [
                                [$class: 'RelativeTargetDirectory', relativeTargetDir: "ilit-models"],
                                [$class: 'CloneOption', timeout: 60],
                                [$class: 'PreBuildMerge', options: [fastForwardMode: 'FF', mergeRemote: 'origin', mergeStrategy: 'DEFAULT', mergeTarget: "${MR_target_branch}"]]
                        ],
                        submoduleCfg                     : [],
                        userRemoteConfigs                : [
                                [credentialsId: "${credential}",
                                url          : "${ilit_url}"]
                        ]
                ]
            }
            else {
                checkout changelog: true, poll: true, scm: [
                        $class                           : 'GitSCM',
                        branches                         : [[name: "${nigthly_test_branch}"]],
                        browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                        doGenerateSubmoduleConfigurations: false,
                        extensions                       : [
                                [$class: 'RelativeTargetDirectory', relativeTargetDir: "ilit-models"],
                                [$class: 'CloneOption', timeout: 60]
                        ],
                        submoduleCfg                     : [],
                        userRemoteConfigs                : [
                                [credentialsId: "${credential}",
                                url          : "${ilit_url}"]
                        ]
                ]
            }
        }
    }
}

node(node_label){
    try{
        cleanup()
        stage('download'){
            download()
        }
        stage('copy binary'){
            catchError {
                copyArtifacts(
                        projectName: 'iLiT-release-wheel-build',
                        selector: specific("${binary_build_job}"),
                        filter: 'ilit*.whl',
                        fingerprintArtifacts: true,
                        target: "${WORKSPACE}")

                archiveArtifacts artifacts: "ilit*.whl"
            }
        }
        stage('env_build'){
            withEnv(["torchvision_version=${torchvision_version}"]) {
                retry(5) {
                    sh'''#!/bin/bash
                        set -xe
                        echo "Create new conda env for UT..."
                        export PATH=${HOME}/miniconda3/bin/:$PATH
                        # pip config set global.index-url https://pypi.douban.com/simple/

                        if [ $(conda info -e | grep ${conda_env} | wc -l) != 0 ]; then
                            conda remove --name ${conda_env} --all -y

                            conda_dir=$(dirname $(dirname $(which conda)))
                            if [ -d ${conda_dir}/envs/${conda_env} ]; then
                                rm -rf ${conda_dir}/envs/${conda_env}
                            fi
                        fi

                        conda create python=${python_version} -y -n ${conda_env}

                        source activate ${conda_env}

                        # Upgrade pip
                        pip install -U pip

                        # Install TF
                        if [ ${tensorflow_version} == '1.15UP1' ]; then
                            if [ ${python_version} == '3.6' ]; then
                                pip install https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up1-cp36-cp36m-manylinux2010_x86_64.whl                
                            elif [ ${python_version} == '3.7' ]; then
                                pip install https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up1-cp37-cp37m-manylinux2010_x86_64.whl
                            elif [ ${python_version} == '3.5' ]; then
                                pip install https://storage.googleapis.com/intel-optimized-tensorflow/intel_tensorflow-1.15.0up1-cp35-cp35m-manylinux2010_x86_64.whl
                            else
                                echo "!!! TF 1.15UP1 do not support ${python_version}"
                            fi
                        elif [ ${tensorflow_version} == '2.4.0' ]; then
                            wheel_dir=/tf_dataset/tensorflow/wheel
                            if [ ${python_version} == '3.6' ]; then
                                pip install ${wheel_dir}/intel_tensorflow-2.4.0-cp36-cp36m-manylinux2010_x86_64.whl
                        elif [ ${python_version} == '3.7' ]; then
                                pip install ${wheel_dir}/intel_tensorflow-2.4.0-cp37-cp37m-manylinux2010_x86_64.whl
                            else
                                echo "!!! local build TF 2.4.0 do not support ${python_version}"
                            fi
                        else
                            pip install intel-tensorflow==${tensorflow_version}
                        fi

                        # Install PyTorch
                        pip install torch==${pytorch_version} -f https://download.pytorch.org/whl/torch_stable.html
                        pip install torchvision==${torchvision_version} -f https://download.pytorch.org/whl/torch_stable.html

                        # Install MXNet
                        if [ ${mxnet_version} == '1.6.0' ]; then
                            pip install mxnet-mkl==${mxnet_version}
                        elif [ ${mxnet_version} == '1.7.0' ]; then
                            pip install mxnet==${mxnet_version}.post1
                        else
                            pip install mxnet==${mxnet_version}
                        fi
                    '''
                }
            }
        }
        stage('unit test') {
            timeout(30) {
                echo "+---------------- unit test ----------------+"
                sh '''#!/bin/bash
                    export PATH=${HOME}/miniconda3/bin/:$PATH
                    source activate ${conda_env}
                    # pip config set global.index-url https://pypi.douban.com/simple/
                    
                    echo "Checking ilit..."
                    python -V
                    pip list
                    c_ilit=$(pip list | grep -c 'ilit') || true  # Prevent from exiting when 'ilit' not found
                    if [ ${c_ilit} != 0 ]; then
                        pip uninstall ilit -y
                        pip list
                    fi
                                    
                    echo "Install iLiT binary..."
                    pip install ilit*.whl
                
                    if [ ! -d ${WORKSPACE}/ilit-models ]; then
                        echo "\\"ilit-model\\" not found. Exiting..."
                        exit 1
                    fi
                    
                    echo -e "\\nInstalling ut requirements..."
                    cd ${WORKSPACE}/ilit-models/test
                    if [ -f "requirements.txt" ]; then
                        sed -i '/^ilit/d' requirements.txt
                        sed -i '/^intel-tensorflow/d' requirements.txt
                        sed -i '/find-links https:\\/\\/download.pytorch.org\\/whl\\/torch_stable.html/d' requirements.txt
                        sed -i '/^torch/d' requirements.txt
                        sed -i '/^mxnet-mkl/d' requirements.txt

                        n=0
                        until [ "$n" -ge 5 ]
                        do
                        python -m pip install -r requirements.txt && break
                        n=$((n+1))
                        sleep 5
                        done

                        pip list
                    else
                        echo "Not found requirements.txt file."
                    fi
                
                    find . -name "test*.py" | sed 's/.\\//python /g' > run.sh
                    ut_log_name=${WORKSPACE}/unit_test.log
                    bash run.sh 2>&1 | tee ${ut_log_name}
                    if [ $(grep -c "FAILED" ${ut_log_name}) != 0 ] || [ $(grep -c "OK" ${ut_log_name}) == 0 ];then
                        exit 1
                    fi
                '''
            }

        }

    }catch (e) {
        // If there was an exception thrown, the build failed
        currentBuild.result = "FAILURE"
        throw e
    }finally {
        // archive artifacts
        stage("Artifacts") {
            archiveArtifacts artifacts: '*.log,*/*.log', excludes: null
            fingerprint: true
        }
    }
}
