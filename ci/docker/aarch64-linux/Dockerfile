FROM ubuntu:16.04

ARG TOOLCHAIN=stable-2023-10-05

RUN apt-get update && apt-get install -y curl git gcc openjdk-11-jdk gcc-aarch64-linux-gnu ca-certificates

ENV PATH=$PATH:/rust/bin:/root/.cargo/bin
ENV JAVA_HOME=/usr/lib/jvm/java-11-openjdk-amd64
ENV CARGO_BUILD_TARGET=aarch64-unknown-linux-gnu
ENV JNILIB_RUST_TARGET=aarch64-unknown-linux-gnu

# Confirm that the JAVA_HOME var is set correctly
RUN ls ${JAVA_HOME}/bin/java

# Install rustup
RUN curl https://sh.rustup.rs -sSf | sh -s -- -y --default-toolchain=${TOOLCHAIN} --profile=minimal && rustc --version
RUN rustup component add rustfmt

RUN rustup target add ${CARGO_BUILD_TARGET}

# Workaround for https://github.com/rust-lang/cargo/issues/4133
# Linker tool needs to be specified explicitly
COPY .cargo_config /root/.cargo/config