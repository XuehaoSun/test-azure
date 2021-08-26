FROM amr-cache-registry.caas.intel.com/cache/library/centos:7

#-----------------------Set env variables-----------------------
ENV GCC_VERSION="7.3.0"
ARG PYTHON_VERSION="3.6"
ARG TENSORFLOW_VERSION="2.5.0"
ARG PYTORCH_VERSION="1.9.0+cpu"
ARG TORCHVISION_VERSION="0.10.0+cpu"
ARG MXNET_VERSION="1.7.0"
ARG ONNX_VERSION="1.9.0"
ARG ONNXRUNTIME_VERSION="1.8.0"

#-------------------------Define proxy--------------------------
ENV http_proxy=http://child-prc.intel.com:913
ENV https_proxy=http://child-prc.intel.com:913

RUN yum -y install epel-release && yum clean all
RUN yum -y install libmpc-devel mpfr-devel gmp-devel mesa-libGL && yum clean all
RUN yum -y group install "Development Tools" && yum clean all
RUN yum -y install curl cmake make python python-devel wget which python-pip sudo yum python3 python3-pip && yum clean all
RUN pip3 install --upgrade pip setuptools

#----------------Install hw detect packages---------------------
RUN yum -y install dmidecode numactl pciutils && yum clean all

#-------------------------GCC-----------------------------------
RUN mkdir /opt/gcc
WORKDIR /opt/gcc
COPY gcc/gcc-${GCC_VERSION}.tar.gz ./gcc-${GCC_VERSION}.tar.gz
RUN tar -xf gcc-${GCC_VERSION}.tar.gz && \
    rm -rf gcc-${GCC_VERSION}.tar.gz && \
    cd gcc-${GCC_VERSION}/ && \
    ./configure --disable-multilib --enable-languages=c,c++ --prefix=/usr/local && \
    make -j ${CORES_NUMBER} && \
    make install && \
    cd /opt && rm -rf /opt/gcc && \
    rm -rf /root/gcc
ENV LD_LIBRARY_PATH=/usr/local/lib64:$LD_LIBRARY_PATH

#-------------------Install miniconda----------------------
RUN wget https://repo.anaconda.com/miniconda/Miniconda3-latest-Linux-x86_64.sh -O /root/miniconda.sh && \
    bash /root/miniconda.sh -b -p /root/miniconda

ENV PATH=/root/miniconda/bin:$PATH

WORKDIR /root
COPY ./lpot-models /root/lpot
COPY ./lpot-validation/Jenkins/dockerfiles/centos7/prepare_environment.sh /root/prepare_environment.sh
RUN bash /root/prepare_environment.sh \
        --python_version=${PYTHON_VERSION} \
        --tensorflow_version=${TENSORFLOW_VERSION} \
        --pytorch_version=${PYTORCH_VERSION} \
        --torchvision_version=${TORCHVISION_VERSION} \
        --mxnet_version=${MXNET_VERSION} \
        --onnx_version=${ONNX_VERSION} \
        --onnxruntime_version=${ONNXRUNTIME_VERSION} && \
    rm /root/prepare_environment.sh

#-------------------Copy mini datasets-----
COPY ./datasets /root/datasets
