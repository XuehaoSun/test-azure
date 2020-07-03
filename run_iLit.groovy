// Groovy 

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

ilit_url="https://gitlab.devtools.intel.com/intelai/LowPrecisionInferenceTool"
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
    if ("${MR_source_branch}" != '') {
        MR_source_branch = params.MR_source_branch
        MR_target_branch = params.MR_target_branch
    }
}
echo "nigthly_test_branch: $nigthly_test_branch"
echo "MR_source_branch: $MR_source_branch"
echo "MR_target_branch: $MR_target_branch"


if(framework == 'pytorch'){
    label=model.split('_')
    if(label[0] == 'bert'){
        sub_node_label='py-bert'
    }
    if(model == 'dlrm'){
        sub_node_label='dlrm'
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

node( sub_node_label ) {

    cleanup()
    dir('ilit-validation') {
        checkout scm
    }

    try {

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
        }

        stage("Performance") {
            sh '''#!/bin/bash -x
                echo "Running ---- ${framework}, ${model} ----"
                # copy examples
                rm -rf ${WORKSPACE}/ilit-models/examples
                cp -r ${WORKSPACE}/ilit-validation/examples ${WORKSPACE}/ilit-models/
                echo "-------w-------"
                w
                echo "-------w-------"
                bash ${WORKSPACE}/ilit-validation/scripts/run_${framework}.sh \
                    --model=${model} \
                    --conda_env_name=${framework}-${framework_version} \
                    2>&1 | tee ${framework}-${model}.log
            '''
        }

        stage("check status"){
            dir("${WORKSPACE}"){
                sh '''#!/bin/bash -x
                    if [ $(grep 'Found a quantized model which meet accuracy goal.' ${framework}-${model}.log | wc -l) == 0 ];then
                        exit 1
                    fi
                '''
            }
        }

    } catch(e) {
        currentBuild.result = "FAILED"
        throw e
    } finally {

        // save log files
        stage("Archive Artifacts") {
            archiveArtifacts artifacts: "${framework}-${model}.log", excludes: null
            fingerprint: true
        }
    }
    
}
