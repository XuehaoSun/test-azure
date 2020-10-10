credential = "5da0b320-00b8-4312-b653-36d4cf980fcb"

node_label = "clx8280"
if ('node_label' in params && params.node_label != '') {
    node_label = params.node_label
}
echo "Running on node ${node_label}"

conda_env = "HOSTNAME"
if ('conda_env' in params && params.conda_env != '') {
    conda_env = params.conda_env
}
echo "Running ut on ${conda_env}"

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
    MR_source_branch = params.MR_source_branch
    MR_target_branch = params.MR_target_branch
}
echo "nigthly_test_branch: $nigthly_test_branch"
echo "MR_source_branch: $MR_source_branch"
echo "MR_target_branch: $MR_target_branch"

binary_build_job="lastSuccessfulBuild"
if ('binary_build_job' in params && params.binary_build_job != ''){
    binary_build_job = params.binary_build_job
}
echo "binary_build_job is ${binary_build_job}"


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
    }

}

def download() {
    dir(WORKSPACE) {
        retry(5) {
            checkout scm

            if(MR_source_branch != '') {
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
    }
}

node(node_label){
    try{
        cleanup()
        stage('download'){
            download()
        }
        stage('copy binary'){
            catchError {
                copyArtifacts(
                        projectName: 'iLiT-release-wheel-build',
                        selector: specific("${binary_build_job}"),
                        filter: 'ilit*.whl',
                        fingerprintArtifacts: true,
                        target: "${WORKSPACE}")

                archiveArtifacts artifacts: "ilit*.whl"
            }
        }
        stage('env_build'){
            sh'''#!/bin/bash
            
            echo "Nightly Create new conda env for UT..."
            export PATH=${HOME}/miniconda3/bin/:$PATH
            pip config set global.index-url https://pypi.douban.com/simple/
            if [ $(conda info -e | grep ${conda_env} | wc -l) == 0 ]; then
                # conda create python=3.6.9 -y -n ${conda_env}
                retry_num=0
                while true
                do
                    tmp_status=$(conda create python=3.6.9 -y -n ${conda_env} > /dev/null 2>&1 && echo $? || echo $?)
                
                    retry_num=$[ $retry_num + 1 ]
                    echo $retry_num
                
                    if [ $tmp_status -eq 0 -o $retry_num -ge 5 ];then
                        break
                    fi
                done
            else    
                conda remove --name ${conda_env} --all -y
                # conda create python=3.6.9 -y -n ${conda_env}
                retry_num=0
                while true
                do
                    tmp_status=$(conda create python=3.6.9 -y -n ${conda_env} > /dev/null 2>&1 && echo $? || echo $?)
                
                    retry_num=$[ $retry_num + 1 ]
                    echo $retry_num
                
                    if [ $tmp_status -eq 0 -o $retry_num -ge 5 ];then
                        break
                    fi
                done
            fi
            
            '''
        }
        stage('unit test') {

            echo "+---------------- unit test ----------------+"
            sh '''#!/bin/bash
                export PATH=${HOME}/miniconda3/bin/:$PATH
                source activate ${conda_env}
                pip config set global.index-url https://pypi.douban.com/simple/
                
                echo "Checking ilit..."
                python -V
                pip list
                c_ilit=$(pip list | grep -c 'ilit') || true  # Prevent from exiting when 'ilit' not found
                if [ ${c_ilit} != 0 ]; then
                    pip uninstall ilit -y
                    pip list
                fi
                                
                echo "Install iLiT binary..."
                pip install ilit*.whl
            
                if [ ! -d ${WORKSPACE}/ilit-models ]; then
                    echo "\\"ilit-model\\" not found. Exiting..."
                    exit 1
                fi
                
                echo -e "\\nInstalling ut requirements..."
                cd ${WORKSPACE}/ilit-models/test
                if [ -f "requirements.txt" ]; then
                    sed -i '/ilit/d' requirements.txt
                    n=0
                    until [ "$n" -ge 5 ]
                    do
                       python -m pip install -r requirements.txt && break
                       n=$((n+1))
                       sleep 5
                    done
                    pip list
                else
                    echo "Not found requirements.txt file."
                fi
               
                find . -name "test*.py" | sed 's/.\\//python /g' > run.sh
                ut_log_name=${WORKSPACE}/unit_test.log
                bash run.sh 2>&1 | tee ${ut_log_name}
                if [ $(grep -c "FAILED" ${ut_log_name}) != 0 ] || [ $(grep -c "OK" ${ut_log_name}) == 0 ];then
                    exit 1
                fi
            '''

        }

    }catch (e) {
        // If there was an exception thrown, the build failed
        currentBuild.result = "FAILURE"
        throw e
    }finally {
        // archive artifacts
        stage("Artifacts") {
            archiveArtifacts artifacts: '*.log,*/*.log', excludes: null
            fingerprint: true
        }
    }
}
