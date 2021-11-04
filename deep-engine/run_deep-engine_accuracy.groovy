credential = "c09d6555-5e41-4b99-bf90-50f518319b49"

@NonCPS
def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}

node_label = ""
if ('node_label' in params && params.node_label != '') {
    node_label = params.node_label
}
echo "Running on node ${node_label}"

deepengine_url="git@github.com:intel-innersource/frameworks.ai.deep-engine.intel-deep-engine.git"
if ('deepengine_url' in params && params.deepengine_url != ''){
    deepengine_url = params.deepengine_url
}
echo "deepengine_url is ${deepengine_url}"

deepengine_branch = ''
PR_source_branch = ''
PR_target_branch = ''
if ('deepengine_branch' in params && params.deepengine_branch != '') {
    deepengine_branch = params.deepengine_branch

}else{
    PR_source_branch = params.PR_source_branch
    PR_target_branch = params.PR_target_branch
}
echo "deepengine_branch: $deepengine_branch"
echo "PR_source_branch: $PR_source_branch"
echo "PR_target_branch: $PR_target_branch"

precision="int8,fp32"
if ('precision' in params && params.precision != ''){
    precision=params.precision
}
echo "precision: ${precision}"

model_list=''
if ('model_list' in params && params.model_list != ''){
    model_list=params.model_list
}
echo "model_list: ${model_list}"

conda_env = "deep-engine-accuracy"
if ('conda_env' in params && params.conda_env != '') {
    conda_env = params.conda_env
}
echo "Running ut on ${conda_env}"

def cleanup() {
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
    }
}

def download() {
    retry(5) {
        if(PR_source_branch != ''){
            checkout changelog: true, poll: true, scm: [
                    $class                           : 'GitSCM',
                    branches                         : [[name: "${PR_source_branch}"]],
                    browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                    doGenerateSubmoduleConfigurations: false,
                    extensions                       : [
                            [$class: 'RelativeTargetDirectory', relativeTargetDir: "deep-engine"],
                            [$class: 'CloneOption', timeout: 5],
                            [$class: 'PreBuildMerge', options: [fastForwardMode: 'FF', mergeRemote: 'origin', mergeStrategy: 'DEFAULT', mergeTarget: "${PR_target_branch}"]]
                    ],
                    submoduleCfg                     : [],
                    userRemoteConfigs                : [
                            [credentialsId: "${credential}",
                             url          : "${deepengine_url}"]
                    ]
            ]
        }
        else {
            checkout changelog: true, poll: true, scm: [
                    $class                           : 'GitSCM',
                    branches                         : [[name: "${deepengine_branch}"]],
                    browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                    doGenerateSubmoduleConfigurations: false,
                    extensions                       : [
                            [$class: 'RelativeTargetDirectory', relativeTargetDir: "deep-engine"],
                            [$class: 'CloneOption', timeout: 5]
                    ],
                    submoduleCfg                     : [],
                    userRemoteConfigs                : [
                            [credentialsId: "${credential}",
                             url          : "${deepengine_url}"]
                    ]
            ]
        }
    }
}

node(node_label){
    try{
        cleanup()
        dir('lpot-validation') {
            checkout scm
        }
        stage('download') {
            download()
        }
        stage('build'){
            timeout(30){
                echo "+---------------- CMake build ----------------+"
                build_status = sh(returnStatus: true, script: '''#!/bin/bash
                    export PATH=${HOME}/miniconda3/bin/:$PATH
                    if [ $(conda info -e | grep ${conda_env} | wc -l) != 0 ]; then
                        echo "${conda_env} exist!"
                    else
                        conda create python=3.7 -y -n ${conda_env}
                    fi
                    source activate ${conda_env}
                    pip install cmake

                    if [ ! -d ${WORKSPACE}/deep-engine ]; then
                        echo "\\"deep-engine\\" not found. Exiting..."
                        exit 1
                    fi
                    
                    export PATH=/usr/local/gcc-9.4/bin:$PATH
                    export LD_LIBRARY_PATH=/usr/local/gcc-9.4/lib64:$LD_LIBRARY_PATH
                    export CC=/usr/local/gcc-9.4/bin/gcc
                    export CXX=/usr/local/gcc-9.4/bin/g++
                    cd ${WORKSPACE}/deep-engine/deep_engine/executor
                    mkdir build && cd build && cmake .. && make -j 2>&1|tee $WORKSPACE/cmake_build.log
                ''')
                if (build_status != 0) {
                    currentBuild.result = 'FAILURE'
                    error("CMake build failed!")
                }
            }
        }

        stage('accuracy'){
            model_list_split=model_list.split(',')
            model_list_split.each { each_model ->
                def modelConf =  jsonParse(readFile("$WORKSPACE/lpot-validation/deep-engine/config/model_list.json"))."${each_model}"
                def dataset = modelConf."dataset_location"
                precision.split(',').each { each_precision ->
                    def weight = modelConf."${each_precision}"."weight"
                    def config = modelConf."${each_precision}"."config"
                    config="${WORKSPACE}/deep-engine/${config}"
                    if (each_model == "bert_mlperf_loadgen"){
                        timeout(30){
                            sh"""#!/bin/bash -x
                            echo "Running ----${each_model}, ${weight}, ${config}, ${each_precision} ---- Accuracy"
                            bash ${WORKSPACE}/lpot-validation/deep-engine/scripts/launch_bert_large_loadgen.sh accuracy ${each_model} ${weight} ${config} 0 0 ${each_precision}  
                            """
                        }
                    }else{
                        timeout(30){
                            sh"""#!/bin/bash -x

                            echo "Running ----${each_model}, ${dataset}, ${weight}, ${config}, ${each_precision} ---- Accuracy"
                            
                            export PATH=${HOME}/miniconda3/bin/:$PATH
                            source activate ${conda_env}
                            pip install six numpy
                            
                            cd ${WORKSPACE}/deep-engine/deep_engine/examples/bert_base_mrpc
                            cp ${WORKSPACE}/deep-engine/deep_engine/executor/build/engine_py.*.so .
                            cp ../mlperf_v1.1/vocab.txt .
                            mkdir -p ${WORKSPACE}/${each_model}
                            python run_engine.py --model=${config} --weight=${weight} --data_dir=${dataset} --do_lower_case 2>&1|tee ${WORKSPACE}/${each_model}/${each_model}_accuracy_${each_precision}.log
                            """
                            withEnv(["each_model=${each_model}","each_precision=${each_precision}"]){
                                sh '''#!/bin/bash
                                accuracy=`grep "acc:" ${WORKSPACE}/${each_model}/${each_model}_accuracy_${each_precision}.log | cut -d':' -f2`
                                echo "accuracy,${each_model},${each_precision},${accuracy}" >> ${WORKSPACE}/summary.log
                                '''
                            }
                        }
                    }
                }
            }

            // check accuracy status
            sh'''#!/bin/bash
                log_file='${WORKSPACE}/summary.log'
                for line in $(grep 'accuracy' $log_file)
                do 
                echo $line
                accuracy=$(echo $line| cut -f4 -d',')
                if [[ $accuracy = '' ]]; then
                    exit 1
                fi
                done
            '''
        }

    }catch (e) {
        // If there was an exception thrown, the build failed
        currentBuild.result = "FAILURE"
        throw e
    } finally {
        // archive artifacts
        stage("Artifacts") {
            archiveArtifacts artifacts: 'summary.log, bert*/**, cmake_build.log', excludes: null
            fingerprint: true
        }
    }
}