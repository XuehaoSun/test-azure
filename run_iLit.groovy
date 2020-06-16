// Groovy 

teamforge_credential = '5da0b320-00b8-4312-b653-36d4cf980fcb'

// currentBuild.displayName = node_label
currentBuild.description = framework + '-' + model

node( node_label ) {

    deleteDir()
    checkout scm

    try {

        if(model_src_dir == "") {
            stage("Download") {
                MODELS: {
                    model_ver = '*/master'
                    if('model_branch' in params && params.model_branch != '') {
                        model_ver = params.model_branch
                    }
                    if('model_commit' in params && params.model_commit != '') {
                        model_ver = params.model_commit
                    }
                    echo "---- ${model_repo} with ${model_ver} ----"
                    checkout([
                        $class: 'GitSCM',
                        branches: [[name: "${model_ver}" ]],
                        browser: [$class: 'AssemblaWeb', repoUrl: ''],
                        doGenerateSubmoduleConfigurations: false,
                        extensions: [
                            [$class: 'RelativeTargetDirectory', relativeTargetDir: "ilit-models"],
                            [$class: 'CloneOption', timeout: 60]
                        ],
                        submoduleCfg: [],
                        userRemoteConfigs: [
                            [credentialsId: "${teamforge_credential}", 
                                url: "${model_repo}" ]
                        ]
                    ])
                    
                    model_src_dir = "${WORKSPACE}/ilit-models/examples/mxnet/rn50/"
                }
            }
        }

        stage("Performance") {
            sh '''#!/bin/bash
                echo "Running ---- ${framework}, ${model} ----"
                bash -x ${WORKSPACE}/scripts/run_${framework}.sh \
                    --model=${model} \
                    --mode=${mode} \
                    --precision=${precision} \
                    --batch_size=${batch_size} \
                    --cores_per_instance=${cores_per_instance} \
                    --numa_nodes_use=${numa_nodes_use} \
                    --cores_per_node=${cores_per_node} \
                    --model_src_dir=${model_src_dir} \
                    --conda_env_name=${conda_env_name} \
                    --in_graph=${in_graph} \
                    --data_location=${data_location} \
                    --data_shape=$data_shape \
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
