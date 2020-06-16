// Groovy 

credential = '5da0b320-00b8-4312-b653-36d4cf980fcb'

// parameters



// currentBuild.displayName = node_label
currentBuild.description = framework + '-' + model

// mxnet point to specific node for dataset
if (framework == "mxnet"){
    sub_node_label="inteltf-clx8280-102.sh.intel.com"
}

node( sub_node_label ) {

    deleteDir()
    checkout scm

    try {

        stage("Download") {
            if(MR_source_branch != ''){
                checkout changelog: true, poll: true, scm: [
                        $class                           : 'GitSCM',
                        branches                         : [[name: "${MR_source_branch}"]],
                        browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                        doGenerateSubmoduleConfigurations: false,
                        extensions                       : [
                                [$class: 'RelativeTargetDirectory', relativeTargetDir: "iLit"],
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
            sh '''#!/bin/bash
                echo "Running ---- ${framework}, ${model} ----"
                bash -x ${WORKSPACE}/scripts/run_${framework}.sh \
                    --model=${model} \
                    --conda_env_name=${framework}-${framework_version} \
                    > ${WORKSPACE}/${framework}-${model}.log 2>&1 
            '''
        }
        
    } catch(e) {
        throw e
    } finally {

        // save log files
        stage("Archive Artifacts") {
            archiveArtifacts artifacts: "*.log", excludes: null
            fingerprint: true
        }
    }
    
}
