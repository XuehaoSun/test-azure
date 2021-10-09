#!/bin/bash
set -xe

# gen-efficientnet-pytorch

function main {
    # prepare workload
    workload_dir="${PWD}"
    cd ../../gen-efficientnet-pytorch/
    # cp ${workload_dir}/main.py ./
    git reset --hard
    patch -f -p1 < ${workload_dir}/gen.patch || true
    pip install -r ${workload_dir}/requirements.txt
    pip install --no-deps torchvision -f https://download.pytorch.org/whl/torch_stable.html

    # init the params
    init_params $@

    # fetch the cpu info
    fetch_cpu_info

    # set ENV for benchmark
    set_environment

    # if multiple use 'xxx,xxx,xxx'
    model_name_list=($(echo "${model_name}" |sed 's/,/ /g'))
    batch_size_list=($(echo "${batch_size}" |sed 's/,/ /g'))
    cores_per_instance_list=($(echo "${cores_per_instance}" |sed 's/,/ /g'))

    # generate benchmark
    for model_name in ${model_name_list[@]}
    do
        # cache weight
        python ./main.py -e --performance --pretrained --dummy --no-cuda -j 1 -w 1 -i 2 \
            -a ${model_name} -b 1 --precision ${precision} --channels_last ${channels_last} > /dev/null 2>&1 || true
        #
        for batch_size in ${batch_size_list[@]}
        do
            for cores_per_instance in ${cores_per_instance_list[@]}
            do
                generate_core
                collect_perf_logs
            done
        done
    done
}

# parameters
function init_params {
    if [ "${WORKSPACE}" == "" ];then
        WORKSPACE=./logs
    fi
    precision='float32'
    numa_nodes_use=1
    cores_per_instance=4
    profile=0
    dnnl_verbose=0
    channels_last=0
    framework='pytorch'
    model_name='gen-efficientnet-pytorch'
    mode_name='realtime'
    batch_size=1
    num_warmup=10
    num_iter=200
    #
    for var in $@
    do
        case ${var} in
            --workspace=*|-ws=*)
                WORKSPACE=$(echo $var |cut -f2 -d=)
            ;;
            --numa_nodes_use=*|--numa=*)
                numa_nodes_use=$(echo $var |cut -f2 -d=)
            ;;
            --cores_per_instance=*)
                cores_per_instance=$(echo $var |cut -f2 -d=)
            ;;
            --profile=*)
                profile=$(echo $var |cut -f2 -d=)
            ;;
            --dnnl_verbose=*)
                dnnl_verbose=$(echo $var |cut -f2 -d=)
            ;;
            --channels_last=*)
                channels_last=$(echo $var |cut -f2 -d=)
            ;;
            --framework=*)
                framework=$(echo $var |cut -f2 -d=)
            ;;
            --model_name=*|--model=*|-m=*)
                model_name=$(echo $var |cut -f2 -d=)
            ;;
            --mode_name=*|--mode=*)
                mode_name=$(echo $var |cut -f2 -d=)
            ;;
            --precision=*|--mode=*)
                precision=$(echo $var |cut -f2 -d=)
            ;;
            --batch_size=*|-bs=*|-b=*)
                batch_size=$(echo $var |cut -f2 -d=)
            ;;
            --num_warmup=*|--warmup=*|-w=*)
                num_warmup=$(echo $var |cut -f2 -d=)
            ;;
            --num_iter=*|--iter=*|-i=*)
                num_iter=$(echo $var |cut -f2 -d=)
            ;;
            *)
                echo "ERROR: No such param: ${var}"
                exit 1
            ;;
        esac
    done
    # all gen models
    if [ "${model_name}" == "gen-efficientnet-pytorch" ];then
        model_name="alexnet,resnet18,resnet34,resnet50,resnet101,resnet152,squeezenet1_0,"
        model_name+="squeezenet1_1,vgg11,vgg13,vgg16,vgg19,vgg11_bn,vgg13_bn,"
        model_name+="vgg16_bn,vgg19_bn,shufflenet_v2_x0_5,shufflenet_v2_x1_0,googlenet,"
        model_name+="resnext50_32x4d,resnext101_32x8d,wide_resnet50_2,wide_resnet101_2,"
        model_name+="inception_v3,efficientnet_b0,efficientnet_b1,efficientnet_b2,"
        model_name+="efficientnet_b3,efficientnet_b4,efficientnet_b5,efficientnet_b6,"
        model_name+="efficientnet_b7,efficientnet_b8,mnasnet1_0,mnasnet0_5,densenet121,"
        model_name+="densenet169,densenet201,densenet161,fbnetc_100,spnasnet_100,"
    fi
}

# cpu info
function fetch_cpu_info {
    # hardware
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
    if [ "${numa_nodes_use}" == "all" ];then
        numa_nodes_use=$(lscpu |grep 'NUMA node(s):' |awk '{print $NF}')
    fi

    # environment
    gcc -v
    python -V
    pip list
    git remote -v
    git branch
    git show -s
}

