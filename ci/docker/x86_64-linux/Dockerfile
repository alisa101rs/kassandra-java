FROM centos:7

ARG TOOLCHAIN=stable-2023-10-05

RUN yum install -y git gcc java-11-openjdk

ENV PATH=$PATH:/rust/bin:/root/.cargo/bin
ENV JAVA_HOME=/etc/alternatives/jre_11_openjdk

# Confirm that the JAVA_HOME var is set correctly
RUN ls ${JAVA_HOME}/bin/java

# Install rustup
RUN curl https://sh.rustup.rs -sSf | sh -s -- -y --default-toolchain=${TOOLCHAIN} --profile=minimal && rustc --version
RUN rustup component add rustfmt