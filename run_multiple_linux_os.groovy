// setting node_label
node_label = "master"
if ('node_label' in params && params.node_label != '') {
    node_label = params.node_label
}
echo "Running on node ${node_label}"

// setting node_label
os_version_list = "lpot"
if ('os_version_list' in params && params.os_version_list != '') {
    os_version_list = params.os_version_list
}
echo "os_version_list is ${os_version_list}"

def cleanup() {

    try {
        sh '''#!/bin/bash -x
        cd $WORKSPACE
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
    }  // catch

}

def runOSJobs(){
    os_version_list.split(',').each { sub_node ->
        list OSTestsParams = [
                string(name: "sub_node_label", value: "${sub_node}")
            ]
        build job: "inc-validation-top-weekly-linux-os", propagate: false, parameters: OSTestsParams
    }
}

node(node_label){
    try {
        cleanup()
        stage("Execute tests") {
            runOSJobs()
        }
    }catch(e) {
          currentBuild.result = "FAILURE"
          error(e.toString())
      }
}
