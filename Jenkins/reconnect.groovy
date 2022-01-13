import hudson.node_monitors.*
import hudson.slaves.*
import java.util.concurrent.*

node('master'){
    stage("connect agent"){
        def agent_list = ("skx-temp-agent1" "skx-temp-agent2" "skx-temp-agent3")    
        Jenkins.instance.slaves.find { agent ->
            if (agent.name in agent_list){
                agent.computer.connect(true)
            }
        }
        retry(5) {
            sh'''#!/bin/bash
                sleep 10
                echo "connecting"
            '''
        }
    }
    
    stage("change agent label"){
        Jenkins.instance.slaves.find { agent ->
            if (agent.name in agent_list && !agent.computer.offline){
                agent.setLabelString("ILIT && oob && linux")
            }
        }
    }   
}
