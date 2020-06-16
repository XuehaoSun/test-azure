
teamforge_credential = '5da0b320-00b8-4312-b653-36d4cf980fcb'

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

// setting node_label
sub_node_label = "ilit"
if ('node_label' in params && params.sub_node_label != '') {
    sub_node_label = params.sub_node_label
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

ilit_url="https://gitlab.devtools.intel.com/chuanqiw/auto-tuning.git"
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
    if ("${gitlabSourceBranch}" != '') {
        MR_source_branch = "${gitlabSourceBranch}"
        MR_target_branch = "${gitlabTargetBranch}"
    }
}
echo "nigthly_test_branch: $nigthly_test_branch"
echo "MR_source_branch: $MR_source_branch"
echo "MR_target_branch: $MR_target_branch"

email_subject="${test_title}"
if ( MR_source_branch != ''){
    email_subject="MR${gitlabMergeRequestIid}: ${test_title}"
}else {
    email_subject="Nightly: ${test_title}"
}

echo "email_subject: $email_subject"

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

def BuildParams(job_framework, framework_version, job_model){
    List ParamsPerJob = []

    ParamsPerJob += string(name: "sub_node_label", value: "${sub_node_label}")
    ParamsPerJob += string(name: "framework", value: "${job_framework}")
    ParamsPerJob += string(name: "framework_version", value: "${framework_version}")
    ParamsPerJob += string(name: "model", value: "${job_model}")
    ParamsPerJob += string(name: "validation_branch", value: "${validation_branch}")
    ParamsPerJob += string(name: "ilit_url", value: "${ilit_url}")
    ParamsPerJob += string(name: "nigthly_test_branch", value: "${nigthly_test_branch}")
    ParamsPerJob += string(name: "MR_source_branch", value: "${MR_source_branch}")
    ParamsPerJob += string(name: "MR_target_branch", value: "${MR_target_branch}")

    return ParamsPerJob
}

def doBuild() {

    def jobs = [:]

    job_frameworks = Frameworks.split(',')

    job_frameworks.each { job_framework ->
        job_models = []
        framework_version = ''
        if (job_framework == 'tensorflow'){
            //job_models=eval("${job_framework}_models")
            job_models = tensorflow_models.split(',')
            framework_version = tensorflow_version
        }else if (job_framework == 'pytorch'){
            job_models = pytorch_models.split(',')
            framework_version = pytorch_version
        }else if (job_framework == 'mxnet'){
            job_models = mxnet_models.split(',')
            framework_version = mxnet_version
        }
        job_models.each { job_model ->
            jobs["${job_framework}_${job_model}"] = {
                catchError {
                    stage("Run Model ${job_model} on ${job_framework}") {
                        // execute build
                        echo "${job_model}, ${job_framework}"
                        def downstreamJob = build job: "run-ilit-tuner", propagate: false, parameters: BuildParams(job_framework, framework_version, job_model)

                        if (downstreamJob.getResult() == 'SUCCESS') {
                            catchError {

                                copyArtifacts(
                                        projectName: "run-ilit-tuner",
                                        selector: specific("${downstreamJob.getNumber()}"),
                                        filter: '*.log',
                                        fingerprintArtifacts: true,
                                        target: "${job_framework}/${job_model}")

                                // Archive in Jenkins
                                archiveArtifacts artifacts: "${job_framework}/${job_model}/**"
                            }
                        }
                    }
                }
            }
        }
    }

    parallel jobs

}

def collectLog() {

    echo "---------------------------------------------------------"
    echo "------------  running collectLog  -------------"
    echo "---------------------------------------------------------"

    job_frameworks = Frameworks.split(',')
    job_frameworks.each { job_framework ->
        job_models = []
        if (job_framework == 'tensorflow'){
            job_models = tensorflow_models.split(',')
        }else if (job_framework == 'pytorch'){
            job_models = pytorch_models.split(',')
        }else if (job_framework == 'mxnet'){
            job_models = mxnet_models.split(',')
        }
        job_models.each { job_model ->
            withEnv(["current_model=$job_model","current_framework=$job_framework"]) {

                sh '''#!/bin/bash -x
                    cd $WORKSPACE
                    chmod 775 ./scripts/collect_logs_ilit.sh
                    ./scripts/collect_logs_ilit.sh --model=${current_model} --framework=${current_framework}                
                '''
            }
        }
    }
    echo "done running collectLog ......."
    stash allowEmpty: true, includes: "*.log", name: "logfile"

}

node( node_label ) {

    try {
        cleanup()
        checkout scm

        SUMMARYTXT = "${WORKSPACE}/summary.log"
        writeFile file: SUMMARYTXT, text: "Framework;Platform;Model;BS;Value;Url\n"

        stage("tune-parallel") {
            doBuild()
        }

        stage("Collect Logs") {
            collectLog()
        }

        stage("report"){
            dir(WORKSPACE) {
                sh'''#!/bin/bash
                    summaryLog="${WORKSPACE}/summary.log"
                    
                    chmod 775 ./scripts/generate_ilit_report.sh
                    ./scripts/generate_ilit_report.sh 
                '''
            }
        }

        // stage("send email") {
        //     dir("$WORKSPACE") {
        //         if (MR_branch != '') {
        //             recipient_list = 'suyue.chen@intel.com,' + "${gitlabUserEmail}"
        //             if ('recipient_list' in params && params.recipient_list != '') {
        //                 recipient_list = params.recipient_list + ',' + gitlabUserEmail
        //             }
        //         } else {
        //             recipient_list = 'suyue.chen@intel.com'
        //             if ('recipient_list' in params && params.recipient_list != '') {
        //                 recipient_list = params.recipient_list
        //             }
        //         }
        // 
        //         echo "Running ${models}"
        //         emailext subject: "${email_subject}",
        //                 to: "${recipient_list}",
        //                 replyTo: "${recipient_list}",
        //                 body: '''${FILE,path="report.html"}''',
        //                 attachmentsPattern: "",
        //                 mimeType: 'text/html'
        // 
        //     }
        // }

    } catch (e) {
        // If there was an exception thrown, the build failed
        currentBuild.result = "FAILED"
        throw e

    } finally {

        // archive artifacts
        stage("Artifacts") {
            archiveArtifacts artifacts: '*.log,*.html,**/*.log', excludes: null
            fingerprint: true
        }
    }
}