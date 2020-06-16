#!/bin/bash

###
function main {
    fetch_cpu_info
    init_params "$@"
    set_environment
    
    if [ "${model_src_dir}" != "" ];then
        cd ${model_src_dir}
    fi
    git remote -v
    git branch
    git show |head -5

    for bs in ${batch_size[@]}
    do
        for cpi in ${cores_per_instance[@]}
        do
            generate_core
        done
    done

}

# init params
function init_params {
    framework='mxnet'
    model='resnet50v1_5'
    mode='inference'
    precision='fp32'
    batch_size=128

    for var in "$@"
    do 
        case $var in
            --framework=*)
                framework=$(echo $var |cut -f2 -d=)
            ;;
            --model=*)
                model=$(echo $var |cut -f2 -d=)
            ;;
            --mode=*)
                mode=$(echo $var |cut -f2 -d=)
            ;;
            --precision=*)
                precision=$(echo $var |cut -f2 -d=)
            ;;
            --batch_size=*)
                batch_size=($(echo $var |cut -f2 -d= |sed 's/,/ /g'))
            ;;
            --numa_nodes_use=*)
                numa_nodes_use=$(echo $var |cut -f2 -d=)
            ;;
            --cores_per_instance=*)
                cores_per_instance=($(echo $var |cut -f2 -d= |sed 's/,/ /g'))
            ;;
            --conda_env_name=*)
                conda_env_name=$(echo $var |cut -f2 -d=)
            ;;
            --model_src_dir=*)
                model_src_dir=$(echo $var |cut -f2 -d=)
            ;;
            --in_graph=*)
                in_graph=$(echo $var |cut -f2 -d=)
            ;;
            --cores_per_node=*)
                cores_per_node=$(echo $var |cut -f2 -d=)
            ;;
            --data_location=*)
                data_location=$(echo $var |cut -f2 -d=)
            ;;
            --data_shape=*)
                data_shape=$(echo $var |cut -f2 -d=)
            ;;
            *)
                echo "Error: No such parameter: ${var}"
                exit 1
            ;;
        esac
    done
}

# environment
function set_environment {
    #
    # export KMP_BLOCKTIME=1 
    export KMP_AFFINITY=granularity=fine,verbose,compact,1,0
    
    # Intel Compiler
    # source /opt/intel/mlsl_2018.3.008/intel64/bin/mlslvars.sh thread
    source /opt/intel/compilers_and_libraries_2019.5.281/linux/bin/compilervars.sh intel64
    # source /opt/intel/compilers_and_libraries_2019.5.281/linux/mpi/intel64/bin/mpivars.sh release_mt
    
    # proxy
    # export ftp_proxy=http://child-prc.intel.com:913
    # export http_proxy=http://child-prc.intel.com:913
    # export https_proxy=http://child-prc.intel.com:913

    # gcc6.3
    # export PATH=${HOME}/tools/gcc6_3_0/bin:$PATH
    # export LD_LIBRARY_PATH=${HOME}/tools/gcc6_3_0/lib64:$LD_LIBRARY_PATH
    gcc -v

    # conda3 python3
    export PATH="${HOME}/tools/anaconda3/bin:$PATH"
    source activate ${conda_env_name}
    # export PYTHONPATH=${mxnet_dir}/python:$PYTHONPATH
    python -V
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
    cores_per_instance=${cores_per_node}
    numa_nodes_use='1'
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
    # log_dir="${WORKSPACE}"
    # mkdir -p ${log_dir}

    excute_cmd_file="/tmp/${framework}-${model}-run-$(date +'%s').sh"
    rm -f ${excute_cmd_file}
    
    # just for rn50
    if [ "${precision}" == "int8" ];then
        # python imagenet_gen_qsym_mkldnn.py --model=resnet50_v1b --num-calib-batches=5 --calib-mode=naive
        extension_ops=" --symbol-file=${in_graph}/resnet50_v1b-quantized-5batches-naive-symbol.json --param-file=${in_graph}/resnet50_v1b-quantized-0000.params "
    else
        extension_ops=" --symbol-file=${in_graph}/resnet50_v1b-symbol.json --param-file=${in_graph}/resnet50_v1b-0000.params "
    fi

    for(( i=0; i<instance; i++ ))
    do
        real_cores_per_instance=$(echo ${cpu_array[i]} |awk -F, '{print NF}')
        # log_file="${log_dir}/intel-ilit-${framework}-${model}-bs${bs}-cores${real_cores_per_instance}-n${i}-$(date +%s).log"

        printf " OMP_NUM_THREADS=${real_cores_per_instance} numactl --localalloc --physcpubind ${cpu_array[i]} \
            python offical_rn50.py \
              ${extension_ops} \
              --rgb-mean=123.68,116.779,103.939 \
              --rgb-std=58.393,57.12,57.375 \
              --batch-size=${bs} \
              --num-skipped-batches=50 --num-inference-batches=200 \
              --ctx=cpu \
              --dataset=${data_location} \
        &  " |tee -a ${excute_cmd_file}
    done

    echo -e "\n wait" >> ${excute_cmd_file}
    echo -e "\n\n\n batch_size: $bs, cores_per_instance: $cpi, instance: ${instance} is Running"

    sleep 3
    source ${excute_cmd_file}
}

main "$@"
