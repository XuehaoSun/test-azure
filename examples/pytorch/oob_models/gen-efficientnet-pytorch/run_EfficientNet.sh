#!/bin/bash

function main {

    if [ "$WORKSPACE" == "" ];then
        WORKSPACE=/tmp
    fi

    fetch_cpu_info
    init_params "$@"
    set_environment

    for bs in ${batch_size[@]}
    do
        for cpi in ${cores_per_instance[@]}
        do
            generate_core
            collect_logs
            
            if [ "${mode}" == "server" ] && [ $(echo |awk -v value=$latency -v target=$latency_constraints '{if(value<=target) {print "1"}else {print "0"}}') -eq 0 ];then
                echo "---- The latency has been achieve for BS=${bs}! ----"
                break
            fi
        done
        
        if [ "${mode}" == "server" ] && [ $(echo |awk -v value=$latency -v target=$latency_constraints '{if(value<=target) {print "1"}else {print "0"}}') -eq 0 ] && [ $cores_per_node -eq $cpi ];then
            echo "---- No better to tune! ----"
            exit 0
        fi
    done
}

# cpu info
function fetch_cpu_info {
    hostname
    cat /etc/os-release
    cat /proc/sys/kernel/numa_balancing
    cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor
    lscpu
    free -h
    numactl -H
    sockets_num=$(lscpu |grep 'Socket(s):' |sed 's/[^0-9]//g')
    cores_per_socket=$(lscpu |grep 'Core(s) per socket:' |sed 's/[^0-9]//g')
    phsical_cores_num=$( echo "${sockets_num} * ${cores_per_socket}" |bc )
    numa_nodes_num=$(lscpu |grep 'NUMA node(s):' |sed 's/[^0-9]//g')
    cores_per_node=$( echo "${phsical_cores_num} / ${numa_nodes_num}" |bc )
    numa_nodes_use=${numa_nodes_num}
    cores_per_instance=${cores_per_node}
}

# init params
function init_params {

    framework='pytorch'
    model='EfficientNet'
    latency_constraints=100
    
    script_path="./"
    precision="fp32"
    mode="realtime"
    batch_size=(1)
    cores_per_instance=(24)
    checkpoint=""
    dataset=""
    basecommit=""
    patch=""
    
    for var in $@
    do
        case $var in
            --script_path=*)
                script_path=$(echo $var |cut -f2 -d=)
            ;;
            --precision=*)
                precision=$(echo $var |cut -f2 -d=)
            ;;
            --mode=*)
                mode=$(echo $var |cut -f2 -d=)
            ;;
            --batch_size=*)
                batch_size=($(echo $var |cut -f2 -d= |sed 's/,/ /g'))
            ;;
            --cores_per_instance=*)
                cores_per_instance=($(echo $var |cut -f2 -d= |sed 's/,/ /g'))
            ;;
            --checkpoint=*)
                checkpoint=$(echo $var |cut -f2 -d=)
            ;;
            --dataset=*)
                dataset=$(echo $var |cut -f2 -d=)
            ;;
            --basecommit=*)
                basecommit=$(echo $var |cut -f2 -d=)
            ;;
            --patch=*)
                patch=$(echo $var |cut -f2 -d=)
            ;;
            *)
                echo "Error: No such parameter: ${var}"
                exit 1
            ;;
        esac
    done
    
    # if [ -f ${script_path} ];then
    #     cd ${script_path%/*}
    # fi
    
    if [ "${dataset}" != "" ];then
        dummy_or_not=False
    else
        dummy_or_not=True
    fi
    
}

# environment
function set_environment {
    #
    export KMP_BLOCKTIME=1
    export KMP_AFFINITY=granularity=fine,verbose,compact,1,0
    
    #
    git remote -v
    git branch
    git show |head -5
}

# run
function generate_core {
    # cpu array
    cpu_array=($(numactl -H |grep "node [0-9]* cpus:" |sed "s/.*node [0-9]* cpus: *//" |\
    head -${numa_nodes_use} |cut -f1-${cores_per_node} -d' ' |sed 's/$/ /' |tr -d '\n' |awk -v cpi=${cpi} -v cpn=${cores_per_node} '{
        for( i=1; i<=NF; i++ ) {
            if(i % cpi == 0 || i % cpn == 0) {
                print $i","
            }else {
                printf $i","
            }
        }
    }' |sed "s/,$//"))
    instance=${#cpu_array[@]}

    # set run command
    log_dir="${WORKSPACE}/${framework}-${model}-${mode}-${precision}-bs${bs}-cpi${cpi}-ins${instance}-nnu${numa_nodes_use}-$(date +'%s')"
    mkdir -p ${log_dir}

    excute_cmd_file="${log_dir}/${framework}-run-$(date +'%s').sh"
    rm -f ${excute_cmd_file}

    for(( i=0; i<instance; i++ ))
    do
        real_cores_per_instance=$(echo ${cpu_array[i]} |awk -F, '{print NF}')
        log_file="${log_dir}/rcpi${real_cores_per_instance}_ins${i}.log"

        printf " OMP_NUM_THREADS=${real_cores_per_instance} numactl --localalloc --physcpubind ${cpu_array[i]} \
            python -u ./main.py -e \
                                --performance \
                                --pretrained \
                                --no-cuda \
                                --mkldnn \
                                -j 1 \
                                -a efficientnet_b7 \
                                -b ${bs} \
                                -w 10 \
                                -i 100 \
                                --dummy \
                                --jit \
                                $dataset \
                                > ${log_file} 2>&1 & " |tee -a ${excute_cmd_file}
    done

    echo -e "\n wait" >> ${excute_cmd_file}
    echo -e "\n\n\n bs: $bs, cores_per_instance: $cpi, instance: ${instance} is Running"

    sleep 3
    source ${excute_cmd_file}
}

# collect logs
function collect_logs {
    latency=$(grep 'Latency:' ${log_dir}/rcpi${cpi}* |sed -e 's/.*log//;s/[^0-9.]//g' |awk '
    BEGIN {
        sum = 0;
        i = 0;
    }
    {
        sum = sum + $1;
        i++;
    }
    END {
        sum = sum / i;
        printf("%.3f", sum);
    }')

    throughput=$(grep 'inference Throughput:' ${log_dir}/rcpi* |sed -e 's/.*log//;s/[^0-9.]//g' |awk '
    BEGIN {
        sum = 0;
    }
    {
        sum = sum + $1;
    }
    END {
        printf("%.2f", sum);
    }')
    echo "write_infos={'model_name': '$model', 'scenario': '$mode', 'data_type': '$precision', 'dummy': '$dummy_or_not', 'batch_size': '$bs',  'latency': '$latency', 'throughput': '$throughput', 'instance_num': '$instance', 'core_per_instance': '$cpi', 'accuracy': 'NA'}"
}

main "$@"

