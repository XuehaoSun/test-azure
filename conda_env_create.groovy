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

node(node_label){

    stage("build"){
            sh'''#!/bin/bash

                export PATH=${HOME}/miniconda3/bin/:$PATH
                pip config set global.index-url https://pypi.tuna.tsinghua.edu.cn/simple
                conda_env_name=${framework}-${framework_version}
                if [ $(conda info -e | grep ${conda_env_name} | wc -l) != 0 ]; then
                    conda remove --name ${conda_env_name} --all -y
                fi
                conda create python=3.6.9 -y -n ${conda_env_name}
                source activate ${conda_env_name}
                
                if [ ${framework} == 'tensorflow' ]; then
                    pip install intel-${framework}==${framework_version}
                elif [ ${framework} == 'pytorch' ]; then
                    pip install torch==1.5.0+cpu torchvision==0.6.0+cpu -f https://download.pytorch.org/whl/torch_stable.html
                else 
                    pip install ${framework}==${framework_version}
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


