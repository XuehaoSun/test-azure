credential = "c09d6555-5e41-4b99-bf90-50f518319b49"

@NonCPS
def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}

node_label = "non-perf"
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

benchmark_config="4:1,28:64,28:1,7:64,7:2"
if ('benchmark_config' in params && params.benchmark_config != ''){
    benchmark_config=params.benchmark_config
}
echo "benchmark_config: ${benchmark_config}"

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

conda_env = "deep-engine-benchmark"
if ('conda_env' in params && params.conda_env != '') {
    conda_env = params.conda_env
}
echo "Running ut on ${conda_env}"

binary_build_job=""
if ('binary_build_job' in params && params.binary_build_job != ''){
    binary_build_job = params.binary_build_job
}
echo "binary_build_job is ${binary_build_job}"

python_version = "3.6"
if ('python_version' in params && params.python_version != '') {
    python_version = params.python_version
}
echo "Python version: ${python_version}"

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
                            [$class: 'CloneOption', timeout: 10],
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
                            [$class: 'CloneOption', timeout: 10]
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

def build_env() {
    catchError {
        copyArtifacts(
                projectName: 'lpot-release-wheel-build',
                selector: specific("${binary_build_job}"),
                filter: 'neural_compressor*.whl',
                fingerprintArtifacts: true,
                target: "${WORKSPACE}")
    }
    timeout(10){
        sh(returnStatus: true, script: '''#!/bin/bash
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
            pip install ${WORKSPACE}/neural_compressor*.whl
            echo "Print components list after install inc..."
            pip list
        ''')
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
        stage('Env setup'){
            retry(3){
                build_env()
            }
        }
        stage('benchmark'){
            model_list_split=model_list.split(',')
            model_list_split.each { each_model ->
                def modelConf = jsonParse(readFile("$WORKSPACE/lpot-validation/deep-engine/config/model_list.json"))."${each_model}"
                def seq_len = modelConf."seq_len"
                seq_len.each { each_seq_len ->
                    benchmark_config.split(',').each { each_ben_conf ->
                        def ncores_per_instance = each_ben_conf.split(':')[0]
                        def bs = each_ben_conf.split(':')[1]
                        precision.split(',').each { each_precision ->
                            def weight = modelConf."${each_precision}"."weight"
                            def config = modelConf."${each_precision}"."config"
                            if (bs==1){
                                if (each_precision=='int8'){
                                    config = modelConf."${each_precision}"."config_latency"
                                }else{
                                    return
                                }
                            }
                            config = "${WORKSPACE}/deep-engine/${config}"
                            timeout(120) {
                                sh """#!/bin/bash -x
                                echo "Running ----${each_model}, ${each_seq_len}, ${weight}, ${config}, ${ncores_per_instance},${bs},${each_precision} ---- Benchmark"
                                
                                echo "=======cache clean======="
                                sudo bash ${WORKSPACE}/lpot-validation/scripts/cache_clean.sh
                                echo "========================="
                                export PATH=${HOME}/miniconda3/bin/:$PATH
                                source activate ${conda_env}
                                bash ${WORKSPACE}/lpot-validation/deep-engine/scripts/launch_benchmark.sh ${each_model} ${each_seq_len} ${ncores_per_instance} ${bs} ${config} ${weight} ${each_precision}
                                """
                            }
                        }
                    }
                }
            }

            // check benchmark status
            sh'''#!/bin/bash
                log_file='${WORKSPACE}/summary'
                for line in $(grep 'throughput' $log_file)
                do 
                echo $line
                throughput=$(echo $line| cut -f8 -d',')
                if [[ $throughput = '' ]]; then
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
            archiveArtifacts artifacts: 'summary, bert*/**, cmake_build.log', excludes: null
            fingerprint: true
        }
    }
}