# ----- Get strategy -----

function get_strategy {
    if [ ! -f ${yaml_config} ]; then
        echo "Not found yaml config at \"${yaml_config}\" location."
        exit 1
    fi

    update_yaml_params=""
    # Replace tuning strategy in yaml file
    if [ "${tuning_strategy}" != "" ]; then
        update_yaml_params="${update_yaml_params} --strategy=${tuning_strategy}"
    fi

    if [ "${framework}" == "pytorch" ] && [ "${model_type}" == "cnn" ]; then
        update_yaml_params="${update_yaml_params} --calib-data=${dataset_location}/train --eval-data=${dataset_location}/val"
    fi

    if [ "${update_yaml_params}" != "" ]; then
        python ${WORKSPACE}/ilit-validation/scripts/update_yaml_config.py --yaml=${yaml_config} ${update_yaml_params}
    fi

    count=$(grep -c 'strategy: ' "${yaml_config}") || true  # Prevent from exiting when 'strategy' not found
    if [ ${count} == 0 ]; then
      strategy='basic'
    else
      strategy=$(grep 'strategy: ' ${yaml_config} | awk -F 'strategy: ' '{print$2}')
    fi

    echo "Tuning strategy: ${strategy}"
}


