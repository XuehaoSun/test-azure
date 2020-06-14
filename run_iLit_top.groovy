
// setting test_title
test_title = "iLit Tests"
if ('test_title' in params && params.test_title != '') {
    test_title = params.test_title
}
echo "Running named ${test_title}"

// setting node_label
node_label = "clx8280"
if ('node_label' in params && params.node_label != '') {
    node_label = params.node_label
}
echo "Running on node ${node_label}"

// chose test frameworks
Frameworks = "tensorflow,mxnet,pytorch"
if ('Frameworks' in params && params.Frameworks != '') {
    Frameworks = params.Frameworks
}
echo "Running ${Frameworks}"

// setting tensorflow_version
tensorflow_version = '1.15.2'
if ('tensorflow_version' in params && params.tensorflow_version != '') {
    tensorflow_version = params.tensorflow_version
}
echo "Running ${tensorflow_version}"

// setting tensorflow models
tensorflow_models = "ResNet-50v1.0"
if ('tensorflow_models' in params && params.tensorflow_models != '') {
    tensorflow_models = params.tensorflow_models
}
echo "Running ${tensorflow_models}"

// setting mxnet_version
mxnet_version = '1.15.2'
if ('mxnet_version' in params && params.mxnet_version != '') {
    mxnet_version = params.mxnet_version
}
echo "Running ${mxnet_version}"

// setting mxnet models
mxnet_models = "ResNet-50v1.0"
if ('mxnet_models' in params && params.mxnet_models != '') {
    mxnet_models = params.mxnet_models
}
echo "Running ${mxnet_models}"

// setting pytorch_version
pytorch_version = '1.15.2'
if ('pytorch_version' in params && params.pytorch_version != '') {
    pytorch_version = params.pytorch_version
}
echo "Running ${pytorch_version}"

// setting mxnet models
pytorch_models = "ResNet-50v1.0"
if ('pytorch_models' in params && params.pytorch_models != '') {
    pytorch_models = params.pytorch_models
}
echo "Running ${pytorch_models}"

// ilit-validation branch to get test groovy
validation_branch = 'master'
if ('validation_branch' in params && params.validation_branch != '') {
    validation_branch = params.validation_branch
}
echo "validation_branch: $validation_branch"

nigthly_test_branch = ''
MR_branch = ''
if ('nigthly_test_branch' in params && params.nigthly_test_branch != '') {
    nigthly_test_branch = params.nigthly_test_branch
}else{
    if ("${gitlabSourceBranch}" != '') {
        MR_branch = "${gitlabSourceBranch}"
        echo MR_branch
    }
}
echo "nigthly_test_branch: $nigthly_test_branch"
echo "MR_branch: $MR_branch"

email_subject="${test_title}"
if ( MR_branch != ''){
    email_subject="MR${gitlabMergeRequestIid}: ${test_title}"
}else {
    email_subject="Nightly: ${test_title}"
}

echo "email_subject: $email_subject"

Flake8_require='True'
if ('Flake8_require' in params && params.Flake8_require != '') {
    Flake8_require = params.Flake8_require
}
echo "Flake8_require: $Flake8_require"

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

def doBuild() {

    def jobs = [:]

    //job_models=models.split(',')
    job_frameworks=Frameworks.split(',')

    job_frameworks.each { job_framework ->
        if (job_framework == 'tensorflow'){
            //job_models=eval("${job_framework}_models")
            job_models=tensorflow_models
        }else if (job_framework == 'pytorch'){
            job_models=pytorch_models
        }else {
            job_models = mxnet_models
        }
        job_models.each { job_model ->
            jobs["${job_model}_${job_node}"] = {
                catchError {
                    stage("Run Models ${job_model} on ${job_node}") {
                        // execute build
                        echo "${job_model}, ${job_node}"
                        def downstreamJob = build job: "run-benchmark-intel-model-zoo-general", propagate: false, parameters: BuildParams(job_model,job_node)


                        if (downstreamJob.getResult() == 'SUCCESS') {
                            catchError {

                                copyArtifacts(
                                        projectName: "run-benchmark-intel-model-zoo-general",
                                        selector: specific("${downstreamJob.getNumber()}"),
                                        filter: '*.log',
                                        fingerprintArtifacts: true,
                                        target: "${job_model}/")

                                // Archive in Jenkins
                                archiveArtifacts artifacts: "${job_model}/*"
                            }
                        }
                    }
                }
            }
        }
    }

    parallel jobs

}

node( NODE_LABEL ) {

    try {
        cleanup()

        // pull the cje-tf
        dir('ilit-validation') {
            checkout([
                    $class                           : 'GitSCM',
                    branches                         : [[name: validation_branch]],
                    browser                          : [$class: 'AssemblaWeb', repoUrl: ''],
                    doGenerateSubmoduleConfigurations: false,
                    extensions                       : [
                            [$class: 'RelativeTargetDirectory', relativeTargetDir: "ilit_validation"],
                            [$class: 'CloneOption', timeout: 60]
                    ],
                    submoduleCfg                     : [],
                    userRemoteConfigs                : [
                            [credentialsId: "${teamforge_credential}",
                             url          : "https://gitlab.devtools.intel.com/suyueche/ilit-validation.git"]
                    ]
            ])
        }

        SUMMARYTXT = "${WORKSPACE}/summary.log"
        writeFile file: SUMMARYTXT, text: "Framework;Platform;Model;BS;Value;Url\n"

        stage("tune-parallel") {
            doBuild()
        }

        stage("Collect Logs") {

            withEnv(["tensorflow_dir=${GIT_NAME}"]) {
                sh '''#!/bin/bash -x
                    # get git commit info 
                    python ${WORKSPACE}/cje-tf/scripts/get_tf_sourceinfo.py --tensorflow_dir=${tensorflow_dir} --workspace_dir=${WORKSPACE}
                '''
            }

            // Prepare logs
            def prepareLog = load("${CJE_TF_COMMON_DIR}/prepareLog.groovy")
            prepareLog(INTEL_MODELS_BRANCH, "", "", SUMMARYLOG, SUMMARY_TITLE)

            collectBenchmarkLog(MODELS, MODES, SINGLE_SOCKET, DATA_TYPE)
        }

    } catch (e) {
        // If there was an exception thrown, the build failed
        currentBuild.result = "FAILED"
        throw e

    } finally {

        // Success or failure, always send notifications
        withEnv(["SUMMARYLOG=$SUMMARYLOG"]) {
            echo "$SUMMARYLOG"
            def msg = readFile SUMMARYLOG

            def notifyBuild = load("${CJE_TF_COMMON_DIR}/slackNotification.groovy")
            notifyBuild(SLACK_CHANNEL, currentBuild.result, msg)
        }

        stage('Archive Artifacts ') {
            dir("$WORKSPACE") {
                archiveArtifacts artifacts: '*.log, */*.log', excludes: null
                fingerprint: true

            }
        }

    }  //try

    echo "===== ${env.BUILD_URL}, ${env.JOB_NAME},${env.BUILD_NUMBER} ====="
}