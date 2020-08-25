@NonCPS
def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}

credential = '5da0b320-00b8-4312-b653-36d4cf980fcb'

currentBuild.description = framework + '-' + model

// parameters
// setting node_label
sub_node_label = "ilit"
if ('sub_node_label' in params && params.sub_node_label != '') {
    sub_node_label = params.sub_node_label
}
echo "Running on node ${sub_node_label}"

// test framework
framework = "tensorflow"
if ('framework' in params && params.framework != '') {
    framework = params.framework
}
echo "framework: ${framework}"

// setting framework_version
framework_version  = '1.15.2'
if ('framework_version' in params && params.framework_version != '') {
    framework_version = params.framework_version
}
echo "framework_version: ${framework_version}"

// model
model  = 'resnet50'
if ('model' in params && params.model != '') {
    model = params.model
}
echo "Running ${model}"

precision  = 'int8,fp32'
if ('precision' in params && params.precision != '') {
    precision = params.precision
}
def precision_list = parseStrToList(precision)
echo "Running ${precision}"

mode  = 'accuracy,throughput,latency'
if ('mode' in params && params.mode != '') {
    mode = params.mode
}
def mode_list = parseStrToList(mode)
echo "Running ${mode}"

ilit_url="https://gitlab.devtools.intel.com/intelai/LowPrecisionInferenceTool"
if ('ilit_url' in params && params.ilit_url != ''){
    ilit_url = params.ilit_url
}
echo "ilit_url is ${ilit_url}"

requirement_list="ruamel.yaml"
if ('requirement_list' in params && params.requirement_list != ''){
    requirement_list = params.requirement_list
}
echo "requirement_list is ${requirement_list}"

python_version="3.6"
if ('python_version' in params && params.python_version != ''){
    python_version = params.python_version
}
echo "python_version is ${python_version}"

strategy="basic"
if ('strategy' in params && params.strategy != ''){
    strategy = params.strategy
}
echo "strategy is ${strategy}"

binary_build_job="lastSuccessfulBuild"
if ('binary_build_job' in params && params.binary_build_job != ''){
    binary_build_job = params.binary_build_job
}
echo "binary_build_job is ${binary_build_job}"

test_mode="nightly"
if ('test_mode' in params && params.test_mode != ''){
    test_mode = params.test_mode
}
echo "test_mode is ${test_mode}"

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

def new_conda_env=true
if(framework == 'pytorch'){
    label=model.split('_')
    if(label[0] == 'bert'){
        sub_node_label='py-bert'
        new_conda_env=false
    }
    if(model == 'dlrm'){
        sub_node_label='dlrm'
        new_conda_env=false
    }
}

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
    }  // catch

}

def parseStrToList(srtingElements, delimiter=',') {
    if (srtingElements == ''){
        return []
    }
    return srtingElements[0..srtingElements.length()-1].tokenize(delimiter)
}

