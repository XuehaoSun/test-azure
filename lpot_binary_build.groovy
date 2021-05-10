credential = "lab_tfbot"

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

lpot_url=""
if ('lpot_url' in params && params.lpot_url != ''){
    lpot_url = params.lpot_url
}
echo "lpot_url is ${lpot_url}"

lpot_branch = ''
MR_source_branch = ''
MR_target_branch = ''
if ('lpot_branch' in params && params.lpot_branch != '') {
    lpot_branch = params.lpot_branch

}else{
    MR_source_branch = params.MR_source_branch
    MR_target_branch = params.MR_target_branch
}
echo "lpot_branch: $lpot_branch"
echo "MR_source_branch: $MR_source_branch"
echo "MR_target_branch: $MR_target_branch"

binary_class = "wheel"
if ('binary_class' in params && params.binary_class != ''){
    binary_class = params.binary_class
}
echo "binary_class is ${binary_class}"

conda_version = "v1.1"
if ('conda_version' in params && params.conda_version != ''){
    conda_version = params.conda_version
}
echo "conda_version is ${conda_version}"

pypi_version = "default"
if ('pypi_version' in params && params.pypi_version != ''){
    pypi_version = params.pypi_version
}
echo "pypi_version is ${pypi_version}"

val_branch="master"
if ('val_branch' in params && params.val_branch != ''){
    val_branch=params.val_branch
}
echo "val_branch: ${val_branch}"

tuning_precision="default"
if ('tuning_precision' in params && params.tuning_precision != ''){
    tuning_precision=params.tuning_precision
}
echo "tuning_precision: ${tuning_precision}"

def cleanup() {

    try {
        sh '''#!/bin/bash -x
        cd $WORKSPACE
        sudo rm -rf *
        sudo rm -rf .git
        git config --global user.email "lab_tfbot@intel.com"
        git config --global user.name "lab_tfbot" 
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
            dir('lpot-validation') {
                checkout scm
            }

            if(MR_source_branch != ''){
                checkout changelog: true, poll: true, scm: [
                        $class                           : 'GitSCM',
                        branches                         : [[name: "${MR_source_branch}"]],
                        browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                        doGenerateSubmoduleConfigurations: false,
                        extensions                       : [
                                [$class: 'RelativeTargetDirectory', relativeTargetDir: "lpot-models"],
                                [$class: 'CloneOption', timeout: 60],
                                [$class: 'PreBuildMerge', options: [fastForwardMode: 'FF', mergeRemote: 'origin', mergeStrategy: 'DEFAULT', mergeTarget: "${MR_target_branch}"]]
                        ],
                        submoduleCfg                     : [],
                        userRemoteConfigs                : [
                                [credentialsId: "${credential}",
                                url          : "${lpot_url}"]
                        ]
                ]
            }
            else {
                checkout changelog: true, poll: true, scm: [
                        $class                           : 'GitSCM',
                        branches                         : [[name: "${lpot_branch}"]],
                        browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                        doGenerateSubmoduleConfigurations: false,
                        extensions                       : [
                                [$class: 'RelativeTargetDirectory', relativeTargetDir: "lpot-models"],
                                [$class: 'CloneOption', timeout: 60]
                        ],
                        submoduleCfg                     : [],
                        userRemoteConfigs                : [
                                [credentialsId: "${credential}",
                                url          : "${lpot_url}"]
                        ]
                ]
            }
        }
    }
}

def do_binary_build() {
    if (binary_class == 'wheel') {
        withEnv(["tuning_precision=${tuning_precision}",
                "pypi_version=${pypi_version}"]) {
            sh '''#!/bin/bash
                set -xe
                echo "Create conda env..."
                export PATH=${HOME}/miniconda3/bin/:$PATH
                if [ $(conda info -e | grep ${conda_env} | wc -l) != 0 ]; then
                    echo "${conda_env} exist!"
                else
                    conda create python=3.6.9 -y -n ${conda_env}
                fi
    
                source activate ${conda_env}
    
                # Upgrade pip
                pip install -U pip
    
                echo "Build Pypi binary..."
                cd lpot-models
                if [ "${tuning_precision}" != "default" ]; then
                    sed -i "s/names: int8, uint8, bf16, fp32/names: ${tuning_precision}/g" lpot/adaptor/tensorflow.yaml
                    echo "lpot/adaptor/tensorflow.yaml..."
                    cat lpot/adaptor/tensorflow.yaml
                fi
                if [ "${pypi_version}" != "default" ]; then
                    cd lpot
                    sed -i '/__version__ =/d' version.py
                    sed -i '$a\\__version__ = \\"'$pypi_version'\\"' version.py
                    cat version.py
                    cd -
                fi
                python3 setup.py sdist bdist_wheel
                cp dist/lpot*.whl ${WORKSPACE}/
                cp dist/lpot*.tar.gz ${WORKSPACE}/
            '''
        }
    } else if (binary_class == 'conda') {
        sh '''#!/bin/bash
            set -xe
            echo "Create conda env..."
            export PATH=${HOME}/miniconda3/bin/:$PATH
            if [ $(conda info -e | grep ${conda_env} | wc -l) != 0 ]; then
                conda remove --name ${conda_env} --all -y
            fi
            
            conda_dir=$(dirname $(dirname $(which conda)))
            if [ -d ${conda_dir}/envs/${conda_env} ]; then
              rm -rf ${conda_dir}/envs/${conda_env}
            fi
            
            conda create python=${python_version} -y -n ${conda_env}

            source activate ${conda_env}

            # Upgrade pip
            pip install -U pip

            echo "Build Pypi binary..."
            cd lpot-models
            python3 setup.py sdist bdist_wheel
            cp dist/lpot*.whl ${WORKSPACE}/
            
            echo "Build Conda binary..."
            conda clean -a -y
            export LPOT_WHL=${WORKSPACE}/lpot*.whl
            pip install pyyaml six 
            conda install patchelf conda-build conda-verify -y
            conda config --add channels conda-forge 
            conda build meta.yaml
            cp /home/tensorflow/miniconda3/envs/${conda_env}/conda-bld/noarch/lpot-*-py_0.tar.bz2 ${WORKSPACE}/
        '''

    } else {
        echo "DO NOT support ${binary_class} build!!!"
    }
}

node(node_label){
    try{
        cleanup()
        stage("download"){
            download()
        }
        stage("binary build") {
            echo "+---------------- ${binary_class} build ----------------+"
            retry(3){
                do_binary_build()
            }
        }
    }catch(e){
        currentBuild.result = "FAILURE"
        throw e
    }finally {
        // archive artifacts
        stage("Artifacts") {
            archiveArtifacts artifacts: 'lpot*.whl, lpot-*-py_0.tar.bz2, lpot-*.tar.gz', excludes: null
            fingerprint: true
        }
    }
}
