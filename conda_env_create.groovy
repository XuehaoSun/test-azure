credential = "lab_tfbot"

// setting node_label
node_label = "ilit"
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

ilit_url="https://gitlab.devtools.intel.com/intelai/LowPrecisionInferenceTool.git"
if ('ilit_url' in params && params.ilit_url != ''){
    ilit_url = params.ilit_url
}
echo "ilit_url is ${ilit_url}"

ilit_branch="v1.0a_rc"
if ('ilit_branch' in params && params.ilit_branch != ''){
    ilit_branch = params.ilit_branch
}
echo "ilit_branch is ${ilit_branch}"

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
            checkout changelog: true, poll: true, scm: [
                    $class                           : 'GitSCM',
                    branches                         : [[name: "${ilit_branch}"]],
                    browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                    doGenerateSubmoduleConfigurations: false,
                    extensions                       : [
                            [$class: 'RelativeTargetDirectory', relativeTargetDir: "iLit"],
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

    stage("build"){
            sh'''#!/bin/bash

                export PATH=${HOME}/miniconda3/bin/:$PATH
                pip config set global.index-url https://pypi.tuna.tsinghua.edu.cn/simple
                conda_env_name=${framework}-${framework_version}
                if [ $(conda info -e | grep ${conda_env_name} | wc -l) == 0 ]; then
                    # conda create python=3.6.9 -y -n ${conda_env_name}
                    retry_num=0
                    while true
                    do
                        tmp_status=$(conda create python=3.6.9 -y -n ${conda_env_name} > /dev/null 2>&1 && echo $? || echo $?)
                    
                        retry_num=$[ $retry_num + 1 ]
                        echo $retry_num
                    
                        if [ $tmp_status -eq 0 -o $retry_num -ge 5 ];then
                            break
                        fi
                    done
                else    
                    if [ ${refresh_env} = true ]; then
                        conda remove --name ${conda_env_name} --all -y

                        # conda create python=3.6.9 -y -n ${conda_env_name}
                        retry_num=0
                        while true
                        do

	                        tmp_status=$(conda create python=3.6.9 -y -n ${conda_env_name} > /dev/null 2>&1 && echo $? || echo $?)
	
	                        retry_num=$[ $retry_num + 1 ]
	                        echo $retry_num
	
	                        if [ $tmp_status -eq 0 -o $retry_num -ge 5 ];then
		                        break
	                        fi

                        done
                    fi
                fi
                
                source activate ${conda_env_name}
                
                if [ ${requirement_only} = false ]; then
                    if [ ${framework} == 'tensorflow' ]; then
                        pip install intel-${framework}==${framework_version}
                    elif [ ${framework} == 'pytorch' ]; then
                        pip install torch==1.5.0+cpu -f https://download.pytorch.org/whl/torch_stable.html
                        cd ${WORKSPACE}/iLit/examples/pytorch/vision
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


