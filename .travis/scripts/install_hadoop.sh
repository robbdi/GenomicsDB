#!/bin/bash

# Install hadoop
# Installation relies on finding JAVA_HOME@/usr/java/latest as a prerequisite

INSTALL_DIR=${INSTALL_DIR:-/usr}
USER=`whoami`

HADOOP=hadoop-${HADOOP_VER:-2.9.2}
HADOOP_DIR=${INSTALL_DIR}/$HADOOP

install_prereqs() {
  if [[ ! -d /usr/java ]]; then
      sudo mkdir /usr/java
  fi
  if [[ -f /usr/java/latest ]]; then
      sudo rm /usr/java/latest
  fi
#  sudo apt update &&
  sudo apt install openjdk-8-jre-headless &&
  sudo ln -s /usr/lib/jvm/java-1.8.0-openjdk-amd64/ /usr/java/latest &&
  echo "install_prereqs successful"
}

download_gcs_connector() {
  if [[ $INSTALL_TYPE == gcs ]]; then
    wget -nv https://storage.googleapis.com/hadoop-lib/gcs/gcs-connector-latest-hadoop2.jar
    cp gcs-connector-latest-hadoop2.jar ${HADOOP_DIR}/share/hadoop/common
  fi
}

download_hadoop() {
  if [[ ! -f $CACHE_DIR/$HADOOP.tar.gz ]]; then
    wget -nv --trust-server-names "https://www.apache.org/dyn/mirrors/mirrors.cgi?action=download&filename=hadoop/common/$HADOOP/$HADOOP.tar.gz" -O $CACHE_DIR/$HADOOP.tar.gz
  fi
  sudo tar -xzf $CACHE_DIR/$HADOOP.tar.gz --directory $INSTALL_DIR &&
  sudo chown -R $USER:$USER $HADOOP_DIR &&
  download_gcs_connector &&
  echo "download_hadoop successful" 
}

configure_passphraseless_ssh() {
  ssh-keygen -t rsa -P '' -f ~/.ssh/id_rsa &&
  cat ~/.ssh/id_rsa.pub >> ~/.ssh/authorized_keys &&
  chmod 0600 ~/.ssh/authorized_keys &&
  ssh-keyscan -H localhost >> ~/.ssh/known_hosts &&
  ssh-keyscan -H "0.0.0.0" >> ~/.ssh/known_hosts &&
  echo "configure_passphraseless_ssh successful"
}

configure_hadoop() {
  configure_passphraseless_ssh &&
  cp -fr $TRAVIS_BUILD_DIR/.travis/resources/hadoop/* $HADOOP_DIR/etc/hadoop &&
  mkdir $HADOOP_DIR/logs &&  
  $HADOOP_DIR/bin/hadoop &&
  $HADOOP_DIR/bin/hadoop namenode -format &&
  $HADOOP_DIR/sbin/start-dfs.sh &&
  echo "configure_hadoop successful"
}

setup_paths() {
  export PATH=$HADOOP_DIR/bin:$PATH &&
  export CLASSPATH=`$HADOOP_DIR/bin/hadoop classpath --glob` &&
  echo "setup_paths successful"
}

install_hadoop() {
  echo "Installing Hadoop..."
  install_prereqs &&
  download_hadoop &&
  configure_hadoop &&
  setup_paths &&
  echo "Install Hadoop SUCCESSFUL"
}

install_hadoop