def create_conda_env(){
    withEnv(["framework=${framework}","framework_version=${framework_version}","python_version=${python_version}",
             "requirement_list=${requirement_list}"]) {
        sh '''#!/bin/bash -xe

            export PATH=${HOME}/miniconda3/bin/:$PATH
            pip config set global.index-url https://pypi.douban.com/simple/
            conda_env_name=${framework}-${framework_version}-${python_version}
            if [ $(conda info -e | grep ${conda_env_name} | wc -l) == 0 ]; then
                conda create python=${python_version} -y -n ${conda_env_name}
            else    
                conda remove --name ${conda_env_name} --all -y
                conda create python=${python_version} -y -n ${conda_env_name}
            fi
        
            source activate ${conda_env_name}
        
            if [ ${framework} == 'tensorflow' ]; then     
                if [ ${framework_version} == '1.15UP1' ]; then
                    if [ ${python_version} == '3.6' ]; then
                        pip install /tf_dataset/tensorflow/tensorflow-1.15.0-cp36-cp36m-linux_x86_64.whl                
                    else
                        echo "!!! TF 1.15UP1 do not support ${python_version}"
                    fi
                else
                    pip install intel-${framework}==${framework_version}
                fi
            elif [ ${framework} == 'pytorch' ]; then
                pip install torch==${framework_version} -f https://download.pytorch.org/whl/torch_stable.html
            elif [ ${framework} == 'mxnet' ]; then 
                pip install ${framework}-mkl==${framework_version}
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

node( sub_node_label ) {

    cleanup()
    dir('ilit-validation') {
        checkout scm
    }

    try {

        stage("Build"){
            if (new_conda_env){
                retry(3){
                    create_conda_env()
                }
            }else{
                println("Test need a special local conda env, DO NOT create again!!!")
            }

        }

        stage("Download") {
            if(MR_source_branch != ''){
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

            // copy ilit binary
            catchError {
                copyArtifacts(
                        projectName: 'iLiT-release-wheel-build',
                        selector: specific("${binary_build_job}"),
                        filter: 'ilit*.whl',
                        fingerprintArtifacts: true,
                        target: "${WORKSPACE}")

            }

        }

        // get params for tuning and benchmark
        def modelConf =  jsonParse(readFile("$WORKSPACE/ilit-validation/config/model_params_new.json"))
        model_src_dir = modelConf."${framework}"."${model}"."model_src_dir"
        dataset_location = modelConf."${framework}"."${model}"."dataset_location"
        input_model = modelConf."${framework}"."${model}"."input_model"
        yaml = modelConf."${framework}"."${model}"."yaml"
        println("test_mode = " + test_mode)
        if ( test_mode != 'weekly'){
            strategy = modelConf."${framework}"."${model}"."strategy"
        }

        timeout="timeout 21600"
        if (nigthly_test_branch == ''){
            timeout="timeout 5400"
            // use mini dataset for tf mr test
            if (framework == "tensorflow" && model == "resnet50v1.0"){
                dataset_location = "/tf_dataset/dataset/TF_mini_imagenet"
                dir("${WORKSPACE}/ilit-models/examples/${framework}/${model_src_dir}"){
                    sh (
                            script: 'sed -i \'s/IMAGENET_NUM_VAL_IMAGES = 50000/IMAGENET_NUM_VAL_IMAGES = 1000/\' datasets.py',
                            returnStdout: true
                    ).trim()
                }
            }
        }
        echo "Tuning timeout ${timeout}"
        stage("Tuning") {

            sh """#!/bin/bash -x
                echo "Running ---- ${framework}, ${model}, ${strategy} ----Tuning"
                
                echo "-------w-------"
                w
                echo "-------w-------"
                ${timeout} bash ${WORKSPACE}/ilit-validation/scripts/run_tuning_trigger.sh \
                    --framework=${framework} \
                    --model=${model} \
                    --model_src_dir=${WORKSPACE}/ilit-models/examples/${framework}/${model_src_dir} \
                    --dataset_location=${dataset_location} \
                    --input_model=${input_model} \
                    --yaml=${yaml} \
                    --strategy=${strategy} \
                    --conda_env_name=${framework}-${framework_version}-${python_version} \
                    2>&1 | tee ${framework}-${model}-tune.log
            """
        }

        if (nigthly_test_branch == ''){
            if (model == "resnet50v1.5" || model == "resnet50v1"){
                batch_size = modelConf."${framework}"."${model}"."batch_size"
                stage("MR Performance") {
                    precision_list.each {precision ->
                        echo "precision is ${precision}"
                            sh """#!/bin/bash -x
                            echo "Running ---- ${framework}, ${model},${precision},throughput ---- Benchmarking"
                            
                            echo "-------w-------"
                            w
                            echo "-------w-------"
                            echo "=======cache clean======="
                            
                            sudo bash ${WORKSPACE}/ilit-validation/scripts/cache_clean.sh
            
                            echo "=======cache clean======="
                            bash ${WORKSPACE}/ilit-validation/scripts/run_dummy_inference.sh \
                                --framework=${framework} \
                                --model=${model} \
                                --input_model=${input_model} \
                                --precision=${precision} \
                                --batch_size=${batch_size} \
                                --conda_env_name=${framework}-${framework_version}-${python_version}
                        """
                        }
                    }
                }
            }

        if (nigthly_test_branch != '' && framework != "pytorch"){
            batch_size = modelConf."${framework}"."${model}"."batch_size"
            stage("Performance") {
                precision_list.each { precision ->
                    echo "precision is ${precision}"
                    mode_list.each { mode ->
                        echo "mode is ${mode}"
                        sh """#!/bin/bash -x
                            echo "Running ---- ${framework}, ${model},${precision},${mode} ---- Benchmarking"
                            
                            echo "-------w-------"
                            w
                            echo "-------w-------"
                            echo "=======cache clean======="
                            
                            sudo bash ${WORKSPACE}/ilit-validation/scripts/cache_clean.sh
            
                            echo "=======cache clean======="
                            bash ${WORKSPACE}/ilit-validation/scripts/run_benchmark_trigger.sh \
                                --framework=${framework} \
                                --model=${model} \
                                --model_src_dir=${WORKSPACE}/ilit-models/examples/${framework}/${model_src_dir} \\
                                --dataset_location=${dataset_location} \
                                --input_model=${input_model} \
                                --precision=${precision} \
                                --mode=${mode} \
                                --batch_size=${batch_size} \
                                --conda_env_name=${framework}-${framework_version}-${python_version}
                        """
                    }
                }
            }
        }

        stage("Check status"){
            dir("${WORKSPACE}"){
                sh '''#!/bin/bash -x
                    if [ $(grep 'Found a quantized model which meet accuracy goal.' ${framework}-${model}-tune.log | wc -l) == 0 ];then
                        exit 1
                    fi
                '''
            }
        }

    } catch(e) {
        currentBuild.result = "FAILURE"
        throw e
    } finally {

        // save log files
        stage("Archive Artifacts") {
            archiveArtifacts artifacts: "${framework}*.log", excludes: null
            fingerprint: true
        }
    }
    
}
