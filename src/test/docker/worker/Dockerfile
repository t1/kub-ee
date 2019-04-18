FROM deployer/wildfly

COPY deployer.config.yaml ${JBOSS_HOME}/standalone/configuration/deployer.config.yaml
COPY deployer.root.bundle ${JBOSS_HOME}/standalone/configuration/deployer.root.bundle

CMD ${JBOSS_HOME}/bin/standalone.sh --server-config ${SERVER_CONFIG} -b 0.0.0.0
