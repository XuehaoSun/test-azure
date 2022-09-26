@NonCPS
def jsonParse(def json) {
    new groovy.json.JsonSlurperClassic().parseText(json)
}

credential = 'c09d6555-5e41-4b99-bf90-50f518319b49'


// parameters
// setting node_label
sub_node_label = params.sub_node_label ?: "lpot"
echo "Running on node ${sub_node_label}"

test_mode = params.test_mode ?: "pre-CI"
echo "test mode ${test_mode}"

python_version = params.python_version ?: "3.8"
echo "python_version: ${python_version}"

//other settings
nlp_url = params.nlp_url ?: "https://github.com/intel-innersource/frameworks.ai.nlp-toolkit.intel-nlp-toolkit.git"
echo "nlp_url is ${nlp_url}"

nlp_branch = ''
MR_source_branch = ''
MR_target_branch = ''
if ('nlp_branch' in params && params.nlp_branch != '') {
    nlp_branch = params.nlp_branch
}else{
    MR_source_branch = params.MR_source_branch
    MR_target_branch = params.MR_target_branch
}
echo "nlp_branch: $nlp_branch"
echo "MR_source_branch: $MR_source_branch"
echo "MR_target_branch: $MR_target_branch"
val_branch = params.val_branch ?: "main"
echo "val_branch: ${val_branch}"

upstreamBuild = ""
upstreamJobName = ""
upstreamUrl = ""
MAX_RERUNS = 3
@NonCPS
def getUpstreamInfo() {
    def upstream_job = currentBuild.rawBuild.getCause(hudson.model.Cause$UpstreamCause)
    if (!upstream_job) {
        return
    }
    println("Found upstream job. Updating info...")
    upstreamJobName = upstream_job.upstreamProject
    upstreamBuild = upstream_job.upstreamBuild
    upstreamUrl = upstream_job.upstreamUrl
}

def cleanup() {
    try {
        sh '''#!/bin/bash 
        set -x
        cd $WORKSPACE
        rm -rf *
        rm -rf .git
        # set perf BKC
        cat /sys/devices/system/cpu/intel_pstate/no_turbo
        lscpu
        cat /proc/sys/kernel/numa_balancing
        '''
    } catch(e) {
        echo "==============================================="
        echo "ERROR: Exception caught in cleanup()           "
        echo "ERROR: ${e}"
        echo "==============================================="
        echo "Error while doing cleanup"
    }  // catch
}

node( sub_node_label ) {
    getUpstreamInfo()
    println("upstreamBuild = ${upstreamBuild}")
    println("upstreamJobName = ${upstreamJobName}")
    println("upstreamUrl = ${upstreamUrl}")

    cleanup()
    dir('lpot-validation') {
        retry(5) {
            checkout scm
        }
    }

    try {
        try {
            stage("Download") {
                retry(5) {
                    if(MR_source_branch != ''){
                        checkout changelog: true, poll: true, scm: [
                                $class                           : 'GitSCM',
                                branches                         : [[name: "${MR_source_branch}"]],
                                browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                                doGenerateSubmoduleConfigurations: false,
                                extensions                       : [
                                        [$class: 'RelativeTargetDirectory', relativeTargetDir: "lpot-models"],
                                        [$class: 'CloneOption', timeout: 5],
                                        [$class: 'PreBuildMerge', options: [fastForwardMode: 'FF', mergeRemote: 'origin', mergeStrategy: 'DEFAULT', mergeTarget: "${MR_target_branch}"]]
                                ],
                                submoduleCfg                     : [],
                                userRemoteConfigs                : [
                                        [credentialsId: "${credential}",
                                        url          : "${nlp_url}"]
                                ]
                        ]
                    }
                    else {
                        checkout changelog: true, poll: true, scm: [
                                $class                           : 'GitSCM',
                                branches                         : [[name: "${nlp_branch}"]],
                                browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                                doGenerateSubmoduleConfigurations: false,
                                extensions                       : [
                                        [$class: 'RelativeTargetDirectory', relativeTargetDir: "lpot-models"],
                                        [$class: 'CloneOption', timeout: 5]
                                ],
                                submoduleCfg                     : [],
                                userRemoteConfigs                : [
                                        [credentialsId: "${credential}",
                                        url          : "${nlp_url}"]
                                ]
                        ]
                    }
                }
            }
            stage("benchmarking") {
                output_log_dir="${WORKSPACE}/benchmark_log"
                sh """#!/bin/bash -x
                    conda_env_name="sparse_lib"
                    if [[ ! \$(conda info -e | grep \$conda_env_name) ]]; then
                        conda create -n \$conda_env_name python=3.8 -y
                    fi
                    bash -x ${WORKSPACE}/lpot-validation/sparse_lib/run_ci_trigger.sh ${output_log_dir}
                """
            }
        } catch(e) {
            currentBuild.result = "FAILURE"
            throw e
        } 
    } catch(e) {
        currentBuild.result = "FAILURE"
        throw e
    } finally {
        // save log files
        stage("Archive Artifacts") {
            archiveArtifacts artifacts: "*.log, benchmark_log/*", excludes: null
            fingerprint: true
        }
    }  
}
