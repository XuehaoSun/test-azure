#!/bin/bash
declare -a nodes_list=(084 092 052)
declare -a jenkins_name=(052:"skx-temp-agent1",084:"skx-temp-agent2",092:"skx-temp-agent3")
declare -a executors=""
function run_connect {
    nodes=`sinfo | grep idle | grep -Po skx-[0-9]{4}`
    for node in ${nodes[@]}
    do
        [[ $node != "skx-6248" ]] && continue
        agents=`sinfo | grep idle | grep {node} | grep -Po mlt-skx.* | grep -Po [0-9]+.[0-9]+`
        extra_cmd=""
        cnt=0
        for agent in ${agents[@]}
        do
            echo ${agent} | grep "," 
            if [[ $? == 0 ]];then
                agent1=`echo ${agent} | awk -F "," '{print $1}'`
                agent2=`echo ${agent} | awk -F "," '{print $2}'`
                for sub_node in ${nodes_list[@]}
                do
                    if [[ ${sub_node} == ${agent1} ]] || [[ ${sub_node} == ${agent2} ]];then
                        extra_cmd=${extra_cmd}" -w mlt-skx${sub_node}"
                        cnt=$((cnt + 1))
                        executor=`echo ${jenkins_name} | grep -Po ${sub_node}:skx-temp-agent[0-9]{1}, | grep -Po skx-temp-agent[0-9]{1}`
                        executors=${executors}","${executor}
                    fi
                done
            else
                agent1=`echo ${agent} | awk -F "-" '{print $1}'`
                agent2=`echo ${agent} | awk -F "-" '{print $2}'`
                for sub_node in ${nodes_list[@]}
                do
                    if [ ${sub_node} <= ${agent2} && ${sub_node} >= ${agent1} ];then
                        extra_cmd=${extra_cmd}" -w mlt-skx${sub_node}"
                        cnt=$((cnt + 1))
                        executor=`echo ${jenkins_name} | grep -Po ${sub_node}:skx-temp-agent[0-9]{1}, | grep -Po skx-temp-agent[0-9]{1}`
                        executors=${executors}","${executor}
                    fi
                done
            fi
        done
        [[ $cnt -eq 0 || -n ${extra_cmd} ]] && continue
        salloc -N${cnt} -p ${node} --qos gmakey ${extra_cmd} && break || true
    done
    [[ -n `squeue | grep tensorfl` ]] && echo "alloc failed" && exit 1
    echo ${executors}
}

function run_disconnect {
    job_id=`squeue | grep tensorfl | awk '{print $1}'`
    scancel ${job_id}
}

case $1 in
    connect)
        run_connect;;
    dis_connect)
       run_disconnect;;
    *)
       echo "quit";;
esac
