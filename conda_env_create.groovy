credential = "c09d6555-5e41-4b99-bf90-50f518319b49"

// setting node_label
node_label = ""
if ('node_label' in params && params.node_label != '') {
    node_label = params.node_label
}
echo "Running on node ${node_label}"

// test framework
framework = "tensorflow"
if ('framework' in params && params.framework != '') {
    framework = params.framework
}
echo "framework ${framework}"

// setting framework_version
framework_version  = "1.15.2"
if ('framework_version' in params && params.framework_version != '') {
    framework_version = params.framework_version
}
echo "framework_version ${framework_version}"

// pip install list
requirement_list = "numpy"
if ('requirement_list' in params && params.requirement_list != ''){
    requirement_list = params.requirement_list
}
echo "pip install list: ${requirement_list}"

lpot_url="https://gitlab.devtools.intel.com/intelai/LowPrecisionInferenceTool.git"
if ('lpot_url' in params && params.lpot_url != ''){
    lpot_url = params.lpot_url
}
echo "lpot_url is ${lpot_url}"

lpot_branch="v1.0a_rc"
if ('lpot_branch' in params && params.lpot_branch != ''){
    lpot_branch = params.lpot_branch
}
echo "lpot_branch is ${lpot_branch}"

refresh_env=false
if ('refresh_env' in params && params.refresh_env){
    echo "refresh_env is true"
    refresh_env=params.refresh_env
}
echo "refresh_env = ${refresh_env}"

requirement_only=false
if ('requirement_only' in params && params.requirement_only){
    echo "requirement_only is true"
    requirement_only=params.requirement_only
}
echo "requirement_only = ${requirement_only}"


node(node_label){

    stage("download"){
        if (framework == 'pytorch'){
            retry(5) {
                checkout changelog: true, poll: true, scm: [
                        $class                           : 'GitSCM',
                        branches                         : [[name: "${lpot_branch}"]],
                        browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                        doGenerateSubmoduleConfigurations: false,
                        extensions                       : [
                                [$class: 'RelativeTargetDirectory', relativeTargetDir: "lpot"],
                                [$class: 'CloneOption', timeout: 10]
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

    stage("build"){
        retry(5) {
            sh'''#!/bin/bash
                set -xe

                export PATH=${HOME}/miniconda3/bin/:$PATH
                # pip config set global.index-url https://pypi.tuna.tsinghua.edu.cn/simple
                conda_env_name=${framework}-${framework_version}-${python_version}
                conda config --add channels defaults
                if [ $(conda info -e | grep ${conda_env_name} | wc -l) != 0 ]; then
                    if [ ${refresh_env} = true ]; then
                        conda remove --name ${conda_env_name} --all -y

                        conda_dir=$(dirname $(dirname $(which conda)))
                        if [ -d ${conda_dir}/envs/${conda_env_name} ]; then
                            rm -rf ${conda_dir}/envs/${conda_env_name}
                        fi
                    fi
                    conda create python=3.6.9 -y -n ${conda_env_name}
                fi
                
                source activate ${conda_env_name}

                # Upgrade pip
                pip install -U pip

                if [ ${requirement_only} = false ]; then
                    if [ ${framework} == 'tensorflow' ]; then
                        pip install intel-${framework}==${framework_version}
                    elif [ ${framework} == 'pytorch' ]; then
                        pip install torch==1.5.0+cpu -f https://download.pytorch.org/whl/torch_stable.html
                        cd ${WORKSPACE}/lpot/examples/pytorch/vision
                        export PATH=${HOME}/gcc6.3/bin/:$PATH
                        export LD_LIBRARY_PATH=${HOME}/gcc6.3/lib64:$LD_LIBRARY_PATH
                        python setup.py install
                    elif [ ${framework} == 'mxnet' ]; then 
                        pip install ${framework}-mkl==${framework_version}
                    fi
                fi
                
                wait

                if [[ ${requirement_list} != '' ]]; then
                    pip install ${requirement_list}
                fi
                
                echo "pip list all the components------------->"
                pip list
                sleep 2
                echo "------------------------------------------"
            '''
        }
    }

}