# environment
function set_environment {
    #
    export http_proxy=http://child-prc.intel.com:913
    export https_proxy=http://child-prc.intel.com:913
    export KMP_BLOCKTIME=1
    export KMP_AFFINITY=granularity=fine,verbose,compact,1,0

    # DNN Verbose
    if [ "${dnnl_verbose}" == "1" ];then
        export DNNL_VERBOSE=1
        export MKLDNN_VERBOSE=1
    else
        unset DNNL_VERBOSE MKLDNN_VERBOSE
    fi
    
    # AMX
    if [ "${precision}" == "bfloat16" ];then
        export DNNL_MAX_CPU_ISA=AVX512_CORE_AMX
    else
        unset DNNL_MAX_CPU_ISA
    fi
    
    # Profile
    addtion_options=""
    if [ "${profile}" == "1" ];then
        addtion_options+=" --profile "
    fi
}

# run
function generate_core {
    # cpu array
    cpu_array=($(numactl -H |grep "node [0-9]* cpus:" |sed "s/.*node [0-9]* cpus: *//" |\
    head -${numa_nodes_use} |cut -f1-${cores_per_node} -d' ' |sed 's/$/ /' |tr -d '\n' |awk -v cpi=${cores_per_instance} -v cpn=${cores_per_node} '{
        for( i=1; i<=NF; i++ ) {
            if(i % cpi == 0 || i % cpn == 0) {
                print $i","
            }else {
                printf $i","
            }
        }
    }' |sed "s/,$//"))
    instance=${#cpu_array[@]}

    # logs saved
    log_dir="${WORKSPACE}/${framework}-${model_name}-${mode_name}-${precision}-bs${batch_size}-"
    log_dir+="cpi${cores_per_instance}-ins${instance}-nnu${numa_nodes_use}-$(date +'%s')"
    mkdir -p ${log_dir}
    if [ ! -e ${WORKSPACE}/summary.log ];then
        printf "framework, model_name, mode_name, precision, batch_size, " | tee ${WORKSPACE}/summary.log
        printf "cores_per_instance, instance, throughput, link, \n" | tee -a ${WORKSPACE}/summary.log
    fi

    # generate multiple instance script
    excute_cmd_file="${log_dir}/${framework}-run-$(date +'%s').sh"
    rm -f ${excute_cmd_file}

    for(( i=0; i<instance; i++ ))
    do
        real_cores_per_instance=$(echo ${cpu_array[i]} |awk -F, '{print NF}')
        log_file="${log_dir}/rcpi${real_cores_per_instance}-ins${i}.log"

        printf "numactl --localalloc --physcpubind ${cpu_array[i]} timeout 7200 \
            python ./main.py -e --performance --pretrained --dummy --no-cuda -j 1 \
                -w ${num_warmup} -i ${num_iter} \
                -a ${model_name} \
                -b ${batch_size} \
                --precision ${precision} \
                --channels_last ${channels_last} \
                ${addtion_options} ${OOB_ADDITION_PARAMS} \
        > ${log_file} 2>&1 &  \n" |tee -a ${excute_cmd_file}
    done
    echo -e "\n wait" >> ${excute_cmd_file}
    echo -e "\n\n bs: ${batch_size}, cores_per_instance: ${cores_per_instance}, instance: ${instance} is Running"
    TZ='Asia/Shanghai' date -d "$(curl -v --silent https://google.com 2>&1 |grep -i '< *date' |sed 's+.*ate: *++;s+GMT.*+GMT+')"
    source ${excute_cmd_file}
}

# collect logs
function collect_perf_logs {
    # dnnl verbose
    if [ "${dnnl_verbose}" == "1" ];then
        for i_file in $(find ${log_dir}/ -type f -name "rcpi*.log" |sort)
        do
            echo -e "---- $(basename ${i_file}) ----" >> ${log_dir}/dnnlverbose.log
            python ${WORKSPACE}/scripts/dnnl-parser.py --file ${i_file} >> ${log_dir}/dnnlverbose.log 2>&1 || true
            echo -e "\n\n" >> ${log_dir}/dnnlverbose.log
        done
    fi
    #
    # latency=$(grep 'Throughput:' ${log_dir}/rcpi* |sed -e 's/.*Throughput//;s/,.*//;s/[^0-9.]//g' |awk -v bs=${batch_size} '
    #     BEGIN {
    #         sum = 0;
    #         i = 0;
    #     }
    #     {
    #         sum = sum + bs / $1 * 1000;
    #         i++;
    #     }
    #     END {
    #         sum = sum / i;
    #         printf("%.3f", sum);
    #     }
    # ')
    throughput=$(grep 'Throughput:' ${log_dir}/rcpi* |sed -e 's/.*Throughput//;s/,.*//;s/[^0-9.]//g' |awk '
        BEGIN {
            sum = 0;
        }
        {
            sum = sum + $1;
        }
        END {
            printf("%.2f", sum);
        }
    ')
    artifact_url="${BUILD_URL}artifact/$(basename ${log_dir})"
    printf "${framework}, ${model_name}, ${mode_name}, ${precision}, " |tee ${log_dir}/result.txt |tee -a ${WORKSPACE}/summary.log
    printf "${batch_size}, ${cores_per_instance}, ${instance}, ${throughput}, " |tee -a ${log_dir}/result.txt |tee -a ${WORKSPACE}/summary.log
    printf "${artifact_url}, \n" |tee -a ${log_dir}/result.txt |tee -a ${WORKSPACE}/summary.log
}

# Start
main "$@"
